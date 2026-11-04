# Task 9 review: «Importar de otra app», removing the exported file, R9 (9ce8d15..3704ffe)

### Spec Compliance

✅ **Matches the brief and spec «Interfaz», with two Important defects to fix (I-1, I-2).**

- ✅ Entry ⋮ → «Importar de otra app» after «Importar copia», on both tabs (`VaultScreen.kt:203`); route `vault/import` (`AppNavHost.kt`, wired with `copyBeforeImport`, `lockAfterMs`, `awayInOtherApp`).
- ✅ Step 1: `OPEN_DOCUMENT` `*/*` (`ImportScreen.kt:458`). The document is opened once (`:236`, pinned count 1). `readAtMost(IMPORT_MAX_BYTES + 1)` runs on IO through `StopOnCancel` (≤64 KiB per read, `ensureActive` before each read). Over the limit is refused before decoding. `readAtMost` wipes its own buffer in `finally` (`core/Streams.kt:15-35`).
- ✅ Step 2: one attempt (R13). `takePassword` sets `passwordTried`, and a second `NeedsPassword` gives `IMPORT_ENCRYPTED_NOT_YET` (`ImportSession.kt:164`, `:186-192`). `pw` is zeroed and the `IMPORT_KDF_RUN` ticket released in `finally` (`ImportScreen.kt:292`).
- ✅ R11b: the only `OutOfMemoryError` catch is around `readImport` on Default (`ImportScreen.kt:287`).
- ✅ Plan: one `importSnapshot()` plus `planMerge(read, entries, revision)`. Overrides are keyed by `ImportedItem.index`, the default is SKIP, and the new tags count kept and replaced duplicates (`ImportSession.kt:400-410`).
- ✅ Step 4: `copyBeforeImport()`. When it gives null, the step is `ChooseCopyPlace`, then `CREATE_DOCUMENT` `seguridad-antes-de-importar-AAAAMMDD-HHMMSS.sgbak` and `exportBackup`. A cancel means no import; a failed or unrequested document is discarded (`ImportScreen.kt:311-366`). Both paths go through `ImportFlow` under `NonCancellable`. The `when` over `ImportOutcome` is exhaustive (`ImportSession.kt:292-322`).
- ✅ Step 5: `deleteDocument` acts on this run's URI only, only after `Imported`, once (`takeRemovable`, `ImportSession.kt:330`). `isDocumentUri` and `FLAG_SUPPORTS_DELETE` are checked first (`ImportScreen.kt:423-432`). On failure or no support, the text says «Bórralo a mano». «No, déjalo» is never asked again.
- ✅ Wipe: on any lock (`ImportScreen.kt:188`), on back (`onCleared`, `:382`), and by the guard (`watchAway`, `GuardEffects`, `usedFor(s.guard)`, `typed()`). The late result is dropped by `run` (`isRun` in every callback). There is no `rememberSaveable` (pinned).
- ✅ Decision (c): only plain `Text`. No `openUri`, `Linkify`, `LinkAnnotation`, `AnnotatedString` or `startActivity` in the screen or the session (pinned in `PlatformSourcesTest`).
- ✅ The trademark line is verbatim (`ImportTexts.kt:160`). Other apps' names appear only in `ImportTexts.kt` literals. No forbidden words: «no garantiza» is the only «garantiza», and there is no «seguro» or «limpio».
- ✅ R17 / LazyColumn: every guard of the ToolColumn pattern is present. FLAG_SECURE is app-wide; `NoCopy` (`:479`); `usedFor(s.guard)` (`:481`); `watchAway` (`:187`); `GuardEffects` (`:451`); wipe on lock, back and away. It also has `touchesVault` and `VaultIdleLock`, as the other vault screens do.
- ✅ `DocumentUriTest` guarded pickers 26 → 28, from the two new `rememberDocumentPicker` calls (`ImportScreen.kt:458`, `:461`).
- ⚠️ R9: complete in `ui/`, but five text builders in `vault/` still show a stored contact title raw (M-6).
- No copied third-party importer code: the screen and session are Clavero's own state machine, with no tell-tale structure or naming from Bitwarden, Aegis, 2FAS or KeePassXC.

