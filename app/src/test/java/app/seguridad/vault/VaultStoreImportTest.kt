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
import app.seguridad.imports.previewEstimate
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

    private fun trashNote(i: Int, len: Int) =
        VaultEntry("t$i", EntryType.NOTE, "T$i", notes = "x".repeat(len), createdAt = 1, updatedAt = 1, deletedAt = clock)

    /** [count] notes in the trash whose vault, with [live] and [extra], measures [target] JSON bytes (as the save does). */
    private fun trashFor(target: Long, live: List<VaultEntry>, extra: VaultData = VaultData(), count: Int = 420): List<VaultEntry> {
        val base = (0 until count).map { trashNote(it, 1) }
        val size = jsonBytes(extra.copy(entries = live + base))
        val each = ((target - size) / count).toInt()
        val first = base.mapIndexed { i, e -> if (i == 0) e else e.copy(notes = "x".repeat(1 + each)) }
        val last = target - jsonBytes(extra.copy(entries = live + first)) + 1
        return first.mapIndexed { i, e -> if (i == 0) e.copy(notes = "x".repeat(last.toInt())) else e }
    }

    /**
     * Review fixes, CR-5: the preview measures what the save measures, the merged entries with the trash, the folders,
     * the views, the colours and the name. Here the live entries fit; with the trash, or with the folders, they do not.
     */
    @Test fun thePreviewMeasuresTheTrashAndTheFoldersToo() {
        val live = listOf(VaultEntry("b", EntryType.LOGIN, "Banco", username = "ana", password = "vieja", createdAt = 1, updatedAt = 1))
        val p = planMerge(read(ImportedItem(0, importedEntry(EntryType.NOTE, "Wifi", notes = "clave"), emptyList())), live, 1)
        val merged = mergedEntries(p, DuplicateChoice.SKIP, emptyMap(), live, clock, { "u".repeat(36) })
        assertEquals("the live entries alone fit", 0L, estimatedOverBytes(merged))
        // The trash takes the vault 10 000 bytes over its 24 MiB.
        val over = VaultData(entries = trashFor(VaultLimits.VAULT_MAX_DATA_BYTES + 10_000L, merged))
        assertTrue(previewEstimate(p, DuplicateChoice.SKIP, emptyMap(), live, over, clock).overBytes >= 10_000)
        // The trash leaves 30 000 bytes free; 500 folders take more.
        val folders = (0 until 500).map { Folder("carpeta-$it".padEnd(36, '0'), "Carpeta $it ${"n".repeat(50)}", null, it) }
        val under = trashFor(VaultLimits.VAULT_MAX_DATA_BYTES - 30_000L, merged)
        assertEquals("fits without the folders", 0L, previewEstimate(p, DuplicateChoice.SKIP, emptyMap(), live, VaultData(entries = under), clock).overBytes)
        val withFolders = VaultData(entries = under, folders = folders)
        assertTrue(jsonBytes(withFolders.copy(entries = merged + under)) > VaultLimits.VAULT_MAX_DATA_BYTES)
        assertTrue(previewEstimate(p, DuplicateChoice.SKIP, emptyMap(), live, withFolders, clock).overBytes > 0)
    }

    /** A vault file of [data] under the master «maestra» and the store opened on it. */
    private suspend fun storeOf(data: VaultData): VaultStore {
        val (file, dek) = VaultCrypto.create("maestra".toCharArray(), clock, KdfParams(1024, 1, 1))
        vaultFile.writeText(VaultCrypto.encodeFile(VaultCrypto.write(file, dek, data)))
        dek.fill(0)
        return store().apply { load(); unlock("maestra".toCharArray()) }
    }

    /** The vault the preview measures for [p] with [default], from [snap] (ids as long as the save's UUIDs). */
    private fun previewData(p: MergePlan, default: DuplicateChoice, snap: VaultSnapshot): VaultData =
        snap.rest.copy(entries = mergedEntries(p, default, emptyMap(), snap.entries, clock, { "u".repeat(36) }) + snap.rest.entries)

    /**
     * Review fixes, CR-5: 200 random vaults (Random(20261120)), each with live entries, a trash, folders, views, colours
     * and a name, and a random import with duplicates and a random choice for them. Small (a few KB), so each case is
     * milliseconds: what the preview counts never is below what the save measures (it does not depend on the cap), and so
     * neither of the two verdicts can differ the wrong way. Through the real [previewEstimate] and [VaultStore.applyImport]:
     * a TooBig of the save has a preview over 0, a preview of 0 is saved, and after each save the data the store holds
     * (JSON) and the file on disk are within what the preview counted. The verdict at the real caps:
     * [thePreviewAndTheSaveAgreeAtTheRealCaps] and [thePreviewAndTheSaveAgreeAtTheRealCapsNearTheEdge].
     */
    @Test fun thePreviewNeverCountsLessThanTheSave() = runTest {
        val rnd = kotlin.random.Random(20261120L)
        var saved = 0
        repeat(200) { case ->
            val live = (0 until rnd.nextInt(1, 8)).map {
                VaultEntry("l$it", EntryType.LOGIN, "Sitio $it", username = "u$it", password = "p".repeat(rnd.nextInt(1, 40)), notes = "n".repeat(rnd.nextInt(0, 500)), createdAt = 1, updatedAt = 1)
            }
            val data = VaultData(
                entries = live + (0 until rnd.nextInt(0, 20)).map { trashNote(it, rnd.nextInt(1, 2_000)) },
                folders = (0 until rnd.nextInt(0, 51)).map { Folder("f$it", "Carpeta $it ${"c".repeat(rnd.nextInt(0, 50))}", null, it) },
                views = (0 until rnd.nextInt(0, 11)).map { SavedView("v$it", "Vista $it ${"v".repeat(rnd.nextInt(0, 50))}", EntryFilter()) },
                tagColors = (0 until rnd.nextInt(0, 31)).associate { "etiqueta$it" to TagColor.entries[rnd.nextInt(TagColor.entries.size)] },
                name = "B".repeat(rnd.nextInt(0, 101)),
            )
            val s = storeOf(data)
            val items = (0 until rnd.nextInt(1, 6)).map {
                // Some are duplicates of a live login (same title and user), with a new password.
                val entry = if (rnd.nextBoolean()) {
                    val l = live[rnd.nextInt(live.size)]
                    importedEntry(EntryType.LOGIN, l.title, username = l.username, password = "nueva".repeat(rnd.nextInt(1, 20)))
                } else {
                    importedEntry(EntryType.NOTE, "Importada $it", notes = "i".repeat(rnd.nextInt(1, 3_000)))
                }
                ImportedItem(it, entry, emptyList())
            }
            val default = DuplicateChoice.entries[rnd.nextInt(DuplicateChoice.entries.size)]
            val snap = s.importSnapshot()!!
            val p = planMerge(read(*items.toTypedArray()), snap.entries, snap.revision)
            val estimate = previewEstimate(p, default, emptyMap(), snap.entries, snap.rest, clock).overBytes
            val counted = app.seguridad.imports.previewDataBytes(previewData(p, default, snap))
            val r = s.applyImport(p, default, emptyMap())
            if (r is ApplyResult.TooBig) assertTrue("case $case: the save says $r, the preview $estimate", estimate > 0)
            if (estimate == 0L) assertTrue("case $case: the preview says 0, the save $r", r is ApplyResult.Done)
            if (r is ApplyResult.Done && (r.added > 0 || r.replaced > 0)) {
                saved++
                val held = jsonBytes((s.state.value as VaultState.Unlocked).data)
                assertTrue("case $case: the store holds $held JSON bytes, the preview counted $counted", held <= counted)
                assertTrue("case $case: the file has ${vaultFile.length()} bytes", vaultFile.length() <= app.seguridad.imports.previewFileBytes(counted))
            }
        }
        assertTrue("$saved of 200 saved", saved >= 150)
    }

    /**
     * Review fixes, CR-5, the verdict at the real caps: vaults a little under the 24 MiB, with a trash, folders, views,
     * colours and a name, and an import that fits or does not. A TooBig of the save has a preview over 0; a preview of 0
     * is saved. Few cases: near 24 MiB, 50 of them did not end in 60 s (writing the file, the unlock, the preview and the
     * save each go over the whole vault).
     */
    @Test fun thePreviewAndTheSaveAgreeAtTheRealCaps() = realCaps(100_000L to listOf(30_000), 100_000L to listOf(50_000, 50_000, 50_000))

    /** [thePreviewAndTheSaveAgreeAtTheRealCaps], close to the edge on each side (split so each test stays under 10 s). */
    @Test fun thePreviewAndTheSaveAgreeAtTheRealCapsNearTheEdge() = realCaps(20_000L to listOf(17_000), 20_000L to listOf(21_000))

    /** Each case: the room the vault leaves, and the notes the file brings. */
    private fun realCaps(vararg cases: Pair<Long, List<Int>>) = runTest {
        val extra = VaultData(
            folders = (0 until 300).map { Folder("f$it", "Carpeta $it", null, it) },
            views = (0 until 20).map { SavedView("v$it", "Vista $it", EntryFilter()) },
            tagColors = (0 until 50).associate { "etiqueta$it" to TagColor.BLUE },
            name = "Bóveda de prueba",
        )
        val live = listOf(VaultEntry("b", EntryType.LOGIN, "Banco", username = "ana", password = "vieja", createdAt = 1, updatedAt = 1))
        for ((room, notes) in cases) {
            val s = storeOf(extra.copy(entries = live + trashFor(VaultLimits.VAULT_MAX_DATA_BYTES - room, live, extra)))
            val items = notes.mapIndexed { i, n -> ImportedItem(i, importedEntry(EntryType.NOTE, "Importada $i", notes = "i".repeat(n)), emptyList()) }
            val snap = s.importSnapshot()!!
            val p = planMerge(read(*items.toTypedArray()), snap.entries, snap.revision)
            val estimate = previewEstimate(p, DuplicateChoice.SKIP, emptyMap(), snap.entries, snap.rest, clock).overBytes
            val r = s.applyImport(p, DuplicateChoice.SKIP, emptyMap())
            println("CR-5 at the real caps: room $room, notes $notes: preview $estimate, save $r")
            val fits = notes.sum() < room - 2_000
            if (fits) assertTrue("room $room, $notes: $r", r is ApplyResult.Done && estimate == 0L)
            if (!fits) assertTrue("room $room, $notes: $r, preview $estimate", r is ApplyResult.TooBig && estimate > 0)
        }
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
        assertEquals(VaultSnapshot(entries(s), s.revision.value, VaultData()), s.importSnapshot())
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
