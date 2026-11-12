package app.seguridad.imports

import app.seguridad.core.appJson
import app.seguridad.crypto.TotpSecret
import app.seguridad.tools.qr.asBrowserReadsIt
import app.seguridad.tools.qr.parseLink
import app.seguridad.vault.VaultData
import app.seguridad.vault.AttachmentRef
import app.seguridad.vault.BACKUP_V3_MAX_ENTRY_BYTES
import app.seguridad.vault.VaultLimits
import app.seguridad.vault.EntryType
import app.seguridad.vault.MAX_TAGS
import app.seguridad.vault.PasswordChange
import app.seguridad.vault.VaultCrypto
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.oldPasswords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale
import java.util.Random

/** 1.4.0, Task 8: the merge plan (spec «El plan de fusión») and the merged list it gives. */
class MergePlanTest {
    /** 2026-11-04 10:00:00 UTC. */
    private val now = 1_793_786_400_000L
    private var ids = 0
    private val newId = { "nuevo-${ids++}" }

    private fun item(index: Int, entry: VaultEntry, folders: List<String> = emptyList()) = ImportedItem(index, entry, folders)

    private fun login(index: Int, title: String, user: String = "", url: String = "", password: String = "", notes: String = "", folders: List<String> = emptyList()) =
        item(index, importedEntry(EntryType.LOGIN, title, username = user, password = password, url = url, notes = notes), folders)

    private fun note(index: Int, title: String, text: String) = item(index, importedEntry(EntryType.NOTE, title, notes = text))

    private fun totp(index: Int, title: String, secret: String) =
        item(index, importedEntry(EntryType.TOTP, title, totp = TotpSecret(secret, issuer = title)))

    private fun read(vararg items: ImportedItem, format: ImportFormat = ImportFormat.BITWARDEN) =
        ImportRead(format, items.toList(), listOf(Skipped(99, "Passkey", SkipReason.PASSKEY)), becameNotes = 2)

    private fun saved(
        id: String,
        type: EntryType,
        title: String,
        user: String = "",
        url: String = "",
        password: String = "",
        notes: String = "",
        totp: TotpSecret? = null,
        tags: List<String> = emptyList(),
    ) = VaultEntry(id, type, title, username = user, password = password, url = url, notes = notes, totp = totp, createdAt = 5, updatedAt = 6, tags = tags)

    private fun plan(read: ImportRead, existing: List<VaultEntry>) = planMerge(read, existing, revision = 7)

    // ---- Duplicates ----

    @Test fun loginDuplicatesByTitleUserAndHost() {
        val bank = saved("b", EntryType.LOGIN, "Banco", user = "ana", url = "https://www.Banco.es/login?x=1")
        val p = plan(
            read(
                login(0, "  BANCO ", user = " ana ", url = "HTTPS://banco.ES/otra"),
                login(1, "Banco", user = "ana", url = "https://otro.es/"),
                login(2, "Banco", user = "Ana", url = "https://banco.es/"), // the user keeps its case: a capital is visible (R34)
                login(3, "Banca", user = "ana", url = "https://banco.es/"),
                login(4, "Banco", user = "ana", url = "https://user:clave@WWW.banco.es:8443/x"),
                login(5, "Banco", user = "ana", url = "http://banco.es/"), // another scheme: not a duplicate (RT-4, R41)
            ),
            listOf(bank),
        )
        assertEquals(listOf(0), p.duplicates.map { it.item.index })
        assertEquals("b", p.duplicates.single().existingId)
        assertEquals(listOf(1, 2, 3, 5), p.newItems.map { it.index })
        // Item 4 has the same title, user and host as item 0: repeated inside the file.
        assertEquals(1, p.repeatedInFile)
        assertEquals(7, p.revision)
        assertEquals(ImportFormat.BITWARDEN, p.format)
        assertEquals(2, p.becameNotes)
        assertEquals(listOf(99), p.skipped.map { it.index })
    }

    // ---- RT-1 (ruling R34): the host is the one a browser goes to ----

    @Test fun theHostIsTheOneABrowserGoesTo() {
        // In a web link «\» ends the authority like «/» (URL Standard), so what follows the «@» is the path.
        for (url in listOf(
            "https://evil.example\\@banco.es/login",
            "https://evil.example\\banco.es",
            "https:\\\\evil.example\\@banco.es",
            "https:/\\evil.example\\@banco.es",
            "HTTPS://Evil.Example\\@Banco.ES/",
            "http://www.evil.example:8080\\@banco.es",
            "https://ana:clave@evil.example\\@banco.es/",
            "evil.example\\@banco.es/login", // no scheme: read as an address bar reads it
            "https://evil.example\t\\@banco.es",
            "wss://evil.example\\@banco.es",
        )) {
            assertEquals(url, "evil.example", urlHost(url))
        }
        // The user info still ends at the last «@» before the end of the authority.
        assertEquals("banco.es", urlHost("https://ana:x@banco.es\\@evil.example/"))
        assertEquals("banco.es", urlHost("https://evil.example@banco.es/"))
        // What it compared before still compares the same: www., case, user, port, path, query and fragment go.
        for (url in listOf(
            "https://www.Banco.es/login?x=1", "http://banco.ES/otra", "https://user:clave@WWW.banco.es:8443/x", "banco.es",
            "banco.es:8443/x", "//banco.es/", "https://banco.es#x", "https://banco.es.",
        )) {
            assertEquals(url, "banco.es", urlHost(url))
        }
        assertEquals("localhost", urlHost("localhost:8080"))
        assertEquals("[::1]", urlHost("https://[::1]:443/"))
        assertEquals("com.example.app", urlHost("androidapp://com.example.app"))
        // No host: the whole text, trimmed, with only the scheme in lower case (ruling R51); no URL: null.
        assertEquals("https://", urlHost(" HTTPS:// "))
        assertNull(urlHost("   "))
    }

    @Test fun theHostAgreesWithTheQrReaderForEveryWebLink() {
        val random = Random(20261108L)
        val parts = listOf(
            "https:", "http:", "//", "\\", "/", "@", ":", "a", "b.es", "www.", "ana", "?", "#", "%40", "\t", " ", "[", "]",
            "Ｂ", "​", "8443", "\uD800",
        )
        repeat(20_000) {
            val url = "https://" + (0 until random.nextInt(10)).joinToString("") { parts[random.nextInt(parts.size)] }
            // A host that shows nothing counts as none (ruling R55).
            val browser = parseLink(url).host?.removePrefix("www.")?.takeUnless { compareKey(it).isEmpty() }
            // Without a host: the text as the browser reads it (ruling R54), with only its scheme in lower case (ruling R51;
            // here it is already).
            assertEquals(url, browser?.ifEmpty { null } ?: asBrowserReadsIt(url), urlHost(url))
        }
    }

