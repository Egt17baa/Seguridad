package app.seguridad

import app.seguridad.vault.VaultState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppContainerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val cache by lazy { tmp.newFolder("cache") }

    private val folder = "content://com.android.externalstorage.documents/tree/primary%3ACopias"

    /**
     * vault.bin damaged and set aside, a new vault created: the automatic copies are turned off, so the rotation never
     * deletes the old vault's copies (the only good ones), and the folder's permission is given back.
     */
    @Test fun newVaultTurnsAutoBackupOffAndGivesTheFolderBack() = runTest {
        File(tmp.root, "vault").mkdirs()
        File(File(tmp.root, "vault"), "vault.bin").writeText("dañado")
        val released = mutableListOf<String>()
        val c = AppContainer(tmp.root, cache, appVersion = "test", releaseFolder = { released += it }, appScope = backgroundScope)
        c.settings.load()
        c.settings.update { it.copy(autoBackupUri = folder, lastAutoBackupAt = 5L) }
        c.vault.load()
        assertEquals(VaultState.NoVault, c.vault.state.value)
        c.vault.create("maestra-larga".toCharArray())
        assertTrue(c.vault.state.value is VaultState.Unlocked)
        assertNull(c.settings.settings.value.autoBackupUri)
        assertNull(c.settings.settings.value.lastAutoBackupAt)
        assertTrue(c.settings.settings.value.autoBackupOffForNewVault)
        assertEquals(listOf(folder), released)
    }

    @Test fun newVaultWithAutoBackupOffReleasesNothing() = runTest {
        val released = mutableListOf<String>()
        val c = AppContainer(tmp.root, cache, appVersion = "test", releaseFolder = { released += it }, appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-larga".toCharArray())
        assertFalse(c.settings.settings.value.autoBackupOffForNewVault)
        assertTrue(released.isEmpty())
    }

    @Test fun importingAVaultForgetsTheAutomaticCopiesMadeOfTheOldOne() = runTest {
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-larga".toCharArray())
        c.settings.update { it.copy(autoBackupUri = folder, autoBackupCopies = listOf("doc-1"), autoBackupCopiesFolder = folder) }
        val other = AppContainer(File(tmp.root, "otro").apply { mkdirs() }, cache, appVersion = "test", appScope = backgroundScope)
        other.settings.load()
        other.vault.load()
        other.vault.create("otra-maestra-larga".toCharArray())
        c.vault.importReplace(other.vault.exportText()!!, "otra-maestra-larga".toCharArray())
        // Copies stay on, to the same folder, but the old vault's are never rotated away.
        assertEquals(folder, c.settings.settings.value.autoBackupUri)
        assertTrue(c.settings.settings.value.autoBackupCopies.isEmpty())
        // A wrong password changes nothing, the list included.
        c.settings.update { it.copy(autoBackupCopies = listOf("doc-2"), autoBackupCopiesFolder = folder) }
        try {
            c.vault.importReplace(other.vault.exportText()!!, "mala".toCharArray())
        } catch (e: app.seguridad.crypto.CryptoFailure) {
            // expected
        }
        assertEquals(listOf("doc-2"), c.settings.settings.value.autoBackupCopies)
    }
}
