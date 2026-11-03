package app.seguridad.tools.classic

import app.seguridad.tools.cipher.GuardedSession
import app.seguridad.tools.codes.CellFactory
import app.seguridad.tools.codes.PlainCells
import app.seguridad.tools.codes.getValue
import app.seguridad.tools.codes.setValue

/** The longest text the lab takes in a field (a paste over it is refused, not cut), so a huge paste cannot freeze the screen. */
const val MAX_CLASSIC_CHARS = 20_000

/** The shorter fields: a key, a shift, rings, positions and plugs. */
const val MAX_CLASSIC_KEY_CHARS = 200

const val DEFAULT_SHIFT = "3"
const val DEFAULT_RINGS = "1 1 1"
const val DEFAULT_POSITIONS = "AAA"

/**
 * What the «Laboratorio clásico» holds that was typed: the texts, the shift, the Vigenère key and the Enigma rings,
 * positions and plugs. On Android it lives in the screen's ViewModel (never in saved state); [guard] applies the
 * «PIN y códigos» rule as in «Cifrar texto» and [wipe] puts every field back to empty or to its starting value. The tab,
 * the mode and the rotors and reflector, which are chosen and not typed, are not here and stay.
 */
class ClassicLabSession(cells: CellFactory = PlainCells, createdAt: Long = 0L) : GuardedSession(createdAt) {
    private val caesarTextCell = cells.cell("")
    var caesarText: String by caesarTextCell
    private val shiftCell = cells.cell(DEFAULT_SHIFT)
    var shift: String by shiftCell
    private val vigenereTextCell = cells.cell("")
    var vigenereText: String by vigenereTextCell
    private val vigenereKeyCell = cells.cell("")
    var vigenereKey: String by vigenereKeyCell
    private val enigmaTextCell = cells.cell("")
    var enigmaText: String by enigmaTextCell
    private val ringsCell = cells.cell(DEFAULT_RINGS)
    var rings: String by ringsCell
    private val positionsCell = cells.cell(DEFAULT_POSITIONS)
    var positions: String by positionsCell
    private val plugsCell = cells.cell("")
    var plugs: String by plugsCell
    private val analysisTextCell = cells.cell("")
    var analysisText: String by analysisTextCell

    override fun forget() {
        caesarText = ""
        shift = DEFAULT_SHIFT
        vigenereText = ""
        vigenereKey = ""
        enigmaText = ""
        rings = DEFAULT_RINGS
        positions = DEFAULT_POSITIONS
        plugs = ""
        analysisText = ""
    }
}
