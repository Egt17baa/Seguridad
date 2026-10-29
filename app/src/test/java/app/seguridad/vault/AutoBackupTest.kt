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

/**
 * A folder in memory; a file exists (with what was written so far) from the moment it is created, as in SAF, and a
 * name already taken gets « (1)» added, as ExternalStorageProvider does. Each file has a document id, which a rename
 * changes (as with ExternalStorageProvider, whose ids are paths); a file put straight into [files] (by another app)
 * gets one the first time it is listed.
 */
private class MemoryTarget : BackupTarget {
    val files = LinkedHashMap<String, ByteArray>()
    private val ids = HashMap<String, String>()
    private var nextId = 0
    private fun idOf(name: String) = ids.getOrPut(name) { "doc-${nextId++}" }
    var failWrite: Exception? = null
    var failAfterBytes: Int? = null
    var failRename: Exception? = null
    var failList: Exception? = null
    var failDelete: Exception? = null
    /** What each file's flags say: a provider without FLAG_SUPPORTS_RENAME. */
    var renames = true
    var writes = 0
    val written = mutableListOf<String>()

    override suspend fun create(name: String): BackupFile {
        failWrite?.let { throw it }
        writes++
        written += name
        val created = if (name in files) "$name (1)" else name
        files[created] = ByteArray(0)
        ids[created] = "doc-${nextId++}"
        return object : BackupFile {
            var current = created

            override val id: String get() = idOf(current)

            override val canRename: Boolean get() = renames

            override suspend fun write(writer: suspend (OutputStream) -> Unit) {
                val out = object : OutputStream() {
                    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        val next = files.getValue(current) + b.copyOfRange(off, off + len)
                        failAfterBytes?.let { if (next.size > it) throw IOException("disco lleno") }
                        files[current] = next
                    }
                }
                writer(out)
            }

            override fun rename(to: String) {
                failRename?.let { throw it }
                check(to !in files) { "Renombrar encima de $to" }
                files[to] = files.remove(current) ?: throw FileNotFoundException()
                ids.remove(current)
                ids[to] = "doc-${nextId++}"
                current = to
            }

            override fun delete() {
                files.remove(current)
                ids.remove(current)
            }
        }
    }

    override fun list(prefix: String): List<BackupEntry> {
        failList?.let { throw it }
        return files.keys.filter { it.startsWith(prefix) }.map { BackupEntry(idOf(it), it) }
    }

    override fun delete(id: String) {
        failDelete?.let { throw it }
        val name = ids.entries.firstOrNull { it.value == id }?.key ?: return
        files.remove(name)
        ids.remove(name)
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

    private val results = mutableListOf<AutoBackupResult>()
    private val errors = mutableListOf<String?>()

    /** The folder chosen in Ajustes; the copies go to [target] whatever it is. */
    private var folder: String? = FOLDER

    /** When the last copy was written, as Ajustes keeps it: by default after any change to the vault file. */
    private var lastCopy: Long? = Long.MAX_VALUE

    /** The copies made, as Ajustes keeps them from each result (only those to the folder still chosen). */
    private var copies: List<String> = emptyList()

    private fun TestScope.auto(store: VaultStore, target: BackupTarget?, keep: () -> Int = { 5 }): AutoBackup =
        AutoBackup(
            store,
            folder = { folder },
            open = { target },
            backgroundScope,
            now = { clock },
            keep = keep,
            lastCopyAt = { lastCopy },
            copies = { copies },
            onResult = { r ->
                results += r
                errors += r.error
                if (r.folder == folder) r.copies?.let { copies = it }
            },
            zone = ZoneOffset.UTC,
        ).also { runCurrent() }

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

    @Test fun rotationGoesByWhenCopiesWereMadeEvenWithTheClockBack() = runTest {
        val s = opened()
        val target = MemoryTarget()
        auto(s, target, keep = { 3 })
        clock += 4L * 365 * 24 * 3_600_000 // Ahead (2030), then back to the right time.
        repeat(3) { i ->
            clock += 60_000
            s.upsert(note("$i"))
            settle()
        }
        clock = 1_793_008_800_000L
        s.upsert(note("x"))
        settle()
        // The copy just written always stays, then the newest made: the oldest made (the first 2030 one) goes.
        val left = target.files.keys.filter { name.matches(it) }.sorted()
        assertEquals(listOf("seguridad-auto-20261026-100000.sgbak", "seguridad-auto-20301025-100200.sgbak", "seguridad-auto-20301025-100300.sgbak"), left)
    }

    @Test fun filesLeftByAnotherAppWithLaterDatesNeverDisplaceTheRealCopies() = runTest {
        val s = opened()
        val target = MemoryTarget()
        auto(s, target, keep = { 3 })
        repeat(3) { i ->
            clock += 60_000
            s.upsert(note("$i"))
            settle()
        }
        val genuine = target.files.keys.filter { name.matches(it) }.toSet()
        assertEquals(3, genuine.size)
        // Another app that can write to the folder leaves two with the latest possible dates, and a copy of 1.2.0's.
        val planted = listOf("seguridad-auto-99991231-235958.sgbak", "seguridad-auto-99991231-235959.sgbak", "seguridad-auto-20200101-000000.sgbak")
        planted.forEach { target.files[it] = byteArrayOf(1) }
        clock += 60_000
        s.upsert(note("3"))
        settle()
        // Only the oldest real copy went: the planted files neither take the kept places nor get deleted.
        val oldest = genuine.min()
        assertEquals(genuine - oldest + "seguridad-auto-20261026-100400.sgbak" + planted, target.files.keys.toSet())
        assertTrue(errors.all { it == null })
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
        val target = MemoryTarget()
        val a = auto(s, target)

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
        // Rename refused and the copy written again straight under its name also fails: nothing is left either.
        target.failAfterBytes = null
        target.failRename = UnsupportedOperationException("rename")
        target.renames = false
        target.failAfterBytes = 100
        s.upsert(note("5"))
        settle()
        assertEquals(AUTO_BACKUP_WRITE_FAILED, errors.last())
        assertTrue(target.files.keys.toString(), target.files.isEmpty())
        target.failAfterBytes = null
        target.renames = true
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

        // A failing copy with no change by the user leaves the vault file byte for byte as it was.
        val vaultBytes = File(tmp.root, "vault.bin").readBytes()
        target.failWrite = IOException("disco lleno")
        clock += 60_000
        a.request()
        settle()
        assertEquals(AUTO_BACKUP_WRITE_FAILED, errors.last())
        assertArrayEquals(vaultBytes, File(tmp.root, "vault.bin").readBytes())
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

    @Test fun strayCancellationIsReportedAndCopiesGoOn() = runTest {
        val s = opened()
        val target = MemoryTarget()
        target.failWrite = CancellationException("cancelado")
        auto(s, target)
        s.upsert(note("1"))
        settle()
        // Thrown by the folder, not by cancelling AutoBackup's scope: a failure like any other, not the end of the copies.
        assertEquals(listOf<String?>(AUTO_BACKUP_FAILED), errors)
        target.failWrite = null
        s.upsert(note("2"))
        settle()
        assertNull(errors.last())
        assertEquals(1, target.files.keys.count { name.matches(it) })
    }

    @Test fun lockedVaultCopiesOnlyAfterUnlock() = runTest {
        val s = opened()
        val target = MemoryTarget()
        val a = auto(s, target)
        s.upsert(note("1"))
        s.lock() // Left the app within the 3 s.
        settle()
        assertTrue("Sin copia con la bóveda bloqueada", target.files.isEmpty())
        assertEquals(0, target.writes)
        assertTrue(errors.isEmpty())
        // «Hacer una copia ahora» while locked waits too.
        a.request()
        settle()
        assertTrue(target.files.isEmpty())

        s.unlock(master.toCharArray())
        runCurrent()
        assertEquals(1, target.writes)
        assertEquals(listOf("seguridad-auto-20261026-100000.sgbak"), target.files.keys.toList())
        assertEquals(listOf<String?>(null), errors)

        // Nothing waits any more: locking and opening again makes no other copy.
        s.lock()
        s.unlock(master.toCharArray())
        settle()
        assertEquals(1, target.writes)
    }

    @Test fun changeMasterCopiesAfterTheNextUnlock() = runTest {
        val s = opened()
        s.upsert(note("1"))
        val target = MemoryTarget()
        auto(s, target)
        assertTrue(s.changeMaster("otra-maestra-larga".toCharArray())) // Locks the vault.
        settle()
        assertTrue(target.files.isEmpty())
        s.unlock("otra-maestra-larga".toCharArray())
        runCurrent()
        val copy = target.files.getValue("seguridad-auto-20261026-100000.sgbak")
        val other = store(File(tmp.root, "otro").apply { mkdirs() }).apply { load() }
        other.importReplace(ByteArrayInputStream(copy), "otra-maestra-larga".toCharArray())
        assertEquals(listOf("1"), (other.state.value as VaultState.Unlocked).entries.map { it.id })
    }

    @Test fun damagedOrMissingAttachmentFailsAndKeepsOlderCopies() = runTest {
        val s = opened()
        s.upsert(note("1"))
        assertTrue(s.addAttachment("1", ByteArrayInputStream(ByteArray(100) { 7 }), "foto.jpg"))
        val target = MemoryTarget()
        val a = auto(s, target, keep = { 3 })
        repeat(3) { i ->
            clock += 60_000
            s.upsert(note("n$i"))
            settle()
        }
        val good = target.files.toMap()
        assertEquals(3, good.size)
        assertTrue(errors.all { it == null })

        val att = File(tmp.root, "att").listFiles()!!.single()
        for (damage in listOf<() -> Unit>({ att.writeBytes(ByteArray(140)) }, { att.delete() })) {
            damage()
            repeat(4) { i ->
                clock += 60_000
                s.upsert(note("d$i"))
                settle()
                assertEquals(AUTO_BACKUP_VAULT_PROBLEM, errors.last())
            }
            // Locked and opened again: still checked, still refused.
            s.lock()
            a.request()
            settle()
            s.unlock(master.toCharArray())
            runCurrent()
            assertEquals(AUTO_BACKUP_VAULT_PROBLEM, errors.last())
            // No new file, not even a temporary one, and the good copies are all still there, unchanged.
            assertEquals(good.keys, target.files.keys)
            good.forEach { (n, bytes) -> assertArrayEquals(n, bytes, target.files.getValue(n)) }
        }
    }

    @Test fun anExistingNameIsNeverReplaced() = runTest {
        val s = opened()
        val target = MemoryTarget()
        // A complete copy with the name of now (clock set back), and a cut temporary one with the next second's.
        target.files["seguridad-auto-20261026-100000.sgbak"] = byteArrayOf(1, 2, 3)
        target.files["seguridad-auto-20261026-100001.sgbak.tmp"] = byteArrayOf(9)
        auto(s, target)
        target.failRename = IOException("rename")
        s.upsert(note("1"))
        settle()
        // The rename failed: written again under the next free name, and the older complete copy is still there.
        assertNull(errors.last())
        assertEquals(listOf("seguridad-auto-20261026-100002.sgbak.tmp", "seguridad-auto-20261026-100002.sgbak"), target.written)
        assertArrayEquals(byteArrayOf(1, 2, 3), target.files.getValue("seguridad-auto-20261026-100000.sgbak"))
        // The new copy is the complete one written now, never the cut leftover, which rotation removed.
        assertTrue(isBackup(target.files.getValue("seguridad-auto-20261026-100002.sgbak").copyOf(BACKUP_HEAD_BYTES)))
        assertFalse(target.files.containsKey("seguridad-auto-20261026-100001.sgbak.tmp"))

        target.failRename = null
        s.upsert(note("2"))
        settle()
        assertNull(errors.last())
        assertEquals("seguridad-auto-20261026-100001.sgbak.tmp", target.written.last())
        assertArrayEquals(byteArrayOf(1, 2, 3), target.files.getValue("seguridad-auto-20261026-100000.sgbak"))
        assertTrue(isBackup(target.files.getValue("seguridad-auto-20261026-100001.sgbak").copyOf(BACKUP_HEAD_BYTES)))
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

    @Test fun providerWithoutRenameGetsTheCopyUnderItsName() = runTest {
        val s = opened()
        val target = MemoryTarget()
        target.renames = false
        auto(s, target)
        s.upsert(note("1"))
        settle()
        assertNull(errors.last())
        // The temporary file is only made to read its flags: the copy is written once, straight under its name.
        assertEquals(listOf("seguridad-auto-20261026-100000.sgbak.tmp", "seguridad-auto-20261026-100000.sgbak"), target.written)
        assertEquals(setOf("seguridad-auto-20261026-100000.sgbak"), target.files.keys)
        assertTrue(isBackup(target.files.getValue("seguridad-auto-20261026-100000.sgbak").copyOf(BACKUP_HEAD_BYTES)))
    }

    @Test fun renameRefusedWritesTheCopyAgainUnderItsName() = runTest {
        val s = opened()
        val target = MemoryTarget()
        target.failRename = UnsupportedOperationException("Rename not supported")
        auto(s, target)
        s.upsert(note("1"))
        settle()
        assertNull(errors.last())
        assertEquals(setOf("seguridad-auto-20261026-100000.sgbak"), target.files.keys)
        val read = BackupReader().read(ByteArrayInputStream(target.files.getValue("seguridad-auto-20261026-100000.sgbak")), File(tmp.root, "read"))
        assertTrue(read.vaultJson.isNotEmpty())
    }

    @Test fun resultNamesTheFolderAndTheTimeOfTheCopy() = runTest {
        val s = opened()
        val target = MemoryTarget()
        auto(s, target)
        s.upsert(note("1"))
        settle()
        val r = results.single()
        assertEquals(FOLDER, r.folder)
        assertEquals(1_793_008_800_000L, r.writtenAt) // The time in its name.
        assertNull(r.error)

        // Another folder chosen: the next result names that one.
        folder = "content://otra/tree/primary%3AOtra"
        s.upsert(note("2"))
        settle()
        assertEquals("content://otra/tree/primary%3AOtra", results.last().folder)

        // Turned off: no copy, nothing to report.
        folder = null
        s.upsert(note("3"))
        settle()
        assertEquals(2, results.size)
    }

    @Test fun oldCopiesNotDeletedStillCountsAsWritten() = runTest {
        val s = opened()
        val target = MemoryTarget()
        auto(s, target, keep = { 3 })
        repeat(3) { i ->
            clock += 60_000
            s.upsert(note("$i"))
            settle()
        }
        target.failDelete = IOException("borrar")
        clock += 60_000
        s.upsert(note("3"))
        settle()
        val r = results.last()
        assertEquals(AUTO_BACKUP_OLD_NOT_DELETED, r.error)
        assertEquals(clock, r.writtenAt) // The copy exists: Ajustes shows its date.
        assertTrue(target.files.containsKey("seguridad-auto-20261026-100400.sgbak"))
        // The one that could not be deleted is still listed as ours, and goes at the next copy.
        target.failDelete = null
        clock += 60_000
        s.upsert(note("4"))
        settle()
        assertNull(errors.last())
        assertEquals(3, target.files.keys.count { name.matches(it) })
        assertEquals(3, copies.size)
    }

    @Test fun failedCopyIsNotWritten() = runTest {
        val s = opened()
        val target = MemoryTarget()
        target.failWrite = IOException("disco lleno")
        auto(s, target)
        s.upsert(note("1"))
        settle()
        assertNull(results.single().writtenAt)
        assertEquals(FOLDER, results.single().folder)
    }

    /** The app closed with a copy waiting (locked, or within the 3 s): the next unlock in a new process makes it. */
    @Test fun changeNotCopiedBeforeTheAppClosedIsCopiedAtTheNextUnlock() = runTest {
        opened().apply { upsert(note("1")) }
        val vaultFile = File(tmp.root, "vault.bin")
        val changedAt = 1_700_000_000_000L
        assertTrue(vaultFile.setLastModified(changedAt))
        lastCopy = changedAt - 60_000 // The last copy is older than the last change.

        val s = store().apply { load() } // A new process: nothing pending in memory.
        val target = MemoryTarget()
        auto(s, target)
        settle()
        assertTrue("Bloqueada: sin copia", target.files.isEmpty())
        s.unlock(master.toCharArray())
        runCurrent()
        assertEquals(1, target.writes)
        assertNull(errors.single())
        // Opening wrote the last master check, but the file keeps the time of the last change.
        assertEquals(changedAt, vaultFile.lastModified())

        // With the copy recorded, opening again copies nothing.
        lastCopy = results.single().writtenAt
        s.lock()
        s.unlock(master.toCharArray())
        settle()
        assertEquals(1, target.writes)
    }

    @Test fun upToDateCopyIsNotRepeatedAtUnlock() = runTest {
        opened().apply { upsert(note("1")) }
        val vaultFile = File(tmp.root, "vault.bin")
        assertTrue(vaultFile.setLastModified(1_700_000_000_000L))
        lastCopy = 1_700_000_000_000L + 1
        val s = store().apply { load() }
        val target = MemoryTarget()
        auto(s, target)
        s.unlock(master.toCharArray())
        settle()
        assertEquals(0, target.writes)
    }

    @Test fun noCopyYetIsMadeAtUnlock() = runTest {
        opened().apply { upsert(note("1")) }
        lastCopy = null // A folder chosen while locked, and the app closed before the vault was opened.
        val s = store().apply { load() }
        val target = MemoryTarget()
        auto(s, target)
        s.unlock(master.toCharArray())
        runCurrent()
        assertEquals(1, target.writes)
    }

    // ---- «Borrar las copias automáticas anteriores y hacer una nueva» (A6) ----

    /** Three copies made by the app, and the ids it keeps for them. */
    private suspend fun TestScope.threeCopies(s: VaultStore, target: MemoryTarget): Set<String> {
        repeat(3) { i ->
            clock += 60_000
            s.upsert(note("$i"))
            settle()
        }
        val own = target.files.keys.filter { name.matches(it) }.toSet()
        assertEquals(3, own.size)
        assertEquals(3, copies.size)
        return own
    }

    @Test fun replaceOldCopiesDeletesOnlyOwnIds() = runTest {
        val s = opened()
        val target = MemoryTarget()
        val a = auto(s, target)
        val own = threeCopies(s, target)
        // Not ours, though named like a copy (one with the latest possible date, one older): never touched.
        val foreign = listOf("seguridad-auto-99991231-235959.sgbak", "seguridad-auto-20200101-000000.sgbak", "notas.txt")
        foreign.forEach { target.files[it] = byteArrayOf(1) }
        clock += 60_000
        val result = a.replaceOldCopies()
        assertNull(result.error)
        assertEquals(clock, result.writtenAt)
        assertEquals(FOLDER, result.folder)
        // The own copies are gone, the foreign ones stay, and there is one new copy.
        val fresh = "seguridad-auto-20261026-100400.sgbak"
        assertEquals(foreign.toSet() + fresh, target.files.keys.toSet())
        assertTrue(own.none { it in target.files })
        // The list kept in Ajustes holds only the new one.
        assertEquals(1, copies.size)
        assertEquals(result.copies, copies)
        // And it is a copy that opens.
        val other = store(File(tmp.root, "otro").apply { mkdirs() }).apply { load() }
        other.importReplace(ByteArrayInputStream(target.files.getValue(fresh)), master.toCharArray())
        assertEquals(3, (other.state.value as VaultState.Unlocked).entries.size)
    }

    @Test fun replaceOldCopiesLeavesOldOnesWhenTheNewOneFails() = runTest {
        val s = opened()
        val target = MemoryTarget()
        val a = auto(s, target)
        val own = threeCopies(s, target)
        val listed = copies
        target.failAfterBytes = 100 // Full disk halfway through the new copy.
        clock += 60_000
        val result = a.replaceOldCopies()
        assertEquals(AUTO_BACKUP_WRITE_FAILED, result.error)
        assertNull(result.writtenAt)
        // Never zero copies: the old ones are all there, the half-written one is not, and the list is as it was.
        assertEquals(own, target.files.keys.toSet())
        assertEquals(listed, copies)
        assertEquals(AUTO_BACKUP_WRITE_FAILED, errors.last())
    }

    @Test fun replaceOldCopiesThatCannotDeleteKeepsThemOnTheListAndSaysSo() = runTest {
        val s = opened()
        val target = MemoryTarget()
        val a = auto(s, target)
        val own = threeCopies(s, target)
        target.failDelete = IOException("sin permiso")
        clock += 60_000
        val result = a.replaceOldCopies()
        assertEquals(AUTO_BACKUP_OLD_NOT_DELETED, result.error)
        assertEquals(clock, result.writtenAt) // The new copy is there all the same.
        assertEquals(own + "seguridad-auto-20261026-100400.sgbak", target.files.keys.toSet())
        assertEquals(4, copies.size) // The old ones are still ours: tried again next time.
    }

    @Test fun replaceOldCopiesWhileLockedWaitsForTheNextUnlock() = runTest {
        val s = opened()
        val target = MemoryTarget()
        val a = auto(s, target)
        val own = threeCopies(s, target)
        // As after «Cambiar contraseña maestra»: the vault is locked.
        s.lock()
        runCurrent()
        clock += 60_000
        val result = a.replaceOldCopies()
        assertEquals(AUTO_BACKUP_REPLACE_WAITS, result.error)
        assertNull(result.writtenAt)
        assertEquals(own, target.files.keys.toSet()) // Nothing deleted, nothing written yet.
        s.unlock(master.toCharArray())
        runCurrent()
        assertEquals(setOf("seguridad-auto-20261026-100400.sgbak"), target.files.keys.toSet())
        assertEquals(1, copies.size)
        // It was asked for once: the next copy after a change is a normal one and the new copy stays.
        clock += 60_000
        s.upsert(note("x"))
        settle()
        assertEquals(2, target.files.size)
    }

    @Test fun replaceOldCopiesWithNoFolderOrNoVaultWritesNothing() = runTest {
        val s = store().apply { load() } // No vault.
        val target = MemoryTarget()
        val a = auto(s, target)
        assertEquals(AUTO_BACKUP_FAILED, a.replaceOldCopies().error)
        assertTrue(target.files.isEmpty())
        val open = opened(store(File(tmp.root, "v2").apply { mkdirs() }))
        val b = auto(open, target)
        folder = null
        assertEquals(AUTO_BACKUP_FAILED, b.replaceOldCopies().error)
        assertTrue(target.files.isEmpty())
        assertEquals(0, target.writes)
    }

    @Test fun changesWhileOpenStillWaitForTheDebounce() = runTest {
        val s = opened()
        val target = MemoryTarget()
        lastCopy = 0L // Behind: but only opening the vault checks it, not each change saved while it is open.
        auto(s, target)
        settle() // Already open when AutoBackup started: the check at start copies once.
        val atStart = target.writes
        s.upsert(note("1"))
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(atStart, target.writes)
        settle()
        assertEquals(atStart + 1, target.writes)
    }
}

private const val FOLDER = "content://com.android.externalstorage.documents/tree/primary%3ACopias"
