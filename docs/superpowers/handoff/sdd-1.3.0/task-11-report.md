# Task 11 report: A6, tras cambiar la maestra, y «Renovar identidad»

Commit: `a2b6a36` «1.3.0: tras cambiar la maestra, copias e identidad nuevas» (rama ccr-a1a6eabb-765f9g, sin push).
Status: DONE_WITH_CONCERNS (ver Concerns).

## What was implemented

- `vault/Contacts.kt`: `renewedIdentityEntry(old, now, random = secureRandom)`. Keeps `id`, `createdAt`, the card name (`username`) and everything else of the entry; new seed from `newIdentitySeed(random)` (the same call `newIdentityEntry` uses, so the Ed25519 and X-Wing keys are derived by the same `Identity(seed)` code as at creation, no new crypto); `updatedAt = now`; the temporary seed array is zeroed in `finally`. Refuses (CryptoFailure) an entry that is not an IDENTITY.
- `vault/AutoBackup.kt`: `suspend fun replaceOldCopies(): AutoBackupResult`.
  - **Order: new copy first, delete after.** It reuses the normal copy path (`attempt`: write `.tmp`, rename, verify) and then `rotate(..., replaceAll = true)` (kept = 1), which deletes only ids in `Settings.currentAutoBackupCopies` (the `copies()` list) plus the exact-name `.tmp` leftovers, never by name pattern. If the new copy fails (disk full, permission, folder gone) nothing is deleted, the list stays as it was, and the error is returned and recorded through `onResult` / `withAutoBackupResult`. So a failure never leaves zero copies. If the new copy is written but some old ones cannot be deleted, the result is `AUTO_BACKUP_OLD_NOT_DELETED` and those ids stay on the list (existing behaviour of `rotate`).
  - A `Mutex` makes the collector's copies and `replaceOldCopies` never overlap. `runOnce` was refactored into `attempt()` (returns the result it gave to `onResult`); behaviour of the normal path is unchanged (existing AutoBackup tests pass untouched).
  - **Locked vault.** `changeMaster` locks the vault when it finishes, so a copy cannot be made right after. In that case `replaceOldCopies` returns `AUTO_BACKUP_REPLACE_WAITS` (not sent to `onResult`), sets an in-memory `replacePending` and `pending`; the copy that the next unlock makes then replaces the old ones. No vault or no folder: `AUTO_BACKUP_FAILED`.
- `ui/screens/SettingsScreen.kt`: `MasterChangedDialog`: title «Contraseña maestra cambiada»; «Cambiar la contraseña no cambia las copias que ya existen.»; «Las copias .sgbak que hiciste antes se siguen abriendo con la contraseña anterior.»; only with automatic backups on, the button «Borrar las copias automáticas anteriores y hacer una nueva» (disabled while it works and after it went well, cannot dismiss the dialog while working; shows `AUTO_BACKUP_REPLACED`, the waiting text, or the existing error text); then «Si temes que alguien tenga la contraseña anterior y una copia, renueva tu identidad» and the button «Renovar identidad», which navigates to `contacts/me`.
- `ui/screens/VaultBackup.kt` and `ui/AppNavHost.kt`: the master change can be started from five screens (Ajustes, Bóveda menu, Códigos, Contactos, Inspector de QR) through `vaultActions`. To cover all of them with one dialog, `vaultActions` calls `LocalOnMasterChanged` (a composition local) after a successful `changeMaster`, and `AppNavHost` provides it and hosts `MasterChangedDialog` (state is `rememberSaveable`, so a rotation keeps the offer). The dialog itself lives in `SettingsScreen.kt` as the brief says.
- `ui/screens/IdentityScreen.kt`: «Renovar identidad» button (before «Borrar mi identidad»), opening a dialog with the verbatim warning «Tus contactos tendrán que añadir tu tarjeta nueva, y no podrás abrir lo que te enviaron a la anterior.». The confirm button is disabled while the work runs (plus a `working` guard in the click), the dialog cannot be dismissed meanwhile, and only after confirming does it run `store.upsert(renewedIdentityEntry(identity, now))`. The card/QR `produceState` key now includes `identity.updatedAt`; without it the screen kept showing the old card after a renewal (same id, same name).
- `tools/ToolTexts.kt`: new named consts next to `PASSWORD_CHANGE_COPIES`, all in `ALL_TOOL_TEXTS` (so `noForbiddenWords` and `everyTextConstantIsChecked` cover them, including the two `AUTO_BACKUP_*` texts that live in `AutoBackup.kt`).

