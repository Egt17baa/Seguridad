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

    // ---- RT-2 (ruling R34): the key the merge compares ----

    @Test fun compareKeyIsWhatIsShownInNfkcWithTheSpacesFolded() {
        assertEquals("Banco", compareKey("Ban​co"))
        assertEquals("Banco", compareKey("‮Banco⁠﻿"))
        assertEquals("Café", compareKey("Café"))
        assertEquals("Café", compareKey("Café"))
        assertEquals("ana", compareKey("an­a"))
        assertEquals("Mi banco", compareKey("  Mi  banco\u0000 "))
        assertEquals("Mi banco", compareKey("Mi\nbanco"))
        assertEquals("ANA", compareKey("ANA")) // the case is kept: the title's key lowers it, the user's does not
        assertEquals("Banco", compareKey("Ｂａｎｃｏ")) // RT-7 (R44): NFKC folds full-width letters
        assertEquals("", compareKey("​​ "))
        assertEquals("", compareKey(""))
    }

    // ---- RT-6 (ruling R43): what draws nothing is never shown nor compared ----

    @Test fun plainReportTextDropsWhatDrawsNothing() {
        for (c in listOf(0x2800, 0x17B4, 0x17B5, 0x180B, 0x180C, 0x180D, 0x180F, 0xFE00, 0xFE0E, 0xFE0F, 0xE0100, 0xE01EF)) {
            val s = String(Character.toChars(c))
            assertEquals("U+%04X".format(c), "banco", plainReportText("ban${s}co"))
            assertEquals("U+%04X".format(c), "banco", plainReportText("banco$s"))
            assertEquals("U+%04X".format(c), "banco", compareKey("${s}ban${s}co$s"))
        }
        // The variation selectors are dropped whole, never half a pair, also after a cut.
        val selectors = (0xE0100..0xE01EF).joinToString("") { String(Character.toChars(it)) }
        assertEquals("ab", plainReportText("a${selectors}b", maxChars = 10_000))
        // Only blanks: nothing is shown.
        assertEquals("", plainReportText("\u2800\u2800\u17B5\uFE0F"))
    }

    @Test fun anEmojiKeepsItsSelectorWhereAFileNameKeepsIt() {
        // As safeFileName (withoutLooseSelectors): after an emoji or a symbol, and in a keycap.
        for (t in listOf("❤\uFE0F", "Banco ❤\uFE0F", "1\uFE0F\u20E3 Banco", "#\uFE0F\u20E3", "☎\uFE0F casa")) assertEquals(t, plainReportText(t))
        // Anywhere else it draws nothing and goes: at the start, after ASCII, a space, a joiner or another selector.
        assertEquals("Banco", plainReportText("\uFE0FBanco"))
        assertEquals("Banco 1", plainReportText("Banco 1\uFE0F"))
        assertEquals("a b", plainReportText("a \uFE0Fb"))
        assertEquals("❤\uFE0F", plainReportText("❤\uFE0F\uFE0F"))
        assertEquals("👨👩", plainReportText("👨\u200D\uFE0F👩"))
        // U+FE0E (the text look) always goes, as in a file name.
        assertEquals("❤", plainReportText("❤\uFE0E"))
        // The comparison drops it even where it is shown: one emoji with and without it is the same picture.
        assertEquals(compareKey("❤ Banco"), compareKey("❤\uFE0F Banco"))
        assertEquals(compareKey("Café"), compareKey("Café\uFE0F"))
        assertEquals(compareKey("Café"), compareKey("Cafe\uFE0F\u0301"))
    }

    // ---- RT-7 (ruling R44): the compatibility forms compare as the plain ones ----

    @Test fun compareKeyFoldsTheCompatibilityForms() {
        for ((a, b) in listOf(
            "Proﬁle" to "Profile", "Cuenta Ⅱ" to "Cuenta II", "Ｂanco" to "Banco", "𝐁anco" to "Banco", "Cuenta²" to "Cuenta2",
            "ｶﾞ" to "ガ", "Banco\u2003X" to "Banco X", "㎏" to "kg",
        )) {
            assertEquals("«$a» vs «$b»", compareKey(b), compareKey(a))
        }
        // Look-alikes that NFKC does not fold stay apart (the report notes them): another script, dashes, «'».
        for ((a, b) in listOf("Bаnco" to "Banco", "Banco‐X" to "Banco-X", "Ana’s" to "Ana's")) assertTrue("«$a»", compareKey(a) != compareKey(b))
        // Only the comparison: what is shown keeps the form it came in.
        assertEquals("Proﬁle", plainReportText("Proﬁle"))
    }

    @Test fun compareKeyIsNotCutNeverThrowsAndIsCheap() {
        assertEquals(5_000, compareKey("x".repeat(5_000)).length) // unlike what is shown, the key is never cut
        assertEquals("ab", compareKey("a\uD800b\uDC00")) // lone surrogates
        val titles = List(40_000) { "Entrada número $it con acentos é ñ ​" }
        val start = System.nanoTime()
        val keys = titles.map(::compareKey)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals("Entrada número 7 con acentos é ñ", keys[7])
        assertTrue("$ms ms for 40 000 keys", ms < 5_000)
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
