package app.seguridad.tools.classic

/** What the Enigma form says about what was typed: the [settings] if it is valid, else the [error] to show (never both). */
class EnigmaForm(val settings: EnigmaSettings?, val error: String?)

private fun isAsciiLetter(c: Char) = c in 'A'..'Z' || c in 'a'..'z'

private val SEPARATORS = Regex("[\\s,;]+")

/**
 * Builds the key from what was typed: [rings] as three integers, [positions] as three letters (case does not matter,
 * spaces are ignored) and [plugs] as pairs of letters separated by spaces. Rotors that repeat give the first error found.
 */
fun buildEnigma(rotors: List<Rotor>, reflector: Reflector, rings: String, positions: String, plugs: String): EnigmaForm {
    if (rotors.toSet().size != rotors.size) return EnigmaForm(null, ENIGMA_REPEATED_ROTOR)
    val ringNumbers = rings.trim().split(SEPARATORS).map { t -> if (t.length in 1..2 && t.all { it in '0'..'9' }) t.toInt() else null }
    if (ringNumbers.size != 3 || ringNumbers.any { it == null || it !in 1..26 }) return EnigmaForm(null, ENIGMA_RINGS_INVALID)
    val typedStart = positions.filterNot { it.isWhitespace() }
    if (typedStart.length != 3 || typedStart.any { !isAsciiLetter(it) }) return EnigmaForm(null, ENIGMA_POSITIONS_INVALID)
    val start = typedStart.uppercase()
    val tokens = plugs.trim().split(SEPARATORS).filter { it.isNotEmpty() }
    val pairs = tokens.map { t ->
        // The raw characters are checked: Unicode case mapping would turn «ﬁ» into FI and «ß» into SS.
        if (t.length != 2 || t.any { !isAsciiLetter(it) }) return EnigmaForm(null, ENIGMA_PLUG_FORMAT)
        val u = t.uppercase()
        if (u[0] == u[1]) return EnigmaForm(null, ENIGMA_PLUG_FORMAT)
        u[0] to u[1]
    }
    if (pairs.size > 13) return EnigmaForm(null, ENIGMA_PLUG_TOO_MANY)
    if (pairs.flatMap { listOf(it.first, it.second) }.let { it.size != it.toSet().size }) return EnigmaForm(null, ENIGMA_PLUG_REPEATED)
    return EnigmaForm(EnigmaSettings(rotors, reflector, ringNumbers.map { it!! }, start, pairs), null)
}
