# Task 2: Laboratorio de hashes - report

## Implemented
- `tools/hashlab/HashLab.kt` (pure JVM): `HashAlgo` (5), `digest` (stream, 64 KiB chunks, `check` before each read; and bytes), `hmacSha256` (BC `HMac`, accepts an empty key), `sameDigest` (`MessageDigest.isEqual`), `parseExpected` (hex with spaces/colons/any case, or Base64; only 32 or 64 bytes; all-hex text is never retried as Base64), `hexDecode`, `base64Decode` (std/URL alphabet, padding optional, strict padding; null on any error, never throws), `hexEncode`, `base64Encode`, `utf8OrNull`, `textBytes`. Bounds: `MAX_CODEC_CHARS` 1 M (decoders), `MAX_TEXT_CHARS` 100 000, `MAX_CODEC_UI_CHARS` 10 000, `MAX_KEY_CHARS` 4096, expected hash 512 (parse) / 1024 (field).
- `tools/hashlab/HashLabTexts.kt`: all UI strings.
- `ui/screens/HashLabScreen.kt`: tabs Texto / Archivo / HMAC / Base64-hex (FilterChip rows, not a TabRow, so they wrap on narrow screens), algorithm selector, «Huella esperada» with Coincide / No coincide (and an invalid-hash message), «Lo que no protege» card. Files: `OPEN_DOCUMENT` through `rememberDocumentPicker`, `Dispatchers.IO`, cancellable (`ensureActive`), stream closed with `use`, progress in bytes (size from `OpenableColumns.SIZE`) and «Cancelar»; changing algorithm re-hashes the chosen file. State in a ViewModel, no `rememberSaveable`, wiped in `onCleared`. Over-limit pastes are refused with a message. Every copy uses `copySensitive`.
- `FileHashScreen`/`FileHashViewModel` removed; route `tools/hash` -> `HashLabScreen`; ToolsScreen entry «Laboratorio de hashes» / «SHA-2, SHA-3, BLAKE y HMAC de texto y archivos». `ToolColumn` made `internal`. `FileHashSession.cancelled(run)` added.
- Docs (`README.md`:283/291, `docs/ficha-google-play.md`:46) still say «Huella SHA-256»; left for the documents task.

