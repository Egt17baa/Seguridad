# Task 3 report: Compartir y guardar (temporal, FileProvider, HEIC)

**Status:** DONE. Commit `2638d11` «1.3.0: compartir imágenes con un FileProvider no exportado» (not pushed).

## What was implemented

- `tools/share/ShareTemp.kt` (pure, JVM-tested): `SHARE_DIR = "compartir"`, `SHARE_KEEP_MS = 10 * 60_000L`, and `class ShareTemp(cacheDir, now)`.
  - `prepare(name, bytes)`: the name must fully match `foto-limpia\.(jpg|png|webp)|imagen-tachada\.(jpg|png)`, or it throws `IllegalArgumentException` before anything touches the disk. It then empties `compartir/` (recursively) and throws `IOException` if anything is left, so there are never two files. It writes only that file, deleting it if the write fails, and records the time in memory.
  - `onReturn()`: keeps the copy only if `0 <= now - preparedAt < SHARE_KEEP_MS`. It wipes when 10 min or more have passed, when the clock went backwards (fails closed), or when nothing was prepared in this process (a stale leftover from an earlier process).
  - `wipeAll()`: empties `compartir/` and never throws (it runs at start).
  - All three methods are `@Synchronized`, because prepare (the screen, on IO) and onReturn (appScope IO) can run on different threads.
- `tools/ImageLimits.kt` (pure): `MAX_IMAGE_BYTES = 50 * 1024 * 1024` and `MAX_REDACT_PIXELS = 24_000_000`, for Tasks 4 and 5.
- `android/ImageShare.kt`: `shareImage(context, file, mime)` does `FileProvider.getUriForFile` with authority `${packageName}.compartir`, sends `ACTION_SEND` with `EXTRA_STREAM`, `FLAG_GRANT_READ_URI_PERMISSION` and `ClipData.newRawUri`, and opens `Intent.createChooser(send, null)`. It never touches `VaultLock.keepOpenOnce`. Exceptions (no receiver, file outside the provider) go to the caller, so Task 4 decides the message.
- `android/HeifToJpeg.kt`: `heifToJpeg(bytes)`.
  - API < 28 throws `PhotoFailure(PHOTO_UNSUPPORTED)`.
  - Otherwise it runs `ImageDecoder` on `ByteBuffer.wrap(bytes)` with the software allocator and no partial images. Sizes <= 0 throw `PHOTO_DAMAGED`. Images above `MAX_REDACT_PIXELS` get `setTargetSize` scaled by sqrt(max/(w*h)), so they decode at 24 MP or less.
  - Then `compress(JPEG, 95)`, `recycle()` and `PhotoCleaner.clean` (which drops the JFIF APP0).
  - Any `Exception` or `OutOfMemoryError` becomes `PhotoFailure(PHOTO_DAMAGED)`.
- `res/xml/share_paths.xml`: only `<cache-path name="compartir" path="compartir/"/>`.
- `AndroidManifest.xml`: `androidx.core.content.FileProvider`, `authorities="${applicationId}.compartir"`, `exported="false"`, `grantUriPermissions="true"`, plus meta-data `FILE_PROVIDER_PATHS` pointing to `@xml/share_paths`. No new permission. `androidx.core` was already a dependency.
- `AppContainer`: new required parameter `cacheDir: File` (no default) and `val shareTemp = ShareTemp(cacheDir, System::currentTimeMillis)`.
- `SeguridadApp`: passes `cacheDir`. `onCreate` launches `shareTemp.wipeAll()` in `appScope` on `Dispatchers.IO`.
- `MainActivity.onStart`: `appScope.launch(Dispatchers.IO) { shareTemp.onReturn() }`.
  - **My call: posted to IO, not on the main thread.** `prepare` may be writing up to 50 MB under the same lock, and `onStart` must never wait on it.
  - Ordering is safe: the decision is taken under the lock with the current time, so a fresh `prepare` is never wiped by a late `onReturn`.
