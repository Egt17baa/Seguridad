package app.seguridad.imports

import app.seguridad.crypto.withoutBidiControls
import app.seguridad.vault.EntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportModelTest {
    @Test fun plainReportTextDropsBidiAndTurnsControlsIntoSpaces() {
        assertEquals("facturafdp.exe", plainReportText("factura\u202Efdp.exe"))
        assertEquals("ab", plainReportText("a\u2066\u2067\u2068\u2069\u202A\u202B\u202C\u202D\u202E\u200E\u200F\u061Cb"))
        assertEquals("a b c d e f", plainReportText("a\nb\u0000c\u0085d\u2028e\u2029f"))
        assertEquals("  [31m", plainReportText("\u0007\u001B[31m"))
        assertEquals("Ñandú 𝄞", plainReportText("Ñandú 𝄞"))
    }

    @Test fun plainReportTextIsBoundedWithoutSplittingAPair() {
        assertEquals(MAX_TITLE, plainReportText("x".repeat(100_000)).length)
        val pairs = "𝄞".repeat(MAX_TITLE) // 2 units each
        val cut = plainReportText(pairs)
        assertEquals(MAX_TITLE, cut.length)
        assertEquals(MAX_TITLE - 1, plainReportText("x$pairs").length) // the high half at the cut is dropped
        assertTrue(Character.isLowSurrogate(plainReportText("x$pairs").last()))
        assertEquals(3, plainReportText("abcdef", maxChars = 3).length)
        assertEquals("", plainReportText("abc", maxChars = 0))
    }

    @Test fun theBidiListIsTheOneOfWithoutBidiControls() {
        for (c in Char.MIN_VALUE..Char.MAX_VALUE) {
            assertEquals("U+%04X".format(c.code), withoutBidiControls(c.toString()).isEmpty(), isBidiControl(c))
        }
    }

    @Test fun skippedTitlesAlwaysGoThroughThePlainText() {
        val b = ImportReadBuilder(ImportFormat.BROWSER_CSV)
        b.skip("a\u202Eb\n" + "x".repeat(5000), SkipReason.BROKEN)
        val s = b.build().skipped.single()
        assertEquals(MAX_TITLE, s.title.length)
        assertTrue(s.title.startsWith("ab x"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aReaderCannotBuildPersonalTypes() {
        ImportReadBuilder(ImportFormat.BITWARDEN).add(importedEntry(EntryType.IDENTITY, "x"))
    }

    @Test fun originTagsAndTexts() {
        assertEquals(
            listOf("importado-navegador", "importado-firefox", "importado-apple", "importado-bitwarden", "importado-aegis", "importado-2fas", "importado-otpauth"),
            ImportFormat.entries.map { it.originTag },
        )
        assertEquals("Código que no es TOTP (HOTP, Steam…)", SkipReason.NOT_TOTP.text)
        assertEquals("Passkey: Clavero no las guarda", SkipReason.PASSKEY.text)
    }
}
