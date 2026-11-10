# Task 7: report (lectores JSON en claro y listas otpauth)

**Status:** DONE_WITH_CONCERNS (see «Concerns»). Commit `fb195cd` «1.4.0: lectores de Bitwarden, Aegis y 2FAS en claro, y listas otpauth», pushed to `ccr-a1a6eabb-765f9g` (BASE `e903cbb`).

## What was built

- `imports/ImportJson.kt` (new, see «Deviations»): `checkJsonShape` (the scan before parsing), `readJsonImport` (detection and dispatch), the typed accessors (`text`, `textOrNull`, `flag`, `obj`, `list`, `number`), `wellFormed`, `folderNames`, `NotesBuilder`, `WrongType`.
- `imports/BitwardenJson.kt`: `readBitwarden(root)` (encrypted / protected / plain) and `fun readBitwardenPlain(json: JsonObject): ImportRead`.
- `imports/AegisJson.kt`: `fun readAegisPlain(db: JsonObject): ImportRead`.
- `imports/TwoFasJson.kt`: `fun readTwoFasPlain(root: JsonObject): ImportRead`.
- `imports/OtpAuthList.kt`: `fun readOtpAuthList(text: String): ImportRead`, plus `readOtpAuthListImport` for detection.
- `imports/Detect.kt:18-22`: the readers run in this order: CSV (first, unchanged), JSON (only a text that starts with `{`), then the otpauth list (only when the first line that is not blank is a link).
- `imports/ImportModel.kt`:
  - the total bound in the builder (`nextIndex`, :167-170);
  - `addOtpAuth` takes `notes` and `favorite`;
  - `addTotp` (:206-232) builds a percent-encoded link from the parts and reads it with `addOtpAuth`.
- `crypto/OtpMigration.kt`: `MigrationResult.leftOut` (:18, default `emptyList()`), `MigrationLeftOutKind` and `MigrationLeftOut` (:22-34). The importer maps the reasons by kind and never parses the Spanish text. `skipped` keeps the same texts in the same order, so `VaultEntryScreen` does not change.
- `imports/CsvFormats.kt:75`: `hostOf` is now `internal`, so the Bitwarden title fallback can reuse it.
- `imports/ImportTexts.kt:45-97`: the new texts (listed below).
- Fixtures in `app/src/test/resources/import/`, all hand-made from the public documentation of each format, with invented data that follows the plan's list:
  - `bitwarden/bitwarden.json`;
  - `aegis/aegis-claro.json`;
  - `2fas/2fas-claro.2fas`;
  - `otpauth/lista.txt` (CRLF);
  - `README.txt`.

  Nothing was copied from any importer.

