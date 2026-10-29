# Task 10 report: Bóveda A8, A11, A12, A16c

## Implemented
- A8: `VaultCrypto.afterMasterUnlock` re-wraps a v3 file whose Argon2id is weaker in memory or passes than the profile (same DEK, revision+1). Deviation, safer than the brief: the new cost is the per-axis maximum of the file's and the profile's, so a copy with m=128 MiB/t=1 is not lowered to 64 MiB; for the brief's cases (8 MiB/t=1 -> profile; 128 MiB/t=4 kept) the result is identical. Parallelism comes from the profile.
- A11: `biometricAllowed(file, now, highWater = 0)` plus `CLOCK_SLACK_MS`; `VaultStore(clockHighWater, onClockSeen)`: gate in `biometricAllowed` and `unlockWithKeyLocked`, `onClockSeen` in `opened()` (every unlock/create/import path) and `touch()`. `Settings.clockHighWaterMs`; `AppContainer.rememberClock` persists the max, writing only when the time is 30 s past the mark (touch runs often; the mark lags at most 30 s, far under the 5 min slack).
- A12: `destroy()` deletes in IO the siblings `vault.bin.bad-*` and `att.bad-*` (deleteRecursively); `FORGOT_DIALOG_TEXT` (annex text, verbatim) in VaultScreen.kt.
- A16c: no existing test checked zeroing of the key on early exit; added `unlockWithKeyEarlyExitWipesKey` (MasterRequired exit and no-vault exit).
- Folded: BiometricVault.unlock zeroes `dataKey` if `onKey` throws. Not zeroed in a finally on purpose: VaultScreen's onKey hands the key to a coroutine that uses it later.