## Tests
- `HashLabTest` (21): official vectors written in full (FIPS 180-4 «abc» SHA-256 and SHA-512, FIPS 202 SHA3-256 «abc», RFC 7693 App. A BLAKE2b-512 «abc», BLAKE3 test_vectors.json input_len 0, RFC 4231 cases 1 and 2; cross-checked with Python hashlib/hmac and the BLAKE3 repo JSON), streaming 10 MB == memory for all 5 algos, partial reads, cancel for all 5, parse/decoder null cases, size limits, UTF-8, codecs.
- Carried over: `GeneratorAndChecksTest` SHA-256 «abc», sameHash and `sha256Hex` cancel tests untouched (`sha256Hex`/`sameHash` still exist); the cancel assertions are repeated in `checkCanCancel`. `PlatformSourcesTest` COV1-2 test re-pointed to `HashLabScreen`; F6 M1/M2 tests adjusted; `CipherSessionsTest` gained a `cancelled` test.
- RED: `gradle -p jvm-check test --tests '*HashLabTest*'` -> `:compileTestKotlin` failed (unresolved `digest`, `HashAlgo`... not yet written). GREEN after implementing: one failure was my own wrong test (`textIsHashedAsUtf8`), fixed; then all passed.
- Full: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline` BUILD SUCCESSFUL (1327 app tests); lint 4 warnings (GradleDependency x2, DiscouragedApi, ObsoleteSdkInt), none new. `gradle -p jvm-check test` BUILD SUCCESSFUL.

## Self-review
- Not tested on a device (UI, picker, progress) - only compiled and source-tested.
- Text hashing is computed on the main thread inside `remember` (<= 100 000 chars, a few ms).
- `Text`/`SelectionContainer` output is bounded by the 10 000-char codec cap.
- The Base64 decoder is lenient on non-zero trailing bits (java.util.Base64).

## Concerns
- The file's result is held as lowercase hex in `FileHashSession` and decoded again for the comparison (fine, small).
- Docs/ficha text not updated (see above).

## Revisión experto-seguridad-cripto
- Standard primitives only (Bouncy Castle digests/HMac, `java.util.Base64`), no new code of crypto: `HashLab.kt:30-100`.
- Digest comparison with `MessageDigest.isEqual`: `HashLab.kt` `sameDigest`; screen uses it at `HashLabScreen.kt` `ResultAndCheck`.
- HMAC with empty key allowed (RFC 2104); key/message/text only in the ViewModel, cleared in `onCleared` (`HashLabScreen.kt` `HashLabViewModel.onCleared`), never `rememberSaveable` (checked by `PlatformSourcesTest` COV1-2 test). Key field is `PasswordField` (inside `NoCopy`).
- Inputs bounded; decoders/`parseExpected` return null, never throw (`HashLabTest.expectedNeverThrowsOnBadInput`, `decodersRefuseAHugeInput`).
- No logging. Copy only through `copySensitive`.
- Note shown to user: a matching hash does not authenticate who published it.

## Revisión Ingeniero Móvil Senior
- Work in `viewModelScope`, IO via `withContext(Dispatchers.IO)`, `ensureActive` per 64 KiB, `use` closes the stream, previous job cancelled on a new pick (`HashLabScreen.kt` `pick`).
- Run-id guard (`FileHashSession`) prevents a slow earlier file showing under a later name; button disabled while busy; `Cancelar` works.
- Picker through `rememberDocumentPicker` (URI check). Layout wraps with `FlowRow`. No new dependency, permission, component or host.

## Textos para el Abogado Digital (revisión final)
All in `app/src/main/java/app/seguridad/tools/hashlab/HashLabTexts.kt` (verbatim there):
«Laboratorio de hashes»; «SHA-2, SHA-3, BLAKE y HMAC de texto y archivos»; intro «Calcula la huella de un texto o de un archivo y compárala con la que publica su autor, por ejemplo la de una descarga o la del firmware de una cartera física. Todo se calcula en tu móvil.»; «Lo que no protege» + «Que la huella coincida solo dice que el archivo es el mismo que el de la huella. No dice que la huella venga de quien dice venir: si te la dio la misma web que el archivo, quien cambie uno puede cambiar la otra.»; tabs «Texto», «Archivo», «HMAC», «Base64/hex»; «Huella esperada (hex o Base64)»; «✓ Coincide»; «✗ No coincide»; «La huella esperada no es válida: tiene que ser hex o Base64 de 32 o de 64 bytes.»; «Texto (se calcula en UTF-8)»; «Ese texto es demasiado largo y no se ha pegado.»; «Elegir archivo»; «No se pudo leer el archivo.»; «Cancelar»; «Copiar»; «HMAC-SHA256 con una clave que escribes. La clave y el mensaje se quedan en esta pantalla y se borran al salir de ella.»; «Clave», «Clave en texto», «Clave en hex», «La clave en hex no es válida.», «Escribe una clave.», «Mensaje (UTF-8)»; «Codificar», «Decodificar», «Entrada», «La entrada no es válida para ese formato.», «No es texto UTF-8: se muestra en hex.»; «Calculando… X [de Y]». Also inline: «Base64», «Hex».

---
# Fix round 1 (review of task 2: 2 Important, 7 Minor, all fixed)

## What changed
- **Important 1.** New pure `tools/hashlab/HashLabSession.kt` (a `GuardedSession`: text, expected, HMAC key, HMAC message, codec input). The ViewModel calls `watchAway(session.guard, store, lockAfterMs, awayInOtherApp)`; the screen takes `store`, `lockAfterMs`, `awayInOtherApp` (AppNavHost passes `vault`, `lockAfterMs`, `container.awayInOtherApp`), uses `GuardEffects` and `ToolColumn(..., s.guard)`, and shows `TOOLS_AWAY_WIPE_NOTE` (exact: back, app closed, vault lock, or away longer than the lock time, 1 min minimum - a tab switch keeps the data only for less than that). Typing counts as use (`guard.typed()` in `limited`). The HMAC intro no longer says «se borran al salir de ella» (new text below). `onCleared` calls `session.wipe()`.
- **Important 2.** Test asserts `"if (f.failed) Text(HASH_LAB_FILE_FAILED" in src`.
- **Minor 1.** `blake3MultiChunkOfficialVectors`: lengths 1025 and 102400 (bytes `i % 251`) from the BLAKE3 team's `test_vectors.json`; I re-fetched that JSON (BLAKE3-team/BLAKE3, `test_vectors/test_vectors.json`) and the test passes against BC 1.86 with them.
- **Minor 2.** `refused` is now a per-field enum; `TooLongNote` sits under each field (text, key, message, codec, and the expected hash in every tab including File). Source test counts them.
- **Minor 3.** `hexEncode` removed. `crypto/Hashes.kt`: `hex` is now table-based (output unchanged, uppercase) and `hexLower` added; `sha256Hex` uses it. Test compares both with all 256 byte values.
- **Minor 4.** Progress written only if `run == mine` and about once per MiB (`PROGRESS_STEP`); `run` is `@Volatile`.
- **Minor 5.** A pasted hash of another size gives `hashLabWrongSize` (names the algorithms of that size; for HMAC says it gives 32 bytes) instead of «No coincide».
- **Minor 6.** Choice: changing the algorithm never reads the file. On the File tab the old result is hidden («Elegiste otro algoritmo: el resultado de antes ya no vale.») and a button «Calcular otra vez con X» re-reads it on request.
- **Minor 7.** `assertEquals(2, calls)`; source test that the screen renders `HASH_LAB_NOT_PROTECTED` (+ title).
- Tests added: `HashLabTest` (+ BLAKE3 multi-chunk, wrong-size text, hex tables, session wipe, guard wipe after long away); `PlatformSourcesTest` COV1-2 test extended (guard wiring, note, typed(), nav wiring, failure and not-protected texts, progress guard, TooLongNote count).

## Commands and output
- Covering: `./gradlew :app:testDebugUnitTest --tests '*HashLabTest*' --tests '*PlatformSourcesTest*' --tests '*CipherSessionsTest*' --tests '*SourceTextsTest*' --tests '*GeneratorAndChecksTest*' ...` -> BUILD SUCCESSFUL (three test-assertion mismatches of my own source tests were fixed on the way).
- Full: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline` -> BUILD SUCCESSFUL (3m 15s); lint 4 warnings (GradleDependency x2, DiscouragedApi, ObsoleteSdkInt). `gradle -p jvm-check test ...` -> BUILD SUCCESSFUL.

