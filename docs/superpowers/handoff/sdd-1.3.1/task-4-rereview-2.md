# Re-review of Task 4, fix round 2 (`86b6eec..b208d9d`)

Read-only: no git commands, no edits, no subagents, no test runs. Read: `task-4-rereview-1.md`, the whole diff `review-86b6eec..b208d9d.diff` once, and the current files at the lines cited below.

The diff touches two files and only KDoc lines: `Crypto.kt:259-261` and `CryptoTest.kt:47-49`. No code line changed, so the fix is comment-only. Every `+`/`-` line sits inside a `/** … */` block (`Crypto.kt:255-262`, `CryptoTest.kt:41-50`).

## Finding Verdicts

1. **CryptoTest.kt Kat KDoc, Serpent claim: ADDRESSED**
   - `CryptoTest.kt:47-48` now reads: "SeedCryptoTest has a published vector for Twofish and only a round trip for Serpent".
   - Twofish: `SeedCryptoTest.kt:40` asserts the all-zero Twofish vector `57ff739d4dc92c1bd7fc01700cc8216f`, labelled "Twofish paper / ecb_tbl.txt, I=1". That is a published vector. I did not recompute it (no test runs).
   - Serpent: `SeedCryptoTest.kt:41-47` is a round trip only (encrypt, decrypt, assert zeros). There is no Serpent vector, so the new claim is true.
   - One residual imprecision in the same sentence, logged under New Breakage as Minor.

2. **Crypto.kt `runAead` KDoc: ADDRESSED** (paraphrase with the same three claims, not verbatim)
   - KDoc, `Crypto.kt:259-261`: "It is wiped on every failure. On success the array that is returned is never wiped; the only buffer wiped then is an unused longer one, when the result is a shorter copy of it."
   - Code, `Crypto.kt:263-277`:
     - `:269` `val result = if (total == out.size) out else out.copyOf(total).also { out.fill(0) }`. On success only `out` (the longer, unused buffer) is wiped, and only in the shorter-copy branch. `result` is never filled.
     - `:270-271` sets `done = true` only after `result` exists, and `:274-275` runs `finally { if (!done) out.fill(0) }`. Every failure wipes `out`: `CryptoFailure` (`:273`) and any other exception.
   - The code matches the three claims.

3. **task-4-report.md, «el subclave H»: ADDRESSED**
   - The residual-risk sentence at `task-4-report.md:160` now reads «…y la subclave `H` y las tablas que GCM deriva de la clave».
   - `task-4-report.md:189` still contains «el subclave `H`», but only inside the change note that quotes the old wording («el subclave `H`» pasa a «la subclave `H`»). It is a record of the change, not the sentence, so it is not an error.
   - My first grep for the literal «subclave H» found nothing because of the backticks around `H`. A grep on «subclave» found both lines.

## New Breakage in the Fix Diff

- **Minor (comment accuracy, not blocking), `CryptoTest.kt:47-48`:** "so for those two only the GCM composition is independent" is not accurate for Twofish. The Twofish raw engine does have an independent published vector at `SeedCryptoTest.kt:40`. For Serpent, the raw engine is not checked independently in this repo (`SeedCryptoTest.kt:41-47`). Suggested clause: "…and only a round trip for Serpent; so the Serpent raw engine is unchecked here, and for both the GCM composition is the independent check". No behaviour effect. It can go with the next touch of the file.
- No other breakage. The `runAead` KDoc agrees with the code (`Crypto.kt:263-277`) and with the `blockGcm` KDoc (`Crypto.kt:279-287`), which says the caller's key is not touched and the Twofish working key is wiped.

## Revisión experto-seguridad-cripto

- Skill read: `SKILL.md`, and `references/cripto-moderna.md` (AEAD use at `:34`; GCM nonce and full 128-bit tag at `:35`).
- The diff changes no algorithm, parameter, key handling or output. It does not touch the AEAD paths the reference covers.
- The security-relevant claims in the comments are memory-wiping claims, and I verified them against the code: `Crypto.kt:269` and `Crypto.kt:274-275` (see Finding 2).
- The residual-risk wording in the report (`task-4-report.md:160`) matches the KDoc in `Crypto.kt:281-287`, which says the rest stays until garbage collection.
- Security findings: none.

## Revisión Ingeniero Móvil Senior

- Skill read: `seguridad-criptografia-movil`.
- The diff touches only pure-JVM Bouncy Castle code and its tests (`Crypto.kt`, `CryptoTest.kt`). It changes no Keystore, StrongBox, biometric, TLS, logging, permission or UI behaviour.
- The wiping is described as best effort, which is what the skill's own note says ("no hay API para ponerlas a cero"). The KDoc at `Crypto.kt:259-261` and the report at `task-4-report.md:160` make no stronger claim.
- Android findings: none.

## Textos para el Abogado Digital (revisión final)

None. The changed text is KDoc in code (`Crypto.kt:259-261`, `CryptoTest.kt:47-49`) and an internal review note (`task-4-report.md:160`, `:189`). It is not UI text, public documentation, the privacy policy or the Play listing.

## Verdict — **Fix round:** All findings addressed

All three findings from `task-4-rereview-1.md` are addressed. The one new Minor nit (the Twofish/Serpent clause, `CryptoTest.kt:47-48`) does not block. The fix changes no behaviour.