### Strengths

- `ImportSession` is a pure, jvm-tested state machine. Every result carries its `run`, and `dropAll` moves the run, zeroes the bytes and drops every reference. This makes the late-result and TOCTOU story easy to verify. It has 34 state tests.
- The read is bounded, opened once, cancellable at each block, and wiped on cancel at both levels: the `box` in the VM and `GrowingBuffer.wipe()`.
- Confirm, remove and the password are single-shot through step guards (`startSaving`, `copyPlaceChosen`, `takeRemovable`, `takePassword`).
- The removal path is careful. It checks `isDocumentUri` and the flags, never throws, and only ever acts on the picked URI. A created document that is not used is always discarded.
- `readImport` has a catch-all (`Detect.kt:47`). So the concurrent zeroing of the bytes by a wipe while a reader runs on Default can only produce a dropped `Failed`, never a crash.
- R9 has a mutation-checked source scan over `ui/` and per-file pins. `plainReportText` caps length (1024), combining marks (3), Cc, Cf, tags and invisible characters.

### Issues

#### Critical
None.

#### Important

**I-1. Tapping an already-selected choice makes «Importar» stay disabled, with «Comprobando…» forever.**
- **Where:** `ImportSession.kt:228-243` (`chooseAll` and `choose` set `overBytes = null` unconditionally) and `ImportScreen.kt:191` (`snapshotFlow { EstimateInput(plan, default, overrides) }.collectLatest`).
- **What happens:** `FilterChip.onClick` fires on the selected chip too (`Choices`, `ImportScreen.kt` near `:668`). Two taps trigger it:
  - «Saltar» for all, when it is already the default and there are no overrides;
  - a duplicate's chip that already matches its choice.

  In both cases `default` and `overrides` get equal values, so no snapshot state changes. `snapshotFlow` neither re-runs nor emits (and `EstimateInput` is equal anyway). `overBytes` stays null, so `canConfirm` stays false and the spinner shows until the user changes a choice and changes it back. This is the most common tap on the preview: «Saltar» is preselected.
- **Fix:** in `chooseAll`, `if (choice == default && overrides.isEmpty()) return` before touching `overBytes`. In `choose`, compute `val next = …` and `if (next == overrides) return`. Add tests in `ImportStateTest`: `chooseAll(current)` keeps `overBytes` and `canConfirm`; `choose(index, sameChoice)` keeps them.

