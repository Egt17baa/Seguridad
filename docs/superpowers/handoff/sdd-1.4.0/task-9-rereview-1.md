### Finding Verdicts (ADDRESSED | NOT ADDRESSED, file:line)

- **I-1: ADDRESSED.** `ImportSession.kt` `chooseAll` returns early when `choice == default && overrides.isEmpty()`. `choose` computes `next` and returns early when `next == overrides`. Both now call `clearEstimate()` only when the state really changes, so `snapshotFlow` always emits a new estimate after an `overBytes = null`. Test: `ImportStateTest.tappingTheChoiceAlreadyMadeKeepsTheEstimate` (chooseAll same, choose same as default, choose same override twice).
- **I-2 / R18: ADDRESSED.**
  - Cap: `IMPORT_MAX_NEW_TAGS = 100` and `TagBudget` in `MergePlan.kt`. An item whose new folder tags would pass the cap keeps only its origin tag.
  - Preview: at most `IMPORT_TAGS_SHOWN = 20` tags plus «y N más.» (`ImportTexts.kt`, `importNewTagsText`). `importFoldersNotTagsText` says how many folders were not turned into tags. The screen shows it when `s.foldersNotTags > 0`.
  - Vault chips: `LazyRow(items(allTags, key = { it }))` in `VaultScreen.kt`.
  - Tests: `MergePlanTest.anImportCreatesAtMostAHundredNewTags` (20 000 distinct folders; origin tag plus 100; `foldersNotTags == 19 900`; item 99 keeps its folder, item 100 and the last keep only the origin) and `tagsTheVaultHasAlreadyNeverSpendTheBudget`. Also the text test with 25 tags, and the LazyRow pin.
- **M-1: ADDRESSED.** `previewEstimate` runs `mergeImport` on Default. `MergeResult.newTags` is the tags of the merged entries that the vault did not have, so a replaced entry already at 10 tags adds none. `previewNewTags` and the main-thread recompute are gone. `IMPORT_REPLACE_NOTE` now says «(hasta 10 por entrada)». Test: `MergePlanTest.theMergedNewTagsLeaveOutWhatAFullEntryCannotTake`.
- **M-2: ADDRESSED.** The `TooBig` branch sets `overBytes = maxOf(1L, r.overBytes)`. The test checks `canConfirm` is false and `startSaving()` is null, then true after a change and a new estimate.
- **M-3: ADDRESSED.** Both `rememberDocumentPicker` calls now sit above the locked return in `ImportScreen.kt`, after `vm` is created. The OpenDocument callback reads only when `store.state.value is VaultState.Unlocked`. `copyTo` discards the document when `plan == null` (`ImportScreen.kt:337-338`). Pinned by order in `PlatformSourcesTest`.
- **M-4: ADDRESSED.** `forget()` sets `error = IMPORT_WIPED_WHILE_SAVING` after `dropAll()` only when `step == Saving`. The late outcome is dropped by `isRun`, and the test shows the message stays after a late `Imported`. A wipe at the preview leaves no message.
- **M-5: ADDRESSED.** The password step button now runs `vm.cancel(); launchPick()`. It is pinned. The step itself is unreachable for now (R13a).
- **M-6: ADDRESSED.** `plainReportText` is applied at all five sites (`ContactsView.kt` three notices, `PkActions.kt` `senderLine` and `fileVerifyVerdict`). All are pinned.
- **M-7: ADDRESSED.** `IMPORT_NOT_PROTECTED` and `IMPORT_REMOVE_EXPLAIN` now say «puede intentar borrar … (algunas apps no lo dejan)». `ImportTextsTest` asserts the new wording and the absence of «Clavero puede borrar».
- **R13a: ADDRESSED.** See risk 2 below.

**Named risks, checked concretely**

