# Task 5 report: Tachar en imagen

**Status:** DONE_WITH_CONCERNS. Commit `7a57de2 1.3.0: Tachar en imagen`, on top of `c2432d4`. Not pushed.

## What was built

### Pure code: `tools/redact/Redaction.kt` (JVM-tested)

- `Box(left, top, right, bottom)`: left and top are included, right and bottom are not.
- `boxFromDrag(...)` (`:27`):
  - converts the drag to image coordinates, multiplying before dividing so whole-number scales stay exact;
  - orders the corners, so a drag in any direction works;
  - clips to the image, then rounds outward: `floor` for the start (`:39`), `ceil` for the end (`:41`);
  - returns `null` when the part inside the image is under 1 image pixel wide or high (`:37`), and for non-finite or non-positive sizes or positions.
- `boxToView(...)`: the same box in view pixels. The preview paints this, so it uses exactly the boxes the copy uses.
- `RedactHistory`: `add`, `undo`, `redo`, `boxes` (a copy), `canUndo` and `canRedo`. A new box clears whatever could be redone.
- `redactOutputFormat` (`:85`): PNG and WebP give PNG; JPEG and HEIF give JPEG.
- `redactFileName` and `redactMime`: the fixed names `imagen-tachada.png` and `imagen-tachada.jpg`, with their types.
- `redactTargetSize` (`:99`): the `setTargetSize` for ImageDecoder. It keeps the shape, never goes over `MAX_REDACT_PIXELS` (including 1-px strips) and is null when the image already fits.
- `redactSampleSize` (`:117`): the power-of-two `inSampleSize` for Android 8. It rounds each side up, as libjpeg does.
- `redactMegapixels`: rounded megapixels, never 0.

### Android: `android/RedactRender.kt`

**`decodeForRedaction(bytes)`** (`:44`):
- The format comes from the content.
- It throws `PHOTO_UNSUPPORTED` for:
  - an unknown format (`:45`);
  - HEIF before API 28 (`:46`);
  - AVIF before API 31 (`:47`).
- **API 28 and later:** ImageDecoder, which applies the orientation. It uses `ALLOCATOR_SOFTWARE` (`:75`), no partial images, and `setTargetSize` above 24 MP (`:81`).
- **API 26–27:** BitmapFactory.
  - It reads the bounds, then decodes with `inSampleSize` (`:94`).
  - It takes the orientation from `PhotoCleaner.inspect(bytes).orientation` (`:100`), JPEG only, and applies it with a `Matrix` (all 8 EXIF values).
  - If `inspect` fails, it falls back to orientation 1. This only affects how the image looks, because the copy is made from the same bitmap the preview shows.
- After decoding it checks the size again: a bitmap over 24 MP is recycled and gives `PHOTO_DAMAGED` (`:54`).
- Any exception or out-of-memory error gives `PHOTO_DAMAGED`. A half-decoded bitmap is recycled.

**`renderRedacted(source, boxes, format)`** (`:137`):
1. It creates a new ARGB_8888 bitmap (`:146`) and draws the source on it (`:149`).
2. It then draws every box with `Paint` black, `alpha = 255`, `Style.FILL` and `isAntiAlias = false`, on whole-pixel `Rect`s.
3. **Extra check:** it reads every pixel under every box back, one row at a time. If any pixel is not `0xFF000000`, nothing is produced (`:160`).
4. It encodes PNG, or JPEG at quality 95 (`:162`).
5. `PhotoCleaner.clean` runs on the output (`:176`), which fails closed, and the output format must match (`:178`).
6. The temporary bitmap is always recycled. Any failure gives `PhotoFailure(PHOTO_DAMAGED)`.

### Screen: `ui/screens/RedactScreen.kt`

