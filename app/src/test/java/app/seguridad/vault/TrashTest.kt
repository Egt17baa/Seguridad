package app.seguridad.vault

import app.seguridad.AppContainer
import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** 2.0, tarea 3: la papelera (30 días), su vaciado al abrir la bóveda, la regla del reloj y «Deshacer» (focos 3 y 5). */
class TrashTest {
    @get:Rule val tmp = TemporaryFolder()

    private val day = 24 * 60 * 60_000L
    private val t0 = 1_800_000_000_000L
    private var clock = t0

    /** The mark of the trash's clock the store reads before an unlock (the A11 mark of the vault, as the ruling reads it). */
    private var mark = t0
    private val master = "maestra-de-prueba-papelera"

    private fun store(dir: File = tmp.root) = VaultStore(
        File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
        elapsed = { clock }, clockHighWater = { mark }, trashClockMark = { mark },
    )

    private fun note(id: String, title: String = "Nota $id") = VaultEntry(id, EntryType.NOTE, title, notes = "texto $id", createdAt = 0, updatedAt = 0)
    private fun VaultStore.open() = state.value as VaultState.Unlocked
    private fun VaultStore.attDir() = File(tmp.root, "att")

    private suspend fun withTwo(): VaultStore = store().apply {
        load()
        create(master.toCharArray())
        assertTrue(upsert(note("a", "Correo")))
        assertTrue(upsert(note("b", "Banco")))
    }

    @Test fun trashedLeaveEntries() = runTest {
        val s = withTwo()
        assertEquals(1, s.moveToTrash(setOf("a", "no-existe")))
        assertEquals(listOf("b"), s.open().entries.map { it.id })
        assertEquals(listOf("a"), s.open().trash.map { it.id })
        assertEquals(clock, s.open().trash.single().deletedAt)
        // Twice changes nothing.
        assertEquals(0, s.moveToTrash(setOf("a")))
        // It stays in the trash after locking: it is in the vault file.
        s.lock()
        mark = clock
        s.unlock(master.toCharArray())
        assertEquals(listOf("b"), s.open().entries.map { it.id })
        assertEquals(listOf("a"), s.open().trash.map { it.id })
        // delete(id) sends it to the trash too.
        assertTrue(s.delete("b"))
        assertEquals(emptyList<String>(), s.open().entries.map { it.id })
        assertEquals(setOf("a", "b"), s.open().trash.map { it.id }.toSet())
    }

    @Test fun restoreBringsBack() = runTest {
        val s = withTwo()
        s.moveToTrash(setOf("a", "b"))
        assertEquals(0, s.restore(setOf("no-existe")))
        assertEquals(1, s.restore(setOf("a")))
        val back = s.open().entries.single()
        assertEquals("a", back.id)
        assertNull(back.deletedAt)
        assertEquals(listOf("b"), s.open().trash.map { it.id })
        // A trashed id is not saved again over its trashed entry (a screen left open on it).
        assertFalse(s.upsert(note("b", "Otra")))
        assertEquals(listOf("b"), s.open().trash.map { it.id })
        assertEquals(1, s.open().entries.size)
        assertTrue(s.addAll(listOf(note("b", "Otra"), note("c"))))
        assertEquals(setOf("a", "c"), s.open().entries.map { it.id }.toSet())
        assertEquals("Banco", s.open().trash.single().title)
    }

    @Test fun personalEntriesSkipTheTrash() = runTest {
        val s = store().apply { load(); create(master.toCharArray()) }
        val me = VaultEntry("me", EntryType.IDENTITY, "Yo", identitySeed = "AAAA", createdAt = 0, updatedAt = 0)
        assertTrue(s.upsert(me))
        // Never to the trash: its screen deletes it for good, after confirmations that say so.
        assertEquals(0, s.moveToTrash(setOf("me")))
        assertTrue(s.delete("me"))
        assertTrue(s.open().data.entries.isEmpty())
        assertFalse(s.delete("me"))
    }

