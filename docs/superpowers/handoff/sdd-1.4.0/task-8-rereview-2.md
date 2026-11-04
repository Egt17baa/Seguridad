# Task 8, re-review of fix round 2 (7782807..b0779e1)

### Finding Verdicts
- **N-1: ADDRESSED.** AutoBackup.kt, the comment above `val targets = ...` in `attempt`. It states why none exists and that AUTO_BACKUP_REPLACED stays true. The behaviour test is `aRequestWithoutTargetsKeepsTheImportCopies`.
- **N-2: ADDRESSED.** ImportTexts.kt: IMPORT_SAVE_FAILED = «No se ha podido terminar la importación. ¿Queda espacio en el móvil? La copia de antes de importar sí se hizo.» The KDocs in ImportFlow.kt (`ImportOutcome.SaveFailed` and `ImportFlow`) are corrected, and the "throws before its atomic rename" claim is gone. Every path that reaches `SaveFailed`:
  - Disk-full IOException before the rename (`save` writes `vault.bin.tmp` first, AtomicFile.kt:17): "could not finish" is true, the question about space fits, and the copy was made.
  - Failure after the rename (`commit` -> `changed()` or `opened()` throwing): the import may be saved. «No se ha podido terminar» is still true. It says nothing about the vault being unchanged. This is the case the old text made false.
  - `checkCanHold`'s refusal: it is not a size check. `VaultStore.checkCanHold` (VaultStore.kt:725-730) only throws IllegalStateException for personal entries on a v2 vault (`NEEDS_MASTER_FOR_CONTACTS`) or for a second identity (`ONLY_ONE_IDENTITY`). `applyImport` passes it only `changed` entries, and an import adds no IDENTITY, CONTACT or SEED. `replaceWith` never touches personal types. So the throw is theoretically possible but unreachable from an import. Size refusals (the entry count over BACKUP_MAX_ENTRIES, the escaped-bytes pre-check, the exact file size) all come back as `ApplyResult.TooBig` through `NotApplied`, never as an exception. The hint «¿Queda espacio en el móvil?» is therefore never shown for a size refusal. In the unreachable checkCanHold case it would be an unneeded but harmless question, not a false statement.
  - "La copia ... sí se hizo" always holds, because `SaveFailed` is only built after `CopyResult.Saved`.
  - Tests: `everySaveFailureGivesTheSameTrueText` (IllegalStateException, IOException, RuntimeException) and `aFullDiskIsASaveFailureAndTheVaultIsAsItWas` (real store, a directory squatting on `vault.bin.tmp`, bytes unchanged, entries empty). The after-rename path cannot be provoked without changing the store. The report says so honestly, and the text no longer depends on it.
- **N-3: ADDRESSED.** The report now labels 1621 as the initial round. Round 2 figures: 1633 app tests, 1628 jvm-check, 16 known lint warnings, BUILD SUCCESSFUL (as shown in the report). I did not re-run the suite.

### New Breakage in the Fix Diff
None (no Critical, Important or Minor). The change is a text, KDocs, one comment and tests. The behaviour is unchanged, and cancellation is still rethrown in `confirm`.

### Out-of-Scope Observations
- Task 9 still has to handle `ImportOutcome.SaveFailed` in its `when` and show `message`.
- docs/privacidad.html and its pin are still for Task 12 (R15).

### Textos para el Abogado Digital (revisión final)
- ImportTexts.kt (IMPORT_SAVE_FAILED), changed: «No se ha podido terminar la importación. ¿Queda espacio en el móvil? La copia de antes de importar sí se hizo.» This replaces round 1's text.

### Revisión experto-seguridad-cripto
- The text now claims only what holds on every path, including a failure after the atomic rename. No exception text reaches the UI. AUTO_BACKUP_REPLACED is guarded by the comment (AutoBackup.kt, `attempt`).

### Revisión Ingeniero Móvil Senior
- No behavioural change. The new tests use the real store and are deterministic (they rely on a directory at the `.tmp` path).

### Verdict — **Fix round:** All findings addressed, no new Critical/Important breakage
