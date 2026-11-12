package app.seguridad.redteam

import app.seguridad.core.VISIBLE_PREPENDED_MARKS
import app.seguridad.core.drawsNothing
import app.seguridad.crypto.isHiddenInName
import app.seguridad.crypto.safeFileName
import app.seguridad.health.WEAK_BITS
import app.seguridad.health.estimateBits
import app.seguridad.crypto.TotpSecret
import app.seguridad.imports.DuplicateChoice
import app.seguridad.imports.ImportFormat
import app.seguridad.imports.ImportRead
import app.seguridad.imports.ImportedItem
import app.seguridad.imports.ReadResult
import app.seguridad.imports.compareKey
import app.seguridad.imports.duplicateSubline
import app.seguridad.imports.importTags
import app.seguridad.imports.importedEntry
import app.seguridad.imports.mergedEntries
import app.seguridad.imports.planMerge
import app.seguridad.imports.plainReportText
import app.seguridad.imports.readImport
import app.seguridad.imports.replaceWith
import app.seguridad.imports.urlKey
import app.seguridad.tools.qr.asBrowserReadsIt
import app.seguridad.tools.qr.hasHiddenChars
import app.seguridad.tools.qr.parseLink
import app.seguridad.tools.qr.visibleText
import app.seguridad.vault.ListQuery
import app.seguridad.vault.EntryType
import app.seguridad.vault.MASTER_TOO_WEAK
import app.seguridad.vault.MASTER_WORD_LISTS
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.masterWords
import app.seguridad.vault.newMasterProblem
import app.seguridad.vault.query
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.SplittableRandom

private fun recheckAssetLines(name: String): List<String> =
    listOf("src/main/assets", "../app/src/main/assets").map { File(it, name) }.first { it.exists() }.readLines().filter { it.isNotBlank() }

private fun cpName(cp: Int) = "U+%04X".format(cp)

private fun str(cp: Int) = String(Character.toChars(cp))

/*
 * Re-check of the second attack's fixes (owner's decision (f)), on bf04f45: a fresh red-team agent tries to get around
 * the fixes of RT-4 to RT-7 (rulings R41 to R44) and the QR Inspector's own list. Same naming as before: rtN_… asserts
 * the SAFE behaviour and failed while RT-N was open (@Ignore'd only for the gate then); resists… passes. RT-8, RT-9,
 * RT-10, RT-12 and RT-13 are fixed (rulings R45 to R49 and R51), and RT-14 of the third check (rulings R53 and R54). RT-11's PoC left this class (ruling R50: the owner
 * decides for 1.4.1; its passwords stay in the report). The third check (db42d3e, ce6b0f9) adds the tests from
 * «Third check» down, the fourth (f6148bb) from «Fourth check» down, the fifth (fd9bd4b) from «Fifth check» down,
 * the sixth (2eaf393) from «Sixth check» down. Report: docs/superpowers/handoff/sdd-1.4.0/redteam-second-recheck.md.
 */
class RedTeamRecheckTest {
    private val known by lazy { masterWords(MASTER_WORD_LISTS.map(::recheckAssetLines)) }

    private fun vaultLogin(url: String) =
        VaultEntry("v1", EntryType.LOGIN, "Banco", username = "ana", password = "la-buena", url = url, createdAt = 1, updatedAt = 1)

    private fun fileLogin(url: String, title: String = "Banco", user: String = "ana") =
        ImportedItem(0, importedEntry(EntryType.LOGIN, title, username = user, password = "la-del-atacante", url = url), emptyList())

    private fun plan(file: ImportedItem, vault: List<VaultEntry>) =
        planMerge(ImportRead(ImportFormat.BROWSER_CSV, listOf(file), emptyList(), 0), vault, revision = 1)

    private fun replaced(file: ImportedItem, vault: List<VaultEntry>): List<VaultEntry> =
        mergedEntries(plan(file, vault), DuplicateChoice.REPLACE, emptyMap(), vault, now = 2, newId = { "n" })

    // ---------------------------------------------------------------------------------------------------------------
    // RT-4 (R41): the scheme is part of the duplicate key.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Scheme and authority tricks around `urlKey` (MergePlan.kt:401-407) and `comparedLink` (:416-423): only a URL that a
     * browser opens as https on banco.es is a duplicate of `https://banco.es/`, and «Reemplazar» never changes the vault's
     * scheme. A leading control or a tab inside the scheme is dropped first, as a browser drops it (ruling R54, RT-14),
     * so `ht\ttps://banco.es` is https on banco.es and a duplicate. `evil.example%2F@banco.es`: an encoded «/» does not end the authority, so
     * it is user info and the browser goes to banco.es, a duplicate as it should be.
     */
    @Test fun resistsSchemeAndAuthorityVariantsAroundTheRt4Fix() {
        val duplicates = listOf(
            "https://banco.es/", "HTTPS://BANCO.ES", "hTtPs://www.banco.es/login", "https:/banco.es", "https:banco.es",
            "https:\\\\banco.es", "https:\\/banco.es", "https:///banco.es", "//banco.es", "\\\\banco.es", "banco.es",
            "banco.es/login", "banco.es:8443/x", "  https://banco.es  ", "https://ana@banco.es/", "https://ana:x@banco.es/",
            "https://banco.es./", "https://%62anco.es", "https://ｂａｎｃｏ.ｅｓ", "https://banco.es。", "https://banco.es:443",
            "https://banco.es:8443", "https://BANCO.ES#x", "https://banco.es?q=1", "https://evil.example%2F@banco.es",
            // Ruling R54 (RT-14): a browser drops the tab and the leading control, and opens https on banco.es.
            "ht\ttps://banco.es", "\u0001https://banco.es",
        )
        val notDuplicates = listOf(
            "http://banco.es/", "HTTP://BANCO.ES", "http:banco.es", "http:\\\\banco.es", "ftp://banco.es", "ws://banco.es",
            "wss://banco.es", "file://banco.es/x", "file:///sdcard/banco.es", "javascript://banco.es/%0Aalert(1)",
            "JavaScript://banco.es/", "intent://banco.es/#Intent;scheme=https;package=com.example.evil;end",
            "javascript:https://banco.es", "blob:https://banco.es/1", "view-source:https://banco.es/", "content://banco.es/x",
            "https+x://banco.es", "mailto:ana@banco.es", "javascript:x@banco.es/", "data:text/html,banco.es",
            "https://banco.es@evil.example", "https://evil.example\\@banco.es", "https://banco.es.evil.example",
            "https://bаnco.es", "https://banco.es..", "java\tscript://banco.es/", "banco.es:8443@evil.example",
        )
        val vault = listOf(vaultLogin("https://banco.es/"))
        for (url in duplicates) assertTrue("«$url» should be a duplicate", plan(fileLogin(url), vault).duplicates.isNotEmpty())
        for (url in notDuplicates) {
            assertTrue("«$url» is a duplicate", plan(fileLogin(url), vault).duplicates.isEmpty())
            // And «Reemplazar» leaves the vault's entry as it was: the item is a new entry.
            assertEquals("«$url»", "https://banco.es/", replaced(fileLogin(url), vault).first().url)
        }
    }

    /** A file item without a URL is a duplicate by title and user, and «Reemplazar» keeps the vault's URL. */
    @Test fun resistsReplacingWithoutAUrl() {
        val vault = listOf(vaultLogin("https://banco.es/"))
        for (url in listOf("", "   ", "\t")) {
            assertTrue(plan(fileLogin(url), vault).duplicates.isNotEmpty())
            assertEquals("https://banco.es/", replaced(fileLogin(url), vault).first().url)
        }
    }

    /**
     * RT-8. When the vault's entry has NO URL, title and user are enough (`Matcher.find`, MergePlan.kt:474-478: `g.any`
     * and `g.hostless`), so a file item with a URL of any scheme is its duplicate, and the row shows only its host
     * (`duplicateSubline`, :435-441, R41): «ana · banco.es» for `javascript://banco.es/…`, `intent://banco.es/…`,
     * `file://banco.es/…` and `http://banco.es/`. With «Reemplazar», `replaceWith` (:263) gives the vault's entry that URL.
     * The same when the vault has the https entry AND a URL-less one with the same title and user: the http item falls
     * to the URL-less one (`g.byHost[host] ?: g.hostless`). Safe behaviour (as rt4_): not a duplicate, or the row shows
     * the scheme that comes in.
     */
    @Test fun rt8_aUrlOfAnySchemeDoesNotEnterSilentlyAnEntryWithoutUrl() {
        val bare = vaultLogin("")
        val both = listOf(vaultLogin("https://banco.es/"), bare.copy(id = "v2"))
        for ((url, scheme) in listOf(
            "javascript://banco.es/%0Aalert(1)" to "javascript",
            "intent://banco.es/#Intent;scheme=https;package=com.example.evil;end" to "intent",
            "file://banco.es/sdcard/x.html" to "file",
            "http://banco.es/login" to "http",
        )) {
            val row = duplicateSubline(fileLogin(url).entry)
            for (vault in listOf(listOf(bare), both)) {
                val p = plan(fileLogin(url), vault)
                val after = replaced(fileLogin(url), vault).map { it.url }
                assertTrue("«$url» against $vault: a duplicate whose row «$row» hides the scheme; after «Reemplazar»: $after", p.duplicates.isEmpty() || "$scheme:" in row)
            }
        }
    }

