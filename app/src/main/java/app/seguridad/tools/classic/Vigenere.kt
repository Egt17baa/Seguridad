package app.seguridad.tools.classic

/**
 * Vigenère cipher over A-Z. Only the letters A-Z of [key] count (case does not matter; the rest is ignored); a key with no
 * such letter throws [IllegalArgumentException]. The key advances only on letters of [text]: «ñ», accents, digits and spaces
 * pass through unchanged and do not use up a key letter. Case of the text is kept. A historical cipher: it protects nothing.
 */
fun vigenere(text: String, key: String, decrypt: Boolean): String {
    val shifts = key.filter { it in 'A'..'Z' || it in 'a'..'z' }.map { it.uppercaseChar() - 'A' }
    require(shifts.isNotEmpty()) { "key without letters" }
    val composed = nfc(text) // «é» typed as e + accent is one character and passes unchanged
    val out = StringBuilder(composed.length)
    var i = 0
    for (c in composed) {
        if (c in 'A'..'Z' || c in 'a'..'z') {
            val s = shifts[i % shifts.size]
            out.append(shiftLetter(c, if (decrypt) 26 - s else s))
            i++
        } else {
            out.append(c)
        }
    }
    return out.toString()
}
