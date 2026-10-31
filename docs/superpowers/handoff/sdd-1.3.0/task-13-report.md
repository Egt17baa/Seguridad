# Task 13 report: Clave pública (A15a, A15b, A16a, A16b)

**Status:** DONE. Commit `7268a24` «1.3.0: salida obsoleta, nombre del archivo y remitente con clave repetida» on `ccr-a1a6eabb-765f9g` (not pushed).

## What was implemented

- **A15a (stale output).**
  - `vault/PkActions.kt:206-220`:
    - `data class PkInputs(contactId, text, fileUri)` and `data class PkResult(inputs, output)`. Both override `toString`, so the plain text never shows.
    - `fun resultFor(current, last): String?` returns the output only when `last.inputs == current`.
  - `ui/screens/PkActionsScreen.kt`:
    - «Cifrar para» (`:279`) and «Firmar» (`:457`) keep a `PkResult` in `remember` and show `resultFor(...)` of the current inputs. The current inputs are the contact id (`contact?.id`, so a deleted contact hides it too), the text in text mode and the URI in file mode. A job that finishes after the contact, the text, the mode or the file changed shows nothing.
    - The «Firmar con mi identidad» switch is not among the inputs, so it is now disabled while a job runs (`:388`). It already cleared the result.
    - The same race existed in «Abrir lo recibido»: the text field stays editable while «Abrir» runs. That result now carries its source text (`textResultFrom`, `:587`, `:702`, `:772`) and is shown only while the field holds that text (`:777`).
    - Rotation: the result lives in `remember`, like the plain text, so it is lost with it. It can never sit next to other inputs.
- **A15b (file name sent to the recipient).**
  - `fun anonymousFileName(original)` (`vault/PkActions.kt:184`):
    - it starts from `safeFileName(original)`, so there is no path and no bidi trick;
    - it keeps the last extension only if it is 1 to 10 ASCII letters or digits, **lowercased** (`Locale.ROOT`);
    - otherwise it gives `archivo`.
  - On the screen (`PkActionsScreen.kt:333-344`), once a file is chosen:
    - `fileNameInsideText(nameInside)` shows the name that really goes inside: `safeFileName(sourceName)`, or `archivo.<ext>` when the box is ticked;
    - a whole-row `toggleable` checkbox «No enviar el nombre» (Role.Checkbox) sits under it. It is `rememberSaveable` and off by default, which is today's behaviour.
  - When the box is ticked:
    - the name passed to `encryptFileForContact` is `anonymousFileName(sourceName)` (`:294`);
    - the proposed outer name becomes `archivo.<ext>.sgenc` (`:398`). Otherwise the `.sgenc`'s own file name would still give the name to the recipient. This is only the name suggested in the save dialog, not the format.
  - The format does not change: the name still travels in record 0, as before.
- **A16a (several contacts with one signing key).**
  - `vault/Contacts.kt`:
    - `Sender.Contact(entry, sameKeyCount = 1)` (`:137`).
    - `senderOf` (`:154-164`): the verified contact wins; among equals, the one with the higher `updatedAt` wins; when everything is equal, the first in the list wins, so the existing test `conDosContactosConLaMismaClaveGanaElPrimero` still holds.
    - `fun sameKeyCount(senderEd, entries)` (`:167`) counts only CONTACT entries with a readable card.
    - **Comparison: `contentEquals`.** The existing code compares public keys with `contentEquals` (`checkIncomingCard`, and `senderOf` before), and these are public keys, not secrets.
  - `SenderLine.sameKey` (`PkActions.kt:100`) is set by `senderLine` when `sameKeyCount > 1` (`:118`). `SenderText` shows it in CautionColor (`PkActionsScreen.kt:212`). It covers all three paths: the text opened, the file decrypted, and the `.sgsig` checked.
- **A16b (name cut inside a UTF-8 character).**
  - `crypto/FileCrypto.kt:134-147`: `safeFileName` keeps at most `MAX_FILE_NAME_CODE_POINTS = 120` code points (`codePointCount` and `offsetByCodePoints`). It only shortens and never rejects.
- **(a) discardDocument.** Every call in `PkActionsScreen.kt` now goes through one of two helpers:
  - `discardLater`, for launcher callbacks: `scope.launch(NonCancellable + Dispatchers.IO)`, which runs even if the scope is cancelled (`:159`, used at `:289`, `:464`, `:634`);
  - `discardNow`, in coroutines: `withContext(NonCancellable + Dispatchers.IO)` (`:164`, used at `:308`, `:311`, `:478`, `:481`, `:655`, `:658`).

  `VaultBackup.kt:75` uses `withContext(NonCancellable + Dispatchers.IO)`.
