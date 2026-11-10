package app.seguridad.tools.qr

import app.seguridad.core.drawsNothing
import app.seguridad.crypto.withoutBidiControls

/**
 * [text] with every character that cannot be seen, or that changes how the text reads, replaced by a visible mark
 * «⟨U+XXXX⟩» (at least four upper-case hex digits). The inspector never removes them: it points them out, because a QR
 * that carries them is trying to look like something else.
 *
 * Marked (rulings R46 and R49): everything the shared list says draws nothing (`drawsNothing`, `core/Invisible.kt`, the
 * one the vault's lists, the import and the names of files use: the default ignorables of Unicode, such as U+00AD,
 * U+034F, the Hangul fillers, U+17B4/U+17B5, U+180B–U+180F, U+200B–U+200F, the bidi controls, U+2060–U+206F, the
 * variation selectors, U+FEFF, U+FFF0–U+FFF8 and plane 14; and U+2800, U+FFF9–U+FFFB and U+13430–U+1343F), plus what
 * only the inspector marks: the bidi controls of [withoutBidiControls]'s own set, the line and paragraph separators
 * U+2028 and U+2029 and the ISO control characters except `'\n'`. Works on code points, so a supplementary character is
 * one mark, with five or six digits. A prepended concatenation mark (U+0600–U+0605, U+06DD…) is drawn, so it is not.
 *
 * Exception, checked first: U+FE0E and U+FE0F right after an emoji or symbol are its normal presentation (a «❤️» in a
 * network name), not marked; so is U+FE0F inside a keycap, between 0–9, # or * and U+20E3 («1️⃣», MN, T6 of §2.5), the
 * rule `safeFileName` keeps it by (CIFRADO-2). After anything else they are.
 */
fun visibleText(text: String): String {
    val sb = StringBuilder(text.length + 16)
    var i = 0
    var previous = -1
    while (i < text.length) {
        val cp = text.codePointAt(i)
        if (isHiddenCodePoint(cp, previous, nextCodePoint(text, i, cp), true)) sb.append(mark(cp)) else sb.appendCodePoint(cp)
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
        if (isHiddenCodePoint(cp, previous, nextCodePoint(text, i, cp), allowEmojiVariation)) return true
        previous = cp
        i += Character.charCount(cp)
    }
    return false
}

/**
 * The marks [visibleText] puts in [text], each once, in the order they first appear: what the screen names in
 * «marcados como ⟨U+202E⟩». Empty exactly when [hasHiddenChars] (with the emoji exception) is false.
 */
fun hiddenMarks(text: String): List<String> {
    val marks = LinkedHashSet<String>()
    var i = 0
    var previous = -1
    while (i < text.length) {
        val cp = text.codePointAt(i)
        if (isHiddenCodePoint(cp, previous, nextCodePoint(text, i, cp), true)) marks += mark(cp)
        previous = cp
        i += Character.charCount(cp)
    }
    return marks.toList()
}

private fun mark(cp: Int) = "\u27E8U+" + String.format(java.util.Locale.ROOT, "%04X", cp) + "\u27E9"

/**
 * An emoji or symbol that U+FE0E and U+FE0F may follow: the emoji and symbol plane (U+1F000 to U+1FFFF) or a symbol of
 * the BMP. Nothing above that plane: the tag characters, the supplementary selectors and plane 2 ideographs are not.
 */
private fun isEmojiOrSymbol(cp: Int): Boolean =
    cp in 0x1F000..0x1FFFF || (cp <= 0xFFFF && Character.getType(cp) == Character.OTHER_SYMBOL.toInt())

/** The code point after [cp] at [i] in [text], or -1 at the end. */
private fun nextCodePoint(text: String, i: Int, cp: Int): Int {
    val j = i + Character.charCount(cp)
    return if (j < text.length) text.codePointAt(j) else -1
}

/** U+FE0F between 0–9, # or * and U+20E3: the keycap emoji, the same fixed rule as `safeFileName` (CIFRADO-2). */
private fun isKeycapSelector(cp: Int, previous: Int, next: Int): Boolean =
    cp == 0xFE0F && next == 0x20E3 && (previous in '0'.code..'9'.code || previous == '#'.code || previous == '*'.code)

private fun isHiddenCodePoint(cp: Int, previous: Int, next: Int, allowEmojiVariation: Boolean): Boolean = when {
    cp == '\n'.code -> false
    (cp == 0xFE0E || cp == 0xFE0F) && allowEmojiVariation && previous >= 0 && isEmojiOrSymbol(previous) -> false
    allowEmojiVariation && isKeycapSelector(cp, previous, next) -> false
    // The one list of what draws nothing (R46): what the lists drop, the inspector marks.
    drawsNothing(cp) -> true
    cp == 0x2028 || cp == 0x2029 -> true
    Character.isISOControl(cp) -> true
    // The bidi set lives in one place; asking it keeps this list from drifting from it.
    cp <= 0xFFFF -> withoutBidiControls(cp.toChar().toString()).isEmpty()
    else -> false
}
