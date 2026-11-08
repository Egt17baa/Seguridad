# Task 10 review: encrypted readers (Bitwarden protected, Aegis encrypted, 2FAS encrypted), R19, R21, R22 (8043583..a1f7771)

The implementer's report was lost when the session ended. This review is based on the code, the commit message, the plan («### Task 10», «Global Constraints», «Review Focus»), the spec («### Los lectores») and the ledger (R11b, R13/R13a, R19, R21, R22, owner decision (e)).

Evidence I ran:
- `./gradlew :app:testDebugUnitTest --tests 'app.seguridad.imports.*'`: green. AegisCryptoTest 10, BitwardenCryptoTest 12, ImportKdfTest 11, TwoFasCryptoTest 10, CsvFormatsTest 24, DetectTest 13, ImportStateTest 42, ImportTextsTest 13, 0 failures.
- An independent Python check (`python3 -I`, `cryptography` + `hashlib`, my own script, not the generator) decrypts every committed encrypted fixture and compares it with its plain twin. Bitwarden PBKDF2: validation is a UUID, `data` == twin. Bitwarden Argon2id: the same. Aegis: `db` == twin's `db`. 2FAS: services == twin, groups equal; salt 256 B, IV 12 B.
- Bouncy Castle 1.86 `SCrypt.generate` with r = 1 and N ≥ 2^16: `IllegalArgumentException: Cost parameter N must be > 1 and < 65536.` (scratch Java run, see M-1).

### Spec Compliance

✅ **Matches the plan and the spec «Por formato» / «Todo lector», with two Important defects in the Bitwarden CSV reader (I-1, I-2) and minors.**

**Bitwarden «Protegida con contraseña»**
- ✅ Header checked before any work (`BitwardenCrypto.kt:69-92`):
  - `kdfType` 0/1, else broken;
  - PBKDF2 iterations through `checkPbkdf2Iterations`;
  - Argon2id memory in MiB through `bitwardenArgon2Params` → `checkSane` (`ImportKdf.kt:89-92`);
  - salt 1..1024 chars and `wellFormed`;
  - both cipher strings parsed, with their sizes bounded before Base64 (`encString`, `:98-108`: IV 16, ct whole blocks, MAC 32).
- ✅ Key derivation (`:114-126`):
  - PBKDF2-SHA256 over the UTF-8 password and the UTF-8 salt string;
  - Argon2id with the salt `SHA-256(UTF-8 salt string)` (`:120`);
  - then HKDF-Expand SHA-256 «enc»/«mac», 32 B each (`ImportKdf.kt:166-170`, RFC 5869 TC1 Expand vector `ImportKdfTest.kt:98-101`).

  The Argon2id salt rule is from documentation and forum posts only. Bitwarden's help page says the export uses «your configured KDF settings» (https://bitwarden.com/help/encrypted-export/). The SHA-256-of-salt rule for Argon2id is consistent with how Bitwarden is publicly described for the account KDF. Not confirmed with a real export: see M-3.
- ✅ The HMAC of `encKeyValidation_DO_NOT_EDIT` and then the HMAC of `data` over IV‖ct are checked before any CBC (`:134-138`). They are compared with `MessageDigest.isEqual` (`:150`). A MAC failure gives `IMPORT_WRONG_PASSWORD`; bad padding after a good MAC gives `IMPORT_BROKEN` (`:166-167`). The validation string is only MAC-checked, never decrypted. That is enough and leaks nothing.
- ✅ The decrypted `data` goes through `decryptedJson` (strict UTF-8, `checkJsonShape` with the tree budget, then `parseToJsonElement` only, `ImportKdf.kt:218-228`), then `readBitwardenPlain`. An inner `encrypted: true` is refused, never decrypted twice (`:180-189`).

**Aegis cifrado**
- ✅ Every password slot is checked before the first scrypt (`AegisCrypto.kt:48-79`: `checkScrypt` inside the map, hex lengths exact, salt 16..64 B). `db` is bounded before Base64.
- ✅ scrypt → AES-256-GCM unwrap (`key_params` nonce/tag) → AES-256-GCM over `db` with `header.params` (`:82-96`, `:104-119`). If no slot opens, the result is `IMPORT_WRONG_PASSWORD` and `db` is never touched (`:112-113`).
- ✅ Only `type: "totp"` enters (`AegisCryptoTest.opensAsItsPlainTwin`: 3 TOTP, HOTP and Steam skipped as `NOT_TOTP`).
- ⚠️ r = 1 with N ≥ 2^16 passes `checkScrypt` but is invalid under RFC 7914 and refused by BC → «dañado» (M-1). More than one password slot multiplies the KDF work (M-2).

