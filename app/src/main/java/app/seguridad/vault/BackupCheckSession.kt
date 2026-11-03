package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.tools.cipher.GuardedSession
import app.seguridad.tools.codes.CellFactory
import app.seguridad.tools.codes.PlainCells
import app.seguridad.tools.codes.getValue
import app.seguridad.tools.codes.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * What «Comprobar una copia» holds: the master password as typed (the text field needs a String; it is emptied when the
 * check starts, which takes it as a CharArray, [BackupCheckRuns]), the copy chosen and its name, and the report. On
 * Android it lives in the screen's ViewModel, never in saved state; [guard] applies the «PIN y códigos» rule as in
 * «Cifrar texto», and [wipe] stops a check going on ([onWipe]) and empties all of it.
 */
class BackupCheckSession(
    cells: CellFactory = PlainCells,
    createdAt: Long = 0L,
    private val onWipe: () -> Unit = {},
) : GuardedSession(createdAt) {
    private val passwordCell = cells.cell("")
    var password: String by passwordCell

    /** The document chosen (the screen keeps its URI here); null until one is chosen, and once wiped. */
    private val sourceCell = cells.cell<Any?>(null)
    var source: Any? by sourceCell
    private val fileNameCell = cells.cell<String?>(null)
    var fileName: String? by fileNameCell
    private val reportCell = cells.cell<BackupCheckReport?>(null)
    var report: BackupCheckReport? by reportCell
    private val errorCell = cells.cell<String?>(null)
    var error: String? by errorCell
    private val busyCell = cells.cell(false)
    var busy: Boolean by busyCell

    override fun forget() {
        onWipe() // the screen stops the check going on, so its result is not shown
        password = ""
        source = null
        fileName = null
        report = null
        error = null
        busy = false
    }
}

/**
 * The checks of «Comprobar una copia»: one at a time, launched in [scope] (the ViewModel's, on the main thread; the work
 * itself switches to IO and Default inside [checkBackup]). Each run gets a number, and the master password as a
 * CharArray it owns: zeroed when the run ends, and at once by [stop]. Only the last run started shows its report or
 * its error, and only if the vault was not locked meanwhile ([lockEpoch], VaultStore.lockEpoch, which also goes up on
 * leaving the app): [stop] (a wipe, «Cancelar», a lock, a new run) cancels the run going on, and a result that still
 * arrives later is dropped. A new run starts its work only once the one it stopped has ended. The run's temporary
 * folder is [checkBackup]'s own, deleted however it ends.
 */
class BackupCheckRuns(
    private val session: BackupCheckSession,
    private val scope: CoroutineScope,
    private val lockEpoch: () -> Int = { 0 },
) {
    private var job: Job? = null
    private var runId = 0L
    private var master: CharArray? = null

    /** Whether a run is going on (for the tests; the screen reads [BackupCheckSession.busy]). */
    val running: Boolean get() = job?.isActive == true

    fun stop() {
        job?.cancel()
        job = null
        runId++
        master?.fill('\u0000')
        master = null
        session.busy = false
    }

    /** Checks with [op], which gets [password] (owned from now on: zeroed however the run ends). */
    fun start(password: CharArray, op: suspend (CharArray) -> BackupCheckReport) {
        val previous = job
        stop()
        val mine = ++runId
        val epoch = lockEpoch()
        master = password
        session.report = null
        session.error = null
        session.busy = true
        job = scope.launch {
            try {
                // A run stopped while in Argon2id ends it first (it cannot be cut): never two derivations at once.
                previous?.join()
                val report = op(password)
                if (current(mine, epoch)) session.report = report
            } catch (e: CancellationException) {
                throw e
            } catch (e: CryptoFailure) {
                if (current(mine, epoch)) session.error = e.message ?: BACKUP_CHECK_FAILED
            } catch (e: IOException) {
                if (current(mine, epoch)) session.error = BACKUP_CHECK_READ_FAILED
            } catch (e: Exception) {
                if (current(mine, epoch)) session.error = BACKUP_CHECK_FAILED
            } finally {
                password.fill('\u0000')
                if (runId == mine) {
                    master = null
                    session.busy = false
                    job = null
                }
            }
        }
    }

    private fun current(mine: Long, epoch: Int) = runId == mine && lockEpoch() == epoch
}
