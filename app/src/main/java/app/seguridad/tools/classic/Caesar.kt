package app.seguridad.tools.classic

/**
 * Caesar cipher over the 26 letters A-Z. [shift] may be any Int (it is taken modulo 26, so a negative one decrypts).
 * Case is kept («a» stays lowercase); every other character, such as «ñ», an accented letter, a digit or a space, passes
 * through unchanged. A historical cipher: it protects nothing.
 */
fun caesar(text: String, shift: Int): String {
    val k = Math.floorMod(shift, 26)
    val out = StringBuilder(text.length)
    for (c in text) out.append(shiftLetter(c, k))
    return out.toString()
}

/** [c] moved [k] places along the alphabet if it is an ASCII letter (keeping its case), else [c] itself. */
internal fun shiftLetter(c: Char, k: Int): Char = when (c) {
    in 'A'..'Z' -> 'A' + (c - 'A' + k) % 26
    in 'a'..'z' -> 'a' + (c - 'a' + k) % 26
    else -> c
}