**2FAS cifrado**
- ✅ `servicesEncrypted` = `Base64(ct‖tag):Base64(salt):Base64(IV)`, bounded before decoding, with IV 12, salt 8..1024 and ct ≥ tag (`TwoFasCrypto.kt:36-53`).
- ✅ PBKDF2-SHA256 with 10 000 iterations (fixed, not in the file), then AES-256-GCM; the plaintext must be a JSON array (`:67-71`).
- ✅ `groups` is taken in clear beside it.

**ImportKdf bounds**
- ✅ `checkPbkdf2Iterations` covers 5 000..2 000 000 (`ImportKdf.kt:56-58`).
- ✅ `checkScrypt` (`:66-74`) covers N a power of 2 in 2^14..2^20, r 1..16 and p 1..4, all checked before the memory calculation. That calculation is `128L*n*r` in Long, after the bounds, so it cannot overflow. Then come `MAX_KDF_MEMORY_KIB` and `checkSane`'s ¾-heap rule.
- ✅ `IMPORT_KDF_TOO_BIG` is the plan's text verbatim (`ImportTexts.kt:67-69`, pinned `ImportKdfTest.kt:24-31`).
- ✅ JSON numbers: a fraction, an exponent or a value outside Int is `NOT_A_WHOLE_NUMBER` (-1, `ImportJson.kt:283-291`), so every bound refuses it. A string is `WrongType` → broken.
- ✅ An OOM inside a derivation becomes `IMPORT_KDF_TOO_BIG` (`ImportKdf.kt:126-130`).
- ✅ Encrypted parts are capped at 8 MiB chars (`IMPORT_MAX_ENCRYPTED_CHARS`) before decoding. Hex lengths are checked before parsing.

**Wrong password and the screen (end of R13a)**
- ✅ After an attempt, `IMPORT_WRONG_PASSWORD` returns to `ImportStep.Password(format)` with the text and the bytes kept (`ImportSession.kt:195-226`). Any other failure drops the file. A `NeedsPassword` after an attempt gives `IMPORT_BROKEN`, so it never loops.
- ✅ Nothing retries on its own: `takePassword` needs a new typed password (`:176-185`; `ImportStateTest.aWrongPasswordSaysSoAndLetsTheUserTryAgain`, `aPasswordAttemptThatStillNeedsAPasswordNeverLoops`, `cancelWhileThePasswordIsCheckedDropsTheLateResult`).
- ✅ `IMPORT_READS_ENCRYPTED` and `IMPORT_ENCRYPTED_NOT_YET` are removed. The password step shows `ErrorText(s.error)` (`ImportScreen.kt:511`).

**Off the main thread, SingleRun**
- ✅ `readImport` runs on `Dispatchers.Default` (`ImportScreen.kt:286-293`).
- ✅ The `IMPORT_KDF_RUN` ticket is taken before `takePassword` and released in `finally` only after the reader returns. A cancelled run still holds it until the uninterruptible derivation ends, and the late result is dropped by `run`.
- ✅ The R11b OOM catch is unchanged.

**Zeroing**
- ✅ Every derived array goes through `Secrets` and is wiped in `finally` in `openBitwarden`/`openAegis`/`openTwoFas` (`BitwardenCrypto.kt:195-206`, `AegisCrypto.kt:104-119`, `TwoFasCrypto.kt:77-82`). This covers the password bytes, salt bytes, PBKDF2/scrypt/Argon2 output, enc/mac keys, MAC outputs, the CBC buffer and copy, the GCM outputs and the plaintext.
- ✅ The `KeyParameter` copies are wiped (`:152`, `:171`). A GCM output from a failed tag is wiped by `runAead` (`crypto/Crypto.kt:264-278`).
- ✅ The CharArray and file bytes are the caller's, zeroed in `ImportScreen.kt:295` and `ImportSession.dropAll`/`zeroBytes`.
- ✅ No `Log`, no file writes, no clipboard in the diff.
- What cannot be wiped (accepted, as CRY-A03): the HMac ipad/opad state, PKCS5S2's internal HMac, the GCM hash subkey, and the Strings of the file text and the plaintext.

