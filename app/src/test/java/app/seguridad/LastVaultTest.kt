package app.seguridad

import app.seguridad.vault.PRIMARY_VAULT_ID
import app.seguridad.vault.TagColor
import app.seguridad.vault.VAULTS_FILE
import app.seguridad.vault.VaultDeletion
import app.seguridad.vault.VaultRegistry
import app.seguridad.vault.VaultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 2.0, tarea 10: recordar la última bóveda (diseño «Recordar la última bóveda»: sale elegida en la pantalla de
 * desbloqueo). La app arranca en la última abierta; si ya no está (borrada, o el registro la nombra sin tenerla), en la
 * primera de la lista. La huella siempre es de la bóveda que se enseña: una respuesta tardía de la huella de otra bóveda
 * no abre nada ni apaga su huella.
 */
class LastVaultTest {
    @get:Rule val tmp = TemporaryFolder()

    private val cache by lazy { tmp.newFolder("cache") }
    private val masterA = "maestra-de-prueba-a"
    private val masterB = "maestra-de-prueba-b"

    private fun container(scope: kotlinx.coroutines.CoroutineScope) = AppContainer(tmp.root, cache, appVersion = "test", appScope = scope)

    @Test fun theAppStartsOnTheLastOneOpened() = runTest {
        val c = container(backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create(masterA.toCharArray(), name = "Uno")
        val b = c.createVault("Dos", masterB.toCharArray())
        // What the registry keeps unencrypted (pendientes, row 8): ids, colours, order and the last one opened; no name.
        val registry = File(tmp.root, VAULTS_FILE).readText()
        assertTrue(registry, "\"lastOpened\":\"$b\"" in registry)
        assertFalse(registry, "Uno" in registry || "Dos" in registry)
        // The next start (a new process) shows «Dos», locked.
        val again = container(backgroundScope)
        assertEquals(b, again.activeVaultId)
        again.vault.load()
        assertEquals(VaultState.Locked, again.vault.state.value)
        again.switchTo(PRIMARY_VAULT_ID)
        assertEquals(PRIMARY_VAULT_ID, container(backgroundScope).activeVaultId)
    }

    @Test fun aDeletedLastVaultFallsBackToTheFirst() = runTest {
        val c = container(backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create(masterA.toCharArray(), name = "Uno")
        val b = c.createVault("Dos", masterB.toCharArray())
        val d = c.createVault("Tres", "maestra-de-prueba-c".toCharArray())
        c.registry.reorder(listOf(b, d, PRIMARY_VAULT_ID))
        assertEquals(d, c.registry.lastOpened)
        assertEquals(VaultDeletion.DELETED, c.deleteVault(d, "maestra-de-prueba-c".toCharArray(), "Tres"))
        // The first one left in the list, now and at the next start.
        assertEquals(b, c.activeVaultId)
        assertEquals(b, c.registry.lastOpened)
        assertEquals(b, container(backgroundScope).activeVaultId)
    }

    /**
     * The last one is still listed but its file is gone (deleted outside the app, say): the app starts on it, as an empty
     * slot («Crea tu bóveda» there, with the way to the others), never on another vault without saying so.
     */
    @Test fun aListedLastVaultWithoutItsFileIsShownEmpty() = runTest {
        val c = container(backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create(masterA.toCharArray(), name = "Uno")
        val b = c.createVault("Dos", masterB.toCharArray())
        File(c.registry.dirOf(b), "vault.bin").delete()
        val again = container(backgroundScope)
        assertEquals(b, again.activeVaultId)
        again.vault.load()
        assertEquals(VaultState.NoVault, again.vault.state.value)
        assertTrue("The other vault is untouched", File(again.registry.dirOf(PRIMARY_VAULT_ID), "vault.bin").exists())
    }

    @Test fun aRegistryNamingAMissingVaultFallsBackToTheFirst() = runTest {
        val r = VaultRegistry(tmp.root, Dispatchers.Unconfined)
        val a = r.create(TagColor.RED)
        r.setLastOpened(a.id)
        val text = File(tmp.root, VAULTS_FILE).readText()
        File(tmp.root, VAULTS_FILE).writeText(text.replace("\"lastOpened\":\"${a.id}\"", "\"lastOpened\":\"${"f".repeat(32)}\""))
        assertEquals(PRIMARY_VAULT_ID, VaultRegistry(tmp.root, Dispatchers.Unconfined).lastOpened)
        // A name that is not an id at all, too.
        File(tmp.root, VAULTS_FILE).writeText(text.replace("\"lastOpened\":\"${a.id}\"", "\"lastOpened\":\"../vault\""))
        assertEquals(PRIMARY_VAULT_ID, VaultRegistry(tmp.root, Dispatchers.Unconfined).lastOpened)
        assertEquals(PRIMARY_VAULT_ID, container(backgroundScope).activeVaultId)
    }

    /**
     * The fingerprint's prompt answers late, after a switch: the key is of the vault it was asked for, which is no longer
     * the one shown. It never opens it (only the vault shown can be open), never opens the one shown, and is not taken for
     * a stale key (which would turn that vault's fingerprint off).
     */
    @Test fun aLateFingerprintOfAnotherVaultOpensNothing() = runTest {
        val c = container(backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create(masterA.toCharArray(), name = "Uno")
        val a = c.vault
        val keyOfA = a.dataKeyCopy()
        assertNotNull(keyOfA)
        c.createVault("Dos", masterB.toCharArray())
        val b = c.vault
        b.lock()
        testScheduler.advanceUntilIdle()
        val fileOfA = File(c.registry.dirOf(PRIMARY_VAULT_ID), "vault.bin").readBytes()
        val settings = File(tmp.root, "settings.json").readBytes()
        // No exception at all: neither StaleBiometricKey (which would turn A's fingerprint off) nor MasterRequired.
        a.unlockWithKey(keyOfA!!.copyOf())
        testScheduler.advanceUntilIdle()
        assertFalse(a.state.value is VaultState.Unlocked)
        assertFalse(b.state.value is VaultState.Unlocked)
        assertTrue(c.vault === b)
        // Ronda 1, i-6: nothing of A is written either (no purge of its trash, no bookkeeping save, no clock mark).
        assertArrayEquals("A's file, byte for byte", fileOfA, File(c.registry.dirOf(PRIMARY_VAULT_ID), "vault.bin").readBytes())
        assertArrayEquals("The settings, byte for byte", settings, File(tmp.root, "settings.json").readBytes())
        // Shown again, A opens with its fingerprint as always.
        c.switchTo(PRIMARY_VAULT_ID)
        a.unlockWithKey(keyOfA)
        assertTrue(a.state.value is VaultState.Unlocked)
    }

    /**
     * Ronda 1, i-6: the same with the master password, more likely there (Argon2id is slow): typed on A's screen, it ends
     * after a switch to B. A stays locked and nothing of it is written; shown again, the master opens it.
     */
    @Test fun aLateMasterUnlockOfAnotherVaultWritesNothing() = runTest {
        val c = container(backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create(masterA.toCharArray(), name = "Uno")
        val a = c.vault
        c.createVault("Dos", masterB.toCharArray())
        val b = c.vault
        testScheduler.advanceUntilIdle()
        val fileOfA = File(c.registry.dirOf(PRIMARY_VAULT_ID), "vault.bin").readBytes()
        val settings = File(tmp.root, "settings.json").readBytes()
        a.unlock(masterA.toCharArray())
        testScheduler.advanceUntilIdle()
        assertFalse(a.state.value is VaultState.Unlocked)
        assertTrue("B is as it was", b.state.value is VaultState.Unlocked)
        assertArrayEquals("A's file, byte for byte", fileOfA, File(c.registry.dirOf(PRIMARY_VAULT_ID), "vault.bin").readBytes())
        assertArrayEquals("The settings, byte for byte", settings, File(tmp.root, "settings.json").readBytes())
        c.switchTo(PRIMARY_VAULT_ID)
        a.unlock(masterA.toCharArray())
        assertTrue(a.state.value is VaultState.Unlocked)
    }
}
