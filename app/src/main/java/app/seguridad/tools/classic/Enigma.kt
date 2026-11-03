package app.seguridad.tools.classic

/** The five rotors of the Enigma I and M3, with the wiring and the notch letter of the historical documentation. */
enum class Rotor(val wiring: String, val notch: Char) {
    I("EKMFLGDQVZNTOWYHXUSPAIBRCJ", 'Q'),
    II("AJDKSIRUXBLHWTMCQGZNPYFVOE", 'E'),
    III("BDFHJLCPRTXVZNYEIWGAKMUSQO", 'V'),
    IV("ESOVPZJAYQUIRHXLNFTGKDCMWB", 'J'),
    V("VZBRGITYUPSDNHLXAWMJQOFECK", 'Z'),
}

/** The two reflectors (Umkehrwalze) B and C of the Enigma I and M3. */
enum class Reflector(val wiring: String) {
    B("YRUHQSLDPXNGOKMIEBFZCWVJAT"),
    C("FVPJIAOYEDRZXWGCTKUQSBNMHL"),
}

/**
 * The key of an Enigma I / M3: [rotors] and [rings] from left to right (slowest first), [rings] each 1..26 (Ringstellung),
 * [positions] the three letters showing in the windows (Grundstellung), and [plugs] the plugboard (at most 13 disjoint
 * pairs of the letters A-Z). Anything else throws [IllegalArgumentException].
 */
data class EnigmaSettings(
    val rotors: List<Rotor>,
    val reflector: Reflector,
    val rings: List<Int>,
    val positions: String,
    val plugs: List<Pair<Char, Char>>,
) {
    init {
        require(rotors.size == 3 && rotors.toSet().size == 3) { "three different rotors" }
        require(rings.size == 3 && rings.all { it in 1..26 }) { "rings 1..26" }
        require(positions.length == 3 && positions.all { it in 'A'..'Z' }) { "three letters A-Z" }
        require(plugs.size <= 13) { "at most 13 plugs" }
        val used = HashSet<Char>()
        for ((a, b) in plugs) {
            require(a in 'A'..'Z' && b in 'A'..'Z' && a != b) { "a plug joins two different letters A-Z" }
            require(used.add(a) && used.add(b)) { "a letter in one plug only" }
        }
    }
}

/**
 * An Enigma I / M3 written from its historical description. Every key press first steps the rotors (the right one always;
 * the middle one when the right one stands on its notch, and then, **double step**, also when the middle one itself stands
 * on its notch, taking the left one with it) and then sends the current through the plugboard, the three rotors, the
 * reflector, back through the rotors and the plugboard. So a letter never comes out as itself and the machine is its own
 * inverse: with the same key and start, encrypting the output gives the input back.
 *
 * Only A-Z count, with case folded: **a lowercase letter is read as its uppercase and comes out uppercase**. Any other
 * character («ñ», an accent, a digit, a space) passes through unchanged and the rotors do not step for it.
 */
class Enigma(private val settings: EnigmaSettings) {
    private val pos = IntArray(3) { settings.positions[it] - 'A' }
    private val ring = IntArray(3) { settings.rings[it] - 1 }
    private val forward = Array(3) { i -> IntArray(26) { settings.rotors[i].wiring[it] - 'A' } }
    private val backward = Array(3) { i -> IntArray(26).also { inv -> forward[i].forEachIndexed { x, y -> inv[y] = x } } }
    private val notch = IntArray(3) { settings.rotors[it].notch - 'A' }
    private val reflector = IntArray(26) { settings.reflector.wiring[it] - 'A' }
    private val plug = IntArray(26) { it }.also { p ->
        for ((a, b) in settings.plugs) {
            p[a - 'A'] = b - 'A'
            p[b - 'A'] = a - 'A'
        }
    }

    /** The three letters in the windows now, left to right. */
    val positions: String get() = String(CharArray(3) { 'A' + pos[it] })

    private fun step() {
        val middleOnNotch = pos[1] == notch[1]
        if (middleOnNotch) {
            pos[0] = (pos[0] + 1) % 26
            pos[1] = (pos[1] + 1) % 26
        } else if (pos[2] == notch[2]) {
            pos[1] = (pos[1] + 1) % 26
        }
        pos[2] = (pos[2] + 1) % 26
    }

    /** Through rotor [i] (0 left, 2 right) in one direction, with its position and ring. */
    private fun through(table: Array<IntArray>, i: Int, x: Int): Int {
        val shift = pos[i] - ring[i]
        val entered = Math.floorMod(x + shift, 26)
        return Math.floorMod(table[i][entered] - shift, 26)
    }

    /** Presses one key: the uppercase of [c] encrypted if it is a letter A-Z (either case), else [c] unchanged and no step. */
    fun press(c: Char): Char {
        val letter = when (c) {
            in 'A'..'Z' -> c - 'A'
            in 'a'..'z' -> c - 'a'
            else -> return c
        }
        step()
        var x = plug[letter]
        for (i in 2 downTo 0) x = through(forward, i, x)
        x = reflector[x]
        for (i in 0..2) x = through(backward, i, x)
        return 'A' + plug[x]
    }

    /** [press] for every character of [text] in order. */
    fun process(text: String): String {
        val out = StringBuilder(text.length)
        for (c in text) out.append(press(c))
        return out.toString()
    }
}
