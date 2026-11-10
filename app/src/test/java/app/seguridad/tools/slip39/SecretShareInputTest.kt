package app.seguridad.tools.slip39

import app.seguridad.crypto.Bip39
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecretShareInputTest {
    private val bip39 = Bip39(
        listOf("src/main/assets", "../app/src/main/assets").map { File(it, "bip39_english.txt") }.first { it.exists() }.readLines().filter { it.isNotBlank() },
    )
    private val zeros12 = List(11) { "abandon" } + "about"

    @Test fun aPhraseGivesItsEntropyWhateverItsCaseAndSpacing() {
        val messy = "﻿ " + zeros12.joinToString("  \n") { it.uppercase() } + "\t"
        val input = splitInput(messy, isPhrase = true, bip39)
        assertNull(input.error)
        assertArrayEquals(ByteArray(16), input.secret)
    }

    /** Ruling R68: a phrase drops what draws nothing (the shared list), not only the format characters. */
    @Test fun aPhraseDropsWhatDrawsNothing() {
        for (hidden in listOf("\u200B", "\u3164", "\u2800", "\uFE00", "\u034F")) {
            val input = splitInput((listOf("aban${hidden}don") + zeros12.drop(1)).joinToString(" "), isPhrase = true, bip39)
            assertNull(hidden, input.error)
            assertArrayEquals(ByteArray(16), input.secret)
        }
    }

    @Test fun aWrongPhraseSaysWhy() {
        assertEquals(SHARE_PHRASE_COUNT, splitInput(zeros12.drop(1).joinToString(" "), true, bip39).error)
        assertEquals(sharePhraseUnknown(3), splitInput((zeros12.take(2) + "abandom" + zeros12.drop(3)).joinToString(" "), true, bip39).error)
        assertEquals(SHARE_PHRASE_CHECKSUM, splitInput(List(12) { "abandon" }.joinToString(" "), true, bip39).error)
        assertEquals(sharePhraseUnknown(1), splitInput("\uD800abandon " + zeros12.drop(1).joinToString(" "), true, bip39).error)
        assertEquals(SHARE_SECRET_TOO_LONG, splitInput("a ".repeat(600), true, bip39).error)
        assertEquals(SHARE_PHRASE_COUNT, splitInput("", true, bip39).error)
    }

    @Test fun hexIsSixteenToThirtyTwoBytesInAnEvenNumber() {
        assertArrayEquals(ByteArray(16) { 0xAB.toByte() }, splitInput("AB".repeat(16), false, bip39).secret)
        assertArrayEquals(ByteArray(18) { 1 }, splitInput("01 ".repeat(18), false, bip39).secret)
        for (bad in listOf("", "ab".repeat(15), "ab".repeat(17), "ab".repeat(33), "zz".repeat(16), "abc")) {
            assertEquals(bad.take(10), SHARE_HEX_INVALID, splitInput(bad, false, bip39).error)
        }
    }

    @Test fun theSchemeNeedsNumbersFromOneToSixteen() {
        assertEquals(1 to listOf(GroupSpec(2, 3)), schemeOf("1", listOf(GroupForm("2", "3"))))
        assertEquals(2 to listOf(GroupSpec(2, 3), GroupSpec(1, 1)), schemeOf(" 2 ", listOf(GroupForm("2", "3"), GroupForm("1", "1"))))
        for (bad in listOf("", "0", "17", "-1", "x", "1.5", "016")) {
            assertNull(bad, schemeOf(bad, listOf(GroupForm("2", "3"))))
            assertNull(bad, schemeOf("1", listOf(GroupForm(bad, "3"))))
            assertNull(bad, schemeOf("1", listOf(GroupForm("1", bad))))
        }
    }

    @Test fun partsAreTheLinesWithText() {
        assertEquals(listOf("a b", "c d"), partsOf("\n a b \r\n\n   \nc d\n").map { it.trim() })
        assertEquals(emptyList<String>(), partsOf("  \n\t\n"))
    }

    @Test fun aRecoveredSecretIsAlsoShownAsAPhraseWhenItCanBeOne() {
        assertEquals(zeros12.joinToString(" "), phraseOrNull(bip39, ByteArray(16)))
        assertEquals(24, phraseOrNull(bip39, ByteArray(32))!!.split(" ").size)
        assertNull(phraseOrNull(bip39, ByteArray(18)))
        assertNull(phraseOrNull(bip39, ByteArray(64)))
    }

    // Owner's decision for task 4, (a): every raw text is measured before anything is parsed, split or copied.

    @Test fun anOversizedCombineIsRefusedBeforeAnyWork() {
        val huge = "academic ".repeat(1_200_000) // about 10 MB
        val started = System.nanoTime()
        val r = combineRequest(huge, "")
        assertEquals(SHARE_PARTS_TOO_LONG, r.error)
        assertNull(r.passphrase)
        assertEquals(emptyList<String>(), r.parts)
        val p = combineRequest("academic acid", "x".repeat(1_000_000))
        assertEquals(slip39PassphraseTooLong(SLIP39_MAX_PASSPHRASE_CHARS), p.error)
        assertNull(p.passphrase)
        assertTrue(System.nanoTime() - started < 1_000_000_000L)
        // Thousands of short parts within the length limit: refused by their number before any is read.
        assertEquals(slip39TooManyParts(SLIP39_MAX_PARTS), combineRequest("a\n".repeat(5_000), "").error)
        val ok = combineRequest("a b\n\nc d\n", "clave")
        assertEquals(listOf("a b", "c d"), ok.parts)
        assertArrayEquals("clave".toCharArray(), ok.passphrase)
        assertNull(ok.error)
    }

    /** Review M-1: the parts field holds the most parts a combination may need, at their longest, and not one character more. */
    @Test fun thePartsFieldHoldsTheLargestCombination() {
        assertEquals(135_936, SHARE_MAX_PARTS_CHARS)
        val longest = List(SLIP39_MAX_WORDS) { "academic" }.joinToString(" ")
        assertTrue(longest.length < SHARE_MAX_PART_LINE_CHARS)
        val all = List(SLIP39_MAX_PARTS) { longest }.joinToString("\n")
        assertTrue(all.length <= SHARE_MAX_PARTS_CHARS)
        val r = combineRequest(all, "")
        assertNull(r.error)
        assertEquals(SLIP39_MAX_PARTS, r.parts.size)
        assertEquals(SHARE_PARTS_TOO_LONG, combineRequest(all + " ".repeat(SHARE_MAX_PARTS_CHARS - all.length + 1), "").error)
        assertNull(combineRequest(all + " ".repeat(SHARE_MAX_PARTS_CHARS - all.length), "").error)
    }

    @Test fun aPhraseWithAnUnknownWordNamesTheFirstOne() {
        val words = zeros12.toMutableList().also { it[4] = "zzzz"; it[8] = "yyyy" }
        assertEquals(sharePhraseUnknown(5), splitInput(words.joinToString(" "), true, bip39).error)
        // An unknown word comes before the count, as before.
        assertEquals(sharePhraseUnknown(2), splitInput("abandon zzzz", true, bip39).error)
        assertTrue(bip39.isWord("abandon") && !bip39.isWord("Abandon") && !bip39.isWord(""))
    }

    @Test fun anOversizedSplitIsRefusedBeforeAnyWork() {
        val one = listOf(GroupForm("2", "3"))
        val huge = "f".repeat(10_000_000)
        val started = System.nanoTime()
        assertEquals(SHARE_SECRET_TOO_LONG, splitRequest(huge, false, "", "1", one, bip39).error)
        assertEquals(SHARE_SECRET_TOO_LONG, splitRequest(huge, true, "", "1", one, bip39).error)
        assertEquals(slip39PassphraseTooLong(SLIP39_MAX_PASSPHRASE_CHARS), splitRequest("ab".repeat(16), false, huge, "1", one, bip39).error)
        assertEquals(SHARE_NUMBER_INVALID, splitRequest("ab".repeat(16), false, "", huge, one + one, bip39).error)
        assertEquals(SHARE_NUMBER_INVALID, splitRequest("ab".repeat(16), false, "", "1", List(5_000) { GroupForm("2", "3") }, bip39).error)
        assertEquals(SHARE_NUMBER_INVALID, splitRequest("ab".repeat(16), false, "", "1", listOf(GroupForm(huge, "3")), bip39).error)
        assertTrue(System.nanoTime() - started < 1_000_000_000L)
        val r = splitRequest("ab".repeat(16), false, "clave", "9", one, bip39)
        assertNull(r.error)
        assertArrayEquals(ByteArray(16) { 0xAB.toByte() }, r.master)
        assertArrayEquals("clave".toCharArray(), r.passphrase)
        // One group: its threshold is 1 whatever the hidden box says.
        assertEquals(1, r.groupThreshold)
        assertEquals(listOf(GroupSpec(2, 3)), r.groups)
    }
}
