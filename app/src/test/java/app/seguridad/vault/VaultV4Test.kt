package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.core.writeTextAtomically
import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * 2.0, tarea 1: el formato v4 (`VaultData` dentro del blob cifrado) y el paso de v3 a v4 con los archivos reales:
 * `vault-1.1.json` (v2, maestra `maestra-de-prueba`) y `vault-1.4.3.json` (v3 de la 1.4.3, maestra `maestra-de-prueba-143`;
 * sus entradas, en `seguridad/README.txt`).
 */
class VaultV4Test {
    @get:Rule val tmp = TemporaryFolder()

    private val kdf = KdfParams(1024, 1, 1)
    private val master11 = "maestra-de-prueba"
    private val master143 = "maestra-de-prueba-143"

    /** Cerca de la última maestra de vault-1.4.3.json (1800000000000). */
    private var clock = 1_800_000_000_000L + 3_600_000L

    private fun text(name: String) = javaClass.getResourceAsStream("/seguridad/$name")!!.use { it.readBytes() }.decodeToString()
    private fun file(name: String) = VaultCrypto.decodeFile(text(name))
    private fun dek(name: String, master: String) = VaultCrypto.unlock(file(name), master.toCharArray())

    private fun store(
        dir: File,
        beforeUpgrade: suspend () -> Boolean = { false },
        writeFile: (File, String) -> Unit = { f, t -> f.writeTextAtomically(t) },
    ) = VaultStore(
        File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { kdf },
        beforeUpgrade = beforeUpgrade, writeFile = writeFile,
    )

    /** Un teléfono con vault-1.4.3.json en su sitio, todavía cerrado. */
    private fun phone143(name: String = "p143"): File = File(tmp.root, name).apply {
        mkdirs()
        File(this, "vault.bin").writeText(text("vault-1.4.3.json"))
    }

    private fun disk(dir: File) = VaultCrypto.decodeFile(File(dir, "vault.bin").readText())
    private fun VaultStore.entries() = (state.value as VaultState.Unlocked).entries
    private fun note(id: String) = VaultEntry(id, EntryType.NOTE, "Nota $id", notes = "n $id", createdAt = 0, updatedAt = 0)

    private fun assertNewFieldsAreDefaults(e: VaultEntry) {
        assertNull(e.folderId)
        assertEquals(emptyList<CustomField>(), e.fields)
        assertEquals(NotesFormat.PLAIN, e.notesFormat)
        assertEquals(emptyList<EntryVersion>(), e.history)
        assertNull(e.deletedAt)
        assertNull(e.expiresAt)
        assertNull(e.rotateMonths)
        assertNull(e.lastUsedAt)
    }

    @Test fun v3FixtureOpensAsVaultData() {
        val v11 = VaultCrypto.read(file("vault-1.1.json"), dek("vault-1.1.json", master11))
        assertEquals(listOf("Banco", "Correo"), v11.entries.map { it.title }.sorted())
        assertEquals("clave-1.1", v11.entries.single { it.title == "Banco" }.password)
        v11.entries.forEach(::assertNewFieldsAreDefaults)
        assertEquals(VaultData(entries = v11.entries), v11)

        val v143 = VaultCrypto.read(file("vault-1.4.3.json"), dek("vault-1.4.3.json", master143))
        assertEquals(3, file("vault-1.4.3.json").version)
        assertEquals(listOf("e1", "e2", "e3", "e4"), v143.entries.map { it.id })
        val login = v143.entries[0]
        assertEquals(EntryType.LOGIN, login.type)
        assertEquals("clave-actual-143", login.password)
        assertEquals(listOf("clave-vieja-2", "clave-vieja-1"), login.passwordHistory.map { it.password })
        assertEquals(listOf("trabajo"), login.tags)
        assertTrue(login.favorite)
        assertEquals("texto de la nota", v143.entries[1].notes)
        assertEquals("JBSWY3DPEHPK3PXP", v143.entries[2].totp!!.secret)
        assertEquals(EntryType.SEED, v143.entries[3].type)
        v143.entries.forEach(::assertNewFieldsAreDefaults)
        assertEquals(VaultData(entries = v143.entries), v143)
    }

