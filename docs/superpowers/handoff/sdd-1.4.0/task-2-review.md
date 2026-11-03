# Task 2 review: Laboratorio de hashes (fba2725..c36b08a)

### Spec Compliance

**Mostly compliant, with one gap against the global constraints and one against the controller's carry-over requirement.** File and line numbers below are for head `c36b08a`.

What the brief and the spec section ask for, and where it is:
- **Five algorithms, all from Bouncy Castle:** `SHA256Digest`, `SHA512Digest`, `SHA3Digest(256)`, `Blake2bDigest(512)` and `Blake3Digest(256)`, at `HashLab.kt:25-40`. They go through `Digest.update` in 64 KiB chunks, at `HashLab.kt:57,64-85`. No new dependency.
- **Interfaces:** every interface the brief names is there: `HashLab.kt:64` and `:76` (both `digest`), `:90` (`hmacSha256`), `:98` (`sameDigest`), `:131` (`hexDecode`), `:150` (`base64Decode`) and `:177` (`parseExpected`).
  - `sameDigest` uses `MessageDigest.isEqual`, as the spec asks.
  - `parseExpected` takes hex in any case, with spaces, colons or line breaks, or Base64, and only for 32 or 64 bytes.
- **Tests:** all ten tests the brief lists are in `HashLabTest.kt`, and so is the 10 MB streaming check (`:74-79`).
- **Test vectors:** I checked each one against Python `hashlib`/`hmac` and against the BLAKE3 team's `test_vectors.json`, fetched from GitHub. All are the full official values with their source cited:
  - SHA-256 «abc», FIPS 180-4 B.1 (`:18-21`);
  - SHA-512 «abc», FIPS 180-4 C.1 (`:23-30`);
  - SHA3-256 «abc», FIPS 202 (`:32-35`);
  - BLAKE2b-512 «abc», RFC 7693 Appendix A (`:37-44`);
  - BLAKE3 of the empty input, `af1349b9…62f3262`, `input_len` 0 (`:46-49`);
  - RFC 4231 cases 1 and 2 (`:51-61`).
- **Screen:**
  - the four tabs, the algorithm selector, «Huella esperada» with «Coincide» / «No coincide» (`HashLabScreen.kt:241-253, 392-408`);
  - `OPEN_DOCUMENT` read on `Dispatchers.IO`, with progress and «Cancelar» (`:164-199, 267-283`);
  - «Lo que no protege» (`:348-353`, text at `HashLabTexts.kt:12-15`);
  - HMAC with the key typed as text or hex (`:291-321`);
  - Base64 and hex, encode and decode (`:322-346`).
- **Route and Tools entry:** `tools/hash` now opens `HashLabScreen` (`AppNavHost.kt:250`), and the Tools entry carries the title and subtitle the brief asks for (`ToolsScreen.kt:47`). `FileHashScreen` and its ViewModel are removed.
- **FilterChip rows instead of a TabRow:** acceptable. The behaviour is the same, the rows wrap on narrow screens and the chips expose their selected state.

Gaps:
- **Missing: the global constraint «dropped … on vault lock».** The HMAC key, the message, the hashed text and the decoded text are dropped only in `onCleared` (`HashLabScreen.kt:201-209`). See Important 1.
- **Controller requirement «every assertion of the removed tests is kept or carried over»: partly met.** The old M8 assertion checked that the screen shows «No se pudo leer el archivo.» when `s.failed` is set. The new test only checks that the string exists in `HashLabTexts.kt` (`PlatformSourcesTest.kt:127`). See Important 2.
- The SHA-256 assertions are kept:
  - the old `sha256Hex` cancel tests and the «abc» SHA-256 test are untouched, because `sha256Hex` and `sameHash` still exist;
  - the cancel checkpoint is carried over in `HashLabTest.kt:99-115`;
  - `job?.cancel()`, `finish(run…)`, `enabled = !s.busy` and the IO name read are carried over in `PlatformSourcesTest.kt:98, 113-119`.
- ⚠️ The diff cannot show the behaviour on a device: the picker, the progress redraws and how the screen feels with a 100 000-character field. The implementer says the UI was only compiled and source-tested.

### Strengths