## TDD
RED: `gradle -p jvm-check test --tests 'app.seguridad.vault.*'` -> compileTestKotlin failed ("No parameter with name 'clockHighWater' found", "'highWater'", "Unresolved reference 'CLOCK_SLACK_MS'"). GREEN after implementing: 323 vault tests, then one test bug of mine (wrong-key case, a v3 file's resolve never checks the key) was removed and re-run.
Tests added: VaultStoreTest importWithWeakKdfIsRewrapped, importWithStrongerKdfIsKept, clockSeenOnUnlockAndTouchAndMarkBlocksFingerprint, destroyRemovesBadCopies, unlockWithKeyEarlyExitWipesKey; VaultV3Test clockSetBackBlocksBiometrics, afterMasterUnlockRewrapsOnlyAWeakerKdf.

## Verification
- `gradle -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL, 846 tests, 0 failures.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: BUILD SUCCESSFUL (online, no --offline).

## Files
Vault.kt, VaultStore.kt, Settings.kt, AppContainer.kt, BiometricVault.kt, VaultScreen.kt; tests VaultStoreTest.kt, VaultV3Test.kt.

## Self-review / concerns
- Old files (legacy, v1, v2) still go through the unchanged wrap branch. No format change; the re-wrap writes the existing v3 form.
- Every master unlock of a vault with weaker KDF than the current profile now re-wraps (one extra Argon2id), also after the user raises the profile: intended.
- The mark is only persisted after Settings.load; a touch before load could skip a write, harmless.
- Gate still bypassable by wiping app data (outside the model); the mark is not authenticated (settings.json is app-private).

## Revisión experto-seguridad-cripto
- KDF re-wrap, same DEK, revision+1, standard Argon2id only: app/src/main/java/app/seguridad/vault/Vault.kt:346; wrap uses fresh salt (Vault.kt wrap).
- Never lowers cost: Vault.kt:350.
- Clock gate: Vault.kt:391, VaultStore.kt:275, VaultStore.kt:292.
- Key wipes: VaultStore.kt unlockWithKeyLocked (fill on all exits), app/src/main/java/app/seguridad/android/BiometricVault.kt:219.
- Leftover removal: VaultStore.kt:737; only names starting vault.bin.bad- / att.bad- (test checks an unrelated file survives).
- No logs, no new permissions/deps/network; formats unchanged.

## Fix round 1
1. Clock set forward: new `onClockReset` in VaultStore, called after the master was verified in unlockLocked, confirmMaster and replace (VaultStore.kt:273); AppContainer.resetClock writes `Settings.resettingClock(now)`, which may lower the mark. Fingerprint unlock and touch() still only rise. Test: masterUnlockResetsAClockMarkInTheFuture (mark = now + 1 year -> CLOCK refusal; after master unlock fingerprint allowed; confirmMaster and import reset too; touch never lowers).
2. `VaultCrypto.biometricRefusal` + enum `BiometricRefusal { MASTER_DUE, CLOCK }` (Vault.kt:391); `biometricAllowed` is now defined as refusal == null. `VaultStore.biometricRefusal`. VaultScreen shows CLOCK_CHANGED_TEXT («La hora del móvil ha cambiado. Por seguridad, escribe la contraseña maestra.») for CLOCK, the 7-day text for MASTER_DUE. No forbidden words. A clock before the last master check also counts as CLOCK. Test: biometricRefusalSaysWhyTheClockOrTheSevenDays.
3. Pure decisions extracted to Settings.kt (`clockMarkWorthWriting`, `seeingClock`, `resettingClock`, `CLOCK_WRITE_STEP_MS`); AppContainer uses them. New app/src/test/java/app/seguridad/settings/ClockMarkTest.kt: lower time never lowers; nothing below mark + 30 s; reset lowers.
4. importWithWeakKdfIsRewrapped uses `KdfProfile.STANDARD.params`.
5. Conversion branch uses the per-axis maximum too (Vault.kt:348). Test conversionOfAStrongerOldFileKeepsItsCost with the real 1.1 fixture (1024 KiB, t=1) against profiles 512/3 and 256/1. No existing test touched.
6. SeguridadApp.onCreate calls `container.rememberClock(System.currentTimeMillis())` right after settings.load (SeguridadApp.kt:51); rememberClock is now public.

Tests were written together with the code (not strictly red first) for this round; they pass and exercise the behaviours above.

### Commands
- `gradle -p jvm-check test --tests VaultStoreTest --tests VaultV3Test --tests ClockMarkTest`: BUILD SUCCESSFUL.
- Full: `gradle -p jvm-check test --no-daemon --max-workers=1`: BUILD SUCCESSFUL, 852 tests, 0 failures.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: BUILD SUCCESSFUL (first try failed on a duplicate import in VaultScreen.kt, fixed).

### Revisión experto-seguridad-cripto (round 1)
- Mark reset only after the master verified (an attacker cannot clear it without the password): VaultStore.kt:273.
- Conversion never lowers KDF cost: Vault.kt:348.
- Gate reasons without duplicating arithmetic: Vault.kt:391.
- Throttle/rise/reset logic: settings/Settings.kt (clockMarkWorthWriting, seeingClock, resettingClock), tests in settings/ClockMarkTest.kt.

## Fix round 2
- I-1: `onClockReset(now())` in `VaultStore.replace` moved from the validation step to after the save: after `commit(...)` returns in the JSON branch, and after `save(checked); changed()` in the .sgbak branch (before `opened`). Failure paths (including moveIn/save failures) never call it. Test `failedImportSaveNeverResetsTheClockMark`: vault.bin.tmp is a directory (as `breakSaving()` in the existing full-disk tests), both a JSON and a .sgbak import fail with IOException, the reset callback never fired, the old vault stays. The successful-import reset test (`masterUnlockResetsAClockMarkInTheFuture`) still passes.
- M-1: `MasterRequired(val reason: BiometricRefusal)`; its message is `CLOCK_CHANGED_TEXT` for CLOCK and the 7-day text otherwise, so `error = e.message` in VaultScreen shows one reason only. `CLOCK_CHANGED_TEXT` moved from VaultScreen.kt to VaultStore.kt (UI imports it). `unlockWithKeyLocked` takes the reason from `VaultCrypto.biometricRefusal` and still zeroes the key before throwing. Test `unlockWithKeyRefusalCarriesItsReason` (reason, message, key zeroed, both reasons).
- Commands: `gradle -p jvm-check test --tests VaultStoreTest --tests VaultV3Test --tests ClockMarkTest`: BUILD SUCCESSFUL. Full `gradle -p jvm-check test`: BUILD SUCCESSFUL, 854 tests, 0 failures. `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL.
- Revisión experto-seguridad-cripto (round 2): the mark can no longer be lowered by an import that did not get saved (VaultStore.kt, `onClockReset` after `commit` / after `save(checked)` in `replace`); the refusal reason is a typed value, not text matching (VaultStore.kt `MasterRequired.reason`); key wipe on the refusal exit kept in `unlockWithKeyLocked`.
