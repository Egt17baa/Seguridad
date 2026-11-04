package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import app.seguridad.imports.IMPORT_COPY_LOCKED
import app.seguridad.imports.IMPORT_COPY_NO_FOLDER
import app.seguridad.imports.IMPORT_COPY_WRITE_FAILED
import app.seguridad.settings.SettingsStore
import app.seguridad.settings.currentAutoBackupCopies
import app.seguridad.settings.currentImportCopies
import app.seguridad.settings.withAutoBackupResult
import app.seguridad.settings.withImportCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.ZoneOffset

/** A folder in memory, as SAF: ids change with a rename; [renames] false is a provider without FLAG_SUPPORTS_RENAME. */
private class ImportCopyTarget : BackupTarget {
    val files = LinkedHashMap<String, ByteArray>()
    private val ids = HashMap<String, String>()
    private var next = 0
    var renames = true
    var failWrite: Exception? = null
    val created = mutableListOf<String>()

    private fun idOf(name: String) = ids.getOrPut(name) { "doc-${next++}" }

    override suspend fun create(name: String): BackupFile {
        created += name
        files[name] = ByteArray(0)
        ids[name] = "doc-${next++}"
        return object : BackupFile {
            var current = name
            override val id: String get() = idOf(current)
            override val canRename: Boolean get() = renames
            override suspend fun write(writer: suspend (OutputStream) -> Unit) {
                val out = object : OutputStream() {
                    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        failWrite?.let { throw it }
                        files[current] = files.getValue(current) + b.copyOfRange(off, off + len)
                    }
                }
                writer(out)
            }
            override fun rename(to: String) {
                files[to] = files.remove(current)!!
                ids.remove(current)
                ids[to] = "doc-${next++}"
                current = to
            }
            override fun delete() {
                files.remove(current)
                ids.remove(current)
            }
        }
    }

    override fun list(prefix: String) = files.keys.filter { it.startsWith(prefix) }.map { BackupEntry(idOf(it), it) }

    override fun delete(id: String) {
        val name = ids.entries.firstOrNull { it.value == id }?.key ?: return
        files.remove(name)
        ids.remove(name)
    }

    fun idOfName(name: String) = idOf(name)

    /** A file put there by hand or by another vault: not on any list. */
    fun put(name: String) {
        files[name] = byteArrayOf(1)
        idOf(name)
    }
}

