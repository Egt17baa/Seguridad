package app.seguridad.tools.hashlab

import app.seguridad.tools.cipher.FileHashSession
import app.seguridad.tools.cipher.GuardedSession
import app.seguridad.tools.codes.CellFactory
import app.seguridad.tools.codes.PlainCells
import app.seguridad.tools.codes.getValue
import app.seguridad.tools.codes.setValue

/**
 * What the «Laboratorio de hashes» holds that can be a secret: the text, the HMAC key and message, the text to encode or
 * decode and the pasted hash, and the File tab's name and result. On Android it lives in the screen's ViewModel (never in saved state); [guard] applies the
 * «PIN y códigos» rule as in «Cifrar texto»: back after `max(lock time, 60 s)` away, another app with the focus, or a lock of
 * the vault, and [wipe] runs. The tab, the algorithm and the chosen file are not secrets and stay.
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

    /** What was chosen (the screen keeps its URI here), so «Calcular otra vez» can read it again; null once wiped or cancelled. */
    private val sourceCell = cells.cell<Any?>(null)
    var source: Any? by sourceCell

    override fun forget() {
        onWipe() // the screen stops the file run going on
        file.clear()
        source = null
        text = ""
        expected = ""
        hmacKey = ""
        hmacMessage = ""
        codecInput = ""
    }
}
