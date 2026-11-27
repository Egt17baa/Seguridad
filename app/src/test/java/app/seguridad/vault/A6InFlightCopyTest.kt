package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import app.seguridad.imports.ApplyResult
import app.seguridad.imports.IMPORT_COPY_MASTER_CHANGED
import app.seguridad.imports.ImportFlow
import app.seguridad.imports.ImportOutcome
import app.seguridad.settings.withResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.ZoneOffset

/**
 * A folder in memory, as SAF (ids change with a rename). [onWrite] runs once, at the first bytes written, with the name
 * being written (a slow upload, during which the user changes the master password). A name in [undeletable] cannot be
 * deleted (a provider that refuses).
 */
private class InFlightFolder : BackupTarget {
    val files = LinkedHashMap<String, ByteArray>()
    private val ids = HashMap<String, String>()
    private var next = 0
    var onWrite: ((String) -> Unit)? = null
    val undeletable = HashSet<String>()

    fun idOf(n: String) = ids.getOrPut(n) { "doc-${next++}" }

    override suspend fun create(name: String): BackupFile {
        files[name] = ByteArray(0)
        ids[name] = "doc-${next++}"
        return object : BackupFile {
            var cur = name
            override val id: String get() = idOf(cur)
            override suspend fun write(writer: suspend (OutputStream) -> Unit) {
                writer(object : OutputStream() {
                    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        onWrite?.let { h -> onWrite = null; h(cur) }
                        files[cur] = files.getValue(cur) + b.copyOfRange(off, off + len)
                    }
                })
            }
            override fun rename(to: String) {
                files[to] = files.remove(cur)!!
                ids.remove(cur)
                ids[to] = "doc-${next++}"
                cur = to
            }
            override fun delete() {
                files.remove(cur)
                ids.remove(cur)
            }
        }
    }

    override fun list(prefix: String) = files.keys.filter { it.startsWith(prefix) }.map { BackupEntry(idOf(it), it) }

    override fun delete(id: String) {
        val n = ids.entries.firstOrNull { it.value == id }?.key ?: return
        if (n in undeletable) throw IOException("refused")
        files.remove(n)
        ids.remove(n)
    }
}

