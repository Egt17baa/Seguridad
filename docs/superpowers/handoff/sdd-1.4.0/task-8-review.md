# Task 8 review: merge plan, pre-import copy and single save (72614ce..5efe79a)

### Spec Compliance
- ✅ Interfaces as in the brief: `DuplicateChoice`, `Duplicate`, `MergePlan`, `planMerge`, `mergedEntries`, `replaceWith`, `ApplyResult` (`Done`/`Stale`/`TooBig`), `VaultStore.applyImport`, `CopyResult`, `AutoBackup.copyBeforeImport`, `ImportFlow`/`ImportOutcome`. The deviations (`TooBig.overFiles`, `zone` params, `applyImport` as a member, AppContainer wiring, `Done(0,0)` with no write) were accepted in Ruling R14.
- ✅ Duplicate rules (`MergePlan.kt:260-296`): LOGIN by trimmed, case-folded title, trimmed user and host (lowercase, no `www.`, no user, port or path); title and user are enough when either side has no URL (`g.any` / `g.hostless`). TOTP by `totpKeyOf`. NOTE by title and trimmed text. Only entries of the same type are compared, so IDENTITY, CONTACT and SEED never match. The file is checked against itself first (`MergePlan.kt:80-88`). Linear, using hash maps.
- ✅ REPLACE (`MergePlan.kt:157-177`): keeps id, `createdAt`, favourite and attachments; a blank incoming field never empties one; notes go under «Importado el D-M-AAAA:» only when both have notes and they differ; the password goes through `withNewPassword` (`History.kt:9`, which skips an equal password); tags are the union, and own tags are never dropped. Gap: inside the 2FA, see M-1.
- ✅ Tags: origin tag first, then folders through `normalizeTags` (commas become spaces), at most 10, the last folders dropped first (`MergePlan.kt:100-108`). `normalizeTags` now cuts by code points (`EntryQuery.kt:41`, `takeCodePoints`), as decision 2 asks.
- ✅ TOCTOU (decision b): the revision is checked inside `modify`'s mutex, in the same critical section as `VaultCrypto.write` and `commit` (`VaultStore.kt:678`, `:602-620`). `commit` is `NonCancellable` (`:350`). A lock before the call gives `Stale`. (See M-4 for how the caller takes the snapshot.)
- ✅ Bounds (R14): attachments + 2 ≤ `BACKUP_MAX_ENTRIES` before anything is built (`VaultStore.kt:680`). Exact checks after encryption: the encoded file ≤ `BACKUP_MAX_ENTRY_BYTES` and marker + file + attachment files ≤ `BACKUP_MAX_TOTAL_BYTES`, the same accounting as `Backup.kt:107-135` (`VaultStore.kt:697-703`). The pre-encryption lower bound is too loose, though: see I-1.
- ✅ Fingerprint and A11: the data key, `wrappedKey` and `salt` do not change (`fingerprintStaysEnabled`); nothing biometric is called; there is no `onClockReset`. `commit` → `opened` does call `onClockSeen` (`VaultStore.kt:388`), as every save does: the mark only goes up, which A11 allows. The KDoc's «no clock mark» is inexact (M-10).
- ✅ A6: `AppContainer.kt:297` adds the import copies to `MasterChanged.oldCopies`. `Settings.kt:163` adds those of the same folder to a request saved while locked. `AutoBackup.kt:642` deletes them by id only when they are targets. The «sin apuntar» count uses `IMPORT_COPY_NAME` (`:663`). Rotation never puts them in `surplus`, and only their `.tmp` leftovers are deleted by name (`:638`). The import copies are not in `autoBackupCopies`, and a new or imported vault forgets them (`forgettingAutoBackupCopies`, with the undo restoring them). Exception: the pre-1.3.0 request path, see M-3.
- ✅ Copy: `seguridad-antes-de-importar-AAAAMMDD-HHMMSS.sgbak`, written under a temp name and renamed, or directly when `canRename == false`; it runs in AutoBackup's scope and lock; a change of generation gives `Failed` and the copy is left unlisted (`AutoBackup.kt:399-432`).
- ✅ Hostile data (c): stored verbatim (`importedTextIsStoredVerbatim`); `urlHost` is plain string work; nothing in the diff opens a URI, builds an Intent, logs or uses `e.message` (searched the diff for `Log.`, `println`, `Intent`, `Uri.parse`, `openUri`, `e.message`: none).
- ✅ All tests the brief lists are present, including `copyBeforeImportWithoutRenameWritesFinalName`, `secondImportOfSameFileAddsNothing` and the process-death case.
- ⚠️ Cannot verify from the diff: whether the readers of Tasks 6 and 7 keep control characters verbatim (I-1's worst case assumes they do, as decision (c) says); whether ART stores the large ASCII strings compressed (this changes I-1's peak by about 2×).

