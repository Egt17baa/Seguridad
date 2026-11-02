# Review of Task 4: CRY-A03, wiping the key copies in the Bouncy Castle parameters

Range `d0702a3..490e973` (one commit, `Crypto.kt` and `CryptoTest.kt`). The diff file was complete, no hunk was cut off. No git commands, no suite re-run. For specific doubts I ran four throwaway Java programs against `bcprov-jdk18on-1.86.jar` (outside the repo, in `/tmp/claude-0/-home-user-Seguridad/912d88ad-2ca8-526e-8826-8cf3516a3c57/scratchpad/chk/Chk.java`, `Chk2.java`, `Chk3.java`, `Chk4.java`).

## Spec Compliance

**Verdict: spec compliant**, with one factual claim in the new KDoc and in the report that is wrong (Important 1). The behaviour the plan asks for is there and is correct.

- Step 1, tests that fail or pin:
  - `KeyParameter(k).key` is not `k` and is the same instance on two calls: `CryptoTest.kt:431`. It would fail if `getKey()` started returning a clone, or if `KeyParameter` stopped copying.
  - Observable, real `KeyParameter`, also on a bad tag: GCM through the `KeySpy` engine (`CryptoTest.kt:41`, tests at `:456` and `:487`); ChaCha through the helper that takes the `KeyParameter` (`:515`), as the plan proposed.
  - Vectors: the diff only adds to `CryptoTest.kt`; no existing test, resource or `DialectTest` is touched.
- Step 3: `val kp = KeyParameter(key)` before the `try`, `finally { kp.key.fill(0) }` in both Bouncy Castle paths (`Crypto.kt:263,275` and `:337-350`, wipe at `:349`), with the why in the KDoc (`:255-261`, `:332-336`). AES `SecretKeySpec` (`:322`) and `HKDFParameters` (`:282`, `:405`) untouched, as the design says. AES stays on Conscrypt.
- Interfaces: `aead(...)` has the same signature and the same bytes (the only change in its body is the ChaCha branch, `:329`). `blockGcm` goes from `private` to `internal`; `chaChaPoly` is new and `internal`. The plan said "ninguna nueva" but itself proposed an `internal` helper, so this is within the plan.
- Design `CRY-A03`: both bullets are met (finally + two pins). The third (accepted risk written in the report) is only partly met because the sentence is inaccurate (Important 1, Minor 5).
- Not verified (no git): author, trailer `Co-Authored-By: Claude Opus 5.5`, absence of `Claude-Session`, and that the commit was pushed.

## Strengths

- Smallest possible change that does the job: no new dependency, no reflection in production, no change to `aead`'s contract, no provider change for AES.
- The `finally` is the right shape. `kp` exists before the `try`, so there is no path where it exists without a `finally` to clear it. Success, `CryptoFailure` on a bad tag and any other exception (for example `IllegalArgumentException` from a bad nonce) all clear it.
- The `KeySpy` is a good idea: `GCMBlockCipher.init` hands the engine the very `KeyParameter` that `blockGcm` built (`AEADParameters.getKey()` returns the one passed in), so the test checks the real object without adding hooks to production code. The implementer's RED run shows the key bytes (`070A0D10…`) still in the spy's parameter before the fix, so the spy really sees the key. The mutation check (no `finally`) is also reported.
- The pin tests would really fail on a Bouncy Castle change:
  - `assertNotSame(key, kp.key)` fails if `KeyParameter` stops copying. That would be the dangerous case, because `fill(0)` would then wipe the caller's key.
  - `assertSame(kp.key, kp.key)` fails if `getKey()` clones.
  - `assertSame(kp.key, lastKey)` fails if `lastKey` becomes a clone; a missing field gives a `fail(...)` with an explanation; a null or other-typed field throws, so it cannot pass silently.
- Test 6 (`CryptoTest.kt:543`) protects against the worst possible regression, wiping the caller's key, for all six algorithms (including CASCADE), on success, decrypt and bad tag.
- Honest report: the ChaCha private copy and the bad-tag buffer are declared, not hidden.

