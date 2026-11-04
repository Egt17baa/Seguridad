package app.seguridad.imports

import app.seguridad.crypto.OTPAUTH_MAX_NAME_CHARS
import app.seguridad.crypto.withoutBidiControls
import app.seguridad.vault.EntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportModelTest {
    @Test fun plainReportTextDropsBidiAndTurnsControlsIntoSpaces() {
        assertEquals("facturafdp.exe", plainReportText("factura\u202Efdp.exe"))
        assertEquals("ab", plainReportText("a\u2066\u2067\u2068\u2069\u202A\u202B\u202C\u202D\u202E\u200E\u200F\u061Cb"))
        assertEquals("a b c d e f g", plainReportText("a\nb\u0000c\u0085d\u2028e\u2029f\tg"))
        assertEquals("  [31m", plainReportText("\u0007\u001B[31m"))
    }

    @Test fun plainReportTextDropsInvisibleFormatCharacters() {
        // Zero-width space, non-joiner, joiner, BOM, word joiner and the invisible operators, soft hyphen, Mongolian vowel separator.
        for (c in listOf(0x200B, 0x200C, 0x200D, 0xFEFF, 0x2060, 0x2061, 0x2062, 0x2063, 0x2064, 0x00AD, 0x180E)) {
            assertEquals("U+%04X".format(c), "banco", plainReportText("ban" + String(Character.toChars(c)) + "co"))
        }
        // Tag characters U+E0000–U+E007F are surrogate pairs: dropped whole, never half.
        val tags = (0xE0000..0xE007F).joinToString("") { String(Character.toChars(it)) }
        assertEquals("ab", plainReportText("a${tags}b", maxChars = 10_000))
        // Interlinear annotations, the combining grapheme joiner, Hangul fillers and a lone surrogate.
        assertEquals("ab", plainReportText("a\uFFF9\uFFFA\uFFFBb"))
        assertEquals("ab", plainReportText("a\u034F\u115F\u1160\u3164\uFFA0\uD800b"))
        // Every character that withoutBidiControls drops is dropped here too (they are all Cf).
        for (c in Char.MIN_VALUE..Char.MAX_VALUE) {
            if (withoutBidiControls(c.toString()).isEmpty()) assertEquals("U+%04X".format(c.code), "ab", plainReportText("a${c}b"))
        }
    }

    @Test fun plainReportTextKeepsOnlyAFewCombiningMarksOnOneBase() {
        val tower = "e" + "\u0301".repeat(1023) + "x"
        assertEquals("e" + "\u0301".repeat(MAX_COMBINING_MARKS) + "x", plainReportText(tower))
        // Enclosing marks (Me) count too; a new base starts the count again.
        assertEquals("a\u20DD\u20DD\u20DDb\u0301", plainReportText("a\u20DD\u20DD\u20DD\u20DD\u20DDb\u0301"))
        // Vietnamese stacks two marks on one base: kept.
        assertEquals("e\u0302\u0301", plainReportText("e\u0302\u0301"))
    }

    @Test fun ordinaryTextComesOutUnchanged() {
        for (t in listOf(
            "Ñandú, cigüeña y pingüino: ¿qué tal? ¡Bien!",
            "Ma\u0301laga en NFD", // a decomposed accent
            "café ☕ 😀 👍🏽 ❤\uFE0F 🇪🇸 𝄞",
            "Привет, 東京, عربى, עברית",
            "https://ejemplo.com/ruta?x=1&y=2",
        )) assertEquals(t, plainReportText(t))
        // A family emoji loses its joiners and shows as its separate emoji (accepted, said in the KDoc).
        assertEquals("👨👩👧", plainReportText("👨\u200D👩\u200D👧"))
    }

    @Test fun plainReportTextIsBoundedWithoutSplittingAPair() {
        assertEquals(MAX_TITLE, plainReportText("x".repeat(100_000)).length)
        val pairs = "𝄞".repeat(MAX_TITLE) // 2 units each
        val cut = plainReportText(pairs)
        assertEquals(MAX_TITLE, cut.length)
        assertEquals(MAX_TITLE - 1, plainReportText("x$pairs").length) // a pair that would pass the limit is left out whole
        assertTrue(Character.isLowSurrogate(plainReportText("x$pairs").last()))
        assertEquals(3, plainReportText("abcdef", maxChars = 3).length)
        assertEquals("", plainReportText("abc", maxChars = 0))
    }

    @Test fun skippedTitlesAlwaysGoThroughThePlainText() {
        val b = ImportReadBuilder(ImportFormat.BROWSER_CSV)
        b.skip("a\u202Eb\n" + "x".repeat(5000), SkipReason.BROKEN)
        val s = b.build().skipped.single()
        assertEquals(MAX_TITLE, s.title.length)
        assertTrue(s.title.startsWith("ab x"))
    }

    @Test fun addTotpNeverCutsANameAndItsPartsCannotAddASetting() {
        val b = ImportReadBuilder(ImportFormat.AEGIS)
        b.addTotp("JBSWY3DPEHPK3PXP", "i".repeat(OTPAUTH_MAX_NAME_CHARS + 1), "a", null, null, null, "t")
        b.addTotp("JBSWY3DPEHPK3PXP", "e", "a".repeat(OTPAUTH_MAX_NAME_CHARS + 1), null, null, null, "t")
        b.addTotp("JBSWY3DPEHPK3PXP", "e&digits=10", "a:b c", "SHA256", 8, 60, "t")
        val read = b.build()
        assertEquals(listOf(SkipReason.FIELD_TOO_LONG, SkipReason.FIELD_TOO_LONG), read.skipped.map { it.reason })
        val s = read.items.single().entry.totp!!
        assertEquals(listOf("e&digits=10", "a:b c", "SHA256", 8, 60), listOf(s.issuer, s.account, s.algorithm, s.digits, s.period))
        // A link whose label the reader would cut is listed too.
        val l = ImportReadBuilder(ImportFormat.OTPAUTH)
        l.addOtpAuth("otpauth://totp/" + "x".repeat(OTPAUTH_MAX_NAME_CHARS + 1) + "?secret=JBSWY3DPEHPK3PXP", "t")
        assertEquals(SkipReason.FIELD_TOO_LONG, l.build().skipped.single().reason)
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