### Strengths
- One critical section for the revision check, the size checks and the write, done through a small, backward-compatible `accept` hook on `modify`. Every other caller keeps its trailing lambda (only `applyImport` passes `accept`, `VaultStore.kt:676`).
- Duplicate detection is linear, with the asymmetric «either side has no URL» rule encoded cleanly in `Matcher`.
- `copyBeforeImport` reuses `copyTo`/`fill`/`discard` (no second write path). It runs under AutoBackup's lock, so `onImportCopy` and the rotation's rewrite of the list never race (both happen under `lock`, `AutoBackup.kt:399`, `:444`, `:524`).
- CADENAS-3 is applied consistently to the new list: generation checked in `recordImportCopy`, and a stale result gives `importCopies = null`.
- The tests are good: mutation-checked Stale, exact-size refusal with JSON escapes, process death between copy and apply, legacy settings compatibility through defaults.

### Issues

#### Critical (Must Fix)
None.

#### Important (Should Fix)

**I-1. The pre-encryption bound does not stop large allocations for escaped text (decision (a), dispatch item 1).**
- **Where:** `VaultStore.kt:686` and `MergePlan.kt:187-191`.
- **What happens:** the bound counts each UTF-16 unit once (`plainCharsLowerBound`). The JSON escapes a control character as `\u00XX` (6 bytes) and writes a CJK character as 3 bytes. A crafted import of about 4.7 M control characters (for example 75 notes × 63 K, a CSV of about 5 MB, under `IMPORT_MAX_BYTES` and `MAX_NOTES`) passes the bound (4.7 M × 4/3 ≈ 6 MiB). It then makes `VaultCrypto.write` build a JSON of about 28 MB, its bytes, the ciphertext, about 38 MB of Base64, and then `encodeFile(next)` (`:698`) about 38 MB more. That is about 130–200 MB in transient allocations before the exact check answers `TooBig`.
- **Impact:** `tooBigIsRefused` itself shows the escape gap (1.2 M chars give more than 6 MiB). The preview's `estimatedVaultFileBytes` encodes the same 28 MB string. On a 192–256 MB heap, with the read file and the plan already in memory, this can end in an `OutOfMemoryError`, which `modify` does not catch (`catch (e: Exception)`), and the app is closed by a hostile file.
- **Fix:** replace `plainCharsLowerBound` with an allocation-free exact length of the escaped string contents:
  - `"` and `\` count 2;
  - `\b \f \n \r \t` count 2;
  - any other character below 0x20 counts 6;
  - everything else counts its UTF-8 length.

  Sum this over the same fields. It is still a lower bound (field names and numbers are left out), but now within the per-entry overhead of the real size. Use it in both `applyImport` and `estimatedVaultFileBytes` before any `encodeToString`.
- **Test:** 75 × 63 000 `\u0001` gives `TooBig` from the pre-check. Assert this, for instance with an `encodeEntries` counter or by checking that no `.tmp` file is created, and in `estimatedVaultFileBytes` without encoding.

#### Minor (Nice to Have)

**M-1. REPLACE of a TOTP loses the vault's issuer and account when the file leaves them blank** (`MergePlan.kt:171`).
- **What happens:** `incoming.totp ?: existing.totp` takes the whole `TotpSecret`. The spec says «un campo vacío del archivo nunca borra uno lleno de la bóveda» («so that replacing never loses anything»). `replaceTakesTheCodeOfATotp` has account «ana» in the vault and «» in the file, and does not assert it; the result is «».
- **Fix:** `totp = incoming.totp?.let { t -> existing.totp?.let { e -> t.copy(issuer = t.issuer.ifBlank { e.issuer }, account = t.account.ifBlank { e.account }) } ?: t } ?: existing.totp`, and assert the account in the test.

**M-2. Repeating REPLACE with the same file keeps growing the notes, and always counts as «replaced» and writes** (`MergePlan.kt:159-176`, `VaultStore.kt:693`).
- **What happens:** after one REPLACE, the vault notes are «A\n\nImportado el …:\nB». The same file again has «B», which differs, so it is appended again, with no `MAX_NOTES` bound. An entry whose only change is `updatedAt` is a new object, so `changed` is non-empty: `Done(replaced = n)`, a needless save and a new automatic copy.
- **Fix:** do not append when `existing.notes` already ends with `"\n" + incoming.notes`. In `replaceWith`, return `existing` itself when the merge equals `existing` but for `updatedAt`.
- **Test:** a second REPLACE import of the same file gives `Done(0, 0)` and leaves the bytes unchanged.

**M-3. A request saved before 1.3.0 (no targets) now deletes every import copy** (`AutoBackup.kt:486`, `+ importCopies()`).
- **What happens:** such a request predates the password change in 1.2.x, and every import copy is made in 1.4.0, so all of them are under the current password. Deleting them deletes the user's rollback copies of imports, which A6 does not intend («cifrada con la maestra de ese momento»).
- **Fix:** drop `+ importCopies()` there, so only `owned.filter { … }` remains.
- **Test:** a pending request with `autoBackupReplaceTargets = null` keeps the listed import copy.

**M-4. Plan snapshot consistency.**
- **What happens:** `planMerge(read, existing, revision)` takes the entries and the revision separately. `commit` raises `_revision` (`changed()`, `VaultStore.kt:354`) before `opened()` publishes the new entries (`:357`). A caller that reads the entries and then `revision` can pair the old entries with the new revision and pass the Stale check. The plan's duplicates then refer to a list that is not the one `mergedEntries` merges into: a deleted target is silently skipped (`MergePlan.kt:139`), and a new duplicate is missed.
- **Fix:** add `suspend fun VaultStore.importSnapshot(): Pair<List<VaultEntry>, Long>?`, read under the mutex, and name it in Task 9's brief as the only way to build a plan.

**M-5. `ImportFlow.confirm()` rethrows any failure of `apply`** (`ImportFlow.kt:39`, documented at `:151-152`).
- **What happens:** the copy has already been made and listed. Whatever Task 9 does with the raw exception decides whether its message reaches the screen.
- **Fix:** catch `Exception` (rethrow `CancellationException`). Return a new `ImportOutcome.SaveFailed(where)` with a fixed text, for example «No se pudo guardar la importación: la bóveda no ha cambiado. La copia de antes de importar está en la carpeta.» (for the Abogado). Or, at least, write this rule into Task 9's brief and the ledger.

**M-6. A copy made while the vault was replaced says «No se pudo hacer la copia», but a complete copy file is left in the folder** (`AutoBackup.kt:409`).
- **What happens:** the veracity rule (as O-1 in 1.3.1) is not met.
- **Fix:** a specific fixed text, for example `IMPORT_COPY_VAULT_CHANGED` = «La bóveda ha cambiado mientras se hacía la copia. No se ha importado nada.», with a test.

**M-7. Misplaced KDoc in `AppContainer.kt:312-331`.** `recordAutoBackup`'s KDoc («Keeps how the last automatic copy went…») now sits just above `recordImportCopy`'s KDoc, so it dangles, and `recordAutoBackup` (`:332`) has none. Move `recordImportCopy` with its KDoc below `recordAutoBackup`.

**M-8. Folders with «, » become tags with double spaces** (`MergePlan.kt:105`; the test pins «trabajo  casa»).
- **What happens:** the tag differs from a user's «trabajo casa», and the filter treats them as two tags.
- **Fix:** `folder.replace(',', ' ').replace(Regex("\\s+"), " ")` before `normalizeTags`, and update the test.

**M-9. A lock racing the import copy schedules an automatic copy.** `fill()` → `defer()` (`AutoBackup.kt:587`) sets `pending` when `exportBackup` returns false. An import copy refused because of a lock in the window between `:405` and the snapshot makes an extra automatic copy at the next unlock, with no change, which rotates out the oldest one. Fix: add a `defer: Boolean = true` parameter to `copyTo`/`fill`, false from `importCopyNow`.

**M-10. The KDoc of `applyImport` says «no clock mark»** (`VaultStore.kt:670`), but `commit` → `opened` calls `onClockSeen` (`:388`), as every save does. Reword to «no clock reset (the mark only rises, as with any save), no biometric call». The report's «applyImport no llama a … onClockSeen» is likewise inexact.

Notes for Task 9 (not findings):
- `overrides` is keyed by `ImportedItem.index`, not by the position in `plan.duplicates`. Overrides for non-duplicates are ignored.
- `newTags` counts only `newItems`: the preview must add the tags of duplicates chosen for KEEP_BOTH or REPLACE.
- `copyBeforeImport() == null` means the `CREATE_DOCUMENT` path.

### Traces 1–7

1. **Stale.** `applyImport` → `modify` (`VaultStore.kt:602`): `mutex.withLock`, then `vault ?: return false` (locked: `result` stays `Stale`, `:672`), then `_state` must be `Unlocked`, then inside `change` `_revision.value != plan.revision → null` (`:678`). An edit, a new master, an import of a backup (`changed()` at `:949`, or `commit` at `:354`), a new vault (`createLocked` → `commit`) all raise `_revision`, and every writer of it holds the mutex. A null `change` gives `key.fill(0)` and no write (`:612-614`). The check, the encryption (`:608`) and `commit` (`:617`) run in one `withLock`. ✅ (Caveat: the caller's snapshot, M-4.)
2. **Cancellation.**
   - `applyImport`: if cancelled while waiting for the mutex, nothing happens. At the `withContext(compute)` exit or the `withContext(io)` inside `accept` (`:699`), a `CancellationException` is caught by `catch (e: Exception)` at `:609`, the key copy is wiped and nothing is written. `commit` is `NonCancellable`, so the atomic rename is never torn. ✅
   - `copyBeforeImport`: runs in `scope.async`, so a caller leaving does not cut it (`AutoBackup.kt:399`). Inside, `fill` discards the file on any `Throwable` under `NonCancellable` (`:589-593`), and a failed rename discards the temp file (`:566`). A `.tmp` left by app death is deleted by the next rotation (`IMPORT_TEMP_NAME`, `:638`). No key is copied in this path (`exportBackup` uses the encrypted file). ✅
3. **Duplicates.** The rules are as in the brief (Spec Compliance above). Repeated items are counted once (`MergePlan.kt:81-84`). An out-of-range or non-duplicate override is ignored: `duplicateOf[item.index]` is null, so the item is a new one (`:134-135`). Duplicates no longer matching the vault cannot pass the revision check. If they did (M-4), `position[...] ?: continue` (`:139`) skips the item silently. Indexes are unique by the reader's contract (`ImportModel.kt:24-30`, `nextIndex`). ✅
4. **REPLACE.** Empty fields are kept (`:168-170`). Notes are appended only when both differ (`:159-165`). The old password goes to the history (`:176`, `History.kt:9`). id, `createdAt`, favourite and attachments are kept (`existing.copy`). Tags: own first, then incoming; over 10, the incoming tags at the end go first (the folders, then the origin tag; the origin tag is lost only when the vault entry already has 10). Gaps: M-1 (TOTP labels) and M-2 (repeat).
5. **20 000 items near the field limits.** The file is ≤ 16 MiB and ≤ 20 000 items (Tasks 6 and 7). `mergedEntries` makes about 20 000 shallow copies (the strings are shared), then `plainCharsLowerBound` refuses at `VaultStore.kt:686` with no encoding, when the characters are over about 4.7 M. When they are under it but heavily escaped, the refusal comes only at `:698`, after about 130–200 MB (I-1). The preview estimate (`MergePlan.kt:187`) has the same shortcut and the same gap. The exact check decides, as the brief says.
6. **Copy.**
   - **Name:** `IMPORT_COPY_PREFIX` + `STAMP` in AutoBackup's `zone` (the system default, fixed at construction, local time like the automatic copies).
   - **Collisions:** `freeName` moves to the next free second, checking both the copy name and its `.tmp` (`:603-610`; tested).
   - **Write:** temp name and rename (`:559-578`). `copyBeforeImportWithoutRenameWritesFinalName` checks that the copy is complete under its final name and as long as `exportBackup`.
   - **Generation change:** `Failed` and an unlisted copy (`:409`; text issue M-6).
7. **ImportFlow.** A copy that is `Failed` or throws gives `CopyFailed` with a fixed text, and `apply` is never called (`ImportFlow.kt:30-37`; tested). A cancellation is rethrown. If `apply` throws, the exception propagates raw to the caller, with nothing saved (the vault is unchanged) and the copy listed (M-5).

### Revisión experto-seguridad-cripto

- **Model:** a hostile import file (decisions (a) and (c)), a vault that changes between the preview and the save (b), and a lost or replaced vault during the copy (CADENAS-3).
- **TOCTOU:** closed in the store (`VaultStore.kt:678` in the same `withLock` as `:608` and `:617`). The remaining gap is in how the snapshot is taken (M-4, Minor: the impact is a wrong but benign merge).
- **Atomicity:** one `VaultCrypto.write` and one `commit` (`NonCancellable`, `save` = temp file + atomic rename). An exception wipes the key copy (`:609-611`). Verified by `applyIsAllOrNothing`.
- **Resource exhaustion:** the file count and the exact size match `writeBackup` (`Backup.kt:107-135` against `VaultStore.kt:680` and `:697-703`). The pre-check undercounts escapes by up to 6× (I-1, Important: a security finding with a concrete trigger).
- **Keys:** no new primitive and no new key; the data key and the wrap do not change (`fingerprintStaysEnabled`); the key copy is wiped on every `modify` exit. `totpKeyOf` keeps the decoded secret as a hex `String` in a HashMap (`TotpImport.kt:88`, unchanged pattern from `totpImportEntries`; the strings are immutable and short-lived, informational).
- **Hostile content:** verbatim storage; `urlHost` takes the text after the last `@`, so `https://evil\@bank.es` compares as `bank.es`. This only affects duplicate matching, and REPLACE needs the user's choice. No URL is opened (informational).
- **A6:** import copies are deleted by id only and counted by exact name only (`AutoBackup.kt:642`, `:663`). The legacy path over-deletes (M-3).
- **Secrets and logs:** no logs, no `e.message`; the error texts are fixed (`ImportTexts.kt:115-128`); `ImportFlow` holds no state.
- **Formats:** none new (`.sgbak` as before; `Settings` gains fields with defaults). No dependency, permission, host or copied code (the matching, tags and size code is specific to this spec; nothing resembles an importer from another app).

