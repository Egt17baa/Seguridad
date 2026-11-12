package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 2.0, tarea 1: la copia `seguridad-backup` con la marca 3 (la de una bóveda v4) y sus topes. */
class BackupV3Test {
    @get:Rule val tmp = TemporaryFolder()

    private val master = "una-maestra-de-prueba"
    private val clock = 1_800_000_000_000L

    private fun store(name: String) = VaultStore(
        File(File(tmp.root, name).apply { mkdirs() }, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined,
        now = { clock }, kdf = { KdfParams(1024, 1, 1) },
    )

    private fun firstEntry(zip: ByteArray): Pair<String, String> = ZipInputStream(ByteArrayInputStream(zip)).use { z ->
        val e = z.nextEntry!!
        e.name to z.readBytes().decodeToString()
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z ->
            for ((name, bytes) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
    }.toByteArray()

    private fun readFails(zip: ByteArray): String? = try {
        BackupReader().read(ByteArrayInputStream(zip), File(tmp.root, "lectura"))
        null
    } catch (e: CryptoFailure) {
        e.message
    }

    private suspend fun v4Copy(): ByteArray {
        val s = store("origen").apply { load(); create(master.toCharArray()) }
        s.upsert(VaultEntry("1", EntryType.NOTE, "Nota", notes = "secreto", createdAt = 0, updatedAt = 0))
        val id = s.entries().single().id
        assertTrue(s.addAttachment(id, "hola".byteInputStream(), "hola.txt"))
        return ByteArrayOutputStream().also { assertTrue(s.exportBackup(it)) }.toByteArray()
    }

    private fun VaultStore.entries() = (state.value as VaultState.Unlocked).entries

    @Test fun v4VaultWritesMarker3() = runTest {
        assertEquals(BACKUP_MARKER to "3\n", firstEntry(v4Copy()))
        assertEquals(3, BACKUP_NEWEST)
    }

    @Test fun v3VaultWritesMarker2() {
        val v3 = javaClass.getResourceAsStream("/seguridad/vault-1.4.3.json")!!.use { it.readBytes() }.decodeToString()
        val out = ByteArrayOutputStream()
        writeBackup(out, v3, emptyMap())
        assertEquals(BACKUP_MARKER to "2\n", firstEntry(out.toByteArray()))
        // A text that is not a vault file at all keeps the marker of before too.
        val other = ByteArrayOutputStream()
        writeBackup(other, "{}", emptyMap())
        assertEquals(BACKUP_MARKER to "2\n", firstEntry(other.toByteArray()))
    }

    @Test fun marker3ReadsBack() = runTest {
        val copy = v4Copy()
        val report = checkBackup(ByteArrayInputStream(copy), master.toCharArray(), File(tmp.root, "comprobar"))
        assertEquals(3, report.version)
        assertEquals(emptyList<String>(), report.problems)
        assertEquals(1, report.attachmentsOk)
        val s = store("destino").apply { load() }
        s.importReplace(ByteArrayInputStream(copy), master.toCharArray())
        val e = s.entries().single()
        assertEquals("secreto", e.notes)
        assertEquals("hola", s.openAttachment(e.id, e.attachments.single().id)!!.decodeToString())
        assertEquals(4, VaultCrypto.decodeFile(File(tmp.root, "destino/vault.bin").readText()).version)
    }

    @Test fun marker4IsNewer() {
        assertEquals(BACKUP_NEWER, readFails(zipOf(BACKUP_MARKER to "4\n".toByteArray(), BACKUP_VAULT to "{}".toByteArray())))
        assertEquals(null, readFails(zipOf(BACKUP_MARKER to "3\n".toByteArray(), BACKUP_VAULT to "{}".toByteArray())))
    }

    @Test fun marker3EntryLimitIs32MiB() {
        assertEquals(32L * 1024 * 1024, BACKUP_V3_MAX_ENTRY_BYTES)
        assertEquals("Un archivo de la copia pasa de 32 MB", BACKUP_ENTRY_TOO_BIG_V3)
        val seven = ByteArray(7 * 1024 * 1024) { 'a'.code.toByte() }
        assertEquals(null, readFails(zipOf(BACKUP_MARKER to "3\n".toByteArray(), BACKUP_VAULT to seven)))
        val over = ByteArray((BACKUP_V3_MAX_ENTRY_BYTES + 1).toInt()) { 'a'.code.toByte() }
        assertEquals(BACKUP_ENTRY_TOO_BIG_V3, readFails(zipOf(BACKUP_MARKER to "3\n".toByteArray(), BACKUP_VAULT to over)))
        assertEquals(40 * 1024 * 1024, VAULT_IMPORT_MAX_BYTES)
    }

    @Test fun marker2EntryLimitStays6MB() {
        val over = ByteArray((BACKUP_MAX_ENTRY_BYTES + 1).toInt()) { 'a'.code.toByte() }
        assertEquals(BACKUP_ENTRY_TOO_BIG, readFails(zipOf(BACKUP_MARKER to "2\n".toByteArray(), BACKUP_VAULT to over)))
        assertEquals(BACKUP_ENTRY_TOO_BIG, readFails(zipOf(BACKUP_MARKER to "1\n".toByteArray(), BACKUP_VAULT to over)))
        assertEquals(6L * 1024 * 1024, BACKUP_MAX_ENTRY_BYTES)
        try {
            writeBackup(ByteArrayOutputStream(), "x".repeat((BACKUP_MAX_ENTRY_BYTES + 1).toInt()), emptyMap())
            fail("a copy with marker 2 over 6 MB is never written")
        } catch (e: CryptoFailure) {
            assertEquals(BACKUP_DOES_NOT_FIT, e.message)
        }
    }
}
