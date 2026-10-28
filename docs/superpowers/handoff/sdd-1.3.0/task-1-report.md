# Task 1 report: Limpiar foto, JPEG y el informe

**Status:** DONE_WITH_CONCERNS (only minor concerns, listed below)
**Commit:** `f850512 1.3.0: limpiar foto, JPEG con lista cerrada e informe` (not pushed)

## What was implemented

New pure-Kotlin package `app.seguridad.tools.photo`, compiled by jvm-check. The interfaces are exactly the ones the brief names.

- `PhotoReport.kt`
  - `PHOTO_DAMAGED` and `PHOTO_UNSUPPORTED`, the annex «Error» and «Formato no admitido» texts verbatim.
  - `PhotoFormat`, `Found`, `PhotoReport(format, found, gps, orientation = 1)` and `PhotoFailure(message)`.
  - `detectFormat(head)`. HEIF matches the major brand or any compatible brand within the ftyp box, bounded by the head length.
- `Jpeg.kt`
  - `object Jpeg { inspect; clean }`: walks the markers from SOI.
    - Kept: DQT, DHT, DRI, SOF0..SOF15 except C4/C8/CC, DAC, SOS with its entropy data, APP2 `ICC_PROFILE\0` and APP14 `Adobe`.
    - Everything else is dropped: other APPn, COM, DNL, reserved markers, standalone markers, and everything after the first top-level EOI.
    - Fill `FF` bytes are accepted between segments.
    - Scan data: `FF00` and `FFD0..D7` are copied, and the data ends at the first other marker, so progressive files with DHT/SOS between scans work.
    - A non-default Orientation is written back as a fresh big-endian APP1 with one IFD0 entry, exactly as the brief describes.
  - `internal object Exif`: a minimal TIFF reader. It handles II/MM, IFD0, the ExifIFD and GPS pointers, and IFD1 only. It follows no other chain, so it cannot loop. Every offset is bounds-checked with `Long` arithmetic. Broken structure is reported as `OTHER` and never throws.
  - What sets each `Found` flag:
    - GPS: any GPS tag other than the version, or `GPSLatitude`/`GPSLongitude` in XMP. Coordinates are rounded to 2 decimals and need valid refs, non-zero denominators and values in range.
    - DEVICE: Make/Model, Exif 0x927C and 0xA430..A435, and XMP `tiff:Make`/`tiff:Model`/`SerialNumber`.
    - DATE_SOFTWARE: 0x0131, 0x0132, 0x013C, 0x9003/9004, 0x9010..12 and 0x9290..92.
    - THUMBNAIL: IFD1 present, a JFIF thumbnail, or JFXX.
    - XMP_COMMENTS: XMP (normal and extended), COM, APP13 Photoshop, and EXIF ImageDescription/UserComment/XP*/XMLPacket/IPTC.
    - ICC_KEPT: APP2 ICC.
    - MOTION_VIDEO: APP2 MPF, XMP `MotionPhoto`/`MicroVideo`, or non-padding bytes after EOI. Trailing `00`/`FF` padding is not reported but is still dropped.
    - C2PA: APP11 starting `JP` with `jumb` in its first 32 bytes.
    - AUTHOR_RIGHTS: 0x013B, 0x8298 and 0x9C9D, XMP `dc:creator`/`dc:rights`/`xmpRights:`, and IPTC 2:80/2:116.
    - OTHER: any other APPn, other EXIF tags (exposure and so on) and malformed EXIF.
- `PhotoCleaner.kt`
  - `object PhotoCleaner { inspect; clean }` dispatches by `detectFormat`.
    - JPEG goes to `Jpeg`.
    - PNG, WebP, HEIF and unknown formats throw `PhotoFailure(PHOTO_UNSUPPORTED)` for now (resolution for this task).
  - `clean` then calls `internal fun verify(format, out)`. It fails closed with `PHOTO_DAMAGED` unless all of these hold:
    - the output detects as the same format;
    - it re-parses;
    - `found` has nothing but `ICC_KEPT`;
    - the image ends exactly at the end of the array.
  - A `guarded` wrapper turns any unexpected `RuntimeException` into `PHOTO_DAMAGED` (defence in depth). `Jpeg` itself has no such wrapper, so the fuzz test exercises it unprotected.

