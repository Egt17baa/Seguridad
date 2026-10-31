# Task 7: Inspector de QR — report

**Status:** DONE. Commit `c416416` «1.3.0: pantalla Inspector de QR» (not pushed).

## State of the WIP found

The interrupted implementer had left nearly all of the task in the working tree, and it compiled. All 795 JVM tests passed on it. I read every file of the diff (the same content as `task-7-wip.patch`) against the brief, the design §2, the annex and the controller rulings:

- **Screen** `QrInspectScreen.kt`: camera, «Elegir imagen», full text through `visibleText`, «Sin avisos» in `onSurfaceVariant` with no icon, «Señales de alerta (N)», «Copiar» and «Copiar contraseña» through `copySensitive`, the password and the text that holds it hidden until tapped, and buttons for the three Seguridad kinds. Complete.
- **Pure view model** `tools/qr/QrView.kt`: the QR's kind → title, fields, alerts (annex lines), notes and action. It is JVM-tested.
- **`decodeQrFromImage`** (`android/QrFromImage.kt`) and the pure sizing in `tools/qr/QrImageSize.kt`. Complete and as ruled.
- **`QrHandoff`**: a target-typed variant (`put(target, text)` / `take(target)` / `clear()`) instead of a bare `var text`. It is stricter: one screen never gets text meant for another, and any read empties it. I kept it. It is cleared from the `SeguridadApp` state collector when the vault is not Unlocked.
- **Hand-offs:**
  - `ContactAddScreen` takes the card as pasted (`qr = false`).
  - `OpenReceivedContent` takes it as pasted text.
  - `QrTotpImportScreen` (VaultGate, then `VaultEntryEditScreen(otpAuthText=…)`) uses the existing `applyOtpAuth`, which runs `parseOtpAuth` / `parseOtpMigration` + `totpImportEntries`.
- **Task 6 leftovers:** both were done, with tests.

Defects I found and fixed:

1. **`Visible.kt`:** the KDoc of `isEmojiOrSymbol` had ended up stacked above `hiddenMarks` (two KDoc blocks in a row, the first one orphaned). I moved it back to its function.
2. **A hidden character with no alert.** A link's `uri` is the *trimmed* text, so marked whitespace at either end was dropped from the link check: a tab, U+2028/U+2029, U+001C to U+001F. The full text showed «⟨U+2028⟩» but the screen said «Sin avisos». This contradicts the anti-goal and the annex. `linkView` now computes the marks over the whole text and adds the annex bidi line when the link's own signal did not fire. Red test first: `QrViewTest.hiddenCharactersOutsideTheLinkStillRaiseAnAlert`.
3. **Password mask:** the Wi-Fi password mask showed `min(len,16)` dots, which gives away its length. It is now always eight dots.
4. **Pixel copy in memory:** `decodeQrFromImage` kept the ARGB `IntArray` (up to 16 MB, which may be a 2FA QR's picture) until it was garbage-collected. It is now zeroed as soon as `RGBLuminanceSource` has made its grey copy.
5. **2FA key left in memory.** Suppose «Añadir a la bóveda» is tapped with the vault locked and the user then leaves the gate without unlocking (Atrás or another tab). The key would stay in `QrHandoff` until the next lock, because the collector only fires on a state change and the vault is already Locked. `QrTotpImportScreen` now clears it in `onDispose`.
6. **Scheme in the alert:** the dangerous-scheme line now passes the scheme through `visibleText`, for consistency with every other value taken from the QR. Test added: `aDangerousSchemeIsNamedWithItsMarks`.

## TDD evidence

- **Task 6 leftover tests:** I restored the old `isEmojiOrSymbol` and the old `ethereum` condition, then ran `QrContentTest`. Exactly `ethereumTransferThatAlsoSendsEtherIsPlainText` and `emojiVariationOnlyFollowsTheEmojiPlane` failed (30 run, 2 failed). With the new code all 30 pass. The files were then restored from backup and checked with the diff stat.
- **New link test:** it failed before fix 2 (`expected:<[Hay caracteres invisibles…⟨U+2028⟩…]> but was:<[]>`) and passes after. My first version used `\u0000`, which passed from the start: Kotlin's `trim()` only removes whitespace. So I rewrote it with the whitespace that is actually marked.

## Verification (run once, on the final tree, before committing)

- `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: exit 0, **797 tests, 0 failures, 0 errors**.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`: **BUILD SUCCESSFUL in 3m 26s**. `testDebugUnitTest` ran 797 tests with 0 failures.
- `grep -n "ACTION_VIEW\|startActivity" app/src/main/java/app/seguridad/ui/screens/QrInspectScreen.kt`: empty (exit 1).
- `aapt2 dump permissions` on the debug APK gives INTERNET, CAMERA, USE_BIOMETRIC, USE_FINGERPRINT and the dynamic-receiver permission, all unchanged. There are no diffs to the manifest, `app/build.gradle.kts` or `gradle/`, so there are no new permissions, components or dependencies.

## Files changed (commit c416416)

New:
- `app/src/main/java/app/seguridad/ui/screens/QrInspectScreen.kt` (with `QrTotpImportScreen`)
- `app/src/main/java/app/seguridad/android/QrFromImage.kt`
- `app/src/main/java/app/seguridad/tools/qr/QrHandoff.kt`
- `app/src/main/java/app/seguridad/tools/qr/QrView.kt`
- `app/src/main/java/app/seguridad/tools/qr/QrImageSize.kt`
- tests: `app/src/test/java/app/seguridad/tools/qr/{QrHandoffTest,QrImageSizeTest,QrViewTest}.kt`

Modified:
- `SeguridadApp.kt`
- `tools/ToolTexts.kt`
- `tools/qr/QrContent.kt`
- `tools/qr/Visible.kt`
- `ui/AppNavHost.kt` (routes `tools/qr`, `tools/qr/totp`)
- `ui/screens/ContactAddScreen.kt`
- `ui/screens/PkActionsScreen.kt`
- `ui/screens/ToolsScreen.kt` (`Tool("tools/qr", "🔍", "Inspector de QR", "Mira qué hay en un QR sin abrirlo")`)
- `ui/screens/VaultEntryScreen.kt` (optional `otpAuthText`)
- `QrContentTest.kt`

## Non-annex texts added (all in `tools/ToolTexts.kt`, all in `ALL_TOOL_TEXTS`, so the forbidden-word test covers them)

- **Tile:** `QR_TOOL_TITLE` «Inspector de QR» and `QR_TOOL_SUBTITLE` «Mira qué hay en un QR sin abrirlo» (the plan's words).
- **`QR_HIDDEN_ELSEWHERE`:** a variant of the annex bidi line for when the hidden characters cannot be shown as marks (percent-encoded in the host, or a line break inside a link). **It should go to the Abogado Digital**, because it changes an annex sentence.
- **Buttons and states:** «📷 Escanear QR», «Elegir imagen», «Buscando el código en la imagen…», «Texto completo», «Mostrar» and «Ocultar», «Copiar», «Copiar contraseña», «Añadir contacto», «Abrir lo recibido», «Añadir a la bóveda». There are also the two texts shown when the full text is hidden: `QR_TEXT_HIDDEN_PASSWORD` and `QR_TEXT_HIDDEN_OTP`.
- **Errors:**
  - `QR_NOT_FOUND`
  - `QR_IMAGE_TOO_BIG` «La imagen ocupa más de 50 MB.»
  - `QR_IMAGE_TOO_LARGE` (260 MP or 32.768 px, worded like `REDACT_TOO_LARGE`)
  - `QR_IMAGE_DAMAGED`
  - `QR_NO_CAMERA_PERMISSION` and `QR_CAMERA_FAILED`
- **Kind names and field labels:** `QR_KIND_*`, `QR_FIELD_*`, `QR_SCHEME_NONE`, `QR_CIPHER_NONE`, `QR_CIPHER_UNKNOWN`, `QR_YES`.

## Self-review

- **Brief and rulings, met:**
  - `decodeQrFromImage(bytes): String?` exists.
  - The size is bounded at three points: `readPhoto` (50 MB), `declaredSizeAllowed` before any decode, and ≤ 2048 px per side.
  - zxing reads through `RGBLuminanceSource` + `MultiFormatReader`, QR_CODE only, with TRY_HARDER.
  - The Bitmap is recycled and nothing is saved.
  - The camera uses `scanQrOptions`/`SecureCaptureActivity` with `keepOpenOnce` set and reset.
  - Nothing opens anything.
  - «Sin avisos» is neutral, and copying goes through `copySensitive`.
  - `QrHandoff` is never in the route, is read once and is cleared on lock.
  - The card is read as pasted, so it stays unverified, and the OTP goes through the vault's existing import behind VaultGate.
- **Extra, beyond the brief:** a light-on-dark code is tried a second time, inverted (`source.invert()`).
- **Not tested on a device.** The screen, the camera and the picker are not covered by unit tests (as with the other screens). Only the pure parts are JVM-tested: `QrView`, `QrHandoff`, `QrImageSize`, `Visible` and `QrContent`.

## Concerns

- **A changed annex sentence:** `QR_HIDDEN_ELSEWHERE` is a new wording of an annex line and should go to the Abogado Digital with the other non-annex texts.
- **«Copiar» copies the raw text,** bidi and invisible characters included: it is what the QR holds. That is faithful, but the user could paste it somewhere that hides those characters. The screen does mark them and raise the alert first.
- **Hand-off lost on rotation.** If the phone is rotated while «Añadir a la bóveda» is waiting at the locked gate, the hand-off is lost (`onDispose` clears it) and the form opens empty after unlock. This fails closed, and the inspector also loses its result on rotation (memory-only state, by design).
- **«Abrir lo recibido» has no `onDispose` clear.** If the user leaves its locked gate, the text (public: an encrypted or signed text) stays in `QrHandoff` until the inspector is opened again or the vault next locks. It is low impact because the text is not secret.

## Revisión experto-seguridad-cripto

Threat model: the QR is hostile, untrusted input (a sticker over a real one, a crafted image). The assets are a 2FA key or Wi-Fi password the QR may carry, and the user's trust in what is shown. Out of scope: root and memory forensics.

- **Nothing is opened or connected.** The grep is empty. The only navigation goes to the app's own routes (`AppNavHost.kt:186-195`), and all output is plain `Text` inside `NoCopy` (`QrInspectScreen.kt:178`). Lo correcto según OWASP MASVS-PLATFORM: no implicit intent with untrusted data.
- **Input limits before processing:**
  - `readPhoto` bounds the file to MAX_IMAGE_BYTES + 1 (`QrInspectScreen.kt:147`, `PhotoCleanScreen.kt:90-100`).
  - The header is refused before any pixel is inflated (`QrFromImage.kt:98`, `:106`).
  - The decode target is ≤ 2048 px per side (`QrFromImage.kt:99`, `:108`, `QrImageSize.kt:14-33`), and a decoder that ignores it is refused (`QrFromImage.kt:54`).
  - The text is ≤ 4096 UTF-8 bytes (`QrContent.kt:51`).
  - OOM and zxing runtime errors become a Spanish message, not a crash (`QrFromImage.kt:46-50`, `:80-86`; `QrInspectScreen.kt:158-172`). CWE-400/CWE-789 covered.
- **Decoder restricted:** QR_CODE only, with TRY_HARDER (`QrFromImage.kt:74-75`).
- **Secrets are short-lived:**
  - The picked bytes are zeroed (`QrInspectScreen.kt:153`) and so is the ARGB copy (`QrFromImage.kt:65`); the Bitmap is recycled (`QrFromImage.kt:59`).
  - The result lives only in `remember`, never in saved state (`QrInspectScreen.kt:110`).
  - The Wi-Fi password is masked with a fixed length (`QrInspectScreen.kt:255`).
  - Copying goes through `copySensitive` (`:258`, `:285`).
- **Hand-off:**
  - Never in the route.
  - Read once, and only by its target (`QrHandoff.kt:23-29`).
  - Cleared when the vault is not Unlocked (`SeguridadApp.kt:39`), on entering the inspector (`QrInspectScreen.kt:116`) and when the 2FA import screen goes away (`QrInspectScreen.kt:312`).
  - The OTP is taken only once the vault is unlocked (`QrInspectScreen.kt:316`).
- **No false trust:**
  - A card from the inspector is never «verified» (`ContactAddScreen.kt:103`, `qr = false`).
  - «Sin avisos» is neutral `onSurfaceVariant`, with no ✓ and no green (`QrInspectScreen.kt:267`).
  - A text with hidden marks always raises the annex alert (`QrView.kt:191`, `:207`; test `QrViewTest.hiddenCharactersOutsideTheLinkStillRaiseAnAlert`).
  - Both host forms are shown, readable and ASCII (`QrView.kt:209-210`).
  - Unicode spoofing checks follow UTS #39 (mixed scripts in the Task 6 signals), and the emoji exception is bounded to the emoji plane (`Visible.kt:72`).
  - An ERC-681 `transfer` that also carries `value=` is not summarised (`QrContent.kt:154`), so ether sent to the contract is never hidden.
- **No new permissions, components or dependencies** (aapt2 output above, no manifest or Gradle diff). No logs: nothing in the new files calls `Log`. Standard primitives only: no cryptography is added.

---

# Fix round 1 (minors from the review of c416416)

**Status:** DONE. Commit `7d39985` «1.3.0: Inspector de QR, revisión 1» (not pushed).

## What changed

1. **Host emphasis.** The highlighted name is now `host`, the ASCII/punycode name the browser connects to, split into labels. An IP is shown whole, as one label. The Unicode reading goes below it, in `bodySmall` and `onSurfaceVariant`, as «Se lee así: «…»», and only when it differs (`QrView.kt:228-234`, `QrInspectScreen.kt:265-280`). The redundant «Web» and «Se conecta a» fields are gone. «Web» is now the small label over the highlighted name, and `QR_FIELD_CONNECTS_TO` was removed. Tests:
   - `punycodeShowsBothFormsOfTheName`: the labels are the `xn--` form, and `hostReading` is the Cyrillic one.
   - `theHighlightedHostIsTheOneConnectedTo`: the host after `@`, an IP whole, and no host for `javascript:`.
   - The tests for the plain and scheme-less links were updated.
2. **No change** (it goes to the Abogado Digital at the end).
3. **`QR_SCHEME_NONE`** is now «Sin indicar» (`ToolTexts.kt`). The scheme-less test now checks it.
4. **«Abrir lo recibido» hand-off.** `OpenReceivedScreen` clears `QrHandoff` in `onDispose`, outside the gate, like the 2FA screen (`PkActionsScreen.kt:516`).
5. **Copy notice.** New `QR_COPIED_WITH_HIDDEN` «Copiado tal cual, con los caracteres ocultos que señalan los avisos.» The inspector now has its own `Scaffold` with a `SnackbarHost`, built like `QrTotpImportScreen` (`BackScaffold` has no snackbar).
   - «Copiar» shows the notice when the text has hidden characters: `hasHiddenChars(text)`, or the link's `HIDDEN_CHARS` signal, which also covers a percent-encoded host.
   - «Copiar contraseña» shows it when the password itself has them.
   - The decision is pure: `QrView.copyNotice` and `secretCopyNotice` (`QrView.kt:192-198`). The copy still goes through `copySensitive` with the raw text, never stripped.
   - I did not use the suggested «…marcados arriba», because a percent-encoded character is not shown as a mark.
   - Test: `copyingSaysWhenHiddenCharactersGoWithIt`, including that an emoji with U+FE0F gives no notice.
6. **Clearing a secret result.** My call: **both** ways (`QrInspectScreen.kt:131-144`). `QrView.carriesSecret` (= `textHidden`) is true only for a Wi-Fi network with a password and for `otpauth://` / `otpauth-migration://` (test `onlyTheKindsWithASecretAreClearedOnLeaving`).
   - **On ON_STOP:** `LifecycleEventEffect(ON_STOP)` drops it when the app stops (Home, screen off, another app). The scanner and the picker stop the activity too, which is harmless: their result is applied after ON_START, because ActivityResult delivers from STARTED.
   - **On vault lock:** a collector on `store.state` (Unlocked → not Unlocked, after `drop(1)`) drops it when the vault locks inside the app.
   - **While away:** a picked image that finishes decoding while the app is stopped is not shown if it carries a secret (`show()` checks `isAtLeast(STARTED)`).

## Verification (once, on the final tree)

- `QrViewTest` 19/19 and `ToolTextsTest` 10/10. The new tests did not compile before the change: `hostReading`, `copyNotice` and `carriesSecret` did not exist.
- `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: exit 0, **800 tests, 0 failures**.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`: **BUILD SUCCESSFUL in 3m 54s**, with 800 unit tests and 0 failures.
- `grep -n "ACTION_VIEW\|startActivity" app/src/main/java/app/seguridad/ui/screens/QrInspectScreen.kt`: empty.

## Non-annex texts, changed or added in this round (for the Abogado Digital)

- `QR_SCHEME_NONE` «Sin indicar» (shortened).
- `qrHostReading` «Se lee así: «…»» (new). It reuses the annex punycode line's wording.
- `QR_COPIED_WITH_HIDDEN` «Copiado tal cual, con los caracteres ocultos que señalan los avisos.» (new).
- `QR_FIELD_CONNECTS_TO` «Se conecta a» was removed.

## Revisión experto-seguridad-cripto (round 1)

- **Homograph emphasis (UTS #39 / IDN spoofing):** what stands out is now the ASCII name actually resolved (`QrView.kt:228-232`, `QrInspectScreen.kt:268-276`). The look-alike Unicode form is secondary text (`QrInspectScreen.kt:277-279`) and is also named by the annex punycode alert.
- **Secret lifetime:**
  - Wi-Fi passwords and 2FA keys leave the screen when the app stops or the vault locks (`QrInspectScreen.kt:131-135`), and are not shown after a decode that finished while away (`:142`).
  - Hand-offs that were never taken are cleared when either target screen goes away (`PkActionsScreen.kt:516`, `QrInspectScreen.kt:356`).
- **No silent data loss or alteration:** the copy is byte-for-byte what the QR holds, and the user is told when it carries hidden characters (`QrInspectScreen.kt:297`, `:328`; `QrView.kt:194`).
- **No new permissions, components, dependencies, connections or logs.** The changes are limited to the five files above.