- **(b) ClipboardNotice.**
  - `accept()` (`tools/ClipboardNotice.kt:70-86`) sets `acceptedNow = true` only after `write` returns.
  - If `write` throws, `accept()` returns `false`: `onCopied` does not run, the root dialog does not save the acceptance in Ajustes (it returns on `false`), and the next copy asks again.
  - I chose to return `false` instead of rethrowing because an exception in the dialog's `onClick` would close the app. The copy that failed is dropped, not retried.

## Formats and the 255 versus 1024 note

- SGS1, SGM1, `.sgenc` (symmetric and type 3) and `.sgsig` do not change. No format test was touched.
- `MAX_FILE_NAME_BYTES` stays **1024** (`FileCrypto.kt:17`), and so does the read check (`FileCrypto.kt:105`; `pk/FileMessage.kt:62`, `:127`).
- The design (A16b) said «sin pasar de 255 bytes». The real limit of the format is 1024 bytes, and it does not change. With 120 code points, a name is at most 480 bytes, so the byte cut at `FileCrypto.kt:63` (`copyOf(MAX_FILE_NAME_BYTES)`) can no longer be reached. It stays as it is: format code, untouched.
- **Reading old files.**
  - `decryptStream` never calls `safeFileName`; it returns the stored name whole.
  - `decryptFileFor` (`pk/FileMessage.kt:238`) and `sanitizeReceivedName` only shorten.
  - `safeFileName` is called on output only: `SecurityToolsScreen.kt:642` (save under), `VaultAttachments.kt:121`, `:127`, `:160` (display and save), `Attachments.kt:99` (the stored attachment name), and `encryptedFileName`/`signatureFileName`. None of these can reject reading an old file.
- **Old-style file test.** No fixture has a long name (`ref.cpenc`, `file-aes.cpenc` and `backup-1.1.sgbak` use short names). So `anOldFileWithANameLongerThan120CodePointsStillDecrypts` (CryptoTest) writes a `.sgenc` by hand, the way `encryptStream` does but without `safeFileName`, with a 304-code-point name of 604 bytes. It decrypts with the whole name, and `safeFileName` gives 120 code points to save under.
  - This test also passes with the old code, as expected: it guards against a future regression on the read side.
  - I did not write a hand-built type-3 (public-key) file with a long name. Its read path also only shortens (`FileMessage.kt:238`).

## TDD evidence

- **RED 1** (all tests written before the code): `gradle -p jvm-check test` did not compile. The errors were «Unresolved reference 'PkInputs' / 'PkResult' / 'resultFor' / 'sameKeyCount'», and «Too many arguments for anonymousFileName» on the test name.
- **RED 2** (runtime): with every other change in place, I put back the old `FileCrypto.kt` and `ClipboardNotice.kt` with `git stash push` on those two files. Result: `CryptoTest > safeFileNameKeepsWholeCodePoints FAILED`, `ClipboardNoticeTest > aWriteThatThrowsLetsTheNoticeAppearAgain FAILED`, «944 tests completed, 2 failed».
- **GREEN:** `gradle -p jvm-check test` gives BUILD SUCCESSFUL, 944 tests, 0 failures, 0 skipped.
- `ToolTextsTest.publicKeyTextsAreTheDesignOnes` was written together with the texts, not before them.

New tests:
- PkActionsTest: `staleResultIsHidden`, `anonymousFileName`, `senderLineWarnsWhenTheKeyIsInSeveralContacts`, `openingAMessageFromAKeyKeptTwiceSaysSo`;
- ContactsTest: `senderPrefersVerifiedThenNewest`;
- CryptoTest: `safeFileNameKeepsWholeCodePoints`, `anOldFileWithANameLongerThan120CodePointsStillDecrypts`;
- ClipboardNoticeTest: `aWriteThatThrowsLetsTheNoticeAppearAgain`;
- ToolTextsTest: `publicKeyTextsAreTheDesignOnes`.

## Full verification

All runs used `ANDROID_HOME=/opt/android-sdk` and `--no-daemon --max-workers=1 --offline`, on the final tree:
- `gradle -p jvm-check test`: BUILD SUCCESSFUL, **944 tests, 0 failed, 0 skipped**.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL in 4m 19s.
  - testDebugUnitTest: **944 tests, 0 failed, 0 skipped**.
  - Lint: 0 errors and 17 warnings, all from before this task and none in the files changed: libs.versions.toml (13), DistrustedCertificates.kt, the AndroidManifest and mipmap.
  - checkLicense, checkFragmentVersion and checkAssets: OK.

## Files changed

