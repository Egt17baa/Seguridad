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

    /**
     * 2.0, task 8: whether the last report counted as this vault's last copy checked ([BackupCheckRuns]'s record: a copy of
     * the open vault, without problems); false for one of another vault or made before 2.0, which the screen says (O-5 of
     * the Abogado Digital); null with no report.
     */
    private val countedCell = cells.cell<Boolean?>(null)
    var counted: Boolean? by countedCell

    override fun forget() {
        onWipe() // the screen stops the check going on, so its result is not shown
        password = ""
        source = null
        fileName = null
        report = null
        error = null
        counted = null
        busy = false
    }
}

/**
 * The checks of «Comprobar una copia»: one at a time, launched in [scope] (the ViewModel's, on the main thread; the work
 * itself switches to IO and Default inside [checkBackup]). Each run gets a number, and the master password as a
 * CharArray it owns: zeroed when the run ends, and at once by [stop]. Only the last run started shows its report or
 * its error, and only if the vault was not locked meanwhile ([lockEpoch], VaultStore.lockEpoch, which also goes up on
 * leaving the app): [stop] (a wipe, «Cancelar», a lock, a new run) cancels the run going on, and a result that still
 * arrives later is dropped. A run stopped during its Argon2id still ends that derivation (it cannot be cut), and the
 * next run's derivation waits for it ([checkBackup] takes one derivation at a time in the process, whatever this class
 * does). The run's temporary folder is [checkBackup]'s own, deleted however it ends.
 *
 * 2.0, task 8: a report without problems is handed to [record] with the [lockEpoch] read when the run started, and what it
 * says goes to [BackupCheckSession.counted]: the app keeps the date of the check in the open vault only for a copy of that
 * vault (its tag, read from the very data checked), and never after a lock since the run started.
 */
class BackupCheckRuns(
    private val session: BackupCheckSession,
    private val scope: CoroutineScope,
    private val lockEpoch: () -> Int = { 0 },
    private val record: suspend (report: BackupCheckReport, sinceLock: Int) -> Boolean = { _, _ -> false },
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
        stop()
        val mine = ++runId
        val epoch = lockEpoch()
        master = password
        session.report = null
        session.error = null
        session.counted = null
        session.busy = true
        job = scope.launch {
            try {
                val report = op(password)
                // Only a copy without problems counts as checked; a failure to save it only means it does not count.
                val counted = report.problems.isEmpty() && current(mine, epoch) && try {
                    record(report, epoch)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                if (current(mine, epoch)) {
                    session.report = report
                    session.counted = counted
                }
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
