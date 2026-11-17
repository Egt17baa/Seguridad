package app.seguridad.redteam

import app.seguridad.AppContainer
import app.seguridad.settings.settingsEditable
import app.seguridad.vault.EntryType
import app.seguridad.vault.PRIMARY_VAULT_ID
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultRegistry
import app.seguridad.vault.VaultState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/*
 * Final attack of Clavero 2.0 (R32), phase 1, surface 3: isolation between vaults (hypotheses H-1 and H-2 of the director)
 * and the registry. Same naming as RedTeam20CopyTest. Report: docs/superpowers/handoff/sdd-2.0/redteam-report.md.
 */
class RedTeam20VaultsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val masterA = "maestra-de-prueba-a-rt"
    private val masterB = "maestra-de-prueba-b-rt"

    private fun container(scope: kotlinx.coroutines.CoroutineScope) =
        AppContainer(tmp.root, tmp.newFolder("cache"), appVersion = "test", appScope = scope)

    /**
     * RT-22 (H-1, COV2-1 with several vaults). [settingsEditable] (Settings.kt:443) is true for [VaultState.NoVault] of the
     * active vault, and «He olvidado la contraseña maestra» (VaultScreen.kt:479, :489-498) empties the vault shown with one
     * confirmation (accepted for that vault, ruling T2, doubt 1). With vault A (the owner's, with its file) and vault B both
     * locked, someone holding the phone for a moment switches to B from the lock screen (VaultScreen.kt:439 →
     * VaultPickerScreen.kt:248, no password asked), empties it, and B is now NoVault: Ajustes lets them change the idle lock
     * and the clipboard time of the whole app (SettingsScreen.kt:633, :641), which then protect A. The rule that closes
     * COV2-1 for creating vaults ([AppContainer.canAddVault], AppContainer.kt:631-632: «no vault of the phone has a file»)
     * is not the one Ajustes uses.
     *
     * The safe behaviour asserted: with a vault of the phone that has a file and is locked, the app-wide settings are not
     * editable, whichever vault is active. If the fix moves the rule elsewhere (a function of the container), this test
     * calls that one instead; the assertion stays.
     */
    @Ignore("RT-22: abierto, ver redteam-report.md")
    @Test fun rt22_emptyingAnotherVaultDoesNotUnlockTheSettingsOfTheWholeApp() = runTest {
        val c = container(backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create(masterA.toCharArray(), name = "Uno")
        c.vault.upsert(VaultEntry("x", EntryType.LOGIN, "Banco", password = "de-a", createdAt = 1, updatedAt = 1))
        val b = c.createVault("Dos", masterB.toCharArray())
        c.vault.lock()
        assertFalse("Both locked: nothing editable", settingsEditable(c.vault.state.value))
        // The steps on the lock screen of B: «He olvidado la contraseña maestra», «Borrar bóveda» (VaultScreen.kt:494-497).
        c.vault.destroy()
        assertEquals(b, c.activeVaultId)
        assertEquals(VaultState.NoVault, c.vault.state.value)
        assertTrue("Vault A still has its file", File(c.registry.dirOf(PRIMARY_VAULT_ID), "vault.bin").exists())
        assertFalse(
            "With vault A locked and its file there, emptying B must not let the lock and the clipboard of the whole app be changed",
            settingsEditable(c.vault.state.value),
        )
    }

    /**
     * RT-23 (H-2, truthfulness), closed by the director's ruling, option (a): no code changes, the design is corrected.
     * «Importar como bóveda nueva» keeps the copy's data key ([VaultCrypto.afterMasterUnlock] only re-seals a v4 copy or wraps
     * its same key again; only [VaultCrypto.changeMaster] makes a new one). What is true: right after the import the new
     * vault has A's data key (and A's master), its own vault tag (D-12), and after a change of its master password it has a
     * key of its own, A's master no longer opens it, and A still opens with its own master and its entry.
     */
    @Test fun resistsAVaultImportedAsNewSharesTheCopysKeyOnlyUntilAMasterChanges() = runTest {
        val c = container(backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create(masterA.toCharArray(), name = "Uno")
        c.vault.upsert(VaultEntry("x", EntryType.LOGIN, "Banco", password = "de-a", createdAt = 1, updatedAt = 1))
        val keyA = c.vault.dataKeyCopy()!!
        val tagA = (c.vault.state.value as VaultState.Unlocked).data.vaultTag
        val copy = ByteArrayOutputStream().also { assertTrue(c.vault.exportBackup(it)) }.toByteArray()
        val idB = c.importAsNewVault(ByteArrayInputStream(copy), masterA.toCharArray(), "Copia")
        assertTrue(c.vault.state.value is VaultState.Unlocked)
        val keyB = c.vault.dataKeyCopy()!!
        assertTrue("Right after the import, the copy's data key", keyA.contentEquals(keyB))
        assertNotEquals("A tag of its own (D-12)", tagA, (c.vault.state.value as VaultState.Unlocked).data.vaultTag)
        // A change of B's master password gives B a data key of its own.
        assertTrue(c.vault.changeMaster(masterB.toCharArray()))
        c.vault.unlock(masterB.toCharArray())
        val keyB2 = c.vault.dataKeyCopy()!!
        assertFalse(keyA.contentEquals(keyB2))
        c.vault.lock()
        try {
            c.vault.unlock(masterA.toCharArray())
        } catch (e: app.seguridad.crypto.CryptoFailure) {
            // A's master no longer opens B.
        }
        assertFalse(c.vault.state.value is VaultState.Unlocked)
        assertEquals(idB, c.activeVaultId)
        c.switchTo(PRIMARY_VAULT_ID)
        c.vault.unlock(masterA.toCharArray())
        assertTrue(c.vault.state.value is VaultState.Unlocked)
        assertTrue((c.vault.state.value as VaultState.Unlocked).entries.any { it.id == "x" && it.password == "de-a" })
        assertTrue(keyA.contentEquals(c.vault.dataKeyCopy()!!))
    }

    /**
     * Surface 3, the registry: a vaults.json with ids that are paths («../», «/»), in capitals, of 33 digits, repeated, or
     * more than 10, is read as the valid ids only (VaultRegistry.kt:150-160), and [VaultRegistry.dirOf] refuses anything
     * else, so no id names a folder outside files/vault/ and files/vaults/<32 hex>/.
     */
    @Test fun resistsAHostileRegistry() = runTest {
        val good = (0 until 12).map { "%032x".format(it) }
        val hostile = listOf("../vault", "/etc", "A".repeat(32), "0".repeat(33), "principal/../../x", good[0]) + good
        File(tmp.root, "vaults.json").writeText(
            "{\"vaults\":[" + hostile.joinToString(",") { "{\"id\":\"${it.replace("\\", "\\\\")}\"}" } + "],\"lastOpened\":\"../vault\"}",
        )
        val r = VaultRegistry(tmp.root)
        val ids = r.vaults.value.map { it.id }
        assertEquals(good.take(10), ids)
        assertEquals(good[0], r.lastOpened)
        for (id in hostile.take(5)) {
            try {
                r.dirOf(id)
                throw AssertionError("dirOf took $id")
            } catch (e: IllegalArgumentException) {
                // As it must.
            }
        }
        ids.forEach { assertTrue(r.dirOf(it).canonicalPath.startsWith(File(tmp.root, "vaults").canonicalPath + File.separator)) }
    }

    /** Surface 3: a vaults.json that is not JSON, or lists no valid id, is rebuilt from the folders: the first vault comes back. */
    @Test fun resistsADamagedRegistry() = runTest {
        File(tmp.root, "vault").mkdirs()
        File(tmp.root, "vault/vault.bin").writeText("{}")
        for (text in listOf("no es json", "{\"vaults\":[{\"id\":\"../x\"}]}", "{\"vaults\":[]}", "")) {
            File(tmp.root, "vaults.json").writeText(text)
            assertEquals(listOf(PRIMARY_VAULT_ID), VaultRegistry(tmp.root).vaults.value.map { it.id })
        }
    }
}