**Step 3 (the owner's files) is still pending.** When `bitwarden.json`, `aegis-claro.json` and `2fas-claro.2fas` arrive, apply Task 6 Step 5: add each file with its own test, which counts the entries by type and checks two titles.

## TDD evidence

- **RED 1 (compile).** Command: `gradle -p jvm-check test --no-daemon --max-workers=1`.
  - Output: `Unresolved reference 'MigrationLeftOut'`, `'JSON_MAX_DEPTH'`, `'IMPORT_JSON_TOO_DEEP'`, `'IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED'`, `'checkJsonShape'`…
  - Expected: the API did not exist yet.
- **RED 2 (behaviour).** Only the declarations were added: empty constants, a no-op `checkJsonShape`, `MigrationLeftOut` with an empty list, and no reader registered.
  - Same command. Output: `1554 tests completed, 68 failed`. Every new test in BitwardenJsonTest, AegisJsonTest, TwoFasJsonTest, OtpAuthListTest, ImportJsonTest and `OtpMigrationTest.leftOutAccountsSayWhyWithoutTheText` failed. The log is in the scratchpad (`red.log`).
  - Two tests passed already, as expected: `ImportJsonTest.aJsonOfNoKnownShapeIsUnknown` and `aBrokenJsonIsUnknownAndItsTextNeverShows`. They pin behaviour that already held (an unknown file gives IMPORT_UNKNOWN) and that must not change.
- **First GREEN run.** All the reader tests passed. Two existing guards failed:
  - `ImportTextsTest`: a `"steam://"` literal in `BitwardenJson.kt`. It moved to `ImportTexts.kt` as `BITWARDEN_STEAM_PREFIX`.
  - `AppNameTest`: «Número de la Seguridad Social». The label is now «Número de seguridad social».

  Both are fixed. A scan also found raw U+202E and U+2028 characters that the editor had written into two test sources. They are now `\uXXXX` escapes and mean the same.
- **GREEN, final, after the last change.**
  - `gradle -p jvm-check test --no-daemon --max-workers=1`: **1554 tests, 0 failed**, BUILD SUCCESSFUL. The new classes run 27 (Bitwarden), 15 (Aegis), 10 (2FAS), 9 (otpauth), 8 (ImportJson) and 14 (OtpMigration, one of them new) tests.
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1`: BUILD SUCCESSFUL, **1559 app unit tests, 0 failed**.
    - Lint: the 16 known warnings (1 AndroidGradlePluginVersion, 6 GradleDependency, 7 NewerVersionAvailable, 1 DiscouragedApi, 1 ObsoleteSdkInt), in libs.versions.toml, the manifest and mipmap. None is new.
    - No Kotlin compiler warnings.
- kotlinx.serialization is available to jvm-check (`jvm-check/build.gradle.kts:31`).

## How (a), huge or hostile input, is covered

| Requirement | Code | Tests |
|---|---|---|
| Depth is checked before any parsing, with one linear scan that knows about strings, `\"` and `\\`. 64 passes and 65 fails. | `ImportJson.kt:37-71` (`checkJsonShape`), called at :88 before `parseToJsonElement` (:89) | `ImportJsonTest.deepJsonIsRefused`, `bracketsInsideStringsDoNotCount`, `theDepthIsCheckedBeforeParsing` (8 M levels of invalid JSON, refused in under 5 s); `deepJsonIsRefused` in each of the three format tests (the real shape plus nesting, 64 OK and 65 refused) |
| The tree the parser builds is bounded: `JSON_MAX_TOKENS = 1 000 000` values and names, counted in the same scan | `ImportJson.kt:31`, :69 | `ImportJsonTest.tooManyValuesAreRefusedBeforeParsing` (exactly at the bound is read, one more is refused; numbers, `[]` and `""`) |
| Total items after every expansion: items and skipped elements both count, the one past IMPORT_MAX_ITEMS throws at once | `ImportModel.kt:167-170` | `BitwardenJsonTest.itemCountOverLimitIsRefused` (20 000 notes pass, 20 001 fail, 10 001 logins with a TOTP fail); `OtpAuthListTest.migrationExpansionIsBoundedEarly` (200 lines of 100 codes = 20 000 pass, 201 fail, under 10 s); `AegisJsonTest.itemCountOverLimitIsRefused` |
| Elements per file are counted before the loop | `BitwardenJson.kt:54`, `AegisJson.kt:24`, `TwoFasJson.kt:21` | same |
| Lines and line length in otpauth lists: more than 20 000 lines that are not blank fail; a line over its limit is FIELD_TOO_LONG and is never copied | `OtpAuthList.kt:56`, :71 (before the `substring` at :72) | `OtpAuthListTest.lineCountAndLineLengthAreBounded` (20 001 lines; 1 M blank lines; a 5 M-character line; a 100 K migration line) |
| Fields as in Task 6. The notes stop building at MAX_NOTES, and a part that would pass it is never appended | `NotesBuilder`, `ImportJson.kt:246-293` (check at :269); `BitwardenJson.kt:163-168`; `AegisJson.kt:51`; `ImportModel.kt:218` (the TOTP parts are checked before the link is built) | `BitwardenJsonTest.fieldOverLimitIsSkipped`, `notesBuiltFromFieldsStopAtTheLimit` (60 000 fields and 5 000 URIs; exactly MAX_NOTES enters whole); `veryLongStringsAreBoundedNotCut` (a 4 M password); `AegisJsonTest.fieldOverLimitIsSkipped` |
| Duplicate keys: the last value wins, as the parser keeps it | `ImportJson.kt:17` (KDoc) | `BitwardenJsonTest.duplicateKeysTakeTheLastValue` (in an item and at the root) |
| Huge numbers: `type` becomes BROKEN; digits or period become TOTP_UNSUPPORTED; an unquoted non-number is BROKEN | `ImportJson.kt:165-173` (`number`, RFC 8259 grammar :176-197, `NOT_A_WHOLE_NUMBER`) | `BitwardenJsonTest.hugeNumbersAreBroken` (`1e999`, 30 digits, `1.0`, `-1`); `AegisJsonTest.aMissingSettingTakesTheDefault…` (`6.5`, 30 digits, `1e999`) |
| NUL and lone surrogates (`\u0000`, `\ud800`, `\udc00`) make the element BROKEN, and the report title drops the surrogate | `ImportJson.kt:206-222` (`wellFormed`), used at `BitwardenJson.kt:156`, `AegisJson.kt:49`, `TwoFasJson.kt:43`, `OtpAuthList.kt:73` | `nulAndLoneSurrogatesAreBroken` in Bitwarden, Aegis and 2FAS; `OtpAuthListTest.hostileLinesAreData` |
| No kotlinx exception escapes. A parse error becomes IMPORT_UNKNOWN and its message, which quotes the file, is never used. `WrongType` becomes Skipped(BROKEN). Anything else becomes IMPORT_BROKEN in `readImport` | `ImportJson.kt:88-95`; `BitwardenJson.kt:87`; `AegisJson.kt:53`; `TwoFasJson.kt:47`; `Detect.kt:45-48` | `ImportJsonTest.aBrokenJsonIsUnknownAndItsTextNeverShows` (truncated, trailing comma, junk after the root, a bad escape, `\u12`; the secret is never in the message); `wrongTypesAreBroken` in all three formats |

## How (c), imported content is data, is covered

- **Only `parseToJsonElement` and access by fixed name.**
  - Parsing: `ImportJson.kt:89`. The accessors are at `ImportJson.kt:117-173`.
  - `grep -rnE "decodeFromString|decodeFromJsonElement|polymorphic|classDiscriminator|Class.forName|::class.java|Log\.|println|Intent|Uri.parse|Linkify" imports/` finds only the KDoc that says these are not used (`ImportJson.kt:15`).
  - The format is chosen by top-level names in a fixed `when` (`ImportJson.kt:97-109`).
- **Values are stored exactly as read.**
  - Titles, notes and URLs are kept verbatim, and the report title goes through `plainReportText` (R9).
  - Tests: `BitwardenJsonTest.hostileValuesAreStoredExactly` (U+202E, U+2028, `javascript:`, `intent:`), `AegisJsonTest.hostileNamesAreStoredAsTheLinkReaderKeepsThem`.
  - No URI becomes an Intent or a link: the readers only build `VaultEntry` strings.
- **Every TOTP goes through `readOtpAuth`/`isSupported`, or through the migration parser plus a second check.**
  - Bitwarden: a link goes to `addOtpAuth`, and a bare key to `addTotp` (`BitwardenJson.kt:185-189`).
  - Aegis and 2FAS: `addTotp`, which builds a link where each part is percent-encoded into its own parameter (`ImportModel.kt:206-235`). An `issuer` such as `x&secret=…`, or an unquoted `"digits":6&period=10`, can never add a setting. Tests: `AegisJsonTest.hostileNamesAre…` and the «inyección» cases in `wrongTypesAreBroken` (Aegis and 2FAS).
  - Migration: `parseOtpMigration`, then `isUsableTotpKey` and `isSupported` again (`OtpAuthList.kt:83-98`).
  - A setting that is not supported gives TOTP_UNSUPPORTED and is never changed. Only a missing one takes the default. Tests: `aMissingSettingTakesTheDefaultAndAPresentOneIsNeverChanged` (Aegis and 2FAS).
- **No secret reaches a log, an exception or `Skipped`.**
  - The titles of skipped elements are the name, the issuer, the account or «Línea N», never a key, a link, a password or a note.
  - `WrongType` has no message and no stack trace (`ImportJson.kt:115-118`).
  - `NotesBuilder.build` overwrites its array.
  - Tests: `secretsNeverReachTheSkippedList` (Bitwarden, Aegis), `OtpAuthListTest.hostileLinesAreData`.

## Detection

- A JSON of unknown shape, invalid JSON, or a JSON whose root is not an object gives IMPORT_UNKNOWN (`ImportJsonTest.aJsonOfNoKnownShapeIsUnknown`, `aBrokenJsonIsUnknown…`).
- CSV detection did not change: CSV is still asked first, and the existing CsvTest, CsvFormatsTest and DetectTest pass (`ImportJsonTest.csvIsStillReadAsCsv`).
- An otpauth list is recognised only when its first line that is not blank is a link (`OtpAuthListTest.aFileThatDoesNotStartWithALinkIsNotAList`).

## Decisions taken (to confirm)

1. **Encrypted Aegis and 2FAS files.** An encrypted Aegis file (`db` is a string) and an encrypted 2FAS file (`servicesEncrypted` is a string) give `NeedsPassword(AEGIS/TWOFAS)`, like a protected Bitwarden file. Until Task 10 lands, calling `readImport` again with a password gives `NeedsPassword` again. Task 9's screen must not loop on it.
2. **The total bound lives in the shared builder.** It therefore also applies to the CSV readers: an Apple CSV with more than 10 000 rows that each have an OTPAuth now fails. It counts skipped elements too.
3. **Invalid JSON gives «Clavero no reconoce este archivo.»**, not IMPORT_BROKEN, following the brief («looks like JSON but matches no format»). A truncated export therefore reads as «unknown».
4. **Bitwarden reading rules.**
   - `login.totp` may be an otpauth link, a `steam://` code (NOT_TOTP) or a bare Base32 key (SHA-1, 6 digits and 30 s, which is what Bitwarden does).
   - Linked custom fields (whose value is null) give no line, and neither does a line with an empty value.
   - A type outside 1 to 5 is BROKEN.
   - Unreadable folder records give no folder; the item still enters.
   - Bitwarden «ssn» uses the generic label «Número de seguridad social».
5. **Empty elements.**
   - A login with nothing is EMPTY, as in CSV.
   - A note or card with only a title enters.
   - An EMPTY login that has a passkey gives both EMPTY and PASSKEY.
6. **Migration lines.** A migration line lists its codes first, then the accounts it leaves out, so the order of the codes inside the QR is not kept between the two groups. Accounts with a short key are BROKEN (`readOtpAuth` treats a short key as not usable).
7. **The TOTP of a Bitwarden login.** It takes the login's title and folders, not its favourite (the spec names only the title and the tags). An Aegis code keeps its `note` and `favorite`.

## Deviations from the plan's file list

- New `imports/ImportJson.kt`. It holds the shared JSON scan, accessors and dispatch, so the three readers do not each repeat them.
- `crypto/OtpMigration.kt`: an addition that stays compatible (`leftOut`). Without it, the importer would have to parse the Spanish skip texts.
- `CsvFormats.kt`: `hostOf` is now `internal`.

## Concerns

- **`JSON_MAX_TOKENS` = 1 000 000** keeps the worst case at a few tens of MB of tree. A Bitwarden login has about 45 to 50 tokens, so a real export of about 20 000 items with history could reach the bound and get «El archivo tiene demasiados datos». I accepted this; the owner may want 2 000 000 instead (about twice the memory in the worst case).
- Decisions 1 to 3 above.
- Step 3, the owner's real files: still pending.

## Revisión experto-seguridad-cripto

- **Threat model:** a hostile import file (a crafted JSON, a list or a migration QR) from another app or person. The goals are no crash, no DoS, nothing executed, no setting changed and no secret leaked.
- **DoS / CWE-400 and CWE-674:**
  - depth and token bounds before parsing (`ImportJson.kt:57,69`). kotlinx's tree reader never sees more than 64 levels;
  - the item bound after expansion (`ImportModel.kt:168`), stopping on the next element;
  - every loop is linear. There is no regular expression on file data (the number grammar is a manual scan at `ImportJson.kt:176-197`, so there is no ReDoS).
- **Injection / CWE-88-like parameter injection into the internal otpauth link:**
  - each part is `URLEncoder`-encoded into its own parameter (`ImportModel.kt:222-228,235`);
  - numeric settings come only from JSON numbers that match RFC 8259, so an unquoted `6&period=10` is WrongType (`ImportJson.kt:169`);
  - the label is `:` + account, so the account cannot become an issuer (`ImportModel.kt:222`; tested).
- **Deserialization / CWE-502:** a tree only, with no class decoding (`ImportJson.kt:89`).
- **Information exposure / CWE-209:** kotlinx messages, which quote the input, are swallowed (`ImportJson.kt:90-94`). `WrongType` carries nothing. Skipped titles are plain names (`plainReportText`).
- **Unicode:** NUL and lone surrogates are refused rather than stored, since they could break the vault's later UTF-8 encoding (`ImportJson.kt:206`). Bidi characters are stored verbatim and removed only for display (R9/R10).
- **TOTP (CRY-B02):** one rule for every source (`readOtpAuth` + `isSupported`), and migration codes are checked twice (`OtpAuthList.kt:87-88`). The test sources contain no raw bidi characters: they are escaped, which avoids Trojan Source (CVE-2021-42574) in review.
- No new crypto, dependency, permission or network access. No secret is written to disk or to a log.

## Revisión Ingeniero Móvil Senior

- **Architecture:**
  - pure Kotlin in `imports/`, compiled and tested in jvm-check;
  - the readers are pure functions over the parsed tree (`readBitwardenPlain(JsonObject)` and the others, as the plan specifies);
  - they share one `ImportReadBuilder`, so indexes, titles and TOTP rules are the same everywhere.
- **Memory on Android:**
  - without `largeHeap`, the worst case is bounded by the 16 MB file, the decoded string (about 32 MB as UTF-16) and at most 1 M JSON tokens;
  - `NotesBuilder` never allocates past MAX_NOTES (`ImportJson.kt:269`);
  - otpauth lines are scanned by index and copied only within their limit (`OtpAuthList.kt:71-72`).
- **Concurrency:** none here. `readImport` stays synchronous and must be called off the main thread by Task 9's ViewModel. That is a note for Task 9: a 16 MB JSON takes a moment to parse.
- **Compatibility:**
  - `MigrationResult` gained a field with a default, so the existing call sites (`VaultEntryScreen.kt:247-252`, `EntryEditSessionTest.kt:93`) compile unchanged;
  - `skipped` keeps the same strings;
  - CSV detection did not change.
- **Lint and compiler:** the 16 known warnings and no new ones; no Kotlin warnings.

## Textos para el Abogado Digital (revisión final)

All in `app/src/main/java/app/seguridad/imports/ImportTexts.kt`:

- :46 `IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED`: «Esta exportación está cifrada con tu cuenta de Bitwarden. Expórtala como "JSON" o como "JSON (protegido con contraseña)".» (the spec's text, verbatim; it names Bitwarden).
- :50 `IMPORT_JSON_TOO_DEEP`: «El archivo tiene más de 64 niveles anidados. Clavero no lo lee.»
- :53 `IMPORT_JSON_TOO_MANY_VALUES`: «El archivo tiene demasiados datos. Clavero no lo lee.»
- :56 `importLineTitle`: «Línea N» (the name of a line in «no entran»).
- :59 `NOTE_LINE_URL`: «URL» (the label of each extra URI in the notes).
- :62-69 `BITWARDEN_CARD_LINES`: «Titular», «Marca», «Número», «Mes de caducidad», «Año de caducidad», «Código de seguridad».
- :72-91 `BITWARDEN_IDENTITY_LINES`: «Tratamiento», «Nombre», «Segundo nombre», «Apellidos», «Dirección», «Dirección 2», «Dirección 3», «Ciudad», «Provincia o estado», «Código postal», «País», «Empresa», «Correo», «Teléfono», «Número de seguridad social», «Usuario», «Pasaporte», «Permiso de conducir».
- :97 `BITWARDEN_STEAM_PREFIX` = `steam://`: not a visible text; it lives here only because it names a product (R2).

---

# Fix round 1 (FIX_BASE fb195cd → 776b27e)

**Commit:** `776b27e` «1.4.0: importar, arreglos de la revisión de la tarea 7», pushed to `ccr-a1a6eabb-765f9g`.

## Findings → changes → covering tests

| Finding | Change | Covering tests |
|---|---|---|
| **I-1** (ruling R11a): the flat token count let a 3 MB file build an 85–108 MB tree | `JSON_MAX_TOKENS` is gone. The same pre-parse scan now estimates the tree in bytes (`ImportJson.kt:69-127`): object 176, member 96 (charged at `:`), array 48, primitive 80, and 2 per character of a string, name or number. It refuses above `JSON_MAX_TREE_BYTES` = 48 MiB (`:62`) with the existing «demasiados datos» text, and it stops at the first character over the budget. The measured shapes and numbers are recorded in the KDoc (`:24-40`), which no longer claims «a few tens of MB». `jsonTreeEstimate` (`:77`) exposes the estimate for tests. | `ImportJsonTest.theEstimateIsNeverBelowTheMeasuredTree` (the three worst shapes from the review, `[]` and `""`, objects of 1/2/13/25/49/97 members, and a 1 M-char non-Latin-1 string); `aTreeOverTheBudgetIsRefusedBeforeParsing` (exactly at the budget is read, +1 is refused, an unterminated file over the budget is still refused, so the check runs before parsing); `aLongStringIsWithinTheBudget` (an 8 M-char string is read); `theConstantsCoverTheMeasuredNodes` |
| **M-1**: folder and group names skipped `wellFormed` | `folderNames` drops a record whose name is not `wellFormed` (`ImportJson.kt:296`), and so does the old Aegis `group` (`AegisJson.kt:42`) | `BitwardenJsonTest.aFolderNameWithANulOrALoneSurrogateGivesNoFolder`; `AegisJsonTest.aGroupNameWith…`; `TwoFasJsonTest.aGroupNameWith…` |
| **M-2**: TOTP names were cut silently at 256 UTF-16 units, which could leave a lone surrogate | `otpName` now cuts by code points, and `otpNameFits` is new (`Totp.kt:156-169`). `readOtpAuth` reports `Usable.namesCut` (`Totp.kt:193-196,238`). `addOtpAuth` lists a cut code as FIELD_TOO_LONG (`ImportModel.kt:190`). `addTotp` refuses an issuer or account that would be cut before it builds the link (`ImportModel.kt:224`). Migration codes carry `importedNamesCut` (`OtpMigration.kt:23`), and the list reader refuses them (`OtpAuthList.kt:87`). So Bitwarden links, Apple CSV links, otpauth lists and migration lines are covered as well, not only Aegis and 2FAS. The vault's own scanner still accepts a cut name, as before. | `AegisJsonTest.anIssuerOrAccountTooLongForTheCodeIsNeverCut` (256 enters; 255 + 🛒 enters whole with no lone surrogate; 257 code points → FIELD_TOO_LONG); `TwoFasJsonTest.anIssuerOrAccountTooLong…`; `OtpAuthListTest.aNameTooLongForTheCodeIsNeverCut` (link label, issuer parameter, migration name and issuer); `ImportModelTest.addTotpNeverCutsANameAndItsPartsCannotAddASetting`; `OtpMigrationTest.otpNameCutsByCodePoints`, `readOtpAuthSaysWhenItCutAName`, `migrationSaysWhichCodesHaveANameCut`; the existing `VaultTotpTest` and `OtpMigrationTest` cut tests still pass |
| **M-3**: a null or empty first URI pushed the URL into the notes | The URL is now the first URI that is not empty (`BitwardenJson.kt:121`) | `BitwardenJsonTest.theUrlIsTheFirstUriThatIsNotEmpty` (also the host fallback for the title) |
| **M-4**: a field without a name gave «: valor» | A blank name now uses `NOTE_LINE_UNNAMED_FIELD` = «Campo» (`ImportTexts.kt:100`, `BitwardenJson.kt:135`) | `BitwardenJsonTest.aFieldWithoutANameGetsAGenericLabel` |
| **M-5**: a duplicate percent-encoder | `otpEncode` is `internal` (`Totp.kt:248`) and used by `addTotp` (`ImportModel.kt:229-231`); `linkPart` is gone | the `addTotp` paths in every Aegis and 2FAS test, and `ImportModelTest.addTotpNeverCuts…` (`&`, `:` and spaces round-trip) |

## Evidence

- **Measurements** (scripts in the scratchpad: `mem2/M2.java`, `mem2/M3.java`; kotlinx-serialization-json 1.11.0 jar, `java -XX:+UseSerialGC -Xmx2g`), per element:

  | Shape | Measured |
  |---|---|
  | `{}` | 82.4 B |
  | `[]` | 44.8 B |
  | `1` | 74.8 B |
  | `true` | 71.8 B |
  | `null` | 0 B (a singleton) |
  | `""` | 25.8 B |
  | `{"k":1}` | 316.8 B |
  | an object with m `null` members | m = 1: 253 B; 2: 319 B; 13: 1237 B; 25: 2290 B; 49: 4394 B; 97: 8601 B |
  | a non-Latin-1 string of 100 characters | 2.68 B per character |
  | `{"a":[{}…]}`, 999 990 objects | 81.9 B each |

  The constants are these numbers rounded up.
- **No heap probe**, by design. There is no stable in-repo probe pattern (Task 6 has none), a GC-based delta in the shared Gradle test JVM can be flaky, and the ruling forbids a flaky test. The tests compare the estimate with the recorded measurements instead, which is deterministic.
- **RED for the Minors.** I wrote the tests together with the fixes, then ran a mutation check: I restored the `fb195cd` versions of `BitwardenJson.kt` and `AegisJson.kt` and removed the `wellFormed` filter from `folderNames`.
  - Command: `gradle -p jvm-check test --no-daemon --max-workers=1 --tests 'app.seguridad.imports.*'`
  - Result: `147 tests completed, 5 failed`. The failures: `aGroupNameWith…` (Aegis and 2FAS), `aFolderNameWith…`, `theUrlIsTheFirstUriThatIsNotEmpty`, `aFieldWithoutANameGetsAGenericLabel`.
  - The fixes were then restored. The I-1 and M-2 tests need the new API (`jsonTreeEstimate`, `otpNameFits`, `namesCut`), so before the fix they could only fail to compile.
- **Covering tests:** `gradle -p jvm-check test --no-daemon --max-workers=1 --tests 'app.seguridad.imports.*' --tests 'app.seguridad.crypto.*' --tests 'app.seguridad.vault.VaultTotpTest'` → BUILD SUCCESSFUL, 440 passed, including the 16 new tests.
- **Full verification after the last change:**
  - `gradle -p jvm-check test --no-daemon --max-workers=1` → BUILD SUCCESSFUL, **1569 tests, 0 failed**.
  - `export ANDROID_HOME=/opt/android-sdk; ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1` → BUILD SUCCESSFUL, **1574 app unit tests, 0 failed**. Lint shows the 16 known warnings (1 AndroidGradlePluginVersion, 1 DiscouragedApi, 6 GradleDependency, 7 NewerVersionAvailable, 1 ObsoleteSdkInt); no new warning, no Kotlin warning.
  - No raw bidi or invisible character in any file touched.

## Notes for later tasks

- **R11b, Task 9:** catch `OutOfMemoryError` only around the one `readImport` call, on a background dispatcher. The `Detect.kt` comment stays true for the readers.
- **For Task 8 (from the review):** `normalizeTags` (`vault/EntryQuery.kt:40`) cuts tags with `take(24)`, which can split a surrogate pair. It should cut by code points.
- **A behaviour change outside imports.** `OTPAUTH_MAX_NAME_CHARS` is now counted in code points, so a scanned name with emoji keeps up to 256 code points (at most 512 UTF-16 units) instead of 256 units. ASCII names are cut exactly as before (the existing tests pass).
- **48 MiB is about 8 000 Bitwarden logins.** A real export larger than that gets «El archivo tiene demasiados datos. Clavero no lo lee.»

## Revisión experto-seguridad-cripto (fix round 1)

- **CWE-770, I-1.** The tree's memory is now bounded by an upper estimate made before parsing (`ImportJson.kt:69-127`), with constants measured on the shipped kotlinx version and rounded up (`:43-62`). The estimate is tested against the measurements, worst shapes included. The worst case is the 48 MiB tree plus the file and its text, instead of about 110 MB. Escapes and duplicate keys are over-counted, never under-counted.
- **Never cut silently, M-2, spec :135.** The rule now covers every TOTP path of the import (`ImportModel.kt:190,224`; `OtpAuthList.kt:87`). The cut in `otpName` no longer splits a surrogate pair (`Totp.kt:162-167`), which also fixes the vault's own QR and paste paths.
- **Unicode integrity, M-1.** Folder and group names go through `wellFormed` (`ImportJson.kt:296`, `AegisJson.kt:42`).
- **One encoder for the link, M-5.** `otpEncode` (`Totp.kt:248`) keeps the writer and `readOtpAuth` on one rule. The injection tests still pass (`ImportModelTest`, the «inyección» cases).
- No new crypto, dependency, permission or network access; nothing is logged.

## Revisión Ingeniero Móvil Senior (fix round 1)

- **Memory.** The 48 MiB budget sits under a 192 MB heap together with the 16 MB bytes and the about 32 MB text. ART's smaller headers make the JVM-measured constants conservative. The OOM guard of R11b stays in Task 9 as a last line of defence.
- **API compatibility.** The new fields are `OtpAuthRead.Usable.namesCut` and `MigrationResult.importedNamesCut`, both with defaults, so `VaultEntryScreen` and `EntryEditSessionTest` compile unchanged. `otpEncode` became internal, with no change in behaviour.
- **Performance.** The scan is still one linear pass with no allocation (`ImportJson.kt:86-127`). The budget tests parse about 0.6 M nodes in under 20 s in CI.

## Textos para el Abogado Digital (fix round 1)

- `ImportTexts.kt:100` `NOTE_LINE_UNNAMED_FIELD`: «Campo» (the label of a custom field without a name, written into imported notes).
- `IMPORT_JSON_TOO_MANY_VALUES` («El archivo tiene demasiados datos. Clavero no lo lee.») is unchanged; it is now shown when the estimated tree passes 48 MiB.

---

# Fix round 2 (FIX_BASE 776b27e → 795d3bd)

**Commit:** `795d3bd` «1.4.0: importar, segunda ronda de arreglos de la tarea 7», pushed to `ccr-a1a6eabb-765f9g`.

## Findings → changes → covering tests

| Finding | Change | Covering tests |
|---|---|---|
| **I-1** (ruling R11c): a non-empty array's backing array was not counted (`[1]`: 173.5 B measured, 130 B estimated; 10 nested levels 1.84 times over the estimate) | The scan now models how containers grow (`ImportJson.kt:98-178`). It keeps three small arrays, one slot per level (at most `JSON_MAX_DEPTH`, made once per scan): whether the level is an object, its element or member count, and the room it has made. **Arrays** (`element()`, `:107`): a value that starts in an array is an element; the first element makes room for 10, then the room grows by half each time it is full (10, 15, 22, 33, 49…), as `ArrayList` does; each new backing array is charged as soon as it is made, minus the old one (`refArrayBytes`, `:65`: 16 B header + 4 B a slot, aligned to 8), so the unused room is included. **Objects** (`member()`, `:117`): each `:` in an object is a member; the first makes a table of 16 slots, which doubles once there are more than ¾ as many members (13, 25, 49…), as `LinkedHashMap` does. The constants were re-derived from the object layouts (`:44-56`): object 88, member 88 (entry plus name string), array 48, primitive 80, 2 B a character. The KDoc (`:23-41`) now describes this model instead of a table of fixed sizes, and points to the measurement table below. | `ImportJsonTest.theEstimateIsNeverBelowTheMeasuredTree`: every shape in the table below, with measured values rounded up, plus two real items of the Bitwarden fixture. `theModelFollowsHowTheTreeGrows`: exact estimates at the growth boundaries (arrays of 10/11/16/23/35, objects of 12/13/25, 62 nested levels). `aTreeOverTheBudgetIsRefusedBeforeParsing` |
| **Minor:** long KDoc lines at `ImportJson.kt:553` and `OtpAuthList.kt:742` (diff lines) | `folderNames` rewrapped (`ImportJson.kt:336-338`); `addMigration` rewrapped (`OtpAuthList.kt:77-81`). Also rewrapped: the other comment lines over 120 columns this task had written (`ImportModel.kt`, `ImportTexts.kt`, `BitwardenJson.kt`, `OtpMigration.kt`, `ImportJson.kt`). | (none needed) |
| **Minor:** tests built strings of tens of MB under a 20 s timeout | `checkJsonShape(text, budget)` and `readJsonImport(text, treeBudget)` now take the budget as a parameter (`ImportJson.kt:79,191`), defaulting to 48 MiB. The under/over test uses a 200 000 B budget: about 2 400 numbers, a binary search for the exact boundary, a long string at the boundary, and the shapes that cost more than they weigh. A single test checks the real 48 MiB limit, on an open file (scan only, about 1.3 MB): 0.06 s. The 8 M-char string test is removed. | `aTreeOverTheBudgetIsRefusedBeforeParsing` (0.013 s), `theRealBudgetIsTheOneReadImportUses` (0.059 s) |
| **Minor:** `OtpMigration.kt:172` `.take(80)` could split a surrogate pair | It now uses the existing `takeCodePoints` (`crypto/FileCrypto.kt:279`) at `OtpMigration.kt:173`, and so does `otpName` (`Totp.kt:162`). There is one code-point cut helper in the codebase. | `OtpMigrationTest.theNameInALeftOutTextIsCutWithoutSplittingAPair` (79 letters + 🛒 + 1 letter: the emoji stays whole) |

`EntryQuery.kt:40` stays for Task 8, as ruled.

## Measurements (I-1)

- **Script:** `/tmp/claude-0/-home-user-Seguridad/afa106da-72c3-5be8-bbce-c61e4a0844be/scratchpad/mem3/M4.java`. It calls the compiled `jsonTreeEstimate` from `jvm-check/build/classes`, so the numbers come from the real code and not from a copy of it.
- **Setup:** kotlinx-serialization-json 1.11.0, JDK 21, `-XX:+UseSerialGC -Xmx3g`, live heap after GC. Each shape is repeated N times inside an outer array; the outer array's own slots are included.
- **Result:** every estimate is at or above its measured value.

| Shape (each repeated N times in an outer array) | N | Measured B/element | Estimate B/element | Estimate/measured |
|---|---|---|---|---|
| `{}` | 200000 | 85.2 | 92.8 | 1.09 |
| `[]` | 200000 | 44.8 | 52.8 | 1.18 |
| `1` | 400000 | 77.4 | 87.4 | 1.13 |
| `true` | 400000 | 77.4 | 93.4 | 1.21 |
| `null` | 400000 | 5.4 | 93.4 | 17.29 |
| `""` | 400000 | 29.4 | 85.4 | 2.90 |
| `{"k":1}` | 200000 | 324.8 | 344.8 | 1.06 |
| `{"":""}` | 200000 | 228.8 | 340.8 | 1.49 |
| `[1]` | 200000 | 172.8 | 190.8 | 1.10 |
| `[true]` | 200000 | 172.8 | 196.8 | 1.14 |
| `{"a":[1]}` | 200000 | 420.8 | 448.8 | 1.07 |
| one-element arrays nested 2 deep | 50000 | 269.7 | 295.7 | 1.10 |
| one-element arrays nested 10 deep | 50000 | 1037.7 | 1127.7 | 1.09 |
| one-element arrays nested 31 deep | 10000 | 3053.6 | 3311.6 | 1.08 |
| one-element arrays nested 62 deep | 10000 | 6029.6 | 6535.6 | 1.08 |
| array of 10 numbers | 40000 | 820.7 | 928.7 | 1.13 |
| array of 11 numbers | 36363 | 917.2 | 1035.2 | 1.13 |
| array of 15 numbers | 26666 | 1204.7 | 1362.7 | 1.13 |
| array of 16 numbers | 25000 | 1301.1 | 1469.1 | 1.13 |
| array of 22 numbers | 18181 | 1732.6 | 1960.6 | 1.13 |
| array of 23 numbers | 17391 | 1852.9 | 2090.9 | 1.13 |
| array of 33 numbers | 12121 | 2572.6 | 2910.6 | 1.13 |
| array of 34 numbers | 11764 | 2708.8 | 3056.8 | 1.13 |
| array of 35 numbers | 11428 | 2780.9 | 3138.9 | 1.13 |
| array of 49 numbers | 8163 | 3788.6 | 4286.6 | 1.13 |
| array of 50 numbers | 8000 | 3956.7 | 4464.7 | 1.13 |
| object of 1 members `"kN":1` | 200000 | 324.8 | 346.8 | 1.07 |
| object of 2 members `"kN":1` | 100000 | 484.3 | 520.3 | 1.07 |
| object of 12 members `"kN":1` | 16666 | 2085.1 | 2265.1 | 1.09 |
| object of 13 members `"kN":1` | 15384 | 2309.5 | 2505.5 | 1.08 |
| object of 24 members `"kN":1` | 8333 | 4068.5 | 4440.5 | 1.09 |
| object of 25 members `"kN":1` | 8000 | 4356.7 | 4744.7 | 1.09 |
| object of 48 members `"kN":1` | 4166 | 8038.0 | 8794.0 | 1.09 |
| object of 49 members `"kN":1` | 4081 | 8452.1 | 9224.1 | 1.09 |
| object of 97 members `"kN":1` | 2061 | 16645.4 | 18185.4 | 1.09 |
| object of 1 members `"kN":null` | 200000 | 252.8 | 352.8 | 1.40 |
| object of 13 members `"kN":null` | 15384 | 1373.5 | 2583.5 | 1.88 |
| object of 25 members `"kN":null` | 8000 | 2556.7 | 4894.7 | 1.91 |
| object of 49 members `"kN":null` | 4081 | 4924.1 | 9518.1 | 1.93 |
| string of 100 `Ж` | 20000 | 268.2 | 284.2 | 1.06 |
| string of 100 `x` | 20000 | 172.2 | 284.2 | 1.65 |
| string of 10 000 `Ж` | 200 | 20069.2 | 20085.2 | 1.00 |
| `{"a":[{},{}…]}` (review) | 300 | 85245.1 | 93263.1 | 1.09 |
| `{"a":[{"k":1}…]}` (review) | 300 | 325245.1 | 345263.1 | 1.06 |
| Bitwarden login (fixture item 1) | 20000 | 5868.2 | 7424.2 | 1.27 |
| Bitwarden card (fixture item 7) | 20000 | 2108.2 | 2460.2 | 1.17 |

- The tightest shapes are a long string outside Latin-1 (1.00: the estimate is 16 B over 20 069 B) and `{"k":1}` (1.06).
- The over-estimates are safe:
  - `null` is a shared singleton;
  - `""` shares its content string;
  - Latin-1 text takes one byte a character on the JVM and on ART;
  - an object with `null` values does not hold primitives.
- ART uses 8-byte headers, against 12 here, so the real cost on the phone is lower still.

## Evidence

- **RED (mutation check).** I removed the array backing cost from `element()` (`bytes += 0`) and put `.take(80)` back.
  - Command: `gradle -p jvm-check test --no-daemon --max-workers=1 --tests 'app.seguridad.imports.ImportJsonTest' --tests 'app.seguridad.crypto.OtpMigrationTest'`
  - Result: `29 tests completed, 4 failed`, namely `theEstimateIsNeverBelowTheMeasuredTree` («[1]: 260048 < 2000 × 173»), `theModelFollowsHowTheTreeGrows`, `aTreeOverTheBudgetIsRefusedBeforeParsing` and `theNameInALeftOutTextIsCutWithoutSplittingAPair`.
  - The code was then restored.
- **Covering tests:** `gradle -p jvm-check test --no-daemon --max-workers=1 --tests 'app.seguridad.imports.*' --tests 'app.seguridad.crypto.*' --tests 'app.seguridad.vault.VaultTotpTest'` → BUILD SUCCESSFUL, 441 passed.
- **Full verification after the last change:**
  - `gradle -p jvm-check test --no-daemon --max-workers=1` → BUILD SUCCESSFUL, **1570 tests, 0 failed**. Every ImportJsonTest case takes at most 0.17 s.
  - `export ANDROID_HOME=/opt/android-sdk; ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1` → BUILD SUCCESSFUL, **1575 app unit tests, 0 failed**. Lint shows the 16 known warnings; no new one, and no Kotlin warning.

## Revisión experto-seguridad-cripto (fix round 2)

- **CWE-770, I-1.** The estimate is now an upper bound for the containers the parser makes, growth slack included (`ImportJson.kt:107-128`). This was verified against the measured live tree for 47 shapes, including nesting 62 levels deep and every growth boundary. It also covers the prefix of a file the parser refuses halfway, because unclosed levels are charged as they grow. The worst case is the 48 MiB tree plus the file and its text; the OOM guard of R11b (Task 9) stays as the last line of defence.
- **Unicode.** The display text of a skipped migration account is cut by code points (`OtpMigration.kt:173`), through the one existing helper (`FileCrypto.kt:279`).
- No new crypto, dependency, permission or network access.

## Revisión Ingeniero Móvil Senior (fix round 2)

- **Cost of the scan.** It stays linear. It makes three arrays of 65 slots per scan and nothing per character; the nested functions are local closures over primitive state. Depth is checked before indexing (`ImportJson.kt:134`), so the arrays are never indexed past `JSON_MAX_DEPTH`.
- **Testability without large files.** The budget is now a parameter with a default (`ImportJson.kt:79,191`), so the tests no longer build 48 MiB inputs and the CI cost is milliseconds. Production always uses the default.
- **One code-point cut helper** (`takeCodePoints`) serves files, TOTP names and migration texts.

## Textos para el Abogado Digital (fix round 2)

- No new visible text. `IMPORT_JSON_TOO_MANY_VALUES` is unchanged; only its KDoc changed (`ImportTexts.kt:55`, the constant at `:56`).
