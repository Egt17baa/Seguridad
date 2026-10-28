# Task 9 report: StrongBox para la huella, con respaldo (R1 y R2)

**Status:** DONE_WITH_CONCERNS (the code is done and verified; the concerns are below and need the owner's phone).
**Commit:** `20e2885` 1.3.0: StrongBox para la clave de la huella, con respaldo a la zona segura (not pushed).

## What was implemented

- **`tools/KeyPlacement.kt` (new, pure, JVM-tested):**
  - `KeyLevel { STRONGBOX, TEE, SOFTWARE, UNKNOWN }`.
  - `firstAttempt(hasStrongBox, sdk, strongBoxFailedBefore)` gives `STRONGBOX` only if `hasStrongBox && sdk >= 28 && !failed`; otherwise `TEE`.
  - `nextAfterFailure(tried, cancelled)`: a StrongBox failure that was not a cancel gives `TEE`. Anything else gives `null`.
  - `shouldOfferStrongBoxHint(...)` = `hasStrongBox && current == TEE && !failed && !shown`.
  - `keyLevelLabel(level)` uses the annex texts.
  - `legacyKeyLevel(insideSecureHardware, createdInStrongBox)` was added; see the decisions below.
- **`android/BiometricVault.kt`:**
  - **`createKey(strongBox)`:** the same spec as 1.2.2 (AES-256, GCM, NoPadding, `setUserAuthenticationRequired(true)`, `setInvalidatedByBiometricEnrollment(true)`, and `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` on API 30+). The only addition is `setIsStrongBoxBacked(true)` when `strongBox` is true and the SDK is 28 or later. There is no `setUnlockedDeviceRequired`; it appears only in the comment that explains why.
  - **`enable`:**
    - The first attempt comes from `firstAttempt(hasStrongBox(), SDK_INT, strongBoxFailed())`.
    - When a fallback is possible, a `spare` copy of the data key is made before the first attempt.
    - Any old wrapped key is dropped first with `disable()`.
    - `attempt()` runs createKey → `Cipher.init` → prompt → `doFinal`/store.
  - **Failure at any step:** a prompt error that is not a cancel, a null CryptoObject, a `doFinal` failure or a createKey/init exception all go to `fail()`, which:
    1. zeroes this attempt's key and deletes the Keystore key;
    2. asks `nextAfterFailure`;
    3. if the answer is `TEE`, persists `strongbox_failed = true` and runs the same cycle with `spare`, which is then zeroed on its own exits;
    4. otherwise zeroes `spare` and reports the message.
  - **User cancel** (`ERROR_NEGATIVE_BUTTON` / `ERROR_USER_CANCELED`): zero both copies, delete the key, no retry and no message, as before.
  - **Retry after a prompt:** it is posted 600 ms later on the main looper. In biometric 1.1.0 the client callback is posted before the removal transaction of `BiometricFragment` (checked with javap: `sendErrorAndDismiss` → `sendErrorToClient` (executor) → `dismiss()` → `remove().commitAllowingStateLoss()`). A prompt started from inside the callback would reuse the fragment that is being removed and be torn down. A retry after createKey/init fails (no prompt shown yet) runs at once.
  - **Activity guard:** before each prompt, if the activity is finishing or destroyed, or `isStateSaved`, both copies are zeroed and the user gets «No se pudo activar la huella». Otherwise `authenticate()` would return silently and leave the key copies in memory.
  - **`disable()`** now removes only `iv`, `wrapped` and `key_strongbox`, then deletes the Keystore key. `strongbox_failed` and `strongbox_hint_shown` survive.
  - **`unlock()`:**
    - If `Cipher.init` fails without `KeyPermanentlyInvalidatedException` and the key is StrongBox (the `key_strongbox` record, or else `KeyInfo`), it persists `strongbox_failed = true`, calls `disable()` and returns «La huella no responde: entra con la contraseña maestra y vuelve a activarla».
    - TEE keys behave as before.
    - The IV is decoded before the Keystore step, so a malformed pref is never taken for a StrongBox failure.
  - **`keyLevel(context)`:**
    - `SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java)`.
    - API 31+: `securityLevel` (STRONGBOX / TRUSTED_ENVIRONMENT / SOFTWARE; `UNKNOWN_SECURE` and the rest give UNKNOWN).
    - Before 31: `isInsideSecureHardware` through `legacyKeyLevel`.
    - No key or any exception gives UNKNOWN. It never throws.
  - **`hasStrongBox(context)`:** SDK ≥ 28 (`VERSION_CODES.P`) and `hasSystemFeature(FEATURE_STRONGBOX_KEYSTORE)`.
  - **New helpers:** `strongBoxFailed`, `strongBoxHintShown` and `markStrongBoxHintShown`. They are stored in the same `vault_biometric` SharedPreferences.
- **`ui/screens/SettingsScreen.kt`:**
  - New `FingerprintKeySection`, placed after «Copia automática». It only appears while the fingerprint is enabled.
  - It shows the title «Dónde está la clave de la huella» and `keyLevelLabel(keyLevel())`.
  - It shows the R2 hint once, through `shouldOfferStrongBoxHint`. `strongbox_hint_shown` is saved at the moment the hint is decided.
  - It is read off the main thread (`produceState` + `Dispatchers.IO`), again on each visit and on every vault state change, so the line disappears when a master change or an import turns the fingerprint off.
- **`tools/ToolTexts.kt`:** two new constants, both in `ALL_TOOL_TEXTS` and checked by `ToolTextsTest`:
  - `FINGERPRINT_NOT_RESPONDING`, the design text, verbatim;
  - `KEY_LOCATION_UNKNOWN = "No se pudo comprobar"`.
- **`ui/screens/VaultScreen.kt`: not changed.** The unlock path already shows `BiometricVault.unlock`'s `onError` message as-is (`error = it`). After the StrongBox disable, `bio` is recomputed on that recomposition, so «Usar huella» goes away and the menu offers «Activar huella», which goes to the TEE directly. `VaultBackup.kt` was not changed either: its `disable()` calls now keep the StrongBox prefs automatically.

## Decisions beyond the brief (please check)

1. **The `key_strongbox` pref and `legacyKeyLevel`.** Before API 31, `KeyInfo` cannot tell StrongBox from the TEE. Without a record:
   - Android 9 to 11 phones with StrongBox would show «Zona segura del procesador» for a StrongBox key;
   - they would get the R2 hint by mistake;
   - and `unlock()` could never detect a failing StrongBox key, so the user would see «No se pudo usar la huella» forever and every re-enable would try StrongBox again.

   `generateKey` with `setIsStrongBoxBacked(true)` either lands in StrongBox or throws, so the record is reliable. It describes the current key and is removed by `disable()`. It is not a secret and not a stored format (only a pref).
2. **`unlock()` also calls `disable()`** on the StrongBox failure. The text says «vuelve a activarla», and this matches the handling of `KeyPermanentlyInvalidatedException`. Re-enabling then goes straight to the TEE because of `strongbox_failed`.
3. **`enable()` now begins with `disable()`.** It used to begin with `deleteEntry` only. It is only called when the fingerprint is off, so the effect is the same, and no wrapped blob can be left pointing at a replaced key.
4. **The key is deleted on cancel and on a failed TEE attempt.** Before, the unused key stayed. This is harmless and cleaner.
5. **UNKNOWN label «No se pudo comprobar».** It is not in the annex (the annex has three labels). It contains no forbidden word. It needs the Abogado's OK, or a decision to hide the line for UNKNOWN.

## Tests and results

- `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL, 836 tests passed and 0 failed. This includes `KeyPlacementTest` (5 tests) and `ToolTextsTest` (11 tests).
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`:
  - BUILD SUCCESSFUL, run on the final code after the last edit; `compileDebugKotlin` was re-executed;
  - 836 unit tests across 52 suites, all with `failures="0"`;
  - lint has no error and no warning in the changed files. The remaining warnings already existed: DiscouragedApi on the manifest's `screenOrientation`, ObsoleteSdkInt on `mipmap-anydpi-v26`, and GradleDependency/NewerVersionAvailable.

## TDD evidence

- **RED 1:** `KeyPlacementTest` written first; `gradle -p jvm-check test --tests 'app.seguridad.tools.KeyPlacementTest'` failed in `compileTestKotlin` with `Unresolved reference 'KeyLevel'`, `'firstAttempt'` and `'shouldOfferStrongBoxHint'`.
- **GREEN 1:** after `KeyPlacement.kt` and the two texts, all 4 tests passed (`strongBoxOnlyWhenPresentAndNewEnoughAndNotFailed` covers all 8 combinations plus SDK 37 and 26; `hintOnlyOnceAndOnlyForTeeWithStrongBox`; `doFinalFailureFallsBackOnce`; `labels`), and `ToolTextsTest` stayed green.
- **RED 2:** the test `beforeApi31SecureHardwareIsStrongBoxOnlyIfCreatedThere` was added and failed with `Unresolved reference 'legacyKeyLevel'`.
- **GREEN 2:** after `legacyKeyLevel`, all 5 tests passed.

## Files changed

- `/home/user/Seguridad/app/src/main/java/app/seguridad/android/BiometricVault.kt`
- `/home/user/Seguridad/app/src/main/java/app/seguridad/tools/KeyPlacement.kt` (new)
- `/home/user/Seguridad/app/src/test/java/app/seguridad/tools/KeyPlacementTest.kt` (new)
- `/home/user/Seguridad/app/src/main/java/app/seguridad/tools/ToolTexts.kt`
- `/home/user/Seguridad/app/src/main/java/app/seguridad/ui/screens/SettingsScreen.kt`

## Self-review

- **Every exit of `enable` zeroes `dataKey` and `spare`:**
  - success: `BiometricVault.kt:226-227`;
  - `fail` without a next step: `:187` and `:191`;
  - `fail` with a fallback: `:187` here, then `spare` becomes the next attempt's `dataKey`, zeroed on that attempt's exits;
  - activity guard: `:202-203`;
  - cancel: through `fail`.
- **The fallback happens at most once:** the second attempt passes `spare = null` and `nextAfterFailure(TEE, _) == null`.
- **`onDone` is called exactly once** on each path except cancel (where it was never called, as before).
- **TEE behaviour stays as in 1.2.2:** no StrongBox flag, the same messages, and the unlock non-KPIE branch still says «No se pudo usar la huella».
- **The Keystore work runs on the main thread** (the menu tap), as before. StrongBox key generation is slower (typically hundreds of ms), and the second attempt adds a 600 ms wait.

## Concerns

1. **The 600 ms fallback delay is a heuristic** based on the biometric 1.1.0 dismissal order. It must be checked on a device. The cheapest way to force the path is to make the StrongBox attempt fail. If the second prompt does not appear, the user gets no message and the fingerprint stays off; the vault is not harmed.
2. **Prompt errors that are not StrongBox's fault also trigger the fallback and set `strongbox_failed` for good**, as the brief requires. Examples: `ERROR_LOCKOUT`, `ERROR_TIMEOUT`, `ERROR_CANCELED` when the app goes to the background. In the lockout case the second prompt fails at once with the system message. The outcome is the safe direction (TEE from then on), but that phone never tries StrongBox again. To undo it, the owner clears the app's data.
3. **`KEY_LOCATION_UNKNOWN` «No se pudo comprobar»** is a new text outside the annex and needs approval.
4. **The design only names the Keystore failure at the start of unlock.** A StrongBox key that fails later, at `doFinal` after the fingerprint, still says «No se pudo desbloquear con la huella» and is neither disabled nor marked. This keeps to the brief; if the phone shows it, it is a candidate follow-up.
5. **None of the Keystore or prompt code can be unit-tested here.** Only the decisions in `KeyPlacement` are covered. The device list below is the real test.

## On-device test list (owner: Honor Magic 8 Pro, Android 17, StrongBox)

Install the debug or release build of this branch. Unlock the vault with the master password before each «enable».

1. **Enable:** Bóveda → ⋮ → «Activar huella» → one fingerprint prompt → snackbar «Huella activada». Ajustes → the section «Dónde está la clave de la huella» says **«Chip seguro (StrongBox)»**, and there is no R2 hint. If the prompt appeared **twice**, StrongBox failed and the label will say «Zona segura del procesador»: report it with the error messages, if any.
2. **10 lock/unlock cycles:** «Bloquear» → the fingerprint prompt appears automatically (or tap «Usar huella») → it opens. Repeat 10 times. Each time it must open with no message, and Ajustes must still say «Chip seguro (StrongBox)».
3. **Reboot:** restart the phone, open the app, and unlock with the fingerprint. The 7-day rule may ask for the master password right after a reboot only if 7 days have passed; otherwise the fingerprint must work. The label must still say StrongBox.
4. **Add a fingerprint:** in system settings, add a new fingerprint. Back in the app, lock and try the fingerprint: the app must say «Has cambiado las huellas del móvil: entra con la contraseña maestra y vuelve a activarla». The master password must open the vault, and «Activar huella» must be offered again and work (still StrongBox, since this is not a StrongBox failure).
5. **Cancel:** while enabling, tap «Usar contraseña maestra» on the prompt. There must be no second prompt and no message, and the fingerprint stays off. Enabling again must work.
6. **R2 (optional):** if a 1.2.2 install with the fingerprint on can be updated to this build without uninstalling, the Ajustes line says «Zona segura del procesador» and the hint «Este móvil tiene chip seguro. Para usarlo, desactiva la huella y vuelve a activarla.» appears once. Leave Ajustes and come back: the hint is gone. Turn the fingerprint off and on: the line now says «Chip seguro (StrongBox)».
7. **Disable and enable again:** ⋮ → «Desactivar huella» → «Activar huella». It must still go to StrongBox (disable does not erase the StrongBox prefs).

**Seeing «Zona segura del procesador»:**
- On any phone without StrongBox (most mid-range Android 9+ phones; check with `adb shell pm list features | grep strongbox`, where no output means none), or on an Android Studio emulator with a fingerprint enrolled (Settings → Security → Fingerprint, then `adb -e emu finger touch 1` at the prompt): enable the fingerprint, and Ajustes must say «Zona segura del procesador». There is only one prompt and no R2 hint, because there is no StrongBox.
- Note: emulators normally report no StrongBox; some emulator images expose a software-backed Keystore, in which case the label is «Software: el móvil no protege esta clave con hardware». That is expected on an emulator, not on a real phone.
- On the Honor itself, the TEE path can only be seen after a StrongBox failure (`strongbox_failed`); clearing the app's data resets it.

## Revisión experto-seguridad-cripto

The relevant checklist items are key management (where the key lives, what invalidates it, what happens on failure), secret wiping, no logs, standard primitives, no format change and no new permission or dependency.

- **Same key as before, with StrongBox only added:**
  - AES-256: `BiometricVault.kt:85`;
  - GCM + NoPadding: `:83-84`;
  - user authentication: `:86`;
  - invalidated by enrollment: `:87`;
  - per-use strong biometric on API 30+: `:89`;
  - `setIsStrongBoxBacked(true)` only on the StrongBox attempt and API 28+: `:90`;
  - `setUnlockedDeviceRequired` appears only in the comment at `:91`, never called.
- **Standard primitives only:** `javax.crypto` Cipher/KeyGenerator/SecretKeyFactory with the AndroidKeyStore provider (`:101`, `:213`). No custom cryptography, no new dependency (androidx.biometric 1.1.0, already present).
- **The fallback never weakens the TEE path:** the second attempt is exactly the 1.2.2 key (`createKey(false)`), and `nextAfterFailure` (`KeyPlacement.kt:23`) allows at most one step, StrongBox → TEE. Cancel stops (`BiometricVault.kt:157`, `:236`), so a user cancel cannot be turned into a downgrade.
- **Downgrade risk:**
  - Persisting `strongbox_failed` (`:195`, `:258`) means a later enable uses the TEE (`KeyPlacement.kt:16-17`).
  - Another app cannot set this pref (private SharedPreferences). Root could, but root can already do worse.
  - The TEE is the 1.2.2 baseline, so the worst case is the old protection level, never below it. Severity: Informativa.
- **Data key wiping:** both copies are zeroed on every exit (`BiometricVault.kt:187`, `:191`, `:202-203`, `:226-227`). The copy is taken before the first attempt (`:176`). The wrapped blob and the IV are the only things stored, as before (`:217-219`).
- **Invalidation still works:** `KeyPermanentlyInvalidatedException` → `disable()` + the master-password message (`:250-252`), unchanged. The StrongBox unlock failure also ends in `disable()` (`:258-260`), so a broken key is never kept in use.
- **Prefs that survive `disable()`:** only `strongbox_failed` and `strongbox_hint_shown` (`:40-41`, `:125-130`). Both are non-secret booleans about the phone. `key_strongbox` is removed together with the key (`:129`).
- **Errors:** `keyLevel` never throws (`:118-122`, `:100-115`), and Settings reads it on `Dispatchers.IO` (`SettingsScreen.kt:258`).
- **No logs:** the diff adds no `Log.` or `println` (checked with grep over the changed files).
- **Network:** none.
- **Permissions and components:** no new permission. `hasSystemFeature` (`BiometricVault.kt:61`) needs none, and there is no new component.
- **Formats:** `SG1:`, `.sgenc`, `seguridad-vault`, `.sgbak`, `CP1:`, `.cpenc` and `criptopanel-vault` are untouched. The pref file `vault_biometric` keeps its `iv` and `wrapped` keys, so a 1.2.2 install unlocks unchanged, and its key reads as TEE, which drives R2.
- **Texts:**
  - annex labels and R2 verbatim (`ToolTexts.kt:410-416`, used by `KeyPlacement.kt:44-49`, `SettingsScreen.kt:273-274`);
  - design unlock text verbatim (`ToolTexts.kt:419`);
  - «Chip seguro» is allowed by `ToolTextsTest`;
  - the new «No se pudo comprobar» (`ToolTexts.kt:416`) is pending the Abogado.
- **Findings:** no Crítica, Alta or Media. Informativa:
  - the delay heuristic (concern 1);
  - the permanent `strongbox_failed` on non-StrongBox prompt errors (concern 2);
  - the main-thread StrongBox key generation (performance only).

---

# Fix round 1 (commit `0bff666`, pushed to `ccr-a1a6eabb-765f9g` together with `20e2885`)

**Status: DONE_WITH_CONCERNS.** All review items are fixed. The new prompt and lifecycle behaviour can only be tested on a device.

## What changed

- **I1, unlock (`BiometricVault.kt:325-370`):**
  - A StrongBox key now leads to `markStrongBoxFailed` + `disable()` + «La huella no responde…» in four cases:
    - `doFinal` fails (`:363`);
    - a prompt error is classified HARDWARE: `ERROR_HW_UNAVAILABLE` 1, `ERROR_UNABLE_TO_PROCESS` 2 or `ERROR_VENDOR` 8 (`:368`);
    - the CryptoObject is null, which `prompt` reports as HARDWARE (`:174`);
    - `Cipher.init` fails, as before.
  - A key counts as StrongBox if the `key_strongbox` record says so, or else if `KeyInfo` reports STRONGBOX.
  - `CANCELED`, `TIMEOUT`, `LOCKOUT`, `LOCKOUT_PERMANENT`, `NO_BIOMETRICS` and the rest keep the 1.2.2 behaviour: their message, and nothing is marked.
  - Cancels still do nothing.
  - The blob is now decoded before the prompt, so a Base64 error is not blamed on StrongBox.
- **I2, enable (mark only on evidence):**
  - Every error that is not a cancel still gets the one TEE retry.
  - Marking moved out of `fail` into `note()`/`end()` (`:286`, `:300-308`, `:314`), driven by `shouldMarkStrongBoxFailed(StrongBoxFailure, RetryOutcome)` (`KeyPlacement.kt:52`):
    - `KEY_SETUP` (createKey or `Cipher.init` threw) marks at once;
    - `AUTH_OR_FINAL` (prompt error, null CryptoObject, `doFinal`) marks only if the TEE retry returns STORED.
  - A retry that fails, is cancelled, times out waiting, is stopped by the guard or loses its activity gives FAILED, and nothing is marked.
  - Error codes are classified by `promptErrorKind(code)` (`KeyPlacement.kt:78`), a pure function on Int codes. The codes are mirrored from `BiometricPrompt.ERROR_*` 1.1.0, values checked with javap.
- **M1, wait instead of a fixed delay (`:288-297`):**
  - The wait loop runs on `Dispatchers.Main`, which posts, so the prompt's removal, already queued, runs first.
  - It polls `findFragmentByTag("androidx.biometric.BiometricFragment")` every 100 ms, for at most 2 s, then calls `attempt`.
  - If the 2 s run out, it ends with «No se pudo activar la huella», wipes both copies and marks nothing.
  - A KEY_SETUP failure retries at once, because no prompt was shown.
- **M2, one enable at a time:**
  - An `AtomicBoolean` (`:211`) makes a second tap a no-op, and the copy that tap brought is zeroed (`:212`).
  - It is reset in `end()` (`:319`), which every exit goes through: success, failure, cancel, guard stop, wait timeout, and `ON_DESTROY` of the activity (`LifecycleEventObserver`, `:220`). So a lost prompt callback, for example after a rotation, cannot block enabling for good.
  - Late callbacks after `end` are ignored (`:251`): nothing is wrapped with a wiped key.
- **M3:** the broad unlock catch is kept, with a comment (`:351`).
- **M4, Ajustes (`SettingsScreen.kt:253-275`):**
  - The key line still reacts to the vault state.
  - The R2 decision is a separate `produceState` keyed on nothing, so it is decided once per visit and survives lock and unlock.
  - `strongbox_hint_shown` is saved by a `LaunchedEffect` inside the branch that renders the hint, on `Dispatchers.IO`. Effects run after the composition is applied.
- **M5, key setup off the main thread:**
  - `createKey` and `Cipher.init` run in `withContext(Dispatchers.Default)` (`:237`), inside a `MainScope` per flow that `end()` cancels.
  - Back on Main, the activity guard is checked again before `authenticate` (`:249`).
  - `unlock`'s `Cipher.init` stays on main, as it was not part of the finding; it is a single operation start, not a key generation.
- **M6:** the label is unchanged, as instructed.

## Tests and verification

- **TDD:** the new tests `strongBoxIsMarkedFailedOnlyOnEvidence` (every StrongBoxFailure × RetryOutcome combination) and `promptErrorsAreClassifiedByBiometricPromptCode` (codes −1 to 20) were RED first (`Unresolved reference 'StrongBoxFailure'`, `'shouldMarkStrongBoxFailed'`, `'PromptErrorKind'`). They went GREEN after `KeyPlacement.kt`. KeyPlacementTest now has 7 tests, all passing.
- `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL, 838 passed and 0 failed.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL in 2m 44s. There were 838 unit tests in 52 suites with 0 failures and 0 errors. Lint shows only the warnings that already existed (DiscouragedApi, ObsoleteSdkInt, GradleDependency, NewerVersionAvailable), none in the changed files.

## Remaining concerns

1. The fragment-gone wait and the lifecycle-based end depend on androidx.biometric 1.1.0 internals (the fragment tag, and the order of the callback and the removal). If the second prompt does not show, the outcome is «No se pudo activar la huella», with nothing marked and the vault untouched. Phone test 9 below covers it.
2. On unlock, a StrongBox key whose blob fails the GCM tag at `doFinal` (a corrupted pref) is also treated as «no responde». This is the safe direction: the user re-enables on the TEE.
3. A StrongBox key generation still in progress on `Dispatchers.Default` when the activity is destroyed can leave an unused Keystore entry. It is harmless, and the next enable deletes it first (`disable()`).

## Phone tests added (Honor Magic 8 Pro)

8. **Home during the first prompt:** start «Activar huella» and press Home while the prompt is up, then come back.
   - The fingerprint stays off, and a message («No se pudo activar la huella» or the system's) may appear.
   - Enable again: there must be only one prompt, and Ajustes must say «Chip seguro (StrongBox)». That shows StrongBox was tried again, so nothing was marked.
9. **Enable, then check Ajustes:** after a normal enable, Ajustes still says «Chip seguro (StrongBox)». Tapping «Activar huella» twice fast must give a single prompt.
10. **Optional, R2 lifetime:** with a 1.2.2-era TEE key, open Ajustes. The hint must stay on screen while you lock and unlock the vault from another tab and come back within the same visit. It must not appear on the next visit.

## Revisión experto-seguridad-cripto (fix round)

- **Downgrade only on evidence:**
  - Before, `strongbox_failed` could be written by user actions (Home, timeout, lockout).
  - Now it is written only by a Keystore refusal (`BiometricVault.kt:286` → `KeyPlacement.kt:52-55`), by a confirmed TEE success after a StrongBox auth or final failure (`:314`), or by a StrongBox unlock failure that implicates the hardware (`:339`, `:363`, `:368`).
  - The worst case is the 1.2.2 TEE level, never lower. Informativa.
- **Key material:**
  - Both copies of the data key are zeroed on every exit through `end()` (`:315`). The ignored second tap zeroes its copy (`:212`).
  - A late callback cannot wrap a wiped key (`:251`).
  - An unused Keystore key is deleted on failure (`:316`).
- **Key spec unchanged:** `createKey` is untouched (AES-256-GCM, NoPadding, user auth, invalidated on enrollment, per-use AUTH_BIOMETRIC_STRONG on API 30+, StrongBox flag only on the StrongBox attempt). There is no `setUnlockedDeviceRequired`.
- **Invalidation unchanged:** `KeyPermanentlyInvalidatedException` still leads to `disable()` and the master-password message.
- **Unlock error handling:** user and environment errors keep the 1.2.2 messages (`:368`).
- **Threads:** the Keystore key generation runs off the main thread (`:237`), and the activity guard is checked again on Main (`:249`).
- **Hygiene:** no logs, no new permission, no new dependency (kotlinx-coroutines-android and lifecycle were already used), and no change to any format.
- **Findings:** no Crítica, Alta or Media; the remaining concerns above are Informativa.