### Revisión Ingeniero Móvil Senior

- **Concurrency:**
  - `copyBeforeImport` runs in `scope.async { lock.withLock }` (`AutoBackup.kt:399`), so it never overlaps a rotation, and `onImportCopy` and `onResult` (`:524`) both run under `lock`, with no lost update of `importCopies`.
  - `applyImport` runs on `compute` with the vault mutex, and reads the attachment lengths on `io` inside it (`VaultStore.kt:699`; local files, so holding the mutex is acceptable).
- **Memory:** I-1. On a fitting import, the file JSON is also built twice (`encodeFile` at `:698` and again in `save`); with I-1 fixed this is at most about 2 × 6 MB, acceptable.
- **Process death:** the copy is durable before the save; the settings list is atomic (`writeTextAtomically`); the vault is untouched (`processDeathBetweenCopyAndApplyLeavesTheVaultAsItWas`).
- **API and compatibility:**
  - `modify(accept = …, change = …)` keeps the trailing lambda for all existing callers.
  - The new `AutoBackup` parameters have defaults, and `Settings` fields have defaults, so an old `settings.json` decodes.
  - `totpKeyOf` changes from private to internal; its existing caller, `totpImportEntries` (`TotpImport.kt:96,102`), keeps the same rule.
- **Wiring:** `AppContainer.kt:283-284` and `:297` are correct. The KDoc is misplaced (M-7).
- **Lifecycle:** no UI; no `rememberSaveable` or `Bundle`; the secrets stay in the caller's lambdas (Task 9's ViewModel).

