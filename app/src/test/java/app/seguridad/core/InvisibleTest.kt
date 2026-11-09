package app.seguridad.core

import app.seguridad.crypto.isHiddenInName
import app.seguridad.crypto.safeFileName
import app.seguridad.imports.compareKey
import app.seguridad.imports.plainReportText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RT-6, RT-9 and RT-13 (rulings R43 and R49): one list of what draws nothing, for what is shown, what is compared and the names of files. */
class InvisibleTest {
    private fun s(cp: Int) = String(Character.toChars(cp))

    private fun cpName(cp: Int) = "U+%04X".format(cp)

    /** Prepended_Concatenation_Mark of Unicode 15.0 (PropList.txt): Cf, and drawn (the one list, ruling R52). */
    private val prependedMarks = VISIBLE_PREPENDED_MARKS.sorted()

    @Test fun theListOfPrependedMarksIsUnicodes() {
        assertEquals(((0x0600..0x0605) + listOf(0x06DD, 0x070F, 0x0890, 0x0891, 0x08E2, 0x110BD, 0x110CD)).map(::cpName), prependedMarks.map(::cpName))
    }

    @Test fun theCodePointsOfRt6DrawNothing() {
        for (cp in listOf(0x2800, 0x17B4, 0x17B5, 0x180B, 0x180C, 0x180D, 0x180E, 0x180F, 0xFE00, 0xFE0E, 0xFE0F, 0xE0100, 0xE01EF)) {
            assertTrue("U+%04X".format(cp), drawsNothing(cp))
        }
        // And the ones that were already dropped: Cf, plane 14, CGJ, the Hangul fillers, the interlinear annotations.
        for (cp in listOf(0x200B, 0x200D, 0x202E, 0x2060, 0xFEFF, 0x00AD, 0x061C, 0xE0001, 0xE0041, 0xE0FFF, 0x034F, 0x115F, 0x1160, 0x3164, 0xFFA0, 0xFFF9, 0xFFFB)) {
            assertTrue("U+%04X".format(cp), drawsNothing(cp))
        }
    }

    @Test fun whatIsSeenIsNotInTheList() {
        for (t in listOf("a", "Z", "0", " ", " ", "　", "ñ", "́", "⃣", "€", "¡", "😀", "❤", "文", "а", "\u0000", " ", "￼")) {
            assertFalse("«$t»", drawsNothing(t.codePointAt(0)))
        }
        // RT-13 (ruling R49): the prepended concatenation marks are Cf but have a glyph, so they are not in the list.
        for (cp in prependedMarks) assertFalse(cpName(cp), drawsNothing(cp))
    }

    /** RT-9 (ruling R49): the unassigned default ignorables draw nothing (HarfBuzz hides them), as the names drop them. */
    @Test fun theUnassignedDefaultIgnorablesDrawNothing() {
        for (cp in listOf(0x2065) + (0xFFF0..0xFFF8)) {
            assertTrue(cpName(cp), drawsNothing(cp))
            assertEquals(cpName(cp), "Banco", plainReportText("Ban${s(cp)}co"))
            assertEquals(cpName(cp), "Banco", compareKey("Banco${s(cp)}"))
        }
        // The Egyptian hieroglyph format controls are Cf without a glyph of their own, and stay in the list.
        for (cp in 0x13430..0x1343F) assertTrue(cpName(cp), drawsNothing(cp))
    }

    /**
     * RT-13 (ruling R49): a prepended concatenation mark is shown and compared, as it draws. A file name still drops it
     * (ruling R52, CIFRADO-2): it spans and reshapes the digits after it, and a name is the safe side.
     */
    @Test fun thePrependedConcatenationMarksAreShown() {
        for (cp in prependedMarks) {
            val c = s(cp)
            assertEquals(cpName(cp), "Banco ${c}1", plainReportText("Banco ${c}1"))
            assertTrue(cpName(cp), c in compareKey("Banco ${c}1"))
            assertTrue(cpName(cp), compareKey("Banco ${c}1") != compareKey("Banco 1"))
            assertEquals(cpName(cp), "ab", safeFileName("a${c}b"))
            assertTrue(cpName(cp), isHiddenInName(cp))
        }
    }

    /**
     * Ruling R49: every format character of the JDK's Unicode (Cf) is in the list or is a prepended concatenation mark,
     * and the list differs from the one before R49 (Cf, plane 14 and the fixed ranges of RT-6) by exactly U+2065 and
     * U+FFF0–U+FFF8 more and the prepended marks fewer.
     */
    @Test fun theListIsTheDefaultIgnorablesWithTheDecidedInvisibles() {
        val before = { cp: Int ->
            Character.getType(cp) == Character.FORMAT.toInt() || cp in 0xE0000..0xE0FFF ||
                cp == 0x034F || cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0 ||
                cp == 0x17B4 || cp == 0x17B5 || cp in 0x180B..0x180F || cp == 0x2800 || cp in 0xFE00..0xFE0F || cp in 0xFFF9..0xFFFB
        }
        val gained = mutableListOf<Int>()
        val lost = mutableListOf<Int>()
        val formatOutside = mutableListOf<Int>()
        for (cp in 0..0x10FFFF) {
            if (drawsNothing(cp) && !before(cp)) gained += cp
            if (!drawsNothing(cp) && before(cp)) lost += cp
            if (Character.getType(cp) == Character.FORMAT.toInt() && !drawsNothing(cp) && cp !in prependedMarks) formatOutside += cp
        }
        assertEquals((listOf(0x2065) + (0xFFF0..0xFFF8)).map(::cpName), gained.map(::cpName))
        assertEquals(prependedMarks.sorted().map(::cpName), lost.map(::cpName))
        assertTrue(formatOutside.joinToString { cpName(it) }, formatOutside.isEmpty())
    }

