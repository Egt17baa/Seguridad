# Task 10 report (reconstructed): encrypted readers (Bitwarden protected, Aegis encrypted, 2FAS encrypted), R19, R21, R22

**Reconstructed after the original was lost.** The implementer committed `a1f7771` on 4-11-2026 and the session ended before its report reached the ledger. This report was written on 8-11-2026, in Task 10's fix round 1 (ruling R25), from:
- the commit and its message;
- the code comments;
- the controller's verification (ledger, 8-11-2026);
- the review `task-10-review.md`.

It is not the implementer's own account. Where this report and the code disagree, the code wins.

**Status of `a1f7771`:**
- the review gave spec ✅ and «Needs fixes»: two Important and eight Minor findings, all fixed in fix round 1 (see the ledger);
- the controller's verification on `a1f7771` was green: 1744 app tests and 1739 jvm-check tests, plus `assembleDebug`, `lintDebug`, `checkLicense`, `checkFragmentVersion` and `checkAssets`.

## What `a1f7771` did

- **`imports/ImportKdf.kt` (new)**, what the encrypted readers share:
  - the KDF bounds `checkPbkdf2Iterations`, `checkScrypt`, `checkArgon2id` and `bitwardenArgon2Params`. Each one is `IMPORT_KDF_TOO_BIG` before any work;
  - `IMPORT_MAX_ENCRYPTED_CHARS` (8 MiB), checked before Base64;
  - `Secrets`: every secret array, wiped in `finally`;
  - PBKDF2-SHA256, scrypt, Argon2id v0x13, HKDF-Expand SHA-256 and AES-256-GCM through `blockGcm`, all from Bouncy Castle;
  - `decryptedJson`: strict text, then `checkJsonShape` with the tree budget, then only `parseToJsonElement`;
  - `argon2idKey` builds its own BC Argon2 call (ruling R23), because `Crypto.argon2id` needs a 16-byte salt.
- **`imports/BitwardenCrypto.kt` (new)**, «.json (Encrypted)» with «Protegida con contraseña»:
  - the header is checked first;
  - PBKDF2-SHA256 or Argon2id, with the salt as SHA-256 of the UTF-8 salt string;
  - HKDF «enc»/«mac»;
  - the HMAC of `encKeyValidation_DO_NOT_EDIT`, then the HMAC of `data`, both before any CBC and with `MessageDigest.isEqual`;
  - then AES-256-CBC with PKCS#7, and the plaintext read as a plain export. An inner `encrypted: true` is broken.
- **`imports/AegisCrypto.kt` (new):**
  - every password slot checked before the first scrypt;
  - scrypt opens the master key with AES-256-GCM (`key_params`), and the master key opens `db` (`header.params`);
  - only `type: "totp"` enters.
- **`imports/TwoFasCrypto.kt` (new):**
  - `servicesEncrypted` = `Base64(ct‖tag):Base64(salt):Base64(IV)`, bounded before decoding;
  - PBKDF2-SHA256 with 10 000 iterations (fixed; the file has none), then AES-256-GCM;
  - the plaintext must be a JSON array; `groups` stays in clear.
- **`Detect.kt`, `ImportSession.kt`, `ImportScreen.kt`:**
  - the readers are wired in;
  - end of R13a: a wrong password returns to the password step with `IMPORT_WRONG_PASSWORD`, nothing retries on its own, and a `NeedsPassword` after an attempt is broken;
  - `IMPORT_KDF_RUN` is held until the reader returns.
- **R19:**
  - the Bitwarden CSV reader (`CsvFormats.kt`);
  - the new `IMPORT_BITWARDEN_ACCOUNT_ENCRYPTED`;
  - `IMPORT_ZIP` (`PK\x03\x04`, before decoding);
  - `IMPORT_FORMATS` updated.
- **R21:**
  - `IMPORT_HTML` (the first 512 bytes, before decoding, never shown);
  - Aegis's TXT read by the otpauth list reader;
  - «Google Passwords.csv» pinned;
  - `IMPORT_GOOGLE_AUTHENTICATOR`;
  - `importPasswordIntro(format)`.
- **R22:** Edge's CSV with and without the note column.
- **Fixtures** were written once by `scripts/import-fixtures/gen_encrypted.py`, an independent Python implementation (`cryptography`, `hashlib`) built from the public documents only. Each encrypted file has its plain twin, and the password is `prueba-clavero-1.4`. The reviewer decrypted all four encrypted fixtures again with its own script.

## Tests (at `a1f7771`, from the review)

| Test class | Tests |
|---|---|
| AegisCryptoTest | 10 |
| BitwardenCryptoTest | 12 |
| ImportKdfTest | 11 |
| TwoFasCryptoTest | 10 |
| CsvFormatsTest | 24 |
| DetectTest | 13 |
| ImportStateTest | 42 |
| ImportTextsTest | 13 |

Every test passed. They cover:
- the plain twins;
- a wrong password with no decryption;
- every byte flipped (Bitwarden: caught by the HMAC before CBC);
- the KDF bounds in under 50 ms;
- truncation at 5 points;
- zeroing;
- the known-answer vectors (RFC 7914 scrypt, PBKDF2-SHA256, RFC 5869 Expand);
- the source pins for the constant-time MAC check and the encrypt-then-MAC order.

## Pendiente de confirmar con exportaciones reales

The owner gives no real exports (owner decision (e)). These points come from documentation only. Check them with one real export per format after release, or in 1.4.1:

- **Bitwarden:**
  - the Argon2id salt is SHA-256 of the UTF-8 salt string, and the salt is used as text, not Base64-decoded (`BitwardenCrypto.kt`, `bitwardenKeys`);
  - with PBKDF2, `kdfMemory` and `kdfParallelism` may be present as `null`, and they are ignored;
  - the CSV `login_uri` cell holds every URI of a login separated by commas (fix round 1, I-1). Since fix round 2 (ruling R26) it is split only on a comma followed by optional spaces and a URI scheme (`,\s*(?=[A-Za-z][A-Za-z0-9+.-]*:)`), so a comma inside a URI (`?q=1,2`) stays in it. A comma inside a URI that is followed by something that looks like a scheme (`?a=1,b:2`) would still split.
- **Aegis:**
  - the password is hashed in UTF-8;
  - GCM takes no associated data;
  - the salt is 32 bytes per `docs/vault.md`, though 16 to 64 bytes are read;
  - only the first password slot is tried (ruling R24).
- **2FAS:**
  - 10 000 iterations;
  - a 12-byte IV;
  - a 256-byte salt;
  - the password is in UTF-8;
  - `groups` stays in clear;
  - the `reference` field is not used, because the tag over the services is the check.

## For Task 12 (README, phone tests)

- One real export per format: Bitwarden protected with PBKDF2 and with Argon2id, the Bitwarden CSV (with a login with two URIs, and one whose URL has a comma, such as `?q=1,2`), Aegis encrypted, and 2FAS encrypted. Each opens with its password and gives the same entries as the source app.
- How long PBKDF2 takes at Bitwarden's 600 000 iterations on a mid-range phone (the informative note of the review).
- The phone test that replaces Task 9's «todavía no» line: an encrypted export asks for its password; a wrong one can be typed again; a `.zip` or HTML file says what to do instead.