### Textos para el Abogado Digital (revisión final)

New texts in the diff:
- `imports/ImportTexts.kt:109`: «Importado el D-M-AAAA:» (for example «Importado el 4-11-2026:»), written into the user's notes.
- `imports/ImportTexts.kt:115-117` (`IMPORT_COPY_NO_PERMISSION`): «No se pudo hacer la copia antes de importar: la app ya no tiene permiso para usar la carpeta de la copia automática. Vuelve a elegirla en Ajustes. No se ha importado nada.»
- `imports/ImportTexts.kt:118-120` (`IMPORT_COPY_NO_FOLDER`): «No se pudo hacer la copia antes de importar: no se encuentra la carpeta de la copia automática. Vuelve a elegirla en Ajustes. No se ha importado nada.»
- `imports/ImportTexts.kt:121-123` (`IMPORT_COPY_WRITE_FAILED`): «No se pudo escribir la copia antes de importar en la carpeta de la copia automática. ¿Queda espacio? No se ha importado nada.»
- `imports/ImportTexts.kt:124-126` (`IMPORT_COPY_VAULT_PROBLEM`): «No se pudo hacer la copia antes de importar: un adjunto falta o está dañado, o la bóveda no cabe en una copia. Exporta la bóveda a mano para ver el motivo. No se ha importado nada.»
- `imports/ImportTexts.kt:127` (`IMPORT_COPY_LOCKED`): «La bóveda se ha bloqueado antes de hacer la copia. No se ha importado nada.»
- `imports/ImportTexts.kt:128` (`IMPORT_COPY_FAILED`): «No se pudo hacer la copia antes de importar. No se ha importado nada.»
- `vault/AutoBackup.kt:98`: the file name the user sees in the folder, `seguridad-antes-de-importar-AAAAMMDD-HHMMSS.sgbak`.

Texts this review proposes (they only exist if the fix round adopts them):
- M-5: «No se pudo guardar la importación: la bóveda no ha cambiado. La copia de antes de importar está en la carpeta.»
- M-6: «La bóveda ha cambiado mientras se hacía la copia. No se ha importado nada.»

Pinned A6 texts to be reworded in the fix round (Ruling R15; not changed in this diff): `AUTO_BACKUP_REPLACED`, `PASSWORD_CHANGE_REPLACE_COPIES`, `AUTO_BACKUP_REPLACE_DROPPED`, `autoBackupUnlistedText` and the `SettingsScreen.kt:284` hint. The proposals are in `task-8-report.md:139-143`; `docs/privacidad.html` is in Task 12.

The forbidden-word list was checked against all the new texts: none appears.

### Assessment
**Task quality:** Needs fixes
**Reasoning:** The core is sound and well tested. The revision check sits in the write's critical section, the save is a single atomic write, the duplicate rules are linear and match the spec, and the A6 handling is consistent. The pre-encryption size bound can still let a crafted, heavily escaped import allocate about 130–200 MB before it is refused (I-1), and ten Minor items (data-loss edges in REPLACE and the legacy A6 path, text veracity, a misplaced KDoc) need the fix round.