**Tests the plan asks for**
- ✅ Each encrypted fixture opens with `prueba-clavero-1.4` and equals its plain twin (`assertSameRead`, whole `ImportRead` equality).
- ✅ Wrong password: `onDecrypt` is never called (Bitwarden, Aegis). For 2FAS, GCM must run, and the output is proven wiped.
- ✅ `everyByteFlipped` in all three. Bitwarden flips IV, ct and MAC of both cipher strings with 2 bits per byte; `decrypts == 0` proves the HMAC caught each flip before CBC. Aegis flips slot key/nonce/tag and db/nonce/tag. 2FAS flips sealed and IV.
- ✅ `kdfOutOfRangeIsRefusedBeforeWork` < 50 ms, after warm-up.
- ✅ Truncation at 5 points, of the whole file and of the encrypted field.
- ✅ `passwordAndKeysAreZeroed`, but the Bitwarden count is loose (M-4).
- A source pin checks that `MessageDigest.isEqual` comes before `PaddedBufferedBlockCipher` and that there is no `contentEquals` (`BitwardenCryptoTest.theMacsAreComparedInConstantTime`).

**Owner decision (e) and no copied code**
- ✅ Fixtures were written once by `scripts/import-fixtures/gen_encrypted.py`, Python `cryptography`/`hashlib`. It is an independent implementation, and the Kotlin tests read the committed bytes, so this is not a self round-trip. My own second Python script confirmed all four encrypted fixtures (above).
- ✅ Sources are named in the generator, the readers and `import/README.txt`. The readers have no tell-tale structure from Aegis (GPL-3), 2FAS (GPL-3) or Bitwarden clients (GPL-3). They are built on Clavero's own helpers (`Secrets`, `importBase64`, `decryptedJson`, `blockGcm`, `WrongType`), naming and flow.
- ✅ The Python script carries no licence header and inherits the repository's Apache-2.0. It is not shipped. No new dependency.
- ⚠️ The plan's «Consumes `argon2id` (`crypto/Crypto.kt:221`)» is not used: `argon2idKey` (`ImportKdf.kt:151-163`) builds its own Argon2 call. This is justified: `argon2id` has `require(salt.size == SALT_BYTES)` (16, `Crypto.kt:223`), and Bitwarden's salt is 32 B. `checkSane` and the OOM guard are kept. Please record it as a ruling.

**R19, R21, R22**
- ✅ R19 (1): `IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED` names «.json», «.json (Encrypted)», «Protegida con contraseña» and «Cuenta restringida» (`ImportTexts.kt:52-54`).
- ⚠️ R19 (2): the Bitwarden CSV reader exists (`CsvFormats.kt:30-33, 59-60, 147-183`). The URL is checked against https://bitwarden.com/help/condition-bitwarden-import/, and the personal and organisation headers match it. But see I-1 (multi-URI cells) and I-2 (exact-header detection).
- ✅ R19 (3): `PK\x03\x04` is recognised before the size check and before decoding (`Detect.kt:43`, `:57-58`).
- ✅ R19 (4): this task.
- ✅ R21:
  - an HTML page is recognised from its first 512 bytes before decoding, never parsed or shown (`Detect.kt:44`, `:69-83`);
  - the Aegis TXT list (`OtpAuthListTest.aegisTxtExportIsAList`) and «Google Passwords.csv» are pinned;
  - there is a Google Authenticator help text (see M-7);
  - each format's password prompt says whose password it asks for (`ImportTexts.kt:223-230`).
- ✅ R22: Edge with and without the note column (`CsvFormatsTest.edgeCsvWithAndWithoutNotesReadsAsChromium`).
- ✅ Third-party names appear only in `ImportTexts.kt` literals (R2). No forbidden word in the new texts (`ImportTextsTest` covers them all, including every `importPasswordIntro`).

### Strengths

- Every reader follows the same three phases:
  1. validate all header parameters, with no work done;
  2. derive and authenticate;
  3. parse the plaintext as hostile data, through the same tree estimate as a plain file.

  Every failure maps to one of five fixed texts. No exception message reaches the screen, and the `readImport` catch-all stays as the last line.
- `Secrets` with its `onKeep` hook makes zeroing testable, and the tests check it on both the success and wrong-password paths.
- The Bitwarden encrypt-then-MAC order is pinned two ways: a behavioural `onDecrypt == 0` over about 400 flips, and a source-order pin. Constant-time comparison is enforced.
- Known vectors pin the primitives: RFC 7914 scrypt, the PBKDF2-SHA256 «password/salt/4096» vector, and RFC 5869 Expand.
- The fixtures are cross-implementation (Python vs Kotlin), each with a plain twin compared as a whole `ImportRead`.
- The no-loop/retry state machine is small and fully state-tested, including a cancel during an uninterruptible derivation.

### Issues