- **Texts:** the annex Explicación, and «Lo que no protege» (`REDACT_LIMITS`) in a card that is always visible (`:298`). «Si se reduce» appears when the image was reduced.
- **Reading the file:** it uses the same bounded `readPhoto` as Limpiar foto. I changed that function from `private` to `internal` in `PhotoCleanScreen.kt` (`:262`). The 50 MB limit is checked by the reported size and again by `readAtMost`.
- **The bitmap:**
  - It is only in memory, in a `SourceHolder` that counts the copies in progress.
  - `close()` runs when the screen leaves (`DisposableEffect`, `:210`) and when another image is picked. It recycles the bitmap at once, or as soon as the last copy in progress gives it back (`:123-134`), so it is never recycled while a copy is drawing from it.
  - The preview is a separate bitmap of at most 2048 px a side (`:104`). The GPU may not be able to show a full 24 MP bitmap.
  - If the job is cancelled or fails after decoding, the decoded bitmap is recycled (`:284`).
- **Preview:**
  - It is fitted to the width and to at most 60 % of the window height, so the page can still be scrolled outside it.
  - The boxes are opaque black, from `boxToView` of the stored `Box` (`:435`).
  - The box being dragged is painted from the same `boxFromDrag` that will store it.
  - The box starts where the finger went down (`awaitFirstDown`), not where the touch slop was crossed.
- **Undo and redo:** two buttons.
- **Saving and sharing:**
  - Both stay disabled until there is at least one box (`:332`), so an unredacted copy cannot go out by mistake.
  - **Save** follows Limpiar foto. CreateDocument uses the fixed name. The copy is rendered from a snapshot of the boxes, then written; on failure the document is discarded (`:215-240`). If the screen is cancelled, the document is kept only if it was written in full. If the screen was rebuilt (process death), the message is `REDACT_LOST`.
  - **Share** works the same way: render, then `ShareTemp.prepare`, then `shareImage`. On failure or cancellation it discards only its own file, under `NonCancellable` (`:350`).
- **Where it appears:** `TOOLS` gets `Tool("tools/redact", "⬛", REDACT_TOOL_TITLE, REDACT_TOOL_SUBTITLE)`, which is the brief's exact strings through constants, and `AppNavHost` gets the route `tools/redact`.

### Carried fixes from Task 4

- **ShareTemp:**
  - `prepare` now stores `private var preparedFile` (`ShareTemp.kt:29`, `:49`).
  - `discard` acts only if `file === preparedFile` (`:75`), then clears `preparedFile` and `preparedAt`. `clear()` also clears it (`:96`).
  - It compares **by identity**, not with `==`, on purpose. The names are fixed, so the second prepare's `File` has the same path and is `equals` to the first. A path comparison would still delete the newer copy. The `compartir/` path check is implied: only `prepare` creates that object.
- **The long line:** `ToolTexts.kt:147` (`photoDoneMessage`, 128 characters) is wrapped. I also wrapped the 171-character `ALL_TOOL_TEXTS` line (formerly `:274`).

## Tests and results

- `RedactionTest`, 10 tests:
  - `dragIsScaledAndRoundedOutward`: the brief's `Box(400, 400, 800, 600)`, plus 2000 random views, images and drags. Each box must cover the whole drawn rectangle and less than 1 px more, stay inside the image and be at least 1 px.
  - `backwardsDrag`: all 3 reversed orders.
  - `clippedToImage`: past every edge, partly outside, entirely outside, and only along an edge.
  - `tinyIsNull`: a tap, 0.5 px, 0.9 px, exactly 1 px (not null), and zero, NaN and infinite sizes or positions.
  - `undoRedo`: includes empty undo and redo, redo cleared by a new box, and the snapshot being a copy.
  - `outputFormatByInput`: includes the names and types.
  - `previewShowsTheSameBoxAsTheCopy`.
  - `targetSizeKeepsUnderTheLimit`: includes 1×30 000 000 and 30 000 000×1, and «unos 24» for a 30 MP image.
  - `sampleSizeForAndroid8`: rounding up and powers of two, including `Int.MAX_VALUE`.
  - `megapixelsAreRoundedAndNeverZero`.
- `ShareTempTest.discardOfAnEarlierCopyLeavesTheNewerOne`: prepare, prepare again with the same name, discard the first. The second survives with its contents, and `onReturn` keeps it 9 minutes later (within 10 minutes of the second prepare). Discarding the second then removes it.
- `ToolTextsTest.downscaleTextIsTheAnnexOneAt24Megapixels`.
- `everyTextConstantIsChecked` and `noForbiddenWords` pass with the new constants.

