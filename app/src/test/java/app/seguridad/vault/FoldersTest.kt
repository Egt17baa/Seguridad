package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 2.0, tarea 4: las carpetas anidadas (diseño «Carpetas anidadas»): 5 niveles, sin ciclos, 500, nombres con `compareKey`. */
class FoldersTest {
    @get:Rule val tmp = TemporaryFolder()

    private var clock = 1_800_000_000_000L
    private val master = "maestra-de-prueba-carpetas"

    private fun store() = VaultStore(
        File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
        elapsed = { clock }, clockHighWater = { clock }, trashClockMark = { clock },
    )

    private fun note(id: String, folderId: String? = null) =
        VaultEntry(id, EntryType.NOTE, "Nota $id", notes = "texto $id", createdAt = 0, updatedAt = 0, folderId = folderId)

    private fun VaultStore.data() = (state.value as VaultState.Unlocked).data

    private suspend fun open(): VaultStore = store().apply {
        load()
        create(master.toCharArray())
    }

    @Test fun depthFiveAllowedSixRefused() = runTest {
        val s = open()
        var parent: String? = null
        repeat(VaultLimits.FOLDER_DEPTH_MAX) { i -> parent = s.createFolder("Nivel ${i + 1}", parent)!!.id }
        assertEquals(5, folderPath(s.data().folders, parent!!).size)
        val e = assertThrows(VaultLimitExceeded::class.java) { kotlinx.coroutines.runBlocking { s.createFolder("Nivel 6", parent) } }
        assertEquals(FOLDERS_TOO_DEEP, e.message)
        assertEquals(5, s.data().folders.size)
        // Moving a two-level branch under level 4 would make 6 levels.
        val a = s.createFolder("Rama", null)!!
        s.createFolder("Hoja", a.id)
        val level4 = folderPath(s.data().folders, parent!!)[3]
        assertThrows(VaultLimitExceeded::class.java) { kotlinx.coroutines.runBlocking { s.moveFolder(a.id, level4.id) } }
        // Under level 3 it fits (3 + 2 = 5).
        val level3 = folderPath(s.data().folders, parent!!)[2]
        assertTrue(s.moveFolder(a.id, level3.id))
        assertEquals(level3.id, s.data().folders.single { it.id == a.id }.parentId)
    }

    @Test fun cycleRefused() = runTest {
        val s = open()
        val a = s.createFolder("A", null)!!
        val b = s.createFolder("B", a.id)!!
        val c = s.createFolder("C", b.id)!!
        val intoChild = assertThrows(VaultLimitExceeded::class.java) { kotlinx.coroutines.runBlocking { s.moveFolder(a.id, c.id) } }
        assertEquals(FOLDER_CYCLE, intoChild.message)
        assertThrows(VaultLimitExceeded::class.java) { kotlinx.coroutines.runBlocking { s.moveFolder(a.id, a.id) } }
        assertEquals(setOf(b.id, c.id), descendants(s.data().folders, a.id))
        // A hostile copy with a cycle: the helpers stop, and the store refuses to save it (checkStructure).
        val looped = listOf(Folder("x", "X", parentId = "y"), Folder("y", "Y", parentId = "x"))
        assertEquals(setOf("y"), descendants(looped, "x"))
        assertTrue(folderPath(looped, "x").size <= 2)
        assertThrows(VaultLimitExceeded::class.java) { VaultData(folders = looped).checkStructure() }
        // A move to the top always works.
        assertTrue(s.moveFolder(c.id, null))
        assertNull(s.data().folders.single { it.id == c.id }.parentId)
    }

    @Test fun deleteToParentMovesEntries() = runTest {
        val s = open()
        val a = s.createFolder("Trabajo", null)!!
        val b = s.createFolder("Proyectos", a.id)!!
        val c = s.createFolder("Viejos", b.id)!!
        assertTrue(s.upsert(note("1")))
        assertTrue(s.upsert(note("2")))
        assertEquals(2, s.moveEntries(setOf("1", "2"), b.id))
        s.saveView(SavedView("v", "Proyectos", EntryFilter(folderId = b.id)))
        assertTrue(s.deleteFolder(b.id, FolderDeletion.PARENT))
        val d = s.data()
        assertEquals(listOf(a.id, c.id), d.folders.map { it.id })
        assertEquals(a.id, d.folders.single { it.id == c.id }.parentId) // Its subfolder goes up too.
        assertEquals(listOf(a.id, a.id), d.live.map { it.folderId })
        assertTrue(d.trash.isEmpty())
        // A view of the folder now looks at the folder that took its place.
        assertEquals(a.id, d.views.single().filter.folderId)
        assertFalse(s.deleteFolder("no-existe", FolderDeletion.PARENT))
    }

