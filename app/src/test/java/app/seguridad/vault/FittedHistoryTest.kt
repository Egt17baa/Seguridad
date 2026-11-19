package app.seguridad.vault

import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Round 3 of the fixes of the attack, RT-29: [fittedHistory] measures each version once (linear) and keeps exactly the
 * same versions as the loop of before, which measured the whole history again after each version it dropped (copied
 * here as the reference, [before]). It decides which versions are deleted, so it is checked case by case: 0 to 25
 * versions, ties in `savedAt`, totals on both sides of 64 KiB, and notes with `"`, `\`, control characters, U+2028 and
 * text that is not ASCII (escapes and multi-byte UTF-8 count in the size); and the real files of the tests' resources,
 * through the import. Random(20261119L).
 */
class FittedHistoryTest {
    private val serializer = ListSerializer(EntryVersion.serializer())

    /** fittedHistory as it was before RT-29 (VaultLimits.kt), the reference. */
    private fun VaultEntry.before(): VaultEntry {
        if (history.isEmpty()) return this
        var kept = history
        if (kept.size > VaultLimits.HISTORY_MAX_VERSIONS) {
            val doomed = kept.withIndex().sortedBy { it.value.savedAt }.take(kept.size - VaultLimits.HISTORY_MAX_VERSIONS).mapTo(HashSet()) { it.index }
            kept = kept.filterIndexed { i, _ -> i !in doomed }
        }
        while (kept.isNotEmpty() && jsonBytes(serializer, kept) > VaultLimits.HISTORY_MAX_BYTES) {
            val oldest = kept.indices.minBy { kept[it].savedAt }
            kept = kept.filterIndexed { i, _ -> i != oldest }
        }
        return if (kept.size == history.size) this else copy(history = kept)
    }

    private val alphabet = listOf("a", "é", "\"", "\\", "\u0001", "\u001f", " ", "€", "😀", "ñ", " ")

    private fun text(rnd: Random, bytes: Int): String = buildString {
        // About [bytes] of JSON: mostly «n», with a few of the characters that escape or take more than a byte.
        while (length < bytes) {
            if (rnd.nextInt(20) == 0) append(alphabet[rnd.nextInt(alphabet.size)]) else append('n')
        }
    }

    private fun case(rnd: Random): VaultEntry {
        val k = rnd.nextInt(26)
        // Totals mostly around 64 KiB (both sides), sometimes far under or over it.
        val target = when (rnd.nextInt(10)) {
            0 -> rnd.nextInt(8_000)
            1 -> 64 * 1024 + rnd.nextInt(150_000)
            else -> 64 * 1024 - 6_000 + rnd.nextInt(12_000)
        }
        val each = if (k == 0) 0 else target / k
        val ties = rnd.nextInt(3) == 0
        val base = VaultEntry("e", EntryType.LOGIN, "t", createdAt = 1, updatedAt = 1)
        val history = List(k) { i ->
            val size = maxOf(1, each + rnd.nextInt(maxOf(1, each / 4 + 1)) - each / 8)
            EntryVersion(if (ties) rnd.nextInt(3).toLong() else rnd.nextInt(1_000_000).toLong(), base.copy(notes = text(rnd, size), password = "p$i"))
        }
        return base.copy(history = history)
    }

    private fun same(rnd: Random, n: Int) {
        var trimmed = 0
        var fits = 0
        repeat(n) { c ->
            val e = case(rnd)
            val expected = e.before()
            val got = e.fittedHistory()
            assertEquals("case $c: the same versions, in the same order", expected.history, got.history)
            assertEquals("case $c", expected, got)
            if (expected === e) {
                assertSame("case $c: the same object when it fits", e, got)
                fits++
            } else {
                trimmed++
            }
            assertTrue("case $c: within 64 KiB", jsonBytes(serializer, got.history) <= VaultLimits.HISTORY_MAX_BYTES || got.history.isEmpty())
        }
        assertTrue("both sides covered: $fits fit, $trimmed trimmed", fits > n / 10 && trimmed > n / 10)
    }

    @Test fun theSameVersionsAsBeforeFirstHalf() = same(Random(20261119L), 1_000)

    @Test fun theSameVersionsAsBeforeSecondHalf() = same(Random(20261119L + 1), 1_000)

    /** The real files of the tests' resources, brought to v4 by the import (old passwords become versions): the same data. */
    @Test fun theRealFilesGiveTheSameData() {
        fun text(name: String) = javaClass.getResourceAsStream("/seguridad/$name")!!.use { it.readBytes() }.decodeToString()
        for ((name, master) in listOf("vault-1.1.json" to "maestra-de-prueba", "vault-1.4.3.json" to "maestra-de-prueba-143", "export-1.0.2.json" to "maestra-de-prueba")) {
            val file = VaultCrypto.decodeFile(text(name))
            val dek = VaultCrypto.unlock(file, master.toCharArray())
            val imported = VaultCrypto.readAsImported(file, dek, copy = true).imported
            assertEquals(name, imported.entries, imported.entries.map { it.before() })
            for (e in imported.entries) assertSame(name, e, e.fittedHistory())
        }
    }
}