#### Critical
None.

#### Important

**I-1. Bitwarden CSV: a login with several URIs is stored with one broken URL `https://a/,https://b/`.**
- **Where:** `CsvFormats.kt:151` (`val url = … c.cell(row, "login_uri")`), used as is at `:176-181`.
- **What happens:** Bitwarden's CSV export writes all of a login's URIs into the one `login_uri` cell, separated by commas. Mozilla bug 1702141 shows this, with a Bitwarden CSV whose first URL arrived «followed by the delimiting comma and part of the second», and users importing into 1Password report the same. Clavero stores the whole cell as the entry's URL. That URL is wrong, and `hostOf` and duplicate matching against existing entries also miss. The JSON reader handles the same data correctly: first URI as URL, the rest as «URL: …» lines in the notes (`BitwardenJson.kt:43-44`, `:121-125`). So the two Bitwarden readers disagree, and the fixtures have no multi-URI row. The owner tests real exports only after release (decision (e)).
- **Fix:**
  - in `readBitwardenRow`, split `login_uri` on `,`, trim and drop empties;
  - the first is the URL; each other one becomes a `URL: …` line in `NotesBuilder`, with the same label and order as the JSON reader;
  - check the field limits after the split;
  - add a two-URI row to `bitwarden.csv` with a test that the JSON and CSV readers give the same URL and notes for the same login;
  - note the delimiter as «pendiente de confirmar con una exportación real» (M-3).

**I-2. Bitwarden CSV is recognised only by its exact header, so one extra or reordered column makes the file «no reconocido».**
- **Where:** `CsvFormats.kt:30-33`, `:59-60` (`names == BITWARDEN_CSV || names == BITWARDEN_ORG_CSV`). `CsvFormatsTest.bitwardenCsvNeedsTheExactHeader` pins the rigidity, asserting that `$cols,extra` and the reversed header are refused.
- **What happens:** the spec «CSV» says the reader «busca las columnas por nombre, sin distinguir mayúsculas ni el orden» and «no da sentido a ninguna columna que no conoce». The plan's Review Focus 1 says a real export with a header the fixtures did not have must still import, with the unknown column ignored. If Bitwarden adds a column (it has added fields to the JSON over time), or a tool writes Bitwarden's format in another order, Clavero says «Clavero no reconoce este archivo».
- **Fix:**
  - recognise Bitwarden when the header, by name and in any order and case, has `type`, `name`, `login_uri`, `login_username` and `login_password`, plus `folder` or `collections`;
  - `Columns` already finds cells by name, and unknown columns stay ignored;
  - there is no clash with the browser formats, which need `url`/`username`/`password`;
  - replace the exact-header test with one where an extra column and a reordered header still read the same items, and a header missing `login_password` does not.

#### Minor (all to be fixed)

**M-1. `checkScrypt` accepts r = 1 with N ≥ 2^16, which RFC 7914 forbids. Bouncy Castle then throws and the user is told the file is damaged.**
- **Where:**
  - `ImportKdf.kt:66-74`;
  - `scryptKey` `:145-148` (only OOM is mapped);
  - the `readImport` catch-all `Detect.kt:50-52` → `IMPORT_BROKEN`;
  - `ImportKdfTest.kt:44` asserts `checkScrypt(1 shl 20, 1, 4, big)` is accepted («the bound itself»).
- **What happens:** RFC 7914 §2 requires N < 2^(128·r/8), so N < 2^16 for r = 1. BC 1.86 enforces it with `IllegalArgumentException: Cost parameter N must be > 1 and < 65536.` (verified). An Aegis file with r = 1 and N = 2^16..2^20 is inside Clavero's published range, but shows «El archivo está dañado…» and is dropped. It never shows `IMPORT_KDF_TOO_BIG`. Aegis itself writes N = 2^15, r = 8, so only a crafted or foreign file hits this. The message is still false, against the veracity rule.
- **Fix:** add `if (r == 1 && n >= 1 shl 16) throw ImportFailure(IMPORT_KDF_TOO_BIG)`, or in general `16 * r < 31 && n >= 1 shl (16 * r)`. Map an `IllegalArgumentException` from `SCrypt.generate` to `IMPORT_KDF_TOO_BIG` as a belt. Change `ImportKdfTest.kt:44` to r = 2 or N = 2^15. Add `withSlot("n", 1 shl 16)` with r = 1 to `AegisCryptoTest.kdfOutOfRangeIsRefusedBeforeWork`.