    @Test fun masterUnlockUpgradesToV4() {
        for ((name, master) in listOf("vault-1.1.json" to master11, "vault-1.4.3.json" to master143)) {
            val old = file(name)
            val key = dek(name, master)
            val before = VaultCrypto.read(old, key)
            val up = VaultCrypto.afterMasterUnlock(old, key, master.toCharArray(), clock, kdf)
            assertEquals(name, 4, up.version)
            assertEquals(name, clock, up.masterCheckedAt)
            assertEquals(name, old.revision + 1, up.revision)
            // The same data key: the fingerprint set up with it keeps working.
            assertArrayEquals(name, key, VaultCrypto.unlock(up, master.toCharArray()))
            val data = VaultCrypto.read(up, key)
            assertEquals(name, before.entries.map { it.id }, data.entries.map { it.id })
            assertEquals(name, 32, data.vaultTag.length)
            assertTrue(name, data.vaultTag.all { it in '0'..'9' || it in 'a'..'f' })
            for ((was, now) in before.entries.zip(data.entries)) {
                assertEquals(was.password, now.password)
                assertEquals(
                    "$name ${was.id}",
                    was.passwordHistory.map { it.changedAt to it.password },
                    now.history.map { it.savedAt to it.entry.password },
                )
                now.history.forEach {
                    assertTrue(it.entry.history.isEmpty())
                    assertTrue(it.entry.attachments.isEmpty())
                }
            }
        }
        // Another upgrade gets another tag.
        val a = VaultCrypto.afterMasterUnlock(file("vault-1.4.3.json"), dek("vault-1.4.3.json", master143), master143.toCharArray(), clock, kdf)
        val b = VaultCrypto.afterMasterUnlock(file("vault-1.4.3.json"), dek("vault-1.4.3.json", master143), master143.toCharArray(), clock, kdf)
        val key = dek("vault-1.4.3.json", master143)
        assertFalse(VaultCrypto.read(a, key).vaultTag == VaultCrypto.read(b, key).vaultTag)
        // A v4 is only re-sealed: it keeps its tag.
        val again = VaultCrypto.afterMasterUnlock(a, key, master143.toCharArray(), clock + 1, kdf)
        assertEquals(4, again.version)
        assertEquals(VaultCrypto.read(a, key).vaultTag, VaultCrypto.read(again, key).vaultTag)
        assertEquals(VaultCrypto.read(a, key).entries, VaultCrypto.read(again, key).entries)
    }

    @Test fun aNewVaultIsV4WithATag() {
        val (f, key) = VaultCrypto.create("otra-maestra".toCharArray(), clock, kdf)
        assertEquals(4, f.version)
        val data = VaultCrypto.read(f, key)
        assertEquals(emptyList<VaultEntry>(), data.entries)
        assertEquals(32, data.vaultTag.length)
    }

    @Test fun v4NeverTriedForUnknownVersion() {
        val (f, key) = VaultCrypto.create("otra-maestra".toCharArray(), clock, kdf)
        val noVersion = f.copy(version = VaultFile.VERSION_UNKNOWN)
        assertThrows(CryptoFailure::class.java) { VaultCrypto.unlock(noVersion, "otra-maestra".toCharArray()) }
        assertThrows(CryptoFailure::class.java) { VaultCrypto.read(noVersion, key) }
        assertNull(VaultCrypto.authenticated(noVersion, key))
    }

    @Test fun v4BindsVersionInAad() {
        val (f, key) = VaultCrypto.create("otra-maestra".toCharArray(), clock, kdf)
        val as3 = f.copy(version = VaultFile.VERSION_3)
        assertThrows(CryptoFailure::class.java) { VaultCrypto.unlock(as3, "otra-maestra".toCharArray()) }
        assertThrows(CryptoFailure::class.java) { VaultCrypto.read(as3, key) }
        // And a 3 does not pass for a 4.
        val v3as4 = file("vault-1.4.3.json").copy(version = VaultFile.VERSION)
        assertThrows(CryptoFailure::class.java) { VaultCrypto.unlock(v3as4, master143.toCharArray()) }
        assertThrows(CryptoFailure::class.java) { VaultCrypto.read(v3as4, dek("vault-1.4.3.json", master143)) }
    }