- `AppContainerTest`: passes `tmp.newFolder("cache")`.
- `ShareTemp` is never wiped on lock. The existing `wipePkTemp(cacheDir)` only deletes its own `PK_OPEN_TEMP_DIR` (vault/PkActions.kt:494-496), and the start-time `dec*.tmp` cleanup only matches top-level files.

## TDD evidence

- **RED:** wrote `ShareTempTest` first. `gradle -p jvm-check test --tests 'app.seguridad.tools.share.*'` failed with `:compileTestKotlin FAILED`, `ShareTempTest.kt:17:32 Unresolved reference 'ShareTemp'` and `:18:44 Unresolved reference 'SHARE_DIR'`.
- **GREEN:** after the implementation, all 10 tests PASSED:
  - `onlyOneFileAtATime`, `prepareAlsoWipesLeftoversItDidNotWrite`;
  - `returnBeforeTenMinutesKeepsIt` (9 min 59 s), `returnAfterTenMinutesWipes` (exactly 10 min, then a second return on nothing);
  - `returnWithNothingPreparedWipesLeftoversAndNeverFails`, `clockGoingBackwardsWipes`;
  - `wipeAllEmptiesTheFolder` (also: does not touch files outside `compartir/`);
  - `rejectsOtherNames` (`../x`, `IMG_2026.jpg`, `foto-limpia.exe`, `.jpeg`, `imagen-tachada.webp`, `.JPG`, trailing or leading `/`, `sub/`, `../foto-limpia.jpg`, NUL, empty, no extension, `.jpg`; nothing written, nothing outside);
  - `acceptsTheFixedNames`, `constants`.
- I strengthened the "nothing prepared" test after GREEN to cover a stale leftover. It passed on the existing code: that behaviour was designed in from the start.

## Verification (full, once before commit)