## TDD evidence

RED (before implementing), `gradle -p jvm-check test --tests '*AutoBackupTest' --tests '*ContactsTest' --no-daemon --max-workers=1 --offline`:
```
e: .../AutoBackupTest.kt:784:24 Unresolved reference 'replaceOldCopies' on receiver of type 'AutoBackup'.
e: .../AutoBackupTest.kt:785:22 Unresolved reference 'AUTO_BACKUP_REPLACE_WAITS'.
e: .../ContactsTest.kt:85:23 Unresolved reference 'renewedIdentityEntry'.
> Task :compileTestKotlin FAILED
BUILD FAILED
```
GREEN (after): the same command, 63 tests, one failure caused by my test (the locked test did not `runCurrent()` after `lock()`, so the StateFlow collector never saw the Locked state; fixed in the test), then:
```
AutoBackupTest > replaceOldCopiesDeletesOnlyOwnIds PASSED
AutoBackupTest > replaceOldCopiesLeavesOldOnesWhenTheNewOneFails PASSED
AutoBackupTest > replaceOldCopiesThatCannotDeleteKeepsThemOnTheListAndSaysSo PASSED
AutoBackupTest > replaceOldCopiesWhileLockedWaitsForTheNextUnlock PASSED
AutoBackupTest > replaceOldCopiesWithNoFolderOrNoVaultWritesNothing PASSED
ContactsTest > renewedIdentityHasNewKeysSameId PASSED
ContactsTest > renewedIdentityTakesItsSeedFromTheRandomGivenAndDerivesTheSameWayAsACreatedOne PASSED
ContactsTest > renewingSomethingThatIsNotAnIdentityIsRefused PASSED
ContactsTest > oldMessageCannotBeOpenedAfterRenew PASSED   (ReceivedOutcome.Failed(NOT_FOR_ME), no throw)
BUILD SUCCESSFUL
```
`ToolTextsTest.afterChangingTheMasterPasswordTheTextsAreTheOnesOfTheDesign` added; the existing `noForbiddenWords` and `everyTextConstantIsChecked` cover the new texts.

## Verification (context.md), all with `--no-daemon --max-workers=1 --offline`, `ANDROID_HOME=/opt/android-sdk`

