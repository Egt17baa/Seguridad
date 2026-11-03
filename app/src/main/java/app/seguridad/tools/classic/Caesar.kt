package app.seguridad.tools.classic

/**
 * Caesar cipher over the 26 letters A-Z. [shift] may be any Int (it is taken modulo 26, so a negative one decrypts).
 * Case is kept («a» stays lowercase); every other character, such as «ñ», an accented letter, a digit or a space, passes
 * through unchanged. The text is first put in NFC, so a letter typed as base + combining accent (as macOS and iOS paste it)
 * is the same «é» and passes unchanged (a letter with no precomposed form keeps its base letter A-Z, which changes). A historical cipher: it protects nothing.
 */
fun caesar(text: String, shift: Int): String {
    val k = Math.floorMod(shift, 26)
    val composed = nfc(text)
    val out = StringBuilder(composed.length)
    for (c in composed) out.append(shiftLetter(c, k))
    return out.toString()
}

/** [c] moved [k] places along the alphabet if it is an ASCII letter (keeping its case), else [c] itself. */
internal fun shiftLetter(c: Char, k: Int): Char = when (c) {
    in 'A'..'Z' -> 'A' + (c - 'A' + k) % 26
    in 'a'..'z' -> 'a' + (c - 'a' + k) % 26
    else -> c
}

/** [text] in Unicode NFC form (decomposed accents joined), so «é» is one character whichever way it was typed. */
internal fun nfc(text: String): String = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