- **The core is pure Kotlin and holds no crypto of its own:** BC digests, BC `HMac` and `java.util.Base64` (`HashLab.kt:1-17`). The reason for using BC `HMac` (it accepts an empty key, which `javax.crypto.Mac` refuses) is written down (`:89`).
- **Hostile input is handled carefully:**
  - Every decoder is bounded: 1 M characters for the decoders and 512 for the expected hash. They return null and never throw (`HashLab.kt:131-182`).
  - The Base64 decoder is strict about padding and refuses a length no encoder makes (`end % 4 == 1`) (`:154-158`).
  - Text that is all hex is never retried as Base64, so a hex hash with a digit missing cannot slip through as Base64 (`:177-182`).
  - The tests cover this: `expectedNeverThrowsOnBadInput`, `decodersRefuseAHugeInput` and `decodersReturnNullOnBadInputAndNeverThrow` (`HashLabTest.kt:145-180`).
- **Every field refuses an over-long paste rather than cutting it** (`HashLabScreen.kt:143-150`).
- **The comparison runs in constant time on bytes, not on normalised strings** (`HashLabScreen.kt:400-406`). The old over-claiming verdicts are gone: «el archivo es el original» and «no uses este archivo».
- **File reading:**
  - The stream is closed by `use`.
  - A missing stream becomes an `IOException`, where the old code used `!!` (`:179-180`).
  - `CancellationException` is rethrown (`:185-186`).
  - `ensureActive` runs every 64 KiB.
  - The `FileHashSession` run id guards against a slow earlier file showing late.
  - The new `cancelled(run)` refuses a stale run, and it has its own test (`CipherSessions.kt:179-187`, `CipherSessionsTest.kt:228-238`).
- **No `rememberSaveable`, every copy goes through `copySensitive`, and nothing is logged.** A source test checks the first two (`PlatformSourcesTest.kt:121-126`).
- **The UI texts contain none of the forbidden words.** The verdicts stay modest.

### Issues

#### Critical (Must Fix)
None.

#### Important (Should Fix)

1. **The HMAC key, the message, the text and the decoded text survive a vault lock, leaving the app and a bottom-tab switch, and the screen says they are deleted when you leave it.**
   - **What the code does:** `HashLabScreen(onBack)` gets neither the vault nor `lockAfterMs` nor `awayInOtherApp` (`AppNavHost.kt:250`; `HashLabScreen.kt:231-234`). It has no `ScreenGuard` (`ToolColumn(HASH_LAB_TITLE, onBack)` at `:239`). The fields are wiped only in `onCleared` (`:201-209`).
   - **What the other tools do:** they all take these (`AppNavHost.kt:237-238`; `TextCryptoScreen` with `GuardEffects` at `SecurityToolsScreen.kt:367-372`).
   - **Why `onCleared` is not enough:** the bottom bar navigates with `popUpTo(start) { saveState = true }` and `restoreState = true` (`AppNavHost.kt:170-178`). Tapping «Bóveda» therefore keeps the `tools/hash` entry and its ViewModel alive, so `onCleared` does not run.
   - **Why this matters:**
     - The HMAC intro says «La clave y el mensaje se quedan en esta pantalla y se borran al salir de ella» (`HashLabTexts.kt:35-36`). That is false for a tab switch, and the project's veracity rule applies.
     - It breaks the global constraint «Secrets … are dropped on back, on vault lock and on cancel». The ViewModel's own KDoc says these values «can be secrets» (`HashLabScreen.kt:108-114`).
     - The threat model names «quien tiene el móvil desbloqueado». The message, the text and the decoded text are on screen in the clear, and the key is behind a `PasswordField`.
   - **Fix:**
     - Follow the `TextCryptoScreen`/`BreachScreen` pattern: pass `vault`, `lockAfterMs` and `container.awayInOtherApp`; wipe `hmacKey`, `hmacMessage`, `text`, `codecInput` and `expected` on lock and on away; and add a `ScreenGuard` to `ToolColumn` with `TOOLS_AWAY_WIPE_NOTE`.
     - Alternatively, make the HMAC text true and wipe on the entry's `ON_STOP` as well.
     - Add a source test like the ones for the other tools.