## Choices beyond the brief (small and conservative)

1. **APP14 is trimmed to its fixed 12 bytes.** Extra bytes in a long APP14 could otherwise carry data out (`Jpeg.kt:167`). An APP14 shorter than 12 bytes is dropped. Test: `adobeIsTrimmedToItsTwelveBytes`.
2. **Every EXIF segment is reported**, not only the first. Orientation and GPS come from the first one.
3. **Structure required for success:** SOI, a SOF before the first SOS, at least one SOS, and a top-level EOI. A file with no EOI (truncated) fails with `PHOTO_DAMAGED`. This is fail-closed, so a truncated camera file is refused rather than repaired.
4. `Jpeg.imageEnd` is internal and is used only by `verify`.

## Tests (TDD evidence)

- **RED:** `gradle -p jvm-check test --tests 'app.seguridad.tools.photo.*'` failed at compile time: `Unresolved reference 'Found'`, `'PhotoFailure'`, and so on. The tests were written before any production code.
- **GREEN:** the same command passed: JpegTest 24/24 and PhotoCleanerTest 7/7.
- **Mutation check:** I removed the `imageEnd == out.size` rule from `verify`, and `checkAfterCleaningFailsClosed` failed. I then restored the rule.
- **Full suite:** `gradle -p jvm-check test --no-daemon --max-workers=1 --offline` gave BUILD SUCCESSFUL, 644 passed and 0 failed.
- **Android:** `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.tools.photo.*' :app:lintDebug --offline` gave BUILD SUCCESSFUL: 31 tests, 0 skipped, 0 failures, and no lint findings in the package.
  - The first attempt failed to compile because android.jar has no `javax.imageio`/`java.awt`.
  - Fix: the baseline is now a hard-coded 660-byte 16x16 JPEG, written once by the JDK encoder, and the pixel test calls ImageIO by reflection, with an `Assume` skip if it is missing. At runtime it ran and was not skipped.
- I did not run `assembleDebug`, `checkLicense`, `checkFragmentVersion` or `checkAssets`. This task changes no dependencies, assets or Android code.

**JpegTest:** all the tests the brief names, under the same names:
- `keepsOnlyTheListedSegments`
- `reportNamesWhatIsThere` (40.42, −3.7)
- `mpfSecondImageAfterEoiIsDropped`
- `orientationIsKeptAlone`: exact bytes, BE and LE input; Orientation 1, 0, 9 and 0xFFFF give no APP1
- `c2paIsReported`
- `artistAndCopyrightAreAuthorRights`
- `segmentLengthPastTheEndFails`
- `lengthBelowTwoFails`
- `noSosFails`
- `restartMarkersAndStuffedBytesSurvive`, including `FF00 D9`
- `progressiveWithSeveralScans`
- `cleanIsIdempotent`

Additional tests:
- `gpsIsReadInLittleEndianToo`
- `paddingAfterEoiIsNotAVideo`
- `unknownSegmentsAreOtherAndDropped`
- `jfifThumbnailIsReported`
- `adobeIsTrimmedToItsTwelveBytes`
- `otherStructuralDamageFails`: empty input, a single byte, SOI only, EOI first, no EOI, truncated fill bytes, SOS before SOF, garbage between segments, and a double SOI
- `fillBytesBetweenSegmentsAreAccepted`
- `pixelsAreUnchanged`: decoded pixels are identical after cleaning
- `hostileExifIsHarmless`: out-of-range IFD and value offsets, a 0x7FFFFFFF count, IFD1 pointing to IFD0, a bad byte order, and an Orientation of the wrong type
- `badGpsGivesNoCoordinates`: a zero denominator, out of range, a bad ref and a missing longitude, plus a southern-hemisphere case
- `corruptedInputNeverEscapesTheWhitelist`: 4000 seeded mutations and truncations. Only `PhotoFailure(PHOTO_DAMAGED)` is ever thrown, every successful output re-inspects to a set within `{ICC_KEPT}` and is idempotent, and more than 100 iterations clean successfully.
- `noOutputByteAfterEoi`

