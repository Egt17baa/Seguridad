# Task 9 report: «Importar de otra app» screen and removing the exported file

**Status:** DONE_WITH_CONCERNS (the concerns are small and listed below; nothing is left open).
**Commit:** `3704ffe 1.4.0: pantalla Importar de otra app` (on `ccr-a1a6eabb-765f9g`, pushed; base `9ce8d15`).

## What was implemented

- **Entry and route.** In the vault, ⋮ → «Importar de otra app» right after «Importar copia» (`ui/screens/VaultScreen.kt`, new `onImportOther` parameter for both the Bóveda and Códigos tabs); route `vault/import` in `ui/AppNavHost.kt`, wired with `container.autoBackup::copyBeforeImport`, `lockAfterMs` and `container.awayInOtherApp`.
- **Pure state, `imports/ImportSession.kt`** (compiled and tested by jvm-check): `ImportStep` (Pick, Reading, Password, Preview, Saving, ChooseCopyPlace, Done, Ended), `ImportSession : GuardedSession`, `RemoveState`/`RemoveResult`, `IMPORT_KDF_RUN` (a process-wide `SingleRun`), `importCopyFileName`, and the preview helpers `previewCounts`, `previewNewTags` (adds the tags of duplicates kept or replaced), `previewOverBytes` (Task 8's `estimatedOverBytes` over `mergedEntries`). Every result is tied to a run; any wipe, lock, new pick or step change drops later results. The read bytes are zeroed as soon as a reader is done with them and by every wipe.
- **Screen, `ui/screens/ImportScreen.kt`** (`ImportViewModel` + `ImportScreen`), the five steps of the spec:
  1. `OPEN_DOCUMENT` with `*/*`, the «Importar copia» picker pattern (`keepOpenOnce`, `store.touch()`, flag back on failure). The document is opened once; `readAtMost(IMPORT_MAX_BYTES + 1)` on IO through `StopOnCancel` (checks the coroutine before each read of at most 64 KiB, so a cancel stops at the next block); over the limit → `IMPORT_FILE_TOO_BIG` without decoding.
  2. Password, if `NeedsPassword`: one attempt (R13), under `IMPORT_KDF_RUN` (released only when the reader returns), the CharArray zeroed in `finally`; a second `NeedsPassword` → `IMPORT_ENCRYPTED_NOT_YET`, back to step 1, never asked again.
  3. Preview: what enters by type, replaced count, repeated in file, cards/identities that become notes, new tags; the option for all (default «Saltar») and each duplicate's own choice keyed by `ImportedItem.index`; «No entran» with reasons; the estimate (computed on Default, conflated with `collectLatest`, plan compared by identity) must say it fits before «Importar» is enabled; nothing to do → disabled.
  4. Confirm: `copyBeforeImport()`; null → `ChooseCopyPlace` step, the user taps «Elegir dónde guardarla» → `CREATE_DOCUMENT` named `seguridad-antes-de-importar-AAAAMMDD-HHMMSS.sgbak` and `VaultStore.exportBackup` into it (a failed or unasked-for document is deleted with `discardDocumentNow`); cancelling the dialog → back to the preview with `IMPORT_COPY_CANCELLED`, nothing imported. Both paths go through `ImportFlow(copy, apply = store.applyImport(plan, default, overrides))` inside `withContext(NonCancellable)`. Every `ImportOutcome` is handled in an exhaustive `when` (`ImportSession.onOutcome`): Imported → result; CopyFailed → preview + its message; NotApplied(Stale) → `IMPORT_VAULT_CHANGED` and start again; NotApplied(TooBig) → `importTooBigText`; SaveFailed → `Ended(message)`.
  5. Result: added/replaced, where the copy is (`importCopyWhereText`, the hand-chosen one says Clavero does not delete it after a master change), «No han entrado», and «Quitar el archivo exportado» with `IMPORT_REMOVE_EXPLAIN` (no cloud, trash or other apps' copies; «no garantiza» physical deletion). It acts only on the URI picked in this run, only after `Imported`, only once (`takeRemovable`); `isDocumentUri` and `FLAG_SUPPORTS_DELETE` are checked first; unsupported or failed → «bórralo a mano». «No, déjalo» is never asked again.
- **Plans** come from one `store.importSnapshot()` (entries and revision together) and `planMerge(read, it.entries, it.revision)` — exactly what `planImport` does, but the snapshot's entries are also kept for the estimate and the vault's tags. Never paired by hand.
- **Lock and back:** `store.state.collect { session.vaultStateIs(it is VaultState.Unlocked) }` wipes on any lock; `onCleared` wipes on back; the guard (`watchAway`, `GuardEffects`, `usedFor(s.guard)`) applies the «PIN y códigos» rule; the screen pops itself when the vault is locked. A confirm already running finishes (NonCancellable) and its late result is dropped.
- **R11b:** `withContext(Dispatchers.Default) { try { readImport(bytes, pw) } catch (e: OutOfMemoryError) { null } }` is the only `OutOfMemoryError` in the code; null → `IMPORT_JSON_TOO_MANY_VALUES`, bytes zeroed and dropped.
- **Decision (c):** imported text is only plain `Text` through `plainReportText`; no link, intent, HTML or annotation (source-tested).
- **R9 (display sanitising, app-wide):** see the call-site list below.
- **Texts:** all new strings in `imports/ImportTexts.kt` (list for the Abogado below). `IMPORT_REPLACE_NOTE` was reworded during self-review to mention the password and the tags (veracity).

## R9 call sites (every file touched)

| File | Display call now through `plainReportText` |
|---|---|
| `ui/screens/VaultScreen.kt` | list title `Text(plainReportText(e.title)…)`; subtitle (user/URL or 2FA service · account) `Text(plainReportText(sub)…)`; tag chips `#${plainReportText(t)}` |
| `ui/screens/VaultEntryScreen.kt` | header `BackScaffold("… ${plainReportText(entry.title)}")`; 2FA header (both: code shown, code not supported); `Field("Usuario"/"Web", …, plain = true)` shows `plainReportText(value, value.length)` (copy stays verbatim); tags line; delete dialog «¿Borrar «…»?»; the Google Authenticator batch dialog's titles |
| `ui/screens/OtpQrScreen.kt` | service · account above the QR |
| `ui/screens/HealthScreen.kt` | entry title of each row |
| `ui/screens/ContactsScreen.kt`, `ContactDetailScreen.kt`, `PkActionsScreen.kt` | `plainReportText(shownContactTitle(…))` |
| `ui/screens/ImportScreen.kt` | file name, duplicate titles and subtitles, skipped titles, new tags, the copy's name |

Editing fields (`OutlinedTextField` values) keep the stored text. Pinned by `PlatformSourcesTest` «R9 every title, user, URL, service, account and tag shown goes through plainReportText» (positive pins per file plus a scan of `ui/` for a raw `Text(`/`BackScaffold(` of `e|entry|it|item|existing.title|username|url` or `Text(listOf(x.issuer`; mutation-checked).

## Tests and results

- New: `imports/ImportStateTest.kt` (34 tests: lock/back clear the bytes and the password, NeedsPassword → password step, one attempt then the «not yet» text, default SKIP, overrides by index, preview counts and tags, estimate gating and a single confirm, every outcome, removal once and only after an import, the copy place and its cancel, the texts' numbers and plurals, the copy's file name).
- `imports/ImportTextsTest.kt` +5 (forbidden words over every new text and samples of the built ones; trademark line verbatim and on the screen; what it does not protect; the removal text says nube, papelera, otras apps and «no garantiza», and «a mano» on failure; no product name in `ImportScreen.kt`).
- `PlatformSourcesTest.kt` +2 (the screen's guard, single read, OOM, KDF guard, snapshot plan, ImportFlow, pickers, delete, no links; R9).
- `core/DocumentUriTest.kt`: guarded pickers 26 → 28 (the two new ones, both through `rememberDocumentPicker`).

### TDD evidence

RED (before the implementation):
```
gradle -p jvm-check test --no-daemon --max-workers=1 --tests 'app.seguridad.imports.ImportStateTest'
e: …/ImportStateTest.kt:53:35 Unresolved reference 'ImportSession'.
e: …/ImportStateTest.kt:60:22 Unresolved reference 'ImportStep'.
BUILD FAILED
```
After `ImportSession.kt` and the texts, before the screen:
```
ImportStateTest: 34 PASSED
ImportTextsTest > theTrademarkLineIsVerbatimAndOnTheScreen FAILED
    java.lang.IllegalStateException: app/seguridad/ui/screens/ImportScreen.kt not found
(4 screen tests FAILED) — 43 tests completed, 4 failed
```
GREEN (full verification, once, before committing):
```
export ANDROID_HOME=/opt/android-sdk; ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1
BUILD SUCCESSFUL in 4m 17s — app tests 1674, failed 0
gradle -p jvm-check test --no-daemon --max-workers=1
BUILD SUCCESSFUL — 1669 PASSED, 0 FAILED
bash scripts/check-manifest.sh app/build/outputs/apk/debug/app-debug.apk
check-manifest: OK (app/build/outputs/apk/debug/app-debug.apk, debug)
```
Lint: 17 issues, all in the known files (14 in `gradle/libs.versions.toml`, `AndroidManifest.xml` ObsoleteSdkInt, mipmap DiscouragedApi). The 17th is a new `AndroidGradlePluginVersion` warning in `libs.versions.toml` (AGP 9.5.0 now available): date-driven, not from this task. No warning in the files of this task; the Kotlin compiler's only warnings are the two old `!!` in PhotoCleanScreen/RedactScreen.

## Files changed

- Added: `app/src/main/java/app/seguridad/imports/ImportSession.kt`, `app/src/main/java/app/seguridad/ui/screens/ImportScreen.kt`, `app/src/test/java/app/seguridad/imports/ImportStateTest.kt`.
- Modified: `imports/ImportTexts.kt` (screen texts appended; `import EntryType`), `ui/AppNavHost.kt`, `ui/screens/VaultScreen.kt`, `ui/screens/VaultEntryScreen.kt`, `ui/screens/OtpQrScreen.kt`, `ui/screens/HealthScreen.kt`, `ui/screens/ContactsScreen.kt`, `ui/screens/ContactDetailScreen.kt`, `ui/screens/PkActionsScreen.kt`; tests `PlatformSourcesTest.kt`, `imports/ImportTextsTest.kt`, `core/DocumentUriTest.kt`.
- Not changed: the behaviour of Task 6–8 code (readers, MergePlan, ImportFlow, VaultStore, AutoBackup).

## Self-review

- Spec «Interfaz» steps 1–5, ViewModel state, FLAG_SECURE (app-wide), TalkBack (each step has a heading or a polite live region), names only in ImportTexts.kt, trademark line, what is not protected: done.
- Brief Step 3 values: `OPEN_DOCUMENT */*`, `readAtMost(IMPORT_MAX_BYTES + 1)` on IO, KDF under `SingleRun`, cancel drops the result, `CREATE_DOCUMENT` name, `exportBackup`, cancel = no import, `deleteDocument` on the picked URI, «a mano» on failure, zeroing and drop on lock/back: done and pinned.
- Decisions 1–10: done; R9 also covers tags and the three contact title sites (cheap, the same risk).
- No new dependency, permission, exported component, host or format; check-manifest OK. No logs.

## Concerns (for the reviewer)

1. **Layout:** the screen is a `LazyColumn` inside `BackScaffold` + `NoCopy` with `usedFor(guard)` and `touchesVault(store)` (what `ToolColumn` does, plus the vault's idle count, since it is a vault screen) instead of `ToolColumn`, because a file can bring thousands of duplicates and skipped rows, each with its own choice; a scrolled `Column` would compose all of them.
2. **Wipe note:** the screen shows its own `IMPORT_WIPE_NOTE` (it names the file read and the preview, and «si la bóveda se bloquea», since any lock wipes here) instead of `TOOLS_AWAY_WIPE_NOTE`.
3. **After confirm:** the copy and the save run under `NonCancellable`; leaving or a lock does not cut them (spec «Si se bloquea después de confirmar, el guardado ya en marcha termina»), but their result is dropped with the session, so the user does not see the report or the removal offer in that case.
4. **Copy chosen by hand:** it is not in the automatic folder, so A6 does not list or delete it (Task 8's design); `importCopyWhereText(…, inAutoFolder = false)` says so. A configured but unusable automatic folder gives its `CopyFailed` text (choose it again in Ajustes); it does not fall back to `CREATE_DOCUMENT`.
5. **Planning:** `store.importSnapshot()` + `planMerge(read, it.entries, it.revision)` instead of `planImport(read)`, to keep the same snapshot's entries for the estimate and the vault's tags (one snapshot, never paired by hand; decision 5 allows `importSnapshot`).
6. **Lint count 17** (date-driven AGP warning), see above.

## Phone tests for the README (Task 12)

- Bóveda ⋮ → «Importar de otra app»: import a real CSV (Chrome/Firefox/Apple), Bitwarden JSON, Aegis and 2FAS plain exports; the preview counts by type, duplicates with «Saltar» by default, change one duplicate and the option for all.
- Import the same file twice with «Saltar»: the second adds nothing («Con estas opciones no entra ni cambia nada»).
- With an automatic-copy folder: the copy `seguridad-antes-de-importar-…sgbak` appears there. Without one: «Elegir dónde guardarla», save it; cancel the dialog → «Sin la copia… no se ha importado nada».
- An encrypted Aegis/2FAS/protected Bitwarden export: asks the password once, then «Esta versión de Clavero todavía no abre exportaciones cifradas», no loop.
- A file over 16 MB: refused at once; «Cancelar» while a big file is read from Drive stops it.
- Lock the vault (button, idle, leaving the app) at the password step and at the preview: the screen goes back and nothing is kept. Back from the preview: nothing kept. Rotate at every step: kept.
- «Quitar el archivo exportado» from Descargas (deleted) and from Drive (deleted, or «bórralo a mano»); «No, déjalo».
- TalkBack reads each step; a title with bidi or invisible characters shows plain in the list, the entry header, the 2FA QR screen, «Salud» and the import preview, while «Copiar» copies it as stored.
- Fingerprint still works after an import.

## Revisión experto-seguridad-cripto

- **Hostile file, size:** one read, at most 16 MB + 1 byte, on IO, stopped at the next 64 KiB block on cancel (`ImportScreen.kt:236`); over the limit refused before any decoding (`ImportScreen.kt:249-252`). Never `readBytes()`.
- **TOCTOU (decision b):** the document is opened once (`openInputStream` count 1, pinned); the bytes are the only input from then on; late results dropped by run (`ImportSession.kt:141`, `:186`, `:295`); the delete acts only on this run's URI, only after `Imported`, once (`ImportSession.kt:330`, `ImportScreen.kt:373`), after `isDocumentUri` and `FLAG_SUPPORTS_DELETE` (`ImportScreen.kt:423-432`).
- **Secrets in memory:** bytes zeroed after each reader and on every wipe (`ImportSession.kt:386`, `ImportScreen.kt:241`, `:250`); password CharArray zeroed in `finally` and the field emptied at once (`ImportScreen.kt:292`, `ImportSession.kt:164`); plan (passwords) dropped after the result (`ImportSession.kt:295`); nothing in `rememberSaveable`/Bundle (pinned); any lock wipes (`ImportScreen.kt:188`, `ImportSession.kt:355`).
- **KDF:** one at a time via `IMPORT_KDF_RUN`, released only when the reader returns (`ImportScreen.kt:260`, `:293`); cancel drops the result.
- **R11b:** `OutOfMemoryError` only around `readImport` (`ImportScreen.kt:287`), references dropped.
- **No copy, no import:** both confirm paths through `ImportFlow` (`ImportScreen.kt:323`, `:341`); a document not used is deleted (`:336`, `:364`); `applyImport` re-checks the revision (Stale → start again, `ImportSession.kt:314`).
- **Display (decision c, R9):** imported and stored text only as plain `Text` through `plainReportText` (call sites above); no link/intent/HTML/annotation in `ImportScreen.kt` or `ImportSession.kt` (pinned). Copies stay verbatim.
- **Third-party code:** nothing copied or adapted from any importer; the screen is Clavero's own code.
- **Not protected (said on screen):** the exported plaintext file and its copies in the cloud, trash and other apps (`IMPORT_NOT_PROTECTED`, `IMPORT_REMOVE_EXPLAIN`); deletion is not guaranteed physically.

## Revisión Ingeniero Móvil Senior

- **ViewModel and lifecycle:** state in `ImportSession` held by `ImportViewModel` (`ImportScreen.kt:173`); rotation keeps it, process death loses it (accepted rule); `onCleared` wipes (`:382`); guard wired (`:187`, `GuardEffects`, `usedFor(s.guard)` at `:481`); `VaultIdleLock` and `touchesVault` as the other vault screens; the screen pops when locked (`:454`).
- **Concurrency:** IO for the read and the delete, Default for the readers, the plan and the estimate; the estimate is conflated by `collectLatest` with identity comparison of the plan (`EstimateInput`); the confirm runs in `NonCancellable` and reports through the run check; no `GlobalScope`.
- **Pickers:** both through `rememberDocumentPicker` (DocumentUriTest 28), with the «Importar copia» `keepOpenOnce`/`touch` pattern (`ImportScreen.kt:467`, `:473`); a rotation never relaunches a picker (the copy place is a button, not an effect).
- **Performance:** `LazyColumn` with stable keys (`d<index>`, `s<index>`) for thousands of rows; no blocking work on the main thread.
- **Accessibility:** step headings with `heading()` and polite live regions; errors announced.
- **Manifest:** no new permission or component (`check-manifest.sh` OK); MASVS-STORAGE (no saved state, no cache file), MASVS-PLATFORM (no new IPC, only SAF), MASVS-CODE (bounded hostile input).

## Textos para el Abogado Digital (revisión final)

All in `app/src/main/java/app/seguridad/imports/ImportTexts.kt`:
- `IMPORT_TITLE` «Importar de otra app» (also the vault menu item).
- `IMPORT_INTRO`, `IMPORT_FORMATS` (names Chrome, Edge, Brave, Google Contraseñas, Firefox, Apple, Bitwarden, Aegis, 2FAS, Google Authenticator; says encrypted exports are not read yet), `IMPORT_TRADEMARKS` (verbatim line), `IMPORT_NOT_PROTECTED`, `IMPORT_WIPE_NOTE`.
- `IMPORT_PICK`, `IMPORT_PICK_OTHER`, `IMPORT_READING`, `IMPORT_CANCEL`, `IMPORT_READ_FAILED`, `importFileText`.
- `IMPORT_PASSWORD_INTRO`, `IMPORT_PASSWORD_LABEL`, `IMPORT_PASSWORD_OPEN`, `IMPORT_PASSWORD_TOO_LONG`, `IMPORT_ENCRYPTED_NOT_YET` (R13), `IMPORT_KDF_BUSY`.
- `IMPORT_PREVIEW_TITLE`, `previewEntersText`, `importReplacedText`, `importDuplicatesText`, `IMPORT_DUPLICATES_ALL`, `IMPORT_CHOICE_SKIP`, `IMPORT_CHOICE_KEEP_BOTH`, `IMPORT_CHOICE_REPLACE`, `IMPORT_REPLACE_NOTE`, `importRepeatedText`, `importBecameNotesText`, `importNewTagsText`, `IMPORT_NEW_TAGS_NONE`, `IMPORT_SKIPPED_TITLE`, `IMPORT_NO_TITLE`, `importSkippedLine`.
- `IMPORT_CONFIRM`, `IMPORT_CONFIRM_NOTE`, `IMPORT_ESTIMATING`, `IMPORT_NOTHING_TO_DO`, `IMPORT_SAVING`, `IMPORT_CHOOSE_COPY_PLACE`, `IMPORT_CHOOSE_COPY_PLACE_BUTTON`, `IMPORT_COPY_CANCELLED`, `IMPORT_COPY_DOCUMENT_FAILED`, `IMPORT_VAULT_CHANGED`, `importTooBigText`.
- `IMPORT_DONE_TITLE`, `importDoneText`, `importCopyWhereText` (both variants; the hand-chosen one says Clavero does not delete it after a master change), `IMPORT_SKIPPED_DONE_TITLE`, `IMPORT_ENDED_TITLE`.
- `IMPORT_REMOVE`, `IMPORT_REMOVE_EXPLAIN` (no cloud/trash/other apps; «no garantiza» physical deletion), `IMPORT_REMOVE_DECLINE`, `IMPORT_REMOVING`, `IMPORT_REMOVED`, `IMPORT_REMOVE_FAILED`, `IMPORT_REMOVE_UNSUPPORTED`, `IMPORT_REMOVE_DECLINED`.
- Shown for the first time (written in Task 8): `IMPORT_COPY_*`, `IMPORT_SAVE_FAILED`, `IMPORT_COPY_VAULT_CHANGED`, and the readers' errors and `SkipReason` texts.

## Fix round 1 (FIX_BASE 3704ffe → 893592c)

**Commit:** `893592c 1.4.0: importar, arreglos de la revisión de la tarea 9` (pushed).

| Finding | Change | Test |
|---|---|---|
| I-1 | `chooseAll` returns early when the choice is already the default with no overrides; `choose` returns early when the overrides would not change (`ImportSession.kt`, `clearEstimate()`). | `ImportStateTest.tappingTheChoiceAlreadyMadeKeepsTheEstimate` |
| I-2 / R18 | `IMPORT_MAX_NEW_TAGS = 100` and `TagBudget` in `MergePlan.kt`, used by `planMerge` and by the new `mergeImport` (which `mergedEntries`, and so `applyImport`, delegates to). Only items that enter spend the budget; an item whose new folder tags would pass it keeps only its origin tag; `MergeResult.foldersNotTags` counts the distinct folder tags left out. The preview names at most `IMPORT_TAGS_SHOWN = 20` tags, then «y N más» (`importNewTagsText`), and says `importFoldersNotTagsText(n)`. The vault's tag chips are a `LazyRow(items(allTags, key = { it }))` (`VaultScreen.kt`). | `MergePlanTest.anImportCreatesAtMostAHundredNewTags` (20 000 distinct folders), `tagsTheVaultHasAlreadyNeverSpendTheBudget`; `ImportStateTest.theTextsSayHowMuchAndHowMany` (20 + «y 5 más»); LazyRow pin in `PlatformSourcesTest` |
| M-1 | `previewEstimate` (replaces `previewOverBytes` and `previewNewTags`) returns `PreviewEstimate(overBytes, newTags, foldersNotTags)` from `mergeImport` on Default; the session keeps the tags only for the choices shown; `MergeResult.newTags` are the merged entries' tags the vault lacked, so a replaced entry at 10 tags adds none. `IMPORT_REPLACE_NOTE` now says «(hasta 10 por entrada)». | `MergePlanTest.theMergedNewTagsLeaveOutWhatAFullEntryCannotTake`; `ImportStateTest.theEstimateGivesTheNewTagsOfWhatEnters`, `theSessionShowsTheTagsOfTheEstimateForTheChoicesShown` |
| R13a | `IMPORT_READS_ENCRYPTED = false`; `ImportSession(readsEncrypted = …)`: until Task 10 the first `NeedsPassword` gives `IMPORT_ENCRYPTED_NOT_YET` and zeroes the bytes. The password step stays (tests run it with `readsEncrypted = true`); Task 10 flips the constant. | `ImportStateTest.untilEncryptedExportsAreReadNoPasswordIsAsked`; the password tests use `withPasswords()` |
| M-2 | `NotApplied(TooBig)` sets `overBytes = maxOf(1, r.overBytes)`: «Importar» stays off until a choice changes and a new estimate fits. | `ImportStateTest.aVaultTooBigSaysHowMuchIsLeftOver` |
| M-3 | Both `rememberDocumentPicker` calls are above the locked return; the `OpenDocument` callback reads only when `store.state.value is VaultState.Unlocked`; a created document with no plan is deleted by `copyTo`. | `PlatformSourcesTest` (picker order and the unlocked check) |
| M-4 | `forget()` during `Saving` leaves `IMPORT_WIPED_WHILE_SAVING` after the wipe. | `ImportStateTest.aWipeWhileSavingSaysTheImportMayHaveHappened` |
| M-5 | Password step «Elegir otro archivo» → `vm.cancel(); launchPick()`. | `PlatformSourcesTest` pin |
| M-6 | `plainReportText` at `vault/ContactsView.kt` (three notices) and `vault/PkActions.kt` (`senderContactText` and `fileSignatureUnverifiedText` call sites). | R9 pins in `PlatformSourcesTest` |
| M-7 | `IMPORT_NOT_PROTECTED` «Clavero puede intentar borrar ese archivo (algunas apps no lo dejan), pero no esas copias.»; `IMPORT_REMOVE_EXPLAIN` «Clavero puede intentar borrar ese mismo archivo; algunas apps no lo dejan. …». | `ImportTextsTest.removingTheExportedFileSaysWhatItDoesNotErase` |

**Commands and output:**
```
gradle -p jvm-check test --tests 'app.seguridad.imports.*' --tests 'app.seguridad.PlatformSourcesTest' --tests 'app.seguridad.core.DocumentUriTest' --tests 'app.seguridad.vault.*'
BUILD SUCCESSFUL
export ANDROID_HOME=/opt/android-sdk; ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1
BUILD SUCCESSFUL in 6m 38s — app tests 1680, failed 0; lint 17 (the same as round 0, none in this task's files)
gradle -p jvm-check test --no-daemon --max-workers=1
BUILD SUCCESSFUL — 1675 PASSED, 0 FAILED
bash scripts/check-manifest.sh app/build/outputs/apk/debug/app-debug.apk
check-manifest: OK (app/build/outputs/apk/debug/app-debug.apk, debug)
```
`DocumentUriTest` stays at 28 guarded pickers. The compiler's only new-looking warnings are the old `!!` in `MergePlanTest.kt:223-227` (Task 8).

**New or changed texts for the Abogado Digital:** `IMPORT_WIPED_WHILE_SAVING` «Se ha borrado lo de esta pantalla mientras se guardaba: la importación puede haberse hecho. Mira la bóveda antes de volver a importar.»; `importFoldersNotTagsText` «N carpetas del archivo no pasan a etiqueta: una importación crea como mucho 100 etiquetas nuevas. Esas entradas llevan solo la etiqueta de origen.» (and the singular); `importNewTagsText` «… y N más.»; `IMPORT_NOT_PROTECTED` and `IMPORT_REMOVE_EXPLAIN` (M-7 wording); `IMPORT_REPLACE_NOTE` «(hasta 10 por entrada)»; the contact notices and signature lines now show the title through `plainReportText` (same text).

**Revisión experto-seguridad-cripto (round 1):** the main-thread composition is bounded: ≤100 new tags per import (`MergePlan.kt`, `TagBudget`), ≤20 named in the preview, lazy tag chips in the vault (R18, decision (a)). No password is typed for an export this version cannot open (R13a). A created document is never left after process death (M-3). The texts no longer overclaim deletion (M-7). The contact texts built in `vault/` are sanitised for display (M-6).

**Revisión Ingeniero Móvil Senior (round 1):** the estimate's `snapshotFlow` now always gets a state change when the estimate is cleared (I-1). The tags are computed on Default with the estimate instead of on every recomposition (M-1). The pickers are registered unconditionally, so `ActivityResultRegistry` delivers results after re-creation (M-3). `LazyRow` with stable keys for the tag chips.

## Fix round 2 (FIX_BASE 893592c → 55b3ed4)

**Commit:** `55b3ed4 1.4.0: importar, segunda ronda de arreglos de la tarea 9` (pushed).

| Minor | Change | Test |
|---|---|---|
| 1 | `PreviewEstimate.newTags` is nullable; the estimate's catch gives `PreviewEstimate(0L, null, 0)`, so the preview names no tags and never says «No se crea ninguna etiqueta nueva» after an error. | `ImportStateTest.anEstimateWithoutTagsNamesNone`; `PlatformSourcesTest` pin |
| 2 + 4 | `MergeResult.foldersNotTags` now counts the distinct normalised folder tags that the cap really kept off an entry. For «Conservar los dos» that is the full tags minus the given ones. For «Reemplazar» it is `replaceWith` with the full tags minus `replaceWith` with the capped ones, so an entry already at 10 tags lost nothing and counts nothing. Tags the vault already had never count. `importFoldersNotTagsText`: «N carpetas del archivo no pasan a etiqueta en sus entradas: una importación crea como mucho 100 etiquetas nuevas. Esas entradas llevan la etiqueta de origen y las etiquetas que ya existían.» | `MergePlanTest.foldersNotTagsCountsTheDistinctFoldersReallyLeftOut`, `aReplacedEntryThatCouldTakeNoTagLostNothing`; `anImportCreatesAtMostAHundredNewTags` still gives 19 900 |
| 3 | `TagBudget.tagsFor`: over the cap, only new folder tags are refused. The item keeps the origin tag and the folder tags in the vault or already created. KDoc of `IMPORT_MAX_NEW_TAGS` and `TagBudget` updated. | `MergePlanTest.tagsTheVaultHasAlreadyNeverSpendTheBudget` |
| 5 | `importNewTagsText` KDoc: «in the order given (that of the merged entries, not sorted)». | — |
| 6 | `IMPORT_REPLACE_NOTE` source line wrapped (same text); `plainReportText` import placed in order in `PkActions.kt`. | — |

**Commands and output:**
```
gradle -p jvm-check test --tests 'app.seguridad.imports.*' --tests 'app.seguridad.PlatformSourcesTest'   → BUILD SUCCESSFUL
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1
BUILD SUCCESSFUL — app tests 1683, failed 0; lint 17 (unchanged, none in this task's files)
gradle -p jvm-check test --no-daemon --max-workers=1   → 1678 PASSED, 0 FAILED
bash scripts/check-manifest.sh app/build/outputs/apk/debug/app-debug.apk   → check-manifest: OK
```

**Texts for the Abogado Digital:** `importFoldersNotTagsText` reworded (above). No other text changed.

**Revisión experto-seguridad-cripto:** the tag cap still bounds what an import creates (≤100 new folder tags, `MergePlan.kt` `TagBudget`). Keeping the existing tags creates nothing new. The preview's counts and claims are exact (minors 1–4).

**Revisión Ingeniero Móvil Senior:** the estimate still runs on Default. The extra `replaceWith` runs only for a replaced item that lost tags to the cap. No new main-thread work.