1. `gradle -p jvm-check test`: BUILD SUCCESSFUL, 864 tests, 0 failures, 0 errors, 0 skipped.
2. `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL, `testDebugUnitTest` 864 tests, 0 failures; lint 0 errors, 16 warnings (the single one I looked at is the AGP-version one; none from the changed files); `checkFragmentVersion` fragment 1.9.1 (>= 1.3.0).

## Files changed

`app/src/main/java/app/seguridad/vault/AutoBackup.kt`, `vault/Contacts.kt`, `tools/ToolTexts.kt`, `ui/AppNavHost.kt`, `ui/screens/IdentityScreen.kt`, `ui/screens/SettingsScreen.kt`, `ui/screens/VaultBackup.kt`; tests `AutoBackupTest.kt`, `ContactsTest.kt`, `ToolTextsTest.kt`. No manifest, dependency, permission, format or network change.

## Self-review

- No format change: `SG1:`/`.sgenc`/`seguridad-vault`/`.sgbak`/SGM1/SGK1 untouched; the renewed entry is the same `VaultEntry` with another `identitySeed` string (same Base64 encoding as before).
- Deletion is by id only: `rotate` filters `owned` (the Settings list) by ids present in the folder; foreign files named like copies (even `seguridad-auto-99991231-235959.sgbak`) are asserted to stay.
- The normal rotation path is unchanged: the `replaceAll` flag only changes `kept` to 1.
- No new logs; no texts built from exceptions or URIs.
- One thing in the diff looks large: `AppNavHost.kt` shows 190 changed lines because the `Scaffold` is now inside `CompositionLocalProvider` (re-indentation only).

## Concerns

1. **Vault is locked right after `changeMaster`** (design says «hace una copia nueva al momento»). A copy cannot be made until the vault is opened with the new password, so the button defers: the dialog says «Se hará en cuanto abras la bóveda con la contraseña nueva, si no cierras la app antes.» The deferral is in memory (`replacePending`): if the app process dies first, the old copies stay until normal rotation (keep N) removes them, and the offer does not come back. I did not add a Settings field to persist it (new persisted state, out of the brief). The controller may want a persistent flag or an extra button in Ajustes; say if so.
2. If the user leaves the app screen at the exact moment the master change finishes, `onMasterChanged()` may not run (the launching coroutine is cancelled after the `NonCancellable` block); the master is changed regardless, and the usual snackbar message is lost in the same way as before.
3. The dialog is shown by `AppNavHost`, not inside the Ajustes composable, so it also appears when the master is changed from the Bóveda menu and the other screens. This goes slightly beyond "SettingsScreen.kt" in the brief, deliberately: otherwise the fix would only cover one of five entry points.
4. Renewing keeps the entry's `username` (name on the card). Contacts must add the new card; the done snackbar says so.

## New UI texts (for the Abogado Digital review, Task 16)

- «Contraseña maestra cambiada» (dialog title)
- «Cambiar la contraseña no cambia las copias que ya existen.» (annex)
- «Las copias .sgbak que hiciste antes se siguen abriendo con la contraseña anterior.»
- «Borrar las copias automáticas anteriores y hacer una nueva» (button)
- «Haciendo la copia nueva…»
- «Se hizo una copia nueva y se borraron las anteriores.»
- «Se hará en cuanto abras la bóveda con la contraseña nueva, si no cierras la app antes.»
- «Si temes que alguien tenga la contraseña anterior y una copia, renueva tu identidad»
- «Renovar identidad» (button, and dialog title and confirm)
- «Tus contactos tendrán que añadir tu tarjeta nueva, y no podrás abrir lo que te enviaron a la anterior.» (verbatim)
- «Identidad renovada. Comparte tu tarjeta nueva con tus contactos.» (snackbar)
- «Entendido» (close), «Cancelar» (existing wording)
- Failure texts reused: AUTO_BACKUP_* and `NOT_SAVED`/`NOT_SAVED_STATE`/`VAULT_CLOSED` via `saveFailureMessage`.

## Revisión experto-seguridad-cripto

- **No crypto of our own:** `vault/Contacts.kt:82-90` only draws a seed with `newIdentitySeed(random)` (`crypto/pk/Identity.kt:96`, `randomBytes` over `secureRandom`, `crypto/Crypto.kt:106`); keys come from the existing `Identity(seed)` via `openIdentity()` (`Contacts.kt:41-52`). Test `ContactsTest.renewedIdentityTakesItsSeedFromTheRandomGivenAndDerivesTheSameWayAsACreatedOne` proves renewal equals creation for the same random.
- **Zeroing:** the temporary seed is filled with 0 in `Contacts.kt:88`; `openIdentity` already zeroes its decoded array (`Contacts.kt:48-50`). The Base64 `String` in the entry cannot be wiped (documented in that file as for all entries).
- **Old data does not open after renewal:** `ContactsTest.oldMessageCannotBeOpenedAfterRenew` (SGM1 for the old card gives `ReceivedOutcome.Failed(NOT_FOR_ME)`, no exception); the new Ed25519 differs.
- **Deletion by id only, never by name:** `AutoBackup.kt:329-330` (`others = owned.filter { it in ids && it != newest.id }`), test `AutoBackupTest.replaceOldCopiesDeletesOnlyOwnIds` (foreign `…99991231-235959.sgbak`, `…20200101-000000.sgbak`, `notas.txt` survive).
- **Never zero copies:** the new copy is written and renamed before `rotate` runs (`AutoBackup.kt:225-232`); `replaceOldCopiesLeavesOldOnesWhenTheNewOneFails` checks the old files and the Settings list are intact on a full disk, and the half-written file is gone (`fill`/`discard`, unchanged).
- **Partial delete failure:** `AutoBackup.kt:331-345` keeps undeleted old ids on the list; test `replaceOldCopiesThatCannotDeleteKeepsThemOnTheListAndSaysSo`.
- **Concurrency:** `AutoBackup.kt:152,178,208` one `Mutex` for the collector and the explicit call; test for the locked case also shows the request is one-shot (`AutoBackup.kt:227`, a later change keeps both copies).
- **Locked-vault honesty:** `AutoBackup.kt:182-186` never claims a copy was made; the text says it waits and that it is lost if the app closes first.
- **Strong warning and double-fire:** `IdentityScreen.kt:242-273` (confirmation first; `enabled = !working`, `if (working) return`, dismiss blocked while working; `FilterObscuredTouches()` in the dialog as the other destructive dialogs). `upsert` replaces by id (`VaultStore.kt:403-409`), so there is never a second identity.
- **Stale card on screen after renewal:** `IdentityScreen.kt:175` key includes `updatedAt`.
- **Texts/forbidden words:** `tools/ToolTexts.kt` new consts in `ALL_TOOL_TEXTS`, covered by `ToolTextsTest.noForbiddenWords` (no «seguro», «garantiza», etc.).
- **No new surface:** no permission, dependency, network, exported component or log added (greps over the diff: no `Log.`; manifest untouched). FLAG_SECURE is activity-wide (`MainActivity`), dialogs inherit.
- **Formats:** unchanged; existing format tests untouched and green (864/864).

---

# Fix round 1

Commit: `a2e753a` «1.3.0: tras cambiar la maestra, copias e identidad nuevas, revisión 1» (not pushed). This round supersedes concern 1 and the "in memory" parts of the first report.

## Changes

- **Important 1 (persisted request).**
  - `settings/Settings.kt:57`: `autoBackupReplacePending: Boolean = false` (defaulted, older `settings.json` decode). `AutoBackupResult` got two defaulted fields: `replaceRequested` (set the flag, touch nothing else) and `replaced` (this copy was a replacement).
  - `withAutoBackupResult` (`Settings.kt:82-92`): the flag is set by a `replaceRequested` result and cleared only by the result of a written copy with `replaced = true`, in the same update that records the copy list. A failure (no `writtenAt`) keeps it and records the error. `forgettingAutoBackupCopies` (new or imported vault) clears it (`Settings.kt:68`); choosing another folder and «Desactivar» clear it in `SettingsScreen.kt:165,243`.
  - `AutoBackup.kt`: in-memory `replacePending` removed; new constructor param `replacePending: () -> Boolean` (wired to the settings in `AppContainer.kt`). The collector also fires on unlock when it is true (`AutoBackup.kt:189`), `runOnce` reads it inside the lock (`:245`). While locked, `replaceOldCopies` hands `AutoBackupResult(..., replaceRequested = true)` to `onResult` (`waitForUnlock`, `:226-230`) and answers «Se hará en cuanto abras la bóveda.`; if it was not saved (flag still false) it answers `AUTO_BACKUP_FAILED` instead of promising.
  - Outcome told once: `AutoBackup.replaceOutcomes` (SharedFlow, emitted only for a replacement run by the collector after an unlock, `:247`); `AppNavHost` shows `ReplaceOutcomeDialog` («Copias automáticas» + the `AUTO_BACKUP_REPLACED` text or the existing error text). Errors also stay in Ajustes through `lastAutoBackupError`, as before.