**PhotoCleanerTest:**
- `detectsTheFormats`, covering all 8 HEIF brands, plus a compatible brand
- `anythingElseIsNull`, including an ftyp box size larger than the head
- `cleansJpeg`
- `heifAndUnknownAreUnsupported`
- `damagedJpegIsDamaged`
- `checkAfterCleaningFailsClosed`: COM, EXIF, trailing zeros, a second image, no EOI, and GIF
- `messagesAreTheAnnexTexts`

All metadata in `TestImages.kt` is invented ("Fabricante Inventado", "SN-0000-PRUEBA", "Autora Inventada"). The only coordinates are the ones the brief sets.

## Files

- `/home/user/Seguridad/app/src/main/java/app/seguridad/tools/photo/PhotoReport.kt`
- `/home/user/Seguridad/app/src/main/java/app/seguridad/tools/photo/Jpeg.kt` (includes `internal object Exif`, reusable by Task 2 for PNG eXIf and WebP EXIF if wanted)
- `/home/user/Seguridad/app/src/main/java/app/seguridad/tools/photo/PhotoCleaner.kt`
- `/home/user/Seguridad/app/src/test/java/app/seguridad/tools/photo/TestImages.kt`
- `/home/user/Seguridad/app/src/test/java/app/seguridad/tools/photo/JpegTest.kt`
- `/home/user/Seguridad/app/src/test/java/app/seguridad/tools/photo/PhotoCleanerTest.kt`: not in the brief's file list. I added it because `detectFormat` and the dispatch and verify logic need tests of their own.

## Self-review

- The interfaces match the brief exactly.
- Task 2 must replace the `else -> UNSUPPORTED` branches in `PhotoCleaner.inspect`, `clean` and `verify` with PNG/WEBP. `PhotoCleanerTest.heifAndUnknownAreUnsupported` deliberately does not test PNG/WebP as unsupported, so Task 2 does not need to break a test.
- Task 3 (`heifToJpeg`): the JFIF APP0 from the Android encoder is dropped and not reported (no thumbnail), so `verify` passes.
- Memory: `clean` makes one exact-size output allocation, and pieces are copied as ranges with no intermediate copies of scan data.
- Every loop advances at least one byte or one entry, and there is no recursion.

## Concerns (minor)

1. **`MAX_IMAGE_BYTES` is not defined here.** The plan puts the 50 MB read limit in Task 4, at the reading layer, so I did not add a constant that might clash later. `PhotoCleaner` accepts any array it is given. Task 4 should define the constant and check it before reading.
2. **The ICC profile is kept verbatim, as the design says.** ICC `desc`/`cprt` tags can contain free text, such as a vendor name. This is by design (ICC_KEPT is shown as kept) but worth knowing. The same applies to the bodies of DQT, DHT and SOF, which are copied without checking their internal table lengths. Extra bytes inside them could hide a few bytes, though they cannot hold metadata that a reader would interpret.
3. **Dropping DNL (0xDC), per the brief,** would make a rare height-0 JPEG undecodable. The Android `BitmapFactory` bounds check in Task 4 would catch it.
4. **Report flag heuristics:**
   - XMP, IPTC and C2PA detection uses substring and byte-pattern matching, so a false positive only adds an informative line.
   - Any uncategorised EXIF tag (exposure and so on) gives `OTHER`, so most camera photos will report `OTHER`. Task 4 decides how to show it.
   - The cleaning itself never depends on these flags: it is a whitelist.

## Revisión experto-seguridad-cripto

**Threat model:** the photo comes from an untrusted source. It may be crafted to crash or hang the parser, or to smuggle metadata past the cleaner. What must hold: the output contains only the whitelisted segments, nothing after EOI, and the only exception is `PhotoFailure`. No cryptography is involved. There is no network and no logging.

