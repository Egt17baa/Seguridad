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
        assertEquals(listOf("vieja"), bank.passwordHistory.map { it.password })
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
        // Many small entries: their texts alone fit, but with the field names the encrypted file does not, so only the
        // exact measure of the file refuses them.
        val items = (0 until 20_000).map { ImportedItem(it, importedEntry(EntryType.NOTE, "N$it", notes = "x".repeat(160)), emptyList()) }
        val p = plan(s, read(*items.toTypedArray()))
        assertTrue("Passes the pre-check", (escapedTextBytes(merged(s, p)) * 4 + 2) / 3 <= BACKUP_MAX_ENTRY_BYTES)
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
        // About 4.7 M control characters, a CSV of about 5 MB: 28 MB of JSON if it were encoded.
        val items = (0 until 75).map { ImportedItem(it, importedEntry(EntryType.NOTE, "N$it", notes = "\u0001".repeat(63_000)), emptyList()) }
        val p = plan(s, read(*items.toTypedArray()))
        val bytes = vaultFile.readBytes()
        val r = s.applyImport(p, DuplicateChoice.SKIP, emptyMap())
        // Exactly the pre-check's figure (the exact measure of an encrypted file would give a larger one).
        assertEquals(ApplyResult.TooBig(overBytes = (escapedTextBytes(merged(s, p)) * 4 + 2) / 3 - BACKUP_MAX_ENTRY_BYTES), r)
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
        val items = (0 until 120).map { ImportedItem(it, importedEntry(EntryType.NOTE, "N$it", notes = "x".repeat(64 * 1024)), emptyList()) }
        val bytes = vaultFile.readBytes()
        val r = s.applyImport(plan(s, read(*items.toTypedArray())), DuplicateChoice.SKIP, emptyMap())
        assertTrue("$r", r is ApplyResult.TooBig && r.overBytes > (120L * 64 * 1024 * 4 / 3) - BACKUP_MAX_ENTRY_BYTES - 1)
        assertArrayEquals(bytes, vaultFile.readBytes())
    }

    @Test fun aVaultWithTooManyAttachmentsForACopyIsRefused() = runTest {
        // Written by hand: 1 999 attachment references (the files need not exist for this check).
        val (file, dek) = VaultCrypto.create("maestra".toCharArray(), clock, KdfParams(1024, 1, 1))
        val refs = (0 until BACKUP_MAX_ENTRIES - 1).map { AttachmentRef("%032x".format(it), "f$it", 1, "0".repeat(64), "k") }
        val heavy = VaultEntry("h", EntryType.NOTE, "Adjuntos", attachments = refs, createdAt = 1, updatedAt = 1)
        vaultFile.writeText(VaultCrypto.encodeFile(VaultCrypto.write(file, dek, listOf(heavy))))
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
        assertEquals(3, VaultCrypto.read(after, key).size - 1)
        val withKey = store().apply { load(); unlockWithKey(key.copyOf()) }
        assertEquals(4, entries(withKey).size)
    }
}
