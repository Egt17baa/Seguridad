package app.seguridad.vault

import app.seguridad.crypto.Aead
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.aead
import app.seguridad.crypto.randomBytes
import app.seguridad.core.appJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64

/** 2.0, tarea 3: el historial por entrada (20 versiones y 64 KiB) y su conciliación con `passwordHistory` (M-4 de la tarea 1). */
class EntryHistoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private var clock = 1_800_000_000_000L
    private val master = "maestra-de-prueba-historial"

    private fun store(dir: File = tmp.root) = VaultStore(
        File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
        elapsed = { clock },
    )

    private fun login(pw: String, notes: String = "") =
        VaultEntry("a", EntryType.LOGIN, "Correo", username = "ana@example.com", password = pw, notes = notes, createdAt = 0, updatedAt = 0)

    private fun VaultStore.entry(id: String = "a") = (state.value as VaultState.Unlocked).entries.single { it.id == id }

    private suspend fun open(): VaultStore = store().apply { load(); create(master.toCharArray()) }

    /** Saves [e] at a later time, as the editor does (it sends no history). */
    private suspend fun VaultStore.edit(e: VaultEntry) {
        clock += 1_000L
        assertTrue(upsert(e.copy(updatedAt = clock)))
    }

    @Test fun editAddsVersion() = runTest {
        val s = open()
        s.edit(login("uno"))
        s.edit(login("dos"))
        val e = s.entry()
        assertEquals("dos", e.password)
        val v = e.history.single()
        assertEquals(clock, v.savedAt)
        assertEquals("uno", v.entry.password)
        assertTrue(v.entry.history.isEmpty())
        // The old password, on the card and in «Salud», from the history.
        assertEquals(listOf(PasswordChange("uno", clock)), e.oldPasswords())
        // Not in the legacy list: 2.0 never writes it again.
        assertTrue(e.passwordHistory.isEmpty())
        // Any field counts, not only the password; newest first.
        s.edit(login("dos", notes = "nota nueva"))
        assertEquals(listOf("dos", "uno"), s.entry().history.map { it.entry.password })
        assertEquals("", s.entry().history.first().entry.notes)
        assertEquals(listOf("uno"), s.entry().oldPasswords().map { it.password })
        // It survives a lock.
        s.lock()
        s.unlock(master.toCharArray())
        assertEquals(2, s.entry().history.size)
    }

    @Test fun noChangeNoVersion() = runTest {
        val s = open()
        s.edit(login("uno"))
        s.edit(login("uno"))
        assertTrue(s.entry().history.isEmpty())
        val e = login("uno")
        assertTrue(e.copy(updatedAt = 5).withVersion(e, clock).history.isEmpty())
    }

    @Test fun lastUsedAtIsNotAVersion() = runTest {
        val e = login("uno")
        assertTrue(e.copy(lastUsedAt = 99, updatedAt = 7).withVersion(e, clock).history.isEmpty())
        val s = open()
        s.edit(login("uno"))
        s.edit(login("uno").copy(lastUsedAt = 77L))
        assertTrue(s.entry().history.isEmpty())
        assertEquals(77L, s.entry().lastUsedAt)
    }

    @Test fun capAt20() = runTest {
        var e = login("p0")
        for (i in 1..25) e = login("p$i").copy(history = e.history).withVersion(e, i.toLong())
        assertEquals(VaultLimits.HISTORY_MAX_VERSIONS, e.history.size)
        // The newest stay, newest first: the 5 oldest (p0 to p4) went.
        assertEquals((24 downTo 5).map { "p$it" }, e.history.map { it.entry.password })
        assertEquals((25L downTo 6L).toList(), e.history.map { it.savedAt })
    }

    @Test fun capAt64KiB() = runTest {
        val big = "x".repeat(20 * 1024)
        val s = open()
        s.edit(login("p0", notes = big + "0"))
        for (i in 1..5) s.edit(login("p$i", notes = big + i))
        val h = s.entry().history
        assertTrue(h.size in 1..3)
        assertTrue(jsonBytes(kotlinx.serialization.builtins.ListSerializer(EntryVersion.serializer()), h) <= VaultLimits.HISTORY_MAX_BYTES)
        // The newest versions are the ones kept.
        assertEquals((4 downTo 5 - h.size).map { "p$it" }, h.map { it.entry.password })
    }

    @Test fun restoreVersionMakesNewVersion() = runTest {
        val s = open()
        s.edit(login("uno"))
        s.edit(login("dos"))
        s.edit(login("tres"))
        val first = s.entry().history.last()
        assertEquals("uno", first.entry.password)
        assertFalse(s.restoreVersion("a", 12345L))
        assertFalse(s.restoreVersion("otra", first.savedAt))
        clock += 1_000L
        assertTrue(s.restoreVersion("a", first.savedAt))
        val e = s.entry()
        assertEquals("uno", e.password)
        assertEquals(clock, e.updatedAt)
        // What was there is now the newest version; nothing older is lost.
        assertEquals(listOf("tres", "dos", "uno"), e.history.map { it.entry.password })
        assertEquals(clock, e.history.first().savedAt)
    }

    @Test fun clearHistoryEmptiesIt() = runTest {
        val s = open()
        s.edit(login("uno"))
        s.edit(login("dos"))
        assertTrue(s.clearHistory("a"))
        assertTrue(s.entry().history.isEmpty())
        assertTrue(s.entry().oldPasswords().isEmpty())
        assertFalse(s.clearHistory("a"))
        assertFalse(s.clearHistory("no-existe"))
        // «Borrar historial» of every entry, those in the trash too.
        s.edit(login("tres"))
        assertTrue(s.upsert(VaultEntry("n", EntryType.NOTE, "Nota", notes = "uno", createdAt = 0, updatedAt = 0)))
        assertTrue(s.upsert(VaultEntry("n", EntryType.NOTE, "Nota", notes = "dos", createdAt = 0, updatedAt = 0)))
        s.moveToTrash(setOf("n"))
        assertEquals(2, s.clearAllHistory())
        assertEquals(0, s.clearAllHistory())
        val all = (s.state.value as VaultState.Unlocked).data.entries
        assertTrue(all.all { it.history.isEmpty() && it.passwordHistory.isEmpty() })
    }

    @Test fun versionsHaveNoAttachments() = runTest {
        val s = open()
        s.edit(login("uno"))
        assertTrue(s.addAttachment("a", ByteArrayInputStream(ByteArray(16) { 1 }), "factura.pdf"))
        s.edit(login("dos"))
        val e = s.entry()
        assertEquals(1, e.attachments.size)
        assertTrue(e.history.single().entry.attachments.isEmpty())
        // Pure: what the caller passes as previous never brings its attachments, history or legacy list into a version.
        val prev = login("uno").copy(
            attachments = e.attachments,
            history = listOf(EntryVersion(1, login("cero"))),
            passwordHistory = listOf(PasswordChange("cero", 1)),
        )
        val v = login("dos").copy(history = prev.history).withVersion(prev, 9).history.first().entry
        assertTrue(v.attachments.isEmpty() && v.history.isEmpty() && v.passwordHistory.isEmpty())
    }

    @Test fun personalEntriesKeepNoHistory() = runTest {
        val s = open()
        val me = VaultEntry("me", EntryType.IDENTITY, "Yo", identitySeed = "AAAA", createdAt = 0, updatedAt = 0)
        assertTrue(s.upsert(me))
        assertTrue(s.upsert(me.copy(title = "Yo mismo")))
        assertTrue(s.entry("me").history.isEmpty())
    }

    @Test fun oldPasswordsJoinLegacyAndHistory() {
        // An entry from 1.4.3 passed to v4: its versions repeat the legacy list, shown once.
        val legacy = listOf(PasswordChange("vieja-2", 20), PasswordChange("vieja-1", 10))
        val base = login("actual")
        val upgraded = base.copy(
            passwordHistory = legacy,
            history = legacy.map { EntryVersion(it.changedAt, base.copy(password = it.password)) },
        )
        assertEquals(legacy, upgraded.oldPasswords())
        // A version trimmed by the limits: the legacy list still shows it.
        assertEquals(legacy, upgraded.copy(history = upgraded.history.take(1)).oldPasswords())
        // A change in 2.0 goes before them.
        val changed = login("nueva").copy(history = upgraded.history, passwordHistory = legacy).withVersion(upgraded, 30)
        assertEquals(listOf("actual", "vieja-2", "vieja-1"), changed.oldPasswords().map { it.password })
        assertEquals(30L, changed.oldPasswords().first().changedAt)
        // A version that only changed other fields, or another type's, is no old password.
        val noteOnly = login("nueva", notes = "otra").copy(history = changed.history).withVersion(changed, 40)
        assertEquals(listOf("actual", "vieja-2", "vieja-1"), noteOnly.oldPasswords().map { it.password })
        val asNote = VaultEntry("a", EntryType.NOTE, "Correo", password = "", createdAt = 0, updatedAt = 0)
            .withVersion(VaultEntry("a", EntryType.SEED, "Correo", password = "frase-extra", createdAt = 0, updatedAt = 0), 50)
        assertTrue(asNote.oldPasswords().isEmpty())
        // Imports: «Reemplazar» keeps the old password in the history too.
        val replaced = app.seguridad.imports.replaceWith(login("de-antes"), login("del-archivo"), 60)
        assertEquals("del-archivo", replaced.password)
        assertEquals(listOf(PasswordChange("de-antes", 60)), replaced.oldPasswords())
    }

    /**
     * M-4 of task 1: a 1.4.3 vault with a large entry (notes near the 64 KiB of the history) passes to v4: what [fitted]
     * trims is the oldest versions, the newest stays, and no old password disappears from the card ([oldPasswords]).
     */
    @Test fun upgradeOfLargeEntryKeepsNewestAndEveryOldPassword() = runTest {
        val fixture = VaultCrypto.decodeFile(javaClass.getResourceAsStream("/seguridad/vault-1.4.3.json")!!.use { it.readBytes() }.decodeToString())
        val master143 = "maestra-de-prueba-143"
        val dek = VaultCrypto.unlock(fixture, master143.toCharArray())
        val legacy = (5 downTo 1).map { PasswordChange("clave-vieja-$it", it * 1_000L) }
        val large = VaultEntry(
            "grande", EntryType.LOGIN, "Grande", password = "clave-actual", notes = "n".repeat(40 * 1024),
            createdAt = 0, updatedAt = 0, passwordHistory = legacy,
        )
        // A version-3 file with that entry, sealed as 1.4.3 sealed its entries.
        val json = appJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(VaultEntry.serializer()), listOf(large)).toByteArray()
        val nonce = randomBytes(12)
        val sealed = Base64.getEncoder().encodeToString(nonce + aead(Aead.AES_256_GCM, true, dek, nonce, dataAad(fixture, 3), json))
        val v3 = fixture.copy(data = sealed)
        assertEquals(3, v3.version)
        val dir = tmp.newFolder("p143")
        File(dir, "vault.bin").writeText(VaultCrypto.encodeFile(v3))
        clock = 1_800_000_000_000L + 3_600_000L
        val s = store(dir).apply { load(); unlock(master143.toCharArray()) }
        assertTrue(s.completeUpgrade())
        val e = s.entry("grande")
        // One version of 40 KiB fits in the 64 KiB; the newest one (the last password before the current one) is kept.
        assertEquals(listOf("clave-vieja-5"), e.history.map { it.entry.password })
        assertEquals(5_000L, e.history.single().savedAt)
        // The card still shows all five, newest first.
        assertEquals(legacy, e.oldPasswords())
        // And saved so: a new store reads the same.
        s.lock()
        val again = store(dir).apply { load(); unlock(master143.toCharArray()) }
        assertEquals(legacy, again.entry("grande").oldPasswords())
        assertNotNull(again.entry("grande").history.single())
    }
}
