package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import app.seguridad.settings.Settings
import app.seguridad.settings.SettingsStore
import app.seguridad.settings.currentAutoBackupCopies
import app.seguridad.settings.withAutoBackupResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    /** M3/M4: files that cannot be deleted, by name (a read-only document, a provider error on that one file). */
    var undeletable: (String) -> Boolean = { false }
    /** How many times [list] was called; at the call whose index is [failListAt], it throws (a folder gone mid-copy). */
    var listCount = 0
    var failListAt: Int? = null
    /** What each file's flags say: a provider without FLAG_SUPPORTS_RENAME. */
    var renames = true
    var writes = 0
    val written = mutableListOf<String>()
    /** Called at the first byte written to any file (to cut the caller in the middle of the work). */
    var onWrite: (() -> Unit)? = null

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
                        onWrite?.let { hook ->
                            onWrite = null
                            hook()
                        }
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
        if (listCount++ == failListAt) throw IOException("carpeta desaparecida")
        return files.keys.filter { it.startsWith(prefix) }.map { BackupEntry(idOf(it), it) }
    }

    override fun delete(id: String) {
        failDelete?.let { throw it }
        val name = ids.entries.firstOrNull { it.value == id }?.key ?: return
        if (undeletable(name)) throw IOException("solo lectura")
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

    /** The request to replace the old copies (pending, targets, told), as Ajustes keeps it from each result. */
    private var request = Settings()

    /** Settings.autoBackupReplacePending, as Ajustes keeps it from each result. */
    private var replaceFlag: Boolean
        get() = request.autoBackupReplacePending
        set(v) {
            request = request.copy(autoBackupReplacePending = v)
        }

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
            replacePending = { replaceFlag },
            replaceTargets = { request.autoBackupReplaceTargets },
            replaceTold = { request.autoBackupReplaceTold },
            onResult = { r ->
                results += r
                errors += r.error
                if (r.folder == folder) r.copies?.let { copies = it }
                request = request.copy(autoBackupUri = folder).withAutoBackupResult(r)
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

    /** Three copies made with the real settings of [settings]; their names. */
    private suspend fun TestScope.threeCopiesInto(s: VaultStore, settings: SettingsStore, target: MemoryTarget): Set<String> {
        repeat(3) { i ->
            clock += 60_000
            s.upsert(note("$i"))
            settle()
        }
        val own = target.files.keys.filter { name.matches(it) }.toSet()
        assertEquals(3, own.size)
        assertEquals(3, settings.settings.value.currentAutoBackupCopies.size)
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
        // R-6: asked with the vault open and no request saved, nothing tries them again as a replacement: no promise.
        assertEquals(AUTO_BACKUP_OLD_NOT_DELETED_NO_RETRY, result.error)
        assertEquals(clock, result.writtenAt) // The new copy is there all the same.
        assertEquals(own + "seguridad-auto-20261026-100400.sgbak", target.files.keys.toSet())
        assertEquals(4, copies.size) // The old ones are still ours (listed), never forgotten.
        assertFalse("CR-1: not a replacement while old copies are left", result.replaced)
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
        assertTrue("The request is kept (persisted) until it is done", replaceFlag)
        assertEquals(own, target.files.keys.toSet()) // Nothing deleted, nothing written yet.
        s.unlock(master.toCharArray())
        runCurrent()
        assertEquals(setOf("seguridad-auto-20261026-100400.sgbak"), target.files.keys.toSet())
        assertEquals(1, copies.size)
        assertFalse("Cleared once the new copy is recorded", replaceFlag)
        // It was asked for once: the next copy after a change is a normal one and the new copy stays.
        clock += 60_000
        s.upsert(note("x"))
        settle()
        assertEquals(2, target.files.size)
    }

    /** The settings file of "the phone", which a restart reads again. */
    private fun settingsFile() = File(tmp.root, "settings.json")

    /** An AutoBackup over [s] that keeps its results in [settings] (the real ones, on disk), as the app does. */
    private fun TestScope.persistent(s: VaultStore, settings: SettingsStore, target: MemoryTarget): AutoBackup {
        val a = AutoBackup(
            s,
            folder = { settings.settings.value.autoBackupUri },
            open = { target },
            backgroundScope,
            now = { clock },
            keep = { 5 },
            lastCopyAt = { Long.MAX_VALUE },
            copies = { settings.settings.value.currentAutoBackupCopies },
            replacePending = { settings.settings.value.autoBackupReplacePending },
            replaceTargets = { settings.settings.value.autoBackupReplaceTargets },
            replaceTold = { settings.settings.value.autoBackupReplaceTold },
            onResult = { r -> settings.update { it.withAutoBackupResult(r) } },
            zone = ZoneOffset.UTC,
        )
        return a.also { runCurrent() }
    }

    @Test fun aReplacementAskedWhileLockedSurvivesARestartAndRunsAtTheNextUnlock() = runTest {
        val s = opened()
        val target = MemoryTarget()
        val settings1 = SettingsStore(settingsFile(), Dispatchers.Unconfined).apply {
            load()
            update { it.copy(autoBackupUri = FOLDER) }
        }
        val a = persistent(s, settings1, target)
        val own = threeCopiesInto(s, settings1, target)
        s.lock() // As after «Cambiar contraseña maestra».
        runCurrent()
        clock += 60_000
        assertEquals(AUTO_BACKUP_REPLACE_WAITS, a.replaceOldCopies().error)
        assertTrue(settings1.settings.value.autoBackupReplacePending)
        assertEquals(own, target.files.keys.toSet())

        // The process dies: a new settings store and a new AutoBackup read what is on disk.
        val settings2 = SettingsStore(settingsFile(), Dispatchers.Unconfined).apply { load() }
        assertTrue("Persisted", settings2.settings.value.autoBackupReplacePending)
        val s2 = store().apply { load() }
        val a2 = persistent(s2, settings2, target)
        s2.unlock(master.toCharArray())
        runCurrent()
        assertEquals(setOf("seguridad-auto-20261026-100400.sgbak"), target.files.keys.toSet())
        assertEquals(1, settings2.settings.value.currentAutoBackupCopies.size)
        assertFalse(settings2.settings.value.autoBackupReplacePending)
        assertFalse("Saved too", SettingsStore(settingsFile(), Dispatchers.Unconfined).apply { load() }.settings.value.autoBackupReplacePending)
        // The outcome waits (in the object, not in a screen) until it has been shown, then it is gone.
        assertNull(a2.replaceOutcome.value!!.error)
        a2.replaceOutcomeShown(a2.replaceOutcome.value!!)
        assertNull(a2.replaceOutcome.value)
    }

    @Test fun theReplacementStaysAskedWhileTheNewCopyFailsAndIsClearedOnlyAfterItWorks() = runTest {
        val s = opened()
        val target = MemoryTarget()
        val settings = SettingsStore(settingsFile(), Dispatchers.Unconfined).apply {
            load()
            update { it.copy(autoBackupUri = FOLDER) }
        }
        val a = persistent(s, settings, target)
        val own = threeCopiesInto(s, settings, target)
        s.lock()
        runCurrent()
        a.replaceOldCopies()
        assertTrue(settings.settings.value.autoBackupReplacePending)

        // The next unlock cannot write the copy: the old ones stay, the request stays, the error is recorded and told.
        target.failAfterBytes = 100
        clock += 60_000
        s.unlock(master.toCharArray())
        runCurrent()
        assertEquals(own, target.files.keys.toSet())
        assertTrue(settings.settings.value.autoBackupReplacePending)
        assertEquals(AUTO_BACKUP_WRITE_FAILED, settings.settings.value.lastAutoBackupError)
        assertEquals(own.size, settings.settings.value.currentAutoBackupCopies.size)
        assertEquals(AUTO_BACKUP_WRITE_FAILED, a.replaceOutcome.value!!.error)
        a.replaceOutcomeShown(a.replaceOutcome.value!!)

        // Locked and opened again with room: it works, and only then is the request cleared.
        target.failAfterBytes = null
        s.lock()
        runCurrent()
        clock += 60_000
        s.unlock(master.toCharArray())
        runCurrent()
        assertEquals(setOf("seguridad-auto-20261026-100500.sgbak"), target.files.keys.toSet())
        assertFalse(settings.settings.value.autoBackupReplacePending)
        assertNull(settings.settings.value.lastAutoBackupError)
        assertNull(a.replaceOutcome.value!!.error)
    }

    // CR-1: a replacement whose old copies could not all be deleted is not done: the request stays and the next copy retries.
    @Test fun aReplacementThatCouldNotDeleteTheOldCopiesStaysAskedAndIsRetried() = runTest {
        val s = opened()
        val target = MemoryTarget()
        val settings = SettingsStore(settingsFile(), Dispatchers.Unconfined).apply {
            load()
            update { it.copy(autoBackupUri = FOLDER) }
        }
        val a = persistent(s, settings, target)
        val own = threeCopiesInto(s, settings, target)
        s.lock() // As after «Cambiar contraseña maestra».
        runCurrent()
        a.replaceOldCopies()
        assertTrue(settings.settings.value.autoBackupReplacePending)

        // The unlock writes the new copy, but the provider refuses every deletion.
        target.failDelete = IOException("sin permiso")
        clock += 60_000
        s.unlock(master.toCharArray())
        runCurrent()
        val first = "seguridad-auto-20261026-100400.sgbak"
        assertEquals(own + first, target.files.keys.toSet())
        assertTrue("The request stays: the old copies (old password) are still there", settings.settings.value.autoBackupReplacePending)
        assertTrue("And it survives a restart", SettingsStore(settingsFile(), Dispatchers.Unconfined).apply { load() }.settings.value.autoBackupReplacePending)
        assertEquals(AUTO_BACKUP_OLD_NOT_DELETED, settings.settings.value.lastAutoBackupError)
        assertEquals(AUTO_BACKUP_OLD_NOT_DELETED, a.replaceOutcome.value!!.error) // Told.
        assertFalse(a.replaceOutcome.value!!.replaced)
        a.replaceOutcomeShown(a.replaceOutcome.value!!)

        // A provider that keeps refusing: each copy retries, none is told again (one notice for the same outcome).
        repeat(2) { i ->
            clock += 60_000
            s.upsert(note("r$i"))
            settle()
            assertNull(a.replaceOutcome.value)
            assertTrue(settings.settings.value.autoBackupReplacePending)
        }

        // The provider lets it delete again: the next copy deletes the old ones, and only then is the request done. The
        // copies made since the request (new password) follow «Copias que guardar» (M4).
        target.failDelete = null
        clock += 60_000
        s.upsert(note("ok"))
        settle()
        assertEquals((4..7).map { "seguridad-auto-20261026-10%02d00.sgbak".format(it) }.toSet(), target.files.keys.toSet())
        assertFalse(settings.settings.value.autoBackupReplacePending)
        assertNull(settings.settings.value.lastAutoBackupError)
        assertEquals(4, settings.settings.value.currentAutoBackupCopies.size)
        assertNull(a.replaceOutcome.value!!.error)
        assertTrue(a.replaceOutcome.value!!.replaced)
    }

    // CR-1: the rotation itself failing (the folder could not be listed after the copy) does not count as replaced either.
    @Test fun aReplacementWhoseRotationThrowsStaysAsked() = runTest {
        val s = opened()
        val target = MemoryTarget()
        val settings = SettingsStore(settingsFile(), Dispatchers.Unconfined).apply {
            load()
            update { it.copy(autoBackupUri = FOLDER) }
        }
        val a = persistent(s, settings, target)
        val own = threeCopiesInto(s, settings, target)
        s.lock()
        runCurrent()
        a.replaceOldCopies()
        // copyTo lists once (the free name), rotate once more: that second list throws.
        target.failListAt = target.listCount + 1
        clock += 60_000
        s.unlock(master.toCharArray())
        runCurrent()
        assertEquals(own + "seguridad-auto-20261026-100400.sgbak", target.files.keys.toSet())
        assertTrue(settings.settings.value.autoBackupReplacePending)
        assertEquals(AUTO_BACKUP_OLD_NOT_DELETED, a.replaceOutcome.value!!.error)
        // Retried at the next unlock, and done.
        s.lock()
        runCurrent()
        clock += 60_000
        s.unlock(master.toCharArray())
        runCurrent()
        // The old ones went; the copy made since the request stays (M4).
        assertEquals(setOf("seguridad-auto-20261026-100400.sgbak", "seguridad-auto-20261026-100500.sgbak"), target.files.keys.toSet())
        assertFalse(settings.settings.value.autoBackupReplacePending)
    }

    @Test fun aFolderThatKeepsFailingDoesNotOpenTheOutcomeAgainAndTheSuccessIsToldOnce() = runTest {
        val s = opened()
        val target = MemoryTarget()
        val settings = SettingsStore(settingsFile(), Dispatchers.Unconfined).apply {
            load()
            update { it.copy(autoBackupUri = FOLDER) }
        }
        val a = persistent(s, settings, target)
        threeCopiesInto(s, settings, target)
        s.lock()
        runCurrent()
        a.replaceOldCopies()
        target.failAfterBytes = 100
        s.unlock(master.toCharArray())
        runCurrent()
        assertEquals(AUTO_BACKUP_WRITE_FAILED, a.replaceOutcome.value!!.error) // Told once, after the unlock.
        a.replaceOutcomeShown(a.replaceOutcome.value!!)
        // Copies after edits retry the replacement and fail the same way: nothing opens again.
        repeat(3) { i ->
            clock += 60_000
            s.upsert(note("f$i"))
            settle()
            assertNull(a.replaceOutcome.value)
        }
        assertTrue(settings.settings.value.autoBackupReplacePending)
        // It works at last, from a copy after an edit: that success is told, once.
        target.failAfterBytes = null
        clock += 60_000
        s.upsert(note("ok"))
        settle()
        assertNull(a.replaceOutcome.value!!.error)
        assertFalse(settings.settings.value.autoBackupReplacePending)
        a.replaceOutcomeShown(a.replaceOutcome.value!!)
        clock += 60_000
        s.upsert(note("after"))
        settle()
        assertNull("Dismissed: it does not come back", a.replaceOutcome.value)
        assertEquals(2, target.files.keys.count { name.matches(it) }) // Normal rotation again: no replacement.
    }

    /** Settings on disk with the copies on, an AutoBackup over them, and three copies made: for the M2 to M4 tests. */
    private suspend fun TestScope.persistentWithThree(keep: Int = 5): Triple<VaultStore, SettingsStore, Pair<MemoryTarget, AutoBackup>> {
        val s = opened()
        val target = MemoryTarget()
        val settings = SettingsStore(settingsFile(), Dispatchers.Unconfined).apply {
            load()
            update { it.copy(autoBackupUri = FOLDER, autoBackupKeep = keep) }
        }
        val a = persistent(s, settings, target)
        threeCopiesInto(s, settings, target)
        return Triple(s, settings, target to a)
    }

    // M2: with the request kept, failures that alternate are each told once per request, not once per alternation.
    @Test fun alternatingFailuresAreEachToldOncePerRequest() = runTest {
        val (s, settings, ta) = persistentWithThree()
        val (target, a) = ta
        s.lock()
        runCurrent()
        a.replaceOldCopies()
        val told = mutableListOf<String?>()
        suspend fun TestScope.attempt(deletes: Boolean, writes: Boolean) {
            target.failDelete = if (deletes) null else IOException("sin permiso")
            target.failAfterBytes = if (writes) null else 100
            clock += 60_000
            s.lock()
            runCurrent()
            s.unlock(master.toCharArray())
            runCurrent()
            told += a.replaceOutcome.value?.error
            a.replaceOutcome.value?.let(a::replaceOutcomeShown)
        }
        attempt(deletes = false, writes = true) // Written, old copies left.
        attempt(deletes = true, writes = false) // Offline: nothing written.
        attempt(deletes = false, writes = true)
        attempt(deletes = true, writes = false)
        attempt(deletes = false, writes = true)
        assertEquals(listOf(AUTO_BACKUP_OLD_NOT_DELETED, AUTO_BACKUP_WRITE_FAILED, null, null, null), told)
        assertTrue(settings.settings.value.autoBackupReplacePending)
        assertEquals(listOf(AUTO_BACKUP_OLD_NOT_DELETED, AUTO_BACKUP_WRITE_FAILED), settings.settings.value.autoBackupReplaceTold)
        // The success is told, once, and ends the request with what it told.
        attempt(deletes = true, writes = true)
        assertEquals(null, told.last()) // Its error is null: told as a success.
        assertFalse(settings.settings.value.autoBackupReplacePending)
        assertEquals(emptyList<String>(), settings.settings.value.autoBackupReplaceTold)
        assertNull(settings.settings.value.autoBackupReplaceTargets)
    }

    // M2: what was told is saved with the request, so a restart does not open the same failure again.
    @Test fun aFailureAlreadyToldIsNotToldAgainAfterARestart() = runTest {
        val (s, settings, ta) = persistentWithThree()
        val (target, a) = ta
        s.lock()
        runCurrent()
        a.replaceOldCopies()
        target.failDelete = IOException("sin permiso")
        clock += 60_000
        s.unlock(master.toCharArray())
        runCurrent()
        assertEquals(AUTO_BACKUP_OLD_NOT_DELETED, a.replaceOutcome.value!!.error)

        // Cold start: the process died (the dialog was never even closed); everything is read again from disk.
        val settings2 = SettingsStore(settingsFile(), Dispatchers.Unconfined).apply { load() }
        assertEquals(listOf(AUTO_BACKUP_OLD_NOT_DELETED), settings2.settings.value.autoBackupReplaceTold)
        val s2 = store().apply { load() }
        val a2 = persistent(s2, settings2, target)
        clock += 60_000
        s2.unlock(master.toCharArray())
        runCurrent()
        assertNull("Same failure, same request: not told again", a2.replaceOutcome.value)
        assertTrue(settings2.settings.value.autoBackupReplacePending)
        // A different failure is still told, once.
        target.failAfterBytes = 100
        clock += 60_000
        s2.upsert(note("x"))
        settle()
        assertEquals(AUTO_BACKUP_WRITE_FAILED, a2.replaceOutcome.value!!.error)
    }

    // M3: a leftover temporary copy that cannot be deleted keeps the request (it may be a complete copy under the old
    // password); the request is done once it goes.
    @Test fun anUndeletableLeftoverTemporaryCopyKeepsTheRequest() = runTest {
        val (s, settings, ta) = persistentWithThree()
        val (target, a) = ta
        val leftover = "seguridad-auto-20261026-095900.sgbak.tmp"
        target.files[leftover] = ByteArray(10) // A copy whose rename and discard both failed, long ago.
        target.undeletable = { it == leftover }
        s.lock()
        runCurrent()
        a.replaceOldCopies()
        clock += 60_000
        s.unlock(master.toCharArray())
        runCurrent()
        assertEquals(setOf(leftover, "seguridad-auto-20261026-100400.sgbak"), target.files.keys.toSet()) // Every target went.
        assertTrue(settings.settings.value.autoBackupReplacePending)
        assertEquals(emptyList<String>(), settings.settings.value.autoBackupReplaceTargets)
        assertEquals(AUTO_BACKUP_OLD_NOT_DELETED, a.replaceOutcome.value!!.error)
        target.undeletable = { false }
        clock += 60_000
        s.upsert(note("x"))
        settle()
        assertFalse(leftover in target.files)
        assertFalse(settings.settings.value.autoBackupReplacePending)
    }

    // M4: the request deletes the copies listed when it was asked; the ones made after it (new password) follow
    // «Copias que guardar», also while a target that cannot be deleted keeps the request pending.
    @Test fun whileARequestStaysPendingTheNewCopiesFollowKeep() = runTest {
        val (s, settings, ta) = persistentWithThree(keep = 5)
        val (target, a) = ta
        val stuck = "seguridad-auto-20261026-100100.sgbak" // The oldest copy, under the old password.
        target.undeletable = { it == stuck }
        s.lock()
        runCurrent()
        a.replaceOldCopies()
        assertEquals(3, settings.settings.value.autoBackupReplaceTargets!!.size) // Snapshot at the request.
        clock += 60_000
        s.unlock(master.toCharArray()) // 10:04: the two other old copies go.
        runCurrent()
        assertEquals(setOf(stuck, "seguridad-auto-20261026-100400.sgbak"), target.files.keys.toSet())
        assertEquals(1, settings.settings.value.autoBackupReplaceTargets!!.size)
        repeat(6) { i -> // 10:05 to 10:10
            clock += 60_000
            s.upsert(note("n$i"))
            settle()
        }
        assertTrue(settings.settings.value.autoBackupReplacePending)
        val expected = (6..10).map { "seguridad-auto-20261026-10%02d00.sgbak".format(it) }.toSet()
        assertEquals("Five new-password copies kept, as asked, plus the stuck one", expected + stuck, target.files.keys.toSet())
        // The stuck one can be deleted at last: the request is done, and the five stay.
        target.undeletable = { false }
        clock += 60_000
        s.upsert(note("ok"))
        settle()
        assertFalse(settings.settings.value.autoBackupReplacePending)
        assertEquals((7..11).map { "seguridad-auto-20261026-10%02d00.sgbak".format(it) }.toSet(), target.files.keys.toSet())
    }

    // m2 (CR re-review): once every target is gone the replacement is done and said as a success, even if a copy beyond
    // «Copias que guardar» (made after the change, new password) could not be deleted; that one is just tried again.
    @Test fun aSurplusCopyThatCannotBeDeletedDoesNotMakeADoneReplacementAFailure() = runTest {
        val (s, settings, ta) = persistentWithThree(keep = 5)
        val (target, a) = ta
        val stuck = "seguridad-auto-20261026-100100.sgbak"
        target.undeletable = { it == stuck }
        s.lock()
        runCurrent()
        a.replaceOldCopies()
        clock += 60_000
        s.unlock(master.toCharArray()) // 10:04
        runCurrent()
        a.replaceOutcome.value?.let(a::replaceOutcomeShown)
        repeat(6) { i -> // 10:05 to 10:10: five new copies kept
            clock += 60_000
            s.upsert(note("n$i"))
            settle()
        }
        // 10:11: the stuck target can go now, but the surplus new copy (10:06) cannot.
        val surplus = "seguridad-auto-20261026-100600.sgbak"
        target.undeletable = { it == surplus }
        clock += 60_000
        s.upsert(note("ok"))
        settle()
        assertFalse(stuck in target.files)
        assertTrue(surplus in target.files)
        assertFalse(settings.settings.value.autoBackupReplacePending)
        assertNull("A success, not «no se pudieron borrar las antiguas»", a.replaceOutcome.value!!.error)
        assertNull(settings.settings.value.lastAutoBackupError)
        // The surplus one stays listed and goes with the next normal rotation.
        target.undeletable = { false }
        clock += 60_000
        s.upsert(note("after"))
        settle()
        assertFalse(surplus in target.files)
        assertEquals(5, target.files.keys.count { name.matches(it) })
    }

    // m2 (CR re-review): «Volver a intentarlo» with the vault open and nothing pending deletes only the copies listed when the
    // password changed (the snapshot the dialog took), never those made after the change.
    @Test fun aReplacementAskedWithTheVaultOpenDeletesOnlyTheCopiesFromBeforeTheChange() = runTest {
        val (s, settings, ta) = persistentWithThree(keep = 5)
        val (target, a) = ta
        val beforeChange = settings.settings.value.currentAutoBackupCopies
        repeat(2) { i -> // Copies made after the change (the vault was opened again with the new password).
            clock += 60_000
            s.upsert(note("n$i"))
            settle()
        }
        clock += 60_000
        val r = a.replaceOldCopies(beforeChange)
        assertNull(r.error)
        assertEquals(
            setOf("seguridad-auto-20261026-100400.sgbak", "seguridad-auto-20261026-100500.sgbak", "seguridad-auto-20261026-100600.sgbak"),
            target.files.keys.toSet(),
        )
    }

    // M4: a request saved before 1.3.0 has no targets: its first attempt deletes every listed copy but the new one, as
    // before, and what is left becomes the targets; from then on the new copies follow «Copias que guardar».
    @Test fun aRequestWithoutTargetsFallsBackToReplacingEverythingOnce() = runTest {
        val (s, settings, ta) = persistentWithThree(keep = 5)
        val (target, _) = ta
        val stuck = "seguridad-auto-20261026-100100.sgbak"
        target.undeletable = { it == stuck }
        s.lock()
        runCurrent()
        settings.update { it.copy(autoBackupReplacePending = true, autoBackupReplaceTargets = null) } // As 1.2.x saved it.
        clock += 60_000
        s.unlock(master.toCharArray())
        runCurrent()
        assertEquals(setOf(stuck, "seguridad-auto-20261026-100400.sgbak"), target.files.keys.toSet())
        assertEquals(1, settings.settings.value.autoBackupReplaceTargets!!.size)
        repeat(2) { i ->
            clock += 60_000
            s.upsert(note("n$i"))
            settle()
        }
        assertEquals(
            setOf(stuck, "seguridad-auto-20261026-100400.sgbak", "seguridad-auto-20261026-100500.sgbak", "seguridad-auto-20261026-100600.sgbak"),
            target.files.keys.toSet(),
        )
    }

    @Test fun anOutcomeNotShownYetSurvivesUntilItIsAcknowledged() = runTest {
        val s = opened()
        val target = MemoryTarget()
        val settings = SettingsStore(settingsFile(), Dispatchers.Unconfined).apply {
            load()
            update { it.copy(autoBackupUri = FOLDER) }
        }
        val a = persistent(s, settings, target)
        threeCopiesInto(s, settings, target)
        s.lock()
        runCurrent()
        a.replaceOldCopies()
        s.unlock(master.toCharArray())
        runCurrent()
        // Nobody collected anything (the activity was being re-created): it is still there for whoever looks next.
        val first = a.replaceOutcome.value
        assertNull(first!!.error)
        assertEquals(first, a.replaceOutcome.value)
    }

    /** A folder whose copies fail, a replacement asked while locked whose failure was told and shown: [a]'s state then. */
    private suspend fun TestScope.failureToldAndShown(): Triple<VaultStore, MemoryTarget, AutoBackup> {
        val s = opened()
        val target = MemoryTarget()
        val settings = SettingsStore(settingsFile(), Dispatchers.Unconfined).apply {
            load()
            update { it.copy(autoBackupUri = FOLDER) }
        }
        val a = persistent(s, settings, target)
        threeCopiesInto(s, settings, target)
        s.lock()
        runCurrent()
        a.replaceOldCopies()
        target.failAfterBytes = 100
        s.unlock(master.toCharArray())
        runCurrent()
        assertEquals(AUTO_BACKUP_WRITE_FAILED, a.replaceOutcome.value!!.error)
        a.replaceOutcomeShown(a.replaceOutcome.value!!)
        assertTrue(settings.settings.value.autoBackupReplacePending)
        return Triple(s, target, a)
    }

    @Test fun aNewRequestWhileOpenStartsTheOnceRuleAgain() = runTest {
        val (s, _, a) = failureToldAndShown()
        // Asked again with the vault open (the direct path, not waitForUnlock): the caller gets the failure.
        assertEquals(AUTO_BACKUP_WRITE_FAILED, a.replaceOldCopies().error)
        assertNull(a.replaceOutcome.value)
        // The request is still saved; the next unlock retries it and fails the same way: that outcome belongs to the
        // new request, so it is told, once.
        s.lock()
        runCurrent()
        clock += 60_000
        s.unlock(master.toCharArray())
        runCurrent()
        assertEquals(AUTO_BACKUP_WRITE_FAILED, a.replaceOutcome.value!!.error)
        a.replaceOutcomeShown(a.replaceOutcome.value!!)
        s.lock()
        runCurrent()
        clock += 60_000
        s.unlock(master.toCharArray())
        runCurrent()
        assertNull("Same request, same outcome: not told again", a.replaceOutcome.value)
    }

    @Test fun closingTheDialogAcknowledgesOnlyTheOutcomeItShowed() = runTest {
        val (s, target, a) = failureToldAndShown()
        // A new request while locked: it fails again after the unlock, and that is on screen.
        s.lock()
        runCurrent()
        a.replaceOldCopies()
        s.unlock(master.toCharArray())
        runCurrent()
        val onScreen = a.replaceOutcome.value!!
        assertEquals(AUTO_BACKUP_WRITE_FAILED, onScreen.error)
        // Before the user taps «Entendido», a copy after an edit does the replacement: a different outcome.
        target.failAfterBytes = null
        clock += 60_000
        s.upsert(note("ok"))
        settle()
        a.replaceOutcomeShown(onScreen)
        val next = a.replaceOutcome.value
        assertNull("The success arrived after what was shown: it is still to be told", next!!.error)
        a.replaceOutcomeShown(next)
        assertNull(a.replaceOutcome.value)
    }

    @Test fun replaceStatusNeverClaimsDoneWithoutARecordedReplacement() {
        val waits = AUTO_BACKUP_REPLACE_WAITS
        assertEquals(ReplaceStatus.NotAsked, replaceStatus(0, false, null, false, null, null))
        // The call said it could not save the request: after a rotation (answer kept) it is still a failure.
        assertEquals(ReplaceStatus.Failed(AUTO_BACKUP_FAILED), replaceStatus(100, true, AUTO_BACKUP_FAILED, false, null, null))
        assertEquals(ReplaceStatus.Done, replaceStatus(100, true, null, false, null, 150))
        assertEquals(ReplaceStatus.Waits, replaceStatus(100, true, waits, true, null, null))
        // The answer was lost (re-created while it ran): only the settings can say.
        assertEquals(ReplaceStatus.Waits, replaceStatus(100, false, null, true, null, null))
        assertEquals(ReplaceStatus.Failed(AUTO_BACKUP_WRITE_FAILED), replaceStatus(100, false, null, false, AUTO_BACKUP_WRITE_FAILED, 90))
        assertEquals(ReplaceStatus.Done, replaceStatus(100, false, null, false, null, 150))
        // Nothing recorded after the request, nothing waiting, no error: unknown, never «done».
        assertEquals(ReplaceStatus.Unknown, replaceStatus(100, false, null, false, null, null))
        assertEquals(ReplaceStatus.Unknown, replaceStatus(100, false, null, false, null, 99))
        // T11-b: the call said «waits», and then the settings record that it ran: the record wins.
        assertEquals(ReplaceStatus.Done, replaceStatus(100, true, waits, false, null, 150))
        assertEquals(ReplaceStatus.Failed(AUTO_BACKUP_OLD_NOT_DELETED), replaceStatus(100, true, waits, false, AUTO_BACKUP_OLD_NOT_DELETED, 150))
        // M1: still pending, but a copy was written after the request and the last attempt failed: that failure is said
        // (with «Volver a intentarlo»), whatever it was, never «se hará en cuanto abras la bóveda».
        assertEquals(ReplaceStatus.Failed(AUTO_BACKUP_WRITE_FAILED), replaceStatus(100, true, waits, true, AUTO_BACKUP_WRITE_FAILED, 150))
        assertEquals(ReplaceStatus.Failed(AUTO_BACKUP_NO_PERMISSION), replaceStatus(100, true, waits, true, AUTO_BACKUP_NO_PERMISSION, 150))
        assertEquals(ReplaceStatus.Failed(AUTO_BACKUP_NO_FOLDER), replaceStatus(100, false, null, true, AUTO_BACKUP_NO_FOLDER, 150))
        // Nothing written after the request: it still waits, unless an attempt for this request already failed and was
        // told (m3: the first attempt after the unlock was offline, so no copy was written).
        assertEquals(ReplaceStatus.Waits, replaceStatus(100, true, waits, true, AUTO_BACKUP_WRITE_FAILED, 99))
        assertEquals(ReplaceStatus.Failed(AUTO_BACKUP_WRITE_FAILED), replaceStatus(100, true, waits, true, AUTO_BACKUP_WRITE_FAILED, 99, toldSinceRequest = true))
        assertEquals(ReplaceStatus.Failed(AUTO_BACKUP_NO_FOLDER), replaceStatus(100, false, null, true, AUTO_BACKUP_NO_FOLDER, null, toldSinceRequest = true))
        assertEquals(ReplaceStatus.Waits, replaceStatus(100, true, waits, true, null, null, toldSinceRequest = true))
        assertEquals(ReplaceStatus.Waits, replaceStatus(100, true, waits, false, null, 99))
        // CR-1: the copy after the request was written but its old copies are left: that is said, though it is retried.
        assertEquals(ReplaceStatus.Failed(AUTO_BACKUP_OLD_NOT_DELETED), replaceStatus(100, true, waits, true, AUTO_BACKUP_OLD_NOT_DELETED, 150))
        assertEquals(ReplaceStatus.Failed(AUTO_BACKUP_OLD_NOT_DELETED), replaceStatus(100, false, null, true, AUTO_BACKUP_OLD_NOT_DELETED, 150))
        // An older copy's error (before the request) still waits.
        assertEquals(ReplaceStatus.Waits, replaceStatus(100, true, waits, true, AUTO_BACKUP_OLD_NOT_DELETED, 99))
    }

    @Test fun cancellingTheCallerDoesNotCutTheReplacement() = runTest {
        val s = opened()
        val target = MemoryTarget()
        val a = auto(s, target)
        val own = threeCopies(s, target)
        var caller: Job? = null
        // The caller (the dialog's scope, on a rotation) is cancelled while the new copy is being written.
        target.onWrite = { caller?.cancel() }
        clock += 60_000
        caller = launch { a.replaceOldCopies() }
        runCurrent()
        assertTrue(caller.isCancelled)
        // The work went on: the new copy is on the list, the old ones are gone, nothing is left outside the list.
        val fresh = "seguridad-auto-20261026-100400.sgbak"
        assertEquals(setOf(fresh), target.files.keys.toSet())
        assertTrue(own.none { it in target.files })
        assertEquals(1, copies.size)
        assertEquals(copies, results.last().copies)
        assertFalse(a.replacing.value)
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
