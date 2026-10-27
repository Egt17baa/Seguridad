package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VaultStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private var clock = 1_000L
    private val idle = 5 * 60_000L
    private fun store(name: String = "vault.bin") =
        VaultStore(File(tmp.root, name), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) })

    private fun entry(id: String, title: String) = VaultEntry(id, EntryType.NOTE, title, notes = "secreto", createdAt = 0, updatedAt = 0)

    @Test fun `create, add, lock and unlock again`() = runTest {
        val s = store().apply { load() }
        assertEquals(VaultState.NoVault, s.state.value)
        s.create("maestra".toCharArray())
        s.upsert(entry("b", "Banco"))
        s.upsert(entry("a", "agua"))
        assertEquals(listOf("agua", "Banco"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
        s.lock()
        assertEquals(VaultState.Locked, s.state.value)
        assertNull(s.dataKeyCopy())
        val reopened = store().apply { load() }
        assertEquals(VaultState.Locked, reopened.state.value)
        reopened.unlock("maestra".toCharArray())
        assertEquals(2, (reopened.state.value as VaultState.Unlocked).entries.size)
    }

    @Test fun `wrong master keeps it locked`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()); lock() }
        try {
            s.unlock("x".toCharArray())
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
        assertEquals(VaultState.Locked, s.state.value)
    }

    @Test fun `biometric key needs the master every 7 days`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        val key = s.dataKeyCopy()!!
        s.lock()
        clock += MASTER_RECHECK_MS - 1
        s.unlockWithKey(key.copyOf())
        assertTrue(s.state.value is VaultState.Unlocked)
        s.lock()
        clock += 1
        assertFalse(s.biometricAllowed)
        try {
            s.unlockWithKey(key.copyOf())
            fail()
        } catch (e: MasterRequired) {
            // expected
        }
    }

    @Test fun `idle lock after 5 minutes`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        clock += idle - 1
        assertFalse(s.lockIfIdle())
        s.touch()
        clock += idle
        assertTrue(s.lockIfIdle())
        assertEquals(VaultState.Locked, s.state.value)
    }

    @Test fun `export and import into another phone`() = runTest {
        val a = store("a.bin").apply { load(); create("m".toCharArray()); upsert(entry("1", "Wallet")) }
        val exported = a.exportText()!!
        val b = store("b.bin").apply { load(); create("otra".toCharArray()) }
        try {
            b.importReplace(exported, "mala".toCharArray())
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
        assertTrue((b.state.value as VaultState.Unlocked).entries.isEmpty())
        b.importReplace(exported, "m".toCharArray())
        assertEquals(listOf("Wallet"), (b.state.value as VaultState.Unlocked).entries.map { it.title })
    }

    /** An export written by hand with its master password, holding [entries] as they are (no store checks). */
    private fun handMadeExport(vararg entries: VaultEntry): String {
        val (file, dek) = VaultCrypto.create("m".toCharArray(), clock, KdfParams(1024, 1, 1))
        return VaultCrypto.encodeFile(VaultCrypto.write(file, dek, entries.toList()))
    }

    private fun ref(id: String, size: Long) = AttachmentRef(id, "a.bin", size, "00".repeat(32), "AA".repeat(22))

    @Test fun `a copy that breaks what new entries keep is refused and nothing changes`() = runTest {
        val identity = VaultEntry("i", EntryType.IDENTITY, "Yo", createdAt = 0, updatedAt = 0)
        val big = (0 until 11).map { ref("%032x".format(it), MAX_ATTACHMENT_BYTES.toLong()) } // 55 MB in all.
        val all = listOf(
            "La copia está dañada: no se puede importar" to handMadeExport(entry("1", "a"), entry("1", "b")),
            "La copia está dañada: no se puede importar" to handMadeExport(identity.copy(attachments = listOf(ref("%032x".format(1), 1)))),
            "La copia está dañada: no se puede importar" to handMadeExport(identity.copy(passwordHistory = listOf(PasswordChange("vieja", 0)))),
            "Los adjuntos de la bóveda pasan de 50 MB en total" to handMadeExport(entry("1", "a").copy(attachments = big)),
            "Los adjuntos de la bóveda pasan de 50 MB en total" to handMadeExport(entry("1", "a").copy(attachments = listOf(ref("%032x".format(1), -1)))),
        )
        val s = store().apply { load(); create("m".toCharArray()); upsert(entry("x", "Mía")) }
        val before = File(tmp.root, "vault.bin").readText()
        for ((message, export) in all) {
            try {
                s.importReplace(export, "m".toCharArray())
                fail(message)
            } catch (e: CryptoFailure) {
                assertEquals(message, e.message)
            }
            assertEquals(listOf("Mía"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
            assertEquals(before, File(tmp.root, "vault.bin").readText())
        }
    }

    @Test fun `a locked vault is never replaced by an import`() = runTest {
        val export = handMadeExport(entry("1", "Otra"))
        val s = store().apply { load(); create("m".toCharArray()); upsert(entry("x", "Mía")); lock() }
        val before = File(tmp.root, "vault.bin").readText()
        for (import in listOf<suspend () -> Unit>(
            { s.importReplace(export, "m".toCharArray()) },
            { s.importReplace(export.byteInputStream(), "m".toCharArray()) },
        )) {
            try {
                import()
                fail()
            } catch (e: IllegalStateException) {
                assertEquals("Abre la bóveda antes de importar una copia", e.message)
            }
        }
        assertEquals(before, File(tmp.root, "vault.bin").readText())
        // With no vault at all («Crea tu bóveda»), importing is the way in.
        val fresh = store("otra.bin").apply { load() }
        fresh.importReplace(export, "m".toCharArray())
        assertEquals(listOf("Otra"), (fresh.state.value as VaultState.Unlocked).entries.map { it.title })
    }

    @Test fun `change master and destroy`() = runTest {
        val s = store().apply { load(); create("vieja".toCharArray()); upsert(entry("1", "x")) }
        s.changeMaster("nueva".toCharArray())
        s.unlock("nueva".toCharArray())
        s.destroy()
        assertEquals(VaultState.NoVault, s.state.value)
        assertFalse(File(tmp.root, "vault.bin").exists())
    }

    // Seguridad 1.1.0: a Google Authenticator batch goes in with one write, all or nothing.
    @Test fun addAllSavesNewEntriesInOneWrite() = runTest {
        val s = store().apply { load(); create("m".toCharArray()); upsert(entry("1", "Wallet")) }
        val h = listOf(PasswordChange("vieja", 1))
        assertTrue(s.addAll(listOf(entry("2", "B").copy(passwordHistory = h), entry("3", "C"), entry("1", "Pisada"))))
        val titles = (s.state.value as VaultState.Unlocked).entries.map { it.title }
        // An id already in the vault is never overwritten, and a new entry starts without history.
        assertEquals(listOf("B", "C", "Wallet"), titles)
        assertTrue((s.state.value as VaultState.Unlocked).entries.all { it.passwordHistory.isEmpty() })
        val reopened = store().apply { load(); unlock("m".toCharArray()) }
        assertEquals(3, (reopened.state.value as VaultState.Unlocked).entries.size)
        s.lock()
        assertFalse(s.addAll(listOf(entry("4", "D"))))
    }

    // Seguridad 1.0.1: a changed master password locks the vault at once, so the user opens it with the new one
    // (and proves they remember it) before going on; the old data key is not left in memory.
    @Test fun changeMasterLocksVault() = runTest {
        val s = store().apply { load(); create("vieja".toCharArray()); upsert(entry("1", "Wallet")) }
        assertTrue(s.changeMaster("nueva".toCharArray()))
        assertEquals(VaultState.Locked, s.state.value)
        assertNull(s.dataKeyCopy())
        // Locked means locked: no change can be saved until it is opened again.
        assertFalse(s.upsert(entry("2", "Otra")))
    }

    @Test fun newMasterUnlocksOldDoesNot() = runTest {
        val s = store().apply { load(); create("vieja".toCharArray()); upsert(entry("1", "Wallet")) }
        assertTrue(s.changeMaster("nueva".toCharArray()))
        try {
            s.unlock("vieja".toCharArray())
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
        assertEquals(VaultState.Locked, s.state.value)
        s.unlock("nueva".toCharArray())
        assertEquals(listOf("Wallet"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
        // Same on disk, as another start of the app sees it.
        val reopened = store().apply { load() }
        reopened.unlock("nueva".toCharArray())
        assertEquals(1, (reopened.state.value as VaultState.Unlocked).entries.size)
    }

    @Test fun failedChangeMasterKeepsVaultUnlockedAndUnchanged() = runTest {
        val s = store().apply { load(); create("vieja".toCharArray()); upsert(entry("1", "Wallet")) }
        val before = File(tmp.root, "vault.bin").readText()
        val key = s.dataKeyCopy()!!
        breakSaving()
        try {
            s.changeMaster("nueva".toCharArray())
            fail()
        } catch (e: java.io.IOException) {
            // expected
        }
        assertEquals(listOf("Wallet"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
        assertTrue(key.contentEquals(s.dataKeyCopy()!!))
        assertEquals(before, File(tmp.root, "vault.bin").readText())
        // The old master password still works; the new one was never saved.
        s.lock()
        s.unlock("vieja".toCharArray())
        assertTrue(s.state.value is VaultState.Unlocked)
    }

    /** Runs [before] (once) as the computation is dispatched: a lock() that lands in the middle of a save. */
    private class InterruptingDispatcher : kotlinx.coroutines.CoroutineDispatcher() {
        var before: (() -> Unit)? = null
        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
            before?.also { before = null }?.invoke()
            block.run()
        }
    }

    @Test fun `locking during a save neither corrupts the vault nor reopens it`() = runTest {
        val compute = InterruptingDispatcher()
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, compute, now = { clock }, kdf = { KdfParams(1024, 1, 1) })
        s.load()
        s.create("maestra".toCharArray())
        compute.before = { s.lock() }
        s.upsert(entry("1", "Wallet"))
        assertEquals(VaultState.Locked, s.state.value)
        assertNull(s.dataKeyCopy())
        // Saved with the real key: the master password still opens it, with the change.
        s.unlock("maestra".toCharArray())
        assertEquals(listOf("Wallet"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
        // Same for a master change in flight.
        compute.before = { s.lock() }
        assertTrue(s.changeMaster("nueva".toCharArray()))
        assertEquals(VaultState.Locked, s.state.value)
        s.unlock("nueva".toCharArray())
        assertEquals(1, (s.state.value as VaultState.Unlocked).entries.size)
    }

    @Test fun `an unlock that finishes after leaving the app stays locked`() = runTest {
        val compute = InterruptingDispatcher()
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, compute, now = { clock }, kdf = { KdfParams(1024, 1, 1) })
        s.load()
        s.create("maestra".toCharArray())
        s.lock()
        compute.before = { s.lock() }
        s.unlock("maestra".toCharArray())
        assertEquals(VaultState.Locked, s.state.value)
        assertFalse(s.changeMaster("x".toCharArray()))
    }

    /** Holds every block dispatched while closed, until [release]: an operation that keeps the mutex meanwhile. */
    private class GateDispatcher : kotlinx.coroutines.CoroutineDispatcher() {
        private val held = ArrayList<Runnable>()
        var open = true
        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
            if (open) block.run() else held += block
        }
        fun release() {
            open = true
            held.toList().also { held.clear() }.forEach { it.run() }
        }
    }

    @Test fun `leaving the app while an unlock waits for another one keeps the vault locked`() = runTest {
        val compute = GateDispatcher()
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, compute, now = { clock }, kdf = { KdfParams(1024, 1, 1) })
        s.load()
        s.create("maestra".toCharArray())
        val key = s.dataKeyCopy()!!
        s.lock()
        for (waiting in listOf<suspend () -> Unit>({ s.unlockWithKey(key.copyOf()) }, { s.unlock("maestra".toCharArray()) })) {
            compute.open = false
            // The first one holds the mutex (its key derivation is held back); the second waits for it.
            val first = launch(start = CoroutineStart.UNDISPATCHED) { s.unlock("maestra".toCharArray()) }
            val second = launch(start = CoroutineStart.UNDISPATCHED) { waiting() }
            s.lock() // Home: MainActivity.onStop.
            compute.release()
            first.join()
            second.join()
            assertEquals(VaultState.Locked, s.state.value)
            assertNull(s.dataKeyCopy())
        }
        // Nothing was harmed: it opens as usual afterwards.
        s.unlockWithKey(key.copyOf())
        assertTrue(s.state.value is VaultState.Unlocked)
    }

    @Test fun `leaving the app while an import waits for the mutex keeps the vault locked`() = runTest {
        val compute = GateDispatcher()
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, compute, now = { clock }, kdf = { KdfParams(1024, 1, 1) })
        s.load()
        s.create("maestra".toCharArray())
        s.upsert(entry("1", "Wallet"))
        val export = s.exportText()!!
        compute.open = false
        val first = launch(start = CoroutineStart.UNDISPATCHED) { s.upsert(entry("2", "Otra")) }
        val second = launch(start = CoroutineStart.UNDISPATCHED) { s.importReplace(export, "maestra".toCharArray()) }
        s.lock()
        compute.release()
        first.join()
        second.join()
        assertEquals(VaultState.Locked, s.state.value)
        assertNull(s.dataKeyCopy())
        // The import was still saved: the master password opens the copy's content.
        s.unlock("maestra".toCharArray())
        assertEquals(listOf("Wallet"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
    }

    @Test fun `a damaged vault file is kept aside, never overwritten`() = runTest {
        File(tmp.root, "vault.bin").writeText("{esto no es una bóveda")
        val s = store().apply { load() }
        assertEquals(VaultState.NoVault, s.state.value)
        s.create("nueva".toCharArray())
        val kept = tmp.root.listFiles()!!.filter { it.name.startsWith("vault.bin.bad-") }
        assertEquals(1, kept.size)
        assertEquals("{esto no es una bóveda", kept.single().readText())
    }

    @Test fun `a 1_9_2 vault opens with the master password and is rewritten with its version`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()); upsert(entry("1", "Wallet")); lock() }
        // Make it look like a file from before the version key was always written.
        val f = File(tmp.root, "vault.bin")
        f.writeText(f.readText().replace("\"version\":3,", ""))
        val again = store().apply { load() }
        again.unlock("m".toCharArray())
        assertEquals(listOf("Wallet"), (again.state.value as VaultState.Unlocked).entries.map { it.title })
        assertTrue(f.readText().contains("\"version\":3"))
    }

    /** A directory where the temporary file goes makes every save fail (even for root, unlike permissions). */
    private fun breakSaving() = File(tmp.root, "vault.bin.tmp").mkdir()

    @Test fun `a full disk does not lock the user out with the right master password`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()); upsert(entry("1", "Wallet")); lock() }
        breakSaving()
        s.unlock("m".toCharArray())
        assertEquals(listOf("Wallet"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
        // A real change cannot be saved: it fails loudly and nothing changes.
        try {
            s.delete("1")
            fail()
        } catch (e: java.io.IOException) {
            // expected
        }
        assertEquals(1, (s.state.value as VaultState.Unlocked).entries.size)
    }

    @Test fun `a vault that could not be written is not shown as locked`() = runTest {
        breakSaving()
        val s = store().apply { load() }
        try {
            s.create("m".toCharArray())
            fail()
        } catch (e: java.io.IOException) {
            // expected
        }
        assertEquals(VaultState.NoVault, s.state.value)
    }

    @Test fun `importing over an unreadable vault keeps the old file aside`() = runTest {
        val source = store("source.bin").apply { load(); create("maestra".toCharArray()); upsert(entry("a", "Nota")) }
        val export = source.exportText()!!
        // Exists but cannot be read as a file right now: the store shows "no vault" without touching it.
        val unreadable = File(tmp.root, "vault.bin").apply { mkdirs(); File(this, "keep").writeText("x") }
        val s = store().apply { load() }
        assertEquals(VaultState.NoVault, s.state.value)
        s.importReplace(export, "maestra".toCharArray())
        assertEquals(1, (s.state.value as VaultState.Unlocked).entries.size)
        assertFalse(unreadable.isDirectory)
        assertTrue(tmp.root.listFiles()!!.any { it.name.startsWith("vault.bin.bad-") && File(it, "keep").exists() })
    }

    @Test fun `opening keeps the time of the last change on the vault file`() = runTest {
        val s = store().apply { load(); create("maestra".toCharArray()) }
        s.upsert(entry("a", "Agua"))
        val f = File(tmp.root, "vault.bin")
        assertTrue(f.setLastModified(1_600_000_000_000L))
        assertEquals(1_600_000_000_000L, s.lastChangeSavedAt())
        s.lock()
        clock += 60_000
        val before = f.readText()
        s.unlock("maestra".toCharArray()) // Saves the last master check: bookkeeping, not a change.
        assertFalse("Sí se guardó la comprobación", before == f.readText())
        assertEquals(1_600_000_000_000L, s.lastChangeSavedAt())
        s.upsert(entry("b", "Banco")) // A real change moves it.
        assertTrue(s.lastChangeSavedAt()!! > 1_600_000_000_000L)
    }

    @Test fun `no vault file, no time`() = runTest {
        val s = store().apply { load() }
        assertNull(s.lastChangeSavedAt())
    }

    @Test fun `creating a vault runs beforeCreate first`() = runTest {
        var calls = 0
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
            beforeCreate = { calls++; assertFalse("Antes de escribir", File(tmp.root, "vault.bin").exists()) })
        s.load()
        s.create("maestra".toCharArray())
        assertEquals(1, calls)
        assertTrue(s.state.value is VaultState.Unlocked)
        // Opening, editing or locking is not creating.
        s.upsert(entry("a", "Agua"))
        s.lock()
        s.unlock("maestra".toCharArray())
        assertEquals(1, calls)
    }

    @Test fun `a failing beforeCreate creates nothing and keeps the damaged file`() = runTest {
        val f = File(tmp.root, "vault.bin").apply { writeText("dañado") }
        val s = VaultStore(f, Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
            beforeCreate = { throw java.io.IOException("disco lleno") })
        s.load()
        assertEquals(VaultState.NoVault, s.state.value)
        assertTrue(runCatching { s.create("maestra".toCharArray()) }.isFailure)
        assertEquals(VaultState.NoVault, s.state.value)
        assertFalse(f.exists())
        assertEquals(0L, s.revision.value)
        assertTrue(tmp.root.listFiles()!!.any { it.name.startsWith("vault.bin.bad-") && it.readText() == "dañado" })
    }
}
