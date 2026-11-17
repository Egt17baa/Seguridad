package app.seguridad.redteam

import app.seguridad.crypto.TotpSecret
import app.seguridad.crypto.totpCode
import app.seguridad.tools.TwoStepCopy
import app.seguridad.tools.TWO_STEP_COPY_MS
import app.seguridad.tools.rowCodeToCopy
import app.seguridad.vault.CustomField
import app.seguridad.vault.EntryFilter
import app.seguridad.vault.EntryType
import app.seguridad.vault.FieldKind
import app.seguridad.vault.LimitedMarkup
import app.seguridad.vault.MarkupBlock
import app.seguridad.vault.SEARCH_TEXT_MAX
import app.seguridad.vault.VaultData
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.query
import app.seguridad.vault.searchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/*
 * Final attack of Clavero 2.0 (R32), phase 2, surfaces 6 to 8 (what runs on the JVM): the notes' format, the clipboard
 * (the 2FA of a row and «Copiar contraseña» in two steps) and the searches. The wifi QR (surface 5, row 24) needs ZXing
 * and Compose sources: app/src/test/java/app/seguridad/ui/redteam/RedTeam20WifiQrTest.kt. Report:
 * docs/superpowers/handoff/sdd-2.0/redteam-report.md.
 */
class RedTeam20TextsTest {
    private val rnd = Random(20261117L)

