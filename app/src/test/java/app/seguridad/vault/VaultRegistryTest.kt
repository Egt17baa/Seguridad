package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 2.0, tarea 2: el registro de las bóvedas (`files/vaults.json`), sin nombres; el nombre va cifrado en `VaultData.name`. */
class VaultRegistryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val kdf = KdfParams(1024, 1, 1)

    private fun registry() = VaultRegistry(tmp.root, Dispatchers.Unconfined)

    private fun store(dir: File) = VaultStore(File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { kdf })

    @Test fun primaryStaysInOldFolder() = runTest {
        val r = registry()
        assertEquals(listOf(PRIMARY_VAULT_ID), r.vaults.value.map { it.id })
        assertEquals(File(tmp.root, "vault"), r.dirOf(PRIMARY_VAULT_ID))
        val other = r.create(TagColor.BLUE)
        assertEquals(File(File(tmp.root, "vaults"), other.id), r.dirOf(other.id))
        assertTrue("16 bytes in hex", Regex("[0-9a-f]{32}").matches(other.id))
        assertEquals(TagColor.BLUE, other.color)
        assertEquals(listOf(PRIMARY_VAULT_ID, other.id), r.vaults.value.map { it.id })
        // Read again from its file, as at the next start.
        assertEquals(r.vaults.value, registry().vaults.value)
    }

    @Test fun elevenIsRefused() = runTest {
        val r = registry()
        repeat(VaultLimits.VAULTS_MAX - 1) { r.create(TagColor.GREEN) }
        assertEquals(VaultLimits.VAULTS_MAX, r.vaults.value.size)
        assertThrows(VaultLimitExceeded::class.java) { kotlinx.coroutines.runBlocking { r.create(TagColor.RED) } }
        assertEquals(VaultLimits.VAULTS_MAX, r.vaults.value.size)
        assertEquals(VaultLimits.VAULTS_MAX, registry().vaults.value.size)
    }

    @Test fun rebuiltFromFoldersWhenRegistryDamaged() = runTest {
        val r = registry()
        File(tmp.root, "vault").mkdirs()
        val a = r.create(TagColor.RED)
        val b = r.create(TagColor.TEAL)
        r.dirOf(a.id).mkdirs()
        r.dirOf(b.id).mkdirs()
        r.setLastOpened(b.id)
        File(tmp.root, VAULTS_FILE).writeText("{ dañado")
        val rebuilt = registry()
        assertEquals(PRIMARY_VAULT_ID, rebuilt.vaults.value.first().id)
        assertEquals(setOf(PRIMARY_VAULT_ID, a.id, b.id), rebuilt.vaults.value.map { it.id }.toSet())
        assertEquals("Order follows the list", rebuilt.vaults.value.indices.toList(), rebuilt.vaults.value.map { it.order })
        // Missing too: the same, and a folder that is not a vault's id is not taken for one.
        File(tmp.root, VAULTS_FILE).delete()
        File(File(tmp.root, "vaults"), "no-es-un-id").mkdirs()
        assertEquals(setOf(PRIMARY_VAULT_ID, a.id, b.id), registry().vaults.value.map { it.id }.toSet())
    }

    @Test fun removeDeletesOnlyItsFolder() = runTest {
        val r = registry()
        val a = r.create(TagColor.RED)
        val b = r.create(TagColor.TEAL)
        listOf(r.dirOf(PRIMARY_VAULT_ID), r.dirOf(a.id), r.dirOf(b.id)).forEach { File(it.apply { mkdirs() }, "vault.bin").writeText("x") }
        r.remove(a.id)
        assertFalse(r.dirOf(a.id).exists())
        assertTrue(File(r.dirOf(PRIMARY_VAULT_ID), "vault.bin").exists())
        assertTrue(File(r.dirOf(b.id), "vault.bin").exists())
        assertEquals(listOf(PRIMARY_VAULT_ID, b.id), r.vaults.value.map { it.id })
        r.remove(PRIMARY_VAULT_ID)
        assertFalse(r.dirOf(PRIMARY_VAULT_ID).exists())
        assertEquals(listOf(b.id), r.vaults.value.map { it.id })
        // The last one is never removed from here.
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { r.remove(b.id) } }
        assertTrue(File(r.dirOf(b.id), "vault.bin").exists())
        // An id that is not in the list deletes nothing (never a path outside files/vaults/).
        assertThrows(IllegalArgumentException::class.java) { r.dirOf("../vault") }
    }

    @Test fun reorderAndColorAndLastOpened() = runTest {
        val r = registry()
        val a = r.create(TagColor.RED)
        val b = r.create(TagColor.TEAL)
        r.reorder(listOf(b.id, PRIMARY_VAULT_ID, a.id))
        assertEquals(listOf(b.id, PRIMARY_VAULT_ID, a.id), r.vaults.value.map { it.id })
        assertEquals(listOf(0, 1, 2), r.vaults.value.map { it.order })
        r.setColor(a.id, TagColor.PURPLE)
        assertEquals(TagColor.PURPLE, r.vaults.value.first { it.id == a.id }.color)
        r.setLastOpened(a.id)
        val again = registry()
        assertEquals(a.id, again.lastOpened)
        assertEquals(listOf(b.id, PRIMARY_VAULT_ID, a.id), again.vaults.value.map { it.id })
        // A list that is not a reordering of the vaults changes nothing.
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { r.reorder(listOf(a.id)) } }
    }

    @Test fun registryHasNoNames() = runTest {
        val r = registry()
        val other = r.create(TagColor.ORANGE)
        val s = store(r.dirOf(other.id))
        s.load()
        s.create("maestra-de-prueba-2".toCharArray())
        assertTrue(s.rename("Trabajo inventado"))
        assertEquals("Trabajo inventado", (s.state.value as VaultState.Unlocked).data.name)
        r.setLastOpened(other.id)
        r.setColor(other.id, TagColor.GREEN)
        val registryText = File(tmp.root, VAULTS_FILE).readText()
        assertFalse(registryText.contains("Trabajo"))
        val vaultText = File(r.dirOf(other.id), "vault.bin").readText()
        assertFalse("Encrypted, never in clear", vaultText.contains("Trabajo"))
        // It is in the vault: read back with its master password.
        val reopened = store(r.dirOf(other.id))
        reopened.load()
        reopened.unlock("maestra-de-prueba-2".toCharArray())
        assertEquals("Trabajo inventado", (reopened.state.value as VaultState.Unlocked).data.name)
        // Too long a name is refused; nothing changes.
        assertThrows(VaultLimitExceeded::class.java) { kotlinx.coroutines.runBlocking { reopened.rename("x".repeat(VaultLimits.VAULT_NAME_MAX + 1)) } }
        assertEquals("Trabajo inventado", (reopened.state.value as VaultState.Unlocked).data.name)
    }

    @Test fun displayNameBeforeAndAfterUnlock() = runTest {
        val r = registry()
        val other = r.create(TagColor.ORANGE)
        val s = store(r.dirOf(other.id))
        s.load()
        s.create("maestra-de-prueba-2".toCharArray(), name = "Casa inventada")
        val order = r.vaults.value.indexOfFirst { it.id == other.id }
        assertEquals("Casa inventada", displayName((s.state.value as VaultState.Unlocked).data, order))
        s.lock()
        assertEquals("Bóveda 2", displayName(null, order))
        // Without a name (a vault from before 2.0), and a name that only draws nothing.
        assertEquals("Bóveda 1", displayName(VaultData(), 0))
        assertEquals("Bóveda 3", displayName(VaultData(name = " ​ "), 2))
        // Shown as plain text: no controls nor direction marks.
        assertEquals("Casa", displayName(VaultData(name = "Ca‮sa"), 0))
    }

    @Test fun perVaultNamesKeepThePrimaryOnes() {
        assertEquals("seguridad_vault_biometric", perVaultName("seguridad_vault_biometric", PRIMARY_VAULT_ID))
        val id = "0123456789abcdef0123456789abcdef"
        assertEquals("seguridad_vault_biometric_$id", perVaultName("seguridad_vault_biometric", id))
        assertNotEquals(perVaultName("vault_biometric", id), perVaultName("vault_biometric", PRIMARY_VAULT_ID))
    }

    /** The code of [path] under the repository, without comment lines. */
    private fun code(path: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, path)
            if (f.isFile) return f.readLines().filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }.joinToString("\n")
            dir = dir.parentFile
        }
        error("$path not found")
    }

    /** Ruling 2: one Keystore key and one preferences file per vault; the first keeps those of before (AppNameTest). */
    @Test fun biometricKeysArePerVault() {
        val src = code("app/src/main/java/app/seguridad/android/BiometricVault.kt")
        assertTrue("perVaultName(ALIAS, vaultId)" in src)
        assertTrue("perVaultName(PREFS, vaultId)" in src)
        for (direct in listOf("getKey(ALIAS", "Builder(ALIAS", "deleteEntry(ALIAS")) {
            assertFalse(direct, direct in src)
        }
        for (signature in listOf(
            "fun enabled(context: Context, vaultId: String)",
            "fun keyLevel(context: Context, vaultId: String)",
            "suspend fun disable(context: Context, vaultId: String)",
            "fun enable(activity: FragmentActivity, vaultId: String, dataKey: ByteArray",
            "fun unlock(activity: FragmentActivity, vaultId: String, onKey",
        )) {
            assertTrue(signature, signature in src)
        }
        // Every screen passes the id of the vault it shows; a deleted vault's key goes with it.
        val screen = code("app/src/main/java/app/seguridad/ui/screens/VaultScreen.kt")
        assertTrue("BiometricVault.enable(activity, store.id, key)" in screen)
        assertTrue("BiometricVault.unlock(activity, store.id, onKey" in screen)
        assertFalse(Regex("""BiometricVault\.(disable|enabled)\(context\)""").containsMatchIn(screen + code("app/src/main/java/app/seguridad/ui/screens/VaultBackup.kt")))
        assertTrue("forgetBiometric = { id -> BiometricVault.disable(this, id) }" in code("app/src/main/java/app/seguridad/SeguridadApp.kt"))
    }

    /** Abogado Digital O-1, O-2 and R-9: what deleting and importing as a new vault do and do not delete, on view. */
    @Test fun vaultTextsSayWhatStays() {
        val warning = vaultDeleteWarning("Trabajo")
        assertTrue(warning.contains("Las copias ya hechas"))
        assertTrue(warning.contains("no se borran"))
        assertTrue(VAULT_IMPORT_NEW_SHORT.contains("No cambia ni borra las bóvedas que ya tienes"))
        assertTrue(VAULTS_MORE.contains("Este móvil guarda sin cifrar cuántas bóvedas hay, su color y su orden"))
        val all = listOf(VAULTS_SHORT, VAULTS_MORE, VAULT_NEW_SHORT, VAULT_IMPORT_NEW_SHORT, warning, VAULT_DELETED, VAULT_DELETE_LAST, VAULT_DELETE_OPEN_FIRST)
            .joinToString(" ").lowercase()
        for (never in listOf("irrecuperable", "borrado seguro", "ocult", "escond", "secreta", "nadie sabrá")) assertFalse(never, all.contains(never))
    }
}