    @Test fun restoreNeverMakesASecondIdentity() = runTest {
        // A vault file (as a copy made elsewhere could bring it) with an identity in the trash.
        val s = store().apply { load(); create(master.toCharArray()) }
        s.lock()
        val f = File(tmp.root, "vault.bin")
        val file = VaultCrypto.decodeFile(f.readText())
        val dek = VaultCrypto.unlock(file, master.toCharArray())
        val old = VaultEntry("me", EntryType.IDENTITY, "Yo", identitySeed = "AAAA", createdAt = 0, updatedAt = 0, deletedAt = t0)
        f.writeText(VaultCrypto.encodeFile(VaultCrypto.write(file, dek, VaultCrypto.read(file, dek).copy(entries = listOf(old)))))
        val again = store().apply { load(); unlock(master.toCharArray()) }
        assertTrue(again.upsert(old.copy(id = "me2", deletedAt = null)))
        assertEquals(0, again.restore(setOf("me")))
        assertEquals(listOf("me2"), again.open().entries.map { it.id })
        assertEquals(listOf("me"), again.open().trash.map { it.id })
        // Without another one, it comes back.
        assertTrue(again.delete("me2"))
        assertEquals(1, again.restore(setOf("me")))
    }

    @Test fun deleteForeverRemovesAttachments() = runTest {
        val s = withTwo()
        assertTrue(s.addAttachment("a", ByteArrayInputStream(ByteArray(64) { 7 }), "foto.jpg"))
        val ref = s.open().entries.single { it.id == "a" }.attachments.single()
        val att = File(s.attDir(), "${ref.id}.bin")
        assertTrue(att.exists())
        s.moveToTrash(setOf("a"))
        assertTrue("The trash keeps its attachments", att.exists())
        // Only what is in the trash is deleted for good.
        assertEquals(0, s.deleteForever(setOf("b")))
        assertEquals(listOf("b"), s.open().entries.map { it.id })
        assertEquals(1, s.deleteForever(setOf("a")))
        assertFalse(att.exists())
        assertTrue(s.open().trash.isEmpty())
        // «Vaciar papelera».
        s.moveToTrash(setOf("b"))
        assertEquals(1, s.emptyTrash())
        assertEquals(0, s.emptyTrash())
        assertTrue(s.open().data.entries.isEmpty())
    }

    @Test fun purgeAfter30Days() = runTest {
        // The pure rule.
        val now = t0 + 100 * day
        val data = VaultData(
            entries = listOf(
                note("viejo").copy(deletedAt = now - 31 * day),
                note("justo").copy(deletedAt = now - 30 * day),
                note("nuevo").copy(deletedAt = now - 29 * day),
                note("vivo"),
                note("futuro").copy(deletedAt = now + day), // A clock that went back: counted from now (review M-6).
            ),
            lastUnlockAt = now - day,
        )
        val purged = data.purgedTrash(now, clockMark = now - 10 * day)
        assertEquals(listOf("justo", "nuevo", "vivo", "futuro"), purged.entries.map { it.id })
        assertEquals(now, purged.entries.single { it.id == "futuro" }.deletedAt)
        assertFalse(purged.trashPurgeSkipped)
        assertEquals(now, purged.lastUnlockAt)
        // Only one of the two signs of a jump: still purged.
        assertEquals(purged.entries, data.copy(lastUnlockAt = 0).purgedTrash(now, clockMark = now - day).entries)

        // In the store, at the unlock with the master password.
        val s = withTwo()
        s.moveToTrash(setOf("a"))
        clock += 2 * day
        s.moveToTrash(setOf("b"))
        s.lock()
        clock = t0 + 31 * day
        mark = clock - 60 * 60_000L // The app was used an hour ago: no jump.
        s.unlock(master.toCharArray())
        assertEquals(listOf("b"), s.open().trash.map { it.id })
        assertEquals(clock, s.open().data.lastUnlockAt)
        // Written to disk: a new store reads the trash without «a».
        s.lock()
        clock += 60_000L
        mark = clock
        val again = store().apply { load(); unlock(master.toCharArray()) }
        assertEquals(listOf("b"), again.open().trash.map { it.id })
    }