- `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL, 683 tests PASSED, 0 failed.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline` (ANDROID_HOME=/opt/android-sdk): BUILD SUCCESSFUL. ShareTempTest has 10/10 and AppContainerTest 3/3 with 0 failures. `androidx.fragment:fragment 1.9.1 (>= 1.3.0)`.
- Lint: 11 warnings, all from before this task and none in the files I changed (DiscouragedApi on SecureCaptureActivity's screenOrientation, ObsoleteSdkInt on mipmap-v26, and 8 GradleDependency/NewerVersionAvailable).

### aapt2 (latest build-tools, debug APK)

```
E: provider (line=82)
  A: android:name="androidx.core.content.FileProvider"
  A: android:exported(0x01010010)=false
  A: android:authorities(0x01010018)="io.github.egt17baa.seguridad.compartir"
  A: android:grantUriPermissions(0x0101001b)=true
    E: meta-data
      A: android:name="android.support.FILE_PROVIDER_PATHS"
      A: android:resource=@0x7f120002
```
`res/xml/share_paths.xml` in the APK has one `cache-path name="compartir" path="compartir/"`.

`aapt2 dump permissions`: INTERNET, CAMERA, USE_BIOMETRIC, USE_FINGERPRINT and androidx's DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. Nothing new: no ACCESS_MEDIA_LOCATION and no READ_MEDIA_IMAGES. There are 3 `exported=true` entries, all from before this task (MainActivity, the debug-only Compose PreviewActivity, and the profileinstaller receiver).

## Files changed

- New:
  - app/src/main/java/app/seguridad/tools/share/ShareTemp.kt
  - app/src/test/java/app/seguridad/tools/share/ShareTempTest.kt
  - app/src/main/java/app/seguridad/tools/ImageLimits.kt
  - app/src/main/java/app/seguridad/android/ImageShare.kt
  - app/src/main/java/app/seguridad/android/HeifToJpeg.kt
  - app/src/main/res/xml/share_paths.xml
- Modified:
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/app/seguridad/AppContainer.kt
  - app/src/main/java/app/seguridad/SeguridadApp.kt
  - app/src/main/java/app/seguridad/MainActivity.kt
  - app/src/test/java/app/seguridad/AppContainerTest.kt

## Self-review and concerns

- `heifToJpeg` has no tests (android/, as agreed). It needs a device check in Task 4 or the final QA with a real HEIC, including an image above 24 MP.
  - `PhotoCleaner.clean` is outside the try. Its own `PhotoFailure` passes straight through, and it only throws `PhotoFailure`.
  - The `PhotoFailure(PHOTO_DAMAGED)` thrown inside the header listener is caught by the generic `catch (Exception)` and rethrown with the same message.
- `heifToJpeg` takes the whole byte array. The caller (Task 4) must enforce `MAX_IMAGE_BYTES` before reading.
- `shareImage` throws on failure (ActivityNotFound, IllegalArgumentException), unlike `shareText`, which returns Boolean. Task 4 should catch and show a Spanish message. The chooser title is null, so the system shows its own localized title, because the brief's signature has no title parameter.
- `ShareTemp` messages (`IllegalArgumentException`/`IOException`) are internal and in Spanish. Task 4 should map them to annex text and never show them raw.
- `onReturn` uses wall-clock time (`System::currentTimeMillis`, per the brief). A backwards clock change wipes early, which fails closed. A forward jump also wipes early. Both are harmless.
- MIME type is the caller's job (`image/jpeg|png|webp`, matching the extension).

## Revisión experto-seguridad-cripto

Threat model: another app on the phone, the app the user shares to, and a crafted HEIC as untrusted input.

- **Exposed component, minimal:**
  - The provider is not exported and only shares by grant (AndroidManifest.xml:38-46; aapt2: `exported=false`, `grantUriPermissions=true`).
  - Paths are limited to `cache-path compartir/` (res/xml/share_paths.xml:4). It never exposes `files/` (the vault) or the rest of the cache (the decrypted `dec*.tmp`, PK temp).
  - No new permissions (aapt2 dump permissions above). CWE-926 / MASVS-PLATFORM-1.
- **Least-privilege grant:** read-only, one URI, for the chosen app only. That is FLAG_GRANT_READ_URI_PERMISSION plus ClipData (android/ImageShare.kt:23-24), with no write flag. No `keepOpenOnce` (ImageShare.kt:18-26), so leaving the app locks the vault as usual.
- **Path traversal / name leaks:**
  - The fixed names are checked with a full-match regex before any disk access (tools/share/ShareTemp.kt:13, :35). There is no caller-controlled path component: `../`, `/`, NUL and case variants are rejected (ShareTempTest `rejectsOtherNames`). CWE-22.
  - The names are neutral (no date or person), as the design asks.
- **Data lifetime:**
  - One file at a time, and `prepare` fails closed if it cannot clear (ShareTemp.kt:36, :76-80).
  - A failed write leaves no partial file (ShareTemp.kt:39-44).
  - The copy is wiped at start (SeguridadApp.kt:38) and on return after 10 minutes or more, or if the clock went backwards (ShareTemp.kt:54-60; MainActivity.kt:50).
  - It is never wiped on lock, by design. The existing lock wipe is scoped to its own folder (vault/PkActions.kt:494-496).
- **Untrusted input (HEIC):**
  - The decode is capped at 24 MP through setTargetSize (android/HeifToJpeg.kt:32-34). Width and height are widened to Long before multiplying, so they cannot overflow (:29-30).
  - Zero or negative sizes are rejected (:31). There are no partial images (:28).
  - Every Exception and OOM becomes PHOTO_DAMAGED (:45-48). API < 28 gives PHOTO_UNSUPPORTED (:22).
  - The output goes through the structural cleaner (:50), so even the encoder's JFIF is dropped.
- **No logs, no network, no new dependencies:**
  - grep finds no `Log.` or `println` in the new files.
  - `FileProvider` comes from `androidx.core`, already declared.
  - checkLicense passes.
- **Crypto:** none touched. No formats changed. Randomness is not used.