**M-2. Aegis: up to 4 password slots, each a full scrypt, so the worst case is about 4× the per-file KDF budget.**
- **Where:** `AegisCrypto.kt:19`, `:70`, `:112`.
- **What happens:** each password slot may ask up to 128 MiB with p = 4, which is several seconds each on a phone. A crafted file with 4 such slots and a wrong password runs all four derivations sequentially. «Cancelar» stops waiting but cannot cut them, and `IMPORT_KDF_RUN` stays held for the whole time, so the next password attempt anywhere says «Todavía se está comprobando…». This is owner decision (a) (DoS through KDF parameters). The spec bounds one derivation, not their sum. Aegis's docs (`docs/vault.md`) describe one slot per credential, and a real vault has one password slot.
- **Fix:** before the first derivation, bound the total work over the password slots: Σ N·r·p ≤ the cost of one derivation at the bound, for example 2^20·4. Simpler: try only one password slot (`AEGIS_MAX_PASSWORD_SLOTS = 1`, more than one is broken), or keep 4 slots within that one budget. Add a test with two maximal slots refused in < 50 ms.

**M-3. The «Pendiente de confirmar» list has no home: the comments and README point to a Task 10 report that does not exist.**
- **Where:** `BitwardenCrypto.kt:30-31`, `AegisCrypto.kt:13-14`, `TwoFasCrypto.kt:14-17`, `app/src/test/resources/import/README.txt:30-31`. The plan's Task 10 step 3 says the Argon2id salt derivation «is written down in the task report».
- **What happens:** the report was lost, so the list of assumptions not confirmed against real exports exists only in scattered comments. Task 12 and 1.4.1 have nothing to check it against.
- **Fix:** write it down, in a reconstructed `task-10-report.md` or a ledger block, and make the three comments and the README point there. The list:
  - Bitwarden:
    - the Argon2id salt is SHA-256 of the UTF-8 salt string, and the salt is used as text, not Base64-decoded;
    - for PBKDF2, `kdfMemory`/`kdfParallelism` may be present as `null` (ignored);
    - the CSV `login_uri` comma delimiter (I-1).
  - Aegis: the password is UTF-8, GCM takes no AAD, and the salt length is 32 per docs/vault.md, though 16..64 is read.
  - 2FAS:
    - 10 000 iterations;
    - IV 12;
    - salt 256 bytes;
    - the password is UTF-8;
    - `groups` stays in clear;
    - the `reference` field is not used, because the tag over the services is the check.

  Add the matching phone checks to the Task 12 README list: one real export per format.

**M-4. `passwordAndKeysAreZeroed` (Bitwarden) is too loose to catch a reader that stops tracking a key.**
- **Where:** `BitwardenCryptoTest.kt:178` (`min = 6`) and `:187` (`min = 4`); `AegisCryptoTest.kt:170` (`min = 2`).
- **What happens:** the Bitwarden success path keeps 9 arrays: password, salt, PBKDF2 key, enc, mac, two MAC outputs, the CBC buffer and the plaintext copy. A change that dropped `s.keep` for the HKDF outputs and the plaintext would still give ≥ 6 and pass. The same holds on the wrong path (5 kept, min 4) and for Aegis wrong (3 kept, min 2).
- **Fix:**
  - assert exact counts per path and KDF;
  - assert identity: `bitwardenKeys(...)`'s `enc` and `mac`, and the array `bitwardenData` returned, are among `seen` (`any { it === x }`); the same for the Aegis master key and the 2FAS key and plaintext.

**M-5. The KDF lower bounds and malformed numbers are not tested through the file path.**
- **Where:** `BitwardenCryptoTest.kt:95-110` and `AegisCryptoTest.kt:105-119`.
- **What happens:** `ImportKdfTest` covers the functions, but the file path is not tested for:
  - Bitwarden `kdfMemory` 0 and -1, `kdfParallelism` 0, `kdfIterations` as a fraction (`600000.5`) and as an exponent (`6e5`);
  - Aegis `n` as a fraction.

  A future change to `number()` or to the header code could turn one of these into a crash path, or into broken instead of too big, without a red test.
- **Fix:** add these to the two `cases` lists with the expected text: `IMPORT_KDF_TOO_BIG`, since `NOT_A_WHOLE_NUMBER` is -1.