    @Test fun purgeAtFingerprintUnlock() = runTest {
        val s = withTwo()
        val key = s.dataKeyCopy()!!
        s.moveToTrash(setOf("a"))
        s.lock()
        mark = clock
        s.unlock(master.toCharArray())
        s.lock()
        // Within the 7 days of the fingerprint, used every day: no jump.
        clock += 6 * day
        mark = clock - day
        assertEquals("Locked: nothing", 0, s.moveToTrash(setOf("b")))
        s.unlockWithKey(key.copyOf())
        assertEquals(listOf("a"), s.open().trash.map { it.id })
        assertEquals(clock, s.open().data.lastUnlockAt)
        s.lock()
        clock += 25 * day // 31 days after the deletion, but past the 7 days of the fingerprint: the master.
        mark = clock - day
        s.unlock(master.toCharArray())
        assertTrue(s.open().trash.isEmpty())
    }

    @Test fun clockJumpDoesNotEmptyTrash() = runTest {
        val s = withTwo()
        s.lock()
        s.unlock(master.toCharArray()) // lastUnlockAt = t0
        s.moveToTrash(setOf("a"))
        s.lock()
        // The clock jumps 400 days ahead; the vault's mark from before this unlock is still t0.
        mark = t0
        clock = t0 + 400 * day
        s.unlock(master.toCharArray())
        assertEquals("A jump of the clock never empties the trash", listOf("a"), s.open().trash.map { it.id })
        // The unlock is recorded all the same.
        assertEquals(clock, s.open().data.lastUnlockAt)
        // The pure rule: both signs of a jump (more than 2 days since the last unlock and since the mark).
        val data = VaultData(entries = listOf(note("x").copy(deletedAt = t0)), lastUnlockAt = t0)
        assertEquals(1, data.purgedTrash(t0 + 400 * day, clockMark = t0).trash.size)
        assertEquals(1, data.purgedTrash(t0 + 2 * day + 31 * day, clockMark = t0 + 3 * day).trash.size)
        assertEquals(0, data.purgedTrash(t0 + 31 * day, clockMark = t0 + 30 * day).trash.size)
    }

    @Test fun backupKeepsTrash() = runTest {
        val s = withTwo()
        assertTrue(s.addAttachment("a", ByteArrayInputStream(ByteArray(32) { 3 }), "doc.pdf"))
        s.moveToTrash(setOf("a"))
        val copy = ByteArrayOutputStream().also { assertTrue(s.exportBackup(it)) }.toByteArray()
        val dir = tmp.newFolder("otro")
        val restored = store(dir).apply { load() }
        restored.importReplace(ByteArrayInputStream(copy), master.toCharArray())
        val open = restored.open()
        assertEquals(listOf("b"), open.entries.map { it.id })
        val trashed = open.trash.single()
        assertEquals("a", trashed.id)
        assertEquals(t0, trashed.deletedAt)
        assertTrue(File(dir, "att/${trashed.attachments.single().id}.bin").exists())
        // And it can be restored from there.
        assertEquals(1, restored.restore(setOf("a")))
    }

    @Test fun undoWithin5s() = runTest {
        val s = withTwo()
        val undo = UndoTrash(s, elapsed = { clock })
        assertEquals(1, undo.moveToTrash(setOf("a")))
        assertTrue(undo.canUndo())
        clock += UNDO_TRASH_MS - 1
        assertEquals(1, undo.undo())
        assertEquals(setOf("a", "b"), s.open().entries.map { it.id }.toSet())
        assertFalse(undo.canUndo())
        assertEquals(0, undo.undo())
        // After 5 s it is forgotten.
        assertEquals(2, undo.moveToTrash(setOf("a", "b")))
        clock += UNDO_TRASH_MS
        assertFalse(undo.canUndo())
        assertEquals(0, undo.undo())
        assertEquals(2, s.open().trash.size)
        // Only the last move is undone.
        s.restore(setOf("a", "b"))
        undo.moveToTrash(setOf("a"))
        undo.moveToTrash(setOf("b"))
        assertEquals(1, undo.undo())
        assertEquals(listOf("a"), s.open().trash.map { it.id })
    }