## Revisión experto-seguridad-cripto (update)
- Key, message, text, decoded text and pasted hash are wiped on vault lock, other app, idle-away and back (`HashLabSession.kt` `forget`; `HashLabScreen.kt` `watchAway`, `onCleared`); the visible note states exactly that. Still Strings (cannot be zeroed), references dropped, as in the other tools.
- BLAKE3 multi-chunk path now pinned to the reference (`HashLabTest.blake3MultiChunkOfficialVectors`).
- A hash of the wrong size is no longer reported as a mismatch (avoids a false «altered file» conclusion).

## Revisión Ingeniero Móvil Senior (update)
- Guard wiring identical to `BreachScreen`/`TextCryptoScreen` (`GuardEffects`, `watchAway`, `usedFor`); bottom-tab switch handled by the guard's away timer.
- No hidden multi-GB re-reads; progress writes throttled and run-checked; hex encoding table-based (no `String.format` per byte on the main thread).
- Not run on a device.

## Textos para el Abogado Digital (revisión final) - new or changed
- Changed `HASH_LAB_HMAC_INTRO`: «HMAC-SHA256 con una clave que escribes. La clave y el mensaje no se guardan en el móvil: solo están en esta pantalla, y el aviso de abajo dice cuándo se borran.»
- New: «Elegiste otro algoritmo: el resultado de antes ya no vale.»
- New: «Calcular otra vez con <algoritmo>».
- New: «Esta huella tiene 32 bytes, el tamaño de SHA-256, SHA3-256 o BLAKE3-256.» / «Esta huella tiene 64 bytes, el tamaño de SHA-512 o BLAKE2b-512.» followed by « Elige uno de esos algoritmos.» or, in the HMAC tab, « HMAC-SHA256 da 32 bytes.»
- Now also shown on this screen (existing text): `TOOLS_AWAY_WIPE_NOTE`.

---
# Fix round 2 (re-review, «New Breakage»: the shared note overclaimed for the File tab)

## What changed
- `HashLabSession` now owns the File tab's `FileHashSession` (`file`) and an opaque `source` (the screen keeps the chosen URI there), plus an `onWipe` hook. `forget()` calls `onWipe()` (the ViewModel cancels the file job: `HashLabScreen.kt` `HashLabSession(ComposeCells, SystemClock.elapsedRealtime()) { job?.cancel(); job = null }`), then `file.clear()` and `source = null`. So the guard's wipe (lock, other app, time away) and `onCleared` now drop the file name and result and make «Calcular otra vez» unavailable (`hasFile` is `session.source != null`). `TOOLS_AWAY_WIPE_NOTE` is unchanged and now true for every tab.
- `FileHashSession.clear()` (new): bumps the run id and resets name, hash, failed and busy, so a late name or result from a run cut by the wipe is refused.
- Removed `lastUri`/`hasFile` state from the ViewModel (replaced by the session).

## Tests
- `HashLabTest.guardWipeAlsoDropsTheFileResultAndTheChosenSource`: after the guard wipes (away longer than the lock), the hook ran once, file name and hash are null, not busy, `source` is null.
- `HashLabTest.guardWipeDuringAFileRunRefusesItsLateResult`: a wipe during a run ends «Calculando…» and refuses the late `named`/`finish`.
- `PlatformSourcesTest` COV1-2 test pins the hook that cancels the job.
- Covering run: `./gradlew :app:testDebugUnitTest --tests '*HashLabTest*' --tests '*PlatformSourcesTest*' --tests '*CipherSessionsTest*' ...` BUILD SUCCESSFUL.
- Full: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1 --offline` BUILD SUCCESSFUL (2m 57s), lint 4 baseline warnings; `gradle -p jvm-check test ...` BUILD SUCCESSFUL.

## Revisión experto-seguridad-cripto
- The file's name and hash (a fingerprint of a possibly sensitive file) go with the rest by the same rule the screen states (`HashLabSession.kt` `forget`).

## Revisión Ingeniero Móvil Senior
- The wipe cancels the IO job (`onWipe`) and invalidates the run, so no coroutine writes into a wiped session; `FileHashSession.clear()` is the single reset.

## Textos para el Abogado Digital (revisión final)
- No new or changed UI text in this round (the shared `TOOLS_AWAY_WIPE_NOTE` is untouched).