- app/src/main/java/app/seguridad/crypto/FileCrypto.kt
- app/src/main/java/app/seguridad/tools/ClipboardNotice.kt
- app/src/main/java/app/seguridad/tools/ToolTexts.kt
- app/src/main/java/app/seguridad/ui/screens/PkActionsScreen.kt
- app/src/main/java/app/seguridad/ui/screens/VaultBackup.kt
- app/src/main/java/app/seguridad/vault/Contacts.kt
- app/src/main/java/app/seguridad/vault/PkActions.kt
- app/src/test/java/app/seguridad/crypto/CryptoTest.kt
- app/src/test/java/app/seguridad/tools/ClipboardNoticeTest.kt
- app/src/test/java/app/seguridad/tools/ToolTextsTest.kt
- app/src/test/java/app/seguridad/vault/ContactsTest.kt
- app/src/test/java/app/seguridad/vault/PkActionsTest.kt

## Self-review and concerns

1. **Outer file name.** With «No enviar el nombre», the proposed `.sgenc` name is also `archivo.<ext>.sgenc`. This goes beyond «only the string inside changes», but it is only the save-dialog suggestion, and without it the box would not do what it says. The user can still rename the file.
2. **Switch disabled while busy.** `PkInputs` has no `sign` field (I kept the brief's signature), so «Firmar con mi identidad» is disabled while a job runs.
3. **Other discardDocument calls.** Calls of the same kind remain in `SecurityToolsScreen.kt` (236, 247, 515, 529, 543, 559) and `VaultAttachments.kt` (91, 102, 105), outside this task's list. I left them for a later task.
4. **ClipboardNotice.** A throwing write now returns `false` instead of throwing (see above). The behaviour for the user: nothing copied, no crash, and the notice comes back next time.
5. **safeFileName length.** Names between 120 UTF-16 units and 120 code points (with emoji) are now longer than before, up to 240 units and 480 bytes. That is still far below 1024. `sanitizeReceivedName` keeps its own 120-unit limit, which never splits a surrogate pair.
6. **Messages.** Error and success messages (`message`) are not tagged with their inputs. They are cleared when the text changes, but a late error from an old job can still show. That is low impact, because it is never data.
7. **Manual checks not run.** Nothing was checked on a device: TalkBack on the checkbox row, and the late result after editing during a slow job.

## Textos para el Abogado Digital (revisión final)

- `fileNameInsideText(name)`, app/src/main/java/app/seguridad/tools/ToolTexts.kt:494: «El nombre del archivo («x») va dentro, cifrado: lo verá el destinatario.» («x» is the real name, or `archivo.<ext>` with the box ticked). Shown at PkActionsScreen.kt:335.
- `DONT_SEND_FILE_NAME`, ToolTexts.kt:497: «No enviar el nombre». Shown at PkActionsScreen.kt:342.
- `sameKeyContactsText(count)`, ToolTexts.kt:500: «Esta clave está guardada en N contactos.» (N = sameKeyCount, always ≥ 2 when shown). Set at PkActions.kt:118 and shown at PkActionsScreen.kt:212.
- All three are in `ALL_TOOL_TEXTS` (ToolTexts.kt:543) and pass `noForbiddenWords`, `everyTextConstantIsChecked` and SourceTextsTest.
- No text names the term `ANONYMOUS_FILE_BASE` = `archivo` as «anónimo».

## Revisión experto-seguridad-cripto

- **Threat model.**
  - A15b: the recipient, or anyone who gets the `.sgenc`, learns the original name. The box removes the name from record 0 (`PkActionsScreen.kt:294`) and from the suggested outer name (`:398`).
  - What it does not hide: the extension (on purpose, so the file opens), the size and the timing. The screen tells what goes inside (`:335`).
- **No new crypto, no format change.**
  - Record 0 still holds `safeFileName(name)` (`pk/FileMessage.kt:125`); the AEAD, nonces and AAD are untouched.
  - The read limit is 1024 bytes (`FileCrypto.kt:105`, `FileMessage.kt:62`).
  - Old long names decrypt (CryptoTest `anOldFileWithANameLongerThan120CodePointsStillDecrypts`).
- **UTF-8 integrity.** The cut is by code points (`FileCrypto.kt:144-146`), so no lone surrogate is produced and the UTF-8 is never cut inside a character. Test with emoji, CJK and 200 emoji ≤ 480 bytes.
- **Sender attribution (A16a).**
  - The choice is deterministic: verified, then newest, then list order (`Contacts.kt:157-163`).
  - The duplicate key is shown as a caution (`PkActionsScreen.kt:212`), so a contact imported a second time without verification cannot quietly take over the name: the verified one wins, and the user is warned.
  - Comparison is `contentEquals` on public keys (`Contacts.kt:170`): no timing-sensitive secret is involved.
- **Plain text.** `PkInputs.text` holds the plain text only in `remember`, as the field does, and `toString` leaves it out (`PkActions.kt:206-208`, test `staleResultIsHidden`). It is never in saved state. `hideName` is the only new saved value, a boolean.
- **No half-written files.** Deleting an unwritten document is no longer cut by a cancellation and is off the main thread (`PkActionsScreen.kt:159-166`, `VaultBackup.kt:75`).
- **Clipboard (A14).** The notice is no longer marked as accepted when the copy did not happen (`ClipboardNotice.kt:78-83`).
- No new permissions, network, dependencies or logs.

## Revisión Ingeniero Móvil Senior

- **State.**
  - The result is UI state in `remember`, and its validity is derived at composition time (`resultFor`, `PkActionsScreen.kt:279`, `:457`, `:777`). There is no extra effect and no `LaunchedEffect` keyed on inputs, so a late coroutine result cannot appear next to other inputs, also across recompositions.
  - Rotation recreates the composition, so the result and the text go together.
- **Concurrency.**
  - Work stays in `Dispatchers.Default`.
  - `discardLater` uses `launch(NonCancellable + Dispatchers.IO)`. Replacing the Job means it runs even when `rememberCoroutineScope` is already cancelled (the screen left during the picker).
  - `discardNow` uses `withContext(NonCancellable + Dispatchers.IO)` inside `catch (CancellationException)`, as RedactScreen and PhotoCleanScreen do.
  - VaultBackup: `runCatching` already turned a cancellation into `ok = false`, and the delete now finishes under NonCancellable (`VaultBackup.kt:75`).
- **Compose and accessibility.**
  - The checkbox row uses `Modifier.toggleable(role = Role.Checkbox)` with `Checkbox(onCheckedChange = null)`: one touch target, read as one checkbox (`PkActionsScreen.kt:337-344`). It is disabled while busy.
  - The switch is disabled while busy (`:388`).
- **Saved state.** `hideName` is `rememberSaveable` (a boolean, not secret) (`:275`). The defaults keep today's behaviour.
- **Pure logic in `vault/`.** It is compiled and tested in jvm-check, and the screens keep only the wiring.
- **Build.** Lint has no new warnings, and assembleDebug passes.

## Fix round 1

**Status:** DONE. Commit `1958578` «1.3.0: salida obsoleta, nombre del archivo y remitente con clave repetida, revisión 1» (not pushed). All 8 Minor findings are fixed.

### What changed, finding by finding

1. **255 bytes (the spec wins over the brief).**
   - `crypto/FileCrypto.kt`:
     - `MAX_SAVED_NAME_BYTES = 255` (`:141`).
     - `safeFileName(name, reservedBytes = 0)` (`:157-162`) first cuts to 120 code points. Then `takeUtf8Bytes` (`:169`) drops whole code points from the end until the UTF-8 is at most 255 − `reservedBytes`.
   - `encryptedFileName` and `signatureFileName` subtract their suffix (`vault/PkActions.kt:198`, `:201`).
   - The symmetric tool now proposes `encryptedFileName(sourceName)` instead of `sourceName + ".sgenc"` (`ui/screens/SecurityToolsScreen.kt:646`).
   - `sanitizeReceivedName`, the name proposed to save a received file, also stops at 255 bytes (`PkActions.kt:161`). 120 CJK characters would be 360 bytes. Its 120-character limit and idempotence are unchanged.
   - The read limit stays `MAX_FILE_NAME_BYTES = 1024`, and no format test was touched.
   - **Brief test expectation updated.** 200 CJK characters used to give 120 code points; they now give **85** (255 bytes, 3 bytes each). 120 or 200 emoji give **63** (252 bytes), ending on a whole emoji. With «.sgenc», 200 CJK give 83 (249 + 6 = 255).
2. **Controls, dots and spaces.**
   - The removal regex is now `[\x00-\x1F\x7F-\x9F  ]` (`FileCrypto.kt:144`).
   - Dots and spaces, including no-break ones, are trimmed at both ends before and after the cut, the way `trimEnds` does.
   - An empty result, «.» or «..» gives «archivo» (`:161`).
   - Tests in `safeFileNameNeverGivesDotsControlsOrLooseEnds` (CryptoTest): «.», «..», « . », «...», «/..», «a/.», U+0085, U+2028, «..bashrc», trailing «. . », no-break spaces, DEL, every code from 0x80 to 0x9F, a space or dot left after the code-point cut, a space left after the byte cut, and idempotence.
3. **1024-byte reading.**
   - CryptoTest `aNameOfExactly1024BytesStillDecryptsAnd1025DoesNot`: a hand-written symmetric `.sgenc` whose name is 512 «é» (exactly 1024 bytes) decrypts with its whole name. One more byte is rejected, as the format already did.
   - For the public-key file, `FileMessage.kt` gains `internal fun encryptFileForNameBytes` (`:131`). `encryptFileFor` now calls it with `safeFileName(name)` (`:124`), so the bytes it writes are the same as before (the format vectors pass). The new function exists only so tests can write the name as raw bytes, as an older build would.
   - PkActionsTest `aPublicKeyFileWithALongNameStillOpens` uses names of 604, 1024 (é) and 1024 (emoji) bytes. Each one:
     - goes through `FileMessage.kt:238`: the raw name comes back whole, and the `safeFileName` path gives at most 255 bytes;
     - decrypts in «Abrir lo recibido» with the right sender, and its name passes through `sanitizeReceivedName` (at most 255 bytes).
4. **Late text result in «Abrir lo recibido».** `keepIfStillCurrent` (`PkActionsScreen.kt:614-621`) stores the result only if the field still holds the text it came from. Otherwise the result is dropped: a card's bytes are zeroed, and a decrypted `String` cannot be zeroed but no reference to it is kept. It is used at `:711` (a file read as text) and `:781` («Abrir»). The display guard `textResultFrom` stays as a second check.
5. **`discardDocumentLater`.** It uses `AppContainer.appScope` with `Dispatchers.IO`, as `discardInBackground` does (`SecurityToolsScreen.kt:481`). It is shared with `discardDocumentNow` (`:488`, `withContext(NonCancellable + Dispatchers.IO)`), and the private copies in PkActionsScreen are gone.
6. **«No se ha podido copiar.»**
   - `ClipboardNoticeRequest.acceptOutcome()` (`tools/ClipboardNotice.kt:75`) returns `AcceptOutcome` (`:104`): COPIED, NOTHING_WAITING, LOCKED or WRITE_FAILED. `accept()` is still `acceptOutcome() == COPIED`, so the existing tests are unchanged.
   - The root dialog (`ui/ClipboardHistoryNotice.kt:34-41`, `:55-58`) shows a short AlertDialog with `CLIPBOARD_COPY_FAILED` only for WRITE_FAILED. Nothing is said for LOCKED or NOTHING_WAITING, and the acceptance is not saved in Ajustes.
   - I used a dialog rather than a snackbar because snackbars belong to each screen, while this notice is global at the app root.
   - Test: ClipboardNoticeTest `theOutcomeSaysWhyNothingWasCopied`.
7. **Messages carry their inputs.**
   - `data class PkMessage(inputs, ok, text)` and `fun messageFor(current, last)` (`PkActions.kt:229-234`), with PkActionsTest `aLateMessageIsHiddenLikeALateResult`.
   - «Cifrar para» and «Firmar» tag every message:
     - file jobs carry `PkInputs(contact, null, uri)`;
     - text jobs carry their own `inputs`;
     - immediate messages (picker errors, «Cancelado.») carry `inputsNow`.
   - They are shown only when the inputs match (`PkActionsScreen.kt:424`, `:549`).
   - «Abrir lo recibido» was left as it is: its text errors already go into `textResult`, which item 4 guards, and its file messages are not tied to the text field.
8. **The other discardDocument calls.**
   - `SecurityToolsScreen.kt` uses `Later` from callbacks (`:240`, `:534`, `:562`) and `Now` inside coroutines (`:251`, `:548`, `:578`).
   - `VaultAttachments.kt:91`, `:102` and `:105` use `Now`, and so does `VaultBackup.kt:75`.
   - RedactScreen and PhotoCleanScreen already did the same thing.

### TDD and verification

- **RED.** I ran the new tests against the main code of the previous commit (`git stash push -- app/src/main`). Compilation failed with «Unresolved reference 'MAX_SAVED_NAME_BYTES'», «Too many arguments for safeFileName(name)», «Unresolved reference 'AcceptOutcome' / 'acceptOutcome'», and the same for `PkMessage`, `messageFor` and `encryptFileForNameBytes`.
- The tests were written in this round together with the code, not strictly before it.
- **GREEN.** With `ANDROID_HOME=/opt/android-sdk` and `--no-daemon --max-workers=1 --offline`:
  - `gradle -p jvm-check test`: BUILD SUCCESSFUL, **950 tests, 0 failed, 0 skipped**.
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL. testDebugUnitTest has **950 tests, 0 failed**. Lint has 0 errors and the same 17 earlier warnings, none in the files changed.
  - After that run I changed only a comment in PkActionsScreen. `:app:compileDebugKotlin` was run again and passed.
- No existing format test (SG1, `.sgenc`, `.cpenc`, `seguridad-pk.json` vectors, backups) was changed, and all of them pass.

### Files changed in round 1

- FileCrypto.kt
- pk/FileMessage.kt
- ClipboardNotice.kt
- ToolTexts.kt
- ui/ClipboardHistoryNotice.kt
- PkActionsScreen.kt
- SecurityToolsScreen.kt
- VaultAttachments.kt
- VaultBackup.kt
- PkActions.kt
- Tests: CryptoTest, ClipboardNoticeTest, ToolTextsTest and PkActionsTest.

### Concerns

- **New test-only function.** `encryptFileForNameBytes` is a new `internal` function used only by tests. It is format-neutral: `encryptFileFor` passes through it and the vectors are unchanged.
- **Shorter names in some places.** `safeFileName` now trims leading dots (`.bashrc` gives `bashrc`) and cuts long non-ASCII names to 255 bytes. That affects the names shown for vault attachments (`VaultAttachments.kt:121`, `:160`) and the name stored when an attachment is added (`Attachments.kt:99`). Nothing is rejected.
- **Name proposed by the symmetric tool.** It now goes through `safeFileName`: no bidi controls, no path, and at most 255 bytes with «.sgenc».

### Textos para el Abogado Digital (revisión final), round 1

- `CLIPBOARD_COPY_FAILED`, `app/src/main/java/app/seguridad/tools/ToolTexts.kt:491`: «No se ha podido copiar.» Shown at `ui/ClipboardHistoryNotice.kt:38`.
- `CLIPBOARD_COPY_FAILED_OK`, `ToolTexts.kt:494`: «Entendido». This is the button that closes it (`ui/ClipboardHistoryNotice.kt:39`).
- Both are in `ALL_TOOL_TEXTS` (`ToolTexts.kt:548`) and pass ToolTextsTest and SourceTextsTest.

### Revisión experto-seguridad-cripto (round 1)

- **Formats are unchanged.**
  - The read limit is still 1024 (`FileCrypto.kt:105`, `FileMessage.kt:62`).
  - Write names are at most 255 bytes, below 1024 (`FileCrypto.kt:157-162`).
  - `encryptFileFor` writes the same bytes (the `seguridad-pk.json` vectors pass).
- **Hostile names from a sender.**
  - C1, DEL, U+2028 and U+2029 are removed, so they cannot break a line or hide text in the UI.
  - «.» and «..» are impossible.
  - Whole code points only, so there are never invalid UTF-8 or lone surrogates (`FileCrypto.kt:169-186`).
- **Plain text.** A late decrypted result is no longer kept in state (`PkActionsScreen.kt:614-621`). A card's bytes are zeroed.
- **No half-written files.** Every place that discards a document now finishes off the main thread and is not cut by a cancellation (item 8).
- No new permissions, network, dependencies or logs.

### Revisión Ingeniero Móvil Senior (round 1)

- **App scope.** `discardDocumentLater` uses `AppContainer.appScope` (SupervisorJob + IO) and the application context, so it outlives the screen and leaks no Activity (`SecurityToolsScreen.kt:481-485`). `discardDocumentNow` follows the NonCancellable pattern of Tachar and Limpiar foto.
- **State reads.** The late-result check reads `pasted` from its `State` when the coroutine resumes on the main thread, so it compares with the value of that moment (`PkActionsScreen.kt:615`).
- **Message tags.** Messages are derived at composition (`messageFor`), as results are. Launcher callbacks see the latest `inputsNow`, because `rememberLauncherForActivityResult` keeps the callback up to date.
- **Copy-failure dialog.** The flag is a `rememberSaveable` boolean, so it survives rotation. The Compose dialog inherits the activity's FLAG_SECURE.

## Fix round 2

**Status:** DONE. Commit `aebc400` «1.3.0: salida obsoleta, nombre del archivo y remitente con clave repetida, revisión 2» (not pushed).

1. **Imports.** The whole import block of `ui/screens/SecurityToolsScreen.kt` is now in alphabetical (ASCII) order. That covers the new imports (`SeguridadApp`, `vault.encryptedFileName`, `CoroutineScope`, `NonCancellable`) and also the lines that were already out of order (`vault.pickedKind`, `CancellationException`). The order is now `android`, `androidx`, `app`, `java`, `kotlin`, `kotlinx`.
2. **FileCrypto.kt:63.** The `.copyOf(MAX_FILE_NAME_BYTES)` cut is gone.
   - `encryptStream` now throws `CryptoFailure("No se ha podido cifrar el archivo")` if the name ever measures more than 1024 bytes (`FileCrypto.kt:63-66`). That is the exception style the file already uses.
   - The guard cannot be reached: `encryptStream` has no internal entry point that skips `safeFileName`, which caps the name at 255 bytes. So there is no test for it; the public-key equivalent (`FileMessage.kt`, `encryptFileForNameBytes`) already had the same guard.
   - Format bytes are unchanged: for every name that could ever be written, the bytes are the same. All format tests and the `seguridad-pk.json` vectors pass.
3. **Format characters (Cf).**
   - The removal class is now `[\x00-\x1F\x7F-\x9F  \p{Cf}&&[^‍]]` (`FileCrypto.kt:153`). It covers U+200B–U+200F, U+202A–U+202E, U+2060–U+2064, U+2066–U+2069, U+FEFF, U+00AD, U+061C, U+180E, and the tag characters U+E0000–U+E007F outside the BMP.
   - **Decision on ZWJ (U+200D): it is kept.**
     - It only joins emoji into one glyph (👨‍👩‍👧). Removing it turns a family into three separate emoji, which corrupts names that are legitimate.
     - `sanitizeReceivedName` already keeps it on purpose (PkActionsTest:187, «unir de ancho cero (emoji compuestos)»), so both sanitisers now agree.
     - Its spoofing power is low: it changes no letter, and the bidi controls, which are the real way to make one name look like another, are all removed.
   - Variation selectors (U+FE0F, category Mn) are kept.
   - The fallback to «archivo» and idempotence are unchanged.
   - Test: CryptoTest `safeFileNameDropsZeroWidthAndFormatCharacters`. It checks:
     - every listed code point, inside a name and alone (which gives «archivo»);
     - tag characters and U+180E;
     - ZWJ kept in a family emoji and in «a‍b.txt»;
     - ❤️ and «Ñandú» unchanged;
     - idempotence;
     - format characters not counting towards the 120-code-point limit.

### TDD and verification

- **RED.** With the old `FileCrypto.kt` (`git stash push` on that file only), `CryptoTest > safeFileNameDropsZeroWidthAndFormatCharacters FAILED` («20 tests completed, 1 failed»).
- **GREEN.** With `ANDROID_HOME=/opt/android-sdk` and `--no-daemon --max-workers=1 --offline`:
  - `gradle -p jvm-check test`: BUILD SUCCESSFUL, **951 tests, 0 failed, 0 skipped**.
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL. testDebugUnitTest has **951 tests, 0 failed**. Lint has 0 errors and the same 17 earlier warnings, none in the files changed.

### Files changed in round 2

- app/src/main/java/app/seguridad/crypto/FileCrypto.kt
- app/src/main/java/app/seguridad/ui/screens/SecurityToolsScreen.kt
- app/src/test/java/app/seguridad/crypto/CryptoTest.kt

### Textos para el Abogado Digital (revisión final), round 2

- `FileCrypto.kt:66`: «No se ha podido cifrar el archivo» (`CryptoFailure` from `encryptStream`).
  - It cannot be reached in practice. Even if it were, Herramientas shows its own fixed «No se pudo cifrar el archivo.» (SecurityToolsScreen).
  - It is listed because it is a new literal. It passes SourceTextsTest.

### Revisión experto-seguridad-cripto (round 2)

- **Spoofing.** Every Cf character except ZWJ is removed (`FileCrypto.kt:146-153`). These invisible characters (zero-width ones, BOM, soft hyphen, bidi controls, tags) cannot hide text in a name or make two names look the same. The ZWJ exception is justified above and covered by tests.
- **UTF-8 integrity.** There is no silent cut that could split a UTF-8 character any more. An impossible oversize now fails loudly (`FileCrypto.kt:63-66`), CWE-176/CWE-20 style.
- **Formats.** Unchanged. Reading still accepts names of up to 1024 bytes (`FileCrypto.kt:105`). The vectors are identical.
- No new permissions, network, dependencies or logs.

### Revisión Ingeniero Móvil Senior (round 2)

- **Regex.** Java's `\p{Cf}` matches by code point, so supplementary characters such as the tags work. The pattern is compiled once, as a top-level `private val` (`FileCrypto.kt:153`).
- **Imports.** Sorting has no effect on behaviour, and lint shows no new warnings.
- **Build.** assembleDebug and lintDebug pass.

## Fix round 3

**Status:** DONE. Commit `b6e0d4a` «1.3.0: salida obsoleta, nombre del archivo y remitente con clave repetida, revisión 3» on ccr-a1a6eabb-765f9g (not pushed).

1. **ZWJ (U+200D) only between emoji.**
   - `withoutLooseJoiners` (`crypto/FileCrypto.kt:167`) keeps a joiner only when both of its neighbours pass `canJoin` (`:160`): not ASCII, not a space, and not another joiner. Otherwise it is removed: at either end, next to ASCII, next to a space, or next to another joiner. For example, «fac‍tura.pdf» becomes «factura.pdf».
   - `trimWithJoiners` (`:180`) repeats that removal and the trim of dots and spaces until nothing changes, because a trim can leave a joiner at an end (for example «😀‍ »).
   - `safeFileName` (`:203`) applies it before the code-point and byte cuts, and again after them, so no joiner is left at the end by a cut.
   - With nothing visible left (only joiners, dots or spaces), the result is still «archivo».
   - It is idempotent: the neighbours of a kept joiner are never removed, so a second pass changes nothing.
   - `sanitizeReceivedName` (`vault/PkActions.kt:157`, `:162`, `:164`) follows the same rule per segment and after its two cuts. An empty result is still `DEFAULT_RECEIVED_NAME`. Its KDoc says so.
   - Two existing tests changed, because they asserted that a joiner between ASCII letters is kept, which this round reverses:
     - `PkActionsTest.seMantienenLosCaracteresQueLaListaAdmiteExpresamente`: «a‍b.txt» now gives «ab.txt»;
     - the CryptoTest line from round 2, the same way.
   - New tests:
     - CryptoTest `safeFileNameKeepsTheJoinerOnlyBetweenEmoji`:
       - the family emoji is kept whole, at the start and after a space;
       - «fac‍tura» drops it;
       - a joiner is dropped at either end, before a space and when doubled;
       - only joiners, dots or spaces gives «archivo»;
       - a joiner left at the end by the byte cut is dropped: 82 «文» + 😀 + ZWJ + 😀 gives 82 «文» + 😀;
       - a joiner left at the end by the code-point cut is dropped;
       - a joiner left after trimming a no-break space is dropped;
       - idempotence.
     - PkActionsTest `elNombreRecibidoSoloGuardaElUnidorEntreEmoji`: the same cases for `sanitizeReceivedName`, a check that it gives the same result as `safeFileName`, and idempotence.
2. **KDoc.** The KDoc of `safeFileName` (`FileCrypto.kt:189-202`) now names the controls, the Cf characters (zero-width spaces and marks, bidi controls, U+2060–U+2064, U+FEFF, the soft hyphen, the tag characters), the ZWJ rule, the fallback to «archivo», the limits and idempotence. The KDoc of `NAME_CONTROLS` (`:146-151`) refers to `withoutLooseJoiners`.
3. **U+180E.** It is no longer in the test, because whether it is Cf depends on the JDK's Unicode version.

### TDD and verification

- **RED.** With the old main code (`git stash push -- app/src/main`), running CryptoTest and PkActionsTest gave 4 failures out of 106:
  - `safeFileNameDropsZeroWidthAndFormatCharacters`;
  - `safeFileNameKeepsTheJoinerOnlyBetweenEmoji`;
  - `elNombreRecibidoSoloGuardaElUnidorEntreEmoji`;
  - `seMantienenLosCaracteresQueLaListaAdmiteExpresamente`.
- **GREEN.** With `ANDROID_HOME=/opt/android-sdk` and `--no-daemon --max-workers=1 --offline`:
  - `gradle -p jvm-check test`: BUILD SUCCESSFUL, **953 tests, 0 failed, 0 skipped**.
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets`: BUILD SUCCESSFUL. testDebugUnitTest has **953 tests, 0 failed**. Lint has 0 errors and the same 17 earlier warnings, none in the files changed.
- No format test or vector changed, and all of them pass.

### Files changed in round 3

- app/src/main/java/app/seguridad/crypto/FileCrypto.kt
- app/src/main/java/app/seguridad/vault/PkActions.kt
- app/src/test/java/app/seguridad/crypto/CryptoTest.kt
- app/src/test/java/app/seguridad/vault/PkActionsTest.kt

### Concerns

- **A joiner between two non-ASCII letters is kept.** `canJoin` only checks for non-ASCII and non-space, not for emoji. So a joiner between two letters such as «ñ‍ñ» stays. This is the rule as asked («between two non-ASCII code points»). A strict emoji check would need data on the emoji properties that the JDK does not expose in a stable way. The risk is small: the joiner is invisible and changes no letter, and the bidi controls, which are the real way to make one name look like another, are all removed.
- No new texts in this round.

### Revisión experto-seguridad-cripto (round 3)

- **Spoofing.** An invisible joiner can no longer be inserted between ASCII letters to make two names that look the same but differ in bytes («fac‍tura.pdf» versus «factura.pdf») (`FileCrypto.kt:160-176`). The only joiners left are the ones needed for emoji sequences.
- **Same rule on both sides.** The sender's name (`safeFileName`) and the name received (`sanitizeReceivedName`) follow the same rule (PkActionsTest checks that they agree).
- **Encoding.** The cuts still go between whole code points, and no joiner is left dangling at the end after a cut (tests for the byte cut and the code-point cut).
- **Formats.** Unchanged. Reading still accepts names of up to 1024 bytes, and the vectors are identical.
- No new permissions, network, dependencies or logs.

### Revisión Ingeniero Móvil Senior (round 3)

- **Cost.** `withoutLooseJoiners` returns early when the name has no joiner. Otherwise it is one pass over the code points (`FileCrypto.kt:167-177`). `trimWithJoiners` ends because each pass only shortens the string or leaves it the same.
- **Pure code.** It is in `crypto/` and `vault/`, compiled and tested in jvm-check. No UI changed.
- **Build.** assembleDebug and lintDebug pass with no new warnings.