/** 1.4.0, Task 8: the copy before an import ([AutoBackup.copyBeforeImport]) and how A6 counts it. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoBackupImportCopyTest {
    @get:Rule val tmp = TemporaryFolder()

    /** 2026-10-26 10:00:00 UTC. */
    private var clock = 1_793_008_800_000L
    private val folder = "content://x/tree/primary%3ACopias"
    private lateinit var settings: SettingsStore

    private val copyName = Regex("seguridad-antes-de-importar-\\d{8}-\\d{6}\\.sgbak")

    private suspend fun setup(): VaultStore {
        settings = SettingsStore(File(tmp.root, "settings.json"), Dispatchers.Unconfined).apply {
            load()
            update { it.copy(autoBackupUri = folder) }
        }
        return VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) })
            .apply { load(); create("maestra".toCharArray()) }
    }

    /** Wired as AppContainer wires it. */
    private fun TestScope.auto(s: VaultStore, target: BackupTarget?, keep: Int = 5) = AutoBackup(
        s, folder = { settings.settings.value.autoBackupUri }, open = { target }, backgroundScope, now = { clock },
        keep = { keep }, lastCopyAt = { Long.MAX_VALUE }, copies = { settings.settings.value.currentAutoBackupCopies },
        replacePending = { settings.settings.value.autoBackupReplacePending },
        replaceTargets = { settings.settings.value.autoBackupReplaceTargets },
        replaceTold = { settings.settings.value.autoBackupReplaceTold },
        onResult = { r -> settings.update { it.withAutoBackupResult(r, s.generation) } },
        zone = ZoneOffset.UTC,
        importCopies = { settings.settings.value.currentImportCopies },
        onImportCopy = { f, id, generation -> settings.update { it.withImportCopy(f, id, generation, s.generation) } },
    ).also { runCurrent() }

    private fun note(id: String) = VaultEntry(id, EntryType.NOTE, "Nota $id", notes = "s $id", createdAt = 0, updatedAt = 0)

    private fun TestScope.settle() {
        advanceTimeBy(AUTO_BACKUP_DEBOUNCE_MS + 1)
        runCurrent()
    }

    @Test fun theCopyHasItsOwnNameAndIsWrittenUnderATemporaryOne() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        val r = auto(s, t).copyBeforeImport()
        assertEquals(CopyResult.Saved("seguridad-antes-de-importar-20261026-100000.sgbak"), r)
        assertEquals(listOf("seguridad-antes-de-importar-20261026-100000.sgbak.tmp"), t.created)
        assertEquals(listOf("seguridad-antes-de-importar-20261026-100000.sgbak"), t.files.keys.toList())
        assertTrue(copyName.matches(t.files.keys.single()))
        // A complete copy of the vault, as the automatic one.
        val bytes = t.files.values.single()
        assertTrue(isBackup(bytes.copyOf(BACKUP_HEAD_BYTES)))
        // A second one in the same second takes the next free name.
        assertEquals(CopyResult.Saved("seguridad-antes-de-importar-20261026-100001.sgbak"), auto(s, t).copyBeforeImport())
    }

    @Test fun itIsListedInImportCopiesAndNotInTheAutomaticOnes() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        auto(s, t).copyBeforeImport()
        val id = t.idOfName(t.files.keys.single())
        assertEquals(listOf(id), settings.settings.value.importCopies)
        assertEquals(folder, settings.settings.value.importCopiesFolder)
        assertTrue(settings.settings.value.autoBackupCopies.isEmpty())
    }

    @Test fun itIsNeverRotated() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        val a = auto(s, t, keep = 3)
        a.copyBeforeImport()
        val mine = t.files.keys.single()
        repeat(6) { i ->
            clock += 60_000
            s.upsert(note("$i"))
            settle()
        }
        assertTrue(mine in t.files)
        assertEquals(3, t.files.keys.count { it.startsWith(AUTO_BACKUP_PREFIX) })
        assertEquals(3, settings.settings.value.autoBackupCopies.size)
        assertFalse(t.idOfName(mine) in settings.settings.value.autoBackupCopies)
        assertEquals(listOf(t.idOfName(mine)), settings.settings.value.importCopies)
    }

    @Test fun theChangeOfMasterPasswordReplacesItWhenTheFolderMatches() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        val a = auto(s, t)
        clock += 60_000
        s.upsert(note("1"))
        settle()
        clock += 60_000
        a.copyBeforeImport()
        val imported = t.idOfName(t.files.keys.first { copyName.matches(it) })
        val auto = settings.settings.value.autoBackupCopies.single()
        // As after «Cambiar contraseña maestra»: locked, the request waits, with the import copy among its targets.
        s.lock()
        runCurrent()
        assertEquals(AUTO_BACKUP_REPLACE_WAITS, a.replaceOldCopies(listOf(auto)).error)
        assertEquals(setOf(auto, imported), settings.settings.value.autoBackupReplaceTargets!!.toSet())
        clock += 60_000
        s.unlock("maestra".toCharArray())
        runCurrent()
        assertFalse(settings.settings.value.autoBackupReplacePending)
        assertTrue(t.files.keys.none { copyName.matches(it) })
        assertEquals(1, t.files.size)
        assertTrue("Forgotten once deleted", settings.settings.value.importCopies.isEmpty())
    }

    @Test fun theReplacementAskedWithTheVaultOpenTakesItToo() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        val a = auto(s, t)
        a.copyBeforeImport()
        val imported = settings.settings.value.importCopies.single()
        clock += 60_000
        val r = a.replaceOldCopies(listOf(imported)) // MasterChanged's list holds the import copies too (AppContainer).
        assertNull(r.error)
        assertTrue(r.replaced)
        assertTrue(t.files.keys.none { copyName.matches(it) })
        assertTrue(settings.settings.value.importCopies.isEmpty())
    }

    @Test fun anotherFolderKeepsItOutOfTheTargets() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        val a = auto(s, t)
        a.copyBeforeImport()
        // The import copy was made in another folder than the one chosen now.
        settings.update { it.copy(importCopiesFolder = "content://x/tree/primary%3AOtra") }
        s.lock()
        runCurrent()
        a.replaceOldCopies(emptyList())
        assertEquals(emptyList<String>(), settings.settings.value.autoBackupReplaceTargets)
    }

    @Test fun theUnlistedNoticeCountsCopiesWithItsName() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        val a = auto(s, t)
        a.copyBeforeImport() // listed: deleted, not counted
        t.put("seguridad-antes-de-importar-20200101-000000.sgbak") // not listed: counted, never deleted
        t.put("seguridad-antes-de-importar-2020.sgbak") // not exactly the name: neither
        clock += 60_000
        val r = a.replaceOldCopies(settings.settings.value.importCopies)
        assertTrue(r.replaced)
        assertEquals(1, r.unlistedLeft)
        assertTrue("seguridad-antes-de-importar-20200101-000000.sgbak" in t.files)
        assertTrue("seguridad-antes-de-importar-2020.sgbak" in t.files)
    }

    @Test fun copyBeforeImportWithoutRenameWritesFinalName() = runTest {
        val s = setup()
        val t = ImportCopyTarget().apply { renames = false }
        val r = auto(s, t).copyBeforeImport()
        val name = "seguridad-antes-de-importar-20261026-100000.sgbak"
        assertEquals(CopyResult.Saved(name), r)
        assertEquals(listOf(name), t.files.keys.toList())
        assertTrue("Complete under its final name", isBackup(t.files.getValue(name).copyOf(BACKUP_HEAD_BYTES)))
        val inMemory = java.io.ByteArrayOutputStream().also { s.exportBackup(it) }.toByteArray()
        assertEquals(inMemory.size, t.files.getValue(name).size)
        assertEquals(listOf(t.idOfName(name)), settings.settings.value.importCopies)
    }

    @Test fun noFolderGivesNull() = runTest {
        val s = setup()
        settings.update { it.copy(autoBackupUri = null) }
        assertNull(auto(s, ImportCopyTarget()).copyBeforeImport())
    }

    @Test fun aFailedCopyLeavesNothingAndSaysSo() = runTest {
        val s = setup()
        val t = ImportCopyTarget().apply { failWrite = IOException("disco lleno") }
        val r = auto(s, t).copyBeforeImport()
        assertEquals(CopyResult.Failed(IMPORT_COPY_WRITE_FAILED), r)
        assertTrue(t.files.isEmpty())
        assertTrue(settings.settings.value.importCopies.isEmpty())
        // A folder that cannot be opened, and a locked vault.
        assertEquals(CopyResult.Failed(IMPORT_COPY_NO_FOLDER), auto(s, null).copyBeforeImport())
        s.lock()
        assertEquals(CopyResult.Failed(IMPORT_COPY_LOCKED), auto(s, ImportCopyTarget()).copyBeforeImport())
    }

    @Test fun aNewVaultForgetsTheList() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        auto(s, t).copyBeforeImport()
        assertEquals(1, settings.settings.value.importCopies.size)
        settings.turnOffAutoBackupForNewVault()
        assertTrue(settings.settings.value.importCopies.isEmpty())
        assertNull(settings.settings.value.importCopiesFolder)
    }
}