    /**
     * 2.0, task 6: two moves of one entry each within 5 s give the same count (1 and 1); each move still has its own
     * mark ([UndoTrash.moves]), so the list starts the 5 s of «Deshacer» again for the second one.
     */
    @Test fun eachMoveHasItsOwnMark() = runTest {
        val s = withTwo()
        val undo = UndoTrash(s, elapsed = { clock })
        val before = undo.moves.value
        undo.moveToTrash(setOf("a"))
        val first = undo.moves.value
        clock += 1_000
        undo.moveToTrash(setOf("b"))
        assertEquals(1, undo.offer.value)
        assertTrue(before != first && first != undo.moves.value)
        // Nothing moved: no new mark.
        val last = undo.moves.value
        assertEquals(0, undo.moveToTrash(setOf("no-existe")))
        assertEquals(last, undo.moves.value)
        // The second has its own 5 s.
        clock += UNDO_TRASH_MS - 1
        assertTrue(undo.canUndo())
    }

    @Test fun undoForgottenOnLock() = runTest {
        val s = withTwo()
        val undo = UndoTrash(s, elapsed = { clock })
        undo.moveToTrash(setOf("a"))
        s.lock()
        assertFalse(undo.canUndo())
        mark = clock
        s.unlock(master.toCharArray())
        assertFalse(undo.canUndo())
        assertEquals(0, undo.undo())
        assertEquals(listOf("a"), s.open().trash.map { it.id })
    }

