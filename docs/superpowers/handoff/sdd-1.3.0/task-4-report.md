# Task 4 report: pantalla «Limpiar foto»

Commit: `cbd6482 1.3.0: pantalla Limpiar foto` (not pushed).

## Implemented

- `app/src/main/java/app/seguridad/tools/ToolTexts.kt`: every annex text (Limpiar foto, Tachar, Inspector de QR, PIN y códigos, Respuestas inventadas, StrongBox y pendientes) as `const val`. Texts with placeholders are functions (`photoDoneText`, `photoGpsLine`, `qrAlertsTitle`, `qrPunycode`, `qrUserInfo`, `qrPort`, `qrScheme`, `qrBidi`, `pinText`). Called with the annex's own placeholder (`"[lista]"`, `"N"`, `"…"`, `"[host]"`…), each one gives the annex text exactly. `PHOTO_DAMAGED` and `PHOTO_UNSUPPORTED` are referenced from `PhotoReport.kt`, not copied.
  - `PHOTO_FOUND_TEXTS: Map<Found, String>` follows the annex order. `photoFoundLines(report)` swaps in `photoGpsLine` when there are coordinates, and always includes the ICC line when `ICC_KEPT` is found.
  - `photoGpsLine`: rounds to 2 decimals again, uses a decimal comma and the minus sign «−» (U+2212, as in the annex), and never shows «−0,00».
  - `photoHasSomethingToRemove` (ignores `ICC_KEPT`) and `photoDoneMessage(report?)`. `photoDoneMessage` gives «Copia lista, sin estos datos: …» with short names joined by «; ». It gives the «Si no hay nada que quitar» text when nothing was removed, and `PHOTO_DONE_ALL` for HEIF (null report).
  - `ALL_TOOL_TEXTS` holds everything.
- `app/src/main/java/app/seguridad/ui/screens/PhotoCleanScreen.kt`:
  - Before picking: the explanation, then «Elegir foto» (`OpenDocument`, `image/*`, `launchOrError`).
  - Read on IO with the size limit. Detect the format, then clean on `Dispatchers.Default`: HEIF goes to `heifToJpeg`; JPEG, PNG and WebP go to `inspect` and then `clean`; anything else gets `PHOTO_UNSUPPORTED`. Only the cleaned bytes stay in memory, and the original is only ever opened for reading.
  - Shows the «Lo que trae la foto» card (or the HEIC notice), «Si no hay nada que quitar», and the file-name note.
  - «Guardar copia» uses `CreateDocument` with the right MIME (jpeg, png or webp) and the name `foto-limpia.<ext>`. It writes with `"wt"`, and `discardDocument` runs on failure.
  - «Compartir» runs `ShareTemp.prepare` on IO, then `shareImage`. On failure it calls `wipeAll` and shows `PHOTO_SHARE_FAILED`.
  - After saving or sharing: «Copia lista…».
- `ToolsScreen.kt`: `Tool("tools/photo", "🧽", "Limpiar foto", "Quita la ubicación y otros datos ocultos")`, using the `PHOTO_TOOL_*` constants. `AppNavHost.kt`: route `tools/photo` → `PhotoCleanScreen(container.shareTemp, onBack)`.
- Task 3 follow-ups:
  - (a) comment in `SeguridadApp.onCreate` that the async `wipeAll` cannot race `prepare` in practice;
  - (b) KDoc on `shareImage` saying it needs an Activity context;
  - (c) the chooser title is now «Compartir».

## Tests and results

- `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL, 690 tests, 0 failures.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline` (ANDROID_HOME=/opt/android-sdk): BUILD SUCCESSFUL, 690 unit tests, 0 failures. Lint reports 12 issues in total, none in the files of this task (I checked the report for PhotoCleanScreen, ToolTexts and ImageShare).
- `ToolTextsTest`, 7 tests:
  - `everyAnnexTextIsInTheListVerbatim`: parses the annex itself, finds the top-level «…» quotes (nested ones kept) between «## Limpiar foto» and «## Cambios en los documentos», and checks each one is in `ALL_TOOL_TEXTS` exactly. It skips the rule line «Los acortadores…» and the instruction word «limpio».
  - `noForbiddenWords`.
  - `exceptionsAreTheAnnexOnes`.
  - `gpsIsRoundedWithSpanishDecimalComma`.
  - `foundLinesFollowTheAnnex`, which checks the ICC line and GPS without coordinates.
  - `nothingToRemoveIgnoresTheKeptProfile`.
  - `doneListNamesWhatWasRemovedButNotTheProfile`.

