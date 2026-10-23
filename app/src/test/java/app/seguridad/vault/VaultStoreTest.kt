package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
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
        VaultStore(File(tmp.root, name), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = KdfParams(1024, 1, 1))

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

    @Test fun `change master and destroy`() = runTest {
        val s = store().apply { load(); create("vieja".toCharArray()); upsert(entry("1", "x")) }
        s.changeMaster("nueva".toCharArray())
        s.lock()
        s.unlock("nueva".toCharArray())
        s.destroy()
        assertEquals(VaultState.NoVault, s.state.value)
        assertFalse(File(tmp.root, "vault.bin").exists())
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
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, compute, now = { clock }, kdf = KdfParams(1024, 1, 1))
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
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, compute, now = { clock }, kdf = KdfParams(1024, 1, 1))
        s.load()
        s.create("maestra".toCharArray())
        s.lock()
        compute.before = { s.lock() }
        s.unlock("maestra".toCharArray())
        assertEquals(VaultState.Locked, s.state.value)
        assertFalse(s.changeMaster("x".toCharArray()))
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
        f.writeText(f.readText().replace("\"version\":2,", ""))
        val again = store().apply { load() }
        again.unlock("m".toCharArray())
        assertEquals(listOf("Wallet"), (again.state.value as VaultState.Unlocked).entries.map { it.title })
        assertTrue(f.readText().contains("\"version\":2"))
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
}