## Named risks, one by one

| Risk | Result |
|---|---|
| Zeroing on every exit | Yes in both paths (`Crypto.kt:274-276`, `:348-350`). Covered for ok, bad body, bad tag, wrong AAD and wrong key (`CryptoTest.kt:487`, `:515`). Other exceptions are covered by the `finally` but have no test (fine). |
| Use-after-wipe | None. The `KeyParameter` is created and wiped inside one call; the cipher object is local and finished before the `finally`. `chaChaPoly` has one caller (`Crypto.kt:329`), with a fresh `KeyParameter` each call. |
| CASCADE | `aead(layer, …, k, …)` gets the HKDF-derived `k`; `blockGcm` copies it into its own `KeyParameter`; the cascade `finally` still wipes `k` afterwards (`Crypto.kt:313-316`, `k.fill(0)` at `:315`). The caller's `key` only goes into `cascadeKey`. No double use. |
| STREAM / file encryption | `FileCrypto.kt:68,79,112,122` call `aead` per record (64 KiB, `FileCrypto.kt:12`) with the same caller `key`. `KeyParameter(key)` copies (verified in 1.86: `kp.getKey() != key`), so the caller's `key` is never zeroed; it is wiped once at the end by `FileCrypto`'s own `finally`. Test 6 would catch a non-copying `KeyParameter`. |
| Tests observe the real `KeyParameter` | GCM: yes, through the spy. ChaCha: the test builds the `KeyParameter` and the function takes it over, so the object is real for `chaChaPoly`, but the wiring `aead -> chaChaPoly(KeyParameter(key), …)` (`:329`) is not observed (Minor 3). |
| Identity pins fail if BC changes | Yes, see Strengths. They cover the two assumptions the single `fill(0)` relies on. |
| Byte-identical output | Yes by construction: same engine, same `AEADParameters`, the wipe happens after `doFinal`. See Minor 4 about what independently pins the bytes. |
| Bad-tag partial plaintext | Real, confirmed. Minor 2. |
| Residual-risk sentence | Not accurate as written. Important 1 and Minor 5. |

## Issues

### Critical

None.

### Important

