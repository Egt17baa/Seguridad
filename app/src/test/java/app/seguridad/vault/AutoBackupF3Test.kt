package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import app.seguridad.settings.Settings
import app.seguridad.settings.SettingsStore
import app.seguridad.settings.currentAutoBackupCopies
import app.seguridad.settings.withAutoBackupResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.time.ZoneOffset

/** A folder in memory whose writes can be held (a slow provider) after the snapshot was taken (verifier L3-A). */
private class GatedTarget : BackupTarget {
    val files = LinkedHashMap<String, ByteArray>() // name -> bytes
    val idOfName = HashMap<String, String>()
    private var next = 0
    val deleted = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null
    var lists = 0
    var onList: ((Int) -> Unit)? = null

    override suspend fun create(name: String): BackupFile {
        files[name] = ByteArray(0)
        idOfName[name] = "doc-${next++}"
        return object : BackupFile {
            var current = name
            override val id get() = idOfName.getValue(current)
            override suspend fun write(writer: suspend (OutputStream) -> Unit) {
                val buf = ByteArrayOutputStream()
                writer(buf) // exportBackup: snapshot taken (with the mutex) and serialised
                gate?.let { g -> gate = null; g.await() } // the upload is slow: the vault mutex is free meanwhile
                files[current] = buf.toByteArray()
            }
            override fun rename(to: String) {
                files[to] = files.remove(current)!!
                idOfName[to] = idOfName.remove(current)!!
                current = to
            }
            override fun delete() {
                files.remove(current); idOfName.remove(current)
            }
        }
    }

    override fun list(prefix: String): List<BackupEntry> {
        lists++
        onList?.invoke(lists)
        return files.keys.filter { it.startsWith(prefix) }.map { BackupEntry(idOfName.getValue(it), it) }
    }

    override fun delete(id: String) {
        deleted += id
        val n = idOfName.entries.firstOrNull { it.value == id }?.key ?: return
        files.remove(n); idOfName.remove(n)
    }

    /** Put a file there "by hand" (a copy made before 1.2.1, not on the list). */
    fun putUnlisted(name: String) {
        files[name] = ByteArray(1); idOfName[name] = "doc-${next++}"
    }
}

