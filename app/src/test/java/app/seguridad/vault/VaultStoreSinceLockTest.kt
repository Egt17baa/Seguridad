package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.coroutines.CoroutineContext

/**
 * 2.1.2, R-1 to R-4: what was asked for before a lock (the [VaultStore.lockEpoch] of the tap, or of the moment the upgrade's
 * dialog was shown) is never done after it, even if the vault was opened again meanwhile; and «He olvidado la contraseña»
 * is never cut half way.
 */
class VaultStoreSinceLockTest {
    @get:Rule val tmp = TemporaryFolder()

    private var clock = 1_800_000_000_000L
    private val master = "maestra-de-prueba-r4"
    private val file get() = File(tmp.root, "vault.bin")

    /** Holds the next block dispatched after [holdNext] until [release]; every other block runs at once. */
    private class OneShotGate : CoroutineDispatcher() {
        private var held: Runnable? = null
        var holdNext = false
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (holdNext && held == null) {
                holdNext = false
                held = block
            } else {
                block.run()
            }
        }
        val holding: Boolean get() = held != null
        fun release() {
            held?.also { held = null }?.run()
        }
    }

    private fun store(io: CoroutineDispatcher = Dispatchers.Unconfined, compute: CoroutineDispatcher = Dispatchers.Unconfined) = VaultStore(
        file, io, compute, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
        elapsed = { clock }, clockHighWater = { clock }, trashClockMark = { clock },
    )

    private fun login(id: String, tags: List<String> = emptyList()) =
        VaultEntry(id, EntryType.LOGIN, "Acceso $id", password = "clave-$id", createdAt = 0, updatedAt = 0, tags = tags)

    private fun VaultStore.data() = (state.value as VaultState.Unlocked).data

    private suspend fun open(io: CoroutineDispatcher = Dispatchers.Unconfined): VaultStore = store(io).apply {
        load()
        create(master.toCharArray())
        upsert(login("a"))
        upsert(login("b"))
    }

    /** The epoch of a tap, then a lock and the vault opened again: what that tap asked for is now old. */
    private suspend fun VaultStore.staleEpoch(): Int {
        val since = lockEpoch
        lock()
        unlock(master.toCharArray())
        assertTrue(state.value is VaultState.Unlocked)
        return since
    }

    @Test fun addAllAfterALockSavesNothing() = runTest {
        val s = open()
        val since = s.staleEpoch()
        assertFalse(s.addAll(listOf(login("c")), since))
        assertEquals(setOf("a", "b"), s.data().live.map { it.id }.toSet())
        assertTrue(s.addAll(listOf(login("c")), s.lockEpoch))
        assertEquals(setOf("a", "b", "c"), s.data().live.map { it.id }.toSet())
    }

    @Test fun moveEntriesAfterALockMovesNothing() = runTest {
        val s = open()
        val folder = s.createFolder("Banco", null)!!
        val since = s.staleEpoch()
        assertEquals(0, s.moveEntries(setOf("a"), folder.id, since))
        assertNull(s.data().live.single { it.id == "a" }.folderId)
        assertEquals(1, s.moveEntries(setOf("a"), folder.id, s.lockEpoch))
    }

    @Test fun setFavoriteAfterALockChangesNothing() = runTest {
        val s = open()
        val since = s.staleEpoch()
        assertEquals(0, s.setFavorite(setOf("a", "b"), true, since))
        assertTrue(s.data().live.none { it.favorite })
        assertEquals(2, s.setFavorite(setOf("a", "b"), true, s.lockEpoch))
    }

    @Test fun addTagAfterALockChangesNothing() = runTest {
        val s = open()
        val since = s.staleEpoch()
        assertEquals(0, s.addTag(setOf("a"), "Viajes", since))
        assertTrue(s.data().live.all { it.tags.isEmpty() })
        assertEquals(1, s.addTag(setOf("a"), "Viajes", s.lockEpoch))
    }

    @Test fun trashAfterALockMovesNothing() = runTest {
        val s = open()
        val since = s.staleEpoch()
        assertEquals(emptySet<String>(), s.trash(setOf("a"), since))
        assertTrue(s.data().trash.isEmpty())
        assertEquals(setOf("a"), s.trash(setOf("a"), s.lockEpoch))
    }

    @Test fun restoreAfterALockBringsNothingBack() = runTest {
        val s = open()
        assertEquals(setOf("a"), s.trash(setOf("a"), s.lockEpoch))
        val since = s.staleEpoch()
        assertEquals(0, s.restore(setOf("a"), since))
        assertEquals(listOf("a"), s.data().trash.map { it.id })
        assertEquals(1, s.restore(setOf("a"), s.lockEpoch))
    }

    /** «Deshacer» brings back only with the epoch of its move: a lock since (even with the vault open again) forgets it. */
    @Test fun undoAfterALockBringsNothingBack() = runTest {
        val s = open()
        val undo = UndoTrash(s, elapsed = { clock })
        assertEquals(1, undo.moveToTrash(setOf("a"), s.lockEpoch))
        s.staleEpoch()
        assertEquals(0, undo.undo())
        assertEquals(listOf("a"), s.data().trash.map { it.id })
    }

    /** A move asked for before a lock: nothing goes and nothing is offered to undo. */
    @Test fun undoMoveAfterALockMovesNothing() = runTest {
        val s = open()
        val undo = UndoTrash(s, elapsed = { clock })
        val since = s.staleEpoch()
        assertEquals(0, undo.moveToTrash(setOf("a"), since))
        assertEquals(0, undo.offer.value)
        assertTrue(s.data().trash.isEmpty())
    }

    @Test fun completeUpgradeAfterALockSavesNothing() = runTest {
        file.writeText(javaClass.getResourceAsStream("/seguridad/vault-1.4.3.json")!!.use { it.readBytes() }.decodeToString())
        val s = store().apply { load() }
        s.unlock("maestra-de-prueba-143".toCharArray())
        assertTrue(s.pendingUpgrade.value)
        val shown = s.lockEpoch // The dialog was shown.
        s.lock()
        s.unlock("maestra-de-prueba-143".toCharArray()) // Opened again: the dialog is shown again, with its own epoch.
        assertTrue(s.pendingUpgrade.value)
        assertFalse(s.completeUpgrade(shown))
        assertTrue(s.pendingUpgrade.value)
        assertFalse(file.readText().contains("\"version\":4"))
        assertTrue(s.completeUpgrade(s.lockEpoch))
        assertTrue(file.readText().contains("\"version\":4"))
    }

    @Test fun openAttachmentAfterALockDuringDecryptReturnsNull() = runTest {
        val io = OneShotGate()
        val s = open(io)
        assertTrue(s.addAttachment("a", ByteArrayInputStream(byteArrayOf(1, 2, 3)), "a.bin"))
        val ref = s.data().live.single { it.id == "a" }.attachments.single()
        assertArrayEquals(byteArrayOf(1, 2, 3), s.openAttachment("a", ref.id))
        var result: ByteArray? = byteArrayOf(9)
        io.holdNext = true
        val job = launch(start = CoroutineStart.UNDISPATCHED) { result = s.openAttachment("a", ref.id) }
        assertTrue(io.holding) // Decrypting.
        s.lock()
        s.unlock(master.toCharArray()) // Even opened again.
        io.release()
        job.join()
        assertNull(result)
        assertNotNull(s.openAttachment("a", ref.id))
    }

    @Test fun destroyIsNotCancelledHalfway() = runTest {
        val io = OneShotGate()
        val s = open(io)
        s.lock()
        io.holdNext = true
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.destroy() }
        assertTrue(io.holding)
        job.cancel() // The screen left while the vault was being deleted.
        io.release()
        job.join()
        assertFalse(file.exists())
        assertEquals(VaultState.NoVault, s.state.value)
    }

    /** A store that is no longer the active vault (a switch came first) deletes nothing. */
    @Test fun destroyOfAVaultNotActiveDeletesNothing() = runTest {
        var active = true
        val s = VaultStore(
            file, Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
            elapsed = { clock }, clockHighWater = { clock }, trashClockMark = { clock }, active = { active },
        ).apply { load(); create(master.toCharArray()); lock() }
        active = false
        assertFalse(s.destroy())
        assertTrue(file.exists())
        active = true
        assertTrue(s.destroy())
        assertFalse(file.exists())
    }
}