- **Important 2 (own scope, rotation).**
  - `replaceOldCopies` is `scope.async { replaceNow() }.await()` (`AutoBackup.kt:206`), `scope` is now a property: cancelling the caller cannot cut the work between `rotate()` and `onResult`. `AutoBackup.replacing: StateFlow<Boolean>` says it is running.
  - `MasterChangedDialog` no longer keeps the state in plain `remember`: `requested` is `rememberSaveable`, `working = busy || replacing`, and after a rotation the status is derived from the saved `pending` / `lastAutoBackupError` of the settings; the button is offered again only after a failure.
- **Minor 1:** `AUTO_BACKUP_REPLACED` = «Se hizo una copia nueva y se borraron las copias automáticas anteriores de esta carpeta.»
- **Minor 2:** `onMasterChanged()` now runs inside the `NonCancellable` block, in a `finally` after `BiometricVault.disable` (`VaultBackup.kt:112`). `changeMaster` has only this call site (`grep changeMaster`: `VaultBackup.kt` and the store; the other screens go through `vaultActions`).
- **Minor 3:** `VaultV3Test.renovarLaIdentidadLaSustituyeYNoCuentaComoUnaSegunda` (placed in `VaultV3Test`, where the identity store tests live, not in `VaultStoreTest`): `upsert(renewedIdentityEntry(...))` is not refused by ONLY_ONE_IDENTITY, leaves exactly one identity with the same id and `createdAt`, new Ed25519 and X-Wing keys, and survives lock/unlock.
- **Minor 4:** `autoBackupOn = settings.autoBackupUri != null` is read from the collected settings in `AppNavHost` at every recomposition (the dialog takes it as a parameter, not a `remember`ed value), so the button disappears if the folder is turned off while the dialog is open, and it is not offered without a folder.

