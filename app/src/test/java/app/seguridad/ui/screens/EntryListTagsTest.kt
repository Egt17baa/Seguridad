package app.seguridad.ui.screens

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import app.seguridad.crypto.KdfParams
import app.seguridad.vault.EntryFilter
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Round 3 of the fixes of the attack, RT-28: [EntryListViewModel] works out, off the main thread, the tags of the filter
 * no entry has any more ([ListResult.goneFilterTags]), so the list's effect only takes them away: with a view of every
 * tag nothing is gone; after 3 tags are taken out of the vault, exactly those 3.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntryListTagsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun theTagsThatAreGoneAreWorkedOutOffTheMainThread() = runBlocking {
        val store = VaultStore(File(tmp.newFolder(), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { KdfParams(1024, 1, 1) })
        store.load()
        store.create("maestra-de-prueba-de-las-etiquetas".toCharArray())
        val tags = List(30) { "t$it" }
        tags.chunked(10).forEachIndexed { i, chunk -> store.upsert(VaultEntry("e$i", EntryType.NOTE, "Nota $i", createdAt = 1, updatedAt = 1, tags = chunk)) }
        val list = EntryListViewModel(store, totpOnly = false)
        try {
            list.filter = EntryFilter(tags = tags.toSet())
            Snapshot.sendApplyNotifications()
            val whole = withTimeout(5_000) { list.result.first { it.data != null && it.tags.size == 30 } }
            assertEquals("the whole view: nothing gone", emptySet<String>(), whole.goneFilterTags)
            // Three tags taken out of the vault: the filter loses exactly those three.
            store.upsert(VaultEntry("e0", EntryType.NOTE, "Nota 0", createdAt = 1, updatedAt = 2, tags = tags.subList(3, 10)))
            val after = withTimeout(5_000) { list.result.first { it.tags.size == 27 } }
            assertEquals(setOf("t0", "t1", "t2"), after.goneFilterTags)
        } finally {
            list.viewModelScope.cancel()
        }
    }
}
