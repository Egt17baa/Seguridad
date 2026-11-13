package app.seguridad

import app.seguridad.settings.oneTarget
import app.seguridad.settings.autoBackupUri
import app.seguridad.crypto.CryptoFailure
import app.seguridad.vault.BiometricRefusal
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultDeletion
import app.seguridad.vault.deletionText
import app.seguridad.vault.VAULT_IMPORT_NEW_DONE
import app.seguridad.vault.PRIMARY_VAULT_ID
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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

    /**
     * Review focus 2: a save that starts in one vault and ends after the switch lands there, or nowhere; never in the
     * other. The interleaving is forced (review M-4): the save of A holds A's mutex and waits on the compute thread, which
     * is held, when the switch starts; the switch locks A and must then wait for that save (if it did not, it would end
     * before the compute thread is let go, and this test fails). No sleeps: each step waits for the one before.
     */
    @Test fun lateWriteNeverLandsInAnotherVault() = runBlocking {
        val pool = Executors.newSingleThreadExecutor()
        // Tells when a block is handed to the compute thread, once armed.
        val armed = java.util.concurrent.atomic.AtomicReference<CountDownLatch?>(null)
        val compute = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                pool.execute(block)
                armed.getAndSet(null)?.countDown()
            }
        }
        // Let go in every case: a failed assertion must end the test, not leave the save of A waiting for ever.
        val gate = CountDownLatch(1)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        try {
            val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = scope, vaultCompute = compute)
            val (_, idB) = twoVaults(c)
            c.switchTo(PRIMARY_VAULT_ID)
            c.vault.unlock(masterA.toCharArray())
            val a = c.vault
            val fileB = File(c.registry.dirOf(idB), "vault.bin")
            val before = fileB.readText()
            // The compute thread is held; the save of A takes A's mutex and is handed to it.
            pool.execute { gate.await() }
            val handed = CountDownLatch(1)
            armed.set(handed)
            val save = scope.async { a.upsert(note("tarde")) }
            assertTrue("the save of A reached the compute thread", handed.await(30, java.util.concurrent.TimeUnit.SECONDS))
            // The switch starts: it locks A at once, and then waits for A's save.
            val switch = scope.async { c.switchTo(idB) }
            withTimeout(30_000) { a.state.first { it is VaultState.Locked } }
            assertEquals("the switch waits for the save of A", null, kotlinx.coroutines.withTimeoutOrNull(1_000) { switch.await() })
            assertSame("A is still the active vault while its save runs", a, c.vault)
            gate.countDown()
            withTimeout(60_000) { switch.await() }
            assertTrue("a save already started lands in A", withTimeout(60_000) { save.await() })
            assertEquals(before, fileB.readText())
            assertFalse(a.unlocked())
            // B, opened, does not have it.
            c.vault.unlock(masterB.toCharArray())
            assertTrue(c.vault.data().entries.none { it.id == "tarde" })
            // A stale screen of A writing now, with B open, saves nothing anywhere.
            assertFalse(a.upsert(note("despues")))
            assertTrue(c.vault.data().entries.none { it.id == "despues" })
            c.switchTo(PRIMARY_VAULT_ID)
            c.vault.unlock(masterA.toCharArray())
            assertTrue(c.vault.data().entries.any { it.id == "tarde" })
            assertTrue(c.vault.data().entries.none { it.id == "despues" })
        } finally {
            gate.countDown()
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            pool.shutdown()
        }
    }

    /**
     * 2.0, re-review 1 of task 7: a save of the editor that waits while the vault locks (behind another save, or a
     * derivation) never lands after the lock: not in the other vault after a switch, and not in its own vault opened
     * again. The editor gives the lock epoch of its «Guardar» ([VaultStore.upsert] with `sinceLock`).
     */
    @Test fun aSaveWaitingAcrossALockOrASwitchLandsNowhere() = runBlocking {
        val pool = Executors.newSingleThreadExecutor()
        val armed = java.util.concurrent.atomic.AtomicReference<CountDownLatch?>(null)
        val compute = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                pool.execute(block)
                armed.getAndSet(null)?.countDown()
            }
        }
        val gate = CountDownLatch(1)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        try {
            val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = scope, vaultCompute = compute)
            val (_, idB) = twoVaults(c)
            c.switchTo(PRIMARY_VAULT_ID)
            c.vault.unlock(masterA.toCharArray())
            val a = c.vault

            // 1. Requested before a lock, reaching the store after the same vault was opened again: refused.
            val before = a.lockEpoch
            a.lock()
            a.unlock(masterA.toCharArray())
            assertTrue(a.unlocked())
            assertFalse("a save of before the lock is not written after it", a.upsert(note("de-antes"), sinceLock = before))
            assertTrue(a.data().entries.none { it.id == "de-antes" })
            // The same save, with the epoch of now, is written.
            assertTrue(a.upsert(note("de-ahora"), sinceLock = a.lockEpoch))

            // 2. Waiting for the mutex behind another save while the vault switches: lands nowhere.
            val fileB = File(c.registry.dirOf(idB), "vault.bin")
            val beforeB = fileB.readText()
            pool.execute { gate.await() }
            val handed = CountDownLatch(1)
            armed.set(handed)
            val first = scope.async { a.upsert(note("primera")) }
            assertTrue(handed.await(30, java.util.concurrent.TimeUnit.SECONDS))
            val epoch = a.lockEpoch
            val waiting = scope.async { a.upsert(note("en-cola"), sinceLock = epoch) }
            val switch = scope.async { c.switchTo(idB) }
            withTimeout(30_000) { a.state.first { it is VaultState.Locked } }
            gate.countDown()
            withTimeout(60_000) { switch.await() }
            assertTrue("a save already started lands in A", withTimeout(60_000) { first.await() })
            assertFalse("a save that waited across the switch lands nowhere", withTimeout(60_000) { waiting.await() })
            assertEquals(beforeB, fileB.readText())
            c.vault.unlock(masterB.toCharArray())
            assertTrue(c.vault.data().entries.none { it.id == "en-cola" || it.id == "primera" })
            c.switchTo(PRIMARY_VAULT_ID)
            c.vault.unlock(masterA.toCharArray())
            assertTrue(c.vault.data().entries.any { it.id == "primera" })
            assertTrue(c.vault.data().entries.none { it.id == "en-cola" || it.id == "de-antes" })
        } finally {
            gate.countDown()
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            pool.shutdown()
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
        c.settings.updateVault(PRIMARY_VAULT_ID) { it.oneTarget(autoBackupUri = "content://uno") }
        c.settings.updateVault(idB) { it.oneTarget(autoBackupUri = "content://dos") }
        val dirB = c.registry.dirOf(idB)
        assertFails<CryptoFailure> { c.deleteVault(idB, masterA.toCharArray(), "Dos") }
        assertTrue(dirB.exists())
        // Its master opens it (and shows its name); a name that is not its own deletes nothing.
        c.vault.lock()
        assertEquals(VaultDeletion.WRONG_NAME, c.deleteVault(idB, masterB.toCharArray(), "Tres"))
        assertTrue(c.vault.unlocked())
        assertEquals(VaultDeletion.WRONG_NAME, c.deleteVault(idB, masterB.toCharArray(), "dos"))
        assertTrue(dirB.exists())
        assertTrue(c.registry.vaults.value.any { it.id == idB })
        // The same text as it is shown: an invisible character does not count.
        assertEquals(VaultDeletion.DELETED, c.deleteVault(idB, masterB.toCharArray(), " Do​s "))
        assertFalse(dirB.exists())
        assertEquals(listOf(PRIMARY_VAULT_ID), c.registry.vaults.value.map { it.id })
        assertEquals(null, c.settings.settings.value.perVault[idB])
        assertEquals(listOf(idB), forgotten)
        assertEquals(listOf("content://dos"), released)
        assertEquals(PRIMARY_VAULT_ID, c.activeVaultId)
        assertFalse(c.vault.unlocked())
        // The last one is not deleted from here («He olvidado la contraseña» is the way for it).
        assertEquals(VaultDeletion.LAST_VAULT, c.deleteVault(PRIMARY_VAULT_ID, masterA.toCharArray(), "Uno"))
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

    /**
     * Review I-1 (ruling T2, I-1): the A11 clock mark is each vault's. Any time seen raises every vault's mark; the master
     * password of one vault lowers only its own, so another vault's master never heals the clock of A's fingerprint.
     */
    @Test fun masterOfOneDoesNotHealTheClockOfAnother() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        val (a, idB) = twoVaults(c)
        // The clock went back an hour: the highest time seen is ahead of it, for every vault.
        val ahead = System.currentTimeMillis() + 60 * 60_000L
        c.rememberClock(ahead)
        assertEquals(ahead, c.clockMark(PRIMARY_VAULT_ID))
        assertEquals(ahead, c.clockMark(idB))
        assertEquals(BiometricRefusal.CLOCK, a.biometricRefusal)
        // B's master password heals B's clock only.
        c.vault.lock()
        c.vault.unlock(masterB.toCharArray())
        assertTrue(c.clockMark(idB) < ahead)
        assertEquals(null, c.vault.biometricRefusal)
        assertEquals(ahead, c.clockMark(PRIMARY_VAULT_ID))
        c.switchTo(PRIMARY_VAULT_ID)
        assertEquals(BiometricRefusal.CLOCK, c.vault.biometricRefusal)
        // A new vault starts at the highest mark of that moment.
        c.vault.unlock(masterA.toCharArray())
        assertEquals(null, c.vault.biometricRefusal)
        c.rememberClock(ahead + 1)
        val third = c.createVault("Tres", "maestra-de-prueba-c".toCharArray())
        assertTrue(c.clockMark(third) >= ahead + 1)
    }

    /** Review I-2: a wrong password of the copy leaves the open vault as it was, open, and says so. */
    @Test fun importAsNewWithAWrongPasswordStaysWhereItWas() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create(masterA.toCharArray(), name = "Uno")
        val copy = ByteArrayOutputStream().also { assertTrue(c.vault.exportBackup(it)) }.toByteArray()
        val message = c.runVaultsAction { c.importAsNewVault(ByteArrayInputStream(copy), masterB.toCharArray(), ""); VAULT_IMPORT_NEW_DONE }
        assertEquals(CryptoFailure().message, message)
        assertEquals(message, c.vaultsNotice.value)
        assertEquals(PRIMARY_VAULT_ID, c.activeVaultId)
        assertTrue("never locked by the attempt", c.vault.unlocked())
        assertEquals(listOf(PRIMARY_VAULT_ID), c.registry.vaults.value.map { it.id })
        // Shown: gone. A good one says so, kept after the switch it makes.
        c.vaultsNoticeShown(message!!)
        assertEquals(null, c.vaultsNotice.value)
        val done = c.runVaultsAction { c.importAsNewVault(ByteArrayInputStream(copy), masterA.toCharArray(), ""); VAULT_IMPORT_NEW_DONE }
        assertEquals(VAULT_IMPORT_NEW_DONE, done)
        assertEquals(VAULT_IMPORT_NEW_DONE, c.vaultsNotice.value)
        assertNotEquals(PRIMARY_VAULT_ID, c.activeVaultId)
        assertTrue(c.vault.unlocked())
        assertEquals("Uno", c.vault.data().name)
    }

    /** Review M-1, M-2: a slot with no vault file at all goes without master or name; one with a file set aside does not. */
    @Test fun anEmptySlotGoesWithoutMasterButASetAsideFileStays() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        val (_, idB) = twoVaults(c)
        c.vault.destroy() // «He olvidado la contraseña maestra» on B: an empty slot.
        assertTrue(c.isEmptySlot(idB))
        assertEquals(VaultDeletion.DELETED, c.deleteVault(idB, CharArray(0), ""))
        assertFalse(c.registry.dirOf(idB).exists())
        // A third vault whose file could not be read and was set aside for recovery (BOV-2): no master, no deletion.
        c.vault.unlock(masterA.toCharArray())
        val idC = c.createVault("Tres", masterB.toCharArray())
        c.switchTo(PRIMARY_VAULT_ID)
        val dirC = c.registry.dirOf(idC)
        File(dirC, "vault.bin").renameTo(File(dirC, "vault.bin.bad-1"))
        val fresh = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        fresh.settings.load()
        fresh.switchTo(idC)
        assertEquals(VaultState.NoVault, fresh.vault.state.value)
        assertFalse(fresh.isEmptySlot(idC))
        assertEquals(VaultDeletion.KEPT_DAMAGED, fresh.deleteVault(idC, CharArray(0), ""))
        assertTrue(File(dirC, "vault.bin.bad-1").exists())
        assertTrue(fresh.registry.vaults.value.any { it.id == idC })
    }

    /** Review M-5: a late result of the copies of a vault already deleted does not bring its settings back. */
    @Test fun aLateCopyResultOfADeletedVaultIsDropped() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        val (_, idB) = twoVaults(c)
        val storeB = c.vault
        c.settings.updateVault(idB) { it.oneTarget(autoBackupUri = "content://dos") }
        assertEquals(VaultDeletion.DELETED, c.deleteVault(idB, masterB.toCharArray(), "Dos"))
        c.recordAutoBackup(idB, storeB, app.seguridad.vault.AutoBackupResult("content://dos", 1L, null, listOf("x")))
        c.recordImportCopy(idB, storeB, "content://dos", "y", storeB.generation)
        assertEquals(null, c.settings.settings.value.perVault[idB])
    }

    /** Re-review 1, minor 1: two taps in one frame start the action once (the busy mark is set before it is launched). */
    @Test fun twoTapsRunTheActionOnce() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        val runs = java.util.concurrent.atomic.AtomicInteger()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        c.launchVaultsAction { runs.incrementAndGet(); release.await(); "uno" }
        c.launchVaultsAction { runs.incrementAndGet(); "dos" }
        assertTrue(c.vaultsBusy.value)
        testScheduler.runCurrent()
        release.complete(Unit)
        val said = withTimeout(30_000) { c.vaultsNotice.first { it != null } }
        withTimeout(30_000) { c.vaultsBusy.first { !it } }
        assertEquals(1, runs.get())
        assertEquals("uno", said)
        // Once it ended, the next one runs.
        c.launchVaultsAction { runs.incrementAndGet(); "tres" }
        withTimeout(30_000) { c.vaultsNotice.first { it == "tres" } }
        assertEquals(2, runs.get())
    }

    /** Re-review 1, minor 2: each way a deletion can end has its own text; the only vault is never «dañado». */
    @Test fun eachDeletionOutcomeSaysWhatHappened() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        // The only vault, empty: not deleted, and the text says why (not that a file is damaged).
        assertEquals(VaultDeletion.LAST_VAULT, c.deleteVault(PRIMARY_VAULT_ID, CharArray(0), ""))
        assertEquals(app.seguridad.vault.VAULT_DELETE_LAST, deletionText(VaultDeletion.LAST_VAULT))
        assertEquals(app.seguridad.vault.VAULT_DELETED, deletionText(VaultDeletion.DELETED))
        assertEquals(app.seguridad.vault.VAULT_DELETE_NAME_WRONG, deletionText(VaultDeletion.WRONG_NAME))
        assertEquals(app.seguridad.vault.VAULT_DELETE_DAMAGED, deletionText(VaultDeletion.KEPT_DAMAGED))
        assertEquals(app.seguridad.vault.VAULT_DELETE_LOCKED, deletionText(VaultDeletion.LOCKED))
        assertEquals(VaultDeletion.entries.size, VaultDeletion.entries.map(::deletionText).distinct().size)
    }
}