2. **The M8 carry-over is weaker than the assertion it replaces (controller requirement).**
   - The removed test asserted `if (s.failed) Text("No se pudo leer el archivo."` in the screen source, which pins that an unreadable file is reported.
   - The new test asserts only `"No se pudo leer" in main("tools/hashlab/HashLabTexts.kt")` (`PlatformSourcesTest.kt:127`). That passes even if the screen stops showing the failure.
   - **Fix:** assert `"if (s.failed) Text(HASH_LAB_FILE_FAILED" in src`. That is the line at `HashLabScreen.kt:284`.

#### Minor (Nice to Have)

1. **There is a BLAKE3 vector only for the empty input** (`HashLabTest.kt:46-49`).
   - The empty input never crosses a 1024-byte chunk, so it does not exercise BLAKE3's chunk chaining or tree merge. `streamingEqualsInMemory` (`:74-79`) only compares BC with itself.
   - I checked BC 1.85 against the official `test_vectors.json` (input byte `i % 251`):
     - `input_len` 1025 gives `d00278ae47eb27b34faecf67b4fe263f82d5412916c1ffd97c8cb7fb814b8444`;
     - `input_len` 102400 gives `bc3e3d41a1146b069abffad3c0d44860cf664390afce4d9661f7902e7943e085`.
   - **Fix:** add both, so the app's own BC 1.86 is pinned against the reference on the multi-chunk path. That is the case users meet when they compare with `b3sum`.
2. **The File tab refuses an over-long «huella esperada» paste in silence.**
   - `vm.limited(v, EXPECTED_FIELD_CHARS)` sets `tooLong` (`HashLabScreen.kt:286-288`), but the File branch (`:267-290`) never renders `TooLongNote`. That contradicts the KDoc «a refused paste is said, not cut» (`:142`).
   - In the Text and HMAC tabs, the note for an expected-field refusal also appears under the input field, not under the expected field.
   - **Fix:** render the note next to the field that refused, in every tab. For example, give `ResultAndCheck` its own flag.
3. **The hex encoder duplicates `crypto.hex` and is slow on the main thread.**
   - `hexEncode` uses `String.format` per byte (`HashLab.kt:103`). This duplicates `crypto/Hashes.kt:6`, which the brief says to consume.
   - The Base64/hex tab encodes up to 10 000 characters, which is up to about 30 000 bytes of UTF-8. That means about 30 000 `String.format` calls inside `remember` on every keystroke (`HashLabScreen.kt:338`), which is a visible stall on a mid-range phone with a long non-ASCII paste.
   - **Fix:** one table-based encoder (`Hashes.kt:8` already has `HEX_DIGITS`) with a lowercase variant, used by both.
4. **Progress is written to Compose state from the IO thread on every 64 KiB, and the write can be stale.**
   - `{ ensureActive(); readBytes = counting.count }` (`HashLabScreen.kt:182`): if a new pick cancels the job between the two statements, the old run's byte count overwrites the `readBytes = 0` of the new run until that run's first chunk.
   - **Fix:** set `readBytes` only if `run == mine`, or publish progress through a throttled value, for example every 1 MiB, applied on the main thread.
5. **A hash of the other length gets only «No coincide».**
   - Example: the user pastes a 64-byte SHA-512 while SHA-256, the default, is selected. `sameDigest` returns false (`HashLabScreen.kt:400-406`) and the user may conclude the file was altered.
   - **Fix:** when `want.size != result.size`, say that the expected hash is for a 64-byte or a 32-byte algorithm and that they should choose another one.
6. **Changing the algorithm on the Text tab re-reads the chosen file in the background** (`HashLabScreen.kt:158-162`).
   - Tapping the algorithm chips while hashing text starts a new full read of a file that may be several GB, with no visible progress on that tab.
   - **Fix:** re-hash only when the File tab is shown, or when it is next opened.
7. **Two weak test assertions.**
   - `assertTrue(calls >= 1)` (`HashLabTest.kt:114`) should be `assertEquals(2, calls)`: one check before the 3-byte read and one before EOF.
   - No test pins that the screen renders `HASH_LAB_NOT_PROTECTED` («Every tool says what it does not protect»). One source assertion in the COV1-2 test would do.

### Revisión experto-seguridad-cripto