## Tests

New or changed:
- `AutoBackupTest.aReplacementAskedWhileLockedSurvivesARestartAndRunsAtTheNextUnlock` (real `SettingsStore` on a file; a second `SettingsStore`, `VaultStore` and `AutoBackup` read the disk and replace on unlock; the flag is cleared on disk; the outcome is emitted once).
- `AutoBackupTest.theReplacementStaysAskedWhileTheNewCopyFailsAndIsClearedOnlyAfterItWorks` (flag stays and old copies stay on a full disk, error recorded and told; cleared after the successful unlock).
- `AutoBackupTest.cancellingTheCallerDoesNotCutTheReplacement` (the caller is cancelled at the first byte written; the new copy is on the list, old ones gone, `replacing` back to false). Mutation check: with `replaceOldCopies` changed to run `replaceNow()` directly this test FAILS (`cancellingTheCallerDoesNotCutTheReplacement FAILED`, 37 tests, 1 failed); restored afterwards.
- `AutoBackupTest.replaceOldCopiesWhileLockedWaitsForTheNextUnlock` now also asserts the flag is set and then cleared.
- `SettingsTest.theReplacementRequestIsPersistedAndOlderFilesStillLoad`, `theReplacementRequestIsSetByTheWaitingResultAndClearedByTheCopyThatReplaced`, `newVaultClearsTheReplacementRequest`.
- `VaultV3Test.renovarLaIdentidadLaSustituyeYNoCuentaComoUnaSegunda`.

## Verification (`--no-daemon --max-workers=1 --offline`, `ANDROID_HOME=/opt/android-sdk`)

