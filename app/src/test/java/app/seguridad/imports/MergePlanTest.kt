package app.seguridad.imports

import app.seguridad.core.appJson
import app.seguridad.crypto.TotpSecret
import app.seguridad.tools.qr.parseLink
import app.seguridad.vault.AttachmentRef
import app.seguridad.vault.BACKUP_MAX_ENTRY_BYTES
import app.seguridad.vault.EntryType
import app.seguridad.vault.MAX_TAGS
import app.seguridad.vault.PasswordChange
import app.seguridad.vault.VaultCrypto
import app.seguridad.vault.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
                login(0, "  BANCO ", user = " ana ", url = "http://banco.ES/otra"),
                login(1, "Banco", user = "ana", url = "https://otro.es/"),
                login(2, "Banco", user = "Ana", url = "https://banco.es/"), // the user keeps its case: a capital is visible (R34)
                login(3, "Banca", user = "ana", url = "https://banco.es/"),
                login(4, "Banco", user = "ana", url = "https://user:clave@WWW.banco.es:8443/x"),
            ),
            listOf(bank),
        )
        assertEquals(listOf(0), p.duplicates.map { it.item.index })
        assertEquals("b", p.duplicates.single().existingId)
        assertEquals(listOf(1, 2, 3), p.newItems.map { it.index })
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
        // No host: the whole text, trimmed and in lower case; no URL: null.
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
            val browser = parseLink(url).host?.removePrefix("www.")
            assertEquals(url, browser?.ifEmpty { null } ?: url.trim().lowercase(Locale.ROOT), urlHost(url))
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

    // ---- RT-2 (ruling R34): what looks the same is a duplicate ----

    @Test fun aTitleOrUserThatLooksTheSameIsADuplicate() {
        val bank = saved("b", EntryType.LOGIN, "Café Banco", user = "ana", url = "https://banco.es/")
        val looks = listOf(
            "Café Ban​co" to "ana", "Café Banco" to "ana", "‮Café Banco" to "ana", "Café  Banco" to "ana",
            "Café Banco\u0000" to "ana", "CAFÉ BANCO" to "an­a", "Café Banco" to "﻿ana⁠", "Café Banco" to "a͏na ",
        )
        for ((title, user) in looks) {
            val p = plan(read(login(0, title, user = user, url = "https://banco.es/")), listOf(bank))
            assertEquals("«$title» / «$user»", listOf("b"), p.duplicates.map { it.existingId })
        }
        // What is visible still counts: a capital in the user, another letter, a space inside a word, full-width letters.
        for ((title, user) in listOf("Café Banco" to "Ana", "Cafe Banco" to "ana", "Café Ban co" to "ana", "Ｃａｆé Ｂａｎｃｏ" to "ana", "Café Banco" to "a na")) {
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
        // Taken from the file, as they are not empty there.
        assertEquals("BANCO", r.title)
        assertEquals("https://banco.es/entrar", r.url)
        assertEquals("nueva", r.password)
        // The old password goes to the history (withNewPassword).
        assertEquals(listOf(PasswordChange("vieja", now)), r.passwordHistory)
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
        assertTrue(replaceWith(bank, importedEntry(EntryType.LOGIN, "Banco", password = "vieja"), now, ZoneOffset.UTC).passwordHistory.isEmpty())
    }

    @Test fun replaceTakesTheCodeOfATotp() {
        val code = saved("t", EntryType.TOTP, "Correo", totp = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Correo", account = "ana"))
        val incomingCode = importedEntry(EntryType.TOTP, "Correo nuevo", totp = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Correo", digits = 8))
        val r = replaceWith(code, incomingCode, now, ZoneOffset.UTC)
        assertEquals(8, r.totp!!.digits)
        // M-1: the file's blank account keeps the vault's; its issuer is there and taken.
        assertEquals("ana", r.totp!!.account)
        assertEquals("Correo", r.totp!!.issuer)
        assertEquals("Correo nuevo", r.title)
        val renamed = replaceWith(code, importedEntry(EntryType.TOTP, "C", totp = TotpSecret("JBSWY3DPEHPK3PXP", issuer = "Otro", account = "eva")), now, ZoneOffset.UTC)
        assertEquals("Otro" to "eva", renamed.totp!!.issuer to renamed.totp!!.account)
        assertTrue(r.passwordHistory.isEmpty())
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

    @Test fun twoDuplicatesOfOneEntryAreAppliedInTurn() {
        val hostless = saved("x", EntryType.LOGIN, "Foro", user = "ana", password = "1")
        val p = plan(read(login(0, "Foro", user = "ana", url = "https://a.es", password = "2"), login(1, "Foro", user = "ana", url = "https://b.es", password = "3")), listOf(hostless))
        assertEquals(listOf("x", "x"), p.duplicates.map { it.existingId })
        val r = mergedEntries(p, DuplicateChoice.REPLACE, emptyMap(), listOf(hostless), now, newId).single()
        assertEquals("3", r.password)
        assertEquals("https://b.es", r.url)
        assertEquals(listOf("2", "1"), r.passwordHistory.map { it.password })
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
        val real = VaultCrypto.encodeFile(VaultCrypto.write(file, key, entries)).toByteArray(Charsets.UTF_8).size.toLong()
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
        val big = (0 until 100).map { saved("id$it", EntryType.NOTE, "N$it", notes = "x".repeat(64 * 1024)) }
        val over = estimatedOverBytes(big)
        assertTrue(over > 0)
        assertEquals(estimatedVaultFileBytes(big) - BACKUP_MAX_ENTRY_BYTES, over)
        assertNotEquals(0L, over)
    }
}