1. **A false statement of fact about the library, in code and in the sentence for the final audit: "neither can be wiped without reflection" and "No hay API para ponerlas a cero" are not true for `ChaCha20Poly1305`.**
   - Where: `Crypto.kt:333-335` (KDoc of `chaChaPoly`), and the report's concern 1 and its "Frase de riesgo residual".
   - Evidence: in `bcprov` 1.86, `ChaCha20Poly1305.init(...)` copies the key into its `private final byte[] key` and into the `ChaCha7539Engine` state (`Salsa20Engine.engineState`, words 4 to 11). Calling `init(false, AEADParameters(KeyParameter(new byte[32]), 128, nonce, new byte[0]))` again on the same instance, with the public API only, overwrites both. I checked it (`Chk2.java`): after the re-init the private `key` is all zeros and `engineState[4..11]` are all 0. `init` in decrypt mode has no nonce-reuse check, so it cannot fail for that reason.
   - Impact: the task is meant to remove the key copies that Bouncy Castle keeps, and this one has a public-API way out. As written, the ChaCha path wipes 1 of 3 copies (the `KeyParameter`'s) and the code and the audit text then say the other two cannot be wiped. The finding's own ceiling is low (Baja 1.9, heap read by root/debugger, ChaCha is not the default algorithm), but a wrong sentence in the audit is worth fixing now; the project rule says text must not claim something false about the code.
   - Fix (small): hoist the cipher out of `run` and re-key it with zeros in the `finally`, guarded so it cannot hide the original exception:
     ```kotlin
     } finally {
         kp.key.fill(0)
         // Overwrites ChaCha20Poly1305's private key copy and the engine's key words (public API, bcprov 1.86).
         try { cipher.init(false, AEADParameters(KeyParameter(ByteArray(KEY_BYTES)), TAG_BYTES * 8, nonce, ByteArray(0))) } catch (_: RuntimeException) {}
     }
     ```
     A test needs the `ChaCha20Poly1305` instance (pass it in, as the `KeyParameter` is passed today) and a reflection read of the field `key`, in the same style as `CryptoTest.kt:443`, failing with a clear message if the field disappears. If the controller prefers not to do this now, the minimum is to correct the KDoc and the sentence (ChaCha: private copy and engine key words stay; they can be overwritten by re-keying with zeros, not done) and record it as a conscious decision, not as "no API".
   - Same trick does not help GCM engines in general: re-keying Twofish allocates new `gSubKeys`/`gSBox` arrays and leaves the old ones (`Chk3.java`), so that part really stays an accepted risk.

### Minor

2. **Bad-tag partial plaintext in `out` (declared by the implementer): confirmed, real, but Minor, and not part of CRY-A03.**
   - Evidence (`Chk.java`): with the tag flipped, `GCMBlockCipher` (Twofish) and `ChaCha20Poly1305` both write the whole decrypted plaintext into `out` through `processBytes`/`doFinal` before the tag check, then throw. The buffer was left holding the full text (`secret plaintext that is long enough…`). `out` is local to the lambda (`Crypto.kt:267-270`, `:341-344`), so after the exception nobody can wipe it.
   - Severity: same adversary and ceiling as CRY-A03 (heap read of a live process). Narrow reach: only the non-default algorithms that go through Bouncy Castle (ChaCha, Twofish, Serpent, Camellia); the default AES-GCM path (Conscrypt) returns nothing on a bad tag; for CASCADE the outer layers produce next-layer ciphertext and the AES layer is Conscrypt. It needs the correct key with a damaged or tampered input (a wrong password gives garbage). Up to 64 KiB per record, or one whole text.
   - It does break the plan's global rule "fill(0) en cada salida, también en las ramas de error" for a secret (plaintext). The project rule "los detalles menores se arreglan, no se aplazan" applies. It is a ~5-line change in the same two functions (hold `out` outside the lambda, clear it when an exception leaves), but a test needs a way to see the buffer, so it is a bit more than the code change. Recommendation: do it in this task together with Important 1, or give it its own line in the plan for the final audit. Do not leave it only in this report.

3. **The production wiring of the ChaCha branch is not observed.** `Crypto.kt:329` (`chaChaPoly(KeyParameter(key), …)`) is only covered indirectly by test 6, which checks the caller's key and the bytes, not that the `KeyParameter` is wiped. A future edit that inlines ChaCha again without the `finally` would pass every test. It is the cost of the plan-mandated helper shape; a cheap mitigation is a one-line source-text check, or keep ChaCha going through a single `aead` path that the spy can reach. Low value, mention only.

4. **"Same bytes as aead" is not an independent check.** `CryptoTest.kt:477` and `:525` compare `blockGcm`/`chaChaPoly` with `aead`, which calls them, so they cannot fail. The real known-answer coverage is the existing tests: NIST case 16 (AES), RFC 8439 (ChaCha, `CryptoTest.kt:77`), and for Twofish/Serpent GCM the cascade fixture `criptopanel/file-cascade-rawkey.cpenc`. Camellia-GCM has no fixed-ciphertext vector in the repo, and the CriptoPanel fixtures are what Task 6 removes. This task does not change any bytes, so there is no regression here; but when Task 6 removes those fixtures, a Clavero-dialect known answer for Twofish, Serpent and Camellia GCM should stay or be added. I did not check what Task 6 plans to do with them.

5. **Residual-risk sentence, besides Important 1.** For the final audit:
   - Add the GCM hash subkey `H` and the multiplier tables that `GCMBlockCipher` keeps (`private byte[] H`, `multiplier`), derived from the key, as residual material.
   - Positive detail you can claim: for Twofish the engine's `workingKey` is the same array as `kp.key` (`Chk4.java`), so it is wiped too; what stays is the expanded subkeys.
   - "Quedan en el heap hasta el recolector" is true but best effort on ART: a moving collector can leave older copies of an array that was relocated before the wipe. One clause is enough ("el borrado es de mejor esfuerzo").
   - "AES vía Conscrypt, que guarda su propia copia en el `Cipher`" is plausible but I did not verify it; I would say "puede guardar".

6. **Style, optional.** `chaChaPoly` mutates the argument (documented in the KDoc, `internal`). `blockGcm` and `chaChaPoly` now duplicate the same body over `AEADCipher`; a shared helper would remove it, but that is cleanup beyond this task.

## Assessment

**Task quality:** Needs fixes (small).

**Reasoning:** The implementation does what CRY-A03 and the plan ask. Both Bouncy Castle paths clear their `KeyParameter` on every exit, the caller's key is never touched (CASCADE and the STREAM loop included), the two assumptions behind a single `fill(0)` are pinned by tests that would fail if Bouncy Castle changed, the tests observe the real `KeyParameter` for GCM and the output bytes do not change. What blocks approval is not the wipe itself but one wrong claim: the code comment and the residual-risk sentence for the final audit say the ChaCha20Poly1305 internal key copy and engine key words cannot be wiped without reflection, and a public-API re-key with zeros does wipe them (verified on 1.86). Fix it or correct the text, and decide explicitly on the bad-tag plaintext buffer (Minor 2) so it is fixed here or planned, not forgotten.

## Revisión experto-seguridad-cripto

Read: `SKILL.md`, `references/cripto-moderna.md` (§2 AEAD, §10).

- AEAD parameters unchanged: 128-bit tag (`TAG_BYTES * 8`, `Crypto.kt:266`, `:340`), same nonce and AAD; no custom crypto; only `javax.crypto` and Bouncy Castle (project rule).
- Key hygiene on every exit: `Crypto.kt:275`, `:349`; tests at `CryptoTest.kt:456`, `:487`, `:515`. The caller's key is never touched: `CryptoTest.kt:431` and `:543`.
- Assumptions of the library that the design relies on are pinned: `CryptoTest.kt:431` (copy + same instance) and `:443` (`lastKey` is the same array). Confirmed independently on 1.86 with scratch programs.
- Honest residual risk, to be written in the final audit with these corrections: ChaCha private copy and engine words (can be overwritten, Important 1), `SecretKeySpec`/Conscrypt, `HKDFParameters` clone (`Crypto.kt:282`, `:405`), expanded subkeys of Twofish/Serpent/Camellia, GCM `H` and tables, best effort on a moving collector. Severity of CRY-A03 stays Baja; it is not lowered to close the cycle.
- Memory hygiene gap on the plaintext after a bad tag (Minor 2): rule "fill(0) en cada salida, también en las ramas de error", pre-existing.
- Reject-AES-to-BC decision respected (no provider change).

## Revisión Ingeniero Móvil Senior (file:line)

Read: `seguridad-criptografia-movil/SKILL.md`.

- Concurrency and lifecycle: `aead`, `blockGcm` and `chaChaPoly` stay pure functions with no shared state; the `KeyParameter` lives for one call (`Crypto.kt:263-276`, `:329`, `:337-350`). Safe if two coroutines call `aead` with the same caller key.
- AES-256-GCM still goes through the platform provider (`Crypto.kt:322`), with hardware acceleration and without moving to Bouncy Castle.
- Android/R8: no reflection in production code; reflection only in the JVM test (`CryptoTest.kt:443`). `internal` visibility is module-only and adds no public API surface. No `proguard` change needed.
- Cost: one 32-byte `fill(0)` per call (and, if Important 1 is applied, one extra ChaCha key setup, negligible against a 64 KiB record).
- No UI text, permission, network or dependency change. Nothing for the Abogado Digital.
