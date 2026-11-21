package app.seguridad.vault

import app.seguridad.settings.oneTarget
import app.seguridad.settings.autoBackupUri
import app.seguridad.settings.autoBackupCopies
import app.seguridad.settings.autoBackupReplacePending
import app.seguridad.settings.autoBackupReplaceTargets
import app.seguridad.settings.autoBackupReplaceTold
import app.seguridad.settings.importCopies
import app.seguridad.settings.importCopiesFolder
import app.seguridad.settings.principal
import app.seguridad.settings.updatePrincipal
import app.seguridad.crypto.KdfParams
import app.seguridad.imports.ApplyResult
import app.seguridad.imports.DuplicateChoice
import app.seguridad.imports.IMPORT_COPY_LOCKED
import app.seguridad.imports.IMPORT_COPY_NO_FOLDER
import app.seguridad.imports.IMPORT_COPY_VAULT_CHANGED
import app.seguridad.imports.IMPORT_COPY_WRITE_FAILED
import app.seguridad.imports.ImportFormat
import app.seguridad.imports.ImportRead
import app.seguridad.imports.ImportedItem
import app.seguridad.imports.importedEntry
import app.seguridad.imports.planMerge
import app.seguridad.settings.SettingsStore
import app.seguridad.settings.currentAutoBackupCopies
import app.seguridad.settings.currentImportCopies
import app.seguridad.settings.withAutoBackupResult
import app.seguridad.settings.withImportCopy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
    /** Awaited once the copy's bytes are serialised (the vault's mutex is free again), as a slow upload. */
    var gate: CompletableDeferred<Unit>? = null
    /** Called at each [create], before anything is written. */
    var onCreate: (() -> Unit)? = null

    private fun idOf(name: String) = ids.getOrPut(name) { "doc-${next++}" }

    override suspend fun create(name: String): BackupFile {
        created += name
        onCreate?.invoke()
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
                gate?.let { g -> gate = null; g.await() }
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
            updatePrincipal { it.oneTarget(autoBackupUri = folder) }
        }
        return VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) })
            .apply { load(); create("maestra".toCharArray()) }
    }

    /** Wired as AppContainer wires it. */
    private fun TestScope.auto(s: VaultStore, target: BackupTarget?, keep: Int = 5) = oneTargetBackup(
        s, folder = { settings.settings.value.principal.autoBackupUri }, open = { target }, backgroundScope, now = { clock },
        keep = { keep }, lastCopyAt = { Long.MAX_VALUE }, copies = { settings.settings.value.principal.currentAutoBackupCopies },
        replacePending = { settings.settings.value.principal.autoBackupReplacePending },
        replaceTargets = { settings.settings.value.principal.autoBackupReplaceTargets },
        replaceTold = { settings.settings.value.principal.autoBackupReplaceTold },
        onResult = { r -> settings.updatePrincipal { it.withAutoBackupResult(r, s.generation) } },
        zone = ZoneOffset.UTC,
        importCopies = { settings.settings.value.principal.currentImportCopies },
        onImportCopy = { f, id, generation -> settings.updatePrincipal { it.withImportCopy(f, id, generation, s.generation) } },
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

    /** A store with the real vault of 1.4.3 opened with its master, its upgrade to v4 waiting for the user. */
    private suspend fun waitingUpgrade(): VaultStore {
        File(tmp.root, "vault.bin").writeBytes(javaClass.getResourceAsStream("/seguridad/vault-1.4.3.json")!!.use { it.readBytes() })
        settings = SettingsStore(File(tmp.root, "settings.json"), Dispatchers.Unconfined).apply {
            load()
            updatePrincipal { it.oneTarget(autoBackupUri = folder) }
        }
        return VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) })
            .apply { load(); unlock("maestra-de-prueba-143".toCharArray()) }
    }

    /** 2.0, tarea 1: the copy before the upgrade to v4 ([AutoBackup.copyBeforeUpgrade]) is written as the import one. */
    @Test fun theUpgradeCopyHasItsOwnNameAndIsListedForA6() = runTest {
        val s = waitingUpgrade()
        assertTrue(s.pendingUpgrade.value)
        val t = ImportCopyTarget()
        assertEquals(CopyResult.Saved("seguridad-antes-de-2-0-20261026-100000.sgbak"), auto(s, t).copyBeforeUpgrade())
        assertEquals(listOf("seguridad-antes-de-2-0-20261026-100000.sgbak.tmp"), t.created)
        val bytes = t.files.values.single()
        assertTrue(isBackup(bytes.copyOf(BACKUP_HEAD_BYTES)))
        // The file still on disk, with marker 2: what earlier versions open.
        java.util.zip.ZipInputStream(bytes.inputStream()).use { z ->
            z.nextEntry
            assertEquals("2\n", z.readBytes().decodeToString())
        }
        assertEquals(listOf(t.idOfName(t.files.keys.single())), settings.settings.value.principal.importCopies)
        // No folder: null, so the store waits for the user's choice.
        settings.updatePrincipal { it.oneTarget(autoBackupUri = null) }
        assertNull(auto(s, t).copyBeforeUpgrade())
        settings.updatePrincipal { it.oneTarget(autoBackupUri = folder) }
        // Review I-1: once the upgrade is saved there is no copy «de antes» to make: refused, and no file left behind.
        assertTrue(s.completeUpgrade())
        val before = t.files.keys.toList()
        assertTrue(auto(s, t).copyBeforeUpgrade() is CopyResult.Failed)
        assertEquals(before, t.files.keys.toList())
    }

    @Test fun aCutUpgradeCopyGoesWithTheNextRotation() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        t.put("seguridad-antes-de-2-0-20200101-000000.sgbak.tmp")
        t.put("seguridad-antes-de-2-0-20200101-000000.sgbak") // Complete and not listed: counted, never deleted.
        auto(s, t)
        s.upsert(note("1"))
        settle()
        assertFalse("seguridad-antes-de-2-0-20200101-000000.sgbak.tmp" in t.files.keys)
        assertTrue("seguridad-antes-de-2-0-20200101-000000.sgbak" in t.files.keys)
    }

    @Test fun itIsListedInImportCopiesAndNotInTheAutomaticOnes() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        auto(s, t).copyBeforeImport()
        val id = t.idOfName(t.files.keys.single())
        assertEquals(listOf(id), settings.settings.value.principal.importCopies)
        assertEquals(folder, settings.settings.value.principal.importCopiesFolder)
        assertTrue(settings.settings.value.principal.autoBackupCopies.isEmpty())
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
        assertEquals(3, settings.settings.value.principal.autoBackupCopies.size)
        assertFalse(t.idOfName(mine) in settings.settings.value.principal.autoBackupCopies)
        assertEquals(listOf(t.idOfName(mine)), settings.settings.value.principal.importCopies)
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
        val auto = settings.settings.value.principal.autoBackupCopies.single()
        // As after «Cambiar contraseña maestra»: locked, the request waits, with the import copy among its targets.
        s.lock()
        runCurrent()
        assertEquals(AUTO_BACKUP_REPLACE_WAITS, a.replaceOldCopies(listOf(auto)).error)
        assertEquals(setOf(auto, imported), settings.settings.value.principal.autoBackupReplaceTargets!!.toSet())
        clock += 60_000
        s.unlock("maestra".toCharArray())
        runCurrent()
        assertFalse(settings.settings.value.principal.autoBackupReplacePending)
        assertTrue(t.files.keys.none { copyName.matches(it) })
        assertEquals(1, t.files.size)
        assertTrue("Forgotten once deleted", settings.settings.value.principal.importCopies.isEmpty())
    }

    @Test fun theReplacementAskedWithTheVaultOpenTakesItToo() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        val a = auto(s, t)
        a.copyBeforeImport()
        val imported = settings.settings.value.principal.importCopies.single()
        clock += 60_000
        val r = a.replaceOldCopies(listOf(imported)) // MasterChanged's list holds the import copies too (AppContainer).
        assertNull(r.error)
        assertTrue(r.replaced)
        assertTrue(t.files.keys.none { copyName.matches(it) })
        assertTrue(settings.settings.value.principal.importCopies.isEmpty())
    }

    @Test fun anotherFolderKeepsItOutOfTheTargets() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        val a = auto(s, t)
        a.copyBeforeImport()
        // The import copy was made in another folder than the one chosen now.
        settings.updatePrincipal { it.oneTarget(importCopiesFolder = "content://x/tree/primary%3AOtra") }
        s.lock()
        runCurrent()
        a.replaceOldCopies(emptyList())
        assertEquals(emptyList<String>(), settings.settings.value.principal.autoBackupReplaceTargets)
    }

    @Test fun theUnlistedNoticeCountsCopiesWithItsName() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        val a = auto(s, t)
        a.copyBeforeImport() // listed: deleted, not counted
        t.put("seguridad-antes-de-importar-20200101-000000.sgbak") // not listed: counted, never deleted
        t.put("seguridad-antes-de-importar-2020.sgbak") // not exactly the name: neither
        clock += 60_000
        val r = a.replaceOldCopies(settings.settings.value.principal.importCopies)
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
        assertEquals(listOf(t.idOfName(name)), settings.settings.value.principal.importCopies)
    }

    /** M-3: a request saved before 1.3.0 (no targets) predates every import copy, which is under today's password: kept. */
    @Test fun aRequestWithoutTargetsKeepsTheImportCopies() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        val a = auto(s, t)
        a.copyBeforeImport()
        val imported = settings.settings.value.principal.importCopies.single()
        s.lock()
        runCurrent()
        settings.updatePrincipal { it.oneTarget(autoBackupReplacePending = true, autoBackupReplaceTargets = null) }
        clock += 60_000
        s.unlock("maestra".toCharArray())
        runCurrent()
        assertFalse(settings.settings.value.principal.autoBackupReplacePending)
        assertTrue(t.files.keys.any { copyName.matches(it) })
        assertEquals(listOf(imported), settings.settings.value.principal.importCopies)
    }

    /** M-6: the vault replaced or deleted while its copy was written: said as such, the copy left there, not listed. */
    @Test fun aVaultReplacedDuringTheCopyIsSaidAsSuch() = runTest {
        val s = setup()
        val t = ImportCopyTarget().apply { gate = CompletableDeferred() }
        val gate = t.gate!!
        val a = auto(s, t)
        val r = async { a.copyBeforeImport() }
        runCurrent()
        s.destroy() // «He olvidado la contraseña», while the copy is still being uploaded
        gate.complete(Unit)
        runCurrent()
        assertEquals(CopyResult.Failed(IMPORT_COPY_VAULT_CHANGED), r.await())
        assertTrue(t.files.keys.any { copyName.matches(it) })
        assertTrue(settings.settings.value.principal.importCopies.isEmpty())
    }

    /** M-9: a lock that refuses the import copy schedules no automatic copy for the next unlock (nothing changed). */
    @Test fun aLockDuringTheCopyMakesNoAutomaticCopyLater() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        t.onCreate = { t.onCreate = null; s.lock() } // locked between the check and the snapshot
        val a = auto(s, t)
        assertEquals(CopyResult.Failed(IMPORT_COPY_LOCKED), a.copyBeforeImport())
        assertTrue(t.files.isEmpty())
        s.unlock("maestra".toCharArray())
        runCurrent()
        settle()
        assertTrue("No copy for nothing: ${t.files.keys}", t.files.isEmpty())
    }

    /**
     * Ruling R37 (branch review A, M-1): a lock after the copy before importing and before the save cancels the import.
     * [VaultStore.applyImport] gives [ApplyResult.Stale] and nothing is saved; the copy, made, stays in the folder and
     * on the list (A6 can still remove it).
     */
    @Test fun aLockBetweenTheCopyAndTheSaveSavesNothing() = runTest {
        val s = setup()
        s.upsert(note("a"))
        val t = ImportCopyTarget()
        val read = ImportRead(ImportFormat.AEGIS, listOf(ImportedItem(0, importedEntry(EntryType.NOTE, "Wifi", notes = "clave"), emptyList())), emptyList(), 0)
        val snap = checkNotNull(s.importSnapshot())
        val plan = planMerge(read, snap.entries, snap.revision)
        assertTrue(auto(s, t).copyBeforeImport() is CopyResult.Saved)
        val vaultBytes = File(tmp.root, "vault.bin").readBytes()
        val revision = s.revision.value
        s.lock()
        assertEquals(ApplyResult.Stale, s.applyImport(plan, DuplicateChoice.KEEP_BOTH, emptyMap()))
        assertArrayEquals("Nothing saved", vaultBytes, File(tmp.root, "vault.bin").readBytes())
        assertEquals(revision, s.revision.value)
        assertTrue(t.files.keys.single().let { copyName.matches(it) })
        assertEquals(1, settings.settings.value.principal.importCopies.size)
        // Opened again, the vault has only what it had before the import.
        s.unlock("maestra".toCharArray())
        assertEquals(listOf("a"), (s.state.value as VaultState.Unlocked).entries.map { it.id })
    }

    @Test fun noFolderGivesNull() = runTest {
        val s = setup()
        settings.updatePrincipal { it.oneTarget(autoBackupUri = null) }
        assertNull(auto(s, ImportCopyTarget()).copyBeforeImport())
    }

    @Test fun aFailedCopyLeavesNothingAndSaysSo() = runTest {
        val s = setup()
        val t = ImportCopyTarget().apply { failWrite = IOException("disco lleno") }
        val r = auto(s, t).copyBeforeImport()
        assertEquals(CopyResult.Failed(IMPORT_COPY_WRITE_FAILED), r)
        assertTrue(t.files.isEmpty())
        assertTrue(settings.settings.value.principal.importCopies.isEmpty())
        // A folder that cannot be opened, and a locked vault.
        assertEquals(CopyResult.Failed(IMPORT_COPY_NO_FOLDER), auto(s, null).copyBeforeImport())
        s.lock()
        assertEquals(CopyResult.Failed(IMPORT_COPY_LOCKED), auto(s, ImportCopyTarget()).copyBeforeImport())
    }

    @Test fun aNewVaultForgetsTheList() = runTest {
        val s = setup()
        val t = ImportCopyTarget()
        auto(s, t).copyBeforeImport()
        assertEquals(1, settings.settings.value.principal.importCopies.size)
        settings.turnOffAutoBackupForNewVault(PRIMARY_VAULT_ID)
        assertTrue(settings.settings.value.principal.importCopies.isEmpty())
        assertNull(settings.settings.value.principal.importCopiesFolder)
    }
}
