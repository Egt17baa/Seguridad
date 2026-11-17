package app.seguridad.vault

import app.seguridad.AppContainer
import app.seguridad.SETTINGS_FILE
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import app.seguridad.settings.withTargetAdded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * RT-21, part c (ruling of the director on the final attack): an [OutOfMemoryError] while a copy or the vault is read is
 * the last defence, and says what is true: [COPY_NO_MEMORY] with nothing changed, [VAULT_NO_MEMORY] with the vault saved
 * and locked and its fingerprint on. The error is made by hand through [VaultCrypto.beforeDecode]: no test runs out of
 * memory for real.
 */
class NoMemoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val master = "maestra-de-prueba-memoria"

    @After fun reset() {
        VaultCrypto.beforeDecode = {}
    }

    private fun failDecode(times: Int = Int.MAX_VALUE) {
        var left = times
        VaultCrypto.beforeDecode = { if (left-- > 0) throw OutOfMemoryError() }
    }

    private suspend fun expect(text: String, block: suspend () -> Unit) {
        try {
            block()
            fail("No failure")
        } catch (e: CryptoFailure) {
            assertEquals(text, e.message)
        }
    }

    private fun note(id: String) = VaultEntry(id, EntryType.NOTE, "Nota $id", notes = "n $id", createdAt = 0, updatedAt = 0)

    @Test fun anImportWithoutMemoryChangesNothing() = runTest {
        val c = AppContainer(tmp.root, tmp.newFolder("cache"), appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create(master.toCharArray())
        c.vault.upsert(note("1"))
        c.settings.updateVault(c.activeVaultId) { it.withTargetAdded("content://prueba", app.seguridad.vault.BackupSchedule.ON_CHANGE) }
        val copy = ByteArrayOutputStream().also { assertTrue(c.vault.exportBackup(it)) }.toByteArray()
        val dir = c.registry.dirOf(c.activeVaultId)
        val vaultBefore = File(dir, "vault.bin").readBytes()
        val settingsBefore = File(tmp.root, SETTINGS_FILE).readBytes()
        failDecode()
        expect(COPY_NO_MEMORY) { c.vault.importReplace(ByteArrayInputStream(copy), master.toCharArray()) }
        assertArrayEquals(vaultBefore, File(dir, "vault.bin").readBytes())
        assertArrayEquals(settingsBefore, File(tmp.root, SETTINGS_FILE).readBytes())
        assertTrue("the temporary folder goes", dir.walkTopDown().none { it.name.startsWith("import-") })
        assertTrue(c.vault.state.value is VaultState.Unlocked)
    }

    @Test fun aCheckWithoutMemoryLeavesNoFolder() = runTest {
        val s = VaultStore(File(tmp.newFolder("a"), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { KdfParams(1024, 1, 1) })
            .apply { load(); create(master.toCharArray()); upsert(note("1")) }
        val copy = ByteArrayOutputStream().also { assertTrue(s.exportBackup(it)) }.toByteArray()
        val temp = File(tmp.root, "comprobar")
        failDecode()
        expect(COPY_NO_MEMORY) { checkBackup(ByteArrayInputStream(copy), master.toCharArray(), temp, Dispatchers.Unconfined, Dispatchers.Unconfined) }
        assertTrue(temp.listFiles().isNullOrEmpty())
    }

    @Test fun anImportAsNewWithoutMemoryLeavesNoVault() = runTest {
        val c = AppContainer(tmp.root, tmp.newFolder("cache"), appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create(master.toCharArray(), name = "Uno")
        c.vault.upsert(note("1"))
        val copy = ByteArrayOutputStream().also { assertTrue(c.vault.exportBackup(it)) }.toByteArray()
        val before = c.registry.vaults.value
        val active = c.activeVaultId
        val vaultBefore = File(c.registry.dirOf(active), "vault.bin").readBytes()
        failDecode()
        expect(COPY_NO_MEMORY) { c.importAsNewVault(ByteArrayInputStream(copy), master.toCharArray(), "") }
        assertEquals(before, c.registry.vaults.value)
        assertEquals(active, c.activeVaultId)
        assertTrue(c.vault.state.value is VaultState.Unlocked)
        assertArrayEquals(vaultBefore, File(c.registry.dirOf(active), "vault.bin").readBytes())
    }

    @Test fun anUnlockWithTheMasterWithoutMemoryIsNeverAWrongPassword() = runTest {
        val dir = tmp.newFolder("m")
        val s = VaultStore(File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { KdfParams(1024, 1, 1) })
            .apply { load(); create(master.toCharArray()); upsert(note("1")) }
        s.lock()
        val before = File(dir, "vault.bin").readBytes()
        failDecode(times = 1)
        try {
            s.unlock(master.toCharArray())
            fail("It opened")
        } catch (e: CryptoFailure) {
            assertEquals(VAULT_NO_MEMORY, masterUnlockErrorText(e))
            assertFalse(masterUnlockErrorText(e) == WRONG_MASTER)
        }
        assertEquals(VaultState.Locked, s.state.value)
        assertArrayEquals(before, File(dir, "vault.bin").readBytes())
        s.unlock(master.toCharArray())
        assertTrue(s.state.value is VaultState.Unlocked)
    }

    @Test fun anUnlockWithTheFingerprintWithoutMemoryKeepsTheFingerprint() = runTest {
        val s = VaultStore(File(tmp.newFolder("h"), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { KdfParams(1024, 1, 1) })
            .apply { load(); create(master.toCharArray()); upsert(note("1")) }
        val key = s.dataKeyCopy()!!
        s.lock()
        failDecode(times = 1)
        try {
            s.unlockWithKey(key.copyOf())
            fail("It opened")
        } catch (e: StaleBiometricKey) {
            fail("Taken for a stale key: the fingerprint would be turned off")
        } catch (e: CryptoFailure) {
            assertEquals(VAULT_NO_MEMORY, e.message)
        }
        assertEquals(VaultState.Locked, s.state.value)
        s.unlockWithKey(key.copyOf())
        assertTrue(s.state.value is VaultState.Unlocked)
    }
}