    @Test fun aUrlOfAnotherSiteIsNotADuplicateAndReplaceCannotTakeIt() {
        val bank = saved("b", EntryType.LOGIN, "Banco", user = "ana", url = "https://banco.es/", password = "la-buena")
        val p = plan(read(login(0, "Banco", user = "ana", url = "https://evil.example\\@banco.es/login", password = "otra")), listOf(bank))
        assertTrue(p.duplicates.isEmpty())
        assertEquals(listOf(0), p.newItems.map { it.index })
        val merged = mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), listOf(bank), now, newId)
        assertEquals(bank, merged.single { it.id == "b" }) // the vault's entry is never touched
    }

    // ---- RT-4 (ruling R41): the scheme is part of the key ----

    @Test fun theSchemeOfTheUrlIsPartOfTheKey() {
        val bank = saved("b", EntryType.LOGIN, "Banco", user = "ana", url = "https://banco.es/", password = "la-buena")
        // A text without a scheme is read as https, as before; case, www., port, path and slashes still do not count.
        for (url in listOf(
            "banco.es", "banco.es/login", "banco.es:8443/x", "//banco.es/", "HTTPS://WWW.BANCO.ES/", "https:banco.es",
            "https:\\\\banco.es", "  https://ana@banco.es:443/x?y#z ",
        )) {
            assertEquals(url, listOf("b"), plan(read(login(0, "Banco", user = "ana", url = url)), listOf(bank)).duplicates.map { it.existingId })
        }
        // Another scheme of the same host is another site: never a duplicate, so «Reemplazar» cannot change the scheme.
        for (url in listOf(
            "http://banco.es/", "HTTP://www.banco.es", "http:banco.es", "javascript://banco.es/%0Aalert(1)",
            "intent://banco.es/#Intent;scheme=https;package=com.example.evil;end", "file://banco.es/sdcard/x.html", "ftp://banco.es/",
            "wss://banco.es", "androidapp://banco.es", "javascript:x@banco.es/", "mailto:ana@banco.es", "data:text/html,banco.es",
        )) {
            val p = plan(read(login(0, "Banco", user = "ana", url = url, password = "otra")), listOf(bank))
            assertTrue(url, p.duplicates.isEmpty())
            assertEquals(bank, mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), listOf(bank), now, newId).single { it.id == "b" })
        }
        // Same scheme, same host: a duplicate, also for plain http.
        val plain = saved("h", EntryType.LOGIN, "Foro", user = "ana", url = "http://foro.es/")
        assertEquals(listOf("h"), plan(read(login(0, "Foro", user = "ana", url = "HTTP://www.foro.es/x")), listOf(plain)).duplicates.map { it.existingId })
        assertTrue(plan(read(login(0, "Foro", user = "ana", url = "https://foro.es/")), listOf(plain)).duplicates.isEmpty())
        assertTrue(plan(read(login(0, "Foro", user = "ana", url = "foro.es")), listOf(plain)).duplicates.isEmpty())
    }

    @Test fun aUrlWithoutAHostIsComparedWholeAndNeverMeetsAHost() {
        assertEquals("hhttps://banco.es", urlKey("banco.es"))
        assertEquals("hhttp://banco.es", urlKey("http://www.banco.es/x"))
        assertEquals("hjavascript://banco.es", urlKey("javascript://banco.es/%0Aalert(1)"))
        assertEquals("tmailto:ana@banco.es", urlKey(" MAILTO:ana@banco.es "))
        assertEquals("tjavascript:x@banco.es/", urlKey("javascript:x@banco.es/"))
        assertNull(urlKey("  "))
        // Two URLs without a host match only when they are the same text.
        val mail = saved("m", EntryType.LOGIN, "Correo", user = "ana", url = "mailto:ana@banco.es")
        assertEquals(listOf("m"), plan(read(login(0, "Correo", user = "ana", url = "MAILTO:ana@banco.es")), listOf(mail)).duplicates.map { it.existingId })
        assertTrue(plan(read(login(0, "Correo", user = "ana", url = "mailto:otra@banco.es")), listOf(mail)).duplicates.isEmpty())
    }

    @Test fun aSchemeThatIsNotWebIsNotReadAsHttps() {
        // A scheme that is not a web one keeps its own reading: its «x@» is not a user before a web host.
        assertEquals("mailto:ana@banco.es", urlHost("mailto:ana@banco.es"))
        assertEquals("javascript:x@banco.es/", urlHost("JavaScript:x@banco.es/"))
        // A host and a port look like a scheme, and are read as a web link, as an address bar does.
        assertEquals("banco.es", urlHost("banco.es:8443"))
        assertEquals("localhost", urlHost("localhost:8080/"))
        assertEquals("localhost", urlHost("localhost:8080?x"))
        // The row shows a scheme that is not https (ruling R45), and never the user info.
        assertEquals("ana · http://banco.es", duplicateSubline(importedEntry(EntryType.LOGIN, "Banco", username = "ana", url = "http://banco.es/")))
        assertEquals("ana · mailto:…@banco.es", duplicateSubline(importedEntry(EntryType.LOGIN, "Banco", username = "ana", url = "mailto:ana@banco.es")))
    }

    /**
     * Ruling R47 (review M-1): a «word:» in front is a scheme, also in «ana:clave@banco.es» (what closes
     * `javascript:x@banco.es/`, as the URL Standard reads it), so it is not a duplicate of `https://banco.es/`; and its
     * row hides the user info (R45), so the password in it is never shown.
     */
    @Test fun aWordAndAColonInFrontIsAScheme() {
        assertEquals("tana:clave@banco.es", urlKey("ana:clave@banco.es"))
        val bank = saved("b", EntryType.LOGIN, "Banco", user = "ana", url = "https://banco.es/", password = "la-buena")
        assertTrue(plan(read(login(0, "Banco", user = "ana", url = "ana:clave@banco.es")), listOf(bank)).duplicates.isEmpty())
        for (url in listOf("ana:clave@banco.es", "https://ana:clave@banco.es/", "http://ana:clave@banco.es/", "ANA:clave@banco.es/x", "clave@", "https://ana:clave@")) {
            val row = duplicateSubline(importedEntry(EntryType.LOGIN, "Banco", username = "ana", url = url))
            assertFalse("«$url» → «$row»", "clave" in row)
        }
        assertEquals("ana · ana:…@banco.es", duplicateSubline(importedEntry(EntryType.LOGIN, "Banco", username = "ana", url = "ana:clave@banco.es")))
    }

    /**
     * Ruling R45: the duplicate's row shows «esquema://host» when the scheme is not https, the host alone for https or no
     * scheme, a URL without a host as written (trimmed), and never the user info («…@» in its place).
     */
    @Test fun theRowShowsASchemeThatIsNotHttpsAndNeverTheUserInfo() {
        val row = { url: String -> duplicateSubline(importedEntry(EntryType.LOGIN, "Banco", username = "ana", url = url)) }
        assertEquals("ana · banco.es", row("https://ana:x@www.banco.es:8443/x"))
        assertEquals("ana · banco.es", row("banco.es/login"))
        assertEquals("ana · http://banco.es", row("HTTP://ana:x@banco.es/"))
        assertEquals("ana · javascript://banco.es", row("javascript://banco.es/%0Aalert(1)"))
        assertEquals("ana · intent://banco.es", row("intent://banco.es/#Intent;scheme=https;package=com.example.evil;end"))
        assertEquals("ana · file://banco.es", row("file://banco.es/sdcard/x.html"))
        // Without a host: as written, through plainReportText, the user info hidden.
        assertEquals("ana · MAILTO:…@banco.es", row(" MAILTO:Ana@banco.es "))
        assertEquals("ana · javascript:…@banco.es/", row("javascript:x@banco.es/"))
        assertEquals("ana · data:text/plain;base64,SGVsbG8=", row("data:text/plain;base64,SGVsbG8="))
        assertEquals("ana · https://", row("https://"))
        assertEquals("ana · mailto:…@banco.es", row("mailto:ana\u202E@banco.es"))
        assertEquals("ana · mailto:…@banco.es", row("mailto:a\u200Bna@banco.es"))
    }

    /**
     * Ruling R51 (RT-12): a URL without a host is compared by its trimmed text with only its scheme in lower case, so two
     * texts that differ in case after the scheme (Base64 in `data:`) are not duplicates.
     */
    @Test fun aUrlWithoutAHostKeepsItsCaseAfterTheScheme() {
        assertEquals("tdata:text/plain;base64,SGVsbG8=", urlKey(" DATA:text/plain;base64,SGVsbG8= "))
        assertEquals("tmailto:Ana@banco.es", urlKey("MAILTO:Ana@banco.es"))
        val data = saved("d", EntryType.LOGIN, "Banco", user = "ana", url = "data:text/plain;base64,SGVsbG8=")
        assertTrue(plan(read(login(0, "Banco", user = "ana", url = "data:text/plain;base64,sGVSbg8=")), listOf(data)).duplicates.isEmpty())
        assertEquals(listOf("d"), plan(read(login(0, "Banco", user = "ana", url = "DATA:text/plain;base64,SGVsbG8=")), listOf(data)).duplicates.map { it.existingId })
    }

    /**
     * Ruling R48 (RT-8, review M-2): a vault entry without a URL is a duplicate only of an item without a URL or with an
     * https one; any other scheme is a new entry, so «Reemplazar» cannot give it that URL. And `https://banco.es` with
     * `http://banco.es` in one file no longer make two duplicates of the same entry.
     */
    @Test fun anEntryWithoutAUrlIsADuplicateOnlyOfHttpsOrNoUrl() {
        val bare = saved("b", EntryType.LOGIN, "Banco", user = "ana", url = "", password = "la-buena")
        for (url in listOf("", "https://banco.es/", "banco.es", "HTTPS://www.banco.es/login")) {
            assertEquals(url, listOf("b"), plan(read(login(0, "Banco", user = "ana", url = url)), listOf(bare)).duplicates.map { it.existingId })
        }
        for (url in listOf("http://banco.es/", "javascript://banco.es/%0Aalert(1)", "intent://banco.es/#Intent;end", "file://banco.es/x",
            "ftp://banco.es", "mailto:ana@banco.es", "https://", "ana:clave@banco.es")) {
            val p = plan(read(login(0, "Banco", user = "ana", url = url, password = "otra")), listOf(bare))
            assertTrue(url, p.duplicates.isEmpty())
            assertEquals(url, bare, mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), listOf(bare), now, newId).single { it.id == "b" })
        }
        // With an https entry and a URL-less one of the same title and user, http falls to neither.
        val both = listOf(saved("h", EntryType.LOGIN, "Banco", user = "ana", url = "https://banco.es/"), bare)
        assertTrue(plan(read(login(0, "Banco", user = "ana", url = "http://banco.es/")), both).duplicates.isEmpty())
        // Review M-2: https and http of one host in one file, against the URL-less entry: one duplicate, one new entry.
        val p = plan(read(login(0, "Banco", user = "ana", url = "https://banco.es"), login(1, "Banco", user = "ana", url = "http://banco.es")), listOf(bare))
        assertEquals(listOf(0), p.duplicates.map { it.item.index })
        assertEquals(listOf(1), p.newItems.map { it.index })
    }

    /**
     * Ruling R53 (re-review I-1): «word:digits» is a host and a port only when the word has a «.», is `localhost` or is a
     * bracketed IPv6 literal; any other word is a scheme, so `javascript:1/alert(1)` is never read as https.
     */
    @Test fun aWordAndDigitsIsAHostAndPortOnlyWhenTheWordLooksLikeAHost() {
        assertEquals("tjavascript:1/alert(1)", urlKey("javascript:1/alert(1)"))
        assertEquals("tdata:1/x", urlKey("data:1/x"))
        assertEquals("tintent:1/#Intent;package=com.example.evil;end", urlKey("intent:1/#Intent;package=com.example.evil;end"))
        assertEquals("tintranet:8080", urlKey("intranet:8080"))
        assertEquals("hhttps://banco.es", urlKey("banco.es:8443/x"))
        assertEquals("hhttps://localhost", urlKey("LOCALHOST:8080"))
        assertEquals("hhttps://[::1]", urlKey("[::1]:8080/x"))
        val bare = saved("b", EntryType.LOGIN, "Banco", user = "ana", url = "", password = "la-buena")
        for (url in listOf("javascript:1/alert(1)", "javascript:1/%0Aalert(1)", "data:1/x", "intent:1/#Intent;package=com.example.evil;end")) {
            val p = plan(read(login(0, "Banco", user = "ana", url = url, password = "otra")), listOf(bare))
            assertTrue(url, p.duplicates.isEmpty())
            assertEquals(url, bare, mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), listOf(bare), now, newId).single { it.id == "b" })
        }
        assertEquals("ana · javascript:1/alert(1)", duplicateSubline(importedEntry(EntryType.LOGIN, "Banco", username = "ana", url = "javascript:1/alert(1)")))
    }

    /** Re-review N-1: without a host, the row hides everything up to the last «@» of the whole text. */
    @Test fun withoutAHostTheRowHidesUpToTheLastAt() {
        val row = { url: String -> duplicateSubline(importedEntry(EntryType.LOGIN, "Banco", username = "ana", url = url)) }
        for (url in listOf("ana:cl/ave@banco.es", "ana:cla?ve@banco.es", "ana:clave#1@banco.es", "ana:clave@banco.es")) {
            assertEquals(url, "ana · ana:…@banco.es", row(url))
        }
        assertEquals("ana · mailto:…@d.es", row("mailto:a@b.es?cc=c@d.es"))
    }

    /**
     * Ruling R54 (RT-14): the text is read as a browser reads it before the scheme is looked for (WHATWG URL: tabs and
     * line breaks go, C0 controls and spaces at the ends go), so a tab inside the scheme or a control in front of it
     * cannot hide `http:` or `javascript:`.
     */
    @Test fun theSchemeIsReadAfterTheBrowsersCleaning() {
        assertEquals("hhttp://banco.es", urlKey("ht\ttp:@banco.es"))
        assertEquals("hhttp://banco.es", urlKey("\u0001http:@banco.es"))
        assertEquals("hhttp://banco.es", urlKey("HT\nTP:x@banco.es/login"))
        assertEquals("tjavascript:alert(1)<!--@banco.es", urlKey("java\tscript:alert(1)<!--@banco.es"))
        assertEquals("hhttps://banco.es", urlKey("ht\ttps://banco.es"))
        assertEquals("tdata:text/plain;base64,SGVsbG8=", urlKey("\u0001da\tta:text/plain;base64,SGVs\r\nbG8= \u0002"))
        val row = { url: String -> duplicateSubline(importedEntry(EntryType.LOGIN, "Banco", username = "ana", url = url)) }
        assertEquals("ana · http://banco.es", row("ht\ttp:@banco.es"))
        assertEquals("ana · banco.es", row("ht\ttps://banco.es"))
        assertEquals("ana · javascript:…@banco.es", row("\u0001javascript:alert(1)<!--@banco.es"))
    }

    /**
     * Ruling R55 (re-review I-2): one notion of «no URL», the browser's: a URL that is only controls, spaces, tabs and line
     * breaks is no URL for the duplicate rule and for «Reemplazar», which keeps the vault's URL.
     */
    @Test fun aUrlOfOnlyControlsIsNoUrlForReplaceToo() {
        val bank = saved("b", EntryType.LOGIN, "Banco", user = "ana", url = "https://banco.es/", password = "la-buena")
        for (url in listOf("\u0001", "\u0000\u001F", "\t\u0001\n", " ")) {
            val p = plan(read(login(0, "Banco", user = "ana", url = url, password = "otra")), listOf(bank))
            assertEquals(url, listOf("b"), p.duplicates.map { it.existingId })
            assertEquals(url, "ana", duplicateSubline(p.duplicates.single().item.entry))
            assertEquals(url, "https://banco.es/", mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), listOf(bank), now, newId).single { it.id == "b" }.url)
        }
    }

    /**
     * Ruling R55, the other fields the duplicate rule compares: a title or a user that shows nothing (only controls or
     * what draws nothing) keys as empty, so «Reemplazar» keeps the vault's, as it keeps it for a blank one.
     */
    @Test fun aTitleOrUserThatShowsNothingKeepsTheVaults() {
        val bank = saved("b", EntryType.LOGIN, "Banco", user = "", url = "https://banco.es/")
        for (user in listOf("\u0001", "\u200B", "\u0000\u2060")) {
            val p = plan(read(login(0, "Banco", user = user, url = "https://banco.es/", password = "otra")), listOf(bank))
            assertEquals(user, listOf("b"), p.duplicates.map { it.existingId })
            assertEquals(user, "", mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), listOf(bank), now, newId).single { it.id == "b" }.username)
        }
        val untitled = saved("u", EntryType.LOGIN, "", user = "ana")
        for (title in listOf("\u0001", "\u200B\u00AD")) {
            assertEquals(title, "", replaceWith(untitled, importedEntry(EntryType.LOGIN, title, username = "ana", password = "otra"), now).title)
        }
    }

    /**
     * Ruling R58 (RT-15): «Reemplazar» keeps the vault's title and user whenever their keys match, the keys that made it a
     * duplicate; it brings in the rest. A title that differs only in case keeps the vault's spelling.
     */
    @Test fun replaceKeepsATitleAndUserWhoseKeysMatch() {
        val bank = saved("b", EntryType.LOGIN, "Café Banco", user = "ana", url = "https://banco.es/", password = "la-buena")
        for ((title, user) in listOf("CAFÉ BANCO" to "ana", "Cafe\u0301 Ban\u200Bco" to "an\u00ADa", "Café\u00A0Banco " to "ana\uFEFF")) {
            val p = plan(read(login(0, title, user = user, url = "https://banco.es/", password = "otra")), listOf(bank))
            assertEquals(title, listOf("b"), p.duplicates.map { it.existingId })
            val after = mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), listOf(bank), now, newId).single { it.id == "b" }
            assertEquals(title, listOf("Café Banco", "ana", "otra"), listOf(after.title, after.username, after.password))
        }
        // A 2FA matches by its key, not by its title: a title that is really another one is still brought in.
        val code = saved("t", EntryType.TOTP, "Correo", totp = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Correo", account = "ana"))
        assertEquals("Correo nuevo", replaceWith(code, importedEntry(EntryType.TOTP, "Correo nuevo", totp = TotpSecret("JBSWY3DPEHPK3PXP")), now).title)
    }

    /**
     * Rulings R57 and R59 (review N-6, RT-16): every field «Reemplazar» takes from the file is empty when it shows nothing:
     * a 2FA's issuer and account, and a login's notes (notes that show nothing add no «Importado el …» line).
     */
    @Test fun replaceTakesNoFieldThatShowsNothing() {
        val code = saved("t", EntryType.TOTP, "Correo", totp = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Correo", account = "ana"))
        for (blank in listOf("\u0001", "\u200B", "\u2800", "\u3164", " \t")) {
            val after = replaceWith(code, importedEntry(EntryType.TOTP, "Correo", totp = TotpSecret("JBSWY3DPEHPK3PXP", issuer = blank, account = blank)), now)
            assertEquals(blank, listOf("Correo", "ana"), listOf(after.totp?.issuer, after.totp?.account))
        }
        val bank = saved("b", EntryType.LOGIN, "Banco", user = "ana", url = "https://banco.es/", notes = "mis notas")
        for (blank in listOf("\u200B\u2800", "\u0001", "\u00AD\n\u3164")) {
            assertEquals(blank, "mis notas", replaceWith(bank, importedEntry(EntryType.LOGIN, "Banco", username = "ana", notes = blank, password = "otra"), now).notes)
        }
    }

    /** Ruling R57 (Info of re-review 3): the row drops « · » when its second part shows nothing. */
    @Test fun theRowHasNoSeparatorBeforeWhatShowsNothing() {
        assertEquals("ana", duplicateSubline(importedEntry(EntryType.LOGIN, "Banco", username = "ana", url = "\u200B")))
        assertEquals("banco.es", duplicateSubline(importedEntry(EntryType.LOGIN, "Banco", username = "\u200B\u0001", url = "https://banco.es")))
    }

    /**
     * Ruling R60 (RT-17): a folder name is read as it shows before it becomes a tag: what draws nothing goes, and a
     * folder that shows nothing gives no tag; «Banco» + U+200B is the vault's «banco».
     */
    @Test fun aFolderIsReadAsItShows() {
        for (folder in listOf("\u200B", "\u2800", "\u3164", "\u0001", "\u00AD\uFEFF")) {
            assertEquals(folder, listOf(ORIGIN_TAG_BROWSER), importTags(listOf(folder), ImportFormat.BROWSER_CSV))
        }
        for (folder in listOf("Banco\u200B", "Ban\u00ADco", "\u2060BANCO", "Ban\u0001co")) {
            val expected = if ('\u0001' in folder) "ban co" else "banco"
            assertEquals(folder, listOf(ORIGIN_TAG_BROWSER, expected), importTags(listOf(folder), ImportFormat.BROWSER_CSV))
        }
        // Rulings R61 and R64 (N-9, RT-18): read as the duplicate rule compares (compareKey: NFKC, spaces as one), so a
        // no-break space, a decomposed accent or full-width letters give the tag that looks the same.
        assertEquals(importTags(listOf("Ban co"), ImportFormat.BROWSER_CSV), importTags(listOf("Ban\u00A0co"), ImportFormat.BROWSER_CSV))
        assertEquals(listOf(ORIGIN_TAG_BROWSER, "banco"), importTags(listOf("Ｂanco"), ImportFormat.BROWSER_CSV))
        assertEquals(listOf(ORIGIN_TAG_BROWSER, "café"), importTags(listOf("Cafe\u0301"), ImportFormat.BROWSER_CSV))
        val bank = saved("b", EntryType.LOGIN, "Banco", user = "ana", url = "https://banco.es/", tags = listOf("banco"))
        val p = plan(read(login(0, "Banco", user = "ana", url = "https://banco.es/", folders = listOf("Banco\u200B")), format = ImportFormat.BROWSER_CSV), listOf(bank))
        val after = mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), listOf(bank), now, newId).single { it.id == "b" }
        assertEquals(listOf("banco", ORIGIN_TAG_BROWSER), after.tags)
    }

    /**
     * Rulings R55 and R57 to R60 as a property: 20 000 files whose title, user, issuer, account, notes and folder are
     * built from letters, spaces, controls and what draws nothing, replaced over a vault login and a vault 2FA. «Reemplazar»
     * never changes a title or user whose key matches, never writes a field that shows nothing over one that shows
     * something, and never adds a tag that shows nothing.
     */
    @Test fun replaceNeverWritesWhatShowsNothing() {
        val random = Random(20261110L)
        val parts = listOf("", "Banco", "banco", "ana", "ANA", "x", " ", "\t", "\n", "\u0001", "\u001F", "\u00A0", "\u200B", "\u00AD", "\u2800", "\u3164", "\uFEFF", "\u2060", "\u0301")
        fun text() = (0 until random.nextInt(4)).joinToString("") { parts[random.nextInt(parts.size)] }
        fun shows(t: String) = compareKey(t).isNotEmpty()
        val secret = "JBSWY3DPEHPK3PXP"
        repeat(20_000) {
            val bank = saved("b", EntryType.LOGIN, "Banco", user = "ana", url = "https://banco.es/", notes = "mis notas", tags = listOf("banco"))
            val code = saved("t", EntryType.TOTP, "Correo", totp = TotpSecret(secret, issuer = "Correo", account = "ana"), notes = "nota")
            // Half the time the title and user are the vault's with what shows nothing around them, so they are duplicates.
            val title = (if (random.nextBoolean()) "Banco" else "") + text()
            val user = (if (random.nextBoolean()) "ana" else "") + text()
            val notes = text(); val folder = text(); val issuer = text(); val account = text()
            val file = read(
                item(0, importedEntry(EntryType.LOGIN, title, username = user, url = "https://banco.es/", notes = notes, password = "otra"), listOf(folder)),
                item(1, importedEntry(EntryType.TOTP, title, notes = notes, totp = TotpSecret(secret, issuer = issuer, account = account)), listOf(folder)),
                format = ImportFormat.BROWSER_CSV,
            )
            val vault = listOf(bank, code)
            val after = mergedEntries(plan(file, vault), DuplicateChoice.REPLACE, emptyMap(), vault, now, newId)
            val all = "«${title}» «${user}» «${notes}» «${folder}» «${issuer}» «${account}»"
            for (e in after) {
                val before = vault.firstOrNull { it.id == e.id }
                for (t in e.tags) assertTrue("$all: tag «$t»", shows(t))
                if (before == null) continue
                for ((name, old, new) in listOf(
                    Triple("title", before.title, e.title), Triple("user", before.username, e.username), Triple("url", before.url, e.url),
                    Triple("notes", before.notes, e.notes), Triple("issuer", before.totp?.issuer.orEmpty(), e.totp?.issuer.orEmpty()),
                    Triple("account", before.totp?.account.orEmpty(), e.totp?.account.orEmpty()),
                )) {
                    if (shows(old) && !shows(new)) fail("$all: $name «$old» → «$new»")
                }
                // The notes only grow by something that shows.
                if (e.notes != before.notes) assertTrue("$all: notes «${e.notes}»", shows(notes) && e.notes.endsWith(notes.trim()))
                if (e.type == EntryType.LOGIN) {
                    // A duplicate login: its keys matched, so its title and user stay as they were (R58).
                    assertEquals(all, listOf(before.title, before.username), listOf(e.title, e.username))
                } else if (compareKey(title).lowercase(Locale.ROOT) == compareKey(before.title).lowercase(Locale.ROOT) || !shows(title)) {
                    assertEquals(all, before.title, e.title)
                }
            }
        }
    }

    /**
     * Ruling R67 (code-review 2): a 2FA item is a duplicate when its key is in any vault entry's 2FA, also a login's (as
     * `totpImportEntries`); «Reemplazar» on that login changes nothing, since it is another type.
     */
    @Test fun aCodeInsideALoginIsADuplicate() {
        val bank = saved("b", EntryType.LOGIN, "Banco", user = "ana", url = "https://banco.es/", totp = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Banco", account = "ana"))
        val p = plan(read(totp(0, "Banco", "jbsw y3dp ehpk 3pxp")), listOf(bank))
        assertEquals(listOf("b"), p.duplicates.map { it.existingId })
        assertTrue(p.newItems.isEmpty())
        assertEquals("Banco", duplicateSubline(p.duplicates.single().item.entry))
        assertSame(bank, mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), listOf(bank), now, newId).single())
    }

    /**
     * Ruling R71 (audit F-1): that duplicate cannot be replaced; «Reemplazar» counts it as skipped, never as a
     * replacement, and spends no tag budget, so the folders of the new items after it all get their tag.
     */
    @Test fun aDuplicateOfAnotherTypeIsNeverAReplacement() {
        val bank = saved("b", EntryType.LOGIN, "Banco", user = "ana", url = "https://banco.es/", totp = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Banco"))
        val code = item(0, importedEntry(EntryType.TOTP, "Banco", totp = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Banco")), listOf("carpeta"))
        val items = listOf(code) + (1..IMPORT_MAX_NEW_TAGS).map { login(it, "Sitio $it", user = "ana", url = "https://sitio$it.es/", folders = listOf("f$it")) }
        val p = plan(read(*items.toTypedArray(), format = ImportFormat.BROWSER_CSV), listOf(bank))
        assertEquals(listOf(false), p.duplicates.map { it.replaceable })
        assertEquals(0, previewCounts(p, DuplicateChoice.REPLACE, emptyMap()).replaced)
        assertEquals(1, previewCounts(p, DuplicateChoice.REPLACE, emptyMap()).skippedDuplicates)
        val r = mergeImport(p, DuplicateChoice.REPLACE, emptyMap(), listOf(bank), now, newId)
        assertSame(bank, r.entries.first { it.id == "b" })
        assertEquals(0, r.foldersNotTags)
        assertFalse("carpeta" in r.newTags)
        assertTrue(r.entries.first { it.title == "Sitio $IMPORT_MAX_NEW_TAGS" }.tags.contains("f$IMPORT_MAX_NEW_TAGS"))
        // A duplicate of the same type can be replaced.
        val same = saved("t", EntryType.TOTP, "Banco", totp = TotpSecret("JBSWY3DPEHPK3PXP"))
        assertEquals(listOf(true), plan(read(code), listOf(same)).duplicates.map { it.replaceable })
    }

    /**
     * Ruling R68 (code-review 3): a «Reemplazar» whose entry already has [MAX_TAGS] tags adds none, so its folder does not
     * spend the budget of new tags, and the folders of the new items after it all get their tag.
     */
    @Test fun aFullEntryReplacedSpendsNoTagBudget() {
        val full = saved("b", EntryType.LOGIN, "Banco", user = "ana", url = "https://banco.es/", tags = (1..MAX_TAGS).map { "t$it" })
        val items = listOf(login(0, "Banco", user = "ana", url = "https://banco.es/", password = "otra", folders = listOf("f0"))) +
            (1..IMPORT_MAX_NEW_TAGS).map { login(it, "Sitio $it", user = "ana", url = "https://sitio$it.es/", folders = listOf("f$it")) }
        val p = plan(read(*items.toTypedArray(), format = ImportFormat.BROWSER_CSV), listOf(full))
        val r = mergeImport(p, DuplicateChoice.REPLACE, emptyMap(), listOf(full), now, newId)
        assertEquals(0, r.foldersNotTags)
        assertTrue(r.entries.first { it.title == "Sitio $IMPORT_MAX_NEW_TAGS" }.tags.contains("f$IMPORT_MAX_NEW_TAGS"))
        assertEquals((1..MAX_TAGS).map { "t$it" }, r.entries.first { it.id == "b" }.tags)
    }

    /**
     * Rulings R53 and R54, as a property over 20 000 random links built from schemes, «:», «//», «@», digits, «.»,
     * tabs, line breaks, C0 controls and spaces: nothing throws; a link whose scheme, once cleaned as a browser cleans
     * it, is not https (and is not a host such as «banco.es» or `localhost`) never gets an https key; and a row never
     * shows the user info that a sentinel «:Zq…» before the last «@» stands for. Ruling R55: it also runs «Reemplazar»
     * end to end against a vault entry with `https://banco.es/` and one without a URL.
     */
    @Test fun noCleanedSchemeButHttpsGivesAnHttpsKeyAndNoRowShowsTheUserInfo() {
        val random = Random(20261109L)
        val parts = listOf(
            "https", "http", "HTTPS", "javascript", "data", "intent", "mailto", "ana", "banco.es", "localhost", "x", ":", "//",
            "@", "1", "80", ".", "\t", "\n", "\r", "\u0001", "\u001F", " ", "/", "banco", "es",
        )
        val secretParts = listOf(":", ".", "1", "ana", "/", "?", "#", "\\", "es")
        fun pick(list: List<String>, n: Int) = (0 until n).joinToString("") { list[random.nextInt(list.size)] }
        repeat(20_000) {
            val s = pick(parts, random.nextInt(9))
            val cleaned = s.filterNot { it == '\t' || it == '\n' || it == '\r' }.trim { it <= ' ' || it.isWhitespace() }
            val key = urlKey(s)
            urlHost(s)
            // Ruling R55, end to end: «Reemplazar» changes the vault's https URL only for a text that keys as https on the
            // same host, and gives a URL-less entry only an https URL, the one its row shows.
            for (vaultUrl in listOf("https://banco.es/", "")) {
                val vault = saved("v", EntryType.LOGIN, "Banco", user = "ana", url = vaultUrl, password = "la-buena")
                val p = plan(read(login(0, "Banco", user = "ana", url = s, password = "otra")), listOf(vault))
                val after = mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), listOf(vault), now, newId).single { it.id == "v" }.url
                if (after == vaultUrl) continue
                val row = duplicateSubline(importedEntry(EntryType.LOGIN, "Banco", username = "ana", url = after))
                if (vaultUrl.isEmpty()) {
                    assertTrue("«$s» → «$after», row «$row»", urlKey(after).orEmpty().startsWith("hhttps://") && row == plainReportText("ana · " + urlHost(after)))
                } else {
                    assertEquals("«$s» replaced «$vaultUrl» with «$after»", "hhttps://banco.es", urlKey(after))
                }
            }
            val scheme = app.seguridad.tools.qr.schemeOf(cleaned)
            if (scheme != null && scheme != "https" && '.' !in scheme && scheme != "localhost") {
                assertFalse("«$s» → $key", key.orEmpty().startsWith("hhttps://"))
            }
            // A sentinel user info: «:Zq» and parts that end no authority, before the last «@».
            val secret = ":Zq" + pick(secretParts.filter { it.none { c -> c in "/?#\\" } }, random.nextInt(4))
            val withSecret = s + secret + "@" + pick(parts.filter { '@' !in it }, random.nextInt(5))
            val row = duplicateSubline(importedEntry(EntryType.LOGIN, "Banco", username = "", url = withSecret))
            assertFalse("«$withSecret» → «$row»", "zq" in row.lowercase(Locale.ROOT))
            // And a secret with «/», «?», «#» or «\» in it, after a scheme that is not a web one (no host): hidden too.
            val opaque = "ana" + secret + pick(secretParts, random.nextInt(4)) + "@banco.es"
            val opaqueRow = duplicateSubline(importedEntry(EntryType.LOGIN, "Banco", username = "", url = opaque))
            assertFalse("«$opaque» → «$opaqueRow»", "zq" in opaqueRow.lowercase(Locale.ROOT))
        }
    }

    // ---- RT-2 (ruling R34): what looks the same is a duplicate ----

    @Test fun aTitleOrUserThatLooksTheSameIsADuplicate() {
        val bank = saved("b", EntryType.LOGIN, "Café Banco", user = "ana", url = "https://banco.es/")
        val looks = listOf(
            "Café Ban​co" to "ana", "Café Banco" to "ana", "‮Café Banco" to "ana", "Café  Banco" to "ana",
            "Café Banco\u0000" to "ana", "CAFÉ BANCO" to "an­a", "Café Banco" to "﻿ana⁠", "Café Banco" to "a͏na ",
            // RT-6 (R43): what draws nothing; RT-7 (R44): the compatibility forms, which NFKC folds.
            "Café Banco\u2800" to "ana", "Café Ban\u17B4co" to "an\uFE00a", "Café\u180B Banco" to "ana\uDB40\uDD00",
            "Ｃａｆé Ｂａｎｃｏ" to "ana", "Café 𝐁anco" to "ａｎａ",
        )
        for ((title, user) in looks) {
            val p = plan(read(login(0, title, user = user, url = "https://banco.es/")), listOf(bank))
            assertEquals("«$title» / «$user»", listOf("b"), p.duplicates.map { it.existingId })
        }
        // What is visible still counts: a capital in the user, another letter, a space inside a word, full-width letters.
        for ((title, user) in listOf("Café Banco" to "Ana", "Cafe Banco" to "ana", "Café Ban co" to "ana", "Café Banco" to "a na")) {
            val p = plan(read(login(0, title, user = user, url = "https://banco.es/")), listOf(bank))
            assertTrue("«$title» / «$user»", p.duplicates.isEmpty())
        }
    }

    @Test fun aNoteWhoseTitleLooksTheSameIsADuplicateAndItsTextIsComparedAsBefore() {
        val memo = saved("m", EntryType.NOTE, "Wifi casa", notes = "clave 123")
        val p = plan(read(note(0, "Wifi​ casa", "clave 123"), note(1, "Wifi casa", "clave​ 123")), listOf(memo))
        assertEquals(listOf(0), p.duplicates.map { it.item.index })
        assertEquals(listOf(1), p.newItems.map { it.index })
    }

    @Test fun aLookAlikeIsStoredAsItCameWhenTheUserKeepsBoth() {
        val bank = saved("b", EntryType.LOGIN, "Banco", user = "ana", url = "https://banco.es/")
        val p = plan(read(login(0, "Ban​co", user = "an­a", url = "https://banco.es/")), listOf(bank))
        val merged = mergedEntries(p, DuplicateChoice.KEEP_BOTH, emptyMap(), listOf(bank), now, newId)
        val added = merged.single { it.id != "b" }
        assertEquals("Ban​co", added.title) // R9: stored as it was read; only the comparison changed
        assertEquals("an­a", added.username)
    }

    @Test fun withoutAUrlOnEitherSideTitleAndUserAreEnough() {
        val noUrl = saved("n", EntryType.LOGIN, "Correo", user = "ana")
        val withUrl = saved("u", EntryType.LOGIN, "Foro", user = "ana", url = "https://foro.es")
        val p = plan(read(login(0, "correo", user = "ana", url = "https://mail.es"), login(1, "Foro", user = "ana")), listOf(noUrl, withUrl))
        assertEquals(listOf(0 to "n", 1 to "u"), p.duplicates.map { it.item.index to it.existingId })
        assertTrue(p.newItems.isEmpty())
    }

    @Test fun totpDuplicatesByKeyAndNotesByTitleAndText() {
        val code = saved("t", EntryType.TOTP, "Correo", totp = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Correo"))
        val memo = saved("m", EntryType.NOTE, "Wifi", notes = "clave 123")
        val p = plan(
            read(
                totp(0, "Otro nombre", "jbsw y3dp-ehpk 3pxp"), // same key, other spelling and title
                totp(1, "Correo", "KRSXG5DSNFXGOIDB"), // same title, other key
                note(2, " wifi ", "clave 123"),
                note(3, "Wifi", "clave 456"),
            ),
            listOf(code, memo),
        )
        assertEquals(listOf(0 to "t", 2 to "m"), p.duplicates.map { it.item.index to it.existingId })
        assertEquals(listOf(1, 3), p.newItems.map { it.index })
    }

    @Test fun repeatedItemsInsideTheFileCountOnce() {
        val bank = saved("b", EntryType.LOGIN, "Banco", user = "ana")
        val p = plan(
            read(
                login(0, "Correo", user = "ana", url = "https://mail.es"),
                login(1, "correo", user = "ana", url = "https://www.mail.es/inbox"),
                login(2, "Correo", user = "ana"), // no URL: equal to item 0 by title and user
                login(3, "Banco", user = "ana"),
                login(4, "Banco", user = "ana"),
                note(5, "N", "t"),
                note(6, "n", "t"),
            ),
            listOf(bank),
        )
        assertEquals(listOf(0, 5), p.newItems.map { it.index })
        assertEquals(listOf(3), p.duplicates.map { it.item.index })
        assertEquals(4, p.repeatedInFile)
        val merged = mergedEntries(p, DuplicateChoice.KEEP_BOTH, emptyMap(), listOf(bank), now, newId)
        assertEquals(1 + 3, merged.size) // each repeated item enters once at most
    }

    @Test fun identityAndContactAreNeverMatchedNorChanged() {
        val me = VaultEntry("i", EntryType.IDENTITY, "Ana", notes = "t", createdAt = 1, updatedAt = 1)
        val friend = VaultEntry("c", EntryType.CONTACT, "Ana", notes = "t", createdAt = 1, updatedAt = 1)
        val existing = listOf(me, friend)
        val p = plan(read(note(0, "Ana", "t"), login(1, "Ana")), existing)
        assertTrue(p.duplicates.isEmpty())
        val merged = mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), existing, now, newId)
        assertSame(me, merged[0])
        assertSame(friend, merged[1])
        assertEquals(4, merged.size)
        // Even asked directly, a personal entry is never changed.
        assertSame(me, replaceWith(me, importedEntry(EntryType.NOTE, "Otro", notes = "x"), now))
    }

    // ---- The choices ----

    private val bank = saved("b", EntryType.LOGIN, "Banco", user = "ana", url = "https://banco.es", password = "vieja", notes = "nota de la bóveda", tags = listOf("finanzas"))
    private val other = saved("o", EntryType.NOTE, "Otra", notes = "sin tocar")
    private val existing = listOf(bank, other)
    private val incoming = login(0, "BANCO", user = "ana", url = "https://banco.es/entrar", password = "nueva", notes = "nota del archivo", folders = listOf("Casa"))
    private val fresh = login(1, "Nuevo", user = "ana", password = "p")

    @Test fun defaultSkipAddsNothingForDuplicates() {
        val p = plan(read(incoming), existing)
        assertEquals(existing, mergedEntries(p, DuplicateChoice.SKIP, emptyMap(), existing, now, newId))
    }

    @Test fun keepBothAddsTheDuplicateWithANewId() {
        val p = plan(read(incoming, fresh), existing)
        val merged = mergedEntries(p, DuplicateChoice.KEEP_BOTH, emptyMap(), existing, now, newId)
        assertEquals(existing, merged.take(2))
        val added = merged.drop(2)
        assertEquals(listOf("nuevo-0", "nuevo-1"), added.map { it.id })
        assertEquals(listOf("BANCO", "Nuevo"), added.map { it.title })
        added.forEach {
            assertEquals(now, it.createdAt)
            assertEquals(now, it.updatedAt)
            assertTrue(it.passwordHistory.isEmpty())
            assertTrue(it.attachments.isEmpty())
            assertTrue(ORIGIN_TAG_BITWARDEN in it.tags)
        }
    }

    @Test fun overridesChangeTheChoiceOfOneDuplicate() {
        val second = note(2, "Otra", "sin tocar")
        val p = plan(read(incoming, second), existing)
        val merged = mergedEntries(p, DuplicateChoice.SKIP, mapOf(2 to DuplicateChoice.KEEP_BOTH), existing, now, newId)
        assertEquals(listOf("b", "o", "nuevo-0"), merged.map { it.id })
        assertEquals(bank, merged[0])
    }

    @Test fun replaceFollowsTheRule() {
        val withExtras = bank.copy(favorite = true, attachments = listOf(AttachmentRef("a".repeat(32), "f.pdf", 3, "0".repeat(64), "k")))
        val vault = listOf(withExtras, other)
        val p = plan(read(incoming), vault)
        val merged = mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), vault, now, newId, ZoneOffset.UTC)
        assertSame(other, merged[1])
        assertEquals(2, merged.size)
        val r = merged[0]
        // Kept: id, createdAt, favourite and attachments.
        assertEquals("b", r.id)
        assertEquals(5, r.createdAt)
        assertTrue(r.favorite)
        assertEquals(withExtras.attachments, r.attachments)
        assertEquals(now, r.updatedAt)
        // Kept too (ruling R58): the title, whose key («BANCO» against «Banco», case aside) made it a duplicate.
        assertEquals("Banco", r.title)
        // Taken from the file, as they are not empty there.
        assertEquals("https://banco.es/entrar", r.url)
        assertEquals("nueva", r.password)
        // The old password goes to the history (2.0, task 3: the entry as it was, as a version).
        assertEquals(listOf(PasswordChange("vieja", now)), r.oldPasswords())
        assertEquals(bank.password, r.history.single().entry.password)
        assertTrue(r.passwordHistory.isEmpty())
        // Both have notes and they differ: the vault's, then the file's under the heading.
        assertEquals("nota de la bóveda\n\nImportado el 4-11-2026:\nnota del archivo", r.notes)
        // Tags: the union.
        assertEquals(listOf("finanzas", ORIGIN_TAG_BITWARDEN, "casa"), r.tags)
    }

    @Test fun replaceNeverEmptiesAFieldOfTheVault() {
        val bare = importedEntry(EntryType.LOGIN, "Banco", username = "", password = "", url = "", notes = "")
        // Nothing to take: the entry itself, not even a new date (M-2).
        assertSame(bank, replaceWith(bank, bare, now, ZoneOffset.UTC))
        // Blank counts as empty too.
        val blank = importedEntry(EntryType.LOGIN, "   ", username = " ", password = "", url = "\t", notes = "\n")
        assertSame(bank, replaceWith(bank, blank, now, ZoneOffset.UTC))
    }

    @Test fun replaceNotesWhenTheVaultHasNoneOrTheyAreEqual() {
        val noNotes = bank.copy(notes = "")
        assertEquals("del archivo", replaceWith(noNotes, importedEntry(EntryType.LOGIN, "Banco", notes = "del archivo"), now, ZoneOffset.UTC).notes)
        assertEquals(bank.notes, replaceWith(bank, importedEntry(EntryType.LOGIN, "Banco", notes = bank.notes), now, ZoneOffset.UTC).notes)
        // The same password: nothing goes to the history.
        assertTrue(replaceWith(bank, importedEntry(EntryType.LOGIN, "Banco", password = "vieja"), now, ZoneOffset.UTC).oldPasswords().isEmpty())
    }

    @Test fun replaceTakesTheCodeOfATotp() {
        val code = saved("t", EntryType.TOTP, "Correo", totp = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Correo", account = "ana"))
        val incomingCode = importedEntry(EntryType.TOTP, "Correo nuevo", totp = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Correo", digits = 8))
        val r = replaceWith(code, incomingCode, now, ZoneOffset.UTC)
        val totp = checkNotNull(r.totp)
        assertEquals(8, totp.digits)
        // M-1: the file's blank account keeps the vault's; its issuer is there and taken.
        assertEquals("ana", totp.account)
        assertEquals("Correo", totp.issuer)
        assertEquals("Correo nuevo", r.title)
        val renamed = replaceWith(code, importedEntry(EntryType.TOTP, "C", totp = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Otro", account = "eva")), now, ZoneOffset.UTC)
        val renamedTotp = checkNotNull(renamed.totp)
        assertEquals("Otro" to "eva", renamedTotp.issuer to renamedTotp.account)
        assertTrue(r.oldPasswords().isEmpty())
    }

    /** M-2: the same file replaced twice changes the entry once: no second notes block, no new date. */
    @Test fun replacingTwiceWithTheSameFileChangesNothingTheSecondTime() {
        val file = importedEntry(EntryType.LOGIN, "Banco", username = "ana", password = "nueva", notes = "nota del archivo")
            .copy(tags = listOf(ORIGIN_TAG_BITWARDEN))
        val once = replaceWith(bank, file, now, ZoneOffset.UTC)
        assertEquals("nota de la bóveda\n\nImportado el 4-11-2026:\nnota del archivo", once.notes)
        assertSame(once, replaceWith(once, file, now + 86_400_000, ZoneOffset.UTC))
        // And through the whole merge: nothing changes, so nothing is saved.
        val vault = listOf(once, other)
        val p = plan(read(login(0, "Banco", user = "ana", url = "https://banco.es", password = "nueva", notes = "nota del archivo")), vault)
        val merged = mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), vault, now + 1, newId, ZoneOffset.UTC)
        assertSame(once, merged[0])
    }

    /**
     * Branch review A, M-2: notes that differ only by the spaces or line breaks around them are the same notes, as for
     * the duplicate rule (trimmed), so «Reemplazar» does not append them again.
     */
    @Test fun notesThatDifferOnlyByTheSpacesAroundThemAreNotAppended() {
        // Without the origin tag an import adds (a replacement that changes nothing else gives the entry itself).
        val bare = { type: EntryType, title: String, user: String, notes: String ->
            importedEntry(type, title, username = user, notes = notes).copy(tags = emptyList())
        }
        val wifi = saved("n", EntryType.NOTE, "Wifi", notes = "clave 123")
        assertSame(wifi, replaceWith(wifi, bare(EntryType.NOTE, "Wifi", "", "clave 123\n"), now, ZoneOffset.UTC))
        assertSame(wifi, replaceWith(wifi, bare(EntryType.NOTE, "Wifi", "", " \tclave 123 \r\n"), now, ZoneOffset.UTC))
        val ana = saved("l", EntryType.LOGIN, "Foro", user = "ana", notes = "a")
        assertSame(ana, replaceWith(ana, bare(EntryType.LOGIN, "Foro", "ana", " a "), now, ZoneOffset.UTC))
        // And through the merge plan: a duplicate (trimmed) whose notes «Reemplazar» leaves as they are (it only adds the
        // file's origin tag).
        val vault = listOf(wifi)
        val p = plan(read(note(0, "Wifi", "clave 123\n")), vault)
        assertEquals(1, p.duplicates.size)
        assertEquals("clave 123", mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), vault, now, newId, ZoneOffset.UTC)[0].notes)
    }

    /** M-2: notes appended once, trimmed, are recognised the next time even with other spaces around them. */
    @Test fun appendedNotesAreTrimmedAndRecognisedAgain() {
        val once = replaceWith(bank, importedEntry(EntryType.LOGIN, "Banco", notes = "\n nota del archivo \n"), now, ZoneOffset.UTC)
        assertEquals("nota de la bóveda\n\nImportado el 4-11-2026:\nnota del archivo", once.notes)
        assertSame(once, replaceWith(once, importedEntry(EntryType.LOGIN, "Banco", notes = "nota del archivo\n"), now + 1, ZoneOffset.UTC))
        // The vault's notes with spaces at their end still recognise the block.
        val spaced = once.copy(notes = once.notes + "  \n")
        assertSame(spaced, replaceWith(spaced, importedEntry(EntryType.LOGIN, "Banco", notes = "nota del archivo"), now + 2, ZoneOffset.UTC))
    }

    /**
     * Branch review A, M-4: a duplicate login's row shows the user and the host of the file's web, the one «Reemplazar»
     * brings in, as a browser reads it ([urlHost]) and as plain text ([plainReportText]).
     */
    @Test fun aDuplicateLoginShowsTheUserAndTheHostReplaceBringsIn() {
        val login = { user: String, url: String -> importedEntry(EntryType.LOGIN, "Banco", username = user, url = url) }
        assertEquals("ana · banco-acceso.example", duplicateSubline(login("ana", "https://banco-acceso.example/entrar?x=1")))
        // The host a browser goes to (RT-1), not the one after the «@».
        assertEquals("ana · evil.example", duplicateSubline(login("ana", "https://evil.example\\@banco.es/")))
        assertEquals("ana · http://banco.es", duplicateSubline(login("ana", "http://www.banco.es")))
        assertEquals("banco.es", duplicateSubline(login("", "banco.es/login")))
        assertEquals("ana", duplicateSubline(login("ana", "")))
        assertEquals("", duplicateSubline(login(" ", " ")))
        // Hidden characters go, as in every other line of the report.
        assertEquals("ana · banco.es", duplicateSubline(login("a​na‮", "https://banco.es")))
        // A TOTP: its issuer and account; a note: nothing.
        val code = importedEntry(EntryType.TOTP, "C", totp = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Correo", account = "ana"))
        assertEquals("Correo · ana", duplicateSubline(code))
        assertEquals("", duplicateSubline(importedEntry(EntryType.NOTE, "Wifi", notes = "clave")))
    }

    @Test fun twoDuplicatesOfOneEntryAreAppliedInTurn() {
        val hostless = saved("x", EntryType.LOGIN, "Foro", user = "ana", password = "1")
        val p = plan(read(login(0, "Foro", user = "ana", url = "https://a.es", password = "2"), login(1, "Foro", user = "ana", url = "https://b.es", password = "3")), listOf(hostless))
        assertEquals(listOf("x", "x"), p.duplicates.map { it.existingId })
        val r = mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), listOf(hostless), now, newId).single()
        assertEquals("3", r.password)
        assertEquals("https://b.es", r.url)
        assertEquals(listOf("2", "1"), r.oldPasswords().map { it.password })
    }

    // ---- Tags ----

    @Test fun foldersBecomeTagsWithTheOriginTag() {
        assertEquals(listOf(ORIGIN_TAG_BITWARDEN), importTags(emptyList(), ImportFormat.BITWARDEN))
        // M-8: commas become spaces and a run of spaces one, so «Trabajo, Casa» is the same tag as a typed «trabajo casa».
        assertEquals(listOf(ORIGIN_TAG_AEGIS, "trabajo casa"), importTags(listOf("Trabajo, Casa"), ImportFormat.AEGIS))
        assertEquals(listOf(ORIGIN_TAG_AEGIS, "trabajo casa"), importTags(listOf(" Trabajo ,,\t Casa "), ImportFormat.AEGIS))
        assertEquals(listOf(ORIGIN_TAG_BROWSER, "a/b"), importTags(listOf("A/B", " a/b "), ImportFormat.BROWSER_CSV))
        val nested = "Personal/Bancos/Cuentas de ahorro"
        assertEquals(listOf(ORIGIN_TAG_FIREFOX, "personal/bancos/cuentas"), importTags(listOf(nested), ImportFormat.FIREFOX_CSV))
        // Cut by code points: an emoji at the edge stays whole.
        val edge = "x".repeat(23) + "😀"
        assertEquals(listOf(ORIGIN_TAG_TWOFAS, edge), importTags(listOf(edge + "más"), ImportFormat.TWOFAS))
    }

    @Test fun overTenTagsTheLastFoldersGoAndTheOriginStays() {
        val folders = (1..12).map { "c$it" }
        val tags = importTags(folders, ImportFormat.OTPAUTH)
        assertEquals(MAX_TAGS, tags.size)
        assertEquals(ORIGIN_TAG_OTPAUTH, tags.first())
        assertEquals((1..9).map { "c$it" }, tags.drop(1))
        // A folder named like the origin tag does not take a second place.
        assertEquals(listOf(ORIGIN_TAG_APPLE), importTags(listOf("Importado-Apple"), ImportFormat.APPLE_CSV))
    }

    @Test fun newTagsAreThoseTheVaultDoesNotHave() {
        val p = plan(read(login(0, "A", folders = listOf("Casa", "Finanzas")), login(1, "B", folders = listOf("Trabajo"))), listOf(bank))
        assertEquals(setOf(ORIGIN_TAG_BITWARDEN, "casa", "trabajo"), p.newTags)
    }

    @Test fun anImportCreatesAtMostAHundredNewTags() {
        // R18: a hostile export with 20 000 items, each in a folder of its own.
        val items = (0 until 20_000).map { login(it, "T$it", user = "u$it", folders = listOf("carpeta $it")) }
        val p = plan(read(*items.toTypedArray()), listOf(bank))
        assertEquals(IMPORT_MAX_NEW_TAGS + 1, p.newTags.size) // The origin tag and 100 folders.
        val r = mergeImport(p, DuplicateChoice.SKIP, emptyMap(), listOf(bank), now, newId)
        assertEquals(IMPORT_MAX_NEW_TAGS + 1, r.newTags.size)
        assertEquals(p.newTags, r.newTags)
        assertEquals(20_000 - IMPORT_MAX_NEW_TAGS, r.foldersNotTags)
        val added = r.entries.drop(1)
        assertEquals(20_000, added.size)
        // The first hundred keep their folder; the rest keep only the origin tag, which is never dropped.
        assertEquals(listOf(ORIGIN_TAG_BITWARDEN, "carpeta 99"), added[99].tags)
        assertEquals(listOf(ORIGIN_TAG_BITWARDEN), added[100].tags)
        assertEquals(listOf(ORIGIN_TAG_BITWARDEN), added.last().tags)
    }

    @Test fun tagsTheVaultHasAlreadyNeverSpendTheBudget() {
        val budget = TagBudget(had = setOf("casa"), max = 1)
        assertEquals(listOf(ORIGIN_TAG_AEGIS, "casa"), budget.tagsFor(listOf("Casa"), ImportFormat.AEGIS))
        assertEquals(listOf(ORIGIN_TAG_AEGIS, "uno"), budget.tagsFor(listOf("Uno"), ImportFormat.AEGIS))
        assertEquals(listOf(ORIGIN_TAG_AEGIS, "casa", "uno"), budget.tagsFor(listOf("Casa", "Uno"), ImportFormat.AEGIS))
        // Round 2, minor 3: over the cap only the NEW folder tag goes; «casa» (the vault's) and «uno» (created) stay.
        assertEquals(listOf(ORIGIN_TAG_AEGIS, "casa", "uno"), budget.tagsFor(listOf("Casa", "Dos", "Uno"), ImportFormat.AEGIS))
        assertEquals(setOf(ORIGIN_TAG_AEGIS, "uno"), budget.created)
    }

    @Test fun foldersNotTagsCountsTheDistinctFoldersReallyLeftOut() {
        // Round 2, minors 2 and 4: two folders that normalise to one tag count once; an existing tag is never counted.
        val items = (0 until IMPORT_MAX_NEW_TAGS).map { login(it, "T$it", user = "u$it", folders = listOf("c$it")) } +
            login(200, "X", user = "x", folders = listOf("Finanzas", "Sobra")) +
            login(201, "Y", user = "y", folders = listOf("sobra ")) +
            login(202, "Z", user = "z", folders = listOf("Otra"))
        val p = plan(read(*items.toTypedArray()), listOf(bank))
        val r = mergeImport(p, DuplicateChoice.SKIP, emptyMap(), listOf(bank), now, newId)
        assertEquals(2, r.foldersNotTags) // «sobra» and «otra».
        assertEquals(listOf(ORIGIN_TAG_BITWARDEN, "finanzas"), r.entries.first { it.title == "X" }.tags)
    }

    @Test fun aReplacedEntryThatCouldTakeNoTagLostNothing() {
        // Round 2, minor 4: under «Reemplazar» an entry already at MAX_TAGS takes no tag, so the cap took nothing from it.
        val fillers = (0 until IMPORT_MAX_NEW_TAGS).map { login(it, "T$it", user = "u$it", folders = listOf("c$it")) }
        val full = saved("f", EntryType.LOGIN, "Lleno", user = "ana", tags = (1..MAX_TAGS).map { "t$it" })
        val dup = login(500, "Lleno", user = "ana", folders = listOf("Nueva"))
        val p = plan(read(*(fillers + dup).toTypedArray()), listOf(full))
        assertEquals(1, p.duplicates.size)
        assertEquals(0, mergeImport(p, DuplicateChoice.REPLACE, emptyMap(), listOf(full), now, newId).foldersNotTags)
        // With room in the entry, the same folder is really left out.
        val roomy = saved("f", EntryType.LOGIN, "Lleno", user = "ana", tags = listOf("t1"))
        val q = plan(read(*(fillers + dup).toTypedArray()), listOf(roomy))
        val r = mergeImport(q, DuplicateChoice.REPLACE, emptyMap(), listOf(roomy), now, newId)
        assertEquals(1, r.foldersNotTags)
        assertEquals(listOf("t1", ORIGIN_TAG_BITWARDEN), r.entries.first { it.id == "f" }.tags)
    }

    @Test fun theMergedNewTagsLeaveOutWhatAFullEntryCannotTake() {
        // M-1: a vault entry with 10 tags takes none of the file's on «Reemplazar», so they are not new tags.
        val full = saved("b", EntryType.LOGIN, "Banco", user = "ana", tags = (1..MAX_TAGS).map { "t$it" })
        val p = plan(read(login(0, "Banco", user = "ana", folders = listOf("Nueva"))), listOf(full))
        val r = mergeImport(p, DuplicateChoice.REPLACE, emptyMap(), listOf(full), now, newId)
        assertEquals(emptySet<String>(), r.newTags)
        assertEquals(0, r.foldersNotTags)
    }

    // ---- Two imports of the same file ----

    @Test fun secondImportOfSameFileAddsNothing() {
        val file = read(
            login(0, "Banco", user = "ana", url = "https://banco.es", password = "p", folders = listOf("Finanzas")),
            login(1, "Correo", user = "ana"),
            note(2, "Wifi", "clave"),
            totp(3, "Correo", "JBSWY3DPEHPK3PXP"),
            note(4, "Wifi", "clave"),
        )
        val first = plan(file, emptyList())
        assertEquals(4, first.newItems.size)
        assertEquals(1, first.repeatedInFile)
        val once = mergedEntries(first, DuplicateChoice.SKIP, emptyMap(), emptyList(), now, newId)
        assertEquals(4, once.size)
        val second = plan(file, once)
        assertTrue(second.newItems.isEmpty())
        assertEquals(4, second.duplicates.size)
        assertEquals(once, mergedEntries(second, DuplicateChoice.SKIP, emptyMap(), once, now + 1, newId))
    }

    // ---- What is imported is data ----

    @Test fun importedTextIsStoredVerbatim() {
        val hostile = "‮fdp.exe <script>alert(1)</script> javascript:x intent://y \u0000"
        val p = plan(read(login(0, hostile, user = hostile, url = "javascript:alert(1)", notes = hostile)), emptyList())
        val e = mergedEntries(p, DuplicateChoice.SKIP, emptyMap(), emptyList(), now, newId).single()
        assertEquals(hostile, e.title)
        assertEquals(hostile, e.username)
        assertEquals("javascript:alert(1)", e.url)
        assertEquals(hostile, e.notes)
    }

    // ---- The size of the result ----

    @Test fun thePreviewEstimateIsTheBriefsFormulaAndCoversTheRealFile() {
        val entries = (0 until 50).map { saved("id$it", EntryType.LOGIN, "Título $it ñ 😀", user = "u", password = "p".repeat(30), notes = "\"\\\n".repeat(20)) }
        val json = appJson.encodeToString(entries).toByteArray(Charsets.UTF_8).size.toLong()
        assertEquals((json * 4 + 2) / 3 + 4096, estimatedVaultFileBytes(entries))
        val (file, key) = VaultCrypto.create("m".toCharArray(), now, app.seguridad.crypto.KdfParams(1024, 1, 1))
        val real = VaultCrypto.encodeFile(VaultCrypto.write(file, key, VaultData(entries = entries))).toByteArray(Charsets.UTF_8).size.toLong()
        assertTrue("$real > estimate", real <= estimatedVaultFileBytes(entries))
        assertEquals(0, estimatedOverBytes(entries))
    }

    /** I-1: the escaped length is what `appJson` writes, counted without building anything. */
    @Test fun theEscapedLengthIsTheJsonOne() {
        val mixed = "a\"b\\c\b\u000C\n\r\t\u0001\u001F ñ € 😀 \uD800 x"
        val json = appJson.encodeToString(mixed).toByteArray(Charsets.UTF_8).size - 2L
        assertEquals(json, jsonEscapedLength(mixed))
        assertEquals(6L * 1000, jsonEscapedLength("\u0001".repeat(1000)))
        val entries = (0 until 20).map { saved("id$it", EntryType.LOGIN, "T\u0002$it", user = "\"u\"", notes = "\n".repeat(50), tags = listOf("a")) }
        val whole = appJson.encodeToString(entries).toByteArray(Charsets.UTF_8).size.toLong()
        assertTrue(escapedTextBytes(entries) <= whole)
        assertTrue("Close: within the field names", whole - escapedTextBytes(entries) < 20L * 200)
    }

    /** I-1: text that JSON escapes six times over is found too big from its count alone, never encoded. */
    @Test fun aHeavilyEscapedImportIsTooBigWithoutEncodingIt() {
        val hostile = (0 until 75).map { saved("id$it", EntryType.NOTE, "N$it", notes = "\u0001".repeat(63_000)) }
        val low = escapedTextBytes(hostile)
        assertTrue(low > 75L * 63_000 * 6 - 1)
        // The bound's own estimate: the formula on the counted bytes, not on an encoded string.
        assertEquals((low * 4 + 2) / 3 + 4096, estimatedVaultFileBytes(hostile))
        assertTrue(estimatedOverBytes(hostile) > 0)
    }

    @Test fun anEstimateOverTheLimitSaysByHowMuch() {
        // 2.0: against the cap of the copy a v4 vault gets (marker 3, 33 MiB), each entry within its own 64 KiB.
        val big = (0 until 600).map { saved("id$it", EntryType.NOTE, "N$it", notes = "x".repeat(60_000)) }
        val over = estimatedOverBytes(big)
        assertTrue(over > 0)
        assertEquals(estimatedVaultFileBytes(big) - BACKUP_V3_MAX_ENTRY_BYTES, over)
        assertNotEquals(0L, over)
        // What passed the 6 MB of marker 2 fits now.
        assertEquals(0, estimatedOverBytes((0 until 120).map { saved("id$it", EntryType.NOTE, "N$it", notes = "x".repeat(60_000)) }))
    }

    /** 2.0, ruling T1-3: an item over the 64 KiB of one entry is in the plan as not fitting, with its own text. */
    @Test fun anItemOverTheEntryLimitIsSkippedInThePlan() {
        // Quotes take two bytes each in JSON: 40 000 of them are 80 KB, though the note has 40 000 characters.
        val big = item(3, importedEntry(EntryType.NOTE, "Grande", notes = "\"".repeat(40_000)))
        val bigDuplicate = item(4, importedEntry(EntryType.NOTE, "Nota", notes = "x".repeat(66_000)))
        val small = item(5, importedEntry(EntryType.NOTE, "Pequeña", notes = "x".repeat(60_000)))
        val p = plan(read(big, bigDuplicate, small), listOf(saved("n", EntryType.NOTE, "Nota", notes = "x".repeat(66_000))))
        assertEquals(listOf(5), p.newItems.map { it.index })
        assertEquals(emptyList<Duplicate>(), p.duplicates)
        assertEquals(
            listOf(Skipped(3, "Grande", SkipReason.ENTRY_TOO_BIG), Skipped(4, "Nota", SkipReason.ENTRY_TOO_BIG), Skipped(99, "Passkey", SkipReason.PASSKEY)),
            p.skipped,
        )
        assertEquals(SKIP_ENTRY_TOO_BIG, SkipReason.ENTRY_TOO_BIG.text)
        assertEquals("Pasa de 64 KB, el máximo de una entrada", SKIP_ENTRY_TOO_BIG)
        // The limit is the vault's own: what the import keeps is within it, the stored form (tags and dates) included.
        assertTrue(VaultLimits.ENTRY_MAX_JSON_BYTES == 64 * 1024)
        assertTrue(entryFitsImported(small.entry, listOf("una-carpeta"), ImportFormat.BITWARDEN))
        assertFalse(entryFitsImported(big.entry, emptyList(), ImportFormat.BITWARDEN))
    }

    // ---- 2.0, task 3: the trash (focus 5) ----

    @Test fun trashIsNotADuplicate() {
        val trashed = saved("t", EntryType.LOGIN, "Banco", user = "ana", url = "https://banco.es/", tags = listOf("solo-en-papelera"))
            .copy(deletedAt = now - 1)
        val code = saved("c", EntryType.TOTP, "Correo", totp = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Correo")).copy(deletedAt = now - 1)
        val live = saved("v", EntryType.NOTE, "Lista", notes = "pan")
        val p = plan(
            read(
                login(0, "Banco", user = "ana", url = "https://banco.es/", folders = listOf("solo-en-papelera")),
                totp(1, "Correo", "JBSWY3DPEHPK3PXP"),
                note(2, "Lista", "pan"),
            ),
            listOf(trashed, code, live),
        )
        // What is in the trash is no duplicate: the items enter as new.
        assertEquals(listOf(0, 1), p.newItems.map { it.index })
        assertEquals(listOf(2), p.duplicates.map { it.item.index })
        assertEquals("v", p.duplicates.single().existingId)
        // A tag only an entry in the trash has is a new one: it spends the budget as any other.
        assertTrue("solo-en-papelera" in p.newTags)
        val merged = mergeImport(p, DuplicateChoice.SKIP, emptyMap(), listOf(trashed, code, live), now, newId, ZoneOffset.UTC)
        assertTrue("solo-en-papelera" in merged.newTags)
        // The trash is kept as it is.
        assertEquals(listOf(trashed, code, live), merged.entries.take(3))
        assertEquals(5, merged.entries.size)
    }
}
