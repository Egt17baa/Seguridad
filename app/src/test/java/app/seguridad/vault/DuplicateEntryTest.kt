package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

/** 2.0, task 5 (design «Duplicar»): «<título> (copia)» with everything but the attachments and the history, and a new id. */
class DuplicateEntryTest {
    @get:Rule val tmp = TemporaryFolder()

    private var clock = 1_800_000_000_000L
    private val master = "maestra-de-prueba-duplicar"

    private fun store() = VaultStore(
        File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
        elapsed = { clock }, clockHighWater = { clock }, trashClockMark = { clock },
    )

    private fun VaultStore.open() = state.value as VaultState.Unlocked

    @Test fun duplicateKeepsAllButAttachmentsAndHistory() = runTest {
        val s = store()
        s.load()
        s.create(master.toCharArray())
        assertTrue(s.createFolder("Casa", null) != null)
        val folder = s.open().data.folders.single().id
        val card = VaultEntry(
            id = "c1", type = EntryType.CARD, title = "Tarjeta del banco", createdAt = 1, updatedAt = 1, favorite = true, tags = listOf("casa"),
            notes = "**nota**", notesFormat = NotesFormat.LIMITED, expiresAt = 5, rotateMonths = 6,
            fields = listOf(CustomField("number", "Número", FieldKind.HIDDEN, "4000 0000 0000 0002")),
        )
        assertTrue(s.upsert(card))
        s.moveEntries(setOf("c1"), folder)
        assertTrue(s.upsert(card.copy(notes = "**otra nota**")))
        assertTrue(s.addAttachment("c1", ByteArrayInputStream(byteArrayOf(1, 2, 3)), "a.bin"))
        val saved = s.open().entries.single()
        assertTrue(saved.history.isNotEmpty() && saved.attachments.isNotEmpty())
        clock += 1_000
        val copy = s.duplicate("c1")!!
        assertNotEquals("c1", copy.id)
        assertTrue(isEntryId(copy.id))
        assertEquals("Tarjeta del banco (copia)", copy.title)
        assertTrue(copy.history.isEmpty() && copy.attachments.isEmpty() && copy.passwordHistory.isEmpty())
        assertNull(copy.lastUsedAt)
        assertEquals(clock, copy.createdAt)
        assertEquals(clock, copy.updatedAt)
        // Everything else as it is: the fields, the notes and their format, the dates, the folder, the tags.
        assertEquals(saved.copy(id = copy.id, title = copy.title, history = emptyList(), attachments = emptyList(), createdAt = clock, updatedAt = clock, lastUsedAt = null), copy)
        assertEquals(copy, s.open().entries.first { it.id == copy.id })
        assertEquals(2, s.open().entries.size)
        // The original keeps its attachment and its history.
        assertEquals(saved, s.open().entries.first { it.id == "c1" })
        // Nothing for an id that is not there, one in the trash, or with the vault locked.
        assertNull(s.duplicate("no-existe"))
        s.moveToTrash(setOf("c1"))
        assertNull(s.duplicate("c1"))
        s.lock()
        assertNull(s.duplicate(copy.id))
    }

    /**
     * Review M-1: the date the password was last changed is the store's own mark ([VaultEntry.passwordChangedAt]): set
     * when the password (a login's, a wifi's) changes, kept by every other edit, by «Borrar historial» and by
     * «Duplicar»; «Salud» and the reminder read the same one ([lastPasswordChange]).
     */
    @Test fun passwordAgeIsKept() = runTest {
        val s = store()
        s.load()
        s.create(master.toCharArray())
        val created = clock
        val login = VaultEntry("l", EntryType.LOGIN, "Banco", password = "primera-clave", createdAt = created, updatedAt = created, rotateMonths = 6)
        assertTrue(s.upsert(login))
        fun saved() = s.open().entries.first { it.id == "l" }
        assertEquals(created, saved().passwordChangedAt)
        clock += 10_000
        assertTrue(s.upsert(saved().copy(notes = "otra nota", passwordChangedAt = null))) // The editor never sends it.
        assertEquals(created, saved().passwordChangedAt)
        clock += 10_000
        val changedAt = clock
        assertTrue(s.upsert(saved().copy(password = "segunda-clave", passwordChangedAt = 5)))
        assertEquals(changedAt, saved().passwordChangedAt)
        clock += 10_000
        assertTrue(s.clearHistory("l"))
        assertEquals(changedAt, saved().passwordChangedAt)
        assertEquals(changedAt, lastPasswordChange(saved()))
        assertEquals(changedAt, s.duplicate("l")!!.passwordChangedAt)
        // A version restored with the old password: changed again, now.
        clock += 10_000
        assertTrue(s.upsert(saved().copy(notes = "tercera")))
        assertTrue(s.restoreVersion("l", saved().history.first().savedAt, 0))
        assertEquals(changedAt, saved().passwordChangedAt) // That version had the same password.
        // A wifi's password too.
        val wifi = VaultEntry("w", EntryType.WIFI, "Casa", createdAt = clock, updatedAt = clock, fields = listOf(CustomField("password", "Contraseña", FieldKind.HIDDEN, "clave-wifi-1")))
        assertTrue(s.upsert(wifi))
        clock += 10_000
        assertTrue(s.upsert(wifi.copy(fields = listOf(CustomField("password", "Contraseña", FieldKind.HIDDEN, "clave-wifi-2")))))
        assertEquals(clock, s.open().entries.first { it.id == "w" }.passwordChangedAt)
        // Without the mark (an entry of before): the newest change the history or the list of 1.4.3 knows, or its last edit.
        val old = VaultEntry("v", EntryType.LOGIN, "V", password = "p", createdAt = 1, updatedAt = 50, passwordHistory = listOf(PasswordChange("o", 20)))
        assertEquals(20L, lastPasswordChange(old))
        assertEquals(50L, lastPasswordChange(old.copy(passwordHistory = emptyList())))
        // A note has no password: no mark.
        assertTrue(s.upsert(VaultEntry("n", EntryType.NOTE, "N", notes = "x", createdAt = 1, updatedAt = 1)))
        assertEquals(null, s.open().entries.first { it.id == "n" }.passwordChangedAt)
    }
}
