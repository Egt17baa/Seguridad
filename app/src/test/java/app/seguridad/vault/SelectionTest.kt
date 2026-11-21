package app.seguridad.vault

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0, task 6: the selection of the list (design «La lista y la ficha»). What is selected is always a part of what the
 * list shows now: «Seleccionar todo» takes the visible entries only (focus 4 of the plan), a lock and a change of the
 * search or the filter empty it, and an entry that leaves the list leaves the selection.
 */
class SelectionTest {
    private val now = 1_800_000_000_000L

    private fun e(id: String, folder: String? = null, tags: List<String> = emptyList(), deleted: Long? = null, type: EntryType = EntryType.LOGIN, favorite: Boolean = false) =
        VaultEntry(id, type, "Título $id", password = "clave-$id", createdAt = 0, updatedAt = 0, folderId = folder, tags = tags, deletedAt = deleted, favorite = favorite)

    private val folders = listOf(Folder("A", "Trabajo"), Folder("A1", "Proyectos", parentId = "A"), Folder("B", "Casa"))

    private val data = VaultData(
        entries = listOf(
            e("a1", "A", tags = listOf("banco")),
            e("a2", "A"),
            e("a11", "A1", tags = listOf("banco")),
            e("b1", "B", tags = listOf("banco")),
            e("free", tags = listOf("banco")),
            e("trashA", "A", tags = listOf("banco"), deleted = now - 1_000),
            e("me", type = EntryType.IDENTITY),
        ),
        folders = folders,
    )

    /** What the list shows for [f], as [app.seguridad.ui.screens.EntryListViewModel] works it out. */
    private fun visible(f: EntryFilter): List<VaultEntry> =
        data.live.filterNot { it.type.isPersonal }.query(f, data, now, weak = { false })

    /** Focus 4 of the plan: with a folder, a view or a tag filter on, only the entries on screen are selected. */
    @Test fun selectAllTakesOnlyVisible() {
        val s = Selection()
        // A folder without its subfolders.
        s.selectAll(visible(EntryFilter(folderId = "A", includeSubfolders = false)))
        assertEquals(setOf("a1", "a2"), s.ids.value)
        // With its subfolders.
        s.selectAll(visible(EntryFilter(folderId = "A")))
        assertEquals(setOf("a1", "a2", "a11"), s.ids.value)
        // A view (a saved filter): folder A and the tag «banco».
        val view = SavedView("v", "Bancos del trabajo", EntryFilter(folderId = "A", tags = setOf("banco")))
        s.selectAll(visible(view.filter))
        assertEquals(setOf("a1", "a11"), s.ids.value)
        // Never the trash nor the identity, even if a caller hands them over with the visible ones.
        s.selectAll(visible(EntryFilter(folderId = "A")) + data.trash + data.entries.filter { it.type.isPersonal })
        assertEquals(setOf("a1", "a2", "a11"), s.ids.value)
        // The search text narrows it too.
        s.selectAll(visible(EntryFilter(text = "Título b1")))
        assertTrue("b1" in s.ids.value)
        assertFalse("a1" in s.ids.value)
    }

    @Test fun anEntryThatLeavesTheListLeavesTheSelection() {
        val s = Selection()
        s.selectAll(visible(EntryFilter()))
        assertEquals(setOf("a1", "a2", "a11", "b1", "free"), s.ids.value)
        s.retainVisible(visible(EntryFilter()).filterNot { it.id == "b1" })
        assertEquals(setOf("a1", "a2", "a11", "free"), s.ids.value)
        s.retainVisible(emptyList())
        assertTrue(s.ids.value.isEmpty())
    }