    /**
     * RT-12 (Info). A URL without a host is compared whole in lower case («t» + `t.lowercase`, MergePlan.kt:406), and the
     * row shows it in lower case too (`urlHost`, :390). An opaque path is case-sensitive (Base64 in `data:`, the code of
     * `javascript:`), so two different links of the same scheme that differ only in case are duplicates, and the row
     * does not show the text that comes in. Safe behaviour: the whole-text key keeps the case after the scheme, and the
     * row shows what comes in.
     */
    @Test fun rt12_aUrlWithoutAHostKeepsItsCase() {
        val vault = listOf(vaultLogin("data:text/plain;base64,SGVsbG8="))
        val incoming = "data:text/plain;base64,sGVSbg8="
        val p = plan(fileLogin(incoming), vault)
        val row = duplicateSubline(fileLogin(incoming).entry)
        assertTrue("a duplicate, row «$row»", p.duplicates.isEmpty() || row.endsWith(incoming))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // RT-5 (R42): the leet of a Spanish keyboard.
    // ---------------------------------------------------------------------------------------------------------------

    /** Around ¡ € ø Ø: case, position (lead, tail, middle), full-width, mixed with the ASCII leet, ç and accents. */
    @Test fun resistsLeetVariantsAroundTheRt5Fix() {
        for (c in listOf(
            "PR¡NC3SS2024", "¡Pr¡nc3ss!2024", "Ｐr¡nc€ss2024", "Dr4gØn!2024", "DR4GØN!2024", "¡¡Dr4gøn!!2024", "Prinçess2024!",
            "Sh4døw€2024!", "C0ntra\$€ña2024", "P4\$\$wØrd!2024", "M¡ll¡on2024!", "H€ll0!2024!!", "Pàsswörd2024!",
        )) {
            assertTrue("«$c» is above the estimate, so the word check decides", estimateBits(c) >= WEAK_BITS || newMasterProblem(c, known) == MASTER_TOO_WEAK)
            assertEquals("«$c»", MASTER_TOO_WEAK, newMasterProblem(c, known))
        }
        // No false refusal: random passwords with ¡ € ø, and a phrase.
        for (c in listOf("¡Q7x€zø#Kp2m", "ø€¡ vTq9#Lm2!Rz", "R7#q¡Xv2€Lm9\$Tzø", "cactus-tímido-lunes-vapor-oruga")) {
            assertNull("«$c»", newMasterProblem(c, known))
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // RT-6 (R43): one list of what draws nothing.
    // ---------------------------------------------------------------------------------------------------------------

    /** The code points of the brief, and more of the default ignorables: dropped from what is shown and compared. */
    @Test fun resistsInvisiblesAroundTheRt6Fix() {
        val cps = listOf(
            0x115F, 0x1160, 0x3164, 0xFFA0, 0x2061, 0x2062, 0x2063, 0x2064, 0x206A, 0x206F, 0xFFF9, 0xFFFA, 0xFFFB,
            0x1D173, 0x1D17A, 0x034F, 0x1BCA0, 0x1BCA3, 0x2800, 0x17B4, 0x180B, 0x180F, 0xFE00, 0xFE0E, 0xE0001, 0xE0080,
            0xE0100, 0xE01EF, 0xE01F0, 0xE0FFF, 0x200B, 0x2060, 0xFEFF, 0x00AD,
        )
        for (cp in cps) {
            assertEquals(cpName(cp), "Banco", plainReportText("Ban${str(cp)}co"))
            assertEquals(cpName(cp), compareKey("Banco"), compareKey("Ban${str(cp)}co"))
            assertEquals(cpName(cp), compareKey("Banco"), compareKey("Banco${str(cp)}${str(cp)}"))
        }
        // Lone surrogates, a reversed pair, and U+FE0F after ASCII: dropped, so shown and compared as «Banco».
        for (t in listOf("Ban\uD800co", "Ban\uDC00co", "Ban\uDC00\uD800co", "Banco\uD83D", "Banco\uFE0F", "\uFE0FBanco", "Ban\uFE0F\u200Dco")) {
            assertEquals(t, "Banco", plainReportText(t))
            assertEquals(t, compareKey("Banco"), compareKey(t))
        }
        // A tower of marks is capped the same way on both sides.
        assertEquals(compareKey("Banco\u0301\u0301\u0301"), compareKey("Banco\u0301\u0301\u0301\u0301\u0301"))
    }

    /** What must still show: emoji with their selector, keycaps, flags, skin tones; and they compare as without it. */
    @Test fun resistsEmojiThatMustStillShow() {
        for (t in listOf("❤\uFE0F", "1\uFE0F\u20E3", "#\uFE0F\u20E3", "*\uFE0F\u20E3", "1\u20E3", "🇪🇸", "👍🏽", "©\uFE0F", "‼\uFE0F", "☎\uFE0F", "↔\uFE0F", "🏳\uFE0F", "Café ❤\uFE0F Banco")) {
            assertEquals(t, t, plainReportText(t))
            assertEquals(t, safeFileName("a $t b").contains('\uFE0F'), plainReportText("a $t b").contains('\uFE0F'))
        }
        assertEquals(compareKey("❤ Banco"), compareKey("❤\uFE0F Banco"))
        assertEquals(compareKey("Café"), compareKey("Café\uFE0F"))
        // Accepted (R9, R43): a ZWJ sequence shows as its separate emoji, a tag flag as the black flag.
        assertEquals("👨👩👧", plainReportText("👨\u200D👩\u200D👧"))
        assertEquals("🏴", plainReportText("🏴\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F"))
    }

    /**
     * RT-9. `drawsNothing` (core/Invisible.kt:32-39) is Cf plus fixed ranges, and misses the default ignorable code points
     * that are not assigned: U+2065 and U+FFF0–U+FFF8 (Default_Ignorable_Code_Point; HarfBuzz, which Android's text stack
     * uses, hides them). `plainReportText` (imports/ImportModel.kt:117-144) keeps them, so «Banco» + U+2065 is shown as
     * «Banco» and is not a duplicate of «Banco»: RT-2's shadow entry once more. And the one list of R43 is not one: the
     * names of files drop them (`isHiddenInName`, crypto/FileCrypto.kt:175-179, through the card list,
     * crypto/pk/Card.kt:243-244), what is shown does not.
     */
    @Test fun rt9_unassignedDefaultIgnorablesAreNotShownNorKeptApart() {
        val missed = (listOf(0x2065) + (0xFFF0..0xFFF8)).filter { cp ->
            plainReportText("Banco${str(cp)}") != "Banco" || compareKey("Ban${str(cp)}co") != compareKey("Banco")
        }
        assertTrue("shown or kept apart: " + missed.joinToString { cpName(it) }, missed.isEmpty())
        // What a name drops and draws nothing must also go from what is shown (the other half of InvisibleTest).
        val namesOnly = (0..0x10FFFF).filter { cp ->
            cp !in 0xD800..0xDFFF && isHiddenInName(cp) && !drawsNothing(cp) && !Character.isISOControl(cp) &&
                Character.getType(cp) != Character.PRIVATE_USE.toInt() && !(cp in 0xFDD0..0xFDEF || (cp and 0xFFFE) == 0xFFFE) &&
                cp != 0x2028 && cp != 0x2029 && cp != 0x200D && cp != 0xFE0F &&
                // Ruling R52: a name drops the prepended concatenation marks, which the lists show (RT-13).
                cp !in VISIBLE_PREPENDED_MARKS
        }
        assertTrue("dropped from names, shown in lists: " + namesOnly.joinToString { cpName(it) }, namesOnly.isEmpty())
    }

    /**
     * RT-13 (Info). The other side of the same list: `drawsNothing` takes every Cf, and some Cf DO draw: the Arabic number
     * signs U+0600–U+0605, U+06DD (end of ayah), U+070F, U+0890–U+0891, U+08E2, U+110BD and U+110CD (the
     * Prepended_Concatenation_Mark ones, which Unicode leaves out of Default_Ignorable_Code_Point because they are meant
     * to be seen). They are dropped from what is shown and from the key: the list shows less than the title has,
     * and two titles that look different compare equal (a false duplicate, the user chooses). Safe behaviour: what draws
     * is shown.
     */
    @Test fun rt13_formatCharactersThatDrawAreShown() {
        val dropped = listOf(0x0600, 0x0601, 0x0605, 0x06DD, 0x070F, 0x08E2, 0x110BD).filter { cp ->
            plainReportText("Banco ${str(cp)}1") == "Banco 1"
        }
        assertTrue("dropped: " + dropped.joinToString { cpName(it) }, dropped.isEmpty())
    }

    // ---------------------------------------------------------------------------------------------------------------
    // RT-7 (R44): NFKC in compareKey.
    // ---------------------------------------------------------------------------------------------------------------

    /** Compatibility forms that look the same compare equal; the stored and shown text is not changed. */
    @Test fun resistsCompatibilityFormsAroundTheRt7Fix() {
        for ((a, b) in listOf(
            "Oﬃce" to "Office", "Cuenta Ⅻ" to "Cuenta XII", "Ｂａｎｃｏ" to "Banco", "𝔅anco" to "Banco", "Banco․es" to "Banco.es",
            "ｶﾞｽ" to "ガス", "ǲenana" to "Dzenana", "ſeguro" to "seguro", "µ" to "μ", "Ｂａｎｃｏ\u3000Ｘ" to "Banco X",
            "ﬀ\u00A0Banco" to "ff Banco", "Café\uFE0F" to "Café", "e\u0301\uFE0F\u0301" to "é\u0301",
        )) {
            assertEquals("«$a» vs «$b»", compareKey(b), compareKey(a))
        }
        // Only for comparing: what is shown keeps the form (R9, R10).
        assertEquals("Oﬃce", plainReportText("Oﬃce"))
        // A title and a user that become the same only by NFKC are a duplicate; the vault's entry is changed only on «Reemplazar».
        val vault = listOf(vaultLogin("https://banco.es/"))
        val item = fileLogin("https://banco.es/", title = "Ｂａｎｃｏ", user = "ａｎａ")
        assertTrue(plan(item, vault).duplicates.isNotEmpty())
        assertEquals("Banco", mergedEntries(plan(item, vault), DuplicateChoice.SKIP, emptyMap(), vault, 2, { "n" }).single().title)
        // NFKC never brings back what draws nothing, nor a run of spaces, nor ends with one.
        for (t in listOf("\uFDFA", "Banco\u2000\u2001X", "\u3000Banco\uFF0E", "Banco\u2028X")) {
            val k = compareKey(t)
            assertFalse("«$t» → «$k»", k.codePoints().anyMatch(::drawsNothing) || k.startsWith(" ") || k.endsWith(" ") || "  " in k)
        }
    }

    /**
     * NFKC does not make a key that throws or grows without bound: the longest expansion (U+FDFA, 18 code points) over a
     * title at the import limit stays a few MB for one key, and lone surrogates and every plane go through.
     */
    @Test fun resistsNfkcExpansionAndOddInput() {
        val k = compareKey("\uFDFA".repeat(1024))
        assertTrue(k.length in 1024..20 * 1024)
        for (cp in listOf(0x10FFFF, 0xFFFF, 0xFDD0, 0xE000, 0x1F1EA)) compareKey("a${str(cp)}b")
        compareKey("\uD800\uDBFF\uDC00\uDFFF")
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The QR Inspector's own list (tools/qr/Visible.kt), one more target.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * RT-10. `isHiddenCodePoint` (tools/qr/Visible.kt:85-97) has its own list, and it misses code points that draw
     * nothing and that the shared list knows (`drawsNothing`, core/Invisible.kt:32-39) or that are default ignorable:
     * U+034F, U+17B4, U+17B5, U+180B–U+180D, U+180F, U+2800, U+2065, U+206A–U+206F, U+FFF0–U+FFF8, U+FFF9–U+FFFB, U+1BCA0–U+1BCA3,
     * U+1D173–U+1D17A, and plane 14 past U+E007F except the selectors. A QR whose text, Wi-Fi name or link carries one is
     * shown without a mark, with no «caracteres ocultos» alert and no notice when copied (`QrView.kt:115`, `:203`, `:206`):
     * «Casa» + U+2800 passes for the network «Casa». Safe behaviour: marked.
     */
    @Test fun rt10_theQrInspectorMarksEverythingThatDrawsNothing() {
        val cps = listOf(0x034F, 0x17B4, 0x17B5, 0x180B, 0x180C, 0x180D, 0x180F, 0x2800, 0x2065, 0x206A, 0x206F, 0xFFF0,
            0xFFF9, 0xFFFA, 0xFFFB, 0x1BCA0, 0x1D173, 0xE0080, 0xE01F0)
        val unmarked = cps.filter { cp -> !hasHiddenChars("Casa${str(cp)}") || visibleText("Casa${str(cp)}") == "Casa${str(cp)}" }
        assertTrue("unmarked: " + unmarked.joinToString { cpName(it) }, unmarked.isEmpty())
    }

    /** What the Inspector already marks, and the emoji it must not mark. */
    @Test fun resistsTheQrInspectorsOwnList() {
        for (cp in listOf(0x200B, 0x200D, 0x2060, 0x2064, 0xFEFF, 0x00AD, 0x115F, 0x3164, 0xFFA0, 0x180E, 0x202E, 0x2066, 0x061C, 0xFE00, 0xE0041, 0xE0100)) {
            assertTrue(cpName(cp), hasHiddenChars("Casa${str(cp)}"))
            assertTrue(cpName(cp), visibleText("Casa${str(cp)}").endsWith("\u27E9"))
        }
        for (t in listOf("Casa ❤\uFE0F", "1\uFE0F\u20E3", "#\uFE0F\u20E3", "🇪🇸", "👍🏽", "☎\uFE0F")) assertFalse(t, hasHiddenChars(t))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Third check (db42d3e, ce6b0f9): the fixes of RT-8, RT-9, RT-10, RT-12 and RT-13 (rulings R45 to R49, R51, R52).
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * RT-14. `urlHost`, `urlKey` and `shownUrl` (imports/MergePlan.kt:389, :406, :445) give the raw text to
     * `comparedLink` (:429), whose `schemeOf` (tools/qr/LinkSignals.kt:119) refuses a scheme with a tab or a line break
     * inside or a C0 control in front. The text then gets `https://` in front, and `parseLink` (which drops the tabs and
     * trims the controls, as a browser does, LinkSignals.kt:63) reads what the browser takes as the scheme as the start of
     * the authority; a «@» after it moves the host to banco.es. So `ht\ttp:@banco.es` and `\u0001http:@banco.es`, which a
     * browser opens as http://banco.es/, have the key «hhttps://banco.es»: a DUPLICATE of the vault's https entry (RT-4
     * again) and of a URL-less one (R48 again), with the row «ana · banco.es» (R45 again). The same with
     * `java\tscript:alert(1)<!--@banco.es` (a classic script: «<!--» starts a comment, Annex B of ECMAScript).
     * `ht\ttps://banco.es` is https, but its row says «ana · https». Safe behaviour: no duplicate unless the browser opens
     * https on banco.es, or the row shows what the browser opens.
     */
    @Test fun rt14_aTabOrALeadingControlDoesNotHideTheScheme() {
        // (URL, the scheme a browser reads after dropping tabs and line breaks and trimming C0 controls and spaces).
        val cases = listOf(
            "ht\ttp:@banco.es" to "http", "\u0001http:@banco.es" to "http", "HT\nTP:x@banco.es/login" to "http",
            "http\t:@banco.es" to "http", "java\tscript:alert(1)<!--@banco.es" to "javascript",
            "\u0001javascript:alert(1)<!--@banco.es" to "javascript", "ht\ttps://banco.es" to "https",
        )
        val wrong = mutableListOf<String>()
        for ((url, scheme) in cases) {
            assertEquals("the browser's reading of «$url»", scheme, parseLink(url).scheme)
            val row = duplicateSubline(fileLogin(url).entry)
            for (vault in listOf(listOf(vaultLogin("https://banco.es/")), listOf(vaultLogin("")))) {
                val dup = plan(fileLogin(url), vault).duplicates.isNotEmpty()
                val rowTells = if (scheme == "https") row == "ana · banco.es" else "$scheme:" in row
                if (dup && !rowTells) wrong += "«${url.replace("\t", "\\t").replace("\n", "\\n").replace("\u0001", "\\u0001")}» vault «${vault[0].url}»: row «$row»"
            }
        }
        // A browser CSV carries both as they are, so the attack comes in through «Importar de otra app».
        val csv = "name,url,username,password,note\nBanco,\"ht\ttp:@banco.es\",ana,x,\nBanco,\"\u0001http:@banco.es\",ana,y,\n"
        val read = (readImport(csv.toByteArray()) as ReadResult.Ok).read
        assertEquals(listOf("ht\ttp:@banco.es", "\u0001http:@banco.es"), read.items.map { it.entry.url })
        assertTrue("duplicates whose row hides what the browser opens: " + wrong.joinToString("; "), wrong.isEmpty())
    }

    /** R48 with every https spelling against a URL-less entry, and what must stay a new entry; the row hides user info. */
    @Test fun resistsTheRt8FixVariants() {
        val bare = listOf(vaultLogin(""))
        for ((url, row) in listOf(
            "HTTPS:banco.es" to "ana · banco.es", "https:/banco.es" to "ana · banco.es", "https:\\\\banco.es" to "ana · banco.es",
            "https://ana:clave@banco.es/" to "ana · banco.es", "banco.es" to "ana · banco.es", "//banco.es/x" to "ana · banco.es",
            "https://www.banco.es:8443/x?y#z" to "ana · banco.es", "  https://banco.es  " to "ana · banco.es",
        )) {
            assertTrue("«$url»", plan(fileLogin(url), bare).duplicates.isNotEmpty())
            assertEquals("«$url»", row, duplicateSubline(fileLogin(url).entry))
            assertEquals("«$url»", url, replaced(fileLogin(url), bare).single().url)
        }
        for (url in listOf(
            "http://banco.es/", "HTTP:banco.es", "http:\\\\banco.es", "ftp://banco.es", "ws://banco.es", "wss://banco.es",
            "javascript:x@banco.es/", "javascript://banco.es/%0Aalert(1)", "mailto:ana@banco.es", "https://", "intent://banco.es/#Intent;end",
            "ana:clave@banco.es", "data:text/plain,banco.es", "file:///sdcard/x",
        )) {
            assertTrue("«$url» is a duplicate of a URL-less entry", plan(fileLogin(url), bare).duplicates.isEmpty())
            assertEquals("«$url»", listOf("", url), replaced(fileLogin(url), bare).map { it.url })
        }
        // The https entry and a URL-less one: http goes to neither; https to the https one; no URL to the first.
        val both = listOf(vaultLogin("https://banco.es/"), vaultLogin("").copy(id = "v2"))
        assertTrue(plan(fileLogin("http://banco.es/"), both).duplicates.isEmpty())
        assertEquals("v1", plan(fileLogin("https://banco.es/x"), both).duplicates.single().existingId)
        assertEquals("v1", plan(fileLogin(""), both).duplicates.single().existingId)
    }

    /** R45: the row never shows user info, whatever its form, and shows a scheme that is not https. */
    @Test fun resistsTheRowShowingUserInfo() {
        for ((url, row) in listOf(
            "https://ana:clave@banco.es" to "ana · banco.es", "https://a@clave@banco.es" to "ana · banco.es",
            "https://ana%40x:clave@banco.es" to "ana · banco.es", "https://:clave@banco.es" to "ana · banco.es",
            "https://clave:@banco.es" to "ana · banco.es", "HTTPS://ANA:CLAVE@BANCO.ES:8443/X" to "ana · banco.es",
            "https:\\\\ana:clave@banco.es" to "ana · banco.es", "//ana:clave@banco.es" to "ana · banco.es",
            "http://ana:clave@banco.es" to "ana · http://banco.es", "ftp://ana:clave@banco.es" to "ana · ftp://banco.es",
            "sftp://ana:cla\\ve@banco.es" to "ana · sftp://banco.es", "javascript://clave@banco.es/" to "ana · javascript://banco.es",
            "ana:clave@banco.es" to "ana · ana:…@banco.es", "ana:1234@banco.es" to "ana · ana:…@banco.es",
            "mailto:clave@banco.es" to "ana · mailto:…@banco.es", "MAILTO:a@clave@banco.es?cc=x" to "ana · MAILTO:…@banco.es?cc=x",
            "https://clave@" to "ana · https://…@", "https://ana:clave@/x" to "ana · https://…@/x",
            "xy:cl\u200Bave@banco.es" to "ana · xy:…@banco.es", "xy:\u202Eclave@banco.es" to "ana · xy:…@banco.es",
            // One letter and a colon is no scheme (a drive letter): read as https, so the user info goes with the rest.
            "x:clave@banco.es" to "ana · banco.es",
        )) {
            assertEquals("«$url»", row, duplicateSubline(fileLogin(url).entry))
        }
    }

    /** R51: without a host only the scheme is folded; the text after it is compared exactly. */
    @Test fun resistsTheRt12FixVariants() {
        val vault = listOf(vaultLogin("data:text/plain;base64,SGVsbG8="))
        // A leading control goes first, as a browser drops it (ruling R54, RT-14).
        for (url in listOf("DATA:text/plain;base64,SGVsbG8=", " data:text/plain;base64,SGVsbG8= ", "Data:text/plain;base64,SGVsbG8=", "\u0001data:text/plain;base64,SGVsbG8=")) {
            assertTrue("«$url»", plan(fileLogin(url), vault).duplicates.isNotEmpty())
        }
        for (url in listOf("data:text/plain;base64,sGVSbG8=", "data:TEXT/plain;base64,SGVsbG8=", "data:text/plain;base64,SGVsbG8")) {
            assertTrue("«$url»", plan(fileLogin(url), vault).duplicates.isEmpty())
        }
        assertEquals("ana · data:text/plain;base64,SGVsbG8=", duplicateSubline(fileLogin("data:text/plain;base64,SGVsbG8=").entry))
    }

    /**
     * R49: the fixed list is Unicode's. Default_Ignorable_Code_Point and the format characters (Cf) of Unicode 17.0
     * (DerivedCoreProperties.txt and extracted/DerivedGeneralCategory.txt, the same ranges as in 15.0 and 16.0) are all in
     * [drawsNothing] or in the visible prepended marks; nothing the JDK calls Cf is outside both; surrogate halves are not
     * in the list, and what is shown and compared drops them (a file name writes «?», as UTF-8 always did).
     */
    @Test fun resistsTheRt9FixAgainstTheUnicodeData() {
        val di = listOf(0x00AD..0x00AD, 0x034F..0x034F, 0x061C..0x061C, 0x115F..0x1160, 0x17B4..0x17B5, 0x180B..0x180F,
            0x200B..0x200F, 0x202A..0x202E, 0x2060..0x206F, 0x3164..0x3164, 0xFE00..0xFE0F, 0xFEFF..0xFEFF, 0xFFA0..0xFFA0,
            0xFFF0..0xFFF8, 0x1BCA0..0x1BCA3, 0x1D173..0x1D17A, 0xE0000..0xE0FFF)
        val cf17 = listOf(0x00AD..0x00AD, 0x0600..0x0605, 0x061C..0x061C, 0x06DD..0x06DD, 0x070F..0x070F, 0x0890..0x0891,
            0x08E2..0x08E2, 0x180E..0x180E, 0x200B..0x200F, 0x202A..0x202E, 0x2060..0x2064, 0x2066..0x206F, 0xFEFF..0xFEFF,
            0xFFF9..0xFFFB, 0x110BD..0x110BD, 0x110CD..0x110CD, 0x13430..0x1343F, 0x1BCA0..0x1BCA3, 0x1D173..0x1D17A,
            0xE0001..0xE0001, 0xE0020..0xE007F)
        for (r in di) for (cp in r) assertTrue(cpName(cp), drawsNothing(cp))
        for (r in cf17) for (cp in r) assertTrue(cpName(cp), drawsNothing(cp) != (cp in VISIBLE_PREPENDED_MARKS))
        for (cp in 0..0x10FFFF) {
            if (Character.getType(cp) == Character.FORMAT.toInt()) assertTrue(cpName(cp), drawsNothing(cp) || cp in VISIBLE_PREPENDED_MARKS)
        }
        val inDi = { cp: Int -> di.any { cp in it } }
        val extra = (0..0x10FFFF).filter { drawsNothing(it) && !inDi(it) }
        assertEquals((listOf(0x2800) + (0xFFF9..0xFFFB) + (0x13430..0x1343F)).map(::cpName), extra.map(::cpName))
        for (cp in 0xD800..0xDFFF) assertFalse(cpName(cp), drawsNothing(cp))
        for (t in listOf("Ban\uD800co", "Ban\uDFFFco")) {
            assertEquals("Banco", plainReportText(t))
            assertEquals(compareKey("Banco"), compareKey(t))
        }
    }

    /** R49 and R52: the thirteen prepended marks show in the lists, compare apart, are not marked, and leave names. */
    @Test fun resistsTheThirteenMarksInNamesAndDisplay() {
        assertEquals(13, VISIBLE_PREPENDED_MARKS.size)
        for (cp in VISIBLE_PREPENDED_MARKS) {
            val m = str(cp)
            assertEquals(cpName(cp), "Banco ${m}12", plainReportText("Banco ${m}12"))
            assertTrue(cpName(cp), compareKey("Banco ${m}12") != compareKey("Banco 12"))
            assertTrue(cpName(cp), isHiddenInName(cp))
            assertEquals(cpName(cp), "factura 12.pdf", safeFileName("factura ${m}12.pdf"))
            assertFalse(cpName(cp), hasHiddenChars("Casa ${m}12"))
            assertEquals(cpName(cp), "Casa ${m}12", visibleText("Casa ${m}12"))
        }
    }

    /** R46: the Inspector marks every point of [drawsNothing], also right after an emoji, but for its emoji exceptions. */
    @Test fun resistsTheRt10FixWithEveryPointAndTheEmojiExceptions() {
        val unmarked = mutableListOf<Int>()
        for (cp in 0..0x10FFFF) {
            if (!drawsNothing(cp)) continue
            val c = str(cp)
            if (!hasHiddenChars("a$c") || visibleText("a$c") != "a⟨${cpName(cp)}⟩") unmarked += cp
            if (cp != 0xFE0E && cp != 0xFE0F && !hasHiddenChars("❤$c")) unmarked += cp
        }
        assertTrue("unmarked: " + unmarked.take(20).joinToString { cpName(it) }, unmarked.isEmpty())
        // The exceptions: after an emoji or a symbol, and the keycap; and only those.
        for (t in listOf("❤\uFE0F", "❤\uFE0E", "©\uFE0F", "😀\uFE0F", "1\uFE0F\u20E3", "#\uFE0F\u20E3")) assertFalse(t, hasHiddenChars(t))
        for (t in listOf("a\uFE0F", "1\uFE0E\u20E3", "1\uFE0F", "❤\uFE0F\uFE0F", "❤\uFE00", "❤\uDB40\uDD00", "文\uFE0F", "\uFE0F❤", "\u2800\uFE0F")) {
            assertTrue(t, hasHiddenChars(t))
        }
        // In a host every selector counts.
        assertTrue(hasHiddenChars("❤\uFE0F", allowEmojiVariation = false))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Fourth check (f6148bb): the shared pre-pass asBrowserReadsIt (R54) and «word:digits» (R53).
    // ---------------------------------------------------------------------------------------------------------------

    private fun esc(s: String) = buildString {
        for (c in s) {
            val odd = c < ' ' || c.code in 0x7F..0xA0 || c == '：' || drawsNothing(c.code) || (Character.isSpaceChar(c) && c != ' ') || c.code == 0x2800 ||
                c.code == 0x3164 || Character.getType(c) == Character.NON_SPACING_MARK.toInt()
            if (odd) append("\\u%04X".format(c.code)) else append(c)
        }
    }

    /**
     * What the basic URL parser of the WHATWG URL Standard does before and during the scheme state: every C0 control
     * and space at either end, and every tab, LF and CR anywhere, in any case of the scheme. Each of them gives the key a
     * browser's reading gives (`asBrowserReadsIt`, tools/qr/LinkSignals.kt:62), so http and javascript never meet https,
     * and the row says the scheme.
     */
    @Test fun resistsTheRt14FixAgainstThePrePass() {
        val https = listOf(vaultLogin("https://banco.es/"))
        val bare = listOf(vaultLogin(""))
        val forms = mutableListOf<String>()
        for (c in (0x00..0x20).map { it.toChar() }) { forms += "${c}http:@banco.es"; forms += "http:@banco.es$c" }
        for (c in listOf('\t', '\n', '\r')) for (i in 0.."http:".length) forms += "http:".substring(0, i) + c + "http:".substring(i) + "@banco.es"
        forms += listOf("hTtP\t:@banco.es", "\t\n\r\u0000 HtTp:\t/\n/x@banco.es", "h\tt\nt\rp:\\\\@banco.es", "\u001F http:@banco.es\u0000\u001F")
        for (u in forms) {
            assertEquals(esc(u), "hhttp://banco.es", urlKey(u))
            assertTrue(esc(u), plan(fileLogin(u), https).duplicates.isEmpty() && plan(fileLogin(u), bare).duplicates.isEmpty())
            assertEquals(esc(u), "ana · http://banco.es", duplicateSubline(fileLogin(u).entry))
        }
        for (u in listOf("JAVA\tSCRIPT:alert(1)<!--@banco.es", "\u0000j\na\rv\ta script:alert(1)<!--@banco.es".replace(" ", ""), "javascript\t:alert(1)<!--@banco.es")) {
            assertTrue(esc(u), urlKey(u)!!.startsWith("tjavascript:"))
            assertTrue(esc(u), plan(fileLogin(u), https).duplicates.isEmpty() && plan(fileLogin(u), bare).duplicates.isEmpty())
            assertEquals(esc(u), "ana · javascript:…@banco.es", duplicateSubline(fileLogin(u).entry).lowercase())
        }
    }

    /**
     * Characters the pre-pass leaves alone because the URL Standard does too: a no-break space, U+3000, U+200B, a C0
     * control inside the scheme, and the full-width colon «：». A browser's parser finds no scheme in any of them
     * (`parseLink(u).scheme` is empty, as for a text without a scheme), so it never opens them as http or javascript; the
     * merge reads them as a text without a scheme, https. What an address bar does when they are pasted (it may fix them
     * up) is a phone check, not a parser one.
     */
    @Test fun resistsWhatTheUrlStandardDoesNotTakeAsAScheme() {
        for (u in listOf(
            "ht\u00A0tp:@banco.es", "http\u00A0:@banco.es", "ht\u3000tp:@banco.es", "ht\u200Btp:@banco.es", "ht\u0001tp:@banco.es",
            "http\u0000:@banco.es", "http：@banco.es", "http：//banco.es", "ｈｔｔｐ:@banco.es", "java\u00ADscript:alert(1)<!--@banco.es",
        )) {
            assertEquals(esc(u), "", parseLink(u).scheme)
            val k = urlKey(u)!!
            assertFalse(esc(u) + " → $k", k.startsWith("hhttp://") || k.startsWith("tjavascript:"))
        }
    }

    /** R53: «word:digits» is host and port only with a «.» or `localhost`; IPv4 and bracketed IPv6 forms; schemes with a dot. */
    @Test fun resistsTheR53HostAndPortRule() {
        for ((u, key) in listOf(
            "localhost:8080" to "hhttps://localhost", "LOCALHOST:80" to "hhttps://localhost", "localhost.:8080" to "hhttps://localhost",
            "banco.es:8443" to "hhttps://banco.es", "Banco.ES:443/x" to "hhttps://banco.es", "a.b:1/" to "hhttps://a.b",
            "0x7f.1:80" to "hhttps://127.0.0.1", "127.1:80" to "hhttps://127.0.0.1", "[::1]:8080" to "hhttps://[::1]",
            "javascript:1/alert(1)" to "tjavascript:1/alert(1)", "data:1/x" to "tdata:1/x", "intranet:8080" to "tintranet:8080",
            "JavaScript:1" to "tjavascript:1", "localhostx:80" to "tlocalhostx:80",
        )) {
            assertEquals(esc(u), key, urlKey(u))
        }
        // A word with a dot before digits is read as a host: never javascript, never banco.es unless the word is it.
        assertEquals("javascript.", parseLink("javascript.:1/alert(1)").scheme)
        assertEquals("hhttps://javascript", urlKey("javascript.:1/alert(1)"))
        assertEquals("hhttps://com.evil.app", urlKey("com.evil.app:1/@banco.es"))
        assertTrue(urlKey("com.evil.app:1@banco.es")!!.startsWith("t"))
        assertFalse(urlKey("[fe80::1%25eth0]:80")!!.contains("banco.es"))
        val bare = listOf(vaultLogin(""))
        for (u in listOf("javascript:1/alert(1)", "data:1/x", "intranet:8080", "javascript.:1/alert(1)")) {
            assertTrue(u, plan(fileLogin(u), listOf(vaultLogin("https://banco.es/"))).duplicates.isEmpty())
        }
        assertTrue(plan(fileLogin("javascript:1/alert(1)"), bare).duplicates.isEmpty())
    }

    /**
     * Property, over 300 000 strings from an alphabet of the characters that matter (scheme letters, «:», «/», «\», «@»,
     * «.», digits, tab, LF, CR, C0 controls, spaces, NBSP, U+3000, U+200B, «：», «%», «#», «?», «[», «]», «<!--»): whenever
     * the merge reads a URL as https on banco.es (a duplicate of the https entry, and the one key a URL-less entry takes),
     * a browser's parser reads it as https on banco.es too, or finds no scheme at all (it is not a link it opens). And the
     * row never shows a scheme the browser does not read.
     */
    @Test fun resistsAPropertyOverRandomLinks() {
        val pieces = listOf(
            "h", "t", "p", "s", "j", "a", "v", "c", "r", "i", "f", "l", "e", "x", ":", ":", "/", "/", "\\", "@", "@", ".", "0", "1", "8",
            "\t", "\n", "\r", "\u0001", "\u0000", " ", "\u00A0", "\u3000", "\u200B", "：", "%", "#", "?", "[", "]", "<!--",
            "http", "https", "javascript", "banco.es", "banco.es", "www.", "localhost",
        )
        val rnd = SplittableRandom(20261109)
        val wrong = mutableListOf<String>()
        var hits = 0
        repeat(300_000) {
            val u = buildString { repeat(1 + rnd.nextInt(12)) { append(pieces[rnd.nextInt(pieces.size)]) } }
            val key = urlKey(u) ?: return@repeat
            if (key != "hhttps://banco.es") return@repeat
            hits++
            val b = parseLink(u)
            val ok = b.scheme == "" || (b.scheme == "https" && b.host?.removePrefix("www.") == "banco.es") ||
                // A word with a dot before a port: the merge reads it as a host, «banco.es:443».
                ('.' in b.scheme || b.scheme == "localhost")
            if (!ok && wrong.size < 10) wrong += "${esc(u)} → browser ${b.scheme}://${b.host}"
            val row = duplicateSubline(fileLogin(u).entry)
            if (row != "ana · banco.es" && wrong.size < 10) wrong += "${esc(u)} → row «$row»"
        }
        assertTrue(wrong.joinToString("; "), wrong.isEmpty())
        // The draw reaches the case often enough to mean something.
        assertTrue("$hits", hits > 2_000)
    }

    /** R45 with N-1: the row hides everything up to the last «@», also with «/», «?», «#», tabs or a control before it. */
    @Test fun resistsTheRowWithTheLastAtRule() {
        for ((u, row) in listOf(
            "ana:cla/ve@banco.es" to "ana · ana:…@banco.es", "ana:cl?ave@banco.es" to "ana · ana:…@banco.es",
            "ana:cl#ave@banco.es" to "ana · ana:…@banco.es", "mailto:a@b.es?cc=clave@d.es" to "ana · mailto:…@d.es",
            "ma\til\tto:cl\tave@banco.es" to "ana · mailto:…@banco.es", "\u0001mailto:clave@banco.es" to "ana · mailto:…@banco.es",
            "https://cl\tave@banco.es" to "ana · banco.es", "\u0000http://clave:x@banco.es" to "ana · http://banco.es",
            "javascript:alert('clave')//@banco.es" to "ana · javascript:…@banco.es", "data:clave@" to "ana · data:…@",
        )) {
            assertEquals(esc(u), row, duplicateSubline(fileLogin(u).entry))
        }
    }

    /**
     * parseLink and the QR Inspector did not change: the pre-pass is the expression parseLink had (50b0d51,
     * LinkSignals.kt), it is idempotent, and parseLink reads a text and its pre-passed form the same.
     */
    @Test fun resistsAChangeInParseLink() {
        val old = { s: String -> s.filterNot { it == '\t' || it == '\n' || it == '\r' }.trim { it <= ' ' || it.isWhitespace() } }
        val rnd = SplittableRandom(4)
        val alphabet = "\t\n\r\u0000\u0001\u001F \u00A0\u3000\u2028\u200Bhttps:/\\@banco.es[]%#?"
        repeat(50_000) {
            val u = buildString { repeat(rnd.nextInt(16)) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
            val pre = asBrowserReadsIt(u)
            assertEquals(esc(u), old(u), pre)
            assertEquals(esc(u), pre, asBrowserReadsIt(pre))
            val a = parseLink(u)
            val b = parseLink(pre)
            assertEquals(esc(u), listOf(a.scheme, a.host, a.port, a.hasUserInfo, a.isIp, a.hostHidden), listOf(b.scheme, b.host, b.port, b.hasUserInfo, b.isIp, b.hostHidden))
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Fifth check (fd9bd4b): «Reemplazar» end to end (R55, R56).
    // ---------------------------------------------------------------------------------------------------------------

    private fun fileItem(title: String, user: String, url: String, notes: String = "") =
        ImportedItem(0, importedEntry(EntryType.LOGIN, title, username = user, password = "la-del-atacante", url = url, notes = notes), emptyList())

    /**
     * RT-15. The matcher finds a duplicate when title and user are the SAME as the vault's for `compareKey` (titleKey,
     * userKey: what draws nothing gone, NFKC, spaces folded and trimmed), but `replaceWith` (imports/MergePlan.kt:267-268)
     * only keeps the vault's when the file's shows NOTHING (R55). So with «Reemplazar», «ana» + U+200B, «ana» + U+3000 at
     * the end, «Cafe» + U+0301 or «Ban» + U+2800 + «co» overwrite the vault's own user and title. Nothing changes on
     * screen (the row and the lists show `plainReportText`), but «Copiar» copies the stored text (VaultEntryScreen.kt:106,
     * `copySensitive(context, value)`), so the user pastes «ana» + an invisible character into the site, and the search
     * («fé banco») no longer finds «Café Ban\u2800co» (`query`, vault/EntryQuery.kt:25-34, compares the raw text). Safe behaviour: when
     * the matcher says they are the same, the vault's title and user stay.
     */
    @Test fun rt15_replaceKeepsATitleAndUserTheMatcherFoundTheSame() {
        val vault = listOf(vaultLogin("https://banco.es/").copy(title = "Café Banco"))
        val wrong = mutableListOf<String>()
        for ((title, user) in listOf(
            "Café Ban\u2800co" to "ana\u200B", "Cafe\u0301 Banco" to "ana\u3000", "Café\u00A0Banco" to "\u3164ana",
            "Café Banco\u034F" to "an\u00ADa", "  Café   Banco " to "ana\uFEFF",
        )) {
            val item = fileItem(title, user, "https://banco.es/")
            assertTrue(esc(title), plan(item, vault).duplicates.isNotEmpty())
            assertEquals("the row looks like the vault's user", "ana · banco.es", compareKey(duplicateSubline(item.entry)))
            val after = replaced(item, vault).single()
            if (after.title != "Café Banco" || after.username != "ana") wrong += "«${esc(after.title)}» / «${esc(after.username)}»"
        }
        // What that does: the search for the title misses the replaced entry.
        val shadowed = replaced(fileItem("Café Ban\u2800co", "ana", "https://banco.es/"), vault)
        if (shadowed.query(ListQuery(text = "fé banco")).isEmpty()) wrong += "search «fé banco» finds nothing"
        assertTrue("replaced with what only differs invisibly: " + wrong.joinToString("; "), wrong.isEmpty())
    }

    /**
     * RT-16 (Info). The other fields of `replaceWith` still use `isBlank`, not «shows nothing» (R55): a 2FA issuer or
     * account of U+200B, U+2800 or U+3164 (imports/MergePlan.kt:272, `ifBlank`) takes the place of the vault's, so the 2FA
     * list (VaultScreen.kt:475) shows the code without its service; and a login's notes of only U+200B are appended
     * with the «importado el…» line and nothing visible after it. The 2FA case needs the code's secret (a TOTP matches
     * by its key), so it is a hostile file that already knows the secret. Safe behaviour: what shows nothing is empty.
     */
    @Test fun rt16_replaceTreatsWhatShowsNothingAsEmptyInEveryField() {
        val code = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Banco", account = "ana")
        val vaultTotp = VaultEntry("t1", EntryType.TOTP, "Banco", totp = code, createdAt = 1, updatedAt = 1)
        val wrong = mutableListOf<String>()
        for (blank in listOf("\u200B", "\u2800", "\u3164", "\u0001")) {
            val incoming = importedEntry(EntryType.TOTP, "Banco", totp = code.copy(issuer = blank, account = blank))
            val after = replaceWith(vaultTotp, incoming, 2).totp!!
            if (after.issuer != "Banco" || after.account != "ana") wrong += "2FA ${esc(blank)}: «${esc(after.issuer)}» / «${esc(after.account)}»"
        }
        val login = vaultLogin("https://banco.es/").copy(notes = "mis notas")
        val notes = replaceWith(login, fileItem("Banco", "ana", "https://banco.es/", notes = "\u200B\u2800").entry, 2).notes
        if (notes != "mis notas") wrong += "notes «${esc(notes)}»"
        assertTrue(wrong.joinToString("; "), wrong.isEmpty())
    }

    /**
     * RT-17 (Info). A folder's tag keeps what draws nothing (`importTags`, imports/MergePlan.kt:152, through
     * `normalizeTags`, vault/EntryQuery.kt, which only trims spaces): a folder «\u200B» gives a chip «#» with nothing
     * after it, and «Banco\u200B» a chip «#banco» next to the vault's own «#banco» (the chips show `plainReportText`,
     * VaultScreen.kt:448), two filters that look the same; «Reemplazar» adds it to the vault's entry (`replaceWith` adds
     * the tags it lacks, compared raw). Safe behaviour: a folder name is read as it shows.
     */
    @Test fun rt17_aFolderTagIsReadAsItShows() {
        val wrong = mutableListOf<String>()
        for (folder in listOf("\u200B", "\u2800", "\u3164", "Banco\u200B", "Ban\u00ADco")) {
            val tags = importTags(listOf(folder), ImportFormat.BROWSER_CSV).drop(1)
            if (tags.any { plainReportText(it).isBlank() || (plainReportText(it) == "banco" && it != "banco") }) wrong += "${esc(folder)} → ${tags.map(::esc)}"
        }
        assertTrue(wrong.joinToString("; "), wrong.isEmpty())
    }

    /** R55: a URL, title or user of only controls, spaces or what draws nothing is empty for the matcher AND for «Reemplazar». */
    @Test fun resistsTheR55EmptyFields() {
        val vault = listOf(vaultLogin("https://banco.es/"))
        for (url in listOf("\u0001", "\u0000\u001F", " \t\n\r ", "\u0001\t\u0002")) {
            assertTrue(esc(url), plan(fileItem("Banco", "ana", url), vault).duplicates.isNotEmpty())
            assertEquals(esc(url), "https://banco.es/", replaced(fileItem("Banco", "ana", url), vault).single().url)
        }
        // A title or user that shows nothing never overwrites the vault's; a vault entry of only controls stays itself.
        val bare = listOf(VaultEntry("v1", EntryType.LOGIN, "\u0001", username = "\u200B", url = "\u0002", createdAt = 1, updatedAt = 1))
        for ((t, u) in listOf("\u200B" to "\u2800", "\u0001\u0001" to "", " " to "\u3164")) {
            val item = fileItem(t, u, "")
            assertTrue(esc(t), plan(item, bare).duplicates.isNotEmpty())
            val after = replaced(item, bare).single()
            assertEquals(listOf("\u0001", "\u200B", "\u0002"), listOf(after.title, after.username, after.url))
        }
        // A vault URL of only controls is «no URL»: it takes an https one (R48), not an http one.
        assertEquals("https://banco.es/x", replaced(fileItem("\u0001", "\u200B", "https://banco.es/x"), bare).single().url)
        assertEquals(listOf("\u0002", "http://banco.es/"), replaced(fileItem("\u0001", "\u200B", "http://banco.es/"), bare).map { it.url })
    }

    /** R56: a host that shows nothing is no host, so its URL is compared whole and never taken by a URL-less entry. */
    @Test fun resistsTheR56HostsThatShowNothing() {
        val bare = listOf(vaultLogin(""))
        for (url in listOf(
            "httpshttp@ /1HTTPS", "https://%C2%A0", "https://%E3%80%80/x", "https://\u00A0/", "https://%E2%80%8B",
            "https://\u200B\u200B", "https://%20%20@%20",
        )) {
            val key = urlKey(url)!!
            assertFalse(esc(url) + " → " + esc(key), key.startsWith("hhttps://"))
            assertTrue(esc(url), plan(fileLogin(url), bare).duplicates.isEmpty())
            assertTrue(esc(url), duplicateSubline(fileLogin(url).entry).length > "ana · ".length)
        }
    }

    /**
     * A host of a Hangul filler (U+3164) or the Braille blank (U+2800) is not «nothing» after the IDN step: it becomes
     * «xn--psd» or «xn--6ii», which the row shows.
     * A host of combining marks alone («https://%CC%81»): it shows a mark on nothing, which a browser refuses as a host
     * (UTS #46: a label cannot start with a mark). Recorded as it is: the row is not empty, and no browser opens it.
     */
    @Test fun resistsAHostOfCombiningMarksAlone() {
        for (url in listOf("https://%CC%81", "https://\u0301\u0301", "https://\u3164", "https://%E2%A0%80")) {
            val row = duplicateSubline(fileLogin(url).entry)
            assertTrue(esc(url) + " → «$row»", row.removePrefix("ana · ").isNotEmpty())
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Sixth check (2eaf393): «Reemplazar» field by field (R57 to R60).
    // ---------------------------------------------------------------------------------------------------------------

    /** R58: every title and user the matcher calls the same keeps the vault's spelling, whatever differs. */
    @Test fun resistsTheR58TitleAndUserVariants() {
        val vault = listOf(vaultLogin("https://banco.es/").copy(title = "Café Banco"))
        for ((title, user) in listOf(
            "CAFÉ BANCO" to "ana", "Ｃａｆé Ｂａｎｃｏ" to "ａｎａ", "\u0001Café\u0002Banco\u0003" to "\u0001ana", "Café\u2028Banco" to "ana\u2029",
            "Cafe\u0301 Banco" to "a\u200Dna", "Café Banco\uFE0F" to "ana\u2065", "Café Ban\uDB40\uDC20co" to "an\uFFF0a",
        )) {
            val item = fileItem(title, user, "https://banco.es/")
            assertTrue(esc(title), plan(item, vault).duplicates.isNotEmpty())
            val after = replaced(item, vault).single()
            assertEquals(esc(title), listOf("Café Banco", "ana"), listOf(after.title, after.username))
        }
        // What differs visibly still comes in only as a new entry, never over the vault's.
        assertTrue(plan(fileItem("Café Banco", "Ana", "https://banco.es/"), vault).duplicates.isEmpty())
        assertTrue(plan(fileItem("Cafe Banco", "ana", "https://banco.es/"), vault).duplicates.isEmpty())
    }

    /**
     * RT-19 (Info). R58 keeps the vault's title and user when the keys match, but a 2FA's issuer and account are not
     * compared: `replaceWith` (imports/MergePlan.kt:281) takes the file's whenever it shows something (R57, R59), also when
     * it only differs from the vault's by what draws nothing or by spaces: «Ban» + U+200B + «co», «ana» + U+3000,
     * «Ｂａｎｃｏ». The 2FA list shows the same, but the search (`query`, vault/EntryQuery.kt:25-34, which looks at the
     * issuer) no longer finds «banco». It needs a file with the code's secret (a 2FA is a duplicate by its key). Safe
     * behaviour: an issuer or account whose `compareKey` is the vault's keeps the vault's.
     */
    @Test fun rt19_replaceKeepsA2faIssuerAndAccountThatAreTheSame() {
        val code = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Banco", account = "ana")
        val vault = listOf(VaultEntry("t1", EntryType.TOTP, "Banco", totp = code, createdAt = 1, updatedAt = 1))
        val wrong = mutableListOf<String>()
        for ((issuer, account) in listOf("Ban\u200Bco" to "ana\u3000", "Banco\u2800" to "\u3164ana", "Ｂａｎｃｏ" to "an\u00ADa")) {
            val item = ImportedItem(0, importedEntry(EntryType.TOTP, "Banco", totp = code.copy(issuer = issuer, account = account)), emptyList())
            assertTrue(esc(issuer), plan(item, vault).duplicates.isNotEmpty())
            val after = mergedEntries(plan(item, vault), DuplicateChoice.REPLACE, emptyMap(), vault, 2, { "n" }).single()
            val t = after.totp!!
            if (t.issuer != "Banco" || t.account != "ana") wrong += "«${esc(t.issuer)}» / «${esc(t.account)}»"
        }
        val one = ImportedItem(0, importedEntry(EntryType.TOTP, "Banco", totp = code.copy(issuer = "Ban\u200Bco")), emptyList())
        val after = mergedEntries(plan(one, vault), DuplicateChoice.REPLACE, emptyMap(), vault, 2, { "n" })
        if (after.query(ListQuery(text = "banco", type = EntryType.TOTP)).isEmpty()) wrong += "search «banco» misses the 2FA"
        assertTrue(wrong.joinToString("; "), wrong.isEmpty())
    }

    /** R57 and R59: what shows nothing in a 2FA issuer, account or a login's notes never replaces nor adds anything; the row has no dangling separator. */
    @Test fun resistsTheR57AndR59EmptyFields() {
        val code = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Banco", account = "ana")
        val vaultTotp = VaultEntry("t1", EntryType.TOTP, "Banco", totp = code, createdAt = 1, updatedAt = 1)
        val login = vaultLogin("https://banco.es/").copy(notes = "mis notas")
        for (blank in listOf("", " ", "\u00A0\u3000", "\u0001\t\n", "\u200B\u2800\u3164", "\uDB40\uDC20", "\u034F\u115F\uFFA0", "\uFFF0\u2065")) {
            val t = replaceWith(vaultTotp, importedEntry(EntryType.TOTP, "Banco", totp = code.copy(issuer = blank, account = blank)), 2).totp!!
            assertEquals(esc(blank), listOf("Banco", "ana"), listOf(t.issuer, t.account))
            assertEquals(esc(blank), "mis notas", replaceWith(login, fileItem("Banco", "ana", "https://banco.es/", notes = blank).entry, 2).notes)
            val row = duplicateSubline(fileItem("Banco", blank, "https://banco.es/").entry)
            assertEquals(esc(blank), "banco.es", row)
            val totpRow = duplicateSubline(importedEntry(EntryType.TOTP, "Banco", totp = code.copy(issuer = blank)))
            assertEquals(esc(blank), "ana", totpRow)
            assertEquals(esc(blank), "", duplicateSubline(fileItem("Banco", blank, "").entry))
        }
        // Notes that differ from the vault's only around them are not appended; different ones are, once.
        for (same in listOf("  mis notas  ", "\nmis notas\n", "\tmis notas")) {
            assertEquals(esc(same), "mis notas", replaceWith(login, fileItem("Banco", "ana", "https://banco.es/", notes = same).entry, 2).notes)
        }
        val once = replaceWith(login, fileItem("Banco", "ana", "https://banco.es/", notes = "otra").entry, 2)
        assertEquals(once.notes, replaceWith(once, fileItem("Banco", "ana", "https://banco.es/", notes = "otra").entry, 3).notes)
    }

    /**
     * RT-20 (Info). `isNoUrl` (imports/MergePlan.kt:438) is «nothing left after the browser's pre-pass», which only
     * drops controls, spaces, tabs and line breaks. A URL of only what draws nothing (U+200B, U+2800, U+3164, U+034F…)
     * is not «no URL»: without a scheme it is read as `https://` + those characters, the IDN step turns what is left
     * into an `xn--` host («xn--psd796i»), and that https key is one a URL-less vault entry takes (R48). «Reemplazar»
     * then writes the invisible text into the vault's URL, whose field (VaultEntryScreen.kt:104, `plainReportText`)
     * shows nothing; «Copiar» copies the invisible characters. The row does say «ana · xn--psd796i». Safe behaviour: a URL
     * that shows nothing is no URL (`showsNothing`), as R55 and R59 decided for the other fields.
     */
    @Test fun rt20_aUrlThatShowsNothingIsNoUrl() {
        val bare = listOf(vaultLogin(""))
        val taken = listOf("\u200B\u2800\u3164", "\u2800", "\u3164", "\u2800\u2800", "\u3164\u034F", "\uFFA0", "\u115F", "\u200B\u2800").filter { url ->
            replaced(fileItem("Banco", "ana", url), bare).any { it.id == "v1" && it.url != "" }
        }
        assertTrue("taken into the URL-less entry: " + taken.joinToString { "${esc(it)} (row «${duplicateSubline(fileItem("Banco", "ana", it).entry)}»)" }, taken.isEmpty())
    }

    /**
     * RT-18 (Info). R60 reads a folder through `plainReportText` before `normalizeTags` (imports/MergePlan.kt:160-161), so
     * what draws nothing goes and a control is a space. But a no-break space (U+00A0, U+2007, U+202F) inside is kept (the
     * `\s` of `SPACES` and `normalizeTags` only fold ASCII spaces inside), and nothing is in NFC: «Banco» + NBSP + «Casa»
     * and «Banco Casa», or «Banco» + U+0301 and «Bancó», give two tags whose chips look the same, «#banco casa» and «#bancó»
     * (VaultScreen.kt:448), next to the vault's own, and «Reemplazar» adds the second to the vault's entry. It is the
     * second «#banco» R60 meant to close, with a character that is not invisible but looks like another. Safe behaviour:
     * a folder that looks like an existing tag is that tag.
     */
    @Test fun rt18_aFolderThatLooksLikeATagIsThatTag() {
        val wrong = mutableListOf<String>()
        for ((folder, same) in listOf(
            "Banco\u00A0Casa" to "Banco Casa", "Banco\u2007Casa" to "Banco Casa", "Banco\u202FCasa" to "Banco Casa",
            "Banco\u0301" to "Bancó", "Cafe\u0301" to "Café",
        )) {
            val a = importTags(listOf(folder), ImportFormat.BROWSER_CSV)
            val b = importTags(listOf(same), ImportFormat.BROWSER_CSV)
            if (a != b) wrong += "${esc(folder)} → ${a.map(::esc)} vs ${b.map(::esc)}"
        }
        // And «Reemplazar» adds it next to the vault's own.
        val vault = listOf(vaultLogin("https://banco.es/").copy(tags = listOf("café")))
        val item = ImportedItem(0, importedEntry(EntryType.LOGIN, "Banco", username = "ana", password = "x", url = "https://banco.es/"), listOf("Cafe\u0301"))
        val tags = mergedEntries(plan(item, vault), DuplicateChoice.REPLACE, emptyMap(), vault, 2, { "n" }).single().tags
        if (tags.map { plainReportText(it) }.count { it == "café" } > 1) wrong += "two «#café»: ${tags.map(::esc)}"
        assertTrue(wrong.joinToString("; "), wrong.isEmpty())
    }

    /** R60: folders that show nothing give no tag, controls are spaces, case and spaces fold; MAX_TAGS and the 100-tag budget hold. */
    @Test fun resistsTheR60FolderTags() {
        val fmt = ImportFormat.BROWSER_CSV
        for (f in listOf("\u200B", "\u2800", "\u3164", "\u0001", "\u00A0", "\u3000", "\uFEFF\u034F", "\uDB40\uDC20", ",,,", " , ")) {
            assertEquals(esc(f), listOf(fmt.originTag), importTags(listOf(f), fmt))
        }
        for ((a, b) in listOf("Banco\u200B" to "banco", "BAN\u00ADCO" to "banco", "Banco\u0001Casa" to "banco casa", "Banco\tCasa" to "banco casa",
            "\u2066Banco\u2069" to "banco", "Ban\u2800co" to "banco", "  Banco  " to "banco")) {
            assertEquals(esc(a), importTags(listOf(b), fmt), importTags(listOf(a), fmt))
        }
        // Folders that show nothing never take a place of MAX_TAGS: nine invisible ones and twelve real ones give ten tags.
        val folders = List(9) { "\u200B".repeat(it + 1) } + List(12) { "f$it" }
        val tags = importTags(folders, fmt)
        assertEquals(10, tags.size)
        assertEquals(listOf(fmt.originTag) + List(9) { "f$it" }, tags)
        // The budget of new tags counts what shows: 150 folders and 150 invisible copies of them create at most 100 tags.
        val items = (0 until 300).map { i ->
            val folder = if (i < 150) "carpeta$i" else "carpeta${i - 150}\u200B"
            ImportedItem(i, importedEntry(EntryType.NOTE, "n$i", notes = "t$i"), listOf(folder))
        }
        val p = planMerge(ImportRead(fmt, items, emptyList(), 0), emptyList(), 1)
        val merged = mergedEntries(p, DuplicateChoice.SKIP, emptyMap(), emptyList(), 2, { "id" })
        val created = merged.flatMap { it.tags }.toSet() - fmt.originTag
        assertTrue("${created.size}", created.size <= 100)
        assertTrue(created.none { t -> t.any { drawsNothing(it.code) } })
    }

    /**
     * Fields R57 to R60 leave as they were, on purpose (the per-field table of the fix report): the password is the secret
     * and is taken as it comes, also one of spaces or of an invisible character (the old one goes to the history); a URL
     * with the same scheme and host is the file's, path included (the row shows the host). Recorded so a change is seen.
     */
    @Test fun resistsAndRecordsWhatReplaceTakesAsItComes() {
        val vault = listOf(vaultLogin("https://banco.es/"))
        val item = ImportedItem(0, importedEntry(EntryType.LOGIN, "Banco", username = "ana", password = "\u200B", url = "https://banco.es/otra"), emptyList())
        val after = mergedEntries(plan(item, vault), DuplicateChoice.REPLACE, emptyMap(), vault, 2, { "n" }).single()
        assertEquals("\u200B", after.password)
        assertEquals(listOf("la-buena"), after.passwordHistory.map { it.password })
        assertEquals("https://banco.es/otra", after.url)
        assertEquals("ana · banco.es", duplicateSubline(item.entry))
    }
}