1. `gradle -p jvm-check test`: BUILD SUCCESSFUL, 871 tests, 0 failures, 0 errors, 0 skipped.
2. `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL, `testDebugUnitTest` 871 tests, 0 failures; lint 0 errors, 16 warnings (same as before).

## Texts (replaces the list of new UI texts for the Abogado Digital, Task 16)

All are in `ALL_TOOL_TEXTS`, so `ToolTextsTest.noForbiddenWords` passed over each (no «seguro», «garantiza», «imposible», «limpio», «de verdad», etc.).
- «Contraseña maestra cambiada» (dialog title)
- «Cambiar la contraseña no cambia las copias que ya existen.» (annex)
- «Las copias .sgbak que hiciste antes se siguen abriendo con la contraseña anterior.»
- «Borrar las copias automáticas anteriores y hacer una nueva» (button)
- «Haciendo la copia nueva…»
- «Se hizo una copia nueva y se borraron las copias automáticas anteriores de esta carpeta.» (changed)
- «Se hará en cuanto abras la bóveda.» (changed; no longer depends on the user)
- «Copias automáticas» (title of the one-time outcome dialog, new)
- «Si temes que alguien tenga la contraseña anterior y una copia, renueva tu identidad»
- «Renovar identidad» (button, dialog title and confirm)
- «Tus contactos tendrán que añadir tu tarjeta nueva, y no podrás abrir lo que te enviaron a la anterior.» (verbatim)
- «Identidad renovada. Comparte tu tarjeta nueva con tus contactos.» (snackbar)
- «Entendido», «Cancelar»
- Reused: `AUTO_BACKUP_*` error texts and `saveFailureMessage` texts.

## Concerns

- If the replacement is asked for while locked and the user then turns the copies off or chooses another folder, the request is dropped on purpose (`SettingsScreen.kt:165,243`).
- While the flag is set, every normal debounced copy after an unlock also tries the replacement, and a failing folder shows the outcome dialog each time until it works.

## Revisión experto-seguridad-cripto (round 1)

- **Request cannot be lost or forged silently:** the flag lives in the saved settings (`Settings.kt:57`) and is only set by `waitForUnlock` (`AutoBackup.kt:226-230`), which refuses to promise when it was not saved; only a written replacement clears it (`Settings.kt:90,92`). Tests: `aReplacementAskedWhileLockedSurvivesARestartAndRunsAtTheNextUnlock`, `theReplacementStaysAskedWhileTheNewCopyFailsAndIsClearedOnlyAfterItWorks`.
- **Order preserved: new copy first, delete after:** `attempt` writes and renames before `rotate(..., replaceAll)` (`AutoBackup.kt:275` after `copyTo`); on failure nothing is deleted and the request stays (test above, old files asserted intact).
- **Deletion still by id from the recorded list only:** unchanged `rotate` (`AutoBackup.kt` `others = owned.filter { it in ids ... }`), `replaceOldCopiesDeletesOnlyOwnIds` still green.
- **No half-done state on UI cancellation:** `AutoBackup.kt:206` (own scope) plus `Mutex`; `cancellingTheCallerDoesNotCutTheReplacement` fails without the `async`.
- **Honest claims:** `AUTO_BACKUP_REPLACED` now names what was deleted (automatic copies listed for this folder); the waiting text carries no blame; old `.sgbak` made by hand are still said to open with the old password (`PASSWORD_CHANGE_OLD_BACKUPS`).
- **Identity renewal at store level:** `VaultV3Test.renovarLaIdentidadLaSustituyeYNoCuentaComoUnaSegunda` (same id, one identity, new Ed25519 and X-Wing, persisted); seed still zeroed (`Contacts.kt:88`).
- **No new surface:** no permission, dependency, network, log, exported component or format change; the settings field is defaulted and optional on read.

---

# Fix round 2

Commit: `126e444` «1.3.0: tras cambiar la maestra, copias e identidad nuevas, revisión 2» (not pushed). Skill `ingeniero-movil-senior:desarrollo-movil-senior` loaded before the fixes.

## Changes

1. **Dialog never claims success without a recorded replacement.** The dialog keeps what it needs across a rotation in `rememberSaveable` (`SettingsScreen.kt:298-300`: `requestedAt`, `answered`, `answerError`). The status comes from the pure `replaceStatus(...)` (`AutoBackup.kt`, bottom, `ReplaceStatus`): «Done» only from an answer with no error, or from a copy written after the request with no error and nothing pending; an answer of `AUTO_BACKUP_FAILED` stays a failure after a rotation; an answer that was lost and cannot be told is `Unknown` (nothing shown, button offered again). Test `replaceStatusNeverClaimsDoneWithoutARecordedReplacement`.
2. **No outcome modal on every copy.** `runOnce` tells an outcome only when it differs from the last one told (`toldOutcome`, `AutoBackup.kt:187,260`); a new request resets it (`:238`). A folder that keeps failing opens the dialog once; a later success (even from a debounced copy) is told once. Test `aFolderThatKeepsFailingDoesNotOpenTheOutcomeAgainAndTheSuccessIsToldOnce`; mutation check: with the comparison forced to `true` that test FAILS (40 tests, 1 failed), restored.
3. **Same folder keeps the request; drops are told.** New pure `Settings.withFolderChosen` / `withAutoBackupTurnedOff` (`Settings.kt:74,83`) clear the flag only when `chosen != previous` or when turned off. `SettingsScreen.kt:174,183,255-259` append «No se borraron las copias automáticas anteriores: siguen en la carpeta anterior.» to the snackbar when a pending request was dropped. Test `theSameFolderChosenAgainKeepsTheRequestAndAnotherOrOffDropsIt`.
4. **Outcome survives re-creation.** `replaceOutcomes` (SharedFlow, no replay) replaced by `replaceOutcome: StateFlow<AutoBackupResult?>` plus `replaceOutcomeShown()` (`AutoBackup.kt:173-184`): it lives in `AutoBackup` (the app container), `AppNavHost.kt:121-124` collects it and clears it when the user closes the dialog, so it never comes back after being dismissed. Tests `anOutcomeNotShownYetSurvivesUntilItIsAcknowledged` and the one in 2; the restart and the failure tests were adapted to the StateFlow.
5. **`BiometricVault.disable` failure no longer says the password did not change.** `VaultBackup.kt:110-123`: the disable is in its own try; the A6 dialog (`onMasterChanged()`) always runs once the master changed, and a disable failure answers the new text `PASSWORD_CHANGED_FINGERPRINT_FAILED` instead of «No se pudo cambiar la contraseña maestra».

Accepted by the controller: if some old copies cannot be deleted, the pending flag is cleared on purpose (the user is told once with `AUTO_BACKUP_OLD_NOT_DELETED`, and the leftovers rotate out).

## Verification (`--no-daemon --max-workers=1 --offline`, `ANDROID_HOME=/opt/android-sdk`)

1. `gradle -p jvm-check test`: BUILD SUCCESSFUL, 875 tests, 0 failures, 0 errors, 0 skipped.
2. `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL, `testDebugUnitTest` 875 tests, 0 failures; lint 0 errors, 16 warnings (unchanged).