    @Test fun writeIsOnlyForV4() {
        val key = dek("vault-1.4.3.json", master143)
        assertThrows(IllegalStateException::class.java) { VaultCrypto.write(file("vault-1.4.3.json"), key, VaultData()) }
        assertThrows(IllegalStateException::class.java) { VaultCrypto.write(file("vault-1.1.json"), key, VaultData()) }
        val (f, k) = VaultCrypto.create("otra-maestra".toCharArray(), clock, kdf)
        val written = VaultCrypto.write(f, k, VaultData(entries = listOf(note("1"))))
        assertEquals(listOf("1"), VaultCrypto.read(written, k).entries.map { it.id })
    }

    @Test fun fingerprintRefusedBeforeUpgrade() = runTest {
        val checked = 1_800_000_000_000L
        for (old in listOf(file("vault-1.4.3.json"), file("vault-1.1.json"), file("vault-1.4.3.json").copy(version = VaultFile.VERSION_UNKNOWN))) {
            assertEquals(BiometricRefusal.UPGRADE, VaultCrypto.biometricRefusal(old, checked + 1_000))
            // Before the clock and the 7 days.
            assertEquals(BiometricRefusal.UPGRADE, VaultCrypto.biometricRefusal(old, checked - 1_000, highWater = checked))
            assertEquals(BiometricRefusal.UPGRADE, VaultCrypto.biometricRefusal(old, checked + MASTER_RECHECK_MS * 2))
        }
        val (v4, _) = VaultCrypto.create("otra-maestra".toCharArray(), checked, kdf)
        assertNull(VaultCrypto.biometricRefusal(v4, checked + 1_000))
        assertEquals(BiometricRefusal.MASTER_DUE, VaultCrypto.biometricRefusal(v4, checked + MASTER_RECHECK_MS))
        assertEquals(UPGRADE_NEEDS_MASTER, MasterRequired(BiometricRefusal.UPGRADE).message)
        assertEquals("Clavero 2.0 necesita la maestra una vez para pasar la bóveda al formato nuevo.", UPGRADE_NEEDS_MASTER)

        // The store refuses the fingerprint key without using it, and the file does not change.
        val dir = phone143()
        val s = store(dir).apply { load() }
        assertEquals(BiometricRefusal.UPGRADE, s.biometricRefusal)
        assertFalse(s.biometricAllowed)
        val key = dek("vault-1.4.3.json", master143)
        try {
            s.unlockWithKey(key)
            fail("expected MasterRequired")
        } catch (e: MasterRequired) {
            assertEquals(BiometricRefusal.UPGRADE, e.reason)
        }
        assertTrue("the key is wiped", key.all { it == 0.toByte() })
        assertFalse(s.state.value is VaultState.Unlocked)
        assertEquals(text("vault-1.4.3.json"), File(dir, "vault.bin").readText())
    }

    @Test fun upgradeInterruptedKeepsV3() = runTest {
        val dir = phone143()
        var fail = true
        val killed: (File, String) -> Unit = { f, t ->
            if (fail) {
                // The process dies half way through the temporary file: the vault file itself is never touched.
                File(f.path + ".tmp").writeText(t.take(t.length / 2))
                throw IOException("killed")
            }
            f.writeTextAtomically(t)
        }
        val s = store(dir, beforeUpgrade = { true }, writeFile = killed).apply { load() }
        s.unlock(master143.toCharArray())
        assertEquals(text("vault-1.4.3.json"), File(dir, "vault.bin").readText())
        assertTrue("still to do", s.pendingUpgrade.value)
        assertEquals(listOf("e1", "e2", "e3", "e4").sorted(), s.entries().map { it.id }.sorted())

        // A new start: the v3 of before opens with the master password.
        fail = false
        val again = store(dir, beforeUpgrade = { true }, writeFile = killed).apply { load() }
        assertEquals(3, disk(dir).version)
        again.unlock(master143.toCharArray())
        assertEquals(4, disk(dir).version)
        assertFalse(again.pendingUpgrade.value)
        assertEquals(listOf("Cartera de prueba", "Correo de ejemplo", "Foro de ejemplo", "Nota de prueba"), again.entries().map { it.title })
    }

