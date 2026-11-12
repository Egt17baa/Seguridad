package app.seguridad

import app.seguridad.crypto.CryptoFailure
import app.seguridad.vault.EntryType
import app.seguridad.vault.PRIMARY_VAULT_ID
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/** 2.0, tarea 2: varias bóvedas en `AppContainer`, una abierta a la vez (foco de revisión 2). */
class AppContainerVaultsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val cache by lazy { tmp.newFolder("cache") }
    private val masterA = "maestra-de-prueba-a"
    private val masterB = "maestra-de-prueba-b"

    /**
     * [block] throws [T]. Not assertThrows with runBlocking: inside runTest that blocks the test's own thread, where the
     * container's background jobs (the clock mark's write, holding the settings' lock) wait to go on.
     */
    private suspend inline fun <reified T : Throwable> assertFails(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return
            throw e
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }

    private fun note(id: String) = VaultEntry(id, EntryType.NOTE, "Nota $id", notes = "n $id", createdAt = 0, updatedAt = 0)
    private fun VaultStore.unlocked() = state.value is VaultState.Unlocked
    private fun VaultStore.data() = (state.value as VaultState.Unlocked).data

    /** A container with the first vault («Uno») open and a second one («Dos») created after it, open now. */
    private suspend fun twoVaults(
        c: AppContainer,
    ): Pair<VaultStore, String> {
        c.settings.load()
        c.vault.load()
        c.vault.create(masterA.toCharArray(), name = "Uno")
        val a = c.vault
        val b = c.createVault("Dos", masterB.toCharArray())
        return a to b
    }

    @Test fun switchLocksTheOpenOne() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        val (a, b) = twoVaults(c)
        assertEquals(b, c.activeVaultId)
        assertNotSame(a, c.vault)
        assertFalse("Creating another locks the open one", a.unlocked())
        assertTrue(c.vault.unlocked())
        assertEquals("Dos", c.vault.data().name)
        val storeB = c.vault
        c.switchTo(PRIMARY_VAULT_ID)
        assertSame(a, c.vault)
        assertSame(a, c.activeVault.value)
        assertFalse(storeB.unlocked())
        assertEquals(VaultState.Locked, c.vault.state.value)
        assertEquals("The one opened last is remembered", PRIMARY_VAULT_ID, c.registry.lastOpened)
        // The same vault again changes nothing, and an id that is not a vault is refused.
        c.vault.unlock(masterA.toCharArray())
        c.switchTo(PRIMARY_VAULT_ID)
        assertTrue(c.vault.unlocked())
        assertFails<IllegalArgumentException> { c.switchTo("0".repeat(32)) }
    }

    @Test fun onlyOneUnlockedAtATime() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        val (a, _) = twoVaults(c)
        val b = c.vault
        // A screen that kept the first vault's store cannot open it while another is the active one.
        a.unlock(masterA.toCharArray())
        assertFalse(a.unlocked())
        assertTrue(b.unlocked())
        c.switchTo(PRIMARY_VAULT_ID)
        c.vault.unlock(masterA.toCharArray())
        assertTrue(a.unlocked())
        b.unlock(masterB.toCharArray())
        assertFalse(b.unlocked())
        assertEquals(1, listOf(a, b).count { it.unlocked() })
    }

    @Test fun masterOfOneDoesNotOpenAnother() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        twoVaults(c)
        c.vault.lock()
        assertFails<CryptoFailure> { c.vault.unlock(masterA.toCharArray()) }
        assertFalse(c.vault.unlocked())
        c.switchTo(PRIMARY_VAULT_ID)
        assertFails<CryptoFailure> { c.vault.unlock(masterB.toCharArray()) }
        assertFalse(c.vault.unlocked())
    }

    /** Review focus 2: a save that starts in one vault and ends after the switch lands there, or nowhere; never in the other. */
    @Test fun lateWriteNeverLandsInAnotherVault() = runBlocking {
        val pool = Executors.newSingleThreadExecutor()
        val compute = pool.asCoroutineDispatcher()
        try {
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
            val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = scope, vaultCompute = compute)
            val (_, idB) = twoVaults(c)
            c.switchTo(PRIMARY_VAULT_ID)
            c.vault.unlock(masterA.toCharArray())
            val a = c.vault
            val fileB = File(c.registry.dirOf(idB), "vault.bin")
            val before = fileB.readText()
            // The save of A waits on the compute thread, holding A's mutex, while the switch to B starts.
            val gate = CountDownLatch(1)
            pool.execute { gate.await() }
            val save = async(Dispatchers.IO) { a.upsert(note("tarde")) }
            Thread.sleep(200)
            val switch = async(Dispatchers.IO) { c.switchTo(idB) }
            Thread.sleep(200)
            gate.countDown()
            withTimeout(60_000) { switch.await() }
            val saved = withTimeout(60_000) { save.await() }
            assertEquals(before, fileB.readText())
            assertFalse(a.unlocked())
            // B, opened, does not have it; A has it if the save said so.
            c.vault.unlock(masterB.toCharArray())
            assertTrue(c.vault.data().entries.none { it.id == "tarde" })
            // A stale screen of A writing now, with B open, saves nothing anywhere.
            assertFalse(a.upsert(note("despues")))
            assertTrue(c.vault.data().entries.none { it.id == "despues" })
            c.switchTo(PRIMARY_VAULT_ID)
            c.vault.unlock(masterA.toCharArray())
            assertEquals(saved, c.vault.data().entries.any { it.id == "tarde" })
            assertTrue(c.vault.data().entries.none { it.id == "despues" })
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun deleteNeedsMasterAndName() = runTest {
        val released = mutableListOf<String>()
        val forgotten = mutableListOf<String>()
        val c = AppContainer(
            tmp.root, cache, appVersion = "test", appScope = backgroundScope,
            releaseFolder = { released += it }, forgetBiometric = { forgotten += it },
        )
        val (_, idB) = twoVaults(c)
        c.settings.updateVault(PRIMARY_VAULT_ID) { it.copy(autoBackupUri = "content://uno") }
        c.settings.updateVault(idB) { it.copy(autoBackupUri = "content://dos") }
        val dirB = c.registry.dirOf(idB)
        assertFails<CryptoFailure> { c.deleteVault(idB, masterA.toCharArray(), "Dos") }
        assertTrue(dirB.exists())
        // Its master opens it (and shows its name); a name that is not its own deletes nothing.
        c.vault.lock()
        assertFalse(c.deleteVault(idB, masterB.toCharArray(), "Tres"))
        assertTrue(c.vault.unlocked())
        assertFalse(c.deleteVault(idB, masterB.toCharArray(), "dos"))
        assertTrue(dirB.exists())
        assertTrue(c.registry.vaults.value.any { it.id == idB })
        // The same text as it is shown: an invisible character does not count.
        assertTrue(c.deleteVault(idB, masterB.toCharArray(), " Do​s "))
        assertFalse(dirB.exists())
        assertEquals(listOf(PRIMARY_VAULT_ID), c.registry.vaults.value.map { it.id })
        assertEquals(null, c.settings.settings.value.perVault[idB])
        assertEquals(listOf(idB), forgotten)
        assertEquals(listOf("content://dos"), released)
        assertEquals(PRIMARY_VAULT_ID, c.activeVaultId)
        assertFalse(c.vault.unlocked())
        // The last one is not deleted from here («He olvidado la contraseña» is the way for it).
        assertFalse(c.deleteVault(PRIMARY_VAULT_ID, masterA.toCharArray(), "Uno"))
        assertTrue(File(c.registry.dirOf(PRIMARY_VAULT_ID), "vault.bin").exists())
    }

    @Test fun importAsNewTwiceMakesTwoVaults() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create(masterA.toCharArray(), name = "Uno")
        c.vault.upsert(note("n1"))
        val copy = ByteArrayOutputStream().also { assertTrue(c.vault.exportBackup(it)) }.toByteArray()
        val first = c.importAsNewVault(ByteArrayInputStream(copy), masterA.toCharArray(), "Copia")
        assertEquals(first, c.activeVaultId)
        assertEquals("Copia", c.vault.data().name)
        assertTrue(c.vault.data().entries.any { it.id == "n1" })
        val second = c.importAsNewVault(ByteArrayInputStream(copy), masterA.toCharArray(), "")
        assertNotEquals(first, second)
        assertEquals("Without a name given, the copy's own", "Uno", c.vault.data().name)
        assertEquals(listOf(PRIMARY_VAULT_ID, first, second), c.registry.vaults.value.map { it.id })
        // Each one is a vault of its own: the first is still there, closed, and opens with the copy's master.
        c.switchTo(first)
        c.vault.unlock(masterA.toCharArray())
        assertTrue(c.vault.data().entries.any { it.id == "n1" })
        // A wrong master creates nothing.
        assertFails<CryptoFailure> { c.importAsNewVault(ByteArrayInputStream(copy), masterB.toCharArray(), "X") }
        assertEquals(3, c.registry.vaults.value.size)
        assertEquals(first, c.activeVaultId)
    }

    /**
     * COV2-1 with several vaults: someone holding the phone for a moment, with the vaults locked, cannot make a vault of
     * their own (which would be open, and with it the settings of the whole app): another vault is made only from an open
     * one, or when the phone has none yet.
     */
    @Test fun newVaultNeedsAnOpenVault() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        assertTrue("No vault yet: the first one is made freely", c.canAddVault())
        c.vault.create(masterA.toCharArray(), name = "Uno")
        c.vault.lock()
        assertFalse(c.canAddVault())
        assertFails<IllegalStateException> { c.createVault("Mía", masterB.toCharArray()) }
        val copy = File(tmp.root, "copia").also { it.writeText("x") }
        assertFails<IllegalStateException> { c.importAsNewVault(copy.inputStream(), masterB.toCharArray(), "") }
        assertEquals(listOf(PRIMARY_VAULT_ID), c.registry.vaults.value.map { it.id })
        c.vault.unlock(masterA.toCharArray())
        assertTrue(c.canAddVault())
        c.createVault("Dos", masterB.toCharArray())
        assertEquals(2, c.registry.vaults.value.size)
    }
}
