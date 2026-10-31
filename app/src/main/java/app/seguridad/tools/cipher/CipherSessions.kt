package app.seguridad.tools.cipher

import app.seguridad.crypto.Aead
import app.seguridad.tools.codes.CellFactory
import app.seguridad.tools.codes.PlainCells
import app.seguridad.tools.codes.ScreenGuard
import app.seguridad.tools.codes.getValue
import app.seguridad.tools.codes.setValue

/**
 * COV1-1 (audit 1.3.0): what «Cifrar texto», «Cifrar archivos» and «¿Contraseña filtrada?» hold, outside the
 * composition. On Android each lives in its screen's ViewModel (never in saved state: a Bundle can go to disk), so a
 * rotation keeps it and the death of the process loses it. [guard] applies the «PIN y códigos» rule: back after
 * `max(lock time, 60 s)` away, or a lock of the vault with the app in use, and [wipe] runs. The choices that are not
 * secrets (the mode, the algorithm, password or key, the chosen file) stay, like the sites in «PIN y códigos».
 *
 * A run that was going on when the screen was wiped must not bring its result back: [token] is taken when it starts
 * and [isCurrent] says whether it may still show what it found. The secrets are Strings (what Compose shows and edits),
 * so they cannot be zeroed: every reference to them is dropped.
 */
abstract class GuardedSession(createdAt: Long) {
    private var generation = 0L

    /** Wired by the screen to ON_STOP and ON_START, the multi-window signal and the vault's state. */
    val guard = ScreenGuard(createdAt) { wipe() }

    /** Taken when a run starts. */
    fun token(): Long = generation

    /** False once the screen was wiped after [token] was taken: that run's result is dropped. */
    fun isCurrent(token: Long): Boolean = token == generation

    /** What was typed and the result go; a run going on will not show its result. */
    fun wipe() {
        generation++
        forget()
    }

    protected abstract fun forget()
}

/** «Cifrar texto». */
class TextCryptoSession(cells: CellFactory = PlainCells, createdAt: Long = 0L) : GuardedSession(createdAt) {
    private val encryptCell = cells.cell(true)
    var encrypt: Boolean by encryptCell
    private val seedModeCell = cells.cell(true)
    var seedMode: Boolean by seedModeCell
    private val algCell = cells.cell(Aead.AES_256_GCM)
    var alg: Aead by algCell
    private val useKeyCell = cells.cell(false)
    var useKey: Boolean by useKeyCell

    /** The plaintext (often a seed phrase) to encrypt, or the ciphertext to decrypt. */
    private val inputCell = cells.cell("")
    var input: String by inputCell
    private val passwordCell = cells.cell("")
    var password: String by passwordCell
    private val keyHexCell = cells.cell("")
    var keyHex: String by keyHexCell

    /** The ciphertext, or the decrypted text. */
    private val outputCell = cells.cell<String?>(null)
    var output: String? by outputCell
    private val errorCell = cells.cell<String?>(null)
    var error: String? by errorCell

    /** Importing or saving a .txt: true for success, and the text. */
    private val fileMessageCell = cells.cell<Pair<Boolean, String>?>(null)
    var fileMessage: Pair<Boolean, String>? by fileMessageCell
    private val busyCell = cells.cell(false)
    var busy: Boolean by busyCell

    override fun forget() {
        input = ""
        password = ""
        keyHex = ""
        output = null
        error = null
        fileMessage = null
    }
}

/** A decrypted copy in the app's private cache, waiting for the save dialog: its [path] and the [run] that made it. */
class PendingCopy(val path: String, val run: Long)

/**
 * «Cifrar archivos»: the password or key, what the last run said, and the decrypted copy waiting to be saved (its path:
 * the file itself is never kept here). M2 of the F6 review: a wipe also gives that copy up ([onDiscard] deletes it), so
 * the save dialog's answer after the time away saves nothing.
 */
class FileCryptoSession(
    cells: CellFactory = PlainCells,
    createdAt: Long = 0L,
    private val onDiscard: (String) -> Unit = {},
) : GuardedSession(createdAt) {
    private val useKeyCell = cells.cell(false)
    var useKey: Boolean by useKeyCell
    private val passwordCell = cells.cell("")
    var password: String by passwordCell
    private val keyHexCell = cells.cell("")
    var keyHex: String by keyHexCell
    private val messageCell = cells.cell<Pair<Boolean, String>?>(null)
    var message: Pair<Boolean, String>? by messageCell
    private val busyCell = cells.cell(false)
    var busy: Boolean by busyCell

    /** The decrypted copy the save dialog is for; null once saved, given up or wiped. */
    var pending: PendingCopy? = null

    /** The save dialog answered: the pending copy, now the caller's to write and delete; null if it was given up. */
    fun takePending(): PendingCopy? = pending.also { pending = null }

    override fun forget() {
        password = ""
        keyHex = ""
        message = null
        pending?.let { onDiscard(it.path) }
        pending = null
    }
}

/** «¿Contraseña filtrada?»: the password being typed and the answer. */
class BreachSession(cells: CellFactory = PlainCells, createdAt: Long = 0L) : GuardedSession(createdAt) {
    private val passwordCell = cells.cell("")
    var password: String by passwordCell
    private val resultCell = cells.cell<Pair<Boolean, String>?>(null)
    var result: Pair<Boolean, String>? by resultCell
    private val busyCell = cells.cell(false)
    var busy: Boolean by busyCell

    override fun forget() {
        password = ""
        result = null
    }
}

/**
 * COV1-2 (audit 1.3.0): «Huella SHA-256» shows a fingerprint only for the file it names. Each pick [start]s a new run
 * (the screen cancels the one before); a result [finish]es only the current run, so a slow earlier file can no longer
 * put its fingerprint under a later file's name, nor end «Calculando…» while the current one is still going.
 */
class FileHashSession(cells: CellFactory = PlainCells) {
    private var run = 0L
    private val nameCell = cells.cell<String?>(null)
    var name: String? by nameCell
        private set
    private val hashCell = cells.cell<String?>(null)
    var hash: String? by hashCell
        private set
    private val busyCell = cells.cell(false)
    var busy: Boolean by busyCell
        private set

    /** M8 of the F6 review: the file picked last could not be read (the screen says so). */
    private val failedCell = cells.cell(false)
    var failed: Boolean by failedCell
        private set

    /**
     * A file was picked: the previous name and result go, and the returned run is the only one [named] and [finish]
     * accept. The name is read from the provider off the main thread, so it comes later ([named]).
     */
    fun start(): Long {
        run++
        name = null
        hash = null
        failed = false
        busy = true
        return run
    }

    /** The picked file's name, for [run] only. */
    fun named(run: Long, name: String): Boolean {
        if (run != this.run) return false
        this.name = name
        return true
    }

    /** [hash] (null: it could not be read) for [run]; false, and nothing changes, if another file was picked since. */
    fun finish(run: Long, hash: String?): Boolean {
        if (run != this.run) return false
        this.hash = hash
        failed = hash == null
        busy = false
        return true
    }
}
