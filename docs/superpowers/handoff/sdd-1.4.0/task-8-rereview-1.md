# Task 8, re-review of fix round 1 (5efe79a..7782807)

### Finding Verdicts
- **I-1: ADDRESSED.** `MergePlan.kt` `jsonEscapedLength`/`escapedTextBytes` count the escaped bytes without building anything. They are used in `estimatedVaultFileBytes` and in the pre-check of `VaultStore.applyImport` (`low = (escapedTextBytes(merged)*4+2)/3`). Tests: `MergePlanTest.theEscapedLengthIsTheJsonOne` (against the real `appJson`), `aHeavilyEscapedImportIsTooBigWithoutEncodingIt`, `VaultStoreImportTest.aHeavilyEscapedImportIsRefusedBeforeEncoding`, and `tooBigIsRefused` redone for the exact path. I checked the encoder myself (kotlinx-serialization-json 1.11.0, a small Java run): `\b \t \n \f \r` get 2 bytes, the other controls U+0000..U+001F get `\u00XX` (6), and `"` and `\` get 2. `/`, U+007F, U+2028/2029 are left raw, and non-BMP and lone surrogates follow UTF-8/`?`, as `jsonEscapedLength` does. The count is never lower than the encoder for any char class.
- **M-1: ADDRESSED.** `replaceWith` (MergePlan.kt, the `totp = ...` block) keeps the vault's blank issuer/account. `replaceTakesTheCodeOfATotp` covers both the blank and the filled case.
- **M-2: ADDRESSED.** `replaceWith` skips the notes block when the vault's notes already end with `"\n"+incoming.notes`. It returns `existing` itself when `replaced.copy(updatedAt = existing.updatedAt) == existing`. Tests: `replacingTwiceWithTheSameFileChangesNothingTheSecondTime`, `aSecondReplaceOfTheSameFileWritesNothing` (same bytes, same revision, `Done(0,0)`).
- **M-3: ADDRESSED.** `AutoBackup.kt:486-491`: a request without targets deletes only `owned` (automatic copies). Test `aRequestWithoutTargetsKeepsTheImportCopies`.
- **M-4 (R16): ADDRESSED.** `VaultStore.importSnapshot()` reads entries and revision under `mutex`, and `planImport` builds the plan from it. `aPlanFromTheSnapshotIsNeverPairedWithANewerRevision` uses a held dispatcher to show the plan waits for the save in progress, so it is a real test of the race.
- **M-5 (R16): ADDRESSED.** `ImportFlow.confirm` catches `Exception` from `apply()` and rethrows `CancellationException`. It returns `SaveFailed(where, IMPORT_SAVE_FAILED)` (ImportFlow.kt). Test `aSaveThatFailsIsSaidWithAFixedText`.
- **M-6: ADDRESSED.** `AutoBackup.kt:413` returns `IMPORT_COPY_VAULT_CHANGED`: «...La copia que se hizo sigue en la carpeta.» That is true, since the copy is renamed complete and left unlisted. Test `aVaultReplacedDuringTheCopyIsSaidAsSuch` (uses `destroy` and a gate on the upload).
- **M-7: ADDRESSED.** `AppContainer.kt`: each KDoc is now over its own function.
- **M-8: ADDRESSED.** `importTags` collapses runs of whitespace before `normalizeTags`; the test covers `«Trabajo, Casa»` and `« Trabajo ,,\t Casa »`.
- **M-9: ADDRESSED.** `copyTo`/`fill` take `defer`, and `importCopyNow` passes `false` on both the temp path and the direct path. Test `aLockDuringTheCopyMakesNoAutomaticCopyLater`.
- **M-10: ADDRESSED.** The `applyImport` KDoc now says "no clock reset, as the mark only rises, as with any save". `commit` does call `onClockSeen` via `opened`, so it is true.
- **R15: ADDRESSED.**
  - The four constants, the `SettingsScreen.kt:284` hint, the pins (`FinalLegalReviewTest`, `ToolTextsTest`, `AutoBackupF3Test`) and the annex agree with the rulings.
  - The normal request: `MasterChanged.oldCopies` = `currentAutoBackupCopies + currentImportCopies` (AppContainer.kt:297), and `rotate` deletes `old + oldImports`, so the texts are exact.
  - The pre-1.3.0 request (no targets), after M-3: only automatic copies are deleted. The texts still say «y las de antes de importar», which is not false: that request predates the first import copy (1.4.0), so there is nothing to delete (see Minor N-1).

### New Breakage in the Fix Diff
No Critical or Important.
- **N-1 (Minor, AutoBackup.kt:521-526 with 486-491).** `AUTO_BACKUP_REPLACED` says import copies from before the change were deleted. In the legacy no-targets path none are targeted. This is true only because none can exist at that moment. Today the code and the text agree. A one-line comment at 489 saying so would stop a later change from making the text false. Not blocking.
- **N-2 (Minor, ImportTexts.kt:162-164 and ImportFlow.kt).** `IMPORT_SAVE_FAILED` says «la bóveda no ha cambiado. ¿Queda espacio en el móvil?». It is also what `SaveFailed` shows for any `Exception` from `apply`, for example `checkCanHold` refusing the merge (nothing saved, but the disk-space hint is off). A `changed()` failure after the atomic rename (`VaultStore.commit` lines 353-354, `mustSave` rethrows) would make «no ha cambiado» false. That is an extremely rare case, and the KDoc claim "throws before its atomic rename" is a little too strong. Suggest «...no se ha guardado nada» only if you want to be exact; fine to leave as it is.
- **N-3 (Minor, the report).** The report's line "1621 tests" (line 69) and "1631" (line 193) are different runs (before and after the round). The round-1 figure of 1631 / 1626 jvm-check is shown, and lint's 16 warnings are listed. No action.

M-2's check cannot skip a real change. `updatedAt` is the only field excluded from the comparison. A password-only change adds a history entry (`withNewPassword`), and a tag-only change alters `tags`, so both differ from `existing` and are saved. The `endsWith("\n"+incoming.notes)` shortcut only skips text that is already in the vault, so nothing is lost.

The annex diff (`2026-10-27-seguridad-1.3.0-textos.md`) touches only the R15 lines: button, result, "Detrás del resultado" header and the two unlisted texts, and the dropped text.

### Out-of-Scope Observations
- `ImportOutcome.SaveFailed`, `IMPORT_SAVE_FAILED`, `IMPORT_COPY_VAULT_CHANGED` and `planImport` have no UI consumer yet (Task 9). The new `when` over `ImportOutcome` there must handle `SaveFailed`.
- `docs/privacidad.html` still has the old A6 sentence (Task 12, per R15). `FinalLegalReviewTest` pins it, so the Task 12 change must update the pin too.

### Textos para el Abogado Digital (revisión final)
- ImportTexts.kt:157: «La bóveda se ha sustituido o borrado mientras se hacía la copia, así que no se ha importado nada. La copia que se hizo sigue en la carpeta.»
- ImportTexts.kt:162: «No se pudo guardar la importación: la bóveda no ha cambiado. ¿Queda espacio en el móvil? La copia de antes de importar sí se hizo.»
- ToolTexts.kt:434: «Hacer una copia nueva y borrar las anteriores (automáticas y de antes de importar)»
- ToolTexts.kt:447: «Las copias anteriores que ibas a borrar no se borraron: siguen en su carpeta.»
- AutoBackup.kt:521: «Se hizo una copia nueva y se borraron las copias automáticas y las de antes de importar hechas antes del cambio de contraseña: las que la app tenía apuntadas en esta carpeta. Las que se hayan copiado a otro sitio (otra carpeta, la nube o su papelera) no se borran.»
- AutoBackup.kt:529: «En la carpeta queda 1 copia con nombre de copia de la app (automática o de antes de importar) que la app no tenía apuntada (por ejemplo, de antes de la versión 1.2.1 o de otra bóveda): no se ha borrado y puede que se abra con una contraseña anterior. Si no la necesitas, bórrala a mano.» (plural: «En la carpeta quedan $n copias con nombre de copia de la app (automática o de antes de importar) que la app no tenía apuntadas (...): no se han borrado y puede que se abran con una contraseña anterior. Si no las necesitas, bórralas a mano.»)
- SettingsScreen.kt:284: «Cada copia va cifrada con la contraseña maestra que tenía la bóveda al hacerla: si la cambias, las copias anteriores se siguen abriendo con la antigua. Solo se borran las copias automáticas más antiguas (seguridad-auto-…) y, si lo pides tras cambiar la contraseña maestra, las de antes de importar (seguridad-antes-de-importar-…); nunca otros archivos de la carpeta.»

### Revisión experto-seguridad-cripto
- Memory exhaustion (I-1): the pre-check counts escaped bytes with no allocation (MergePlan.kt `jsonEscapedLength`, VaultStore.kt `applyImport`), so a 5 MB file of controls is refused before it is encoded or encrypted. I checked the escape rules against the real kotlinx 1.11.0 encoder.
- TOCTOU (M-4): entries and revision come from one snapshot under the mutex, and `applyImport` still re-checks the revision inside the critical section.
- Veracity: the R15 texts match what `rotate` deletes in the normal path (MasterChanged.oldCopies includes the import copies). The legacy-path nuance is N-1. No exception text reaches the UI (ImportFlow).
- No data loss: M-1 and M-2 only skip changes that add nothing; the TOTP issuer/account are never emptied.

### Revisión Ingeniero Móvil Senior
- Concurrency: `importSnapshot` uses the store's mutex; `ImportFlow` rethrows `CancellationException`; `copyBeforeImport` stays under the AutoBackup lock. The `defer=false` flag is passed on both write paths (AutoBackup.kt `copyTo`/`fill`).
- Scheduling: a REPLACE with no change does not write or trigger an automatic copy (the `Done(0,0)` path in `applyImport`).
- Tests are deterministic (a held dispatcher, a gate on the upload).

### Verdict — **Fix round:** All findings addressed, no new Critical/Important breakage
(Three Minors, N-1 to N-3, all optional; the project fixes every Minor, so N-1 and N-2 are worth a one-line change each.)
