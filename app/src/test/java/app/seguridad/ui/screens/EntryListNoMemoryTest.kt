package app.seguridad.ui.screens

import androidx.lifecycle.viewModelScope
import app.seguridad.crypto.KdfParams
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections

/**
 * RT-25, part a (ruling of round 2 of the fixes of the attack): running out of memory while [EntryListViewModel] works the
 * list out (the search index, «Salud»'s marks, the tags) never closes the app: the result says [ListResult.noMemory] with
 * nothing of the vault in it, the selection is empty and the vault stays open; the next change of the data works it out
 * again. The error is made by hand through [beforeListCompute]: no test runs out of memory for real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntryListNoMemoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val uncaught = Collections.synchronizedList(mutableListOf<Throwable>())
    private var handler: Thread.UncaughtExceptionHandler? = null

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        handler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
    }

    @After fun tearDown() {
        beforeListCompute = {}
        Thread.setDefaultUncaughtExceptionHandler(handler)
        Dispatchers.resetMain()
    }

    private fun note(id: String) = VaultEntry(id, EntryType.NOTE, "Nota $id", notes = "n $id", createdAt = 0, updatedAt = 0)

    private fun check(totpOnly: Boolean) = runBlocking {
        val store = VaultStore(File(tmp.newFolder(), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { KdfParams(1024, 1, 1) })
        store.load()
        store.create("maestra-de-prueba-de-la-lista".toCharArray())
        store.upsert(note("1"))
        beforeListCompute = { throw OutOfMemoryError() }
        val list = EntryListViewModel(store, totpOnly)
        try {
            val r = withTimeout(5_000) { list.result.first { it.noMemory } }
            assertEquals("nothing of the vault is held", null, r.data)
            assertTrue(r.shown.isEmpty() && r.tags.isEmpty() && r.total == 0)
            assertTrue("the selection is empty", list.selection.ids.value.isEmpty())
            assertTrue("the vault stays open", store.state.value is VaultState.Unlocked)
            assertTrue("nothing thrown: $uncaught", uncaught.isEmpty())
            // Without the seam, the next change of the data works the list out again and shows it.
            beforeListCompute = {}
            store.upsert(note("2"))
            val again = withTimeout(5_000) { list.result.first { !it.noMemory && it.total == 2 } }
            if (!totpOnly) assertEquals(setOf("1", "2"), again.shown.mapTo(HashSet()) { it.id })
            assertTrue(again.data != null)
            assertTrue("nothing thrown: $uncaught", uncaught.isEmpty())
        } finally {
            // Cancelled and waited for before resetMain: no computation of the list is left to resume on Dispatchers.Main.
            list.viewModelScope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test fun theVaultListOutOfMemoryShowsTheTextAndStaysOpen() = check(totpOnly = false)

    @Test fun theCodesTabOutOfMemoryShowsTheTextAndStaysOpen() = check(totpOnly = true)

    /** What is painted: out of memory first, never the empty list nor its actions; the rest as before. */
    @Test fun whatTheListPaints() {
        val data = app.seguridad.vault.VaultData(entries = listOf(note("1")))
        assertEquals(ListShows.OUT_OF_MEMORY, listShows(ListResult.NO_MEMORY))
        // Even with data and nothing shown (which would be «La bóveda está vacía»), no memory wins.
        assertEquals(ListShows.OUT_OF_MEMORY, listShows(ListResult(data, emptyList(), emptyList(), emptyList(), 0, emptyMap(), emptyList(), noMemory = true)))
        assertEquals(ListShows.LOADING, listShows(ListResult.EMPTY))
        assertEquals(ListShows.EMPTY, listShows(ListResult(data, emptyList(), emptyList(), emptyList(), 0, emptyMap(), emptyList())))
        assertEquals(ListShows.NO_MATCH, listShows(ListResult(data, emptyList(), emptyList(), emptyList(), 1, emptyMap(), emptyList())))
        assertEquals(ListShows.ROWS, listShows(ListResult(data, data.entries, emptyList(), emptyList(), 1, emptyMap(), emptyList())))
    }

    /**
     * The anchor: the list and «Códigos» (the same [EntryList]) paint [app.seguridad.vault.LIST_NO_MEMORY] and return
     * before anything else: the filter of tags is not dropped, no tools, no «Todas (0)», no rows, no empty list.
     */
    @Test fun theListShowsTheTextBeforeAnythingElse() {
        val src = File("src/main/java/app/seguridad/ui/screens/VaultList.kt").takeIf { it.exists() }
            ?: File("../app/src/main/java/app/seguridad/ui/screens/VaultList.kt")
        val code = src.readText()
        val list = code.indexOf("internal fun EntryList(")
        val at = code.indexOf("if (listShows(result) == ListShows.OUT_OF_MEMORY) {", list)
        assertTrue("the branch is in EntryList", list > 0 && at > list)
        val body = code.substring(at, code.indexOf("}", at))
        assertTrue(body, "Text(LIST_NO_MEMORY" in body && "return" in body)
        for (later in listOf("LaunchedEffect(result)", "ListTools(", "QuickFilters(", "LazyColumn(state = listState", "EmptyVaultActions(")) {
            assertTrue("before $later", at < code.indexOf(later, list))
        }
        assertTrue("the empty list follows listShows", "if (shows == ListShows.EMPTY || shows == ListShows.NO_MATCH)" in code)
    }
}
