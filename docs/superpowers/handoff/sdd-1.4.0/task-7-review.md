# Task 7 review: JSON readers in clear (Bitwarden, Aegis, 2FAS) and otpauth lists

Diff `e903cbb..fb195cd`, read in two passes (main code to diff line 1259, then tests and fixtures). Code outside the diff was opened only for the named risks in «Hostile-input trace» and the Issues.

### Spec Compliance
- ✅ Spec compliant on every item the brief lists, with one exception:
  - Every Step 1 test exists and checks real behaviour: Bitwarden types 1 to 5, extra URIs and custom fields as «Nombre: valor», `login.totp` as a second TOTP, passkey as an extra Skipped, `folderId`, `favorite`, `itemWithNullLoginGoesToSkipped`; Aegis totp/hotp/steam/motp/yandex and groups; 2FAS tokenType and `groupId`; otpauth lines with blank lines and migration expansion; and `deepJsonIsRefused`, `wrongTypesAreBroken`, `encryptedBitwardenNeedsPassword` and `accountEncryptedBitwardenSaysHow` (the spec's message verbatim, spec :178 = `ImportTexts.kt:46-47`).
  - The interfaces exist with the brief's signatures (`readBitwardenPlain(json: JsonObject)`, `readAegisPlain(db: JsonObject)`, `readTwoFasPlain(root: JsonObject)`, `readOtpAuthList(text: String)`). They are registered in `Detect.kt:20-21`, and the parse runs after the depth scan (`ImportJson.kt:87` → `:89`).
  - The out-of-plan pieces are the ones R13 accepts (`ImportJson.kt`, `MigrationResult.leftOut`, `hostOf` internal).
  - ❌ **The exception (Minor M-2):** the spec says «Si un campo pasa del tope, la entrada va a «no entran» con su motivo; nunca se corta en silencio» (design :135). An Aegis or 2FAS issuer or account longer than 256 characters is cut without a word by `otpName` (`crypto/Totp.kt:156`, `take(OTPAUTH_MAX_NAME_CHARS)`), because `addTotp` only checks the 4096 sum (`ImportModel.kt:218`).
- ⚠️ Cannot verify from the diff:
  - Step 3, the owner's real exports, is pending, as the report says (`README.txt` in the fixtures).
  - The verification commands were not re-run here (the brief says not to). I only ran one scratch measurement, outside the repo (Important I-1).
  - Peak heap on the phone (Android heap limit) for the worst file. Neither the JVM test run nor the diff answers this.
  - That `readImport` runs off the main thread and that the screen discards a result after cancel or lock (decisions (a) and (b)). That belongs to Task 9. `readImport` is synchronous and cannot be interrupted mid-parse.
  - No copied code: I cannot prove where the code came from. Nothing in the diff looks like another importer's structure or identifiers: the code uses the project's own idioms (`ImportReadBuilder`, Spanish labels), and the fixtures use invented data.

### Strengths
- **A sound pre-parse scan.** `checkJsonShape` (`ImportJson.kt:37-71`) knows strings, `\"` and `\\`, makes one pass without allocating, and checks both bounds before `parseToJsonElement`. Unbalanced closers cannot hide depth, because kotlinx is a strict LL parser and fails at the first stray `]`/`}` before it reaches anything nested after it.
- **No type decoding.** Only `parseToJsonElement` runs, and values are read by fixed name with typed accessors. `WrongType` carries no message and no stack (`ImportJson.kt:115-118`). The diff has no `decodeFrom*`, reflection, `Log`, `println`, `Intent`, `Uri.parse` or `Linkify` (grep of the `+` lines).
- **No link injection.** `number()` follows the RFC 8259 grammar by hand (`ImportJson.kt:165-197`), so kotlinx's lenient unquoted literals (`6&period=10`) can never reach the link. Each part is percent-encoded into its own parameter (`ImportModel.kt:222-228`). The `:`+account label keeps a `:` from creating an issuer. All of these are tested.
- **CRY-B02 holds on every path.** The rule runs through `readOtpAuth` plus `isSupported`; migration codes are checked again (`OtpAuthList.kt:87-88`); a present setting is refused, and only a missing one takes the default (tested in Aegis and 2FAS).
- **The item bound applies after expansion.** It lives in the shared builder (`ImportModel.kt:167-170`), so a migration line can allocate at most 100 extra codes before the failure.
- **Notes are bounded and never cut.** `NotesBuilder` checks before it appends (`ImportJson.kt:265-276`), so there is never a partial item.
- **`MigrationLeftOut` lets the importer map reasons by kind.** It never parses the Spanish texts, and the skipped strings stay identical (OtpMigrationTest `leftOutAccountsSayWhyWithoutTheText`).
- **Hostile values are stored verbatim and tested.** Raw bidi and line-separator characters in the test sources are written as escapes (Trojan Source).

### Issues

#### Critical (Must Fix)
None.

#### Important (Should Fix)

**I-1. `JSON_MAX_TOKENS` does not keep the tree at «a few tens of MB». Measured, it is about 85–108 MB for a 3 MB file, and R11 rests on that claim.**
- **Where:** `ImportJson.kt:24-29` (the KDoc says the bound «bounds the tree the parser builds to a few tens of MB»); the report's «Concerns» and «Memory on Android»; ruling R11.
- **What I measured:** kotlinx-serialization-json 1.11.0 (the app's version) on JDK, SerialGC, live heap after GC, with a file just under 1 M tokens:

  | File | File size | Tree |
  |---|---|---|
  | `{"a":[{},{},…]}` | 3.0 MB | **84.6 MB** |
  | `[1,1,…]` | 2.0 MB | 76.9 MB |
  | `[[],[],…]` | 3.0 MB | 44.9 MB |
  | `["","",…]` | 3.0 MB | 28.9 MB |
  | `{"k":1}` × 333 000 (1 M tokens) | 2.7 MB | **108 MB** |

  Script: `/tmp/claude-0/-home-user-Seguridad/afa106da-72c3-5be8-bbce-c61e4a0844be/scratchpad/mem/Measure.java`.
- **Impact:** that is ~100 MB for the tree alone, on top of the caller's `ByteArray`, the decoded `String`, the vault and Compose. ART object sizes are close to the JVM's with compressed oops. `OutOfMemoryError` is deliberately not caught (`Detect.kt` KDoc: «the limits are what prevent them»). On a phone with a 192–256 MB heap limit, a crafted 3 MB file, or a real export near the bound, can crash the app. Owner decision (a) forbids this.
- **Fix (controller to re-rule with these numbers):**
  1. Weight the scan by cost: count an object or array `{`/`[` as 3, a key–value pair as 2, and a scalar as 1. Then set the bound from a measured budget, for example ~30 MB, which is ≈300 000 weighted units.
  2. Or keep 1 M and get the owner's explicit agreement to a ~110 MB transient peak, after a phone heap test (`Runtime.maxMemory()` on the owner's Honor).

  Either way:
  - correct the KDoc to the measured numbers;
  - add a jvm-check test that pins the worst shape (`{"k":1}` repeated) under the chosen budget, measured with `Runtime` after GC (tolerant), so the bound cannot drift again;
  - tell Task 9 to show IMPORT_BROKEN rather than crash if an OOM still escapes.

#### Minor (Nice to Have)

**M-1. Folder and group names skip the NUL and lone-surrogate rule that every other imported string follows.**
- **Where:** `ImportJson.kt:229-239` (`folderNames`), `AegisJson.kt:42` (`group`), `BitwardenJson.kt:101`.
- **Impact:** a JSON escape (`"name":"\ud800"` or `"\u0000"`) gives a folder that becomes a tag in Task 8. The decoder of the file (`decodeText`) can never produce such text; JSON escapes are the only way in, and this task's own invariant («NUL and lone surrogates make the element BROKEN, … could break the vault's later UTF-8 encoding») is not applied here.
- **Fix:**
  - In `folderNames`, give no folder for a record whose name is not `wellFormed` (as for an unreadable record). Do the same for Aegis `group` at `AegisJson.kt:42`.
  - Add one test per format.
  - Note for Task 8: `normalizeTags` (`vault/EntryQuery.kt:40`) cuts with `take(24)`, which can split a surrogate pair. It should cut by code points.