**Commands:**
- `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL, 706 tests, 0 failures (694 before).
- `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL in 3m 26s, 706 unit tests, 0 failures.
  - Lint reports 12 issues, the same count as before. None is in the touched files.
  - Two `UseKtx` warnings in my files showed up on the first run. I fixed them with `createBitmap` and `scale` from androidx.core, which is already a dependency, and then ran the full verification again.
- `aapt2 dump permissions`: unchanged (INTERNET, CAMERA, USE_BIOMETRIC, USE_FINGERPRINT, and the not-exported dynamic receiver permission). No media or location permission.

## TDD evidence

- **RED 1:** compilation failed on the new tests: unresolved `Box`, `boxFromDrag` and `redactDownscaledText`, among others.
- **GREEN for Redaction and texts:** all `RedactionTest` and `ToolTextsTest` tests passed. At the same point the new ShareTemp test failed at runtime on the old `discard`: `discardOfAnEarlierCopyLeavesTheNewerOne FAILED: expected:<[imagen-tachada.png]> but was:<[]>`. That was the bug.
- **GREEN 2:** after the `preparedFile` identity guard, all share tests passed.

## Files changed

- New:
  - `app/src/main/java/app/seguridad/tools/redact/Redaction.kt`
  - `app/src/test/java/app/seguridad/tools/redact/RedactionTest.kt`
  - `app/src/main/java/app/seguridad/android/RedactRender.kt`
  - `app/src/main/java/app/seguridad/ui/screens/RedactScreen.kt`
- Modified:
  - `tools/ToolTexts.kt`
  - `tools/share/ShareTemp.kt`
  - `ui/AppNavHost.kt`
  - `ui/screens/ToolsScreen.kt`
  - `ui/screens/PhotoCleanScreen.kt` (`readPhoto` and `PhotoTooBig` are now `internal`)
  - `test/.../ToolTextsTest.kt`
  - `test/.../share/ShareTempTest.kt`

## Non-annex texts added (for the Abogado Digital, Task 16)

- `REDACT_TOOL_SUBTITLE` «Tapa con bloques negros lo que no quieras enseñar». This is from the brief and plan, and close to the Play listing line.
- `redactDownscaledText(n)`: the annex «Si se reduce» with the real number. At 24 it is the annex text exactly, and a test fixes that. It only differs on Android 8, where `inSampleSize` halves the sides and the result can be about 6–24 MP. Saying «unos 24» there would be false.
- `REDACT_PICK` «Elegir imagen», `REDACT_PICK_OTHER` «Elegir otra imagen».
- `REDACT_OPENING` «Abriendo la imagen…», `REDACT_RENDERING` «Preparando la copia…».
- `REDACT_UNDO` «Deshacer», `REDACT_REDO` «Rehacer».
- `REDACT_TOO_BIG` «La imagen ocupa más de 50 MB.»
- `REDACT_DAMAGED` «No se ha podido abrir esta imagen: el archivo está dañado o tiene un formato que no conocemos.» `PHOTO_DAMAGED` says «limpiar esta foto», which is wrong for this tool.
- `REDACT_COPY_FAILED` «No se ha podido crear la copia tachada. No se ha guardado nada.»
- `REDACT_LOST` «No se ha guardado: vuelve a elegir la imagen y a tachar lo que quieras tapar.»
- Reused: `PHOTO_UNSUPPORTED` (annex), `PHOTO_SAVE_COPY`, `SHARE`, `PHOTO_SAVE_FAILED`, `PHOTO_SHARE_FAILED` and `PHOTO_DONE_SHORT` «Copia lista.».

## Self-review

- Pure code is in `tools/redact`, so jvm-check compiles it. Android code is only in `android/` and `ui/`.
- Nothing is logged. A search for `Log.` and `println` in the new files finds nothing. All errors are fixed texts.
- The bitmap is never in saved state (`remember` only).
- The output names are fixed. ShareTemp rejects anything else, and `outputFormatByInput` checks that every input format maps to one of the two names.
- `ShareTemp.discard` still never throws and still never touches files it did not prepare. The old `discardRemovesOnlyThePreparedFile` test passes unchanged.

