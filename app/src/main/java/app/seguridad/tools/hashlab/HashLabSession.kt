package app.seguridad.tools.hashlab

import app.seguridad.tools.cipher.FileHashSession
import app.seguridad.tools.cipher.GuardedSession
import app.seguridad.tools.codes.CellFactory
import app.seguridad.tools.codes.PlainCells
import app.seguridad.tools.codes.getValue
import app.seguridad.tools.codes.setValue

/**
 * What the «Laboratorio de hashes» holds that can be a secret: the text, the HMAC key and message, the text to encode
 * or decode and the pasted hash, and the File tab's name and result. On Android it lives in the screen's ViewModel
 * (never in saved state); [guard] applies the «PIN y códigos» rule as in «Cifrar texto»: back after
 * `max(lock time, 60 s)` away, another app with the focus, or a lock of the vault, and [wipe] runs. The tab and the
 * algorithm ([algo]) are not secrets and stay; the chosen file goes with the rest, with its result, its size, how much
 * of it was read and the algorithm of that result (the name of a file can say something).
 */
class HashLabSession(
    cells: CellFactory = PlainCells,
    createdAt: Long = 0L,
    private val onWipe: () -> Unit = {},
) : GuardedSession(createdAt) {
    private val textCell = cells.cell("")
    var text: String by textCell
    private val expectedCell = cells.cell("")
    var expected: String by expectedCell
    private val hmacKeyCell = cells.cell("")
    var hmacKey: String by hmacKeyCell
    private val hmacMessageCell = cells.cell("")
    var hmacMessage: String by hmacMessageCell
    private val codecInputCell = cells.cell("")
    var codecInput: String by codecInputCell

    /** The File tab's name and result. */
    val file = FileHashSession(cells)

    /**
     * What was chosen (the screen keeps its URI here), so «Calcular otra vez» can read it again; null once wiped or
     * cancelled.
     */
    private val sourceCell = cells.cell<Any?>(null)
    var source: Any? by sourceCell

    /** The algorithm chosen in the tabs that hash (not a secret: it stays). */
    private val algoCell = cells.cell(HashAlgo.SHA256)
    var algo: HashAlgo by algoCell

    /** The algorithm of the result [file] holds (or of the reading going on). */
    private val fileAlgoCell = cells.cell(HashAlgo.SHA256)
    var fileAlgo: HashAlgo by fileAlgoCell
        private set

    /** How much of the file was read so far, for the progress line. */
    private val readBytesCell = cells.cell(0L)
    var readBytes: Long by readBytesCell
        private set

    /** The file's size as its provider gives it; -1 when unknown. */
    private val totalBytesCell = cells.cell(-1L)
    var totalBytes: Long by totalBytesCell
        private set

    /**
     * A file is read with [algo]: the previous result, size and progress go ([FileHashSession.start]), and the returned
     * run is the only one [sized], [progress] and [FileHashSession.finish] accept.
     */
    fun startFile(): Long = synchronized(fileState) {
        // The new run first, so a reading of the old one can no longer write; then its figures go (re-review B, N-2).
        val run = file.start()
        fileAlgo = algo
        readBytes = 0
        totalBytes = -1
        run
    }

    /** The size of [run]'s file; false, and nothing changes, once another file was picked, or after a cancel or a wipe. */
    fun sized(run: Long, size: Long): Boolean = synchronized(fileState) {
        if (!file.isCurrent(run)) return false
        totalBytes = size
        true
    }

    /**
     * How much of [run]'s file was read (called from the reading's thread); false as [sized]. The check and the write go
     * under the same lock as [startFile] and [forget], so an old reading never leaves its figure on a new file.
     */
    fun progress(run: Long, read: Long): Boolean = synchronized(fileState) {
        if (!file.isCurrent(run)) return false
        readBytes = read
        true
    }

    /** Guards the file's run check and its figures ([readBytes], [totalBytes], [fileAlgo]) against the reading's thread. */
    private val fileState = Any()

    override fun forget() {
        onWipe() // the screen stops the file run going on; outside the lock, so it never waits for the reading
        synchronized(fileState) {
            file.clear()
            fileAlgo = algo
            readBytes = 0
            totalBytes = -1
        }
        source = null
        text = ""
        expected = ""
        hmacKey = ""
        hmacMessage = ""
        codecInput = ""
    }
}