| Check | Evidence |
|---|---|
| Closed list: only listed segments are copied, and anything unknown is dropped | `Jpeg.kt:80-101` (the `else -> OTHER` branch at `Jpeg.kt:101` never adds a piece). Only ICC is kept in the APPn handler, at `Jpeg.kt:156`, and APP14 is rebuilt to 12 bytes at `Jpeg.kt:167` |
| Nothing after the first top-level EOI | `Jpeg.kt:69` (break), output ends with a fresh EOI at `Jpeg.kt:31`; test `noOutputByteAfterEoi`, `mpfSecondImageAfterEoiIsDropped` |
| Every length checked before use (CWE-125/CWE-787) | `Jpeg.kt:73-78` (length available, `len >= 2`, `segEnd <= n`); scan loop bounds `Jpeg.kt:115-128`; TIFF `ok()` with Long arithmetic `Jpeg.kt:258`, IFD fits `Jpeg.kt:273-294`; `detectFormat` bounded by `head.size` `PhotoReport.kt:32-55` |
| No infinite loop or recursion (CWE-835/CWE-674) | Top-level loop advances by at least 1 byte per marker (`Jpeg.kt:61-103`). The scan loop advances on every branch. EXIF follows only fixed pointers (IFD0, Exif, GPS, IFD1), with no chain following and no recursion (`Jpeg.kt:324-372`) |
| Integer overflow on output size | `Jpeg.kt:20-22` (Long sum, refuses more than `Int.MAX_VALUE - 8`) |
| Only `PhotoFailure` leaves the API | `Jpeg` throws only through `damaged()` (`Jpeg.kt:47`). `Exif` returns `OTHER` instead of throwing. `PhotoCleaner.guarded` is a backstop (`PhotoCleaner.kt:43-49`). The 4000-case fuzz test `corruptedInputNeverEscapesTheWhitelist` runs without the backstop |
| Fail-closed check after cleaning | `PhotoCleaner.kt:30-40`: same format, re-parse, `found ⊆ {ICC_KEPT}` and `imageEnd == size`. The mutation check confirmed the test catches its removal |
| No metadata re-created except Orientation | `Jpeg.kt:192-207` (one IFD0 entry, next IFD 0); test `orientationIsKeptAlone` compares exact bytes |
| GPS shown only coarsened (2 decimals) and validated | `Jpeg.kt:380-398` (zero denominator, range, refs) |
| Errors in Spanish, verbatim from the annex; no logs | `PhotoReport.kt:4-8`; test `messagesAreTheAnnexTexts`. `grep Log.\|println` over `tools/photo/` finds nothing |
| Hermetic: no new permission, dependency, network or exported component | Only pure Kotlin and stdlib. No change to the manifest, `build.gradle` or `libs.versions.toml`. ImageIO is used only in tests, by reflection |
| No real personal data in tests | `TestImages.kt`: invented make, model, serial and author; the GPS value is the one the brief sets |
| Existing formats untouched | No file outside `tools/photo/` changed (`git show --stat f850512`) |

Residual risks are the minor concerns 2 and 3 above. There are no confirmed security findings.

---

# Fix round 1 (review of f850512)

**Commit:** `b05a987 1.3.0: limpiar foto, comprobación estructural de la salida (revisión 1)` (not pushed)

## Changes

- **IMPORTANT 1: the check after cleaning is now structural.**
  - New `Jpeg.isCleanOutput(b)` (`Jpeg.kt:204`) walks the output's markers and accepts only:
    - SOI;
    - the canonical Orientation APP1, the exact bytes `orientationApp1(2..8)` writes, only right after SOI (`Jpeg.kt:223`);
    - APP2 `ICC_PROFILE\0`;
    - APP14 `Adobe` of exactly 12 payload bytes (`Jpeg.kt:225`);
    - DQT, DHT, DRI, SOFn (not C4/C8/CC) and DAC;
    - SOS with its scan data, which needs a SOF before it;
    - a final EOI with nothing after it, and at least one SOS (`Jpeg.kt:215`).
  - Fill bytes, standalone markers and every other segment are refused.
  - `PhotoCleaner.verify` (`PhotoCleaner.kt:35-37`) requires all three layers:
    1. `isCleanOutput`;
    2. `inspect(out).found ⊆ {ICC_KEPT}`;
    3. `Jpeg.clean(out).contentEquals(out)`.
  - Task 2 extends this by adding a `Png.isCleanOutput` / `Webp.isCleanOutput` branch to the same `when (format)`. The KDoc says so.
  - The now unused `Jpeg.imageEnd` was removed.
- **Minor: `Bytes` object.** `startsWith`/`contains` moved into `internal object Bytes` (`Jpeg.kt:280`), so they will not clash with Png.kt or Webp.kt.
- **Minor: Dublin Core under any prefix.** `dublinCoreAuthor` (`Jpeg.kt:189`) finds every prefix bound to `http://purl.org/dc/elements/1.1/`, using a bounded regex at `Jpeg.kt:276` with no nested quantifiers. It reports AUTHOR_RIGHTS when `<prefix>:creator` or `<prefix>:rights` appears. The literal `dc:` check remains.
- **Minor: standalone markers are reported.** Top-level RST and TEM markers are now reported as OTHER and dropped (`Jpeg.kt:68`).