**M-6. Extra plaintext-sized copies raise the memory peak of the 8 MiB path.**
- **Where:** `BitwardenCrypto.kt:162-169` (the CBC `out` buffer stays alive with its `copyOf(n)`, both about 6 MB until `wipe`); `AegisCrypto.kt:95` (`e.db + e.tag`, about 6 MB) and `:87`.
- **What happens:** with a maximal encrypted part, the following are alive at once:
  - the file bytes (≤ 16 MB) and the file text;
  - the outer tree's 8 MiB `data` string;
  - the decoded ct;
  - these copies;
  - the plaintext and its String;
  - an inner tree of up to 48 MiB.

  This is the case `IMPORT_MAX_ENCRYPTED_CHARS`'s KDoc (`ImportKdf.kt:48-52`) argues fits a 192 MB heap. The two extra copies (about 12 MB) cut that margin, and the R11b OOM catch is meant as the last line, not the bound.
- **Fix:**
  - in `aesCbcOpen`, `fill(0)` the full buffer right after the copy, or hand `decryptedJson` the `(out, n)` slice and drop the copy;
  - in `aegisEncrypted`, decode `db` into an array with 16 spare bytes and write the tag there once, so no `+` copy is made;
  - keep the bound comment accurate.

**M-7. The Google Authenticator help text has two statements that are not backed by a source.**
- **Where:** `ImportTexts.kt:92-95`.
- **What happens:**
  - «solo exporta sus códigos como un código QR … escanéalo»: Google Authenticator shows several QR codes when there are many accounts, so the singular can leave the user with only the first batch.
  - «La exportación «Google Authenticator» de Aegis funciona igual»: no source is cited, and Aegis's `docs/vault.md` does not describe it. If that Aegis export is a file of `otpauth-migration://` links rather than a QR on screen, the sentence is wrong, because the list reader could open the file directly. The project's veracity rule applies, as with O-1 in 1.3.1.
- **Fix:** say «como uno o varios códigos QR … escanéalos uno a uno». Verify the Aegis export against its current documentation or UI and cite it in the KDoc. If it cannot be confirmed, drop the sentence. «no deja hacer captura de esa pantalla» also needs its source noted in the KDoc.

**M-8. The spec and the docs still describe the pre-R19/R21/R22 behaviour.**
- **Where:** spec `docs/superpowers/specs/2026-11-03-clavero-1.4.0-design.md:178` still has the old account-encrypted text. «Por formato» has no Bitwarden CSV, `.zip`, HTML, Aegis TXT or Edge without notes. The Task 9 phone-test line in `progress.md` still expects «todavía no».
- **What happens:** Task 12 and the final audit check the code against the spec, and the two now disagree.
- **Fix:** bring the spec «Los lectores» in line with R19/R21/R22 and the new texts, in this fix round or as an explicit Task 12 note. Replace the «todavía no» phone test with: «una exportación cifrada pide su contraseña; una equivocada se puede volver a escribir; un archivo .zip o HTML dice qué hacer».

#### Informative (no change asked)

- PBKDF2 runs on Bouncy Castle's Java HMAC, not `javax.crypto`/Conscrypt (native). That choice gives byte arrays Clavero can wipe and the same code on JVM and phone. Bitwarden's 600 000 iterations may take a few seconds on a mid phone, and 2 000 000 more. Add a timing check to the Task 12 phone tests.
- Bitwarden accepts Argon2id up to 1024 MiB and 16 threads, and the export uses the account's KDF (help page). Such a file gets `IMPORT_KDF_TOO_BIG`, by design (spec). The text's «o en un formato sin cifrar» gives the user a way out.
- `checkPbkdf2Iterations(TWOFAS_PBKDF2_ITERATIONS)` (`TwoFasCrypto.kt:51`) checks a constant, so it is a no-op. It is harmless.
- `isZip` does not recognise an empty zip (`PK\x05\x06` only). It falls to «no es texto» or «no reconoce», which is acceptable.

### Revisión experto-seguridad-cripto

Threat model: a hostile export file, crafted or tampered, opened by the owner on his phone. The goal is no crash or hang, no memory or CPU bomb, no partial import, no secret left in memory longer than needed, no leak to logs, and no decryption before authentication.

