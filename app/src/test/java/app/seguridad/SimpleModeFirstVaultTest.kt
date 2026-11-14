package app.seguridad

import app.seguridad.settings.guideDue
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 2.0, tarea 10: el modo sencillo se decide con la primera bóveda de una instalación nueva, por los caminos reales de la
 * app: «Crea tu bóveda» (`VaultStore.create`) lo enciende; «Importar copia» e «Importar como bóveda nueva» lo dejan
 * apagado (el diseño solo lo enciende «al crear la primera bóveda»); una segunda bóveda, o volver a crearla tras «He
 * olvidado la contraseña maestra», no lo cambian.
 */
class SimpleModeFirstVaultTest {
    @get:Rule val tmp = TemporaryFolder()

    private val master = "maestra-de-prueba-sencillo"

    private fun note(id: String) = VaultEntry(id, EntryType.NOTE, "Nota $id", notes = "n $id", createdAt = 0, updatedAt = 0)

    /** A new install, started as the app starts it (SeguridadApp): the settings, the first-start decision, the vault. */
    private suspend fun fresh(dir: File, scope: kotlinx.coroutines.CoroutineScope): AppContainer {
        val c = AppContainer(dir, File(dir, "cache").apply { mkdirs() }, appVersion = "test", appScope = scope)
        c.settings.load()
        c.decideRenameNotice()
        c.vault.load()
        return c
    }

    /** A copy (.sgbak) of a vault made in another folder. */
    private suspend fun aCopy(scope: kotlinx.coroutines.CoroutineScope): ByteArray {
        val other = fresh(tmp.newFolder("otro"), scope)
        other.vault.create(master.toCharArray(), name = "Origen")
        other.vault.upsert(note("n1"))
        return ByteArrayOutputStream().also { assertTrue(other.vault.exportBackup(it)) }.toByteArray()
    }

    @Test fun creatingTheFirstVaultTurnsItOn() = runTest {
        val c = fresh(tmp.newFolder("app"), backgroundScope)
        assertNull("Undecided, so off, until the first vault", c.settings.settings.value.simpleMode)
        c.vault.create(master.toCharArray())
        assertEquals(true, c.settings.settings.value.simpleMode)
        // What it keeps unencrypted (pendientes, row 8): one switch, and the guide only once it is seen (a false is not written).
        val written = File(tmp.root, "app/settings.json").readText()
        assertTrue(written, "\"simpleMode\":true" in written)
        assertTrue(written, "guideSeen" !in written)
        // A second vault, or the first one made again after «He olvidado la contraseña maestra», changes nothing.
        c.settings.update { it.copy(simpleMode = false) }
        c.createVault("Dos", "maestra-de-prueba-dos".toCharArray())
        assertEquals(false, c.settings.settings.value.simpleMode)
        c.vault.destroy()
        c.vault.create("maestra-de-prueba-tres".toCharArray())
        assertEquals(false, c.settings.settings.value.simpleMode)
    }

    /**
     * Ronda 1, m-3: the branch that looks at the files. Still undecided (a build of 2.0 under development, or a first
     * vault whose choice could not be saved) while a vault of the phone has a file: a second vault, created or imported,
     * is not the first, so nothing is decided.
     */
    @Test fun aVaultWithAFileMeansItIsNotTheFirst() = runTest {
        val copy = aCopy(backgroundScope)
        val c = fresh(tmp.newFolder("app"), backgroundScope)
        c.vault.create(master.toCharArray())
        c.settings.update { it.copy(simpleMode = null) }
        c.createVault("Dos", "maestra-de-prueba-dos".toCharArray())
        assertNull(c.settings.settings.value.simpleMode)
        c.importAsNewVault(ByteArrayInputStream(copy), master.toCharArray(), "Copia")
        assertNull(c.settings.settings.value.simpleMode)
    }

    /**
     * Ronda 1: a phone with a vault of 1.x and no settings.json (1.4.3 writes it at its first start, so only a write
     * that failed leaves none): an update all the same, by its vault: the short guide and «Modo sencillo» off.
     */
    @Test fun aVaultOf1xWithoutSettingsIsAnUpdate() = runTest {
        val dir = tmp.newFolder("app")
        File(dir, "vault").mkdirs()
        File(File(dir, "vault"), "vault.bin").writeText(javaClass.getResourceAsStream("/seguridad/vault-1.4.3.json")!!.use { it.readBytes() }.decodeToString())
        assertTrue(!File(dir, "settings.json").exists())
        val c = fresh(dir, backgroundScope)
        assertEquals(false, c.settings.settings.value.simpleMode)
        assertEquals(app.seguridad.settings.GUIDE_BEFORE_2_0, c.settings.settings.value.guideSeen)
        assertEquals(app.seguridad.settings.GuideDue.WHATS_NEW, c.settings.settings.value.guideDue)
        // Written, so the next start keeps it.
        val again = fresh(dir, backgroundScope)
        assertEquals(app.seguridad.settings.GuideDue.WHATS_NEW, again.settings.settings.value.guideDue)
        assertEquals(false, again.settings.settings.value.simpleMode)
    }

    @Test fun importingTheFirstVaultLeavesItOff() = runTest {
        val copy = aCopy(backgroundScope)
        val c = fresh(tmp.newFolder("app"), backgroundScope)
        c.vault.importReplace(ByteArrayInputStream(copy), master.toCharArray())
        assertTrue(c.vault.state.value is app.seguridad.vault.VaultState.Unlocked)
        assertEquals(false, c.settings.settings.value.simpleMode)
        // Then creating another vault does not turn it on.
        c.createVault("Dos", "maestra-de-prueba-dos".toCharArray())
        assertEquals(false, c.settings.settings.value.simpleMode)
    }

    @Test fun importingAsANewVaultFirstLeavesItOff() = runTest {
        val copy = aCopy(backgroundScope)
        val c = fresh(tmp.newFolder("app"), backgroundScope)
        c.importAsNewVault(ByteArrayInputStream(copy), master.toCharArray(), "Copia")
        assertEquals(false, c.settings.settings.value.simpleMode)
    }

    /** An update from 1.4.3 (its settings.json and its vault): off, and the short guide of what is new (owner, 14-11-2026). */
    @Test fun anUpdateKeepsItOff() = runTest {
        val dir = tmp.newFolder("app")
        File(dir, "settings.json").writeText(javaClass.getResourceAsStream("/seguridad/settings-1.4.3.json")!!.use { it.readBytes() }.decodeToString())
        File(dir, "vault").mkdirs()
        File(File(dir, "vault"), "vault.bin").writeText(javaClass.getResourceAsStream("/seguridad/vault-1.4.3.json")!!.use { it.readBytes() }.decodeToString())
        val c = fresh(dir, backgroundScope)
        assertEquals(false, c.settings.settings.value.simpleMode)
        assertEquals(app.seguridad.settings.GuideDue.WHATS_NEW, c.settings.settings.value.guideDue)
        assertTrue("\"guideSeen\":1" in File(dir, "settings.json").readText())
    }
}