## TDD evidence

- RED: I wrote `ToolTextsTest` first, before `ToolTexts.kt` existed. `:compileTestKotlin FAILED` (unresolved references).
- GREEN, first run: 6 of 7 passed. The verbatim test flagged only «limpio», the annex's instruction («sin ✓ ni «limpio»»), not a UI text. I excluded it explicitly and every test passed. Every annex text matched verbatim on the first run.

## Files changed

- A `app/src/main/java/app/seguridad/tools/ToolTexts.kt`
- A `app/src/main/java/app/seguridad/ui/screens/PhotoCleanScreen.kt`
- A `app/src/test/java/app/seguridad/tools/ToolTextsTest.kt`
- M `app/src/main/java/app/seguridad/ui/screens/ToolsScreen.kt`
- M `app/src/main/java/app/seguridad/ui/AppNavHost.kt`
- M `app/src/main/java/app/seguridad/android/ImageShare.kt`
- M `app/src/main/java/app/seguridad/SeguridadApp.kt`

## Self-review

- `catch (e: Exception)` also catches `CancellationException` when the screen leaves mid-task. This is the same pattern as `FileHashScreen`, and it only sets state that is then dropped.
- While busy, the pick, save and share buttons are disabled, so two jobs never overlap.
- If the activity is recreated while `CreateDocument` is open, the copy is gone: the document is discarded and the screen shows `PHOTO_LOST` (the same pattern as `TextCryptoScreen`).
- `keepOpenOnce`: the brief says `launchOrError` sets it, but it does not (`core/PickerLaunch.kt:15`). The other tool pickers do not set it either (`FileHashScreen`). This screen does not use the vault, so leaving it for the picker locks the vault as usual, which is the conservative choice. I left it like that.

## Concerns (texts not in the annex; for Abogado Digital review)

The annex has no text for these cases, so I added neutral ones that only state facts. They live in ToolTexts, and the forbidden-words test covers them:

- `PHOTO_TOO_BIG` «La foto ocupa más de 50 MB. No se ha guardado nada.»
- `PHOTO_SAVE_FAILED` «No se ha podido guardar la copia. No se ha guardado nada.»
- `PHOTO_SHARE_FAILED` «No se ha podido compartir la copia.»
- `PHOTO_LOST` «No se ha guardado: vuelve a elegir la foto.»
- `PHOTO_OTHER` «Otros datos ocultos del archivo», for `Found.OTHER`.
- `PHOTO_GPS_NO_COORDS` «Ubicación GPS», for GPS in XMP without coordinates we can read.
- The short names inside «[lista]» (`PHOTO_REMOVED_NAMES`), and `PHOTO_DONE_ALL` «todos los que guardaba el archivo original» for HEIC.
- `PHOTO_PICK` «Elegir foto», `PHOTO_PICK_OTHER` «Elegir otra foto», `PHOTO_FOUND_TITLE` «Lo que trae la foto» and `PHOTO_ANALYSING` «Analizando la foto…».

Other points:

- The forbidden-words test lets «Chip seguro (StrongBox)» and «Este móvil tiene chip seguro…» through. They are annex texts that name the hardware, and they would break the literal «seguro» rule. The test removes «chip seguro» before checking.
- Memory: `readAtMost` on a file close to 50 MB can briefly take about 64 MB (buffer) plus the copy. `OutOfMemoryError` is caught on screen and shows `PHOTO_DAMAGED`. When the provider reports the size, the file is refused before any read.

## Revisión experto-seguridad-cripto

Threat model: a picked image that is malformed or huge (untrusted input), leaking metadata or the original, leaking internal messages, and the receiving app.