## Revisión experto-seguridad-cripto (round 2)

- No claim of protection that did not happen: success text only from a recorded replacement (`AutoBackup.kt` `replaceStatus`; `SettingsScreen.kt:303`). A failed save of the request is shown as failure even after a rotation (`answerError` saved).
- The request is not lost by an accidental action: the same folder chosen again keeps it (`Settings.kt:74-80`); a drop is announced (`SettingsScreen.kt:183,259`). Deletion is still by id from the recorded list only, new copy first (unchanged `rotate`/`attempt`).
- Honest error after the master changed: `VaultBackup.kt:110-123` never reports a failed change after `changeMaster` succeeded; the stale fingerprint key wraps the old data key, which `changeMaster` already replaced, so it cannot open the vault (the text tells the user to re-enable it).
- No new surface: no permission, dependency, network, log, exported component or format change. FLAG_SECURE is activity-wide.

## Revisión Ingeniero Móvil Senior

- **Lifecycle / rotation:** nothing that must survive lives in a composable's plain `remember`: the work and its state live in `AutoBackup` (app container scope, `AutoBackup.kt:206` `scope.async`, `replacing` StateFlow `:172`), the one-time outcome in `replaceOutcome` (`:179`), the dialog's own memory in `rememberSaveable` (`SettingsScreen.kt:298-300`) and `rememberSaveable` for `masterChanged` (`AppNavHost.kt`). `busy` stays in `remember` on purpose: after a rotation `replacing` says whether the call still runs.
- **Process death:** the request is in the saved settings (`Settings.kt:57`, defaulted so old files load) and the collector reads it on unlock (`AutoBackup.kt:189`); test with a fresh `SettingsStore`/`VaultStore`/`AutoBackup` (`aReplacementAskedWhileLockedSurvivesARestartAndRunsAtTheNextUnlock`). The unshown outcome is lost on process death by design; an error is still in Ajustes (`lastAutoBackupError`) and a success is visible as the last copy.
- **State hoisting / testability:** the status logic is a pure function (`replaceStatus`), the settings changes are pure (`withFolderChosen`, `withAutoBackupTurnedOff`, `withAutoBackupResult`), all JVM-tested; the composables only render.
- **Reactive UI:** `AppNavHost` reads the settings and `replacing`/`replaceOutcome` with `collectAsStateWithLifecycle`; the folder switch hides the button at the next recomposition.
- **Cancellation:** work not tied to the UI scope; the UI call only awaits. The master change finishes inside `NonCancellable`, including `onMasterChanged()` (`VaultBackup.kt:112-117`).
- **Not done / limits:** no instrumented rotation test (the module has none for Compose); covered by the pure status and StateFlow tests.