**I-2. A hostile export can make the import create tens of thousands of tags, and the UI composes all of them on the main thread (owner's decision (a), no ANR).**
- **Where:**
  - `ImportScreen.kt:556`: one `Text` holding every new tag;
  - `VaultScreen.kt:445`: after the import, a non-lazy `Row(horizontalScroll)` with one `FilterChip` per distinct tag, on every vault screen composition;
  - the source is `MergePlan.kt:110-118`: `importTags` gives up to 10 tags per item, and nothing limits how many distinct tags a whole import creates.
- **How:** a 16 MB Bitwarden JSON easily holds 20 000 items (`IMPORT_MAX_ITEMS`), each in its own folder (folders are not counted against that limit; `BitwardenJson.kt:56`). That gives about 20 000 new tags of up to 24 code points: a ~0.5 MB single `Text` in the preview, then 20 000 chips on every visit to the vault. The vault stays far under 6 MB, so `TooBig` does not stop it.
- **Severity:** Important. It is a concrete hostile-input DoS on the main thread, and it persists in the vault. The exact timing needs a device to measure, but the unbounded composition is plain in the code. It is also what R17's LazyColumn decision was meant to avoid.
- **Fix (controller to choose the cap; it touches Task 8 code and adds a text):**
  - (a) Cap the distinct new tags one import may create, e.g. `IMPORT_MAX_NEW_TAGS = 100`. Folder tags beyond it are not applied (the origin tag still is), and the preview says so with a new fixed text for the Abogado.
  - (b) In the preview, show at most N tags plus «y N más».
  - (c) Make the vault tag chip row a `LazyRow(items(allTags, key = { it }))`. This also protects vaults that already have many tags.
  - Add a hostile-input test for (a).

#### Minor (all to be fixed)

**M-1. The preview's «Etiquetas nuevas» can list tags the import will not create.**
- **Where:** `previewNewTags` (`ImportSession.kt:400-410`) adds every tag of a replaced duplicate. `replaceWith` keeps only `take(maxOf(MAX_TAGS, existing.tags.size))` (`MergePlan.kt:190`), so a vault entry that already has 10 tags gets none of the incoming ones. `IMPORT_REPLACE_NOTE` («suma … sus etiquetas») says the same without the cap.
- **Fix:** derive the new tags from `mergedEntries(...)`, which the estimate coroutine already builds on Default: the tags of the merged list minus `vaultTags`. Set them together with `overBytes` via `estimated(...)`. This also takes `previewNewTags` off the main thread, where it is recomputed on every recomposition today. Optionally add «hasta 10» to `IMPORT_REPLACE_NOTE` (Abogado list).

**M-2. After `ApplyResult.TooBig` from the save, «Importar» stays enabled with the same choices.**
- **Where:** `ImportSession.kt:309-312`.
- **What happens:** the estimate said it fits, the exact check said no, and `overBytes` is still 0. Each new tap makes another pre-import copy and gets the same refusal.
- **Fix:** in that branch, also set `overBytes = maxOf(1, r.overBytes)` (or a flag) so that the button stays disabled until a choice changes and a new estimate arrives.

**M-3. The two pickers are registered after the locked early `return`, so a picker result delivered after process death (or while locked) is lost.**
- **Where:** `ImportScreen.kt:453-461`.
- **What happens:** for `CREATE_DOCUMENT`, the provider has already created the document, so an empty `seguridad-antes-de-importar-….sgbak` stays in the user's folder. Its name suggests a valid pre-import copy. Android often kills the process while a cloud provider's dialog is on top.
- **Fix:**
  - Move both `rememberDocumentPicker(...)` calls above `if (state !is VaultState.Unlocked)`. `copyTo` then sees `plan == null` and discards the document.
  - In the `OpenDocument` callback, call `vm.pick(uri)` only when `store.state.value is VaultState.Unlocked`.
  - Pin it in `PlatformSourcesTest`.

**M-4. A guard wipe without a lock during `Saving` (split screen or floating window away longer than `max(lock, 60 s)`, `watchAway`) silently puts the visible screen back at step 1 while the import goes on.**
- **Where:** `ImportSession.forget()` (`ImportSession.kt:359`) during `ImportStep.Saving`.
- **What happens:** the save goes on under `NonCancellable` and its result is dropped. The user gets no hint that it happened and may import again; with «Conservar los dos», the entries go in twice. R17 accepted that a result is not shown after a lock or back, when the screen is gone. This is a third case, where the screen stays visible.
- **Fix:** when `forget()` runs with `step == Saving`, after `dropAll()` set a fixed, non-secret `error`, for example «Se ha borrado lo de esta pantalla mientras se guardaba: la importación puede haberse hecho. Mira la bóveda antes de volver a importar.» (new text, Abogado list). Add a session test.

**M-5. On the password step, «Elegir otro archivo» only goes back to step 1 (`vm::cancel`) and opens no picker.**
- **Where:** `ImportScreen.kt:508`.
- **What happens:** on the Preview, Done and Ended steps, the same label launches the picker. The label does not match what this button does.
- **Fix:** `onClick = { vm.cancel(); launchPick() }`, or relabel it `IMPORT_CANCEL`.

**M-6. R9 gaps: stored contact titles still go raw into texts the UI shows.**
- **Where:**
  - `vault/ContactsView.kt:142`, `:151` (`shownContactTitle(check.entry.title)` without `plainReportText`);
  - `vault/ContactsView.kt:159` (`«${check.entry.title}»`, without `shownContactTitle` either);
  - `vault/PkActions.kt:123` → `senderContactText` (`:135-138`);
  - `vault/PkActions.kt:167` → `fileSignatureUnverifiedText` (`:153`).
- **Why it matters:** contact names are checked by `hasForbiddenNameCharacter` when they are written. But R9 says «every entry», the task already sanitises the contact lists and the contact header, and a restored backup's contact titles are not re-checked.
- **Fix:** pass `plainReportText(shownContactTitle(title))` at those five sites (or inside the two text builders), and add them to the R9 pins.

**M-7. Veracity: two texts say «Clavero puede borrar ese archivo», which is false for providers that do not allow deletion.**
- **Where:** `IMPORT_NOT_PROTECTED` (`ImportTexts.kt:161-163`) and `IMPORT_REMOVE_EXPLAIN` (`ImportTexts.kt:312-315`). The second is shown before the attempt.
- **Fix:** «Clavero puede intentar borrar ese archivo (algunas apps no lo dejan), pero no esas copias.» and «Clavero puede intentar borrar ese mismo archivo; algunas apps no lo dejan. No borra…». These go to the Abogado list. Update the `ImportTextsTest` assertions if they match on the exact wording.

#### Informative (no change asked)

- A provider whose `read()` blocks forever keeps one IO thread blocked until the provider returns. The UI returns at once on «Cancelar» or a wipe, and `ensureActive` cannot interrupt a blocking read. This is inherent to SAF.
- `submitPassword` relies on `viewModelScope` being `Main.immediate`: the body enters `try` synchronously, so the ticket is always released. A `CoroutineStart.ATOMIC` launch would make that explicit.
- R13 makes the screen ask for an export password that this version cannot use. That is the controller's ruling. Until Task 10, the controller may prefer to show `IMPORT_ENCRYPTED_NOT_YET` at the first `NeedsPassword`, so that no Bitwarden or Aegis password is typed for nothing.

### Traces 1–8

1. **Lock.** `store.state.collect` → `vaultStateIs(false)` → `wipe` → `forget` → `stopAll` (cancels `job`, resets `refused`/`kdfBusy`) → `dropAll`: run++, bytes zeroed, plan, choices, password, source, removable and skipped dropped (`ImportSession.kt:355-395`). The screen pops (`ImportScreen.kt:453-456`) and `onCleared` wipes again.
   - *While reading:* the read stops at the next 64 KiB block, and `box` and `GrowingBuffer` are wiped.
   - *During the KDF:* `readImport` runs to its end on Default (CPU, cannot be cut). Then `finally` zeroes `pw` and releases the ticket, and `withContext` throws Cancellation, so `onRead` is never called. The bytes are zeroed under it, which is safe (`Detect.kt:47`).
   - *During the preview:* the plan goes, and the estimate collector sees `plan == null`.
   - *While confirming or after `Imported`:* the copy and `applyImport` finish under `NonCancellable`. `applyImport` returns `Stale` if the vault is already locked (`VaultStore.kt:669-676`). The result is dropped by `isRun` (R17).
2. **Back, rotation, process death.**
   - *Back:* NavBackStackEntry popped → `onCleared` → wipe.
   - *Rotation:* the ViewModel survives. State lives in `ComposeCells`, nothing in a Bundle, and `pickerError` is a non-secret `remember`. A rotation never relaunches a picker.
   - *Process death:* the ViewModel is lost and the vault is locked, so the screen pops. The save is atomic, so nothing is half-imported. The one leftover is M-3: an empty created document.
3. **Double taps.**
   - *Confirm:* `startSaving()` works once (`canConfirm` is false in `Saving`).
   - *«Quitar»:* `takeRemovable()` works once (`OFFERED` → `REMOVING`).
   - *«Abrir»:* the second tap finds the KDF ticket held and only sets `kdfBusy`; nothing is taken twice.
   - *Picker:* `rememberDocumentPicker` has no launch guard, so two pickers can stack, as everywhere in the app. For OPEN, the last pick wins and the earlier read is cancelled and zeroed. For CREATE, the second URI meets `copyPlaceChosen() == null` and is discarded. Either way, one action happens.
4. **Hostile sources.**
   - *200 GB file or an endless pipe:* `readAtMost(16 MB + 1)` stops there → `IMPORT_FILE_TOO_BIG`.
   - *A provider that throws:* caught → `IMPORT_READ_FAILED`.
   - *A provider that blocks:* «Cancelar» returns at once (see Informative).
   - *A hostile `displayName`:* `runCatching`, then `sanitizeReceivedName` (120 code points), then `plainReportText`.
   - The read is never on the main thread, so there is no ANR from reading. I-2 is the remaining main-thread cost.
5. **NeedsPassword.** Password step → one `takePassword` (`passwordTried`) → a second `NeedsPassword` → `IMPORT_ENCRYPTED_NOT_YET`, back to Pick, never asked again (`ImportSession.kt:186-192`). The CharArray is zeroed in `finally` (`ImportScreen.kt:292`). The Compose `String` field is emptied at once; a String cannot be zeroed, which is the project's accepted pattern.
6. **CREATE_DOCUMENT.**
   - *Cancelled:* `copyPlaceCancelled` → Preview with `IMPORT_COPY_CANCELLED`, nothing imported.
   - *Write failure* (null stream, exception, `exportBackup == false`): the document is discarded and `CopyResult.Failed` → `CopyFailed` → Preview, nothing imported.
   - *Success:* `CopyResult.Saved(name)` → `applyImport`.
   - *A URI that arrives outside `ChooseCopyPlace`:* discarded.
7. **deleteDocument.**
   - *URI identity:* `removable` is set only from `source`, which comes from `begin(uri)` of this run's OPEN result (already filtered by `isAcceptableDocument`). The created copy's URI is never `source`.
   - *Checks:* `isDocumentUri`, then `COLUMN_FLAGS & FLAG_SUPPORTS_DELETE` (unreadable flags → try, and a failure is reported).
   - *Failure:* exceptions are caught → `FAILED` («Bórralo a mano…»). An unsupported provider gives `UNSUPPORTED`.
   - It never deletes anything else. `discardDocumentNow` acts only on documents this screen created.
8. **Hostile titles.** `plainReportText` (`ImportModel.kt:106`):
   - U+202E and other Cf are dropped, NUL becomes a space, and combining marks are capped at 3 per base;
   - the text is cut at 1024 UTF-16 units at a code point boundary;
   - each pass is linear (one scan of the original string).

   Where it is used:
   - *Preview:* the duplicate title and subtitle (`ImportScreen.kt:647`, `:653`) and the skipped lines (`:637`; already sanitised by `ImportReadBuilder.skip`).
   - *Result:* the copy name (`:598`).
   - *Vault list after the import:* title and subtitle with `maxLines = 1`, and the tag chips.
   - *Entry header and fields:* the user and web fields are not cut (`value.length`) but are sanitised; copying stays verbatim.

   A 1 MB title is bounded on display. The vault list rows run `plainReportText` per visible row only (LazyColumn).

### R9 completeness check

Grep of `app/src/main/java/app/seguridad/ui` for `Text`/`BackScaffold` with `.title`, `.username`, `.url`, `issuer`, `account`, `tags` and `shownContactTitle`, and for `${…title…}` templates.

**Sanitised:**
- the vault and 2FA list (title, subtitle, tag chips);
- the entry header, the TOTP headers (×2), the user and web fields, the tags line, the delete dialog, the batch-add dialog;
- the OTP QR header;
- «Salud» rows;
- the contacts list, the contact detail header, the PkActions contact list;
- the whole import screen.

**Not covered:**
- `vault/ContactsView.kt:142`, `:151`, `:159`;
- `vault/PkActions.kt:123→135`, `:167→153` (M-6).

**Fine as they are:**
- `ContactDetailScreen.kt:150` (`current = entry.title`, an edit field: stored text by design);
- `QrInspectScreen.kt:294` (`QrView.title`, the inspector's own text);
- `LicensesScreen.kt:62` (fixed URLs);
- `VaultAttachments.kt:159` (already `safeFileName`).

**Navigation:** the `vault/import` route and the VaultScreen menu item (both tabs) are correct, and `DocumentUriTest` 26 → 28 matches the two new pickers.

### Revisión experto-seguridad-cripto

- **Size and DoS, decision (a):** the read is bounded, cancellable and on IO (`ImportScreen.kt:236`). The OOM guard covers only `readImport` (`:287`). The main-thread composition of an unbounded tag set is not bounded: I-2, Important.
- **TOCTOU, decision (b):**
  - one `openInputStream` (pinned);
  - results gated by `run` (`ImportSession.kt:141`, `:181`, `:208`, `:292`);
  - the plan from one snapshot, with the revision re-checked by `applyImport` (Stale → `IMPORT_VAULT_CHANGED`);
  - deletion only on this run's URI, once, after `Imported` (`ImportSession.kt:330`).
- **Secrets in memory:** the bytes are zeroed by every reader outcome and wipe (`ImportSession.kt:386-395`), on cancel (`ImportScreen.kt:241`) and when too big (`:250`). `pw` is zeroed in `finally` (`:292`). The plan is dropped on `Imported` (`ImportSession.kt:296-303`). Nothing is in saved state. No log.
- **Hostile content, decision (c):** plain `Text` only. No link, intent or HTML. `plainReportText` is used on every imported and stored field shown, except the M-6 contact texts.
- **No import without a copy:** both paths go through `ImportFlow`. A configured but unusable folder does not fall back (R17).
- **Third-party code:** none copied or adapted.
- **What it does not protect:** said on screen (`IMPORT_NOT_PROTECTED`, `IMPORT_REMOVE_EXPLAIN`). Their «puede borrar» overclaims (M-7).
- **Crypto:** none new. The KDF is serialised by `IMPORT_KDF_RUN`; until Task 10 no KDF runs.

### Revisión Ingeniero Móvil Senior

- **Lifecycle:** `ImportViewModel` is scoped to the NavBackStackEntry (`ImportScreen.kt:449`). `onCleared` wipes (`:382`). The guard is wired (`:187`, `:451`, `:481`) and `VaultIdleLock` and `touchesVault` are present. The pickers are registered after the early return: M-3.
- **Concurrency:**
  - `viewModelScope` (`Main.immediate`); IO for the read and the delete, Default for the readers, the plan and the estimate;
  - `NonCancellable` only for the commit and the removal;
  - `collectLatest` with an identity-compared plan, but its distinct-only semantics combined with the unconditional `overBytes = null` cause I-1;
  - cancelled estimates keep running to completion on Default; they are linear, so this is acceptable.
- **Compose performance:** a LazyColumn with stable keys `d<index>` and `s<index>` (unique across items and skipped, since they share one counter). `counts`, `newTags` and `canConfirm` are recomputed over the whole plan on the main thread at each recomposition (moving them into the estimate fixes M-1 too). There is unbounded single composition in the tag `Text` and the vault chip `Row`: I-2.
- **Accessibility:** step headings use `heading()` with polite live regions; errors and Busy are live.
- **MASVS:**
  - STORAGE: no saved state, no cache file; an empty file can be left behind (M-3).
  - PLATFORM: SAF only, `isAcceptableDocument` on results, no new component or permission.
  - CODE: bounded parsing (Tasks 6–8) and a bounded read; the UI bound is missing (I-2).

### Textos para el Abogado Digital (revisión final)

All new, in `app/src/main/java/app/seguridad/imports/ImportTexts.kt`. The vault menu item reuses `IMPORT_TITLE` (`VaultScreen.kt:203`).

**Start of the screen and picking the file**
- :152 `IMPORT_TITLE` «Importar de otra app»
- :153-155 `IMPORT_INTRO` «Lee un archivo que hayas exportado de otro gestor de contraseñas, de un navegador o de una app de 2FA, y añade sus entradas a la bóveda. Antes de cambiar nada te enseña lo que entra y te pide que lo confirmes.»
- :156-159 `IMPORT_FORMATS` «Lee las exportaciones en CSV de Chrome, Edge, Brave, Google Contraseñas, Firefox y Apple; el JSON de Bitwarden; las exportaciones sin cifrar de Aegis y 2FAS, y las listas de enlaces otpauth:// (también los de exportación de Google Authenticator). Las exportaciones cifradas todavía no.»
- :160 `IMPORT_TRADEMARKS` «Los nombres de otras apps son marcas de sus titulares. Clavero no tiene relación con ellos.»
- :161-163 `IMPORT_NOT_PROTECTED` «Clavero no protege el archivo exportado: está sin cifrar en tu móvil, y puede haber copias en la nube, en la papelera o en otras apps. Al terminar, Clavero puede borrar ese archivo, pero no esas copias.» (M-7)
- :164-167 `IMPORT_WIPE_NOTE` «Lo leído del archivo, la contraseña que escribas y la vista previa se borran si vuelves atrás, si se cierra la app, si la bóveda se bloquea o si estás fuera de esta pantalla más tiempo que el bloqueo de la bóveda (1 minuto como mínimo). Entonces hay que volver a empezar.»
- :168 «Elegir archivo»
- :169 «Elegir otro archivo»
- :170 «Leyendo el archivo…»
- :171 «Cancelar»
- :172 «No se pudo leer el archivo.»
- :175 «Archivo: $name»

**Password**
- :177 «Este archivo está cifrado. Escribe la contraseña con la que lo exportaste.»
- :178 «Contraseña del archivo»
- :179 «Abrir»
- :180 «Esa contraseña es demasiado larga: no se ha pegado.»
- :186-188 `IMPORT_ENCRYPTED_NOT_YET` «Esta versión de Clavero todavía no abre exportaciones cifradas. No se ha importado nada. Si exportas sin cifrar, borra ese archivo cuando termines.»
- :191 «Todavía se está comprobando la contraseña anterior. Inténtalo dentro de unos segundos.»

**Preview**
- :193 «Vista previa»
- :199-201 «No entra ninguna entrada nueva.»
- :199-201 «Entra 1 entrada nueva: …» / «Entran N entradas nuevas: …» (joined with «, » and « y »)
- :205-208 «1 acceso» / «N accesos»
- :205-208 «1 nota» / «N notas»
- :205-208 «1 código 2FA» / «N códigos 2FA»
- :212 «Se reemplaza 1 entrada de la bóveda.» / «Se reemplazan N entradas de la bóveda.»
- :215 «1 ya está en la bóveda» / «N ya están en la bóveda»
- :217 «Para todos:»
- :218 «Saltar»
- :219 «Conservar los dos»
- :220 «Reemplazar»
- :221-224 `IMPORT_REPLACE_NOTE` «“Reemplazar” toma del archivo el título, el usuario, la contraseña, la web y el 2FA que no estén vacíos, y suma sus notas y sus etiquetas a las de la bóveda. La contraseña anterior queda en el historial; los adjuntos y el favorito no cambian.» (the source uses «» around Reemplazar; see M-1 on the tag cap)
- :227 «1 elemento está repetido dentro del archivo: entra solo una vez.» / «N elementos están repetidos dentro del archivo: entra solo uno de cada.»
- :230 «1 tarjeta o identidad entra como nota.» / «N tarjetas o identidades entran como notas.»
- :232 «Etiquetas nuevas: #a, #b»
- :233 «No se crea ninguna etiqueta nueva.»
- :236 «No entran» (shown as «No entran (N)»)
- :237 «(sin título)»
- :240 «$title: $reason»

**Confirm and the copy before importing**
- :242 «Importar»
- :243-245 `IMPORT_CONFIRM_NOTE` «Antes de guardar, Clavero hace una copia cifrada de la bóveda tal como está, con tu contraseña maestra: en la carpeta de la copia automática o donde elijas. Sin esa copia no se importa nada.»
- :246 «Comprobando que la bóveda resultante cabe en una copia…»
- :247 «Con estas opciones no entra ni cambia nada.»
- :248 «Haciendo la copia de antes de importar y guardando…»
- :251-253 «No hay carpeta para la copia automática. Elige dónde guardar la copia de antes de importar: está cifrada con tu contraseña maestra.»
- :254 «Elegir dónde guardarla»
- :255 «Sin la copia de antes de importar no se importa: no se ha importado nada.»
- :256-257 «No se pudo escribir la copia antes de importar en el archivo elegido. ¿Queda espacio? No se ha importado nada.»
- :260-261 «La bóveda ha cambiado desde la vista previa, así que no se ha importado nada. Vuelve a elegir el archivo.»
- :267-277 «La bóveda resultante no cabría en una copia: {sobra 1 archivo | sobran N archivos | sobran unos X,Y MB | sobran unos N KB}. No se ha importado nada.»

**Result**
- :279 «Importación terminada»
- :281-293 «No ha entrado ninguna entrada nueva.» / «Ha entrado 1 entrada nueva.» / «Han entrado N entradas nuevas.»
- :281-293, optional second sentence: « Se ha actualizado 1 entrada de la bóveda.» / « Se han actualizado N entradas de la bóveda.»
- :301 «La copia de antes de importar es «$where», en la carpeta de la copia automática.»
- :303-304 «La copia de antes de importar es «$where», donde la guardaste. Clavero no la borra si cambias la contraseña maestra: bórrala tú cuando ya no la necesites.»
- :307 «No han entrado»
- :308 «No se ha terminado la importación»

**Removing the exported file**
- :311 «Quitar el archivo exportado»
- :312-315 `IMPORT_REMOVE_EXPLAIN` «El archivo que has importado sigue en tu móvil sin cifrar. Clavero puede borrar ese mismo archivo. No borra las copias que haya en la nube, en la papelera ni en otras apps, y no garantiza que el almacenamiento lo borre físicamente.» (M-7)
- :316 «No, déjalo»
- :317 «Borrando el archivo…»
- :318-319 «Archivo borrado. Mira si queda alguna copia en la nube, en la papelera o en otras apps.»
- :320-321 «No se pudo borrar el archivo. Bórralo a mano desde la app donde lo guardaste, y mira también la papelera y la nube.»
- :322-324 «La app donde está el archivo no deja que Clavero lo borre. Bórralo a mano desde ella, y mira también la papelera y la nube.»
- :325 «De acuerdo. Clavero no lo vuelve a preguntar: bórralo a mano cuando ya no lo necesites.»

**From earlier tasks, shown now for the first time:**
- `IMPORT_COPY_FAILED`, `IMPORT_COPY_LOCKED`, `IMPORT_COPY_VAULT_PROBLEM`, `IMPORT_COPY_VAULT_CHANGED`, `IMPORT_SAVE_FAILED`, `IMPORT_FILE_TOO_BIG`, `IMPORT_JSON_TOO_MANY_VALUES`;
- the readers' errors and the `SkipReason` texts.

**Proposed by this review** (subject to the fixes): M-4's new notice; I-2's «y N más» and the tag-cap note; M-7's rewording.

### Assessment

**Task quality:** Needs fixes

**Reasoning:** The screen meets the brief: bounded single read, one password attempt, a copy before every import, removal tied to this run, wipes on lock, back and away. But tapping an already-selected choice locks «Importar» (I-1), and a hostile export can create an unbounded number of tags that the UI composes on the main thread (I-2). Seven Minors also need fixing.