    @Test fun upgradeWaitsForChoiceWithoutBackupFolder() = runTest {
        val dir = phone143()
        val s = store(dir).apply { load() }
        s.unlock(master143.toCharArray())
        assertTrue(s.state.value is VaultState.Unlocked)
        assertTrue(s.pendingUpgrade.value)
        assertEquals(4, s.entries().size)
        // The history the upgrade made is already what the open vault shows.
        assertEquals(2, s.entries().single { it.id == "e1" }.history.size)
        assertEquals(text("vault-1.4.3.json"), File(dir, "vault.bin").readText())
        // No save until the choice: neither an entry nor a new master password.
        val refused = assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { s.upsert(note("n")) } }
        assertEquals(UPGRADE_PENDING, refused.message)
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { s.delete("e2") } }
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { s.changeMaster("una-maestra-nueva-larga".toCharArray()) } }
        assertEquals(text("vault-1.4.3.json"), File(dir, "vault.bin").readText())
        // «Exportar una copia antes» exports the v3 on disk, which earlier versions open.
        val out = ByteArrayOutputStream()
        assertTrue(s.exportBackup(out))
        val zip = ZipInputStream(ByteArrayInputStream(out.toByteArray()))
        assertEquals(BACKUP_MARKER, zip.nextEntry!!.name)
        assertEquals("2\n", zip.readBytes().decodeToString())
        assertEquals(BACKUP_VAULT, zip.nextEntry!!.name)
        assertEquals(text("vault-1.4.3.json"), zip.readBytes().decodeToString())

        assertTrue(s.completeUpgrade())
        assertFalse(s.pendingUpgrade.value)
        assertEquals(4, disk(dir).version)
        assertFalse("only once", s.completeUpgrade())
        s.upsert(note("n"))
        assertEquals(5, VaultCrypto.read(disk(dir), dek("vault-1.4.3.json", master143)).entries.size)

        // A lock before the choice drops the upgrade: the file stays the v3.
        val other = phone143("otro")
        val t = store(other).apply { load() }
        t.unlock(master143.toCharArray())
        assertTrue(t.pendingUpgrade.value)
        t.lock()
        assertFalse(t.pendingUpgrade.value)
        assertFalse(t.completeUpgrade())
        assertEquals(text("vault-1.4.3.json"), File(other, "vault.bin").readText())
        // The next unlock with the master password asks again.
        t.unlock(master143.toCharArray())
        assertTrue(t.pendingUpgrade.value)
    }

    @Test fun upgradeCopiesFirstWithBackupFolder() = runTest {
        val dir = phone143()
        var s: VaultStore? = null
        val seen = mutableListOf<Int>()
        s = store(dir, beforeUpgrade = {
            seen += disk(dir).version
            // The copy is made from the open vault: the v3 still on disk, with the store free to export it.
            assertTrue(s!!.state.value is VaultState.Unlocked)
            assertTrue(s!!.exportBackup(ByteArrayOutputStream()))
            true
        }).apply { load() }
        s.unlock(master143.toCharArray())
        assertEquals(listOf(3), seen)
        assertEquals(4, disk(dir).version)
        assertFalse(s.pendingUpgrade.value)
        assertEquals(2, VaultCrypto.read(disk(dir), dek("vault-1.4.3.json", master143)).entries.single { it.id == "e1" }.history.size)

        // The copy failed (or there was none): nothing saved, the choice is still to make.
        val other = phone143("sin-copia")
        val t = store(other, beforeUpgrade = { false }).apply { load() }
        t.unlock(master143.toCharArray())
        assertEquals(text("vault-1.4.3.json"), File(other, "vault.bin").readText())
        assertTrue(t.pendingUpgrade.value)

        // A v4 is never copied first.
        var called = false
        val u = store(dir, beforeUpgrade = { called = true; true }).apply { load() }
        u.unlock(master143.toCharArray())
        assertFalse(called)
        assertFalse(u.pendingUpgrade.value)
    }

    @Test fun aV4VaultOpensWithTheFingerprint() = runTest {
        val dir = phone143()
        val s = store(dir, beforeUpgrade = { true }).apply { load() }
        s.unlock(master143.toCharArray())
        s.lock()
        assertNull(s.biometricRefusal)
        s.unlockWithKey(dek("vault-1.4.3.json", master143))
        assertEquals(4, s.entries().size)
    }
}
