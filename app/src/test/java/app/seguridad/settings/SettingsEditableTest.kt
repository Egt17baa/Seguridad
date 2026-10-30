package app.seguridad.settings

import app.seguridad.vault.FolderGrant
import app.seguridad.vault.VaultState
import app.seguridad.vault.hasFolderGrant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audit 1.3.0, batch F3: COV2-1 (owner ruling) and the persisted-permission check of the backup folder (CADENAS-1). */
class SettingsEditableTest {
    @Test fun `COV2-1 the guarded settings change only with the vault open or with no vault`() {
        assertTrue(settingsEditable(VaultState.Unlocked(emptyList())))
        assertTrue("no vault: nothing to protect, and Argon2id also serves text and file encryption", settingsEditable(VaultState.NoVault))
        assertFalse(settingsEditable(VaultState.Locked))
        assertFalse("not known yet: closed", settingsEditable(VaultState.Loading))
    }

    private val tree = "content://com.android.externalstorage.documents/tree/primary%3ACopias"

    @Test fun `the backup folder is used only with a persisted read and write grant`() {
        assertTrue(hasFolderGrant(tree, listOf(FolderGrant(tree, read = true, write = true))))
        assertFalse("none at all (a settings.json written by someone else)", hasFolderGrant(tree, emptyList()))
        assertFalse(hasFolderGrant(tree, listOf(FolderGrant(tree, read = true, write = false))))
        assertFalse(hasFolderGrant(tree, listOf(FolderGrant(tree, read = false, write = true))))
        assertFalse("another folder's grant", hasFolderGrant("content://com.prueba.evil/tree/x", listOf(FolderGrant(tree, read = true, write = true))))
        assertFalse("not a content URI", hasFolderGrant("file:///data/user/0/x/files", listOf(FolderGrant("file:///data/user/0/x/files", read = true, write = true))))
    }
}
