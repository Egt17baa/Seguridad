package app.seguridad.vault

import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.TotpSecret
import app.seguridad.imports.ApplyResult
import app.seguridad.imports.DuplicateChoice
import app.seguridad.imports.ImportFormat
import app.seguridad.imports.ImportRead
import app.seguridad.imports.ImportedItem
import app.seguridad.imports.MergePlan
import app.seguridad.imports.ORIGIN_TAG_AEGIS
import app.seguridad.imports.escapedTextBytes
import app.seguridad.imports.estimatedOverBytes
import app.seguridad.imports.planImport
import app.seguridad.imports.importedEntry
import app.seguridad.imports.mergedEntries
import app.seguridad.imports.planMerge
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.coroutines.CoroutineContext

/** 1.4.0, Task 8: [VaultStore.applyImport], the single save of an import (spec «Confirmar: la copia y el guardado»). */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultStoreImportTest {
    @get:Rule val tmp = TemporaryFolder()
    private var clock = 1_793_786_400_000L
    private val vaultFile get() = File(tmp.root, "vault.bin")

    private fun store() =
        VaultStore(vaultFile, Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) }, elapsed = { clock })

    private suspend fun opened() = store().apply {
        load()
        create("maestra".toCharArray())
        upsert(VaultEntry("b", EntryType.LOGIN, "Banco", username = "ana", password = "vieja", createdAt = 1, updatedAt = 1))
    }

    private fun entries(s: VaultStore) = (s.state.value as VaultState.Unlocked).entries

    private fun read(vararg items: ImportedItem) = ImportRead(ImportFormat.AEGIS, items.toList(), emptyList(), 0)

    private fun fileRead() = read(
        ImportedItem(0, importedEntry(EntryType.LOGIN, "banco", username = "ana", password = "nueva"), emptyList()),
        ImportedItem(1, importedEntry(EntryType.TOTP, "Correo", totp = TotpSecret("JBSWY3DPEHPK3PXP")), listOf("Trabajo")),
        ImportedItem(2, importedEntry(EntryType.NOTE, "Wifi", notes = "clave"), emptyList()),
    )

    private fun plan(s: VaultStore, read: ImportRead = fileRead()): MergePlan = planMerge(read, entries(s), s.revision.value)

    @Test fun applySavesEverythingInOneWrite() = runTest {
        val s = opened()
        val before = s.revision.value
        val r = s.applyImport(plan(s), DuplicateChoice.REPLACE, emptyMap())
        assertEquals(ApplyResult.Done(added = 2, replaced = 1), r)
        assertEquals("One save", before + 1, s.revision.value)
        val list = entries(s)
        assertEquals(3, list.size)
        val bank = list.first { it.id == "b" }
        assertEquals("nueva", bank.password)
        assertEquals(listOf("vieja"), bank.oldPasswords().map { it.password })
        assertTrue(list.first { it.type == EntryType.TOTP }.tags.containsAll(listOf(ORIGIN_TAG_AEGIS, "trabajo")))
        // On disk: a new store reads the same entries.
        val again = store().apply { load(); unlock("maestra".toCharArray()) }
        assertEquals(list.map { it.id }.toSet(), entries(again).map { it.id }.toSet())
    }

    @Test fun aPlanThatChangesNothingWritesNothing() = runTest {
        val s = opened()
        val only = read(ImportedItem(0, importedEntry(EntryType.LOGIN, "Banco", username = "ana"), emptyList()))
        val bytes = vaultFile.readBytes()
        val revision = s.revision.value
        assertEquals(ApplyResult.Done(added = 0, replaced = 0), s.applyImport(plan(s, only), DuplicateChoice.SKIP, emptyMap()))
        assertArrayEquals(bytes, vaultFile.readBytes())
        assertEquals("No save, so no automatic copy either", revision, s.revision.value)
    }

    @Test fun applyRefusesAStalePlan() = runTest {
        val s = opened()
        val p = plan(s)
        // An edit in another window, a sync…: the vault changed after the preview.
        s.upsert(VaultEntry("x", EntryType.NOTE, "Otra", createdAt = 1, updatedAt = 1))
        val bytes = vaultFile.readBytes()
        val revision = s.revision.value
        assertEquals(ApplyResult.Stale, s.applyImport(p, DuplicateChoice.KEEP_BOTH, emptyMap()))
        assertArrayEquals(bytes, vaultFile.readBytes())
        assertEquals(revision, s.revision.value)
        assertEquals(2, entries(s).size)
    }

    @Test fun applyAfterALockIsStaleToo() = runTest {
        val s = opened()
        val p = plan(s)
        s.lock()
        val bytes = vaultFile.readBytes()
        assertEquals(ApplyResult.Stale, s.applyImport(p, DuplicateChoice.KEEP_BOTH, emptyMap()))
        assertArrayEquals(bytes, vaultFile.readBytes())
        // Opened again, the same plan is still refused only if the vault changed: here it did not, and it applies.
        s.unlock("maestra".toCharArray())
        assertTrue(s.applyImport(p, DuplicateChoice.SKIP, emptyMap()) is ApplyResult.Done)
    }

    /**
     * Ruling R66 (code-review 1, R37): the screen records [VaultStore.lockEpoch] when saving starts; a lock after that
     * (during the copy before importing) cancels the import, even if the vault is opened again before the save.
     */
    @Test fun aLockSinceSavingStartedMakesTheImportStale() = runTest {
        val s = opened()
        val p = plan(s)
        val since = s.lockEpoch
        s.lock()
        s.unlock("maestra".toCharArray())
        val bytes = vaultFile.readBytes()
        val revision = s.revision.value
        assertEquals(ApplyResult.Stale, s.applyImport(p, DuplicateChoice.KEEP_BOTH, emptyMap(), sinceLock = since))
        assertArrayEquals(bytes, vaultFile.readBytes())
        assertEquals(revision, s.revision.value)
        // With no lock since, it saves.
        assertTrue(s.applyImport(plan(s), DuplicateChoice.KEEP_BOTH, emptyMap(), sinceLock = s.lockEpoch) is ApplyResult.Done)
    }

    @Test fun applyIsAllOrNothing() = runTest {
        val s = opened()
        val p = plan(s)
        val bytes = vaultFile.readBytes()
        val revision = s.revision.value
        val shown = entries(s)
        // A directory where the temporary file goes: the write throws midway (as in VaultStoreTest).
        File(tmp.root, "vault.bin.tmp").mkdir()
        try {
            s.applyImport(p, DuplicateChoice.REPLACE, emptyMap())
            fail()
        } catch (e: java.io.IOException) {
            // expected
        }
        assertArrayEquals(bytes, vaultFile.readBytes())
        assertEquals(revision, s.revision.value)
        assertEquals(shown, entries(s))
    }

    /** What [VaultStore.applyImport] merges, with ids as long as its UUIDs. */
    private fun merged(s: VaultStore, p: MergePlan) = mergedEntries(p, DuplicateChoice.SKIP, emptyMap(), entries(s), clock, { "u".repeat(36) })

    @Test fun tooBigIsRefused() = runTest {
        val s = opened()
        // Many small entries: their texts alone fit a copy with marker 3 (33 MiB), but with the field names the data
        // passes the vault's 24 MiB (2.0), so only the exact measure refuses them: TooBig, never a failed save.
        val items = (0 until 20_000).map { ImportedItem(it, importedEntry(EntryType.NOTE, "N$it", notes = "x".repeat(1_200)), emptyList()) }
        val p = plan(s, read(*items.toTypedArray()))
        assertTrue("Passes the pre-check", (escapedTextBytes(merged(s, p)) * 4 + 2) / 3 <= BACKUP_V3_MAX_ENTRY_BYTES)
        assertTrue("The preview says so too", estimatedOverBytes(merged(s, p)) > 0)
        val bytes = vaultFile.readBytes()
        val revision = s.revision.value
        val r = s.applyImport(p, DuplicateChoice.SKIP, emptyMap())
        assertTrue("$r", r is ApplyResult.TooBig && r.overBytes > 0 && r.overFiles == 0)
        assertArrayEquals(bytes, vaultFile.readBytes())
        assertEquals(revision, s.revision.value)
        assertEquals(1, entries(s).size)
    }

    /** I-1: text JSON escapes six times over is refused from its count, before anything is encoded or encrypted. */
    @Test fun aHeavilyEscapedImportIsRefusedBeforeEncoding() = runTest {
        val s = opened()
        // 5 M control characters, a CSV of about 5 MB: 30 MB of JSON if it were encoded (each entry 60 KB, within its 64 KiB).
        val items = (0 until 500).map { ImportedItem(it, importedEntry(EntryType.NOTE, "N$it", notes = "\u0001".repeat(10_000)), emptyList()) }
        val p = plan(s, read(*items.toTypedArray()))
        val bytes = vaultFile.readBytes()
        val r = s.applyImport(p, DuplicateChoice.SKIP, emptyMap())
        // Exactly the pre-check's figure (the exact measure of an encrypted file would give a larger one).
        assertEquals(ApplyResult.TooBig(overBytes = (escapedTextBytes(merged(s, p)) * 4 + 2) / 3 - BACKUP_V3_MAX_ENTRY_BYTES), r)
        assertArrayEquals(bytes, vaultFile.readBytes())
        assertFalse(File(tmp.root, "vault.bin.tmp").exists())
    }

    /** M-2: the same file replaced twice: the second time nothing changes, so nothing is written and no copy follows. */
    @Test fun aSecondReplaceOfTheSameFileWritesNothing() = runTest {
        val s = opened()
        val file = read(
            ImportedItem(0, importedEntry(EntryType.LOGIN, "Banco", username = "ana", password = "nueva", notes = "del archivo"), emptyList()),
            ImportedItem(1, importedEntry(EntryType.TOTP, "Correo", totp = TotpSecret("JBSWY3DPEHPK3PXP")), listOf("Trabajo")),
        )
        assertEquals(ApplyResult.Done(added = 1, replaced = 1), s.applyImport(plan(s, file), DuplicateChoice.REPLACE, emptyMap()))
        val bytes = vaultFile.readBytes()
        val revision = s.revision.value
        assertEquals(ApplyResult.Done(added = 0, replaced = 0), s.applyImport(plan(s, file), DuplicateChoice.REPLACE, emptyMap()))
        assertArrayEquals(bytes, vaultFile.readBytes())
        assertEquals(revision, s.revision.value)
    }

    /** A dispatcher that holds every task until [release]: a save that waits on a slow disk, with the mutex held. */
    private class HeldDispatcher : CoroutineDispatcher() {
        private val held = ArrayDeque<Runnable>()
        var open = true
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (open) block.run() else held += block
        }
        fun release() {
            open = true
            while (held.isNotEmpty()) held.removeFirst().run()
        }
    }

    /** M-4: the plan's entries and revision come from one snapshot, taken under the mutex, never an old list with a newer revision. */
    @Test fun aPlanFromTheSnapshotIsNeverPairedWithANewerRevision() = runTest {
        val io = HeldDispatcher()
        val s = VaultStore(vaultFile, io, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) }, elapsed = { clock })
            .apply { load(); create("maestra".toCharArray()) }
        val before = s.revision.value
        io.open = false
        val edit = launch { s.upsert(VaultEntry("x", EntryType.NOTE, "Otra", createdAt = 1, updatedAt = 1)) }
        runCurrent() // the edit holds the mutex, its save waiting on the disk
        val planned = async { s.planImport(fileRead()) }
        runCurrent()
        assertFalse("Waits for the save in progress", planned.isCompleted)
        io.release()
        runCurrent()
        edit.join()
        val p = planned.await()!!
        assertEquals(before + 1, p.revision)
        assertEquals(s.revision.value, p.revision)
        // Built on the list with the edit: it applies, and the edit is there.
        assertTrue(s.applyImport(p, DuplicateChoice.SKIP, emptyMap()) is ApplyResult.Done)
        assertTrue(entries(s).any { it.id == "x" })
        assertEquals(VaultSnapshot(entries(s), s.revision.value), s.importSnapshot())
        s.lock()
        assertNull(s.importSnapshot())
        assertNull(s.planImport(fileRead()))
    }

    @Test fun aFileClearlyTooBigIsRefusedBeforeEncrypting() = runTest {
        val s = opened()
        val items = (0 until 600).map { ImportedItem(it, importedEntry(EntryType.NOTE, "N$it", notes = "x".repeat(60_000)), emptyList()) }
        val bytes = vaultFile.readBytes()
        val r = s.applyImport(plan(s, read(*items.toTypedArray())), DuplicateChoice.SKIP, emptyMap())
        assertTrue("$r", r is ApplyResult.TooBig && r.overBytes > (600L * 60_000 * 4 / 3) - BACKUP_V3_MAX_ENTRY_BYTES - 1)
        assertArrayEquals(bytes, vaultFile.readBytes())
    }

    /** 2.0, ruling T1-2: what passed the 6 MB of a copy with marker 2 fits the 33 MiB of the one a v4 vault gets. */
    @Test fun theFitIsTheCapOfTheCopyThatWouldBeWritten() = runTest {
        val s = opened()
        val items = (0 until 150).map { ImportedItem(it, importedEntry(EntryType.NOTE, "N$it", notes = "x".repeat(60_000)), emptyList()) }
        val p = plan(s, read(*items.toTypedArray()))
        assertTrue("Over what marker 2 allows", (escapedTextBytes(merged(s, p)) * 4 + 2) / 3 > BACKUP_MAX_ENTRY_BYTES)
        assertEquals(ApplyResult.Done(added = 150, replaced = 0), s.applyImport(p, DuplicateChoice.SKIP, emptyMap()))
        assertEquals(BACKUP_V3_MAX_ENTRY_BYTES, backupMaxEntryBytes(VaultFile.VERSION))
        assertEquals(BACKUP_MAX_ENTRY_BYTES, backupMaxEntryBytes(VaultFile.VERSION_3))
        assertEquals(BACKUP_MAX_ENTRY_BYTES, backupMaxEntryBytes(VaultFile.VERSION_2))
        // And its copy is written and read back.
        val copy = java.io.ByteArrayOutputStream().also { assertTrue(s.exportBackup(it)) }.toByteArray()
        val other = VaultStore(File(tmp.root, "otra.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) })
            .apply { load() }
        other.importReplace(java.io.ByteArrayInputStream(copy), "maestra".toCharArray())
        assertEquals(151, (other.state.value as VaultState.Unlocked).entries.size)
    }

    /** 2.0, ruling T1-3: an item over 64 KiB stays out in the plan; the rest is saved, never a failed save. */
    @Test fun anItemOverTheEntryLimitNeverFailsTheSave() = runTest {
        val s = opened()
        val p = plan(
            s,
            read(
                ImportedItem(0, importedEntry(EntryType.NOTE, "Grande", notes = "\"".repeat(40_000)), emptyList()),
                ImportedItem(1, importedEntry(EntryType.NOTE, "Pequeña", notes = "hola"), emptyList()),
            ),
        )
        assertEquals(listOf(0), p.skipped.map { it.index })
        assertEquals(ApplyResult.Done(added = 1, replaced = 0), s.applyImport(p, DuplicateChoice.SKIP, emptyMap()))
        assertEquals(setOf("Banco", "Pequeña"), entries(s).map { it.title }.toSet())
    }

    /** 2.0: «Reemplazar» that would join two notes past 64 KiB is refused with its own result, and nothing is saved. */
    @Test fun aReplaceThatWouldPassTheEntryLimitIsRefusedWithItsOwnResult() = runTest {
        val s = opened()
        // A login of the vault whose notes differ from the file's: «Reemplazar» appends the file's under a heading.
        s.upsert(VaultEntry("m", EntryType.LOGIN, "Correo", username = "ana", notes = "c".repeat(40_000), createdAt = 1, updatedAt = 1))
        val login = plan(s, read(ImportedItem(0, importedEntry(EntryType.LOGIN, "Correo", username = "ana", notes = "d".repeat(40_000)), emptyList())))
        assertEquals(1, login.duplicates.size)
        val bytes = vaultFile.readBytes()
        val revision = s.revision.value
        assertEquals(ApplyResult.EntryTooBig, s.applyImport(login, DuplicateChoice.REPLACE, emptyMap()))
        assertArrayEquals(bytes, vaultFile.readBytes())
        assertEquals(revision, s.revision.value)
        // «Conservar los dos» brings it in as a new entry, which fits.
        assertEquals(ApplyResult.Done(added = 1, replaced = 0), s.applyImport(login, DuplicateChoice.KEEP_BOTH, emptyMap()))
    }

    @Test fun aVaultWithTooManyAttachmentsForACopyIsRefused() = runTest {
        // Written by hand: 1 999 attachment references (the files need not exist for this check).
        val (file, dek) = VaultCrypto.create("maestra".toCharArray(), clock, KdfParams(1024, 1, 1))
        val refs = (0 until BACKUP_MAX_ENTRIES - 1).map { AttachmentRef("%032x".format(it), "f$it", 1, "0".repeat(64), "k") }
        val heavy = VaultEntry("h", EntryType.NOTE, "Adjuntos", attachments = refs, createdAt = 1, updatedAt = 1)
        vaultFile.writeText(VaultCrypto.encodeFile(VaultCrypto.write(file, dek, VaultData(entries = listOf(heavy)))))
        val s = store().apply { load(); unlock("maestra".toCharArray()) }
        val bytes = vaultFile.readBytes()
        val r = s.applyImport(plan(s), DuplicateChoice.SKIP, emptyMap())
        assertEquals(ApplyResult.TooBig(overBytes = 0, overFiles = 1), r)
        assertArrayEquals(bytes, vaultFile.readBytes())
    }

    @Test fun fingerprintStaysEnabled() = runTest {
        val s = opened()
        val key = s.dataKeyCopy()!!
        val before = VaultCrypto.decodeFile(vaultFile.readText())
        assertTrue(s.applyImport(plan(s), DuplicateChoice.KEEP_BOTH, emptyMap()) is ApplyResult.Done)
        val after = VaultCrypto.decodeFile(vaultFile.readText())
        // The same data key, wrapped the same way: the fingerprint's copy of it still opens the vault.
        assertArrayEquals(key, s.dataKeyCopy())
        assertEquals(before.wrappedKey, after.wrappedKey)
        assertEquals(before.salt, after.salt)
        assertEquals(before.masterCheckedAt, after.masterCheckedAt)
        assertEquals(3, VaultCrypto.read(after, key).entries.size - 1)
        val withKey = store().apply { load(); unlockWithKey(key.copyOf()) }
        assertEquals(4, entries(withKey).size)
    }
}