## Concerns

1. **Not tested on a device.** `renderRedacted` and the decoding are Android-only, with no instrumented test here. What it relies on:
   - the read-back check that every box pixel is black (fails closed);
   - `PhotoCleaner.clean` with the format check;
   - the gesture code (`awaitFirstDown`, touch slop, `drag` inside a `verticalScroll`). The child consumes events in the Main pass, so dragging on the image draws and does not scroll; this needs a manual check.

   I suggest a manual pass on the owner's phone: a PNG screenshot, a JPEG with orientation 6, a HEIC, and an image over 24 MP.
2. **Colour space.** The new bitmap is sRGB (the default of `createBitmap`). A Display-P3 HEIC or JPEG loses its wide gamut in the copy, which also gets no ICC profile from the encoder. This is a small colour change, in exchange for fewer bytes carried over. I did not use `source.colorSpace`.
3. **Android 8 orientation is applied for JPEG only.** This matches what ImageDecoder certainly applies on Android 9 and later. A PNG or WebP with EXIF orientation may look different across versions. It is cosmetic: the copy is always what the preview shows.
4. **Decompression bombs, CPU only.** A 50 MB PNG can declare a huge size. Memory is bounded by `setTargetSize` or `inSampleSize` plus the check after decoding, but inflating the whole stream can take a long time, and ImageDecoder cannot be interrupted. Limpiar foto's `heifToJpeg` has the same exposure. A cap on the declared size (for example ≥ 200 MP, enough for phone sensors) would need a ruling.
5. **`heifToJpeg` (Task 3/4)** computes its target size inline. For a 1-px-wide strip over 24 MP that size can exceed the limit, because the 1-px side is coerced up. `redactTargetSize` handles this case and could replace that inline code in a later task. I left it alone, as it is outside this task's scope.
6. The non-annex texts listed above are for the Abogado Digital.

## Revisión experto-seguridad-cripto

**Threat model.** The user shares a redacted image. The adversary is whoever receives it, with any tool, trying to recover:
- what was covered;
- metadata of the original file.

The original is never modified. Out of scope, and said on screen in `REDACT_LIMITS` (`ToolTexts.kt:166`): the original itself, context clues, and copies already sent.

**Covered pixels cannot be recovered** (anti-goal «se pueda recuperar lo que había debajo»):
- The covered pixels are replaced, not blurred: an opaque black FILL with alpha 255 and no anti-aliasing, drawn **after** the source onto a new bitmap (`RedactRender.kt:146-158`).
- Each box is then read back pixel by pixel, and nothing is encoded unless every one is `0xFF000000` (`:160`).
- There are no layers and no transparency in the output, and the original is not embedded. The output is encoded from pixels alone (`:162-164`).
- The JPEG DCT blocks that straddle a box edge mix only pixels that are visible anyway. The values under the box are already 0 before encoding.

**Never covers less than what was drawn:**
- floor and ceil, clipped to the image (`Redaction.kt:37-42`);
- property-tested on 2000 random cases (`RedactionTest.kt`);
- the preview paints the same `Box` (`RedactScreen.kt:435`).

**No metadata from the original:**
- The copy is a new bitmap.
- `PhotoCleaner.clean` (keep-list, verify, idempotence) runs on the output, plus a format check (`RedactRender.kt:176-178`).
- The file names are fixed (`Redaction.kt:91`), and ShareTemp enforces them by regex.

**Untrusted input:**
- The 50 MB limit is checked before reading (`PhotoCleanScreen.kt` `readPhoto`, used at `RedactScreen.kt:262`).
- The format is sniffed from the content (`RedactRender.kt:45`).
- The 24 MP limit holds before decoding (`:81`, `:94`) and again after (`:54`).
- Out-of-memory errors and exceptions map to fixed Spanish messages. Nothing is logged.