**M-2. Issuer and account are cut silently at 256 characters (spec :135, «nunca se corta en silencio»), and the cut can leave a lone surrogate.**
- **Where:** `ImportModel.kt:218`. The check sums the four parts against 4096 but does not compare each name with `OTPAUTH_MAX_NAME_CHARS`. `otpName` (`crypto/Totp.kt:156`) then applies `take(256)` on UTF-16 units.
- **Effect:**
  - An Aegis or 2FAS issuer of 300 characters enters with a 256-character `totp.issuer`, while the title keeps all 300.
  - `"x".repeat(255) + "🛒"` stores a lone high surrogate, the very thing `wellFormed` exists to stop.
  - The same cut reaches migration names (up to 1024 bytes) and long otpauth-list labels.
- **Fix:**
  - In `addTotp`, send an `issuer` or `account` longer than `OTPAUTH_MAX_NAME_CHARS` to `SkipReason.FIELD_TOO_LONG` before building the link.
  - Make `otpName` cut by code points (`offsetByCodePoints`), which also fixes the existing QR and paste paths.
  - Test: 257-character issuer → FIELD_TOO_LONG; 255 + emoji → no lone surrogate.

**M-3. A first URI that is `null` or empty pushes the real URL into the notes.**
- **Where:** `BitwardenJson.kt:118-122`. `first` turns false on the first element, whatever its value.
- **Effect:** `"uris":[{"uri":null},{"uri":"https://a"}]` gives an empty `url` and «URL: https://a» in the notes. The duplicate check (title + user + host) and the host title fallback then miss it.
- **Fix:** take the first **non-empty** URI as the URL (`if (url.isEmpty() && uri.isNotEmpty()) url = uri else notes.line(...)`), and add a test.

