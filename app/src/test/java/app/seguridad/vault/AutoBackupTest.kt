package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.time.ZoneOffset

/** A folder in memory; a file exists (with what was written so far) from the moment it is created, as in SAF. */
private class MemoryTarget : BackupTarget {
    val files = LinkedHashMap<String, ByteArray>()
    var failWrite: Exception? = null
    var failAfterBytes: Int? = null
    var failRename: Exception? = null
    var failList: Exception? = null
    var writes = 0
    val written = mutableListOf<String>()

    override suspend fun write(name: String, writer: suspend (OutputStream) -> Unit) {
        failWrite?.let { throw it }
        writes++
        written += name
        files[name] = ByteArray(0)
        val out = object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                val next = files.getValue(name) + b.copyOfRange(off, off + len)
                failAfterBytes?.let { if (next.size > it) throw IOException("disco lleno") }
                files[name] = next
            }
        }
        writer(out)
    }

    override fun list(prefix: String): List<String> {
        failList?.let { throw it }
        return files.keys.filter { it.startsWith(prefix) }
    }

    override fun delete(name: String) {
        files.remove(name)
    }

    override fun rename(from: String, to: String) {
        failRename?.let { throw it }
        files[to] = files.remove(from) ?: throw FileNotFoundException()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AutoBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private val master = "maestra-de-prueba"

    /** 2026-10-26 10:00:00 UTC. */
    private var clock = 1_793_008_800_000L

    private fun store(dir: File = tmp.root) =
        VaultStore(File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) })

    private fun note(id: String) = VaultEntry(id, EntryType.NOTE, "Nota $id", notes = "secreto $id", createdAt = 0, updatedAt = 0)

    private val errors = mutableListOf<String?>()

    private fun TestScope.auto(store: VaultStore, target: BackupTarget?, keep: () -> Int = { 5 }): AutoBackup =
        AutoBackup(store, { target }, backgroundScope, now = { clock }, keep = keep, onError = { errors += it }, zone = ZoneOffset.UTC)
            .also { runCurrent() }

    private suspend fun opened(s: VaultStore = store()) = s.apply {
        load()
        create(master.toCharArray())
    }

    private fun TestScope.settle() {
        advanceTimeBy(AUTO_BACKUP_DEBOUNCE_MS + 1)
        runCurrent()
    }

    private val name = Regex("seguridad-auto-\\d{8}-\\d{6}\\.sgbak")

    @Test fun writesAfterChangeDebounced() = runTest {
        val s = opened()
        val target = MemoryTarget()
        auto(s, target)
        s.upsert(note("1"))
        advanceTimeBy(1_000)
        s.upsert(note("2"))
        advanceTimeBy(1_000)
        s.upsert(note("3"))
        advanceTimeBy(AUTO_BACKUP_DEBOUNCE_MS - 1)
        runCurrent()
        assertTrue("Aún no: 3 s desde el último cambio", target.files.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, target.writes)
        assertEquals(listOf("seguridad-auto-20261026-100000.sgbak"), target.files.keys.toList())
        // Written under a temporary name and renamed when complete: a cut copy never has the real name.
        assertEquals(listOf("seguridad-auto-20261026-100000.sgbak.tmp"), target.written)
        assertEquals(listOf<String?>(null), errors) // null: the copy went well.
    }

    @Test fun startingDoesNotCopy() = runTest {
        val s = opened()
        val target = MemoryTarget()
        auto(s, target)
        settle()
        assertTrue(target.files.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test fun requestCopiesWithoutAChange() = runTest {
        val s = opened()
        val target = MemoryTarget()
        val a = auto(s, target)
        a.request()
        settle()
        assertEquals(1, target.files.size)
    }

    @Test fun rotationKeepsN() = runTest {
        val s = opened()
        val target = MemoryTarget()
        // Not ours, or not exactly our name: never touched.
        val foreign = listOf(
            "notas.txt",
            "seguridad-boveda-20260101.sgbak",
            "seguridad-auto-2026.sgbak",
            "seguridad-auto-20200101-000000.sgbak.bak",
            "seguridad-auto-20200101-000000 (1).sgbak",
            "xseguridad-auto-20200101-000000.sgbak",
        )
        foreign.forEach { target.files[it] = byteArrayOf(1) }
        auto(s, target, keep = { 3 })
        repeat(5) { i ->
            clock += 60_000
            s.upsert(note("$i"))
            settle()
        }
        val ours = target.files.keys.filter { name.matches(it) }.sorted()
        assertEquals(
            listOf("seguridad-auto-20261026-100300.sgbak", "seguridad-auto-20261026-100400.sgbak", "seguridad-auto-20261026-100500.sgbak"),
            ours,
        )
        foreign.forEach { assertTrue(it, target.files.containsKey(it)) }
        assertTrue(errors.all { it == null })
    }

    @Test fun rotationNeverDeletesTheNewestEvenWithTheClockBack() = runTest {
        val s = opened()
        val target = MemoryTarget()
        listOf("20300101-000000", "20300102-000000", "20300103-000000").forEach { target.files["seguridad-auto-$it.sgbak"] = byteArrayOf(1) }
        auto(s, target, keep = { 3 })
        s.upsert(note("1"))
        settle()
        assertTrue(target.files.containsKey("seguridad-auto-20261026-100000.sgbak"))
        assertEquals(3, target.files.keys.count { name.matches(it) })
    }

    @Test fun unknownKeepFallsBackToFive() = runTest {
        val s = opened()
        val target = MemoryTarget()
        auto(s, target, keep = { 1 })
        repeat(7) { i ->
            clock += 60_000
            s.upsert(note("$i"))
            settle()
        }
        assertEquals(5, target.files.keys.count { name.matches(it) })
    }

    @Test fun leftoverTemporaryCopiesAreCleaned() = runTest {
        val s = opened()
        val target = MemoryTarget()
        target.files["seguridad-auto-20200101-000000.sgbak.tmp"] = byteArrayOf(1)
        target.files["otra-cosa.sgbak.tmp"] = byteArrayOf(1)
        auto(s, target)
        s.upsert(note("1"))
        settle()
        assertEquals(setOf("otra-cosa.sgbak.tmp", "seguridad-auto-20261026-100000.sgbak"), target.files.keys)
    }

    @Test fun targetErrorReportedNotThrown() = runTest {
        val s = opened()
        s.upsert(note("1"))
        val vaultBytes = File(tmp.root, "vault.bin").readBytes()
        val target = MemoryTarget()
        auto(s, target)

        // Permission revoked.
        target.failWrite = SecurityException("Permission Denial: content://secret/tree")
        clock += 60_000
        s.upsert(note("2"))
        settle()
        assertEquals(AUTO_BACKUP_NO_PERMISSION, errors.last())
        // Folder deleted.
        target.failWrite = FileNotFoundException("/storage/emulated/0/Copias")
        s.upsert(note("3"))
        settle()
        assertEquals(AUTO_BACKUP_NO_FOLDER, errors.last())
        // Full disk halfway through: no partial copy is left behind.
        target.failWrite = null
        target.failAfterBytes = 100
        s.upsert(note("4"))
        settle()
        assertEquals(AUTO_BACKUP_WRITE_FAILED, errors.last())
        assertTrue(target.files.keys.toString(), target.files.isEmpty())
        // Rename refused: the temporary copy is deleted too.
        target.failAfterBytes = null
        target.failRename = UnsupportedOperationException("rename")
        s.upsert(note("5"))
        settle()
        assertEquals(AUTO_BACKUP_FAILED, errors.last())
        assertTrue(target.files.keys.toString(), target.files.isEmpty())
        // Listing fails: reported too.
        target.failRename = null
        target.failList = IllegalStateException("cursor")
        s.upsert(note("6"))
        settle()
        assertEquals(AUTO_BACKUP_FAILED, errors.last())

        // No message carries what the exception said (paths, URIs).
        errors.filterNotNull().forEach { e -> assertFalse(e, e.contains("content://") || e.contains("/storage") || e.contains("cursor")) }
        // The vault is untouched and still open; the next change copies again.
        assertTrue(s.state.value is VaultState.Unlocked)
        assertEquals(6, (s.state.value as VaultState.Unlocked).entries.size)
        target.failList = null
        s.upsert(note("7"))
        settle()
        assertNull(errors.last())
        assertEquals(1, target.files.keys.count { name.matches(it) })
        assertFalse(vaultBytes.contentEquals(File(tmp.root, "vault.bin").readBytes())) // Only by the user's own saves.
    }

    @Test fun damagedAttachmentLeavesNoCopyAndNamesNothing() = runTest {
        val s = opened()
        s.upsert(note("1"))
        assertTrue(s.addAttachment("1", ByteArrayInputStream(ByteArray(100) { 7 }), "pasaporte-secreto.jpg"))
        File(tmp.root, "att").listFiles()!!.single().writeBytes(ByteArray(140))
        val target = MemoryTarget()
        auto(s, target)
        s.upsert(note("2"))
        settle()
        assertEquals(AUTO_BACKUP_VAULT_PROBLEM, errors.last())
        assertFalse(errors.last()!!.contains("pasaporte"))
        assertTrue(target.files.isEmpty())
    }

    @Test fun noTargetNoWrite() = runTest {
        val s = opened()
        auto(s, null)
        s.upsert(note("1"))
        settle()
        assertTrue(errors.isEmpty())
    }

    @Test fun noVaultNothingWritten() = runTest {
        val s = store().apply { load() }
        val target = MemoryTarget()
        val a = auto(s, target)
        a.request()
        settle()
        assertTrue(target.files.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test fun backupIsValidSgbak() = runTest {
        val s = opened()
        s.upsert(note("1"))
        assertTrue(s.addAttachment("1", ByteArrayInputStream(ByteArray(5_000) { it.toByte() }), "foto.jpg"))
        val target = MemoryTarget()
        auto(s, target)
        s.upsert(note("2"))
        settle()
        val copy = target.files.getValue("seguridad-auto-20261026-100000.sgbak")
        assertTrue(isBackup(copy.copyOf(BACKUP_HEAD_BYTES)))
        val read = BackupReader().read(ByteArrayInputStream(copy), File(tmp.root, "read"))
        assertEquals(1, read.attachments.size)

        // It opens on another phone with the master password, attachment included.
        val other = store(File(tmp.root, "otro").apply { mkdirs() }).apply { load() }
        other.importReplace(ByteArrayInputStream(copy), master.toCharArray())
        val entries = (other.state.value as VaultState.Unlocked).entries
        assertEquals(listOf("1", "2"), entries.map { it.id }.sorted())
        val att = entries.first { it.id == "1" }.attachments.single()
        assertArrayEquals(ByteArray(5_000) { it.toByte() }, other.openAttachment("1", att.id))
    }

    @Test fun cancellationIsNotReported() = runTest {
        val s = opened()
        val target = MemoryTarget()
        target.failWrite = CancellationException("cancelado")
        auto(s, target)
        s.upsert(note("1"))
        settle()
        assertTrue(errors.isEmpty())
    }

    @Test fun revisionCountsSavedChangesOnly() = runTest {
        val s = store().apply { load() }
        assertEquals(0L, s.revision.value)
        s.create(master.toCharArray())
        assertEquals(1L, s.revision.value)
        s.upsert(note("1"))
        assertEquals(2L, s.revision.value)
        s.lock()
        s.unlock(master.toCharArray()) // Bookkeeping only (the last master check): not a change to copy.
        assertEquals(2L, s.revision.value)
        s.delete("nada") // Nothing removed, but saved: still counts (cheap and safe).
        val afterDelete = s.revision.value
        assertTrue(s.addAttachment("1", ByteArrayInputStream(ByteArray(10)), "a.txt"))
        assertEquals(afterDelete + 1, s.revision.value)
        assertTrue(s.changeMaster("otra-maestra-larga".toCharArray()))
        assertEquals(afterDelete + 2, s.revision.value)
        val copy = java.io.ByteArrayOutputStream()
        s.unlock("otra-maestra-larga".toCharArray())
        s.exportBackup(copy)
        assertEquals(afterDelete + 2, s.revision.value) // Exporting changes nothing.
        s.importReplace(ByteArrayInputStream(copy.toByteArray()), "otra-maestra-larga".toCharArray())
        assertEquals(afterDelete + 3, s.revision.value)
    }

    @Test fun failedSaveDoesNotBumpRevision() = runTest {
        val dir = File(tmp.root, "v").apply { mkdirs() }
        val s = opened(store(dir))
        val before = s.revision.value
        File(dir, "vault.bin.tmp").mkdirs() // The atomic write cannot create its temporary file (works as root too).
        assertTrue(runCatching { s.upsert(note("1")) }.isFailure)
        assertEquals(before, s.revision.value)
    }
}