    private fun timed(block: () -> Unit): Long {
        val t = System.nanoTime()
        block()
        return (System.nanoTime() - t) / 1_000_000
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Surface 6: the notes' format (vault/LimitedMarkup.kt).
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Surface 6: pathological notes, with time and memory bounded (decision (a)): 64 KiB of `*` and `**` alternating
     * with letters and spaces, one line of 1 MiB of runs, 10 000 list lines and more, numbered lines of 9 and 10 digits,
     * and 20 MiB of note (what a copy can bring; only the first 64 KiB are read as format, LimitedMarkup.kt:47-61). Each
     * parse is linear (LimitedMarkup.kt:86-155): well under a second, and the text comes back whole, nothing lost.
     */
    @Test fun resistsPathologicalNotes() {
        val alternating = buildString { while (length < 64 * 1024) append(listOf("*", "**", "***", "a", " ", "****")[rnd.nextInt(6)]) }
        val oneLongLine = buildString { while (length < 1024 * 1024) append(if (rnd.nextBoolean()) "*a" else "**b ") }
        val manyLines = (0 until 12_000).joinToString("\n") { "- *x* **y** $it" }
        val numbers = "999999999. nueve\n1234567890. diez\n0000000001. ceros"
        val huge = "# título\n" + "x".repeat(20 * 1024 * 1024)
        for (text in listOf(alternating, oneLongLine, manyLines, numbers, huge)) {
            var blocks: List<MarkupBlock> = emptyList()
            val ms = timed { blocks = LimitedMarkup.parse(text) }
            assertTrue("${text.length} chars took $ms ms", ms < 2_000)
            // Nothing lost: the spans and the line breaks give the text back (numbers and marks aside, only counted here).
            assertTrue(blocks.size <= LimitedMarkup.MAX_LINES + 1)
            val chars = blocks.sumOf { b -> b.spans.sumOf { it.text.length } }
            assertTrue(chars <= text.length)
        }
        val n = LimitedMarkup.parse(numbers)
        assertEquals(999_999_999, (n[0] as MarkupBlock.Numbered).n)
        assertTrue("10 digits is no number", n[1] is MarkupBlock.Paragraph)
        // A link, an address, HTML: text as it is, in a paragraph (there is no block that could be tapped).
        val link = LimitedMarkup.parse("[x](https://evil.example) <a href=\"intent://x\">y</a> www.evil.example")
        assertEquals(listOf("[x](https://evil.example) <a href=\"intent://x\">y</a> www.evil.example"), link.flatMap { b -> b.spans.map { it.text } })
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Surface 7: the clipboard.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Surface 7, the 2FA of a row (tools/TwoStepCopy.kt:97-102): with fewer than 5 s left the next code is copied, and that
     * «next» is always the code of the next period, never the one about to end, at every millisecond of a period of 30 and
     * of 60 s (crypto/Totp.kt:140-142: the seconds left are counted from the whole second, so now + left × 1 000 is always
     * past the end of the period).
     */
    @Test fun resistsTheNextCodeIsAlwaysTheNextPeriod() {
        for (period in listOf(30, 60)) {
            val s = TotpSecret("JBSWY3DPEHPK3PXP", period = period)
            val start = 1_800_000_000_000L / (period * 1_000L) * (period * 1_000L)
            for (ms in 0 until period * 1_000 step 7) {
                val now = start + ms
                val r = rowCodeToCopy(s, now)!!
                val (current, left) = totpCode(s, now)!!
                if (left >= 5) {
                    assertEquals(current, r.code)
                    assertTrue(!r.next)
                } else {
                    assertTrue(r.next)
                    assertEquals("At $ms ms of the period", totpCode(s, start + period * 1_000L)!!.first, r.code)
                }
            }
        }
    }

    /**
     * Surface 7, «Copiar contraseña» after «Copiar usuario» (tools/TwoStepCopy.kt): only after leaving the app and coming
     * back, once, within 30 s; a lock or a switch of vault (the app's lock counter) ends it, also if the vault is opened
     * again; a clock that goes back ends it. It holds only the entry's id.
     */
    @Test fun resistsTheTwoStepCopyOutsideItsWindow() {
        var clock = 1_000_000L
        var epoch = 7
        val t = TwoStepCopy({ clock }, { epoch })
        t.offerPassword("e1")
        assertNull("Not offered without leaving the app", t.takeReturnOffer())
        t.markLeft()
        clock += TWO_STEP_COPY_MS - 1
        assertEquals("e1", t.takeReturnOffer())
        assertNull("Once per return", t.takeReturnOffer())
        // A lock (or a switch) since the offer: gone, even after unlocking again.
        t.offerPassword("e2")
        t.markLeft()
        epoch++
        assertNull(t.takeReturnOffer())
        assertNull(t.pending())
        // 30 s later, and the clock going back.
        t.offerPassword("e3")
        t.markLeft()
        clock += TWO_STEP_COPY_MS
        assertNull(t.takeReturnOffer())
        t.offerPassword("e4")
        t.markLeft()
        clock -= 1
        assertNull(t.takeReturnOffer())
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Surface 8: the searches never read a secret.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Surface 8 (vault/EntryQuery.kt:106-140, :154-195): the search reads the title, the user, the host of the URL, the 2FA's
     * service and account, the tags, the folders and the names of the fields that are not hidden; never a password, a
     * hidden field's value or name, a note, a seed phrase, a 2FA secret, a card number or the user and password inside a
     * URL. Each of those secrets, searched exactly and with one typo, finds nothing, so no result (nor its order) tells
     * anything about them. The text searched is cut at 200 code points (EntryQuery.kt:55-58).
     */
    @Test fun resistsTheSearchAsAnOracleOfASecret() {
        val secret = "zorrocuantico"
        val e = VaultEntry(
            "s", EntryType.LOGIN, "Banco", username = "ana", password = secret, url = "https://ana:$secret@banco.example/",
            notes = "nota $secret", seedWords = secret, totp = TotpSecret("JBSWY3DPEHPK3PXP${secret.uppercase()}"), card = secret,
            fields = listOf(CustomField("h", "$secret-oculto", FieldKind.HIDDEN, secret), CustomField("t", "visible", FieldKind.TEXT, secret)),
            createdAt = 1, updatedAt = 1,
        )
        val data = VaultData(entries = listOf(e))
        fun find(q: String) = listOf(e).query(EntryFilter(text = q), data, now = 1, weak = { false })
        assertEquals("The control: the title is found", listOf(e), find("banco"))
        for (q in listOf(secret, "zorrocuantic", "zorocuantico", "zorrocuantico-oculto", "JBSWY3DPEHPK3PXP")) {
            assertEquals("«$q» finds nothing", emptyList<VaultEntry>(), find(q))
        }
        assertEquals(SEARCH_TEXT_MAX, searchText("ñ".repeat(10_000)).length)
    }
}