    @Test fun deleteToTrashTrashesEntries() = runTest {
        val s = open()
        val a = s.createFolder("Casa", null)!!
        val b = s.createFolder("Facturas", a.id)!!
        assertTrue(s.upsert(note("1")))
        assertTrue(s.upsert(note("2")))
        assertTrue(s.upsert(note("3")))
        s.moveEntries(setOf("1"), a.id)
        s.moveEntries(setOf("2"), b.id)
        clock += 1000
        assertTrue(s.deleteFolder(a.id, FolderDeletion.TRASH))
        val d = s.data()
        assertTrue(d.folders.isEmpty()) // The folder and its subfolders.
        assertEquals(listOf("3"), d.live.map { it.id })
        assertEquals(setOf("1", "2"), d.trash.map { it.id }.toSet())
        assertTrue(d.trash.all { it.deletedAt == clock })
        // Restored, they come back to no folder: theirs is gone.
        assertEquals(2, s.restore(setOf("1", "2")))
        assertTrue(s.data().live.all { it.folderId == null })
    }

    @Test fun namesComparedWithCompareKey() = runTest {
        val s = open()
        val a = s.createFolder("Banco", null)!!
        for (same in listOf("banco", " BANCO ", "Ban​co", "Ｂａｎｃｏ")) {
            val e = assertThrows(OrganizeRefused::class.java) { kotlinx.coroutines.runBlocking { s.createFolder(same, null) } }
            assertEquals(FOLDER_NAME_TAKEN, e.message)
        }
        // The same name in another place is fine.
        assertNotNull(s.createFolder("Banco", a.id))
        val e = assertThrows(OrganizeRefused::class.java) { kotlinx.coroutines.runBlocking { s.createFolder("​ ", null) } }
        assertEquals(FOLDER_NAME_EMPTY, e.message)
        assertThrows(OrganizeRefused::class.java) { kotlinx.coroutines.runBlocking { s.createFolder("x".repeat(FOLDER_NAME_MAX + 1), null) } }
        // Renaming to another sibling's name is refused; to its own name in other case, allowed.
        val c = s.createFolder("Correo", null)!!
        assertThrows(OrganizeRefused::class.java) { kotlinx.coroutines.runBlocking { s.renameFolder(c.id, "BANCO") } }
        assertTrue(s.renameFolder(c.id, "CORREO"))
        assertEquals("CORREO", s.data().folders.single { it.id == c.id }.name)
        // A move next to a folder of the same name is refused.
        val inside = s.data().folders.single { it.parentId == a.id }
        assertThrows(OrganizeRefused::class.java) { kotlinx.coroutines.runBlocking { s.moveFolder(inside.id, null) } }
    }

    @Test fun limit500() = runTest {
        val s = open()
        val many = (1..VaultLimits.FOLDERS_MAX).map { Folder("f$it", "Carpeta $it", order = it) }
        val full = VaultData(folders = many)
        full.checkStructure()
        val e = assertThrows(VaultLimitExceeded::class.java) { full.withNewFolder("Una más", null) }
        assertEquals(TOO_MANY_FOLDERS, e.message)
        // Through the store too: 500 fit, the 501st does not.
        repeat(VaultLimits.FOLDERS_MAX) { s.createFolder("C$it", null) }
        assertEquals(500, s.data().folders.size)
        assertThrows(VaultLimitExceeded::class.java) { kotlinx.coroutines.runBlocking { s.createFolder("C500", null) } }
        assertEquals(500, s.data().folders.size)
    }

    @Test fun pathAndTreeOrder() {
        val folders = listOf(
            Folder("b", "Banco", order = 2), Folder("a", "Archivo", order = 1),
            Folder("a2", "Zeta", parentId = "a", order = 2), Folder("a1", "Alfa", parentId = "a", order = 1),
            Folder("orphan", "Huérfana", parentId = "falta", order = 3),
        )
        assertEquals(listOf("a", "a1"), folderPath(folders, "a1").map { it.id })
        assertEquals(emptyList<Folder>(), folderPath(folders, "no-existe"))
        // Depth first, by order; a folder whose parent is missing counts as a top one.
        assertEquals(listOf("a" to 0, "a1" to 1, "a2" to 1, "b" to 0, "orphan" to 0), folderTree(folders).map { it.first.id to it.second })
    }