- **Primitives:** only standard ones, from Bouncy Castle and `java.util.Base64` (`HashLab.kt:9-16, 33-39, 90-95`). Nothing of our own, and no new dependency.
- **Vectors:** every official vector is correct, checked independently with Python `hashlib`/`hmac` and the official BLAKE3 JSON. BC's `Blake3Digest` also matches the official multi-chunk vectors (1025 and 102400 bytes), checked locally with BC 1.85 (Minor 1).
- **Comparison:** `MessageDigest.isEqual` on decoded bytes (`HashLab.kt:98`, `HashLabScreen.kt:404`). Only the length leaks, which is public anyway. There is no string normalisation or `==` on digests.
- **HMAC:**
  - It is RFC 2104 through BC `HMac`, and accepts an empty key (`HashLab.kt:89-95`), with RFC 4231 cases 1 and 2.
  - A text key goes in as UTF-8 with no KDF. That is correct for a tool that computes an HMAC from a given key, and it is what the spec asks for. It does not claim to be password hashing.
- **Hostile input:**
  - All parsers are bounded and never throw (`HashLab.kt:131-182`), and every field length is capped (`HashLabScreen.kt:143-150, 358`).
  - A provider that throws mid-read leads to `failed` (`:183-189`), and the stream is closed by `use`.
  - Cancellation stops the read within 64 KiB, and `CancellationException` is rethrown.
- **Secrets at rest in memory:**
  - The HMAC key, the message and the decoded text are not wiped on vault lock, on away or on a bottom-tab switch, and the text about it overstates the wiping (Important 1, CWE-226/CWE-524 class: sensitive data kept longer than stated).
  - They never reach a `Bundle`: no `rememberSaveable`, and a source test checks it (`PlatformSourcesTest.kt:122`).
- **Clipboard:** every copy uses `copySensitive` (`HashLabScreen.kt:385`), and the source test bans `setPrimaryClip`/`LocalClipboard` (`PlatformSourcesTest.kt:125`).
- **Logs:** there are no `Log` calls in the diff.
- **«Lo que no protege»:**
  - The text is accurate: a match proves identity with the hash, not the origin of the hash (`HashLabTexts.kt:12-15`).
  - The verdicts no longer claim authenticity: «Coincide» and «No coincide» replace «el archivo es el original».

### Revisión Ingeniero Móvil Senior

- **State:**
  - It lives in a nav-entry-scoped ViewModel that holds the application context, with no leak (`HashLabScreen.kt:115, 234`).
  - The work runs in `viewModelScope` with `withContext(Dispatchers.IO)`. The previous job is cancelled on a new pick (`:165`), and the button is off while busy (`:270`).
- **Lifecycle:** `onCleared` does not run when the bottom bar saves state (`AppNavHost.kt:170-178`). This is the root of Important 1.
- **Threading:**
  - Text, HMAC and parse work runs on the main thread inside `remember`. This is bounded: up to 100 000 characters, a few ms for the digests.
  - The hex encoder with `String.format` is the exception (Minor 3).
  - Progress writes to snapshot state from IO are allowed but frequent and can be stale (Minor 4).
- **UI:**
  - `FlowRow` with `FilterChip` wraps on narrow screens and exposes the selected state, which is reasonable instead of a TabRow.
  - The «Calculando…» row has a cancel button.
  - The over-limit note is missing on the File tab (Minor 2).
  - ⚠️ Accessibility and real-device behaviour were not checked: there was no device run.
- **Structure:**
  - The pure logic in `tools/hashlab` is separate from the screen, and the texts are in their own file for JVM tests.
  - `ToolColumn` became `internal` so it can be reused (`SecurityToolsScreen.kt:217`), which is fine.
  - `SecurityToolsScreen.kt` shrank by 83 lines.
  - `HashLabScreen.kt` is 408 lines, acceptable for four tabs.
- **No new dependency, permission, exported component or network host.**

### Assessment
**Task quality:** Needs fixes
**Reasoning:** The core is correct and well-tested: every official vector checks out, the comparison runs in constant time and the parsing is bounded. But the screen keeps the HMAC key, the message, the text and the decoded text after a vault lock, after leaving the app and after a bottom-tab switch, while the screen says they are deleted when you leave it; and one carried-over test assertion was weakened. Both, and the seven minor findings, are small and local fixes.
