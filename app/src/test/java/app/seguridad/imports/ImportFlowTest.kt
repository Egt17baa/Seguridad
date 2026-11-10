package app.seguridad.imports

import app.seguridad.crypto.KdfParams
import app.seguridad.vault.CopyResult
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** 1.4.0, Task 8: «Confirmar» (spec «Confirmar: la copia y el guardado»): no copy, no import. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportFlowTest {
    @get:Rule val tmp = TemporaryFolder()

    private var applied = 0
    private val done = ApplyResult.Done(added = 3, replaced = 1)
    private val apply: suspend () -> ApplyResult = { applied++; done }

    @Test fun aCopyThatFailsNeverImports() = runTest {
        val r = ImportFlow(copy = { CopyResult.Failed("No se pudo") }, apply = apply).confirm()
        assertEquals(ImportOutcome.CopyFailed("No se pudo"), r)
        assertEquals(0, applied)
    }

    @Test fun aCopyThatThrowsNeverImports() = runTest {
        val r = ImportFlow(copy = { throw IOException("x") }, apply = apply).confirm()
        assertEquals(ImportOutcome.CopyFailed(IMPORT_COPY_FAILED), r)
        assertEquals(0, applied)
    }

    @Test fun aStalePlanIsNotApplied() = runTest {
        val r = ImportFlow(copy = { CopyResult.Saved("copia.sgbak") }, apply = { ApplyResult.Stale }).confirm()
        assertEquals(ImportOutcome.NotApplied(ApplyResult.Stale), r)
        val big = ApplyResult.TooBig(overBytes = 10)
        assertEquals(ImportOutcome.NotApplied(big), ImportFlow(copy = { CopyResult.Saved("c") }, apply = { big }).confirm())
    }

    /** M-5: a save that fails says so with a fixed text, never what the exception said, and keeps where the copy went. */
    @Test fun aSaveThatFailsIsSaidWithAFixedText() = runTest {
        val r = ImportFlow(copy = { CopyResult.Saved("copia.sgbak") }, apply = { throw IOException("/data/secreto 1234") }).confirm()
        assertEquals(ImportOutcome.SaveFailed("copia.sgbak", IMPORT_SAVE_FAILED), r)
        assertFalse((r as ImportOutcome.SaveFailed).message.contains("secreto"))
    }

    /** N-2: every failure of the save, whatever it was, gives the same text, which claims only what holds on each path. */
    @Test fun everySaveFailureGivesTheSameTrueText() = runTest {
        for (failure in listOf(IllegalStateException("Solo puede haber una identidad"), java.io.IOException("x"), RuntimeException())) {
            val r = ImportFlow(copy = { CopyResult.Saved("c") }, apply = { throw failure }).confirm()
            assertEquals(ImportOutcome.SaveFailed("c", IMPORT_SAVE_FAILED), r)
        }
        assertFalse("Never says the vault is unchanged", IMPORT_SAVE_FAILED.contains("no ha cambiado"))
        assertTrue(IMPORT_SAVE_FAILED.contains("sí se hizo"))
    }

    /** N-2: a full disk, through the real store: SaveFailed, and the vault on disk as it was. */
    @Test fun aFullDiskIsASaveFailureAndTheVaultIsAsItWas() = runTest {
        val file = File(tmp.root, "vault.bin")
        val s = VaultStore(file, Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { KdfParams(1024, 1, 1) }).apply {
            load()
            create("maestra".toCharArray())
        }
        val read = ImportRead(ImportFormat.OTPAUTH, listOf(ImportedItem(0, importedEntry(EntryType.NOTE, "Otra", notes = "u"), emptyList())), emptyList(), 0)
        val plan = s.planImport(read)!!
        val bytes = file.readBytes()
        File(tmp.root, "vault.bin.tmp").mkdir() // the temporary file cannot be written: as a full disk
        val r = ImportFlow(copy = { CopyResult.Saved("c") }, apply = { s.applyImport(plan, DuplicateChoice.SKIP, emptyMap()) }).confirm()
        assertEquals(ImportOutcome.SaveFailed("c", IMPORT_SAVE_FAILED), r)
        assertArrayEquals(bytes, file.readBytes())
        assertTrue((s.state.value as VaultState.Unlocked).entries.isEmpty())
    }

    @Test fun theCopyComesFirstThenTheSave() = runTest {
        val order = mutableListOf<String>()
        val r = ImportFlow(copy = { order += "copia"; CopyResult.Saved("copia.sgbak") }, apply = { order += "guardar"; done }).confirm()
        assertEquals(ImportOutcome.Imported(done, "copia.sgbak"), r)
        assertEquals(listOf("copia", "guardar"), order)
    }

    @Test fun processDeathBetweenCopyAndApplyLeavesTheVaultAsItWas() = runTest {
        val file = File(tmp.root, "vault.bin")
        fun store() = VaultStore(file, Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { KdfParams(1024, 1, 1) })
        val s = store().apply {
            load()
            create("maestra".toCharArray())
            upsert(VaultEntry("a", EntryType.NOTE, "Nota", notes = "t", createdAt = 1, updatedAt = 1))
        }
        val read = ImportRead(ImportFormat.OTPAUTH, listOf(ImportedItem(0, importedEntry(EntryType.NOTE, "Otra", notes = "u"), emptyList())), emptyList(), 0)
        val plan = planMerge(read, (s.state.value as VaultState.Unlocked).entries, s.revision.value)
        val bytes = file.readBytes()
        val copied = CompletableDeferred<Unit>()
        var applyCalled = false
        val flow = ImportFlow(
            copy = {
                copied.complete(Unit)
                CompletableDeferred<Unit>().await() // the process dies here, with the copy written
                CopyResult.Saved("copia.sgbak")
            },
            apply = {
                applyCalled = true
                s.applyImport(plan, DuplicateChoice.SKIP, emptyMap())
            },
        )
        val job = launch { flow.confirm() }
        runCurrent()
        assertTrue(copied.isCompleted)
        job.cancel()
        runCurrent()
        assertFalse(applyCalled)
        assertArrayEquals(bytes, file.readBytes())
        // What the next start reads: the vault as it was.
        val next = store().apply { load(); unlock("maestra".toCharArray()) }
        assertEquals(listOf("a"), (next.state.value as VaultState.Unlocked).entries.map { it.id })
    }
}