## Covering tests

- `PhotoCleanerTest.checkAfterCleaningFailsClosed` adds these cases:
  - (a) JFIF with a 0x0 thumbnail and «secreto» after it;
  - (b) EXIF with an empty IFD0, and the canonical Orientation APP1 with «secreto» appended;
  - (c) a long APP14 Adobe;
  - (d) top-level `FFD0` and `FF01`.
- It also adds fill bytes, an APP1 written with Orientation 1, SOS before SOF, and no SOS.
- Each dirty case is checked twice: `verify` must fail with PHOTO_DAMAGED, and `Jpeg.isCleanOutput` on its own must return false, without help from inspect or idempotence.
- The accepted cases are: the canonical APP1 for every value 2..8, and APP1 + ICC + Adobe together.
- `JpegTest.dublinCoreUnderAnyPrefixIsAuthorRights`: `foo:creator`, `foo:rights` and `b:creator` with spaces around `=` all give AUTHOR_RIGHTS. `foo:title` alone gives only XMP_COMMENTS.
- `JpegTest.standaloneMarkersOutsideAScanAreOther` covers 01, D0 and D7: found is `{OTHER}` and the output is plain.
- `JpegTest.corruptedInputNeverEscapesTheWhitelist` now also asserts `isCleanOutput(out)` and `PhotoCleaner.verify` for every successful clean of the 4000 corrupted inputs.

## Evidence

- **RED,** before the fix: `gradle -p jvm-check test --tests 'app.seguridad.tools.photo.*' --no-daemon --max-workers=1 --offline` gave `33 tests completed, 3 failed`:
  - `dublinCoreUnderAnyPrefixIsAuthorRights`: expected `[AUTHOR_RIGHTS, XMP_COMMENTS]` but was `[XMP_COMMENTS]`;
  - `standaloneMarkersOutsideAScanAreOther`: expected `[OTHER]` but was `[]`;
  - `checkAfterCleaningFailsClosed`: «expected PhotoFailure to be thrown, but nothing was thrown».
- **GREEN:** the same command gave BUILD SUCCESSFUL, and all 33 photo tests passed.
- **Mutation checks** (each one restored afterwards; `cmp` confirmed the file is unchanged):
  - With `isCleanOutput` removed from `verify`, the suite still passed, because idempotence alone also catches all of (a) to (d) (layered defence). That is why every dirty case now also asserts `isCleanOutput` directly.
  - With the APP14 length check removed from `isCleanOutput`, PhotoCleanerTest failed.
  - With the canonical-APP1 check replaced by `true`, PhotoCleanerTest failed.
- **Full suite:** `gradle -p jvm-check test --no-daemon --max-workers=1 --offline` gave BUILD SUCCESSFUL, 646 passed and 0 failed.

## Revisión experto-seguridad-cripto (fix round 1)

| Check | Evidence |
|---|---|
| Fail-closed re-check is now a whitelist, not a list of what inspect can report | `Jpeg.kt:204-238`; `PhotoCleaner.kt:35-37`; cases (a) to (d) in `PhotoCleanerTest.checkAfterCleaningFailsClosed` |
| Re-written metadata accepted only as exact canonical bytes | `Jpeg.kt:223` (byte-exact equality with `orientationApp1(2..8)`, at offset 2 only) |
| APP14 cannot carry extra bytes in an accepted output | `Jpeg.kt:225` (`len == 14`) |
| Bounds in the structural walker | `p + 2 > n`, `p + 4 > n` and `end > n` checked before every read; scan data goes through the bounded `scanEnd`, and its PhotoFailure maps to false |
| No ReDoS on hostile XMP | `Jpeg.kt:276`: fixed quantifiers (`{0,63}`, `{0,16}`) with no nesting; it runs only when the namespace URI is present |
| Every dropped item is reported | `Jpeg.kt:68` (standalone markers become OTHER) |

No new findings. Concerns 1 to 4 from the first round are unchanged.
