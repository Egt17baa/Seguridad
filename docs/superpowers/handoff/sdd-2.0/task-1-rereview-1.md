### Finding Verdicts
- **I-1 (the upgrade dialog shows during the automatic copy; the copy "de antes" can be a v4 or leave an empty document)** — ADDRESSED.
  - `opened()` no longer sets `pendingUpgrade` (diff 653-656). `unlock()` sets it only after `beforeUpgrade()` returned something other than a saved copy, or after the save failed. It does so under `opening` and only if `upgrade != null && epoch == since` (diff 698-708). Nobody sees `true` while the copy runs, and `upgrade` still blocks writes the whole time.
  - `exportBeforeUpgrade` checks `upgrade == null` with the mutex held, where the snapshot is taken, and writes nothing in that case (diff 806). `AutoBackup.copyBeforeUpgrade` and the dialog button both use it. `fill` deletes the document on `false`, and the dialog calls `discardDocumentNow`.
  - Tests: `upgradeCopiesFirstWithBackupFolder` (pending is false and `upsert` throws inside `beforeUpgrade`; the export has marker 2 and the v3 byte for byte), `theCopyBeforeTheUpgradeIsNeverTheV4`, and `theUpgradeCopyHasItsOwnNameAndIsListedForA6` (marker 2; after `completeUpgrade` the result is `Failed` and no file is left). They cover the three points suggested in the review.
- **M-1 (the master password stays in memory during the automatic copy)** — ADDRESSED. `unlock()` does `master.fill('\u0000')` once `unlockLocked` is done and before the copy (diff 689). The only caller, `VaultScreen.kt:380`, does not reuse `chars` afterwards. Test: `theMasterIsWipedBeforeTheCopy`.
- **M-2 (every copy decodes the vault file again to learn its version)** — ADDRESSED. `writeBackup(..., vaultVersion)` is passed `v.version`, taken in the snapshot (diff 427, 818). `isV4` is only the fallback when no version is given. Test: `writeBackupTakesTheVaultVersion`.
- **M-3 (the dialog hides the reason an export failed)** — ADDRESSED. `upgradeExportFailure` returns the `CryptoFailure` message or the fixed text (diff 163, 200). Test: `theExportFailureSaysWhy`.
- **M-5 (the user does not know the automatic copy failed)** — ADDRESSED. `upgradeAutoCopyFailed` is true only when there was a folder and the copy was not made (`copy != null && copy !is Saved`, diff 706). It is set before `pending`, both under `opening`, and the dialog shows `UPGRADE_AUTO_COPY_FAILED` in the negative colour (diff 213). It is false with no folder, and false when the copy was made but the save failed. Tests: `upgradeCopiesFirstWithBackupFolder` and `upgradeInterruptedKeepsV3`.
- **M-7 (marker 3 allows 33 MiB for attachments too)** — ADDRESSED. The reader applies `vaultMax` only to `vault.json`; attachments and ignored names keep `BACKUP_MAX_ENTRY_BYTES` (diff 475-498, 531). The writer's per-attachment cap is also `BACKUP_MAX_ENTRY_BYTES` (diff 450). Test: `marker3KeepsTheAttachmentCap`.
- **O-3 with a folder (a visible one-time notice after a silent upgrade)** — ADDRESSED.
  - After a saved copy and a successful `completeUpgradeLocked`, `upgradeDone` takes `copy.where` (diff 698-700).
  - `VaultGate` shows `VaultUpgradeDoneNotice` when `pendingUpgrade` is false and `upgradeDone != null` (`VaultScreen.kt` diff 97-102). It says that earlier versions no longer open the vault or its new copies, and it gives the copy's file name through `plainReportText`.
  - Any dismissal calls `upgradeDoneShown()`, and `destroy()` clears it. The texts are fixed, with a `ForbiddenWords` test.
  - Tests: `upgradeCopiesFirstWithBackupFolder` (the value, then `upgradeDoneShown()`) and `theNewTextsAreFixedAndClean`.

### New Breakage in the Fix Diff
None Critical or Important. Details I checked:
- **Race on the new `pendingUpgrade` path.**
  - A lock during the copy sets `upgrade = null` and bumps `epoch`, so the `synchronized(opening)` check in `unlock` skips the dialog.
  - A re-unlock during the copy starts a new epoch, so the old `unlock` cannot touch the new state.
  - If the save fails after a saved copy, `pending` becomes `true` with `upgradeAutoCopyFailed = false`. `upgradeInterruptedKeepsV3` asserts this.
  - Nothing else completes the upgrade between unlock and the dialog, because the dialog is the only caller of `completeUpgrade`.
- **The new `export` with `beforeUpgradeOnly`.** The early `return false` sits inside `mutex.withLock` before any other value is assigned, and `refs`, `json` and `version` are assigned inside the lock. It reads the same as the rest of the function, so I see no definite-assignment problem. The compiled tests (1956 and 1949, per the report) support this.
- **Minor, informational:** `upgradeDone` lives in memory only. A process death before it is shown loses the notice, and the report states this as a limit. `lock()` does not clear `upgradeDone`, so the notice waits for the next unlock. That fits "shown once".

### Out-of-Scope Observations
- M-4, M-6 and M-8 are deferred by the ruling and were not part of this round. They stay with task 3, the performance test, and task 2.
- The new texts (`UPGRADE_AUTO_COPY_FAILED`, `UPGRADE_DONE_*`, `upgradeDoneCopy`) are listed in the report for the final Abogado Digital review.
- I did not re-run the suite; the report shows the output and the diff matches its claims.

### Verdict
**Fix round:** All findings addressed, no new Critical/Important breakage.