- **Size limit before loading** (CWE-400): `ui/screens/PhotoCleanScreen.kt:89` refuses on the size the provider reports. `:90` reads at most `MAX_IMAGE_BYTES + 1` and `:91` refuses if more arrives. `:161` catches `OutOfMemoryError`, which the cleaner does not catch.
- **Format routing, failing closed:** `PhotoCleanScreen.kt:97` sends only `PhotoFormat.HEIF` to `heifToJpeg`, and `:101` turns an unknown format into `PhotoFailure(PHOTO_UNSUPPORTED)`. JPEG, PNG and WebP go through `PhotoCleaner.clean`, which checks its own output (`tools/photo/PhotoCleaner.kt:35`).
- **No internal messages:** `PhotoCleanScreen.kt:155-163` shows `PhotoFailure.message` (always an annex text, `PhotoReport.kt:25`) or `PHOTO_DAMAGED`. The save fails with `PHOTO_SAVE_FAILED` (`:135`) and the share with `PHOTO_SHARE_FAILED` (`:214`). Exceptions from `ShareTemp` or `shareImage` never reach the screen text.
- **No half-written files:** `PhotoCleanScreen.kt:133` runs `discardDocument` on a write error. `:212` runs `wipeAll` when the share fails, so a copy that was never handed over does not wait 10 minutes.
- **Original untouched:** the original is only opened with `openInputStream` (`:90`). Writes go to the `CreateDocument` target (`:129`) or to `ShareTemp` (`:206`).
- **Fixed output names:** `foto-limpia.${c.ext}`, with `ext` limited to jpg, png or webp (`:97-100`, `:196`, `:206`). `ShareTemp` also enforces this with its regex (`tools/share/ShareTemp.kt:13`).
- **Heavy work off the main thread:** IO for the read, write and prepare (`:129`, `:153`, `:206`). Default for inspect, clean and HEIF (`:154`).
- **No logs:** a search finds no `Log.` or `println` in the new files.
- **No new permissions, components, dependencies or network.** The `FileProvider` is the one from Task 3. `shareImage` grants read access to the single URI (`android/ImageShare.kt:20-28`). The KDoc about the Activity context is at `:18`, and the chooser title «Compartir» at `:28`.
- **Minimal GPS precision:** `tools/ToolTexts.kt:113-120` rounds to 2 decimals (about 1 km) and shows nothing finer.
- **ICC kept and said so:** `ToolTexts.kt:62` and `:122`, checked by `ToolTextsTest.foundLinesFollowTheAnnex`.
- **Annex texts verbatim, with no forbidden words:** `ToolTextsTest` parses the annex itself, so the code cannot drift from the document unnoticed.
- **Start-time wipe:** `SeguridadApp.kt:38` documents why the async `wipeAll` does not race `prepare`. `ShareTemp` is `@Synchronized`.
- **No cryptography in this task:** nothing to check against `cripto-moderna.md`.

Findings: none Critical or Important. Minor: the non-annex texts above need Abogado Digital review.

---

# Fix round 1 (commit `c2432d4 1.3.0: pantalla Limpiar foto (revisión 1)`, not pushed)

## Changes

- **IMPORTANT 1, HEIC «Copia lista» promised too much.** Fixed.
  - `PhotoCleanScreen.kt:110-114`: after `heifToJpeg`, `PhotoCleaner.inspect(jpeg)` analyses the converted JPEG. `CleanCopy` now carries a non-null `report` and a `converted` flag.
  - The card (`:278-284`) shows the HEIC notice, then the converted JPEG's lines, so `PHOTO_ICC_KEPT` appears when the encoder embedded a profile. It never shows «No hemos encontrado…» for a converted photo.
  - `ToolTexts.kt:86`: `PHOTO_DONE_ALL` («todos…») is gone, replaced by `PHOTO_DONE_CONVERTED` «Copia lista, sin los datos ocultos del archivo original. El original no se ha tocado y sigue teniéndolos: comparte la copia, no el original.» This text is not in the annex: flagged for the Abogado Digital.
  - `photoDoneMessage(report, converted)` (`:146`) appends « Perfil de color: se conserva para que no cambien los colores.» when `ICC_KEPT` is found.
- **Minor 1.** When there was nothing to remove, the done message is `PHOTO_DONE_SHORT` «Copia lista.» (`ToolTexts.kt:91`, not in the annex, flagged), not the card's text again.
- **Minor 2, cancellation.** `CancellationException` is now caught first and rethrown in all three jobs.
  - Pick (`PhotoCleanScreen.kt:183`): `ensureActive()` runs between reading and cleaning (`:181`) and between the steps of `cleanCopy` (`:107`, `:113`).
  - Save (`:147-157`): a `written` flag keeps a fully written copy; only a document that was never written is discarded.
  - Share (`:238-252`): the cleanup runs under `NonCancellable + Dispatchers.IO`.