- **Primitives:** standard only, all from BC 1.86 or JCA: PBKDF2-HMAC-SHA256 (`ImportKdf.kt:133-142`), scrypt (`:145-148`), Argon2id v0x13 (`:151-163`), HKDF-Expand (`:166-170`), AES-GCM through `blockGcm` (`:177-181`), AES-CBC/PKCS7 and HMAC-SHA256 (`BitwardenCrypto.kt:141-173`). Nothing home-made. Known-answer vectors for scrypt, PBKDF2 and HKDF (`ImportKdfTest.kt:86-104`).
- **Encrypt-then-MAC:** the MAC is over IV‖ct, with the MAC key separate from the enc key (HKDF infos «enc»/«mac»). It is verified for both cipher strings before any CBC, with `MessageDigest.isEqual` (`BitwardenCrypto.kt:135`, `:150`). So there is no padding oracle: a padding error is only reachable after a valid MAC.
- **GCM:** 16-byte tags, the nonces from the file (decrypt only, so no reuse question). The output is wiped on a failed tag (`Crypto.kt:276`).
- **KDF bounds:** all checked before work, over all slots (`AegisCrypto.kt:52-69`). The numbers are typed strictly (`ImportJson.kt:283-291`). Gaps: M-1 (the RFC 7914 r = 1 limit) and M-2 (the sum over slots).
- **Size bounds:** an 8 MiB cap before Base64 (`ImportKdf.kt:188-189`, `BitwardenCrypto.kt:99`, `TwoFasCrypto.kt:42`). Exact hex lengths. The plaintext goes through the same tree estimate and depth scan as a plain file (`ImportKdf.kt:218-228`), so a Base64 or JSON bomb inside is refused like a plain one.
- **Zeroing:** see Spec Compliance. The test strength is M-4.
- **Logs and storage:** none. Fixed texts only. The exception messages from BC and the parser are never shown (`Detect.kt:50-52`, `ImportKdf.kt:221-227`).
- **Wrong password versus tampered file:** deliberately one text (`ImportTexts.kt:64`). This is correct, because HMAC and GCM cannot tell the two apart.
- **Confirmed security findings:** none at Important. Correctness and DoS-hardening items are M-1, M-2, M-4, M-5 and M-6.

### Revisión Ingeniero Móvil Senior

- **Threading:**
  - the reader and KDF run on `Dispatchers.Default` (`ImportScreen.kt:286-293`), never on Main;
  - one derivation at a time across the app (`IMPORT_KDF_RUN`, `ImportScreen.kt:263-279`);
  - the ticket is released only after the uninterruptible BC call returns (`withContext` waits for its block), so «cancelar» never starts a second 128 MiB derivation on top of the first;
  - this is correct under structured concurrency.
- **State:**
  - the password is a ViewModel String field, emptied at `takePassword`; the CharArray is zeroed in `finally` (`ImportScreen.kt:295`);
  - there is no `rememberSaveable` or `Bundle`;
  - the file bytes are kept between attempts only in `ImportSession` and are wiped by `dropAll` on back, lock or away (`ImportSession.kt:400-418`);
  - a late result is dropped by `run` (`ImportStateTest.cancelWhileThePasswordIsCheckedDropsTheLateResult`).
- **UX and accessibility:**
  - a wrong password stays on the step with a polite live-region error (`ImportScreen.kt:511`, `:701-703`);
  - the field empties, and «Abrir» is disabled until something is typed;
  - the intro names whose password it is (`importPasswordIntro`, `ImportScreen.kt:508`).
- **Memory:** OOM inside a derivation gives `IMPORT_KDF_TOO_BIG` (`ImportKdf.kt:126-130`). The R11b guard is still around `readImport`. The peak is M-6.
- **CPU and battery:** see M-2 (Aegis slots) and the informative PBKDF2 timing. Both are phone checks for Task 12.
- **MASVS:**
  - STORAGE: nothing written;
  - CRYPTO: standard, authenticated before decrypting;
  - CODE-4: hostile input bounded before work, no reflective or polymorphic decoding (`parseToJsonElement` only);
  - PLATFORM: no WebView, and HTML is never rendered (`Detect.kt:69-83` only looks at 512 bytes).

### Textos para el Abogado Digital (revisión final)

New or changed visible texts, verbatim:

1. `ImportTexts.kt:52-54`, `IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED` (changed, R19): «Esta exportación está cifrada con tu cuenta de Bitwarden y solo Bitwarden puede abrirla. Vuelve a exportar con el formato «.json», o con «.json (Encrypted)» y el tipo «Protegida con contraseña» (no «Cuenta restringida»).»
2. `ImportTexts.kt:64`, `IMPORT_WRONG_PASSWORD` (new): «La contraseña no es correcta o el archivo está dañado.»
3. `ImportTexts.kt:67-69`, `IMPORT_KDF_TOO_BIG` (new, spec text): «Este archivo pide más memoria o más cálculo del que Clavero puede usar en este móvil. Vuelve a exportarlo con los ajustes normales de la otra app o en un formato sin cifrar.»
4. `ImportTexts.kt:72`, `IMPORT_ENCRYPTED_TOO_BIG` (new): «La parte cifrada de este archivo pasa de 8 MB. Clavero no la lee.»
5. `ImportTexts.kt:78-80`, `IMPORT_ZIP` (new, R19): «Es un archivo .zip, y Clavero no lee archivos .zip (como la exportación «.zip (with attachments)» de Bitwarden). Exporta en el formato «.json»: los adjuntos no se importan.»
6. `ImportTexts.kt:83-85`, `IMPORT_HTML` (new, R21): «Es una página HTML, y Clavero no lee exportaciones en HTML. Vuelve a exportar en «.json» o en «.txt» (en Aegis, por ejemplo, la exportación JSON o TXT).»
7. `ImportTexts.kt:92-95`, `IMPORT_GOOGLE_AUTHENTICATOR` (new, R21; shown on the first step, `ImportScreen.kt:500`; see M-7): «Google Authenticator solo exporta sus códigos como un código QR, y no deja hacer captura de esa pantalla. Para traerlos, muestra ese QR en otro dispositivo y escanéalo con Clavero: en la bóveda, pulsa «Añadir», elige «Código 2FA» y luego «Escanear QR». La exportación «Google Authenticator» de Aegis funciona igual.»
8. `ImportTexts.kt:199-203`, `IMPORT_FORMATS` (changed): «Lee las exportaciones en CSV de Chrome, Edge, Brave, Google Contraseñas, Firefox, Apple y Bitwarden; el JSON de Bitwarden, sin cifrar o «Protegida con contraseña»; el JSON de Aegis y la copia de 2FAS, cifrados o sin cifrar, y las listas de enlaces otpauth:// (como el TXT de Aegis, y también los enlaces de exportación de Google Authenticator). No lee el .zip con adjuntos de Bitwarden ni el HTML de Aegis.»
9. `ImportTexts.kt:223-230`, `importPasswordIntro` (new, R21; it replaces `IMPORT_PASSWORD_INTRO`, whose text survives as the last branch):
   - Aegis: «Esta exportación de Aegis está cifrada. Escribe la contraseña de la bóveda de Aegis con la que se hizo.»
   - 2FAS: «Esta copia de 2FAS está cifrada. Escribe la contraseña que pusiste al hacerla en 2FAS.»
   - Bitwarden: «Esta exportación de Bitwarden está protegida con contraseña. Escribe la contraseña que pusiste al exportarla.»
   - Any other format: «Este archivo está cifrado. Escribe la contraseña con la que lo exportaste.»
10. Removed (they were on the Task 9 list):
    - `IMPORT_ENCRYPTED_NOT_YET`: «Esta versión de Clavero todavía no abre exportaciones cifradas. No se ha importado nada. Si exportas sin cifrar, borra ese archivo cuando termines.»;
    - the «Las exportaciones cifradas todavía no.» sentence of `IMPORT_FORMATS`.

    Consequence: the advice «Si exportas sin cifrar, borra ese archivo…» is now only in `IMPORT_NOT_PROTECTED`. That text is unchanged and still on the screen.

All third-party names are inside `ImportTexts.kt`, next to `IMPORT_TRADEMARKS` on the same screen. «Cuenta restringida», «Protegida con contraseña», «.json (Encrypted)» and «.zip (with attachments)» quote Bitwarden's UI labels, from the owner's screenshot of 4-11-2026.

### Assessment

**Task quality: Needs fixes.**

The cryptographic core is sound and well tested:
- standard primitives with known-answer vectors;
- every KDF parameter bounded before work;
- the HMAC verified in constant time before any CBC;
- GCM output wiped on failure;
- the plaintext parsed as hostile data under the same bounds as a plain file;
- secrets wiped on every path;
- fixtures from an independent Python implementation, which I cross-checked a second time.

R13a ended correctly: a wrong password can be retried, and nothing loops.

Two Important defects are in the new Bitwarden CSV reader (R19). I-1 stores multi-URI logins with a broken URL, against the JSON reader's own convention. I-2's exact-header detection contradicts the spec and the plan's Review Focus 1.

The minors are:
- M-1, a range the RFC forbids, which gives a false «dañado»;
- M-2, KDF work summed over Aegis slots;
- M-3, the lost «pendiente de confirmar» list;
- M-4 and M-5, test strength;
- M-6, the memory peak;
- M-7, text accuracy;
- M-8, spec drift.

All are small and local. After the fix round, a scoped re-review of `CsvFormats.kt`, `ImportKdf.kt`, `AegisCrypto.kt`, the four test files and `ImportTexts.kt` is enough.
