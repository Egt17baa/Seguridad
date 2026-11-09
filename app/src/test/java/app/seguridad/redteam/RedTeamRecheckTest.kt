package app.seguridad.redteam

import app.seguridad.core.VISIBLE_PREPENDED_MARKS
import app.seguridad.core.drawsNothing
import app.seguridad.crypto.isHiddenInName
import app.seguridad.crypto.safeFileName
import app.seguridad.health.WEAK_BITS
import app.seguridad.health.estimateBits
import app.seguridad.imports.DuplicateChoice
import app.seguridad.imports.ImportFormat
import app.seguridad.imports.ImportRead
import app.seguridad.imports.ImportedItem
import app.seguridad.imports.ReadResult
import app.seguridad.imports.compareKey
import app.seguridad.imports.duplicateSubline
import app.seguridad.imports.importedEntry
import app.seguridad.imports.mergedEntries
import app.seguridad.imports.planMerge
import app.seguridad.imports.plainReportText
import app.seguridad.imports.readImport
import app.seguridad.tools.qr.hasHiddenChars
import app.seguridad.tools.qr.parseLink
import app.seguridad.tools.qr.visibleText
import app.seguridad.vault.EntryType
import app.seguridad.vault.MASTER_TOO_WEAK
import app.seguridad.vault.MASTER_WORD_LISTS
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.masterWords
import app.seguridad.vault.newMasterProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
 * «Third check» down. Report: docs/superpowers/handoff/sdd-1.4.0/redteam-second-recheck.md.
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
        for (t in listOf("❤️", "1️⃣", "#️⃣", "*️⃣", "1⃣", "🇪🇸", "👍🏽", "©️", "‼️", "☎️", "↔️", "🏳️", "Café ❤️ Banco")) {
            assertEquals(t, t, plainReportText(t))
            assertEquals(t, safeFileName("a $t b").contains('\uFE0F'), plainReportText("a $t b").contains('\uFE0F'))
        }
        assertEquals(compareKey("❤ Banco"), compareKey("❤️ Banco"))
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
        for (t in listOf("Casa ❤️", "1️⃣", "#️⃣", "🇪🇸", "👍🏽", "☎️")) assertFalse(t, hasHiddenChars(t))
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
}