## Textos para el Abogado Digital

Texts new or changed in this task (all in `ALL_TOOL_TEXTS`; `ToolTextsTest.noForbiddenWords` passes over each). Constants in `app/src/main/java/app/seguridad/tools/ToolTexts.kt` unless said:

| Const | Text | Line |
|---|---|---|
| `PASSWORD_CHANGED_TITLE` | «Contraseña maestra cambiada» | 430 |
| `PASSWORD_CHANGE_COPIES` (annex) | «Cambiar la contraseña no cambia las copias que ya existen.» | 427 |
| `PASSWORD_CHANGE_OLD_BACKUPS` | «Las copias .sgbak que hiciste antes se siguen abriendo con la contraseña anterior.» | 431 |
| `PASSWORD_CHANGE_REPLACE_COPIES` | «Borrar las copias automáticas anteriores y hacer una nueva» | 432 |
| `PASSWORD_CHANGE_RENEW_HINT` | «Si temes que alguien tenga la contraseña anterior y una copia, renueva tu identidad» | 433 |
| `PASSWORD_CHANGE_WORKING` | «Haciendo la copia nueva…» | 434 |
| `PASSWORD_CHANGE_CLOSE` | «Entendido» | 435 |
| `AUTO_BACKUP_OUTCOME_TITLE` | «Copias automáticas» | 436 |
| `AUTO_BACKUP_REPLACE_DROPPED` (new, round 2) | «No se borraron las copias automáticas anteriores: siguen en la carpeta anterior.» | 437 |
| `PASSWORD_CHANGED_FINGERPRINT_FAILED` (new, round 2) | «Contraseña maestra cambiada. No se pudo apagar la huella: abre la bóveda con la contraseña nueva y vuelve a activarla.» | 438 |
| `RENEW_IDENTITY` | «Renovar identidad» | 442 |
| `RENEW_IDENTITY_WARNING` | «Tus contactos tendrán que añadir tu tarjeta nueva, y no podrás abrir lo que te enviaron a la anterior.» | 443 |
| `RENEW_IDENTITY_DONE` | «Identidad renovada. Comparte tu tarjeta nueva con tus contactos.» | 444 |
| `AUTO_BACKUP_REPLACED` (`vault/AutoBackup.kt`) | «Se hizo una copia nueva y se borraron las copias automáticas anteriores de esta carpeta.» | 95 |
| `AUTO_BACKUP_REPLACE_WAITS` (`vault/AutoBackup.kt`) | «Se hará en cuanto abras la bóveda.» | 96 |

Reused, not new: the `AUTO_BACKUP_*` error texts, «Cancelar», and `saveFailureMessage` texts.
