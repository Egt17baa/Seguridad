package app.seguridad.core

import app.seguridad.crypto.isHiddenInName
import app.seguridad.crypto.safeFileName
import app.seguridad.imports.compareKey
import app.seguridad.imports.plainReportText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RT-6 (ruling R43): one list of what draws nothing, for what is shown, what is compared and the names of files. */
class InvisibleTest {
    private fun s(cp: Int) = String(Character.toChars(cp))

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