**No half-written files:**
- Save discards the document on failure, and on cancellation unless it was written in full (`RedactScreen.kt:215-240`).
- Share discards only its own prepared file (`:350`).
- `ShareTemp.discard` compares by identity, so it can no longer delete a newer copy that another app may still be reading, nor reset its 10-minute timer (`ShareTemp.kt:75-78`). The test is `discardOfAnEarlierCopyLeavesTheNewerOne`.

**Memory hygiene:**
- The decoded image lives only in memory and is recycled when the screen leaves, never while a copy is drawing from it (`RedactScreen.kt:123-134`, `:210`, `:284`).

**Hermetic:**
- No new permissions (aapt2 output above), connections, dependencies or exported components.
- `FLAG_SECURE` comes from MainActivity.

**No cryptography in this task:** nothing to check against `cripto-moderna.md`.

**Findings:** none Critical or Important. The minor items are concerns 2–5 above.

---

# Fix round 1 (commit `b89a9b5 1.3.0: Tachar en imagen (revisión 1)`, on top of `c801d5d`; not pushed)

## Changes

**IMPORTANT 1, the copy could lack a box the screen shows.** Fixed in two ways.

- **(a) Input is blocked while busy.**
  - `RedactCanvas` takes `enabled = busy == null` (`RedactScreen.kt:341`).
  - When it is disabled:
    - the gesture returns right after `awaitFirstDown`, without consuming the touch, so the page still scrolls (`:447`);
    - a drag that was in progress when it became disabled does not store a box (`:462`);
    - no box is painted while being dragged.
  - `onBox` also checks `busy == null` (`:342`).
  - Undo and Redo are disabled while busy (`:350`, `:355`), and their `onClick` checks it too.
- **(b) The boxes are compared again before anything goes out.** `stillMatches(p, snapshot)` (`:229`) requires `picture === p && p.history.boxes == snapshot`. It runs after `renderCopy` and before writing (`:244`) or before `ShareTemp.prepare` (`:382`). On a mismatch, `BoxesChanged` is thrown:
  - **save:** the document is discarded;
  - **share:** nothing was prepared yet, and `discardPrepared` is a no-op;
  - in both cases the screen shows `REDACT_CHANGED` (`:258`, `:395`) instead of «Copia lista.».

**Controller ruling, the declared size is checked before decoding.**
- `MAX_DECLARED_PIXELS = 100_000_000L`, `MAX_DECLARED_SIDE = 20_000` and `class ImageTooLarge` are in `tools/ImageLimits.kt:13-17`.
- The pure predicate `declaredSizeAllowed(w, h)` is in `Redaction.kt:134`. It requires each side in `1..20000` and `w × h ≤ 100 MP`, multiplied as Long.
- It is used in three places:
  - in the ImageDecoder header listener (`RedactRender.kt:87`), which throws before any pixel is inflated;
  - after BitmapFactory's bounds decode (`RedactRender.kt:100`);
  - in the `heifToJpeg` header listener (`HeifToJpeg.kt:38`).
- `ImageTooLarge` is rethrown unchanged, not mapped to «dañado» (`RedactRender.kt:64`, `HeifToJpeg.kt:52`).
- Messages: `REDACT_TOO_LARGE` in Tachar (`RedactScreen.kt:300`) and `PHOTO_TOO_LARGE` in Limpiar foto (`PhotoCleanScreen.kt:189`).
- I did not reuse PhotoFailure, because its contract says the message is only `PHOTO_DAMAGED` or `PHOTO_UNSUPPORTED`.

**Minor 3, the preview bitmap is recycled.** `Picture` now keeps `previewBitmap`. It is recycled:
- by `DisposableEffect(p)` in `RedactCanvas` (`:431`), once that preview leaves the composition, which happens on a re-pick and when leaving the screen. This is after the composition stops drawing it, never in the middle of a frame;
- by the screen's own `onDispose` (`:222`), for an image picked just as the screen leaves, before it was ever composed. Calling `recycle()` twice does nothing.

**Minor 4.** `import app.seguridad.tools.redact.Box as RedactBox` (`:82`), and every use of the redact type now says `RedactBox`.