**M-4. A custom field with an empty name gives a line «: valor».**
- **Where:** `BitwardenJson.kt:135` with `ImportJson.kt:261-263`.
- **Fix:** when the name is blank, write the value alone, or under a generic label (for example the existing «Campo», if one exists, or a new `ImportTexts.kt` constant that then goes to the Abogado list). Add a test.

**M-5. A second percent-encoder that duplicates an existing one.**
- **Where:** `linkPart` (`ImportModel.kt:235`) is the same as the private `otpEncode` in `crypto/Totp.kt` (just below `readOtpAuth`), which is documented as the encoding `readOtpAuth` reads.
- **Fix:** make `otpEncode` `internal` and use it in `addTotp`, so both ends stay one rule.

### Hostile-input trace (1–10)

1. **Depth.**
   - Root = level 1; `++depth > 64` throws at the 65th opener (`ImportJson.kt:57`). Tests: 64 passes and 65 fails, in `ImportJsonTest` and in each format.
   - Strings: on `"` the scanner skips to the unescaped `"`, and `\` skips two (`:49-52`). `"\\\""` is backslash, quote, still inside the string; `"\\\\"` closes after two escaped backslashes. Tested in `bracketsInsideStringsDoNotCount`.
   - An unterminated string runs to the end and the loop exits (`i ≥ n`). The parser then fails, so the result is IMPORT_UNKNOWN.
   - Each index is visited once, so the cost is linear (8 M levels refused in <5 s, tested).
   - Negative depth from stray closers cannot help an attacker: the strict parser fails at the first stray closer, before any later nesting.
2. **Size.**
   - The token bound is checked during the scan, before `parseToJsonElement` (`:69`, `:87-89`). A file of `[[[[…` stops at depth 65. A file of `{"a":1,"a":1,…}` stops at 1 M tokens (each key and each value counts).
   - A single 16 MiB string is 1 token. The memory is the file, the `String` and one copy in the tree, bounded by `IMPORT_MAX_BYTES`.
   - Many tiny tokens: bounded before parsing, but the bound allows ~85–108 MB of tree → **I-1**.
3. **Duplicate keys.**
   - The last value wins (kotlinx map put), documented at `ImportJson.kt:17` and tested (`duplicateKeysTakeTheLastValue`, also at the root for `encrypted`). There is no security effect: nothing is decrypted or authorised on these keys.
   - NUL and lone surrogates in values give BROKEN (`wellFormed`, `ImportJson.kt:206-222`, applied in every reader), except folder names (**M-1**).
   - U+202E and U+2028 are stored verbatim (`hostileValuesAreStoredExactly`); TOTP issuer and account go through `otpName`, which removes controls and bidi. Display sanitising is Task 9 (R9).
4. **Migration expansion.**
   - Each line is at most 64 K characters and is copied only after that check (`OtpAuthList.kt:71-72`).
   - `parseOtpMigration` caps a line at 100 parameters (`OtpMigration.kt`, `MIGRATION_MAX_PARAMETERS`).
   - Every code and every left-out account passes through `nextIndex`, which throws at the 20 001st (`ImportModel.kt:167-170`). So at most 20 000 results are held, plus one line's 100. Non-blank lines are also capped (`OtpAuthList.kt:56`). Tested: 200 × 100 passes and 201 × 100 fails, in <10 s.
5. **otpauth lines.**
   - Scheme case: `regionMatches(..., ignoreCase = true)` (`OtpAuthList.kt:68-69`), and `readOtpAuth` compares scheme and host case-insensitively (`Totp.kt`, start of `readOtpAuth`).
   - `otpauth://hotp` → `isOtherOtpAuth` → NOT_TOTP.
   - A missing `secret`, or one that is not Base32 → `NotUsable` → BROKEN.
   - A duplicate `secret`: `toMap()` keeps the last one. This is the existing vault reader, not this diff; noted for the whole-branch audit, informational.
   - A huge issuer or label: the line is refused past 4096 before any copy. Up to 4096, `otpName` cuts names to 256 (**M-2**).
   - Invalid UTF-8 in percent escapes: `URLDecoder` puts U+FFFD, never a lone surrogate, and never throws out (everything is inside `try` in `readOtpAuth`; `labelNames` catches at `OtpAuthList.kt:114`).
   - `period=0`, `digits=99`, MD5 → `UnsupportedSettings` → TOTP_UNSUPPORTED, never changed.
6. **Numbers** (`ImportJson.kt:165-197`).
   - `1e400` follows the grammar and has an exponent → `NOT_A_WHOLE_NUMBER` → refused by `isSupported`, or BROKEN for `type`.
   - `-0` → 0 → refused (BROKEN for `type`; a 0 period is unsupported).
   - `30.5` and a huge integer → −1 → refused.
   - A string, or `true` for digits → `WrongType` → BROKEN.
   - Nothing throws past the reader. Tested in `hugeNumbersAreBroken` and in Aegis and 2FAS.
7. **Exceptions.**
   - The parser's `SerializationException` and `IllegalArgumentException` → null → IMPORT_UNKNOWN, and the message is never used (`ImportJson.kt:90-94`).
   - `WrongType` is caught in each element reader (`BitwardenJson.kt:87`, `AegisJson.kt:53`, `TwoFasJson.kt:47`).
   - `ImportFailure` → `Failed`, and any other `Exception` → IMPORT_BROKEN (`Detect.kt:44-48`).
   - `StackOverflowError` cannot happen: depth is capped at 64, far under kotlinx's recursive limit.
   - `OutOfMemoryError` is not caught, by design → **I-1**.
8. **Notes, title, username and URL.**
   - Notes are never cut: a part that would pass `MAX_NOTES` sets `tooLong`, and the item is FIELD_TOO_LONG (`ImportJson.kt:265-276`), so no surrogate pair is split.
   - Title, username, password and URL are compared with their limits, then skipped, never cut (`BitwardenJson.kt:164-168`, `AegisJson.kt:51`, `TwoFasJson.kt:45`).
   - Report titles are cut by code points in `plainReportText` (`ImportModel.kt:105-119`).
   - Exception: the TOTP names → **M-2**.
9. **URIs.** `login.uris` only fills `url` or a notes line (`BitwardenJson.kt:118-122`). The grep of the diff for `Intent|Uri.parse|Linkify|autoLink|openUri|LocalUriHandler|fromHtml|WebView` finds nothing in code. `javascript:` and `intent:` are stored as text (tested).
10. **Secrets.**
    - The diff has no `Log`, `println` or `printStackTrace`.
    - Skipped titles are the name, issuer, account or «Línea N», never a key or a password (tests `secretsNeverReachTheSkippedList` and `hostileLinesAreData`).
    - `WrongType` carries nothing.
    - `NotesBuilder.build` zeroes its buffer (`ImportJson.kt:279-285`). The parsed tree and the source `String` still hold every secret until GC; that is inherent to a `String` and tree reader, and informational only.

### Revisión experto-seguridad-cripto
- **Threat model:** a crafted import file (JSON, otpauth list, migration QR text) from another app or person. Targets: availability (CWE-400/674/770), parameter injection into the internal otpauth link, deserialisation (CWE-502), information exposure (CWE-209/532), and TOTP settings being silently changed (CRY-B02).
- **CWE-674 (recursion):** depth is capped before parsing (`ImportJson.kt:57`), and the scan is consistent with the parser's string grammar (`:49-52`). Mitigated.
- **CWE-770 (allocation without limits):** the file (`IMPORT_MAX_BYTES`), the tokens (`:69`) and the items after expansion (`ImportModel.kt:168`) are all bounded. But the token bound's memory cost is about 3–4× what the code claims (**I-1**, measured). Open.
- **CWE-88-like injection into the otpauth link:** each part is URL-encoded (`ImportModel.kt:222-228`), and numeric settings come only from grammar-valid JSON numbers (`ImportJson.kt:169`). Mitigated and tested (`inyección` cases).
- **CWE-502:** tree only, with no class decoding (`ImportJson.kt:89`). Mitigated.
- **CWE-209:** parser messages, which quote the input, are discarded (`ImportJson.kt:90-94`). Mitigated and tested (`aBrokenJsonIsUnknownAndItsTextNeverShows`).
- **CRY-B02:**
  - One rule through `readOtpAuth` and `isSupported` (`ImportModel.kt:184-196`), with a second check on migration codes (`OtpAuthList.kt:87-88`). An empty `algo` is refused, not defaulted (`Totp.kt`, `algorithm.isNotEmpty()`).
  - Silent cut of names (**M-2**).
- **Unicode integrity:**
  - `wellFormed` is applied to every value except folder names (**M-1**).
  - Bidi characters are stored verbatim (R9), and removed from TOTP names by `otpName`.
- **Constant-time comparison:** no secret is compared in this task.
- **Crypto:** none is added (encrypted formats are NeedsPassword, R13). No new dependency (`app/build.gradle.kts:106`, kotlinx-serialization-json 1.11.0 already present), permission or network host.

### Revisión Ingeniero Móvil Senior
- **Architecture:**
  - pure Kotlin in `imports/`, with a shared JSON layer (`ImportJson.kt`) and thin readers per format, all feeding one `ImportReadBuilder`;
  - file sizes are reasonable (largest new file: `ImportJson.kt`, 286 lines);
  - `MigrationResult.leftOut` has a default, so `VaultEntryScreen.kt:247` (the only other caller of `parseOtpMigration`) compiles unchanged, and the `skipped` texts are identical;
  - `hostOf` is internal and used only by `BitwardenJson.kt:160`.
- **Memory on Android:** the JVM numbers of **I-1** carry over roughly to ART (4-byte references, 8-byte headers). With no `largeHeap`, a ~100 MB transient on top of the app is a real OOM risk on mid-range phones. It needs a heap test on the owner's device.
- **Concurrency and lifecycle:**
  - `readImport` is synchronous and cannot be interrupted mid-parse (kotlinx has no cancellation check). Task 9 must run it on `Dispatchers.Default` from the ViewModel, check `isActive` and the vault lock state after it returns, and discard a late result (decision (b)).
  - Nothing here touches `rememberSaveable` or a `Bundle`.
- **Performance:**
  - Every pass is linear, with no regex on file data; the number grammar is hand-written (`ImportJson.kt:176-197`).
  - `linkTitle` parses each list link once more with `readOtpAuth` (`OtpAuthList.kt:105-108`). That is bounded and acceptable.
- **CSV compatibility:** CSV still runs first (`Detect.kt:19`). The shared count now bounds CSV too (R12, accepted).
- **Tests:**
  - They exercise behaviour through the public `readImport`, with timeouts on the large cases and fixtures from invented data.
  - Missing: a memory pin for the worst JSON shape (I-1), tests for folder names that are not well formed (M-1), and a null-first-URI test (M-3).

### Textos para el Abogado Digital (revisión final)
All in `app/src/main/java/app/seguridad/imports/ImportTexts.kt`, verbatim:
- `:46-47` `IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED`: «Esta exportación está cifrada con tu cuenta de Bitwarden. Expórtala como "JSON" o como "JSON (protegido con contraseña)".» (names Bitwarden).
- `:50` `IMPORT_JSON_TOO_DEEP`: «El archivo tiene más de 64 niveles anidados. Clavero no lo lee.»
- `:53` `IMPORT_JSON_TOO_MANY_VALUES`: «El archivo tiene demasiados datos. Clavero no lo lee.»
- `:56` `importLineTitle`: «Línea N».
- `:59` `NOTE_LINE_URL`: «URL».
- `:62-69` `BITWARDEN_CARD_LINES` labels, written into imported notes: «Titular», «Marca», «Número», «Mes de caducidad», «Año de caducidad», «Código de seguridad».
- `:72-91` `BITWARDEN_IDENTITY_LINES` labels, written into imported notes: «Tratamiento», «Nombre», «Segundo nombre», «Apellidos», «Dirección», «Dirección 2», «Dirección 3», «Ciudad», «Provincia o estado», «Código postal», «País», «Empresa», «Correo», «Teléfono», «Número de seguridad social», «Usuario», «Pasaporte», «Permiso de conducir».
- `:97` `BITWARDEN_STEAM_PREFIX` = `steam://`: not shown to the user; it lives here because it names a product (R2).
- If M-4 adds a label for unnamed fields, it joins this list.

### Assessment
**Task quality:** Needs fixes

**Reasoning:** The readers are careful and well tested against hostile input. But the JSON token bound that ruling R11 accepted lets a ~3 MB file build a ~85–108 MB tree (measured), against the code's own «a few tens of MB» and owner decision (a). It needs a re-ruling or a weighted, lower bound with a memory test. The five Minors (folder names, silent cut of TOTP names, null first URI, unnamed fields, duplicate encoder) are small and each has a stated fix.
