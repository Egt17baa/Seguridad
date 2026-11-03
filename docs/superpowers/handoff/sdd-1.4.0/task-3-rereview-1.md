# Task 3 re-review, fix round 1 (082df93..031ef66)

Read-only. Diff read once; two small checks (Enigma.kt press, GuardedSession/CellFactory) and a grep of the new strings against the forbidden words. No test re-run (report shows 1354 tests, 0 failures, jvm-check green).

### Finding Verdicts

- **I-1 (ENIGMA_INTRO wrong reason)** — ADDRESSED. ClassicTexts.kt (diff hunk at `ENIGMA_INTRO`) now says the stepping changes the letter at each press and the reflector is why no letter maps to itself. Pinned by `enigmaIntroGivesTheReflectorAsTheReasonNoLetterMapsToItself` (ClassicTest.kt, new). The string is built by concatenation with single spaces; the test's `replace("  ", " ")` is harmless.
- **M-1 (non-ASCII accepted as letters/digits)** — ADDRESSED. EnigmaInput.kt: `isAsciiLetter` on the raw characters before `uppercase()` for positions and plugs; rings only `length in 1..2` and all `'0'..'9'`. Tests: «ﬁ», «Aß», Arabic-Indic digits, «001».
  - Surrogate pairs: each surrogate is not an ASCII letter, and `typedStart.length != 3` counts UTF-16 units, so they are rejected, no throw.
  - Fullwidth letters and digits: rejected (not in the ASCII ranges).
  - Other combining marks: not ASCII letters, rejected.
  - After the checks, `uppercase()` runs on pure ASCII (Kotlin uses Locale.ROOT), so there is no «ı»/«İ» surprise.
  - Empty rings string gives size 1, so the error text, no throw. `EnigmaSettings.init` cannot throw after these checks.
- **M-2 (NFD accent)** — ADDRESSED. `nfc()` in caesar, vigenere, `Enigma.process` and `letterCounts` (Caesar.kt, Vigenere.kt, Enigma.kt, Frequency.kt). `java.text.Normalizer` accepts any String: unpaired surrogates pass through, no throw. Cost is linear on at most 20 000 chars. `Enigma.press` is unchanged and still public for single chars, which is fine (the screen uses `process`). Test `decomposedAccentPassesLikeThePrecomposedOne` covers the three ciphers, no step, and the counts. KDoc says a letter with no precomposed form keeps its base letter.
- **M-3 (silent refusal in key fields)** — ADDRESSED. Five new `Field` entries, `KeyTooLong` under SHIFT, KEY, RINGS, POSITIONS, PLUGS (ClassicLabScreen.kt, five calls; PlatformSourcesTest asserts 5 calls and no `vm.limited(null`). `CLASSIC_VALUE_TOO_LONG` is a fixed text and does not echo the pasted value. A refused paste leaves the old value and sets only `refused`, a Field enum, not the text.
- **M-4 (rotors/reflector survive the guard)** — ADDRESSED. `rotors` and `reflector` are now cells in `ClassicLabSession`, with defaults I II III and B, reset in `forget()`; the screen reads and writes `s.rotors` and `s.reflector`, with no `vm.rotors` left (asserted). `sessionWipeEmptiesWhatWasTyped` sets V IV I and C, wipes, and asserts I II III and B. `CellFactory.cell` is generic, so `List<Rotor>` and `Reflector` fit. `DEFAULT_ROTORS` is an immutable list and the UI copies with `toMutableList()` before writing, so the shared default is never mutated.
- **M-5 (indicator asserts and source)** — ADDRESSED (honest outcome). The asserts `WXC: KCH -> BLA` and `CRS: YPJ -> LSD` are in `historicalMessage` and pass in the reported run. They match my earlier independent model. The comment cites only the README actually read, says it gives the settings, the bodies and the plaintext but not the indicators, and says the indicators were checked by decryption and that no other source was read. The report says web and cryptocellar searches found no reproduction. Nothing false is claimed. Style only: the comment is in the first person ("I read no other source") and keeps the phrase «from the Crypto Museum / Enigma literature», which is the README's own attribution (Minor, below).
- **M-6 (misleading settingsValidation cases)** — ADDRESSED. The two bad cases are gone; the new ones are self-pair, `'ñ'` plug, lowercase plug and `List(14) { 'A' to 'B' }` with a comment that the size check comes first. They are clear. The first `'A' to 'A'` line now appears twice (the old line 602 and the new one); harmless duplication (Minor).

### New Breakage in the Fix Diff

No Critical. No Important.