1. **Tag cap, same in the preview and the save.**
   - The preview calls `mergeImport` (`ImportSession.kt:454`). The save calls `mergedEntries` (`VaultStore.kt:696`), which is `mergeImport(...).entries` (`MergePlan.kt:171`). The same `TagBudget`, the same file order and the same `had` set (existing entries' tags) are used. The placeholder id does not touch tags.
   - Only items that enter spend the budget. `SKIP` duplicates `continue` before `tagsFor`. `KEEP_BOTH` and `REPLACE` duplicates call `tagsFor`, so they count.
   - `had` (vault tags) never counts: `freshFolders` excludes `it in had`, and `created.add` runs only for `t !in had`.
   - The origin tag is never counted against the cap and is never dropped.
   - `planMerge` uses the same class for `newItems` only, which matches `mergeImport` with `SKIP`. The test asserts `p.newTags == r.newTags`.
2. **`IMPORT_READS_ENCRYPTED = false`: no password asked or held.**
   - The first `NeedsPassword` gives `dropAll()` (zeroes the bytes, `run++`, `password = ""`) and `IMPORT_ENCRYPTED_NOT_YET`. It never reaches `step = Password`.
   - `takePassword` needs the Password step, so it returns null. The Password UI, `submitPassword` and the KDF ticket are unreachable.
   - `untilEncryptedExportsAreReadNoPasswordIsAsked` checks the zeroed bytes and `assertFalse(IMPORT_READS_ENCRYPTED)`. The old password tests use `withPasswords()`, so the dormant path stays tested.
3. **Texts.** All are true; details and verbatim texts are below. The forbidden-word test covers `IMPORT_WIPED_WHILE_SAVING`, `importFoldersNotTagsText(1)`, `importFoldersNotTagsText(19_900)` and `importNewTagsText` with 30 tags. There is no «limpio», «seguro» or «garantiza» (apart from the existing «no garantiza»).
4. **M-4 notice.** It is a constant with no data in it, so it cannot leak anything. The save itself is not run again. The `run` moves, so the late outcome is dropped. A new import needs a new pick (`begin` clears the error and the plan). A second import started while the first still saves is refused by the revision check (`Stale`) or by the copy step. The guard rule is unchanged.

**Evidence of tests and output.** The fix report ("Fix round 1") shows the targeted jvm-check run, then app tests 1680 with 0 failed, 17 lint warnings (the same as round 0, none in this task's files), jvm-check 1675 passed with 0 failed, and check-manifest OK. `DocumentUriTest` stays at 28 pickers. I did not re-run the suite.

### New Breakage in the Fix Diff (severity, file:line; list Minors too)

No Critical or Important.

- **Minor 1.** `ImportScreen.kt` (the estimate's catch block): `PreviewEstimate(0L, emptySet(), 0)` on an unexpected exception makes the preview say «No se crea ninguna etiqueta nueva.» while the save may create tags. Before the fix the fallback only affected `overBytes`. Since the exception is not expected and the save is exact, this is low risk. A better fallback is `newTags = null` with `overBytes = 0`.
- **Minor 2.** `importFoldersNotTagsText` says «carpetas», but `foldersNotTags` counts distinct normalised folder tags. Folders that normalise to the same tag count once, which only makes the number lower. «Esas entradas llevan solo la etiqueta de origen» is exact for new entries. For a duplicate replaced under «Reemplazar» the entry keeps its own tags plus the origin tag. The text is still accurate in effect: no folder tag is added.
- **Minor 3.** `MergePlan.kt` `TagBudget.tagsFor`: when an item is over the cap, it also loses folder tags the vault already has (for example «casa»), not only the new ones. This matches the text «solo la etiqueta de origen», but it is stricter than needed. It is a design choice and it is documented in the KDoc.
- **Minor 4.** Under `REPLACE`, a vault entry at 10 tags takes no tags from the file, but the item still spent budget (the brief asks that duplicates count). `foldersNotTags` can then be above 0 in a case where the user lost nothing. This is the cost of the ruling.
- **Minor 5.** The KDoc of `importNewTagsText` says «the first 20 by name». The tags are in the order of the merged entries, not sorted. The code and the test agree with each other. Only the comment is off.
- **Minor 6.** Style: `IMPORT_REPLACE_NOTE` line length, and import order in `ContactsView.kt` and `PkActions.kt`. The vault package now imports `app.seguridad.imports.plainReportText`. The layering is acceptable, and the jvm-check suite passes with it.

### Out-of-Scope Observations

- The Password step, `submitPassword` and the KDF ticket are dead code until Task 10 flips `IMPORT_READS_ENCRYPTED`. Task 10 must check that the screen tests exercise the flipped constant.
- `PkActions.kt` and `ContactsView.kt` for a blank contact title: `senderLine` and the «tu contacto «…»» notice do not use `shownContactTitle` (it exists for blank titles). Pre-existing.
- `BiometricVault` threading (1.4.0, as in CLAUDE.md) is not touched.

### Textos para el Abogado Digital (revisión final)

- `ImportTexts.kt` `IMPORT_WIPED_WHILE_SAVING`: «Se ha borrado lo de esta pantalla mientras se guardaba: la importación puede haberse hecho. Mira la bóveda antes de volver a importar.»
- `importFoldersNotTagsText(n)`: «1 carpeta del archivo no pasa a etiqueta: una importación crea como mucho 100 etiquetas nuevas. Esas entradas llevan solo la etiqueta de origen.» and «N carpetas del archivo no pasan a etiqueta: una importación crea como mucho 100 etiquetas nuevas. Esas entradas llevan solo la etiqueta de origen.»
- `importNewTagsText(tags)`: «Etiquetas nuevas: #a, #b» up to 20, then «… y N más.»
- `IMPORT_NOT_PROTECTED`: «Clavero no protege el archivo exportado: está sin cifrar en tu móvil, y puede haber copias en la nube, en la papelera o en otras apps. Al terminar, Clavero puede intentar borrar ese archivo (algunas apps no lo dejan), pero no esas copias.»
- `IMPORT_REMOVE_EXPLAIN`: «El archivo que has importado sigue en tu móvil sin cifrar. Clavero puede intentar borrar ese mismo archivo; algunas apps no lo dejan. No borra las copias que haya en la nube, en la papelera ni en otras apps, y no garantiza que el almacenamiento lo borre físicamente.»
- `IMPORT_REPLACE_NOTE`: ««Reemplazar» toma del archivo el título, el usuario, la contraseña, la web y el 2FA que no estén vacíos, y suma sus notas y sus etiquetas (hasta 10 por entrada) a las de la bóveda. La contraseña anterior queda en el historial; los adjuntos y el favorito no cambian.»
- The text of `IMPORT_FORMATS` and `IMPORT_ENCRYPTED_NOT_YET` (unchanged) now matches the behaviour: «Las exportaciones cifradas todavía no.» / «Esta versión de Clavero todavía no abre exportaciones cifradas. …»
- The contact notices and signature lines are the same text; the stored name now goes through `plainReportText`.

### Revisión experto-seguridad-cripto

- **Hostile input (decision (a)):** the composition is bounded by ≤100 new tags per import (`MergePlan.kt` `TagBudget`), ≤20 named in the preview (`ImportTexts.kt` `importNewTagsText`) and lazy chips in the vault (`VaultScreen.kt`). Because the cap lives in `mergeImport`, the one place used by the preview and by `VaultStore.kt:696`, an export cannot get past it through a different path.
- **Secrets:** with the encrypted path off, no password is typed or held. The bytes are zeroed on the first `NeedsPassword` (`ImportSession.kt` `dropAll`). The M-4 notice is a constant. The new `newTags` and `foldersNotTags` are dropped by `dropAll`.
- **TOCTOU and the run guard:** the estimate, the outcome and the wipe all go through `isRun` or the equality checks in `estimated`. The `TooBig` fix does not open a loop.
- **Honesty of texts:** the «puede intentar borrar» wording is exact (M-7). The M-4 notice says «puede haberse hecho», not «se ha hecho».
- **Display of stored names (R9):** the five vault/ sites are covered. No crypto changes.

### Revisión Ingeniero Móvil Senior

- **Compose:** `LazyRow` with `key = { it }` for the tag chips (`VaultScreen.kt`); the tag set is computed once on Default instead of on every recomposition. The tag line shows only after the estimate arrives, so no flicker of a false «ninguna».
- **State:** `estimated` stores `overBytes`, `newTags` and `foldersNotTags` together, only for the choices shown; `clearEstimate()` resets all three. No new `rememberSaveable`.
- **Lifecycle:** the pickers are registered before the early return (`ImportScreen.kt`), so `ActivityResultRegistry` can deliver after re-creation. The callback guards on the unlocked state, and `copyTo` deletes an orphan document.
- **Concurrency:** `mergeImport` runs in `Dispatchers.Default` under `collectLatest`. The exception fallback is Minor 1.
- **Tests:** the new JVM tests cover the 20 000-folder case, the budget rules, the M-1 case, I-1, M-2, M-4 and R13a. The PlatformSources pins cover M-3, M-5, M-6 and the LazyRow.

### Verdict — **Fix round:** All findings addressed, no new Critical/Important breakage
