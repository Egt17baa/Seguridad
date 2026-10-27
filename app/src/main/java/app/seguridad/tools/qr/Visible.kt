package app.seguridad.tools.qr

import app.seguridad.crypto.withoutBidiControls

/**
 * [text] with every character that cannot be seen, or that changes how the text reads, replaced by a visible mark
 * «⟨U+XXXX⟩» (at least four upper-case hex digits). The inspector never removes them: it points them out, because a QR
 * that carries them is trying to look like something else.
 *
 * Marked: the bidi controls ([withoutBidiControls]'s own set), U+200B to U+200D, U+2060 to U+2064, U+FEFF, the soft
 * hyphen U+00AD, the Hangul fillers (U+115F, U+1160, U+3164, U+FFA0), U+180E, the line and paragraph separators
 * U+2028 and U+2029, the variation selectors (U+FE00 to U+FE0F, U+E0100 to U+E01EF), the tag characters U+E0000 to
 * U+E007F (invisible, used to smuggle text) and the ISO control characters except `'\n'`. Works on code points, so a
 * supplementary character is one mark, with five or six digits.
 *
 * Exception: U+FE0E and U+FE0F right after an emoji or symbol are its normal presentation (a «❤️» in a network name),
 * not marked; after anything else they are.
 */
fun visibleText(text: String): String {
    val sb = StringBuilder(text.length + 16)
    var i = 0
    var previous = -1
    while (i < text.length) {
        val cp = text.codePointAt(i)
        if (isHiddenCodePoint(cp, previous, true)) sb.append("\u27E8U+").append(String.format(java.util.Locale.ROOT, "%04X", cp)).append('\u27E9')
        else sb.appendCodePoint(cp)
        previous = cp
        i += Character.charCount(cp)
    }
    return sb.toString()
}

/**
 * True if [text] has something [visibleText] would mark. With [allowEmojiVariation] false, U+FE0E and U+FE0F always
 * count: in a host name a browser drops them, so `❤️.ws` and `❤.ws` are the same name.
 */
fun hasHiddenChars(text: String, allowEmojiVariation: Boolean = true): Boolean {
    var i = 0
    var previous = -1
    while (i < text.length) {
        val cp = text.codePointAt(i)
        if (isHiddenCodePoint(cp, previous, allowEmojiVariation)) return true
        previous = cp
        i += Character.charCount(cp)
    }
    return false
}

private fun isEmojiOrSymbol(cp: Int): Boolean =
    cp >= 0x1F000 || Character.getType(cp) == Character.OTHER_SYMBOL.toInt()

private fun isHiddenCodePoint(cp: Int, previous: Int, allowEmojiVariation: Boolean): Boolean = when {
    cp == '\n'.code -> false
    (cp == 0xFE0E || cp == 0xFE0F) && allowEmojiVariation && previous >= 0 && isEmojiOrSymbol(previous) -> false
    cp in 0x200B..0x200D || cp in 0x2060..0x2064 || cp == 0xFEFF -> true
    cp == 0x00AD || cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0 || cp == 0x180E -> true
    cp == 0x2028 || cp == 0x2029 -> true
    cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF -> true
    cp in 0xE0000..0xE007F -> true
    Character.isISOControl(cp) -> true
    // The bidi set lives in one place; asking it keeps this list from drifting from it.
    cp <= 0xFFFF -> withoutBidiControls(cp.toChar().toString()).isEmpty()
    else -> false
}