    @Test fun undoFollowsTheActiveVault() = runTest {
        val c = AppContainer(tmp.newFolder("app"), tmp.newFolder("cache"), appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-de-prueba-a".toCharArray(), name = "Uno")
        val a = c.vault
        assertTrue(a.upsert(note("a")))
        assertEquals(1, c.undoTrash.moveToTrash(setOf("a")))
        assertTrue(c.undoTrash.canUndo())
        val b = c.createVault("Dos", "maestra-de-prueba-b".toCharArray())
        assertFalse("Another vault has nothing to undo", c.undoTrash.canUndo())
        assertEquals(0, c.undoTrash.undo())
        c.switchTo(a.id)
        c.vault.unlock("maestra-de-prueba-a".toCharArray())
        assertFalse("Switching locks, and a lock forgets", c.undoTrash.canUndo())
        assertEquals(0, c.undoTrash.undo())
        assertEquals(listOf("a"), c.vault.open().trash.map { it.id })
        assertTrue(b != a.id)
    }

    @Test fun appStartIsNotATrashWitness() = runTest {
        val c = AppContainer(tmp.newFolder("app2"), tmp.newFolder("cache2"), appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        val now = System.currentTimeMillis()
        c.appStarted(now)
        val id = c.activeVaultId
        // The A11 mark rises at the start, as before; the trash's clock does not take it as a use.
        assertEquals(now, c.clockMark(id))
        assertEquals(0L, c.trashClockMark(id))
        c.rememberClock(now + 1)
        assertEquals(now + 1, c.trashClockMark(id))
    }

    /** Abogado Digital, O-2: on view, the copies keep what they hold and the trash empties itself after 30 days. */
    @Test fun trashTextsSayWhatStays() {
        for (t in listOf(TRASH_DELETE_FOREVER_TEXT, trashEmptyText(1), trashEmptyText(12), HISTORY_CLEAR_TEXT)) {
            assertTrue(t, t.contains(COPIES_KEEP_IT))
        }
        assertTrue(TRASH_SHORT.contains("30 días") && TRASH_SHORT.contains("Al abrir la bóveda"))
        assertEquals("¿Mover «Correo» a la papelera?", moveToTrashTitle("Correo"))
        assertEquals("¿Mover 12 entradas a la papelera?", moveToTrashCountTitle(12))
        assertTrue(trashEmptyText(1).startsWith("Se quita 1 entrada "))
        assertTrue(trashEmptyText(3).startsWith("Se quitan 3 entradas "))
        val all = listOf(TRASH_SHORT, COPIES_KEEP_IT, TRASH_MORE, TRASH_DELETE_FOREVER_TEXT, trashEmptyText(2), HISTORY_SHORT, HISTORY_MORE, HISTORY_CLEAR_TEXT, MOVE_TO_TRASH_TEXT)
        for (t in all) {
            assertFalse(t, t.lowercase().contains("irrecuperable"))
            assertFalse(t, t.lowercase().contains("borrado seguro"))
        }
        assertEquals("Se quita en 30 días", trashLeavesIn(t0, t0))
        assertEquals("Se quita en 1 día", trashLeavesIn(t0, t0 + 29 * day))
        assertEquals("Se quita al abrir la bóveda", trashLeavesIn(t0, t0 + 31 * day))
    }

    /**
     * Ruling T3, I-1: the purge is skipped at most once in a row. Unlocks 3 days apart, each in a new process (a new
     * AppContainer: no use seen yet), each one a «jump» by the rule: the first skips, the next one purges.
     */
    @Test fun spacedUnlocksStillEmptyTheTrash() = runTest {
        val files = tmp.newFolder("movil")
        val cache = tmp.newFolder("cache-movil")
        var now = t0
        fun process() = AppContainer(files, cache, appVersion = "test", appScope = backgroundScope, vaultNow = { now })
        val first = process()
        first.settings.load()
        first.vault.load()
        first.vault.create(master.toCharArray())
        assertTrue(first.vault.upsert(note("viejo")))
        assertTrue(first.vault.upsert(note("nuevo")))
        now = t0 - 40 * day
        first.vault.moveToTrash(setOf("viejo"))
        now = t0
        first.vault.moveToTrash(setOf("nuevo"))
        first.vault.lock()
        val seen = ArrayList<List<String>>()
        // Unlocks at t0 + 3, + 6 (a skip, then a purge), then + 31, + 34 (again: a skip, then a purge).
        for (at in listOf(3, 6, 31, 34)) {
            now = t0 + at * day
            val c = process()
            c.settings.load()
            c.vault.load()
            c.vault.unlock(master.toCharArray())
            seen += c.vault.open().trash.map { it.id }.sorted()
            c.vault.lock()
        }
        assertEquals(listOf(listOf("nuevo", "viejo"), listOf("nuevo"), listOf("nuevo"), emptyList()), seen)
        // Pure: never two skips in a row.
        var d = VaultData(entries = listOf(note("x").copy(deletedAt = t0)), lastUnlockAt = t0)
        val skipped = (1..6).map { i -> d.purgedTrash(t0 + i * 3 * day, clockMark = 0).also { d = it.copy(entries = d.entries) }.trashPurgeSkipped }
        assertEquals(listOf(true, false, true, false, true, false), skipped)
    }

    /** Review M-1: a move, a switch to another vault and back: no «Deshacer» left over. */
    @Test fun undoOfferGoesWithTheLock() = runTest {
        val c = AppContainer(tmp.newFolder("app3"), tmp.newFolder("cache3"), appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-de-prueba-a".toCharArray(), name = "Uno")
        val a = c.vault
        assertTrue(a.upsert(note("a")))
        assertEquals(1, c.undoTrash.moveToTrash(setOf("a")))
        val undoA = c.undoTrash
        assertEquals(1, undoA.offer.value)
        c.createVault("Dos", "maestra-de-prueba-b".toCharArray())
        testScheduler.runCurrent()
        assertEquals(0, undoA.offer.value)
        c.switchTo(a.id)
        c.vault.unlock("maestra-de-prueba-a".toCharArray())
        testScheduler.runCurrent()
        assertEquals(0, c.undoTrash.offer.value)
    }

    /** Review M-3: the editor on an entry that is in the trash says so, not «se bloqueó». */
    @Test fun saveRefusedSaysWhy() = runTest {
        val s = withTwo()
        s.moveToTrash(setOf("a"))
        assertFalse(s.upsert(note("a", "Otra")))
        assertEquals(ENTRY_IN_TRASH, saveRefusedText(s.state.value, "a"))
        assertEquals(SAVE_LOCKED, saveRefusedText(s.state.value, "b"))
        s.lock()
        assertEquals(SAVE_LOCKED, saveRefusedText(s.state.value, "a"))
    }
}