/**
 * C-2 (2.1.2): a copy whose snapshot was taken under the old master password and that is still being written when the
 * password changes. Before, it was listed after the A6 dialog read its copies, so A6 said «done» with that copy, which
 * opens with the old password, still in the folder. Now it is deleted at once; if it cannot be, it joins the lists A6
 * deletes. Dummy data only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class A6InFlightCopyTest {
    @get:Rule val tmp = TemporaryFolder()
    private var clock = 1_793_008_800_000L
    private val uri = "content://com.android.externalstorage.documents/tree/primary%3ACopias"
    private val old = "maestra-antigua-ficticia"
    private val new = "maestra-nueva-ficticia-larga"

    private fun store(dir: File) =
        VaultStore(File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) })

    private fun note(id: String) = VaultEntry(id, EntryType.NOTE, "Nota $id", notes = "dato ficticio $id", createdAt = 0, updatedAt = 0)

    /** One vault, one folder, the settings of its target kept here as the app keeps them, and the A6 dialog. */
    private inner class Setup(scope: TestScope) {
        val s = store(tmp.newFolder()).apply { runBlocking { load(); create(old.toCharArray()) } }
        val target = InFlightFolder()
        var req = BackupTargetSettings(uri, 5, BackupSchedule.ON_CHANGE)
        var imports: List<String> = emptyList()
        val results = ArrayList<AutoBackupResult>()
        lateinit var dialog: MasterChanged
        val a = oneTargetBackup(
            s, folder = { uri }, open = { target }, scope.backgroundScope, now = { clock }, keep = { 5 },
            copies = { req.copies }, replacePending = { req.replacePending }, replaceTargets = { req.replaceTargets },
            replaceTold = { req.replaceTold }, importCopies = { imports },
            onResult = { r -> results += r; req = req.copy(importCopies = imports).withResult(r); imports = req.importCopies },
            zone = ZoneOffset.UTC,
            onImportCopy = { _, id, _ -> imports = imports + id },
            onOldPasswordCopy = { _, id -> dialog.addOldCopy(id) },
        )

        init {
            dialog = MasterChanged(scope.backgroundScope, replace = { a.replaceOldCopies(it) }, now = { clock }, oldCopies = { req.copies + imports })
        }

        /** The next write changes the master password and opens the A6 dialog, as ChangeMaster does; gives the copy's final name. */
        fun changeMasterDuringNextWrite(cannotDelete: Boolean = false): () -> String {
            var name: String? = null
            target.onWrite = { written ->
                name = written.removeSuffix(".tmp")
                if (cannotDelete) target.undeletable += name!!
                runBlocking { assertTrue(s.changeMaster(new.toCharArray())) }
                dialog.changed(fingerprintFailed = false)
            }
            return { name!! }
        }

        suspend fun TestScope.change(id: String) {
            s.upsert(note(id))
            advanceTimeBy(AUTO_BACKUP_DEBOUNCE_MS + 1)
            runCurrent()
        }
    }

    private fun opensWith(bytes: ByteArray, password: String): Boolean = runBlocking {
        val other = store(tmp.newFolder()).apply { load() }
        try {
            other.importReplace(ByteArrayInputStream(bytes), password.toCharArray())
            true
        } catch (e: Exception) {
            false
        }
    }

    @Test fun inFlightCopyAcrossChangeMasterIsDeleted() = runTest {
        val k = Setup(this)
        runCurrent()
        with(k) { change("1") }
        val first = k.req.copies.toList()
        assertTrue(first.isNotEmpty())
        val inFlight = k.changeMasterDuringNextWrite()
        clock += 60_000
        with(k) { change("2") }
        val snapshot = k.dialog.state.value.oldCopies
        assertEquals(first, snapshot)
        // Deleted at once, never listed.
        assertFalse(inFlight() in k.target.files)
        assertEquals(first, k.req.copies)
        // A6 after the unlock with the new password: done, and no copy opens with the old one.
        clock += 60_000
        k.s.unlock(new.toCharArray())
        runCurrent()
        val r = k.a.replaceOldCopies(snapshot)
        runCurrent()
        assertTrue(r.replaced)
        assertNull(r.error)
        assertFalse(inFlight() in k.target.files)
        for ((name, bytes) in k.target.files) assertFalse(name, opensWith(bytes, old))
    }

    @Test fun inFlightCopyThatCannotBeDeletedJoinsA6() = runTest {
        val k = Setup(this)
        runCurrent()
        with(k) { change("1") }
        val inFlight = k.changeMasterDuringNextWrite(cannotDelete = true)
        clock += 60_000
        with(k) { change("2") }
        val id = k.target.idOf(inFlight())
        // Still there (the provider refused), so it is on the list of copies (the rotation) and in the A6 dialog's.
        assertTrue(inFlight() in k.target.files)
        assertTrue(id in k.req.copies)
        assertTrue(id in k.dialog.state.value.oldCopies.orEmpty())
        assertTrue(opensWith(k.target.files.getValue(inFlight()), old))
        // The provider accepts again; the user unlocks and confirms A6: that copy goes too.
        k.target.undeletable.clear()
        clock += 60_000
        k.s.unlock(new.toCharArray())
        runCurrent()
        assertTrue(k.dialog.replace())
        runCurrent()
        val answered = k.dialog.state.value
        assertTrue(answered.answered)
        assertNull(answered.answerError)
        assertFalse(inFlight() in k.target.files)
        for ((name, bytes) in k.target.files) assertFalse(name, opensWith(bytes, old))
    }

    @Test fun inFlightCopyThatCannotBeDeletedJoinsASavedRequest() = runTest {
        val k = Setup(this)
        runCurrent()
        with(k) { change("1") }
        // An A6 request saved while locked (after an earlier change), waiting for the unlock, with its targets.
        val earlier = k.req.copies.toList()
        k.req = k.req.copy(replacePending = true, replaceTargets = earlier)
        val inFlight = k.changeMasterDuringNextWrite(cannotDelete = true)
        clock += 60_000
        with(k) { change("2") }
        val id = k.target.idOf(inFlight())
        assertTrue(inFlight() in k.target.files)
        assertTrue(k.req.replacePending)
        assertTrue(id in k.req.replaceTargets.orEmpty())
        assertTrue(id in k.req.copies)
    }

    /** «Borrar…» pressed while the copy is still written: the request waits for it, and still gets it. */
    @Test fun a6PressedWhileTheCopyIsWrittenStillGetsIt() = runTest {
        val k = Setup(this)
        runCurrent()
        with(k) { change("1") }
        var name: String? = null
        k.target.onWrite = { written ->
            name = written.removeSuffix(".tmp")
            k.target.undeletable += name!!
            runBlocking { assertTrue(k.s.changeMaster(new.toCharArray())) }
            k.dialog.changed(fingerprintFailed = false)
            assertTrue(k.dialog.replace()) // Read the dialog's list now, without this copy.
        }
        clock += 60_000
        with(k) { change("2") }
        val id = k.target.idOf(name!!)
        // Locked: the request is saved with its targets, this copy among them.
        assertTrue(k.req.replacePending)
        assertTrue(id in k.req.replaceTargets.orEmpty())
        k.target.undeletable.clear()
        clock += 60_000
        k.s.unlock(new.toCharArray())
        runCurrent()
        assertFalse(k.req.replacePending)
        assertFalse(name in k.target.files)
        for ((n, bytes) in k.target.files) assertFalse(n, opensWith(bytes, old))
    }

    @Test fun aCopyFinishedBeforeTheChangeIsKept() = runTest {
        val k = Setup(this)
        runCurrent()
        with(k) { change("1") }
        val written = k.target.files.keys.toSet()
        val listed = k.req.copies.toList()
        val at = k.req.lastAt
        assertTrue(k.s.changeMaster(new.toCharArray()))
        k.dialog.changed(fingerprintFailed = false)
        runCurrent()
        assertEquals(written, k.target.files.keys)
        assertEquals(written.map { k.target.idOf(it) }.toSet(), listed.toSet())
        assertEquals(listed, k.req.copies)
        assertEquals(k.req.copies, k.dialog.state.value.oldCopies)
        assertEquals(at, k.req.lastAt)
    }

    @Test fun importCopyAcrossChangeMasterIsDeletedAndImportIsRefused() = runTest {
        val k = Setup(this)
        runCurrent()
        val inFlight = k.changeMasterDuringNextWrite()
        var applied = false
        val outcome = ImportFlow(copy = { k.a.copyBeforeImport()!! }, apply = { applied = true; ApplyResult.Stale }).confirm()
        runCurrent()
        assertEquals(ImportOutcome.CopyFailed(IMPORT_COPY_MASTER_CHANGED), outcome)
        assertFalse(applied)
        assertTrue(inFlight().startsWith(IMPORT_COPY_PREFIX))
        assertFalse(inFlight() in k.target.files)
        assertTrue(k.imports.isEmpty())
    }

    @Test fun importCopyThatCannotBeDeletedGoesToImportCopiesNotToTheRotation() = runTest {
        val k = Setup(this)
        runCurrent()
        val inFlight = k.changeMasterDuringNextWrite(cannotDelete = true)
        var applied = false
        val outcome = ImportFlow(copy = { k.a.copyBeforeImport()!! }, apply = { applied = true; ApplyResult.Stale }).confirm()
        runCurrent()
        assertEquals(ImportOutcome.CopyFailed(IMPORT_COPY_MASTER_CHANGED), outcome)
        assertFalse(applied)
        val id = k.target.idOf(inFlight())
        assertTrue(inFlight() in k.target.files)
        assertEquals(listOf(id), k.imports)
        assertFalse(id in k.req.copies)
        assertTrue(id in k.dialog.state.value.oldCopies.orEmpty())
        // A6 deletes it once the provider accepts.
        k.target.undeletable.clear()
        clock += 60_000
        k.s.unlock(new.toCharArray())
        runCurrent()
        assertTrue(k.dialog.replace())
        runCurrent()
        assertNull(k.dialog.state.value.answerError)
        assertFalse(inFlight() in k.target.files)
    }

    /** O-3 of the Abogado Digital: «Última copia» never shows the time of a copy that was deleted. */
    @Test fun deletedCopyIsNeverTheLastCopyShown() = runTest {
        val k = Setup(this)
        runCurrent()
        with(k) { change("1") }
        val before = k.req
        assertTrue(before.lastAt != null)
        k.changeMasterDuringNextWrite()
        clock += 60_000
        with(k) { change("2") }
        val last = k.results.last()
        assertNull(last.writtenAt)
        assertNull(last.copies)
        assertNull(last.error)
        assertEquals(before.lastAt, k.req.lastAt)
        assertEquals(before.copiedMark, k.req.copiedMark)
        assertEquals(before.copies, k.req.copies)
    }

    @Test fun changeMasterWithoutCopyInFlightWorksAsToday() = runTest {
        val k = Setup(this)
        runCurrent()
        with(k) { change("1") }
        val oldNames = k.target.files.keys.toSet()
        assertTrue(k.s.changeMaster(new.toCharArray()))
        k.dialog.changed(fingerprintFailed = false)
        clock += 60_000
        k.s.unlock(new.toCharArray())
        runCurrent()
        assertTrue(k.dialog.replace())
        runCurrent()
        assertNull(k.dialog.state.value.answerError)
        assertTrue(oldNames.none { it in k.target.files })
        assertTrue(k.target.files.isNotEmpty())
        for ((name, bytes) in k.target.files) assertTrue(name, opensWith(bytes, new))
        assertEquals(k.target.files.keys.map { k.target.idOf(it) }.toSet(), k.req.copies.toSet())
    }
}