- **Minor 3.** New test `ToolTextsTest.everyTextConstantIsChecked`. It uses reflection over `ToolTextsKt`:
  - every public static `String` constant must be in `ALL_TOOL_TEXTS`;
  - every private one (the GPS prefix and suffix) must be inside one of its texts;
  - the values of both maps must be in the list too;
  - the test fails if it finds fewer than 60 constants, so it cannot pass vacuously.
- **Minor 4.** `ImageShare.kt:29` uses `SHARE` from ToolTexts.
- **Minor 5, my call.** A failed or cancelled share deletes only the file this attempt prepared, through the new `ShareTemp.discard(file)` (`ShareTemp.kt:68`, `@Synchronized`). `discard` does nothing for files outside `compartir/` and never throws. `wipeAll` is no longer called from the screen. Any earlier copy was already removed by `prepare` itself (one file at a time), so `discard` never touches another app's pending read beyond what `prepare` already did.
- **Minor 6, AVIF before API 31.**
  - `PhotoReport.kt` now reads the brands once, in a private `ftypBrands(head)` (`:39`). `detectFormat` uses it and behaves the same, and its tests are unchanged and green.
  - New `isAvif(head)` (`:64`): true for an `avif` or `avis` brand with no HEVC brand (`heic`, `heix`, `hevc`, `heim`, `heis`, `hevx`).
  - `HeifToJpeg.kt:25`: on API < 31, an AVIF throws `PhotoFailure(PHOTO_UNSUPPORTED)` instead of failing to decode and showing `PHOTO_DAMAGED`.

## Covering tests (RED, then GREEN)

- RED: compilation failed on the new tests (unresolved `PHOTO_DONE_SHORT`, `PHOTO_DONE_CONVERTED`, the `converted` parameter, `isAvif` and `discard`).
- `ToolTextsTest`, now 9 tests:
  - `doneListNamesWhatWasRemovedButNotTheProfile`, updated for «Copia lista.»;
  - new `convertedPhotoNeverClaimsEverythingWentAndNamesTheKeptProfile`, which also checks that «todos» is absent;
  - new `everyTextConstantIsChecked`.
- `PhotoCleanerTest.avifIsToldApartFromHeic`: avif and avis major brands, avif as a compatible brand, HEIC, a mix with a HEVC brand, mif1 alone, JPEG, empty input, and a box longer than the head.
- `ShareTempTest.discardRemovesOnlyThePreparedFile`: a file outside `compartir/` is left alone, the prepared file is deleted, and a second discard does not fail.

## Commands and output

- `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL, 694 tests, 0 failures.
- `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL in 4m 59s, 694 unit tests, 0 failures. Lint reports 12 issues, the same as before, none in the touched files.

## Revisión experto-seguridad-cripto (round 1)

- **Accurate claims:** the converted copy is analysed with the same whitelist reader (`PhotoCleanScreen.kt:114`). The screen states only what it checked; the absolute «todos» is gone (`ToolTexts.kt:86`).
- **Fails closed with the right text:** AVIF before API 31 shows «no se admite» (`HeifToJpeg.kt:25`), detected without reading past the `ftyp` box (`PhotoReport.kt:39-50`).
- **No leftovers and no lost copies:**
  - when the screen is cancelled, `discardDocument` runs only if nothing was written (`PhotoCleanScreen.kt:157`);
  - a share copy that was never handed over is deleted under `NonCancellable` (`:239`);
  - `ShareTemp.discard` is limited to `compartir/` (`ShareTemp.kt:68-72`).
- **Work stops when the screen leaves:** `ensureActive` runs between steps (`PhotoCleanScreen.kt:107`, `:113`, `:181`), so 50 MB are not processed for a screen that is gone.
- **Text governance:** `everyTextConstantIsChecked` makes `noForbiddenWords` cover every constant in ToolTexts.

Findings: none Critical or Important. The new non-annex texts (`PHOTO_DONE_CONVERTED`, `PHOTO_DONE_SHORT`) join the list for the Abogado Digital.
