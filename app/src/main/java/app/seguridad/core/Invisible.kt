package app.seguridad.core

/*
 * The one list of code points that draw nothing (rulings R43 and R49; RT-6 of the second attack, RT-9 and RT-13 of its
 * re-check): what the import report and the lists show (`plainReportText`), what the merge compares (`compareKey`), the
 * file names that are shown or saved (`isHiddenInName`) and what the QR Inspector marks (`visibleText`) all take it from
 * here, so they agree on what is invisible. Fixed data, no `Character.getType`: a category that changes with the JDK or
 * the Android version (ICU) would make two phones disagree.
 */

/** The zero-width joiner. Each caller decides on it: a name keeps it between two emoji, what is shown drops it. */
internal const val ZERO_WIDTH_JOINER = 0x200D

/** The emoji presentation selector. It draws nothing by itself; after an emoji it picks the emoji look ([emojiSelectorShows]). */
internal const val EMOJI_SELECTOR = 0xFE0F

private const val KEYCAP = 0x20E3

/**
 * Default_Ignorable_Code_Point of Unicode 15.0, the version of the JDK 21 that builds and tests the app (the ranges are
 * the same in Unicode 15.1 and 16.0, the ICU of Android 15 and 16), copied as data from
 * https://www.unicode.org/Public/15.0.0/ucd/DerivedCoreProperties.txt, with the adjacent ranges joined. Pairs of first
 * and last code point, in order.
 */
private val DEFAULT_IGNORABLE = intArrayOf(
    0x00AD, 0x00AD,
    0x034F, 0x034F,
    0x061C, 0x061C,
    0x115F, 0x1160,
    0x17B4, 0x17B5,
    0x180B, 0x180F,
    0x200B, 0x200F,
    0x202A, 0x202E,
    0x2060, 0x206F,
    0x3164, 0x3164,
    0xFE00, 0xFE0F,
    0xFEFF, 0xFEFF,
    0xFFA0, 0xFFA0,
    0xFFF0, 0xFFF8,
    0x1BCA0, 0x1BCA3,
    0x1D173, 0x1D17A,
    0xE0000, 0xE0FFF,
)

/** Whether [cp] is in [DEFAULT_IGNORABLE]: a binary search over its pairs. */
private fun isDefaultIgnorable(cp: Int): Boolean {
    var lo = 0
    var hi = DEFAULT_IGNORABLE.size / 2 - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        when {
            cp < DEFAULT_IGNORABLE[2 * mid] -> hi = mid - 1
            cp > DEFAULT_IGNORABLE[2 * mid + 1] -> lo = mid + 1
            else -> return true
        }
    }
    return false
}

/**
 * Whether [cp] draws nothing, whatever is around it (ruling R49):
 * - Unicode's Default_Ignorable_Code_Point ([DEFAULT_IGNORABLE]): U+00AD, the combining grapheme joiner U+034F, U+061C,
 *   the Hangul fillers U+115F, U+1160, U+3164 and U+FFA0, the Khmer inherent vowels U+17B4 and U+17B5, the Mongolian
 *   free variation selectors and vowel separator U+180B–U+180F, U+200B–U+200F (U+200D, the zero-width joiner, too), the
 *   bidi controls U+202A–U+202E and U+2066–U+2069, U+2060–U+206F (also U+2065, not assigned), the variation selectors
 *   U+FE00–U+FE0F, U+FEFF, U+FFF0–U+FFF8 (not assigned), U+1BCA0–U+1BCA3, U+1D173–U+1D17A and the whole of plane 14,
 *   U+E0000–U+E0FFF (the tags and the supplementary variation selectors U+E0100–U+E01EF);
 * - the invisibles already decided that are not default ignorable: the Braille pattern blank U+2800, a symbol drawn as
 *   an empty cell; the interlinear annotation marks U+FFF9–U+FFFB, which can hide the text of an annotation; and the
 *   other format characters (Cf) without a glyph, the Egyptian hieroglyph format controls U+13430–U+1343F.
 * Not in it: the prepended concatenation marks U+0600–U+0605, U+06DD, U+070F, U+0890, U+0891, U+08E2, U+110BD and
 * U+110CD, which are Cf but are drawn (Unicode leaves them out of the default ignorables), so they are shown (RT-13).
 * U+FE0F after an emoji is its normal look: the callers that keep it there ask [emojiSelectorShows]; the zero-width
 * joiner's own rule is each caller's. It does not cover look-alike letters of other scripts, nor the spaces (they are
 * visible as a gap), nor the controls (Cc), which each caller already turns into a space or drops.
 */
internal fun drawsNothing(cp: Int): Boolean =
    cp >= 0x00AD && (isDefaultIgnorable(cp) || cp == 0x2800) || cp in 0xFFF9..0xFFFB || cp in 0x13430..0x1343F

/** A code point a kept zero-width joiner may touch: not ASCII, not a space, not another joiner (an emoji, in practice). */
internal fun canJoin(cp: Int) =
    cp >= 0x80 && cp != ZERO_WIDTH_JOINER && !Character.isWhitespace(cp) && !Character.isSpaceChar(cp)

/**
 * Whether a U+FE0F after [previous] and before [next] (-1 at either end) can be seen: after a code point that [canJoin]
 * and is not another U+FE0F (an emoji or a symbol in practice, but also any other non-ASCII one, such as «é», the same
 * tolerance as the joiner's rule), or after 0–9, # or * when U+20E3 follows (a keycap emoji, «1️⃣»). A fixed rule, like
 * the joiner's: no Unicode tables that change with the JDK.
 */
internal fun emojiSelectorShows(previous: Int, next: Int) =
    (previous >= 0 && canJoin(previous) && previous != EMOJI_SELECTOR) ||
        ((previous in '0'.code..'9'.code || previous == '#'.code || previous == '*'.code) && next == KEYCAP)
