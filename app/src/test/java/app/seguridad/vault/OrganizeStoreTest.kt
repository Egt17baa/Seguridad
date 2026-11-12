package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 2.0, tarea 4: vistas guardadas, colores de etiquetas y `markUsed` en la tienda (sin versión ni copia automática). */
class OrganizeStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private var clock = 1_800_000_000_000L
    private val master = "maestra-de-prueba-organizar"
    private val file get() = File(tmp.root, "vault.bin")

    private fun store() = VaultStore(
        file, Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
        elapsed = { clock }, clockHighWater = { clock }, trashClockMark = { clock },
    )

    private fun login(id: String, title: String = "Acceso $id", tags: List<String> = emptyList()) =
        VaultEntry(id, EntryType.LOGIN, title, password = "clave-$id", createdAt = 0, updatedAt = 0, tags = tags)

    private fun VaultStore.data() = (state.value as VaultState.Unlocked).data

    private suspend fun open(): VaultStore = store().apply {
        load()
        create(master.toCharArray())
    }

    @Test fun markUsedIsNoVersionNorChange() = runTest {
        val s = open()
        assertTrue(s.upsert(login("a")))
        val revision = s.revision.value
        val changedAt = file.lastModified()
        clock += 3_600_000
        assertTrue(s.markUsed("a"))
        val used = s.data().live.single()
        assertEquals(clock, used.lastUsedAt)
        assertTrue(used.history.isEmpty()) // No version.
        assertEquals(0L, used.updatedAt) // Not a modification.
        assertEquals(revision, s.revision.value) // Not a change for the copy on every change.
        assertEquals(changedAt, file.lastModified()) // Nor for the copy due when the vault opens.
        // Saved: it is there after locking and opening again.
        s.lock()
        s.unlock(master.toCharArray())
        assertEquals(clock, s.data().live.single().lastUsedAt)
        // Used again within a minute: nothing written.
        clock += 30_000
        assertFalse(s.markUsed("a"))
        assertFalse(s.markUsed("no-existe"))
        // In the trash: never.
        clock += 120_000
        s.moveToTrash(setOf("a"))
        assertFalse(s.markUsed("a"))
        s.lock()
        assertFalse(s.markUsed("a"))
    }

    @Test fun viewsSavedReplacedAndDeleted() = runTest {
        val s = open()
        val v = SavedView("v1", " Trabajo ", EntryFilter(text = "banco", sort = SortOrder.USED))
        assertTrue(s.saveView(v))
        assertEquals("Trabajo", s.data().views.single().name)
        // The same id replaces it; another view with the same name (as compareKey reads it) is refused.
        assertTrue(s.saveView(v.copy(name = "Trabajo", filter = EntryFilter(favorites = true))))
        assertEquals(EntryFilter(favorites = true), s.data().views.single().filter)
        assertEquals(VIEW_NAME_TAKEN, assertThrows(OrganizeRefused::class.java) { runBlocking { s.saveView(SavedView("v2", "TRABAJO", EntryFilter())) } }.message)
        assertEquals(VIEW_NAME_EMPTY, assertThrows(OrganizeRefused::class.java) { runBlocking { s.saveView(SavedView("v2", " ", EntryFilter())) } }.message)
        // A view of a folder that is not in the vault is refused.
        assertThrows(OrganizeRefused::class.java) { runBlocking { s.saveView(SavedView("v3", "X", EntryFilter(folderId = "falta"))) } }
        // 50 views at most.
        repeat(VaultLimits.VIEWS_MAX - 1) { assertTrue(s.saveView(SavedView("n$it", "Vista $it", EntryFilter()))) }
        assertEquals(TOO_MANY_VIEWS, assertThrows(VaultLimitExceeded::class.java) { runBlocking { s.saveView(SavedView("over", "Otra", EntryFilter())) } }.message)
        assertTrue(s.deleteView("v1"))
        assertFalse(s.deleteView("v1"))
        assertEquals(VaultLimits.VIEWS_MAX - 1, s.data().views.size)
    }

    @Test fun tagColors() = runTest {
        val s = open()
        assertTrue(s.upsert(login("a", tags = listOf("banco"))))
        assertTrue(s.setTagColor("Banco", TagColor.GREEN)) // As the tags are stored: lowercase.
        assertEquals(mapOf("banco" to TagColor.GREEN), s.data().tagColors)
        assertFalse(s.setTagColor("banco", TagColor.GREEN))
        assertTrue(s.setTagColor("banco", null))
        assertTrue(s.data().tagColors.isEmpty())
        assertThrows(OrganizeRefused::class.java) { runBlocking { s.setTagColor(" , ", TagColor.RED) } }
        // 200 at most; at the limit, the colours of tags no entry has (not even in the trash) go first.
        repeat(VaultLimits.TAG_COLORS_MAX) { assertTrue(s.setTagColor("t$it", TagColor.BLUE)) }
        assertEquals(200, s.data().tagColors.size)
        assertTrue(s.setTagColor("banco", TagColor.RED))
        assertEquals(mapOf("banco" to TagColor.RED), s.data().tagColors)
        assertTrue(s.upsert(login("b", tags = (0 until 10).map { "u$it" })))
        val many = (0 until 20).map { i -> login("m$i", tags = (0 until 10).map { "m$i-$it" }) }
        assertTrue(s.addAll(many))
        val used = listOf("banco") + (0 until 10).map { "u$it" } + many.flatMap { it.tags }
        used.drop(1).take(VaultLimits.TAG_COLORS_MAX - 1).forEach { assertTrue(s.setTagColor(it, TagColor.TEAL)) }
        assertEquals(TOO_MANY_TAG_COLORS, assertThrows(VaultLimitExceeded::class.java) { runBlocking { s.setTagColor(used.last(), TagColor.TEAL) } }.message)
        assertNull(s.data().tagColors[used.last()])
    }

    /** An entry of 1.4.3 over 64 KiB (its notes had no limit) can still be moved, used and sent to the trash. */
    @Test fun oldBigEntryCanMoveAndBeUsed() {
        val big = VaultEntry("big", EntryType.NOTE, "Grande", notes = "x".repeat(70_000), createdAt = 0, updatedAt = 0)
        val before = VaultData(entries = listOf(big), folders = listOf(Folder("f", "F")))
        before.copy(entries = listOf(big.copy(folderId = "f"))).fittedForSave(before)
        before.copy(entries = listOf(big.copy(lastUsedAt = 5, deletedAt = 6))).fittedForSave(before)
        assertEquals(ENTRY_TOO_BIG, assertThrows(VaultLimitExceeded::class.java) { before.copy(entries = listOf(big.copy(notes = big.notes + "y"))).fittedForSave(before) }.message)
    }
}