    /**
     * Ruling R52: what a file name drops is, code point by code point over the whole of Unicode, what it dropped before
     * R49 (Cf, plane 14 and the fixed ranges of RT-6, plus the card list and the noncharacters), so every name comes out
     * as it did.
     */
    @Test fun theNamesDropWhatTheyDroppedBefore() {
        val before = { cp: Int ->
            cp != ZERO_WIDTH_JOINER && cp != EMOJI_SELECTOR && (
                Character.getType(cp) == Character.FORMAT.toInt() || cp in 0xE0000..0xE0FFF ||
                    cp == 0x034F || cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0 ||
                    cp == 0x17B4 || cp == 0x17B5 || cp in 0x180B..0x180F || cp == 0x2800 || cp in 0xFE00..0xFE0F ||
                    cp in 0xFFF9..0xFFFB ||
                    app.seguridad.crypto.pk.isForbiddenNameCodePoint(cp) || cp in 0xFDD0..0xFDEF || (cp and 0xFFFE) == 0xFFFE
                )
        }
        val changed = (0..0x10FFFF).filter { isHiddenInName(it) != before(it) }
        assertTrue("changed: " + changed.take(20).joinToString { cpName(it) }, changed.isEmpty())
    }

    /**
     * Ruling R49, the other direction: whatever a file name drops is dropped from what is shown and from the key too,
     * except what each shows on purpose: a control and U+2028/U+2029 (a space), private use and the noncharacters
     * (drawn as a box), and the prepended concatenation marks, which a name drops and the lists show (ruling R52).
     */
    @Test fun whatANameDropsIsDroppedFromWhatIsShownAndCompared() {
        val shown = mutableListOf<Int>()
        for (cp in 0..0x10FFFF) {
            if (cp in 0xD800..0xDFFF || !isHiddenInName(cp) && cp != ZERO_WIDTH_JOINER && cp != EMOJI_SELECTOR) continue
            if (Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029) continue
            if (Character.getType(cp) == Character.PRIVATE_USE.toInt() || cp in 0xFDD0..0xFDEF || (cp and 0xFFFE) == 0xFFFE) continue
            if (cp in prependedMarks) continue
            val c = s(cp)
            if (plainReportText("a${c}b") != "ab" || compareKey("Ban${c}co") != "Banco" || !drawsNothing(cp)) shown += cp
        }
        assertTrue("dropped from names, shown or compared: " + shown.take(20).joinToString { cpName(it) }, shown.isEmpty())
    }

    @Test fun whatIsShownWhatIsComparedAndTheNamesOfFilesAgree() {
        val shownApart = mutableListOf<Int>()
        val comparedApart = mutableListOf<Int>()
        val inNames = mutableListOf<Int>()
        val notInTheList = mutableListOf<Int>()
        for (cp in 0..0x10FFFF) {
            if (cp in 0xD800..0xDFFF) continue
            val c = s(cp)
            val shown = plainReportText("a${c}b")
            if (drawsNothing(cp)) {
                // After an ASCII letter even U+FE0F shows nothing, so every one of them goes.
                if (shown != "ab") shownApart += cp
                if (compareKey("Ban${c}co") != "Banco") comparedApart += cp
                // A name drops them all too: the joiner and U+FE0F by their own rules (next to ASCII they go).
                if (cp == ZERO_WIDTH_JOINER || cp == EMOJI_SELECTOR) {
                    if (safeFileName("a${c}b") != "ab") inNames += cp
                } else if (!isHiddenInName(cp)) {
                    inNames += cp
                }
            } else if (shown == "ab") {
                // What is shown drops nothing that is not in the list (controls become spaces, they are not dropped).
                notInTheList += cp
            }
        }
        for ((what, list) in listOf("shown" to shownApart, "compared apart" to comparedApart, "kept in a name" to inNames, "dropped, not in the list" to notInTheList)) {
            assertTrue("$what: " + list.take(20).joinToString { "U+%04X".format(it) }, list.isEmpty())
        }
    }

    @Test fun anEmojiSelectorStaysInWhatIsShownExactlyWhereItStaysInAName() {
        val befores = listOf("❤", "😀", "☎", "a", " ", "é", "1", "#", "*", "‍", "️", "")
        val afters = listOf("⃣", "x", "😀", "")
        for (before in befores) for (after in afters) {
            val text = "${before}️$after"
            val inName = safeFileName("q${text}q").contains('️')
            val shown = plainReportText("q${text}q").contains('️')
            assertEquals("«$before» U+FE0F «$after»", inName, shown)
        }
    }
}
