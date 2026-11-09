package app.seguridad.core

/*
 * The one list of code points that draw nothing (ruling R43, RT-6 of the second attack): what the import report and the
 * lists show (`plainReportText`), what the merge compares (`compareKey`) and the file names that are shown or saved
 * (`isHiddenInName`) all take it out, so the three agree on what is invisible. Fixed ranges, plus the format category:
 * no other Unicode table that changes with the JDK or the Android version.
 */

/** The zero-width joiner. Each caller decides on it: a name keeps it between two emoji, what is shown drops it. */
internal const val ZERO_WIDTH_JOINER = 0x200D

/** The emoji presentation selector. It draws nothing by itself; after an emoji it picks the emoji look ([emojiSelectorShows]). */
internal const val EMOJI_SELECTOR = 0xFE0F

private const val KEYCAP = 0x20E3

/**
 * Whether [cp] draws nothing, whatever is around it:
 * - every format character (category Cf): the bidi controls, U+00AD, U+061C, U+180E, U+200B–U+200F, U+2060–U+206F,
 *   U+FEFF, U+FFF9–U+FFFB, the tag characters, U+200D too;
 * - the whole of plane 14, U+E0000–U+E0FFF: the tag block (with its unassigned code points) and the supplementary
 *   variation selectors U+E0100–U+E01EF;
 * - the combining grapheme joiner U+034F and the Hangul fillers U+115F, U+1160, U+3164 and U+FFA0;
 * - the Khmer inherent vowels U+17B4 and U+17B5, and the Mongolian free variation selectors U+180B–U+180D and U+180F;
 * - the Braille pattern blank U+2800, a symbol that is drawn as an empty cell;
 * - the variation selectors U+FE00–U+FE0F. U+FE0F after an emoji is its normal look: the callers that keep it there ask
 *   [emojiSelectorShows].
 * It does not cover look-alike letters of other scripts, nor the spaces (they are visible as a gap), nor the controls
 * (Cc), which each caller already turns into a space or drops.
 */
internal fun drawsNothing(cp: Int): Boolean =
    Character.getType(cp) == Character.FORMAT.toInt() ||
        cp in 0xE0000..0xE0FFF ||
        cp == 0x034F || cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0 ||
        cp == 0x17B4 || cp == 0x17B5 || cp in 0x180B..0x180F ||
        cp == 0x2800 ||
        cp in 0xFE00..0xFE0F ||
        cp in 0xFFF9..0xFFFB

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