Minor:
- **N-1. NFC changes characters other than accents** (Caesar.kt `nfc`, used in all four entry points). Canonical singletons are folded: the Kelvin sign U+212A becomes ASCII `K` and is then shifted or stepped. Also, the output of any input is in NFC form, so a passthrough character such as an NFD sequence or a CJK compatibility ideograph can differ from what was typed. No crash and no bypass, but CLASSIC_LETTERS_ONLY («pasan sin cambiar») and ENIGMA_LETTERS_ONLY are not literally true for these rare cases, nor for a letter with no precomposed form (the KDoc says so; the UI text does not). Fix at the Abogado/controller's choice: keep it, or soften the UI text to «las letras con tilde habituales pasan sin cambiar».
- **N-2. M-5 comment wording** (ClassicTest.kt, comment above `historicalMessage`): first-person phrasing and the retained «from the Crypto Museum / Enigma literature» could be read as a second source. Suggest «the README states the settings come from the Crypto Museum»; not blocking.
- **N-3. Duplicate assertion** in `settingsValidation` (`'A' to 'A'` twice).
- **N-4. `refused` outlives a tab switch** (ClassicLabViewModel): a "too long" note for a key field can stay visible after changing tab and is cleared by the next accepted edit. Cosmetic, and `refused` holds no secret.

Checks that came out clean: over-limit messages are constants and do not echo the paste; `refused` stores an enum only; no new `rememberSaveable`, no clipboard path, no log; new strings (ENIGMA_INTRO, CLASSIC_VALUE_TOO_LONG) contain none of the forbidden words (grepped ClassicTexts.kt against the list; ForbiddenWords tests run over all main strings in the reported full run).

### Out-of-Scope Observations

None.

### Verdict

**Fix round:** All findings addressed, no new Critical/Important breakage. Only Minor notes N-1 to N-4 (the project rule says Minor ones get fixed; N-1 is the only one that touches user-visible truthfulness).

## Revisión experto-seguridad-cripto

- Input filters: ASCII-only letters and digits on raw characters before case mapping, EnigmaInput.kt (diff lines for `isAsciiLetter`, `typedStart`, plug tokens, ring regex-free check). Surrogate pairs, fullwidth letters and foreign combining marks are rejected; none can reach `EnigmaSettings.init`.
- NFC step (Caesar.kt `nfc`, Vigenere.kt, Enigma.kt `process`, Frequency.kt `letterCounts`): total on any String, linear, no exception. Residual: Kelvin sign folds to `K` (N-1).
- Enigma mechanics untouched (Enigma.kt `step`, `through`, `press` unchanged); vectors and indicators still assert and pass.
- Wipe now covers the whole key: rotors and reflector in ClassicLabSession (rotors/reflector cells, `forget()`), asserted by `sessionWipeEmptiesWhatWasTyped`.
- Over-limit notes do not echo pasted text (CLASSIC_VALUE_TOO_LONG constant, ClassicLabScreen.kt `KeyTooLong`).
- UI claim fixed: reflector is the reason no letter maps to itself (ENIGMA_INTRO).
- Source honesty for the Barbarossa test: acceptable (M-5).

## Revisión Ingeniero Móvil Senior

- State: `s.rotors` / `s.reflector` in the ViewModel-owned session via `ComposeCells`; no `rememberSaveable`, asserted in PlatformSourcesTest (`vm.rotors` !in src, `vm.limited(null` absent).
- Compose: `remember(s.rotors, s.reflector, s.rings, s.positions, s.plugs)` keys on state reads; chips write new list instances, so recomposition triggers (ClassicLabScreen.kt EnigmaTab).
- `limited` keeps calling `session.guard.typed()` first, so a refused paste still counts as use of the screen.
- Performance: NFC on every recomposition-keyed run of at most 20 000 chars; inside `remember` for results, cheap. `letterCounts` is called again by `indexOfCoincidence`, unchanged.
- Not run on a device (as before).

## Textos para el Abogado Digital (revisión final)

Changed or new since the first review (ClassicTexts.kt):
- ENIGMA_INTRO (replaces the old one): «Enigma I y M3 con sus cableados históricos. Cada pulsación hace girar los rotores antes de cifrar, así que la misma letra cambia a cada pulsación. El reflector devuelve la corriente por otro camino, y por eso una letra nunca sale como ella misma.»
- CLASSIC_VALUE_TOO_LONG (new): «Ese valor es demasiado largo y no se ha pegado.»
- For the lawyer/controller to judge (N-1): CLASSIC_LETTERS_ONLY and ENIGMA_LETTERS_ONLY still say accented letters «pasan sin cambiar»; this holds for letters with a precomposed form, not for a combining mark on a letter with none.
- No forbidden word in any of them.
