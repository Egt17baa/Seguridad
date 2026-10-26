package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
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
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/** The `seguridad-backup` v1 copy (.sgbak): the vault file plus its encrypted attachments, all or nothing. */
class BackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private val master = "maestra-de-prueba"

    /** Each phone in its own folder, so each has its own att/. */
    private fun phone(name: String) = File(tmp.root, name).apply { mkdirs() }

    private fun store(dir: File) =
        VaultStore(File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { 7_000L }, kdf = { KdfParams(1024, 1, 1) })

    private fun note(id: String, title: String = "Nota $id") = VaultEntry(id, EntryType.NOTE, title, notes = "secreto", createdAt = 0, updatedAt = 0)

    private fun VaultStore.entries() = (state.value as VaultState.Unlocked).entries

    private fun bytes(n: Int, seed: Int = n) = Random(seed).nextBytes(n)

    /** Phone A: a vault with two entries and three attachments (one entry with two). */
    private suspend fun phoneA(): VaultStore = store(phone("a")).apply {
        load()
        create(master.toCharArray())
        upsert(note("1", "Pasaporte"))
        upsert(note("2", "Contrato"))
        assertTrue(addAttachment("1", ByteArrayInputStream(bytes(70_000, 1)), "pasaporte.jpg"))
        assertTrue(addAttachment("1", ByteArrayInputStream(bytes(10, 2)), "reverso.jpg"))
        assertTrue(addAttachment("2", ByteArrayInputStream(bytes(0, 3)), "vacío.txt"))
    }

    private suspend fun VaultStore.backup(): ByteArray = ByteArrayOutputStream().also { assertTrue(exportBackup(it)) }.toByteArray()

    private fun entriesOf(zip: ByteArray): List<Pair<String, ByteArray>> {
        val out = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                out += e.name to z.readBytes()
            }
        }
        return out
    }

    private fun zipOf(entries: List<Pair<String, ByteArray>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    /** ZipOutputStream refuses duplicate names: write a same-length stand-in and patch every copy of it. */
    private fun patchName(zip: ByteArray, from: String, to: String): ByteArray {
        require(from.length == to.length)
        val f = from.toByteArray()
        val t = to.toByteArray()
        val out = zip.copyOf()
        var i = 0
        while (i <= out.size - f.size) {
            if ((f.indices).all { out[i + it] == f[it] }) t.copyInto(out, i)
            i++
        }
        return out
    }

    private val marker = "seguridad-backup" to "1\n".toByteArray()

    /** Every file under [dir] (relative path to bytes), to prove nothing changed. */
    private fun snapshot(dir: File): Map<String, List<Byte>> =
        dir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dir).path to it.readBytes().toList() }

    private suspend fun assertImportFails(s: VaultStore, zip: ByteArray, message: String? = null, pw: String = master) {
        try {
            s.importReplace(ByteArrayInputStream(zip), pw.toCharArray())
            fail("Debía fallar")
        } catch (e: CryptoFailure) {
            if (message != null) assertEquals(message, e.message)
        }
    }

    private fun readFails(zip: ByteArray, tempDir: File) {
        try {
            BackupReader().read(ByteArrayInputStream(zip), tempDir)
            fail("Debía fallar")
        } catch (e: CryptoFailure) {
            // expected
        }
        assertTrue("No deja nada en el directorio temporal", tempDir.listFiles().isNullOrEmpty())
    }

    @Test fun roundTripWithAttachments() = runTest {
        val a = phoneA()
        val zip = a.backup()
        // Phone B starts with nothing: the "Crea tu bóveda" screen.
        val b = store(phone("b")).apply { load() }
        assertEquals(VaultState.NoVault, b.state.value)
        b.importReplace(ByteArrayInputStream(zip), master.toCharArray())
        assertEquals(a.entries(), b.entries())
        assertArrayEquals(bytes(70_000, 1), b.openAttachment("1", a.entries().single { it.id == "1" }.attachments[0].id))
        assertArrayEquals(bytes(10, 2), b.openAttachment("1", a.entries().single { it.id == "1" }.attachments[1].id))
        assertArrayEquals(ByteArray(0), b.openAttachment("2", a.entries().single { it.id == "2" }.attachments[0].id))
        // Only the attachment files and the vault are left: no temporary folder.
        assertEquals(setOf("att", "vault.bin"), phone("b").list()!!.toSet())
        assertEquals(3, File(phone("b"), "att").list()!!.size)
        // And after restarting the app.
        val again = store(phone("b")).apply { load(); unlock(master.toCharArray()) }
        assertArrayEquals(bytes(70_000, 1), again.openAttachment("1", again.entries().single { it.id == "1" }.attachments[0].id))
    }

    @Test fun importReplacesAnExistingVaultAndCollectsItsOldAttachments() = runTest {
        val zip = phoneA().backup()
        val b = store(phone("b")).apply { load(); create("otra-maestra".toCharArray()); upsert(note("x")) }
        assertTrue(b.addAttachment("x", ByteArrayInputStream(bytes(500)), "viejo.bin"))
        val old = b.entries().single().attachments.single().id
        b.importReplace(ByteArrayInputStream(zip), master.toCharArray())
        assertEquals(listOf("Contrato", "Pasaporte"), b.entries().map { it.title })
        assertFalse(File(phone("b"), "att/$old.bin").exists())
        assertEquals(3, File(phone("b"), "att").list()!!.size)
    }

    @Test fun reimportingItsOwnBackupKeepsEverything() = runTest {
        val a = phoneA()
        val zip = a.backup()
        a.upsert(note("3", "Después"))
        a.importReplace(ByteArrayInputStream(zip), master.toCharArray())
        assertEquals(listOf("Contrato", "Pasaporte"), a.entries().map { it.title })
        for (e in a.entries()) for (ref in e.attachments) a.openAttachment(e.id, ref.id)
    }

    @Test fun exportWhileLockedWritesNothing() = runTest {
        // Locked, the entries (which attachments go, and their SHA-256) cannot be read: no unchecked copy is made.
        val a = phoneA()
        a.lock()
        val out = ByteArrayOutputStream()
        assertFalse(a.exportBackup(out))
        assertEquals(0, out.size())
        a.unlock(master.toCharArray())
        val zip = a.backup()
        assertEquals(3, entriesOf(zip).count { it.first.startsWith("att/") })
    }

    @Test fun exportRefusesADamagedLocalAttachment() = runTest {
        val a = phoneA()
        val ref = a.entries().single { it.id == "2" }.attachments.single()
        File(phone("a"), "att/${ref.id}.bin").writeBytes(bytes(40))
        try {
            a.backup()
            fail("Debía fallar")
        } catch (e: CryptoFailure) {
            assertEquals("El adjunto «vacío.txt» falta o está dañado: quítalo de su entrada para poder hacer la copia", e.message)
        }
    }

    @Test fun nothingToExportWithoutAVault() = runTest {
        val s = store(phone("a")).apply { load() }
        assertFalse(s.exportBackup(ByteArrayOutputStream()))
    }

    @Test fun jsonExportsStillImport() = runTest {
        // CriptoPanel (1.9.2, 1.9.3 and 1.10.0) and Seguridad 1.0.2 exports, as text and as a stream.
        val fixtures = listOf("/criptopanel/vault-v1.json", "/criptopanel/vault-noversion.json", "/criptopanel/vault-v2.json", "/seguridad/export-1.0.2.json")
        for (path in fixtures) for (asStream in listOf(false, true)) {
            val text = javaClass.getResource(path)!!.readText()
            val s = store(phone("j${path.hashCode()}$asStream")).apply { load(); create("otra-maestra".toCharArray()) }
            if (asStream) s.importReplace(ByteArrayInputStream(text.toByteArray()), master.toCharArray()) else s.importReplace(text, master.toCharArray())
            val entries = s.entries()
            assertEquals(path, 2, entries.size)
            assertEquals("JBSWY3DPEHPK3PXP", entries.single { it.type == EntryType.TOTP }.totp!!.secret)
            assertTrue(entries.all { it.attachments.isEmpty() })
        }
        val s = store(phone("seg")).apply { load() }
        s.importReplace(ByteArrayInputStream(javaClass.getResource("/seguridad/export-1.0.2.json")!!.readBytes()), master.toCharArray())
        assertEquals("clave-1.0.2", s.entries().single { it.title == "Banco" }.password)
    }

    @Test fun jsonWithAttachmentsIsRejected() = runTest {
        val a = phoneA()
        val json = a.exportText()!!
        val b = store(phone("b")).apply { load(); create("otra-maestra".toCharArray()); upsert(note("x")) }
        val before = snapshot(phone("b"))
        for (stream in listOf(false, true)) {
            try {
                if (stream) b.importReplace(ByteArrayInputStream(json.toByteArray()), master.toCharArray()) else b.importReplace(json, master.toCharArray())
                fail("Debía fallar")
            } catch (e: CryptoFailure) {
                assertEquals("Esta copia tiene adjuntos: importa el archivo .sgbak", e.message)
            }
        }
        assertEquals(before, snapshot(phone("b")))
        assertEquals(listOf("x"), b.entries().map { it.id })
    }

    @Test fun hugeJsonIsRefusedWithoutReadingItAll() = runTest {
        val b = store(phone("b")).apply { load() }
        val huge = object : java.io.InputStream() {
            var given = 0L
            override fun read(): Int = if (given++ < 100L * 1024 * 1024) ' '.code else -1
        }
        try {
            b.importReplace(huge, master.toCharArray())
            fail("Debía fallar")
        } catch (e: CryptoFailure) {
            // expected
        }
        assertTrue(huge.given <= 20L * 1024 * 1024 + 8192 + 64)
    }

    @Test fun missingAttachmentRejectsWholeImport() = runTest {
        val a = phoneA()
        val ref = a.entries().single { it.id == "1" }.attachments[1]
        val zip = zipOf(entriesOf(a.backup()).filterNot { it.first == "att/${ref.id}.bin" })
        val b = store(phone("b")).apply { load(); create("otra-maestra".toCharArray()); upsert(note("x")) }
        assertTrue(b.addAttachment("x", ByteArrayInputStream(bytes(300)), "mío.bin"))
        val before = snapshot(phone("b"))
        assertImportFails(b, zip, "La copia está incompleta o dañada: falta o no coincide el adjunto «reverso.jpg»")
        assertEquals(before, snapshot(phone("b")))
        assertEquals(listOf("x"), b.entries().map { it.id })
    }

    @Test fun tamperedAttachmentRejectsWholeImport() = runTest {
        val a = phoneA()
        val ref = a.entries().single { it.id == "1" }.attachments[0]
        val name = "att/${ref.id}.bin"
        val original = entriesOf(a.backup())
        val flipped = original.map { (n, d) -> n to if (n == name) d.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() } else d }
        val cut = original.map { (n, d) -> n to if (n == name) d.copyOf(d.size - 1) else d }
        // The file of another attachment under this one's name.
        val other = "att/${a.entries().single { it.id == "1" }.attachments[1].id}.bin"
        val swapped = original.map { (n, d) -> n to if (n == name) original.single { it.first == other }.second else d }
        val b = store(phone("b")).apply { load(); create("otra-maestra".toCharArray()) }
        val before = snapshot(phone("b"))
        for (bad in listOf(flipped, cut, swapped)) {
            assertImportFails(b, zipOf(bad), "La copia está incompleta o dañada: falta o no coincide el adjunto «pasaporte.jpg»")
            assertEquals(before, snapshot(phone("b")))
        }
    }

    @Test fun zipSlipNamesIgnoredOrRejected() = runTest {
        val a = phoneA()
        val good = entriesOf(a.backup())
        val evil = listOf(
            "../evil.bin", "../../evil.bin", "/tmp/evil.bin", "att/../../evil.bin", "att/../vault.bin",
            "att/" + "A".repeat(32) + ".bin", "att/" + "0".repeat(31) + ".bin", "att/" + "0".repeat(32) + ".bin/../x",
            "att\\..\\evil.bin", "att/" + "0".repeat(32) + ".bin.tmp", "./vault.json", "VAULT.JSON", "att/", "att/sub/" + "0".repeat(32) + ".bin",
        ).map { it to "evil".toByteArray() }
        val zip = zipOf(good.take(2) + evil + good.drop(2))
        val work = phone("work")
        val temp = File(work, "temp")
        val contents = BackupReader().read(ByteArrayInputStream(zip), temp)
        val ids = good.drop(2).map { it.first.removePrefix("att/").removeSuffix(".bin") }.toSet()
        assertEquals(ids, contents.attachments.keys)
        assertEquals(ids.map { "$it.bin" }.toSet(), temp.list()!!.toSet())
        assertEquals(setOf("temp"), work.list()!!.toSet())
        assertFalse(File(tmp.root, "evil.bin").exists())
        // The whole import still works, and nothing lands outside the vault folder.
        val b = store(phone("b")).apply { load() }
        b.importReplace(ByteArrayInputStream(zip), master.toCharArray())
        assertEquals(2, b.entries().size)
        assertEquals(setOf("att", "vault.bin"), phone("b").list()!!.toSet())
        assertEquals(ids.map { "$it.bin" }.toSet(), File(phone("b"), "att").list()!!.toSet())
        assertFalse(tmp.root.walkTopDown().any { it.name == "evil.bin" })
    }

    @Test fun duplicateNamesRejected() = runTest {
        val good = entriesOf(phoneA().backup())
        val att = good[2]
        val stand = "att/" + "f".repeat(32) + ".bix"
        val dupAtt = patchName(zipOf(good + (stand to att.second)), stand, att.first)
        val dupJson = patchName(zipOf(good.take(2) + ("vault.jsoX" to good[1].second) + good.drop(2)), "vault.jsoX", "vault.json")
        val dupMarker = patchName(zipOf(good + ("seguridad-backuX" to "1\n".toByteArray())), "seguridad-backuX", "seguridad-backup")
        for (zip in listOf(dupAtt, dupJson, dupMarker)) readFails(zip, File(phone("work"), "temp"))
        val b = store(phone("b")).apply { load(); create("otra-maestra".toCharArray()) }
        val before = snapshot(phone("b"))
        assertImportFails(b, dupAtt)
        assertEquals(before, snapshot(phone("b")))
    }

    @Test fun oversizedEntryRejected() = runTest {
        val good = entriesOf(phoneA().backup())
        val temp = File(phone("work"), "temp")
        val sixMb = 6 * 1024 * 1024
        // Zeros compress to almost nothing: the limit is on the bytes really read, not on what the header says.
        readFails(zipOf(good + ("att/" + "e".repeat(32) + ".bin" to ByteArray(sixMb + 1))), temp)
        readFails(zipOf(listOf(marker, "vault.json" to ByteArray(sixMb + 1))), temp)
        readFails(zipOf(good + ("ignorado" to ByteArray(sixMb + 1))), temp)
        // 6 MB exactly is fine.
        BackupReader().read(ByteArrayInputStream(zipOf(good + ("att/" + "e".repeat(32) + ".bin" to ByteArray(sixMb)))), temp)
        temp.deleteRecursively()
        // More than 100 MB in all, in entries of 6 MB.
        val many = (0 until 17).map { "att/" + "%032x".format(it) + ".bin" to ByteArray(sixMb) }
        readFails(zipOf(good.take(2) + many), temp)
        val b = store(phone("b")).apply { load(); create("otra-maestra".toCharArray()) }
        val before = snapshot(phone("b"))
        assertImportFails(b, zipOf(good.take(2) + many))
        assertEquals(before, snapshot(phone("b")))
    }

    @Test fun tooManyEntriesRejected() = runTest {
        val good = entriesOf(phoneA().backup())
        val temp = File(phone("work"), "temp")
        val filler = { n: Int -> (0 until n).map { "relleno/$it" to ByteArray(0) } }
        // 2 000 entries in all are fine; 2 001 are not.
        BackupReader().read(ByteArrayInputStream(zipOf(good + filler(2000 - good.size))), temp)
        temp.deleteRecursively()
        readFails(zipOf(good + filler(2001 - good.size)), temp)
    }

    @Test fun notABackupOrNewerVersionRejected() = runTest {
        val good = entriesOf(phoneA().backup())
        val temp = File(phone("work"), "temp")
        readFails(zipOf(listOf("seguridad-backup" to "2\n".toByteArray()) + good.drop(1)), temp)
        readFails(zipOf(listOf("seguridad-backup" to "1".toByteArray()) + good.drop(1)), temp)
        readFails(zipOf(good.drop(1)), temp) // No marker.
        readFails(zipOf(listOf(good[1], good[0]) + good.drop(2)), temp) // Marker not first.
        readFails(zipOf(listOf(marker) + good.drop(2)), temp) // No vault.json.
        readFails(good.first().second, temp) // Not a ZIP.
        val whole = zipOf(good)
        readFails(whole.copyOf(whole.size / 2), temp) // Cut.
    }

    @Test fun failedImportLeavesExistingVaultUntouched() = runTest {
        val a = phoneA()
        // B already has A's attachments (it imported a copy of A before) and one of its own.
        val b = store(phone("b")).apply { load() }
        b.importReplace(ByteArrayInputStream(a.backup()), master.toCharArray())
        b.upsert(note("x", "Solo en B"))
        assertTrue(b.addAttachment("x", ByteArrayInputStream(bytes(900)), "b.bin"))
        // B's file of one shared attachment differs from the copy's (damaged here), so putting it back is visible.
        val shared = b.entries().single { it.id == "1" }.attachments[0]
        File(phone("b"), "att/${shared.id}.bin").writeBytes(bytes(77))
        // A has a new attachment since: the next copy brings a file B does not have.
        assertTrue(a.addAttachment("2", ByteArrayInputStream(bytes(4_000, 9)), "nuevo.pdf"))
        val zip = a.backup()
        val before = snapshot(phone("b"))
        val entries = b.entries()
        assertImportFails(b, zip, pw = "contraseña-mala")
        assertImportFails(b, zipOf(entriesOf(zip).dropLast(1)))
        assertImportFails(b, zip.copyOf(zip.size / 2))
        assertEquals(before, snapshot(phone("b")))
        // Saving vault.bin fails after the attachments were moved in: they are taken back out, the old ones restored.
        val broken = File(phone("b"), "vault.bin.tmp").apply { mkdir() }
        try {
            b.importReplace(ByteArrayInputStream(zip), master.toCharArray())
            fail("Debía fallar")
        } catch (e: java.io.IOException) {
            // expected
        }
        broken.delete()
        assertEquals(before, snapshot(phone("b")))
        assertEquals(entries, b.entries())
        for (e in b.entries()) for (ref in e.attachments) if (ref.id != shared.id) b.openAttachment(e.id, ref.id)
        assertEquals(setOf("att", "vault.bin"), phone("b").list()!!.toSet())
        // And the same copy imports once saving works again.
        b.importReplace(ByteArrayInputStream(zip), master.toCharArray())
        assertArrayEquals(bytes(70_000, 1), b.openAttachment("1", shared.id))
        assertEquals(4, File(phone("b"), "att").list()!!.size)
    }

    @Test fun importLeftoversAreRemovedOnLoadAndNothingElse() = runTest {
        val a = phoneA()
        val dir = phone("a")
        val before = snapshot(dir)
        // What an import killed halfway leaves: import-<16 hex>/ with the copy's files and the displaced ones.
        File(dir, "import-0123456789abcdef/prev").mkdirs()
        File(dir, "import-0123456789abcdef/vault.json").writeText("{}")
        File(dir, "import-0123456789abcdef/prev/x.bin").writeBytes(bytes(10))
        File(dir, "import-fedcba9876543210").writeBytes(bytes(5)) // A file with the name, too.
        // Near misses: not ours, never touched.
        val kept = listOf("import-0123456789ABCDEF", "import-0123456789abcde", "import-0123456789abcdef0", "ximport-0123456789abcdef", "import-")
        kept.forEach { File(File(dir, it).apply { mkdirs() }, "f").writeBytes(bytes(3)) }

        val again = store(dir).apply { load() }
        assertEquals(VaultState.Locked, again.state.value)
        assertFalse(File(dir, "import-0123456789abcdef").exists())
        assertFalse(File(dir, "import-fedcba9876543210").exists())
        kept.forEach { assertTrue(it, File(dir, "$it/f").isFile) }
        kept.forEach { File(dir, it).deleteRecursively() }
        // The vault and its attachments are exactly as they were, and still open.
        assertEquals(before, snapshot(dir))
        again.unlock(master.toCharArray())
        for (e in again.entries()) for (ref in e.attachments) again.openAttachment(e.id, ref.id)
    }

    @Test fun formatPinned() = runTest {
        val a = phoneA()
        val zip = a.backup()
        val entries = entriesOf(zip)
        val ids = a.entries().flatMap { it.attachments }.map { it.id }.sorted()
        assertEquals(listOf("seguridad-backup", "vault.json") + ids.map { "att/$it.bin" }, entries.map { it.first })
        assertArrayEquals("1\n".toByteArray(Charsets.US_ASCII), entries[0].second)
        // The vault file exactly as on disk, and the attachment files exactly as stored (still encrypted).
        assertEquals(File(phone("a"), "vault.bin").readText(), entries[1].second.decodeToString())
        for ((name, data) in entries.drop(2)) assertArrayEquals(File(phone("a"), name).readBytes(), data)
        // The head identifies it; JSON, other ZIPs and short input do not.
        assertTrue(isBackup(zip.copyOf(BACKUP_HEAD_BYTES)))
        assertFalse(isBackup(File(phone("a"), "vault.bin").readBytes().copyOf(BACKUP_HEAD_BYTES)))
        assertFalse(isBackup(zipOf(listOf("vault.json" to ByteArray(3), marker)).copyOf(BACKUP_HEAD_BYTES)))
        assertFalse(isBackup(zip.copyOf(40)))
        assertFalse(isBackup(ByteArray(0)))
        assertEquals("seguridad-boveda-20261026.sgbak", backupFileName(LocalDate.of(2026, 10, 26)))
        assertEquals("seguridad-boveda-20270105.sgbak", backupFileName(LocalDate.of(2027, 1, 5)))
    }

    @Test fun neverWritesACopyItCouldNotReadBack() {
        val dir = phone("w")
        val f = File(dir, "x").apply { writeBytes(bytes(10)) }
        val big = File(dir, "big").apply { writeBytes(ByteArray(6 * 1024 * 1024 + 1)) }
        val tooMany = (0 until BACKUP_MAX_ENTRIES - 1).associate { "%032x".format(it) to f }
        val cases = listOf<() -> Unit>(
            { writeBackup(ByteArrayOutputStream(), "x".repeat(6 * 1024 * 1024 + 1), emptyMap()) },
            { writeBackup(ByteArrayOutputStream(), "{}", tooMany) },
            { writeBackup(ByteArrayOutputStream(), "{}", mapOf("e".repeat(32) to big)) },
        )
        for (case in cases) {
            try {
                case()
                fail("Debía fallar")
            } catch (e: CryptoFailure) {
                assertEquals("La bóveda no cabe en una copia (100 MB en total, 6 MB por archivo y 2 000 archivos)", e.message)
            }
        }
        // Right at the limits it is written, and read back.
        val bos = ByteArrayOutputStream()
        writeBackup(bos, "{}", tooMany.entries.drop(1).associate { it.key to it.value })
        assertEquals(BACKUP_MAX_ENTRIES - 2, BackupReader().read(ByteArrayInputStream(bos.toByteArray()), File(dir, "t")).attachments.size)
    }

    @Test fun writeBackupIsReadBack() {
        val dir = phone("w")
        val id = "0123456789abcdef0123456789abcdef"
        val f = File(dir, "x").apply { writeBytes(bytes(1234)) }
        val bos = ByteArrayOutputStream()
        writeBackup(bos, "{\"v\":1}", mapOf(id to f))
        val c = BackupReader().read(ByteArrayInputStream(bos.toByteArray()), File(dir, "t"))
        assertEquals("{\"v\":1}", c.vaultJson)
        assertArrayEquals(bytes(1234), c.attachments.getValue(id).readBytes())
        // writeBackup never takes an unchecked name.
        try {
            writeBackup(ByteArrayOutputStream(), "{}", mapOf("../x" to f))
            fail("Debía fallar")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