/** Audit 1.3.0, batch F3: CADENAS-3 + COPIAS-1, COPIAS-2 and COPIAS-3, wired as AppContainer wires them. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoBackupF3Test {
    @get:Rule val tmp = TemporaryFolder()
    private var clock = 1_793_008_800_000L
    private val folderUri = "content://x/tree/primary%3ACopias"

    private lateinit var settings: SettingsStore

    private suspend fun newSettings() = SettingsStore(File(tmp.root, "settings.json"), Dispatchers.Unconfined).apply {
        load(); update { it.copy(autoBackupUri = folderUri) }
    }

    private fun vaultStore(dir: File) = VaultStore(
        File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
        beforeReplace = { settings.forgetAutoBackupCopiesForImport() }, // as AppContainer
    )

    private fun TestScope.auto(s: VaultStore, target: BackupTarget) = AutoBackup(
        s, folder = { settings.settings.value.autoBackupUri }, open = { target }, backgroundScope, now = { clock },
        keep = { 5 }, lastCopyAt = { Long.MAX_VALUE }, copies = { settings.settings.value.currentAutoBackupCopies },
        replacePending = { settings.settings.value.autoBackupReplacePending },
        // As AppContainer.recordAutoBackup: the vault's generation is read under the settings lock.
        onResult = { r -> settings.update { it.withAutoBackupResult(r, s.generation) } }, zone = ZoneOffset.UTC,
    ).also { runCurrent() }

    private fun note(id: String) = VaultEntry(id, EntryType.NOTE, "Nota $id", notes = "s $id", createdAt = 0, updatedAt = 0)

    private fun TestScope.settle() { advanceTimeBy(AUTO_BACKUP_DEBOUNCE_MS + 1); runCurrent() }

    /** The old vault with 3 automatic copies, and the text of another vault to import. */
    private suspend fun setup(): Triple<VaultStore, GatedTarget, String> {
        settings = newSettings()
        val s = vaultStore(tmp.newFolder("a")).apply { load(); create("maestra-a".toCharArray()) }
        val other = VaultStore(File(tmp.newFolder("b"), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { KdfParams(1024, 1, 1) })
            .apply { load(); create("maestra-b".toCharArray()); upsert(note("B")) }
        return Triple(s, GatedTarget(), other.exportText()!!)
    }

    private suspend fun TestScope.threeCopies(s: VaultStore, t: GatedTarget): Set<String> {
        repeat(3) { i -> clock += 60_000; s.upsert(note("old$i")); settle() }
        assertEquals(3, settings.settings.value.currentAutoBackupCopies.size)
        return t.idOfName.values.toSet()
    }

    // ---- CADENAS-3 + COPIAS-1 ----

    @Test fun `generation goes up with create, import and destroy, not with a failed import`() = runTest {
        val (s, _, text) = setup()
        val afterCreate = s.generation
        s.upsert(note("x"))
        assertEquals("an edit is the same vault", afterCreate, s.generation)
        try {
            s.importReplace(text, "mala".toCharArray())
        } catch (e: Exception) {
            // expected: wrong password
        }
        assertEquals("nothing replaced", afterCreate, s.generation)
        s.importReplace(text, "maestra-b".toCharArray())
        val afterImport = s.generation
        assertTrue(afterImport > afterCreate)
        s.destroy()
        assertTrue(s.generation > afterImport)
        s.create("nueva-maestra".toCharArray())
        assertTrue(s.generation > afterImport + 1)
    }

    /** Case (a): the import completes while the copy's upload is held. */
    @Test fun `CADENAS-3 case a, a copy of the replaced vault is never listed and A6 deletes none of its copies`() = runTest {
        val (s, t, text) = setup()
        val a = auto(s, t)
        val old = threeCopies(s, t)
        val gate = CompletableDeferred<Unit>()
        t.gate = gate
        clock += 60_000
        s.upsert(note("old-last"))
        settle() // the copy starts: snapshot of the OLD vault, upload held
        assertFalse(gate.isCompleted)
        s.importReplace(text, "maestra-b".toCharArray())
        assertTrue(settings.settings.value.currentAutoBackupCopies.isEmpty())
        gate.complete(Unit); runCurrent()
        val raceCopy = t.idOfName.getValue("seguridad-auto-20261026-100400.sgbak")
        assertTrue("the old vault's copy is not listed", settings.settings.value.currentAutoBackupCopies.isEmpty())
        clock += 60_000
        val r = a.replaceOldCopies() // A6 with the imported vault open
        assertNull(r.error)
        assertTrue("A6 deletes no copy of the replaced vault: ${t.deleted}", t.deleted.none { it in old + raceCopy })
        assertTrue(raceCopy in t.idOfName.values)
    }

    /** Case (b): the import lands between copies() and onResult (during rotate's list). */
    @Test fun `CADENAS-3 case b, the forgotten list is never written back`() = runTest {
        val (s, t, text) = setup()
        val a = auto(s, t)
        val old = threeCopies(s, t)
        var imported = false
        val listsBefore = t.lists
        t.onList = { n ->
            if (n == listsBefore + 2 && !imported) { // 1st list: freeName in copyTo, 2nd: rotate
                imported = true
                backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { s.importReplace(text, "maestra-b".toCharArray()) }
            }
        }
        clock += 60_000
        s.upsert(note("old-last"))
        settle()
        assertTrue(imported)
        assertTrue("import done", s.state.value.let { it is VaultState.Unlocked && it.entries.any { e -> e.id == "B" } })
        assertTrue("nothing of the old vault listed", settings.settings.value.currentAutoBackupCopies.isEmpty())
        clock += 60_000
        val r = a.replaceOldCopies()
        assertNull(r.error)
        assertTrue("A6 deletes no copy of the replaced vault: ${t.deleted}", t.deleted.none { it in old })
        assertTrue("the old copies are all there", old.all { it in t.idOfName.values })
    }

    @Test fun `CADENAS-3 a stale result recorded after the import keeps neither its list nor clears a request`() {
        val listed = Settings(autoBackupUri = folderUri, autoBackupCopies = listOf("a"), autoBackupCopiesFolder = folderUri, autoBackupReplacePending = true)
        val stale = AutoBackupResult(folderUri, 9L, null, listOf("a", "b"), replaced = true, generation = 1)
        val after = listed.withAutoBackupResult(stale, 2)
        assertEquals(listOf("a"), after.currentAutoBackupCopies)
        assertTrue(after.autoBackupReplacePending)
        assertEquals(9L, after.lastAutoBackupAt)
        // Same generation: recorded as before.
        val same = listed.withAutoBackupResult(stale, 1)
        assertEquals(listOf("a", "b"), same.currentAutoBackupCopies)
        assertFalse(same.autoBackupReplacePending)
    }

    // ---- COPIAS-2 ----

    @Test fun `COPIAS-2 A6 counts the copies with an automatic name it does not have listed, and deletes none of them`() = runTest {
        val (s, t, _) = setup()
        val a = auto(s, t)
        t.putUnlisted("seguridad-auto-20250101-000000.sgbak") // pre-1.2.1 copy
        t.putUnlisted("seguridad-auto-20250101-000000 (1).sgbak") // not exactly the name: not counted
        threeCopies(s, t)
        clock += 60_000
        val r = a.replaceOldCopies()
        assertNull(r.error)
        assertEquals(1, r.unlistedLeft)
        assertTrue("seguridad-auto-20250101-000000.sgbak" in t.files.keys)
        assertEquals(AUTO_BACKUP_REPLACED + " " + autoBackupUnlistedText(1), replacedText(r.unlistedLeft))
    }

    @Test fun `COPIAS-2 with every copy listed the text has no addition`() = runTest {
        val (s, t, _) = setup()
        val a = auto(s, t)
        threeCopies(s, t)
        clock += 60_000
        val r = a.replaceOldCopies()
        assertNull(r.error)
        assertEquals(0, r.unlistedLeft)
        assertEquals(AUTO_BACKUP_REPLACED, replacedText(0))
        assertTrue(autoBackupUnlistedText(2).contains("2 copias"))
        assertTrue(autoBackupUnlistedText(1).contains("1 copia "))
    }

    // ---- COPIAS-3 ----

    @Test fun `COPIAS-3 a failed import keeps the pending A6 request and the list`() = runTest {
        val (s, t, text) = setup()
        auto(s, t)
        threeCopies(s, t)
        settings.update { it.copy(autoBackupReplacePending = true) }
        val listBefore = settings.settings.value.currentAutoBackupCopies
        // Make the save fail: vault.bin.tmp is a non-empty directory (works as root too).
        File(tmp.root, "a/vault.bin.tmp").apply { mkdirs(); File(this, "x").writeText("x") }
        val failed = try { s.importReplace(text, "maestra-b".toCharArray()); false } catch (e: Exception) { true }
        assertTrue(failed)
        assertTrue((s.state.value as VaultState.Unlocked).entries.none { it.id == "B" })
        assertTrue("request kept", settings.settings.value.autoBackupReplacePending)
        assertEquals(listBefore, settings.settings.value.currentAutoBackupCopies)
    }

    @Test fun `COPIAS-3 a successful import says when it dropped a pending A6 request`() = runTest {
        val (s, _, text) = setup()
        settings.update { it.copy(autoBackupReplacePending = true) }
        assertTrue(s.importReplace(text, "maestra-b".toCharArray()))
        assertFalse(settings.settings.value.autoBackupReplacePending)
        assertFalse("nothing pending: nothing dropped", s.importReplace(text, "maestra-b".toCharArray()))
    }

    /** As a full disk for the settings: their temporary file is a folder, so every write throws. */
    private fun fillSettingsDisk() = check(File(tmp.root, "settings.json.tmp").mkdir())

    private fun reloaded() = SettingsStore(File(tmp.root, "settings.json"), Dispatchers.Unconfined)

    @Test fun `M1 a failed save puts the list and the request back on disk too`() = runTest {
        val (s, t, text) = setup()
        auto(s, t)
        threeCopies(s, t)
        settings.update { it.copy(autoBackupReplacePending = true) }
        val listBefore = settings.settings.value.currentAutoBackupCopies
        File(tmp.root, "a/vault.bin.tmp").apply { mkdirs(); File(this, "x").writeText("x") }
        assertTrue(runCatching { s.importReplace(text, "maestra-b".toCharArray()) }.isFailure)
        val disk = reloaded().apply { load() }.settings.value
        assertTrue(disk.autoBackupReplacePending)
        assertEquals(listBefore, disk.currentAutoBackupCopies)
    }

    @Test fun `M1 a forget that cannot be written aborts the import and changes nothing`() = runTest {
        val (s, t, text) = setup()
        auto(s, t)
        threeCopies(s, t)
        settings.update { it.copy(autoBackupReplacePending = true) }
        val listBefore = settings.settings.value.currentAutoBackupCopies
        fillSettingsDisk()
        val e = runCatching { s.importReplace(text, "maestra-b".toCharArray()) }.exceptionOrNull()
        assertTrue("$e", e is app.seguridad.crypto.CryptoFailure && e.message == IMPORT_SETTINGS_FAILED)
        assertTrue("still the old vault", (s.state.value as VaultState.Unlocked).entries.none { it.id == "B" })
        assertTrue(settings.settings.value.autoBackupReplacePending)
        assertEquals(listBefore, settings.settings.value.currentAutoBackupCopies)
        // The JSON path and the .sgbak path abort alike.
        val copy = ByteArrayOutputStream().also { s.exportBackup(it) }.toByteArray()
        assertTrue(runCatching { s.importReplace(copy.inputStream(), "maestra-a".toCharArray()) }.exceptionOrNull() is app.seguridad.crypto.CryptoFailure)
        assertEquals(listBefore, settings.settings.value.currentAutoBackupCopies)
    }

    /** The process dies once the forget is on disk and before the vault is saved: the old vault is still the one. */
    @Test fun `M1 a process death between the forget and the save never lets A6 delete a wrong copy`() = runTest {
        val (_, t, text) = setup()
        var dying = false
        val s = VaultStore(
            File(tmp.root, "a/vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) },
            beforeReplace = {
                val prepared = settings.forgetAutoBackupCopiesForImport()
                if (dying) throw IllegalStateException("process killed") // nothing after this runs
                prepared
            },
        ).apply { load(); unlock("maestra-a".toCharArray()) }
        auto(s, t)
        val old = threeCopies(s, t)
        dying = true
        runCatching { s.importReplace(text, "maestra-b".toCharArray()) }
        // Restart: everything is read again from disk.
        settings = reloaded().apply { load() }
        val s2 = vaultStore(File(tmp.root, "a")).apply { load() }
        val a2 = auto(s2, t)
        s2.unlock("maestra-a".toCharArray())
        runCurrent()
        assertTrue("the old vault is still the one", (s2.state.value as VaultState.Unlocked).entries.none { it.id == "B" })
        clock += 60_000
        a2.replaceOldCopies()
        assertTrue("nothing of the vault on disk is deleted by a list it never had: ${t.deleted}", t.deleted.none { it in old })
    }

    @Test fun `M3 the unlisted text does not claim the old password opens them`() {
        assertTrue(autoBackupUnlistedText(1).contains("puede que se abra con una contraseña anterior"))
        assertTrue(autoBackupUnlistedText(2).contains("puede que se abran con una contraseña anterior"))
    }
}