    @Test fun toggleAddsAndRemoves() {
        val s = Selection()
        s.toggle("a1")
        s.toggle("a2")
        assertEquals(setOf("a1", "a2"), s.ids.value)
        s.toggle("a1")
        assertEquals(setOf("a2"), s.ids.value)
        s.clear()
        assertTrue(s.ids.value.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun clearedOnLock() = runTest(UnconfinedTestDispatcher()) {
        val state = MutableStateFlow<VaultState>(VaultState.Unlocked(data))
        val s = Selection()
        s.followVault(state, backgroundScope)
        s.toggle("a1")
        assertEquals(setOf("a1"), s.ids.value)
        state.value = VaultState.Locked
        assertTrue(s.ids.value.isEmpty())
        // Selecting while locked keeps nothing either (a late tap of a row that is leaving).
        s.toggle("a2")
        assertTrue(s.ids.value.isEmpty())
        state.value = VaultState.Unlocked(data)
        s.toggle("a2")
        assertEquals(setOf("a2"), s.ids.value)
    }

    @Test fun clearedOnFilterChange() {
        val s = Selection()
        s.onFilter(selectionFilterKey("", EntryFilter()))
        s.selectAll(visible(EntryFilter()))
        // The same search and filter again (a recomposition, a new result of the vault): kept.
        s.onFilter(selectionFilterKey("", EntryFilter()))
        assertEquals(5, s.ids.value.size)
        // Only the order: what is shown is the same, so it stays.
        s.onFilter(selectionFilterKey("", EntryFilter(sort = SortOrder.UPDATED)))
        assertEquals(5, s.ids.value.size)
        // Another folder: emptied.
        s.onFilter(selectionFilterKey("", EntryFilter(folderId = "A")))
        assertTrue(s.ids.value.isEmpty())
        s.selectAll(visible(EntryFilter(folderId = "A")))
        // A search text: emptied.
        s.onFilter(selectionFilterKey("trab", EntryFilter(folderId = "A")))
        assertTrue(s.ids.value.isEmpty())
    }

    // The actions of the selection bar.

    @Test fun favoriteMarksOnlyLiveListedEntriesWithAVersion() {
        val (next, n) = data.withFavorite(setOf("a1", "a2", "trashA", "me", "nope"), favorite = true, now = now)!!
        assertEquals(2, n)
        val byId = next.entries.associateBy { it.id }
        assertTrue(byId.getValue("a1").favorite && byId.getValue("a2").favorite)
        assertFalse(byId.getValue("trashA").favorite)
        assertFalse(byId.getValue("me").favorite)
        // As the editor saves it: a version of how it was, and the time of the change.
        assertEquals(1, byId.getValue("a1").history.size)
        assertFalse(byId.getValue("a1").history.single().entry.favorite)
        assertEquals(now, byId.getValue("a1").updatedAt)
        // Already so: nothing to save.
        assertNull(next.withFavorite(setOf("a1", "a2"), favorite = true, now = now))
        val (off, m) = next.withFavorite(setOf("a1"), favorite = false, now = now)!!
        assertEquals(1, m)
        assertFalse(off.entries.first { it.id == "a1" }.favorite)
    }

    @Test fun tagIsNormalisedAndKeepsTheLimit() {
        val full = e("full", tags = (1..MAX_TAGS).map { "t$it" })
        val d = data.copy(entries = data.entries + full)
        val (next, n) = d.withTagAdded(setOf("a1", "a2", "full", "trashA", "me"), "  Viajes ", now)!!
        // a1 gets it, a2 gets it; «full» already has 10 tags; the trash and the identity never.
        assertEquals(2, n)
        val byId = next.entries.associateBy { it.id }
        assertEquals(listOf("banco", "viajes"), byId.getValue("a1").tags)
        assertEquals(listOf("viajes"), byId.getValue("a2").tags)
        assertEquals(MAX_TAGS, byId.getValue("full").tags.size)
        assertFalse("viajes" in byId.getValue("trashA").tags)
        assertEquals(1, byId.getValue("a2").history.size)
        // Already there: nothing to save; an empty tag: refused.
        assertNull(next.withTagAdded(setOf("a1"), "viajes", now))
        assertEquals(null, normalizedTag("  ,  "))
        assertEquals("banco", normalizedTag("Banco"))
    }

    @Test fun theConfirmationOfOneEntryNamesIt() {
        assertEquals(moveToTrashTitle("Correo"), moveSelectedToTrashTitle(1, "Correo"))
        assertEquals("¿Mover 3 entradas a la papelera?", moveSelectedToTrashTitle(3, "Correo"))
        assertEquals(MOVE_TO_TRASH_TEXT, moveToTrashText(1))
        assertEquals("Puedes restaurarlas desde la papelera durante 30 días.", moveToTrashText(3))
        assertEquals("Ya estaban ahí", movedSelected(0, "Casa"))
        assertEquals("1 entrada movida a «Casa»", movedSelected(1, "Casa"))
        assertEquals("2 entradas movidas fuera de las carpetas", movedSelected(2, null))
    }
}
