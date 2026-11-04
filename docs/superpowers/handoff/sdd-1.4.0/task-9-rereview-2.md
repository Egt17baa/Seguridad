### Finding Verdicts (ADDRESSED | NOT ADDRESSED, file:line)

- **Minor 1 (estimate fallback): ADDRESSED.** `PreviewEstimate.newTags` is nullable (`ImportSession.kt`). The catch in `ImportScreen.kt` (~:262-264) gives `PreviewEstimate(0L, null, 0)`. `estimated` stores null, and the preview shows no tag line (`s.newTags?.let`). Tests: `anEstimateWithoutTagsNamesNone` and the PlatformSources pin.
- **Minors 2 + 4 (count exactly true): ADDRESSED.** `mergeImport` (`MergePlan.kt`) keeps a `lost` set and returns `lost.count { it !in had }`.
  - **SKIP:** the loop `continue`s before `tagsFor`, so nothing is spent and nothing is counted.
  - **KEEP_BOTH** (and new items): `lost += importTags(folders) - tags.toSet()`, which is exactly the folder tags the cap refused. They are always fresh, so none are in `had`.
  - **REPLACE:** only when the cap changed the tags (`full.size != tags.size`), `lost += replaceWith(entry, full).tags - replaced.tags`. This is what the entry would have received minus what it received. An entry already at 10 tags takes no tag either way, so the difference is empty. A type `replaceWith` leaves alone also gives an empty difference.
  - Text and count agree. Tests: `foldersNotTagsCountsTheDistinctFoldersReallyLeftOut` (two spellings of one folder count once; the existing tag is kept; the result is 2), `aReplacedEntryThatCouldTakeNoTagLostNothing` (0 for the full entry, 1 for the roomy one) and `anImportCreatesAtMostAHundredNewTags` (still 19 900).
- **Minor 3: ADDRESSED.** `TagBudget.tagsFor` over the cap returns `full.filter { origin || in had || in created }`. Only new folder tags are refused. The `dropped` set is removed. Test: `tagsTheVaultHasAlreadyNeverSpendTheBudget` (`[origin, casa, uno]` is kept).
- **Minor 5: ADDRESSED.** The `importNewTagsText` KDoc now says the order given, not sorted.
- **Minor 6: ADDRESSED.** `IMPORT_REPLACE_NOTE` is wrapped with the same text. The `plainReportText` import is in order in `PkActions.kt`.

**Named checks**
- **One computation for preview and save: yes, unchanged.** The preview calls `previewEstimate`, which calls `mergeImport`. The save goes `VaultStore.applyImport` → `mergedEntries` → `mergeImport(...).entries`. The new `lost` logic lives inside `mergeImport`, so both use it. The cap (`budget.tagsFor`) and the file order are the same as before. The extra `replaceWith` runs only for a replaced item that lost tags, and it has no side effects.
- **Reworded text:** «N carpetas del archivo no pasan a etiqueta en sus entradas: una importación crea como mucho 100 etiquetas nuevas. Esas entradas llevan la etiqueta de origen y las etiquetas que ya existían.»
  - It is true for KEEP_BOTH: the capped tags are a prefix-order subset of the full ones, and they keep the origin and the existing tags.
  - It is true for REPLACE. A replaced entry is counted only if the full tags would have fitted. Since the capped tags are a subset in the same order, the origin and the existing tags fit too. It also keeps the entry's own tags.
  - For SKIP nothing is counted.
- **Evidence:** the report «Fix round 2» shows app tests 1683 with 0 failed, lint 17 (unchanged), jvm-check 1678 passed with 0 failed, and check-manifest OK. I did not re-run the suite.

### New Breakage in the Fix Diff (severity, file:line; list Minors too)

No Critical or Important.

- **Minor A.** `MergePlan.kt` `mergeImport`: a folder tag refused to entry A can be created later for entry B, if B has fewer new tags and the budget has room. It is then counted in `foldersNotTags`, which the old `dropped.count { it !in created }` excluded. «No pasa a etiqueta en sus entradas» stays true, because A did not get it. The text may sound as if the tag does not exist at all. It is a rare edge and the KDoc says «kept off an entry».
- **Minor B.** The count is per distinct tag. With a hostile file, A, B and C can each lose different tags, which is still the right number.

### Out-of-Scope Observations

- The dormant Password step remains dead code until Task 10 (already noted in round 1).

### Textos para el Abogado Digital (revisión final)

- `ImportTexts.kt` `importFoldersNotTagsText(n)`: «1 carpeta del archivo no pasa a etiqueta en sus entradas: una importación crea como mucho 100 etiquetas nuevas. Esas entradas llevan la etiqueta de origen y las etiquetas que ya existían.» and «N carpetas del archivo no pasan a etiqueta en sus entradas: una importación crea como mucho 100 etiquetas nuevas. Esas entradas llevan la etiqueta de origen y las etiquetas que ya existían.»
- `IMPORT_REPLACE_NOTE`: same text as before, only the source line was wrapped. No other text changed. The forbidden-word test still covers `importFoldersNotTagsText(1)` and `(19_900)`.

### Revisión experto-seguridad-cripto

- The cap still bounds what an import creates: ≤100 new folder tags (`TagBudget`), and keeping existing tags creates nothing. Nothing in the preview overclaims: with no estimate the tags are unknown and unnamed. No crypto or secret handling changed.

### Revisión Ingeniero Móvil Senior

- The estimate stays on Default; the extra `replaceWith` is only for capped replaced items. The nullable `newTags` flows through `estimated` and the UI without new state or recomposition cost.

### Verdict — **Fix round:** All findings addressed, no new Critical/Important breakage
