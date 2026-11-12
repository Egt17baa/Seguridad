package app.seguridad.settings

import app.seguridad.AppContainer
import app.seguridad.SETTINGS_FILE
import app.seguridad.vault.EntryType
import app.seguridad.vault.PRIMARY_VAULT_ID
import app.seguridad.vault.VAULTS_FILE
import app.seguridad.vault.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 2.0, tarea 2: los ajustes de cada bóveda (`Settings.perVault`) y los de toda la app. */
class PerVaultSettingsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val folder = "content://com.android.externalstorage.documents/tree/primary%3ACopias"
    private val other = "0123456789abcdef0123456789abcdef"

    private fun store(file: File = File(tmp.root, SETTINGS_FILE)) = SettingsStore(file, Dispatchers.Unconfined, heapKiB = { 1L shl 30 })

    /** A settings.json as 1.4.3 writes it: the copies, A6 and the cost at the top, with the settings of the whole app. */
    private val settings143 = """
        {"lockAfter":"MIN_1","clipboard":"S15","kdf":"HIGH","theme":"LIGHT","noticeAccepted":true,
         "autoBackupUri":"$folder","autoBackupKeep":10,"lastAutoBackupError":"No se pudo hacer la copia automática.",
         "lastAutoBackupAt":1700000000000,"autoBackupCopies":["c1","c2"],"autoBackupCopiesFolder":"$folder",
         "autoBackupReplacePending":true,"autoBackupReplaceTargets":["c1"],"autoBackupReplaceTold":["x"],
         "clockHighWaterMs":1800000000000,"clipboardHistoryNoticeAccepted":true,"renameNoticePending":false,
         "importCopies":["i1"],"importCopiesFolder":"$folder"}
    """.trimIndent()

    @Test fun oldSettingsMoveToPrincipal() = runTest {
        File(tmp.root, SETTINGS_FILE).writeText(settings143)
        val s = store()
        s.load()
        val v = s.settings.value.vault(PRIMARY_VAULT_ID)
        assertEquals(folder, v.autoBackupUri)
        assertEquals(10, v.autoBackupKeep)
        assertEquals(1700000000000L, v.lastAutoBackupAt)
        assertEquals(listOf("c1", "c2"), v.autoBackupCopies)
        assertEquals(folder, v.autoBackupCopiesFolder)
        assertTrue("A6, as it was", v.autoBackupReplacePending)
        assertEquals(listOf("c1"), v.autoBackupReplaceTargets)
        assertEquals(listOf("x"), v.autoBackupReplaceTold)
        assertEquals(listOf("i1"), v.importCopies)
        assertEquals(KdfProfile.HIGH, v.kdf)
        // The settings of the whole app stay where they were.
        val g = s.settings.value
        assertEquals(LockAfter.MIN_1, g.lockAfter)
        assertEquals(ClipboardClear.S15, g.clipboard)
        assertEquals(ThemeMode.LIGHT, g.theme)
        assertTrue(g.noticeAccepted)
        // 2.0, ruling T2, I-1: the A11 mark is each vault's; 1.4.3's goes to the first.
        assertEquals(1800000000000L, v.clockHighWaterMs)
        assertTrue(g.clipboardHistoryNoticeAccepted)
        assertEquals(false, g.renameNoticePending)
        // Written once in the new shape: read again, the same; the old fields are gone from the top.
        s.update { it.copy(theme = ThemeMode.DARK) }
        val text = File(tmp.root, SETTINGS_FILE).readText()
        assertFalse(text.startsWith("{\"autoBackupUri"))
        assertFalse(Regex("^\\{[^{]*\"autoBackupUri\"").containsMatchIn(text))
        val again = store()
        again.load()
        assertEquals(v, again.settings.value.vault(PRIMARY_VAULT_ID))
        // A vault deleted later is not brought back by a second reading of fields that are no longer there.
        again.update { it.withoutVault(PRIMARY_VAULT_ID) }
        val third = store()
        third.load()
        assertNull(third.settings.value.perVault[PRIMARY_VAULT_ID])
    }

    @Test fun newVaultStartsWithoutBackup() = runTest {
        File(tmp.root, SETTINGS_FILE).writeText(settings143)
        val s = store()
        s.load()
        val fresh = s.settings.value.vault(other)
        assertNull(fresh.autoBackupUri)
        assertEquals(emptyList<String>(), fresh.autoBackupCopies)
        assertFalse(fresh.autoBackupReplacePending)
        assertEquals(emptyList<String>(), fresh.importCopies)
        assertEquals(KdfProfile.STANDARD, fresh.kdf)
        // A change to one vault leaves the other as it was.
        s.updateVault(other) { it.withFolderChosen("content://otra") }
        assertEquals("content://otra", s.settings.value.vault(other).autoBackupUri)
        assertEquals(folder, s.settings.value.vault(PRIMARY_VAULT_ID).autoBackupUri)
        assertEquals(listOf("c1", "c2"), s.settings.value.vault(PRIMARY_VAULT_ID).autoBackupCopies)
        // A new vault turns off only its own copies.
        assertEquals("content://otra", s.turnOffAutoBackupForNewVault(other))
        assertNull(s.settings.value.vault(other).autoBackupUri)
        assertEquals(folder, s.settings.value.vault(PRIMARY_VAULT_ID).autoBackupUri)
    }

    @Test fun noSecretInSettings() = runTest {
        val cache = tmp.newFolder("cache")
        val c = AppContainer(tmp.root, cache, appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-de-prueba-1".toCharArray())
        c.vault.rename("Nombre-inventado-uno")
        c.vault.upsert(VaultEntry("e1", EntryType.LOGIN, "Correo-inventado", username = "usuario-inventado", password = "secreto-inventado-123", createdAt = 0, updatedAt = 0))
        c.settings.updateVault(PRIMARY_VAULT_ID) { it.withFolderChosen(folder) }
        val id = c.createVault("Nombre-inventado-dos", "maestra-de-prueba-2".toCharArray())
        c.vault.upsert(VaultEntry("e2", EntryType.NOTE, "Nota-inventada", notes = "nota-secreta-inventada", createdAt = 0, updatedAt = 0))
        c.settings.updateVault(id) { it.withFolderChosen(folder) }
        val written = File(tmp.root, SETTINGS_FILE).readText() + File(tmp.root, VAULTS_FILE).readText()
        for (secret in listOf("Nombre-inventado", "Correo-inventado", "usuario-inventado", "secreto-inventado", "Nota-inventada", "nota-secreta", "maestra-de-prueba")) {
            assertFalse(secret, written.contains(secret))
        }
        assertTrue(written.contains(id))
    }
}