    @Test fun moveEntriesKeepsHistoryAndDates() = runTest {
        val s = open()
        val a = s.createFolder("A", null)!!
        assertTrue(s.upsert(note("1")))
        val before = s.data().live.single()
        assertEquals(1, s.moveEntries(setOf("1", "no-existe"), a.id))
        val after = s.data().live.single()
        assertEquals(a.id, after.folderId)
        assertEquals(before.updatedAt, after.updatedAt)
        assertEquals(before.history, after.history) // A move is not a new version.
        assertEquals(0, s.moveEntries(setOf("1"), a.id)) // Already there: nothing saved.
        assertThrows(OrganizeRefused::class.java) { kotlinx.coroutines.runBlocking { s.moveEntries(setOf("1"), "no-existe") } }
        // An edit from the editor (which knows nothing of folders) keeps the entry in its folder.
        assertTrue(s.upsert(after.copy(title = "Otra", folderId = null)))
        assertEquals(a.id, s.data().live.single().folderId)
        // A version restored keeps the folder the entry is in now.
        val version = s.data().live.single().history.first()
        assertTrue(s.moveEntries(setOf("1"), null) == 1)
        assertTrue(s.restoreVersion("1", version.savedAt, 0))
        assertNull(s.data().live.single().folderId)
    }

    /** Review I-1: a copy made elsewhere with a folder id twice is damaged, and nothing walks every path of it. */
    @Test fun duplicateFolderIdsRefused() {
        val twice = VaultData(folders = listOf(Folder("a", "A"), Folder("a", "Otra A")))
        assertEquals(ORGANIZE_DAMAGED, assertThrows(VaultLimitExceeded::class.java) { twice.checkStructure() }.message)
        assertEquals(COPY_DAMAGED, assertThrows(app.seguridad.crypto.CryptoFailure::class.java) { checkCopyStructure(twice) }.message)
        // 100 folders «A» at the top, 100 «B» in «A»… down to «E»: 500 and 5 levels, the shape of the review.
        val hostile = listOf("A", "B", "C", "D", "E").flatMapIndexed { i, id ->
            (0 until 100).map { Folder(id, "$id$it", parentId = if (i == 0) null else listOf("A", "B", "C", "D")[i - 1]) }
        }
        assertEquals(500, hostile.size)
        val t0 = System.nanoTime()
        assertEquals(5, branchHeight(hostile, "A"))
        assertTrue("${(System.nanoTime() - t0) / 1_000_000} ms", System.nanoTime() - t0 < 100_000_000)
        assertThrows(app.seguridad.crypto.CryptoFailure::class.java) { checkCopyStructure(VaultData(folders = hostile)) }
        // A cycle does not loop either.
        assertEquals(2, branchHeight(listOf(Folder("x", "X", parentId = "y"), Folder("y", "Y", parentId = "x")), "x"))
    }

    /** Review I-1: two views with the same id (or an id «manage») only come from a copy made elsewhere: damaged. */
    @Test fun duplicateViewIdsRefused() {
        val twice = VaultData(views = listOf(SavedView("manage", "Una", EntryFilter()), SavedView("manage", "Otra", EntryFilter())))
        assertEquals(ORGANIZE_DAMAGED, assertThrows(VaultLimitExceeded::class.java) { twice.checkStructure() }.message)
        assertEquals(COPY_DAMAGED, assertThrows(app.seguridad.crypto.CryptoFailure::class.java) { checkCopyStructure(twice) }.message)
        // One view called «manage» is fine (its key in the list has its own prefix, review C-1).
        VaultData(views = listOf(SavedView("manage", "Una", EntryFilter()))).checkStructure()
    }

    /** Review I-1, through the store: importing a copy whose folders repeat an id changes nothing. */
    @Test fun hostileCopyWithRepeatedIdsIsNotImported() = runTest {
        val maker = open()
        val file = VaultCrypto.decodeFile(maker.exportText()!!)
        val key = VaultCrypto.unlock(file, master.toCharArray())
        val hostile = VaultData(
            entries = listOf(note("1")),
            folders = listOf(Folder("a", "A"), Folder("a", "B", parentId = "a")),
            views = listOf(SavedView("manage", "V", EntryFilter()), SavedView("manage", "W", EntryFilter())),
        )
        val text = VaultCrypto.encodeFile(VaultCrypto.write(file, key, hostile))
        val other = VaultStore(
            File(tmp.newFolder("otra"), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
            elapsed = { clock }, clockHighWater = { clock }, trashClockMark = { clock },
        ).apply {
            load()
            create(master.toCharArray())
            upsert(note("mía"))
        }
        val e = assertThrows(app.seguridad.crypto.CryptoFailure::class.java) { kotlinx.coroutines.runBlocking { other.importReplace(text, master.toCharArray()) } }
        assertEquals(COPY_DAMAGED, e.message)
        assertEquals(listOf("mía"), other.data().live.map { it.id })
    }
}