Side effect: concern 5 from the first report (`heifToJpeg`'s target size for 1-px strips) is now unreachable, since no side can pass 20 000 px.

## New non-annex texts (for the Abogado Digital)

- `REDACT_TOO_LARGE` «La imagen es demasiado grande para editarla: pasa de 100 megapíxeles o de 20.000 píxeles de lado.»
- `REDACT_CHANGED` «Los bloques cambiaron mientras se preparaba la copia, así que no se ha guardado ni compartido. Vuelve a intentarlo.»
- `PHOTO_TOO_LARGE` «La foto es demasiado grande para convertirla: pasa de 100 megapíxeles o de 20.000 píxeles de lado. No se ha guardado nada.»

All three are in `ALL_TOOL_TEXTS`, and `everyTextConstantIsChecked` and `noForbiddenWords` pass.

## TDD

- **RED:** `RedactionTest.declaredSizeIsCheckedBeforeDecoding` did not compile (unresolved `MAX_DECLARED_PIXELS`, `MAX_DECLARED_SIDE` and `declaredSizeAllowed`).
- **GREEN** after the predicate. The test covers:
  - sides 20000 and 20001, in both directions;
  - 10000×10000 (exactly 100 MP) against 10000×10001 and 10001×10000;
  - 20000×5000 (exactly 100 MP at the side limit);
  - a real 50 MP sensor (8160×6120, allowed) and a 200 MP one (16320×12240, refused);
  - 0, negative values, `Int.MIN_VALUE`, `Int.MAX_VALUE` and 65536² (an Int overflow).
- The input gating and the snapshot check are Compose and Android code, which jvm-check cannot run. They go to the phone checklist.

## Verification

- `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL, 707 tests, 0 failures. That includes `RedactionTest` (11 tests) and `ToolTextsTest`.
- `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL in 3m 54s, 707 unit tests, 0 failures. Lint reports 12 issues, as before, none in the touched files. No compiler warnings.

## Phone checklist (for the owner's device)

1. Draw one box, share, and check the box is in the received image.
2. On a 24 MP PNG, tap Compartir and try to drag a new box and to tap Rehacer during «Preparando la copia…». Neither should be possible, and the received image should match the screen.
3. The first report's list: a PNG screenshot, a JPEG with orientation 6, a HEIC, and an image over 24 MP.
4. An image over 100 MP or with a side over 20 000 px is refused at once with the new text.

## Concerns

- The 100 MP cap refuses full-resolution 200 MP photos, in Tachar and in the HEIC conversion of Limpiar foto. Some phones have such sensors, and the owner's Honor may be one. This is the controller's ruling: I implemented it as stated and am flagging it.

## Revisión experto-seguridad-cripto (round 1)

- **The copy matches what the screen shows.**
  - While a copy is being made, input is blocked: canvas, Undo and Redo (`RedactScreen.kt:341-355`, `:447`, `:462`).
  - If the boxes differ from the snapshot anyway, the copy is discarded before it is written or shared (`:229`, `:244`, `:382`).
  - This closes the case where «Copia lista.» appeared over a copy missing a box (anti-goal: a redacted image that does not cover what the user covered).
- **Decompression bombs: CPU is now bounded too, not just memory.**
  - The declared size is refused from the header, before inflation: `RedactRender.kt:87` (ImageDecoder header listener), `:100` (BitmapFactory bounds) and `HeifToJpeg.kt:38`.
  - The predicate is overflow-safe and JVM-tested (`Redaction.kt:134`).
  - It fails closed with fixed texts, and nothing is logged.
- **Memory hygiene.** Both bitmaps, full-size and preview, are recycled on re-pick and on leaving (`RedactScreen.kt:222`, `:431`). The full-size one is still never recycled while a copy is drawing from it.
- No new permissions, connections, dependencies or exported components. No cryptography in this task.

**Findings:** none Critical or Important open. The 200 MP cap is noted above as a product concern.

---

# Fix round 2 (commit `c05897a 1.3.0: Tachar en imagen, límite para fotos de 200 MP`, on top of `b89a9b5`; not pushed)

## Changes (controller ruling: 200 MP photos must not be refused)

- `tools/ImageLimits.kt`: `MAX_DECLARED_PIXELS = 260_000_000L` and `MAX_DECLARED_SIDE = 32_768`. The KDoc names the case: full-resolution 200 MP photos (16320×12240), still decoded scaled down to `MAX_REDACT_PIXELS`.
- `declaredSizeAllowed` (`Redaction.kt:134`) is unchanged in code, because it reads the constants. It requires each side in `1..32768` and `w × h ≤ 260 000 000`, multiplied as Long.
- The texts that state the limit now say «pasa de 260 megapíxeles o de 32.768 píxeles de lado»:
  - `PHOTO_TOO_LARGE` (`ToolTexts.kt:117`);
  - `REDACT_TOO_LARGE` (`ToolTexts.kt:196`).

  Both are still non-annex texts, for the Abogado Digital.
- No other code or text mentions the old figures (checked by grep in `app/src/main/java`).

## Tests

`RedactionTest.declaredSizeIsCheckedBeforeDecoding` was rewritten for the new limits.
- **RED:** the updated test failed against the old constants (`AssertionError`). **GREEN** after the change.
- **Sides:** 32768 is allowed and 32769 is refused, in both directions. 32768×7934 (259 981 312 px) is allowed.
- **Pixels:**
  - 20000×13000 and 13000×20000, exactly 260 000 000, are allowed.
  - 20000×13001 and 20001×13000 are refused, and so are 32768×7935 (260 014 080) and 32768².
  - 260 000 001 has no factor pair with both sides ≤ 32 768, so one row or one column more is the smallest step over. The test says so in a comment.
- **Phones:** 16320×12240 and 12240×16320 are allowed, and 8160×6120 still is.
- **Invalid sizes:** 0, negative values, `Int.MIN_VALUE`, `Int.MAX_VALUE` and 65536² are refused.

## Verification

- `gradle -p jvm-check test --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL, 707 tests, 0 failures. That includes `RedactionTest` and `ToolTextsTest`.
- `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline`: BUILD SUCCESSFUL in 3m 42s, 707 unit tests, 0 failures. Lint reports 12 issues, as before.

## Concerns

- **Peak memory of a 200 MP decode, to check on the phone.** The size check now admits 200 MP, so the decoder's own peak memory matters:
  - **JPEG:** libjpeg can scale by ½ while decoding, so the peak is about 50 MP (~200 MB) before the final scaling to 24 MP. That is native memory.
  - **HEIC:** depending on the platform decoder, it may decode at full resolution before scaling. That would be about 800 MB of native memory. If it fails, it fails closed with «dañado» and does not crash, because OOM and decode errors are caught. Even so, the result is worse for the user.

  Add to the phone checklist: a full-resolution 200 MP JPEG and HEIC from the Honor, in Tachar and in Limpiar foto (HEIC).
- **CPU bound.** A hostile PNG can still declare up to 260 MP, about 1 GB of inflated data, which takes seconds and not minutes. This is the trade-off of the ruling.

## Revisión experto-seguridad-cripto (round 2)

- **The limit is still checked before any pixel is decoded:**
  - in the ImageDecoder header listener (`RedactRender.kt:87`);
  - after BitmapFactory's bounds decode (`RedactRender.kt:100`);
  - in `heifToJpeg` (`HeifToJpeg.kt:38`).
- **The predicate** is overflow-safe and tested at every boundary (`Redaction.kt:134`, `RedactionTest.declaredSizeIsCheckedBeforeDecoding`).
- **Memory stays capped by the target size:**
  - setTargetSize or inSampleSize keep the decoded bitmap at 24 MP or less;
  - the bitmap is checked again after decoding (`RedactRender.kt:54`).
- **Raising the cap from 100 to 260 MP** mainly allows more CPU time for a hostile file. Severity: Low. It is a self-inflicted slowdown from a file the user picks, it is bounded, and it is cancelled by leaving the screen (the result is discarded).
- **No new findings.** No new permissions, connections or dependencies.
