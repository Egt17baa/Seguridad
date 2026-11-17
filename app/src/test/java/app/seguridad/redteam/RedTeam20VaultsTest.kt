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
     * RT-23 (H-2, truthfulness). «Importar como bóveda nueva» (AppContainer.kt:612-624) runs [VaultStore.importReplace],
     * whose [VaultCrypto.afterMasterUnlock] only re-seals a v4 copy (Vault.kt:532) or wraps its same key again (:534); only
     * [VaultCrypto.changeMaster] makes a new data key (Vault.kt:555-563). So the vault made from a copy of vault A has A's
     * data key, and so has each vault imported again from the same copy. The design says the opposite (spec :38 «cada
     * bóveda tiene su propia clave de datos, así que los trozos de dos bóvedas no se abren juntos» and :62 «la clave de un
     * archivo no abre los datos de otro»): here A's key opens B's data and the other way round.
     *
     * No way without root was found to use it (report, «H-2»): the files are private, the fingerprint keys are one per
     * vault and wrap only their vault's key, each attachment has its own key. The safe behaviour asserted is the design's;
     * if the Ruling corrects the design's sentence instead of the code, this test becomes a resists… test of what is true.
     */
    @Ignore("RT-23: abierto, ver redteam-report.md")
    @Test fun rt23_aVaultImportedAsNewHasADataKeyOfItsOwn() = runTest {
        val c = container(backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create(masterA.toCharArray(), name = "Uno")
        c.vault.upsert(VaultEntry("x", EntryType.LOGIN, "Banco", password = "de-a", createdAt = 1, updatedAt = 1))
        val keyA = c.vault.dataKeyCopy()!!
        val copy = ByteArrayOutputStream().also { assertTrue(c.vault.exportBackup(it)) }.toByteArray()
        c.importAsNewVault(ByteArrayInputStream(copy), masterA.toCharArray(), "Copia")
        assertTrue(c.vault.state.value is VaultState.Unlocked)
        val keyB = c.vault.dataKeyCopy()!!
        assertFalse("The vault made from A's copy has A's data key", keyA.contentEquals(keyB))
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
