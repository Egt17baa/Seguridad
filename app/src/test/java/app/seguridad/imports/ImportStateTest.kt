package app.seguridad.imports

import app.seguridad.crypto.TotpSecret
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.4.0, Task 9: what the «Importar de otra app» screen holds ([ImportSession], the ViewModel's pure state). Lock and back
 * drop the read data and the password; a late result is dropped; an encrypted file gets one password attempt (R13); the
 * default choice is «Saltar»; overrides go by [ImportedItem.index]; «Quitar el archivo exportado» once, only after an
 * import.
 */
class ImportStateTest {
    private val uri = Any()

    private fun login(index: Int, title: String, user: String = "", folders: List<String> = emptyList()) =
        ImportedItem(index, importedEntry(EntryType.LOGIN, title, username = user, password = "clave-$index"), folders)

    private fun note(index: Int, title: String) = ImportedItem(index, importedEntry(EntryType.NOTE, title, notes = "texto $index"), emptyList())

    private fun totp(index: Int, secret: String) =
        ImportedItem(index, importedEntry(EntryType.TOTP, "Código $index", totp = TotpSecret(secret, issuer = "Emisor")), emptyList())

    private fun saved(id: String, title: String, user: String = "", tags: List<String> = emptyList()) =
        VaultEntry(id, EntryType.LOGIN, title, username = user, password = "p", createdAt = 1, updatedAt = 1, tags = tags)

    private val existing = listOf(saved("a", "Banco", "ana", tags = listOf("casa")), saved("b", "Correo", "ana"))

    /** Items 0 and 3 are new; 5 and 2 match the vault (so their positions in plan.duplicates are not their indexes). */
    private val read = ImportRead(
        ImportFormat.BITWARDEN,
        listOf(
            login(0, "Tienda", folders = listOf("Compras")),
            login(5, "Correo", "ana", folders = listOf("Trabajo")),
            login(2, "Banco", "ana", folders = listOf("Casa", "Dinero")),
            note(3, "Wifi"),
            totp(4, "JBSWY3DPEHPK3PXP"),
        ),
        listOf(Skipped(1, "Llave", SkipReason.PASSKEY)),
        becameNotes = 1,
    )

    private fun plan() = planMerge(read, existing, revision = 9)

    /** A session at the preview of [read], with its run. */
    private fun atPreview(): Pair<ImportSession, Long> {
        val s = ImportSession()
        val run = s.begin(uri)
        val bytes = byteArrayOf(1, 2, 3)
        assertTrue(s.hold(run, bytes))
        assertSame(read, s.onRead(run, ReadResult.Ok(read)))
        s.onPlan(run, plan(), existing)
        assertEquals(ImportStep.Preview, s.step)
        return s to run
    }

    @Test fun startsAtThePickStepHoldingNothing() {
        val s = ImportSession()
        assertEquals(ImportStep.Pick, s.step)
        assertNull(s.plan)
        assertNull(s.error)
        assertFalse(s.busy)
    }

    @Test fun aNewPickReadsAndDropsWhatWasShownBefore() {
        val (s, _) = atPreview()
        val run = s.begin(uri)
        assertEquals(ImportStep.Reading, s.step)
        assertTrue(s.busy)
        assertNull(s.plan)
    }

    @Test fun needsPasswordGoesToThePasswordStepAndKeepsTheBytesForTheRetry() {
        val s = ImportSession()
        val run = s.begin(uri)
        val bytes = byteArrayOf(7, 7, 7)
        assertTrue(s.hold(run, bytes))
        assertNull(s.onRead(run, ReadResult.NeedsPassword(ImportFormat.AEGIS)))
        assertEquals(ImportStep.Password(ImportFormat.AEGIS), s.step)
        assertSame(bytes, s.bytesFor(run))
        assertArrayEquals(byteArrayOf(7, 7, 7), bytes)
    }

    @Test fun oneAttemptThatStillNeedsAPasswordSaysNotYetAndNeverLoops() {
        val s = ImportSession()
        val run = s.begin(uri)
        val bytes = byteArrayOf(7, 7, 7)
        s.hold(run, bytes)
        s.onRead(run, ReadResult.NeedsPassword(ImportFormat.BITWARDEN))
        s.password = "contraseña"
        val (taken, pw) = s.takePassword()!!
        assertEquals(run, taken)
        assertEquals("contraseña", String(pw))
        assertEquals("", s.password) // The field empties at once; the caller zeroes the CharArray.
        assertEquals(ImportStep.Reading, s.step)
        assertNull(s.onRead(run, ReadResult.NeedsPassword(ImportFormat.BITWARDEN)))
        assertEquals(ImportStep.Pick, s.step)
        assertEquals(IMPORT_ENCRYPTED_NOT_YET, s.error)
        assertArrayEquals(byteArrayOf(0, 0, 0), bytes)
        assertNull(s.bytesFor(run))
        assertNull(s.takePassword())
    }

    @Test fun noPasswordIsTakenEmptyOrOutsideThePasswordStep() {
        val s = ImportSession()
        assertNull(s.takePassword())
        val run = s.begin(uri)
        s.hold(run, byteArrayOf(1))
        s.onRead(run, ReadResult.NeedsPassword(ImportFormat.TWOFAS))
        assertNull(s.takePassword())
    }

    @Test fun aLockDropsTheReadDataAndThePassword() {
        val s = ImportSession()
        val run = s.begin(uri)
        val bytes = byteArrayOf(9, 9)
        s.hold(run, bytes)
        s.fileName = "export.json"
        s.onRead(run, ReadResult.NeedsPassword(ImportFormat.AEGIS))
        s.password = "secreto"
        s.vaultStateIs(unlocked = true)
        assertEquals("secreto", s.password)
        s.vaultStateIs(unlocked = false)
        assertEquals(ImportStep.Pick, s.step)
        assertEquals("", s.password)
        assertNull(s.fileName)
        assertArrayEquals(byteArrayOf(0, 0), bytes)
        assertNull(s.bytesFor(run))
        assertNull(s.takePassword())
    }

    @Test fun aLockAtThePreviewDropsThePlanAndTheChoices() {
        val (s, run) = atPreview()
        s.choose(5, DuplicateChoice.REPLACE)
        s.vaultStateIs(unlocked = false)
        assertEquals(ImportStep.Pick, s.step)
        assertNull(s.plan)
        assertEquals(emptyMap<Int, DuplicateChoice>(), s.overrides)
        assertEquals(DuplicateChoice.SKIP, s.default)
        assertNull(s.startSaving())
        assertFalse(s.isRun(run))
    }

    @Test fun backDropsEverything() {
        val (s, run) = atPreview()
        var stopped = 0
        val wiped = ImportSession(onWipe = { stopped++ })
        wiped.wipe()
        assertEquals(1, stopped)
        s.wipe() // What onCleared does when the screen is left.
        assertEquals(ImportStep.Pick, s.step)
        assertNull(s.plan)
        assertEquals(emptyList<Skipped>(), s.skipped)
        assertFalse(s.isRun(run))
    }

    @Test fun theDefaultChoiceIsSkip() {
        val (s, _) = atPreview()
        assertEquals(DuplicateChoice.SKIP, s.default)
        assertEquals(DuplicateChoice.SKIP, s.choiceOf(5))
        assertEquals(DuplicateChoice.SKIP, s.choiceOf(2))
    }

    @Test fun okZeroesTheBytesAndAPlanShowsThePreview() {
        val s = ImportSession()
        val run = s.begin(uri)
        val bytes = byteArrayOf(4, 5, 6)
        s.hold(run, bytes)
        assertSame(read, s.onRead(run, ReadResult.Ok(read)))
        assertArrayEquals(byteArrayOf(0, 0, 0), bytes)
        assertEquals(ImportStep.Reading, s.step) // The plan is built next.
        s.onPlan(run, plan(), existing)
        assertEquals(ImportStep.Preview, s.step)
        assertEquals(listOf(5, 2), s.plan!!.duplicates.map { it.item.index })
        assertEquals(read.skipped, s.skipped)
    }

    @Test fun aNullPlanMeansTheVaultLockedAndDropsAll() {
        val s = ImportSession()
        val run = s.begin(uri)
        s.hold(run, byteArrayOf(1))
        s.onRead(run, ReadResult.Ok(read))
        s.onPlan(run, null, emptyList())
        assertEquals(ImportStep.Pick, s.step)
        assertNull(s.plan)
    }

    @Test fun aFailureShowsItsMessageAndZeroesTheBytes() {
        val s = ImportSession()
        val run = s.begin(uri)
        val bytes = byteArrayOf(3, 3)
        s.hold(run, bytes)
        assertNull(s.onRead(run, ReadResult.Failed(IMPORT_UNKNOWN)))
        assertEquals(ImportStep.Pick, s.step)
        assertEquals(IMPORT_UNKNOWN, s.error)
        assertArrayEquals(byteArrayOf(0, 0), bytes)
    }

    @Test fun noResultAtAllIsTooMuchData() {
        // R11b: the screen catches an OutOfMemoryError around readImport only and hands over null.
        val s = ImportSession()
        val run = s.begin(uri)
        val bytes = byteArrayOf(3)
        s.hold(run, bytes)
        assertNull(s.onRead(run, null))
        assertEquals(IMPORT_JSON_TOO_MANY_VALUES, s.error)
        assertArrayEquals(byteArrayOf(0), bytes)
    }

    @Test fun aReadThatCouldNotStartSaysSo() {
        val s = ImportSession()
        val run = s.begin(uri)
        s.readFailed(run, IMPORT_READ_FAILED)
        assertEquals(ImportStep.Pick, s.step)
        assertEquals(IMPORT_READ_FAILED, s.error)
    }

    @Test fun aLateResultIsDroppedAndItsBytesZeroed() {
        val s = ImportSession()
        val first = s.begin(uri)
        val second = s.begin(uri)
        val late = byteArrayOf(8, 8)
        assertFalse(s.hold(first, late))
        assertArrayEquals(byteArrayOf(0, 0), late)
        assertNull(s.onRead(first, ReadResult.Ok(read)))
        s.onPlan(first, plan(), existing)
        assertEquals(ImportStep.Reading, s.step)
        s.readFailed(first, IMPORT_READ_FAILED)
        assertNull(s.error)
        assertTrue(s.isRun(second))
    }

    @Test fun cancelStopsTheReadAndDropsItsBytes() {
        val s = ImportSession()
        val run = s.begin(uri)
        val bytes = byteArrayOf(2, 2)
        s.hold(run, bytes)
        s.cancel()
        assertEquals(ImportStep.Pick, s.step)
        assertArrayEquals(byteArrayOf(0, 0), bytes)
        assertFalse(s.isRun(run))
    }

    @Test fun overridesAreKeyedByTheItemIndex() {
        val (s, _) = atPreview()
        s.choose(5, DuplicateChoice.REPLACE)
        assertEquals(mapOf(5 to DuplicateChoice.REPLACE), s.overrides)
        assertEquals(DuplicateChoice.REPLACE, s.choiceOf(5))
        assertEquals(DuplicateChoice.SKIP, s.choiceOf(2))
        // Not an index of a duplicate: nothing changes.
        s.choose(0, DuplicateChoice.KEEP_BOTH)
        assertEquals(mapOf(5 to DuplicateChoice.REPLACE), s.overrides)
        // The option for all: every duplicate follows it.
        s.chooseAll(DuplicateChoice.KEEP_BOTH)
        assertEquals(DuplicateChoice.KEEP_BOTH, s.default)
        assertEquals(emptyMap<Int, DuplicateChoice>(), s.overrides)
        assertEquals(DuplicateChoice.KEEP_BOTH, s.choiceOf(5))
    }

    @Test fun previewCountsFollowTheChoices() {
        val p = plan()
        val skip = previewCounts(p, DuplicateChoice.SKIP, emptyMap())
        assertEquals(mapOf(EntryType.LOGIN to 1, EntryType.NOTE to 1, EntryType.TOTP to 1), skip.added)
        assertEquals(0, skip.replaced)
        assertEquals(2, skip.skippedDuplicates)
        val mixed = previewCounts(p, DuplicateChoice.SKIP, mapOf(5 to DuplicateChoice.KEEP_BOTH, 2 to DuplicateChoice.REPLACE))
        assertEquals(mapOf(EntryType.LOGIN to 2, EntryType.NOTE to 1, EntryType.TOTP to 1), mixed.added)
        assertEquals(1, mixed.replaced)
        assertEquals(0, mixed.skippedDuplicates)
        assertEquals(4, mixed.addedTotal)
    }

    @Test fun previewTagsAddThoseOfDuplicatesKeptOrReplaced() {
        val p = plan()
        val had = existing.flatMapTo(HashSet()) { it.tags }
        // Only the new items' tags with «Saltar»: the origin tag and «compras» («casa» is already in the vault).
        assertEquals(setOf(ORIGIN_TAG_BITWARDEN, "compras"), previewNewTags(p, had, DuplicateChoice.SKIP, emptyMap()))
        assertEquals(
            setOf(ORIGIN_TAG_BITWARDEN, "compras", "trabajo"),
            previewNewTags(p, had, DuplicateChoice.SKIP, mapOf(5 to DuplicateChoice.KEEP_BOTH)),
        )
        assertEquals(
            setOf(ORIGIN_TAG_BITWARDEN, "compras", "trabajo", "dinero"),
            previewNewTags(p, had, DuplicateChoice.REPLACE, emptyMap()),
        )
    }

    @Test fun theEstimateIsComputedOnTheMergedVault() {
        val p = plan()
        assertEquals(0L, previewOverBytes(p, DuplicateChoice.KEEP_BOTH, emptyMap(), existing, now = 1L))
    }

    @Test fun confirmWaitsForTheEstimateOfTheChoicesShownAndRunsOnce() {
        val (s, run) = atPreview()
        assertFalse(s.canConfirm)
        assertNull(s.startSaving())
        // An estimate made for other choices than those shown now is ignored.
        s.estimated(s.plan!!, DuplicateChoice.REPLACE, emptyMap(), 0)
        assertFalse(s.canConfirm)
        s.estimated(s.plan!!, s.default, s.overrides, 0)
        assertTrue(s.canConfirm)
        // A change of choice asks for a new estimate.
        s.chooseAll(DuplicateChoice.KEEP_BOTH)
        assertFalse(s.canConfirm)
        s.estimated(s.plan!!, s.default, s.overrides, 0)
        assertEquals(run, s.startSaving())
        assertEquals(ImportStep.Saving, s.step)
        assertTrue(s.busy)
        assertNull(s.startSaving()) // A second tap does nothing.
    }

    @Test fun aVaultThatWouldNotFitCannotBeConfirmed() {
        val (s, _) = atPreview()
        s.estimated(s.plan!!, s.default, s.overrides, 2048)
        assertFalse(s.canConfirm)
        assertEquals(2048L, s.overBytes)
    }

    @Test fun nothingToImportCannotBeConfirmed() {
        val s = ImportSession()
        val run = s.begin(uri)
        s.hold(run, byteArrayOf(1))
        val onlyDuplicate = ImportRead(ImportFormat.BITWARDEN, listOf(login(0, "Banco", "ana")), emptyList(), 0)
        s.onRead(run, ReadResult.Ok(onlyDuplicate))
        s.onPlan(run, planMerge(onlyDuplicate, existing, 1), existing)
        s.estimated(s.plan!!, s.default, s.overrides, 0)
        assertFalse(s.canConfirm)
        s.choose(0, DuplicateChoice.REPLACE)
        s.estimated(s.plan!!, s.default, s.overrides, 0)
        assertTrue(s.canConfirm)
    }

    private fun saving(): Pair<ImportSession, Long> {
        val (s, _) = atPreview()
        s.estimated(s.plan!!, s.default, s.overrides, 0)
        return s to s.startSaving()!!
    }

    @Test fun importedShowsTheResultAndOffersToRemoveTheFileOnce() {
        val (s, run) = saving()
        assertNull(s.takeRemovable()) // Never before an import.
        s.onOutcome(run, ImportOutcome.Imported(ApplyResult.Done(added = 3, replaced = 1), "seguridad-antes-de-importar-x.sgbak"), inAutoFolder = true)
        assertEquals(ImportStep.Done(3, 1, "seguridad-antes-de-importar-x.sgbak", inAutoFolder = true), s.step)
        assertNull(s.plan) // The plan held the file's passwords: gone.
        assertEquals(read.skipped, s.skipped)
        assertEquals(RemoveState.OFFERED, s.removal)
        assertSame(uri, s.takeRemovable())
        assertEquals(RemoveState.REMOVING, s.removal)
        assertNull(s.takeRemovable()) // Only once.
        s.onRemoved(RemoveResult.REMOVED)
        assertEquals(RemoveState.REMOVED, s.removal)
    }

    @Test fun aRefusedRemovalIsNeverAskedAgain() {
        val (s, run) = saving()
        s.onOutcome(run, ImportOutcome.Imported(ApplyResult.Done(1, 0), "copia"), inAutoFolder = false)
        s.declineRemoval()
        assertEquals(RemoveState.DECLINED, s.removal)
        assertNull(s.takeRemovable())
    }

    @Test fun aRemovalThatFailsOrIsNotSupportedSaysSo() {
        val (s, run) = saving()
        s.onOutcome(run, ImportOutcome.Imported(ApplyResult.Done(1, 0), "copia"), inAutoFolder = true)
        s.takeRemovable()
        s.onRemoved(RemoveResult.UNSUPPORTED)
        assertEquals(RemoveState.UNSUPPORTED, s.removal)
        val (t, run2) = saving()
        t.onOutcome(run2, ImportOutcome.Imported(ApplyResult.Done(1, 0), "copia"), inAutoFolder = true)
        t.takeRemovable()
        t.onRemoved(RemoveResult.FAILED)
        assertEquals(RemoveState.FAILED, t.removal)
    }

    @Test fun aCopyThatFailedGoesBackToThePreviewWithItsText() {
        val (s, run) = saving()
        s.onOutcome(run, ImportOutcome.CopyFailed(IMPORT_COPY_WRITE_FAILED), inAutoFolder = true)
        assertEquals(ImportStep.Preview, s.step)
        assertEquals(IMPORT_COPY_WRITE_FAILED, s.error)
        assertNull(s.takeRemovable())
    }

    @Test fun aStalePlanSaysTheVaultChangedAndStartsAgain() {
        val (s, run) = saving()
        s.onOutcome(run, ImportOutcome.NotApplied(ApplyResult.Stale), inAutoFolder = true)
        assertEquals(ImportStep.Pick, s.step)
        assertEquals(IMPORT_VAULT_CHANGED, s.error)
        assertNull(s.plan)
    }

    @Test fun aVaultTooBigSaysHowMuchIsLeftOver() {
        val (s, run) = saving()
        s.onOutcome(run, ImportOutcome.NotApplied(ApplyResult.TooBig(overBytes = 3L * 1024 * 1024 / 2)), inAutoFolder = true)
        assertEquals(ImportStep.Preview, s.step)
        assertEquals(importTooBigText(3L * 1024 * 1024 / 2, 0), s.error)
    }

    @Test fun aSaveThatFailedEndsWithItsMessage() {
        val (s, run) = saving()
        s.onOutcome(run, ImportOutcome.SaveFailed("copia.sgbak"), inAutoFolder = true)
        assertEquals(ImportStep.Ended(IMPORT_SAVE_FAILED), s.step)
        assertNull(s.plan)
        assertNull(s.takeRemovable())
    }

    @Test fun anOutcomeAfterAWipeIsDropped() {
        val (s, run) = saving()
        s.vaultStateIs(unlocked = false)
        s.onOutcome(run, ImportOutcome.Imported(ApplyResult.Done(1, 0), "copia"), inAutoFolder = true)
        assertEquals(ImportStep.Pick, s.step)
        assertNull(s.takeRemovable())
    }

    @Test fun withoutAFolderThePlaceIsAskedAndCancellingImportsNothing() {
        val (s, run) = saving()
        assertNull(s.copyPlaceChosen()) // Only once asked.
        s.needsCopyPlace(run)
        assertEquals(ImportStep.ChooseCopyPlace, s.step)
        assertFalse(s.busy)
        s.copyPlaceCancelled()
        assertEquals(ImportStep.Preview, s.step)
        assertEquals(IMPORT_COPY_CANCELLED, s.error)
        assertNull(s.copyPlaceChosen())
        // Asked again, then chosen: saving, once.
        s.estimated(s.plan!!, s.default, s.overrides, 0)
        val again = s.startSaving()!!
        s.needsCopyPlace(again)
        assertEquals(again, s.copyPlaceChosen())
        assertEquals(ImportStep.Saving, s.step)
        assertNull(s.copyPlaceChosen())
    }

    @Test fun theCopyChosenByHandHasTheNameOfTheImportCopies() {
        assertEquals("seguridad-antes-de-importar-20261104-090507.sgbak", importCopyFileName(java.time.LocalDateTime.of(2026, 11, 4, 9, 5, 7)))
    }

    @Test fun theTextsSayHowMuchAndHowMany() {
        assertEquals("La bóveda resultante no cabría en una copia: sobran unos 1,5 MB. No se ha importado nada.", importTooBigText(3L * 1024 * 1024 / 2, 0))
        assertEquals("La bóveda resultante no cabría en una copia: sobran unos 3 KB. No se ha importado nada.", importTooBigText(2049, 0))
        assertEquals("La bóveda resultante no cabría en una copia: sobran 2 archivos. No se ha importado nada.", importTooBigText(0, 2))
        assertEquals("Ha entrado 1 entrada nueva.", importDoneText(1, 0))
        assertEquals("Han entrado 3 entradas nuevas. Se ha actualizado 1 entrada de la bóveda.", importDoneText(3, 1))
        assertEquals("No ha entrado ninguna entrada nueva. Se han actualizado 2 entradas de la bóveda.", importDoneText(0, 2))
        assertEquals("Entran 3 entradas nuevas: 1 acceso, 1 nota y 1 código 2FA.", previewEntersText(mapOf(EntryType.LOGIN to 1, EntryType.NOTE to 1, EntryType.TOTP to 1)))
        assertEquals("Entran 2 entradas nuevas: 2 accesos.", previewEntersText(mapOf(EntryType.LOGIN to 2)))
        assertEquals("No entra ninguna entrada nueva.", previewEntersText(emptyMap()))
    }
}
