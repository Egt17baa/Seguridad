# Task 3 re-review, fix round 2 (031ef66..ad7e744)

Read-only; diff and report read once; no tests re-run (report: 1355 tests, 0 failures; jvm-check green).

### Finding Verdicts

- **N-1 (NFC folds more than accents; texts overclaim)** — ADDRESSED. ClassicTexts.kt: CLASSIC_LETTERS_ONLY and ENIGMA_LETTERS_ONLY now promise unchanged only «la ñ, las letras con tilde habituales, los números y los signos» and add «Antes de cifrar, el texto se pasa a la forma normalizada (NFC), que une la tilde escrita aparte con su letra.»
  - Match with the code: `caesar`, `vigenere`, `Enigma.process` and `letterCounts` all call `nfc()` (java.text.Normalizer NFC) first, which composes base + combining mark. The wording is exact for that, and «habituales» leaves out letters with no precomposed form and singletons such as the Kelvin sign. The Kelvin behaviour is pinned by `normalisationAlsoFoldsCanonicalSingletons` (caesar("K sign",1) = "L"; Enigma steps AAA to AAB).
  - The help text does not list the Kelvin sign, which is fine because it is not a "habitual" letter; the text makes no claim about it.
  - Forbidden words: none in the two changed strings («habituales», «normalizada», «NFC» are not on the list). The Analysis tab uses `letterCounts` (NFC too) but shows neither text; no claim there.
- **N-2 (first-person comment)** — ADDRESSED. The `historicalMessage` comment is impersonal, cites only the README, and says the indicators were checked by decryption. The "Crypto Museum / Enigma literature" attribution is gone.
- **N-3 (duplicate assertion)** — ADDRESSED. The extra `'A' to 'A'` line is removed in `settingsValidation`.
- **N-4 (note outlives screen)** — ADDRESSED. `chooseTab` already cleared `refused`; new `clearRefused()` is called from `DisposableEffect(vm) { onDispose { vm.clearRefused() } }` (ClassicLabScreen.kt, after `GuardEffects`). PlatformSourcesTest pins both. It also fires on rotation, harmless (the note is only a transient hint and holds an enum, no text).

### New Breakage in the Fix Diff

None (no Critical, Important or Minor). `DisposableEffect` import is added; the key `vm` is stable per entry.

### Out-of-Scope Observations

None.

### Verdict

**Fix round:** All findings addressed, no new breakage.

## Revisión experto-seguridad-cripto

- The only code change is UI text and a note-clearing hook; cipher code (Enigma.kt, Caesar.kt, Vigenere.kt, Frequency.kt) is untouched in this round.
- The new help texts state exactly what `nfc()` does (compose a detached accent with its base) and promise "sin cambiar" only for the usual accented letters; the canonical-singleton fold (Kelvin sign to K) is real and tested (ClassicTest `normalisationAlsoFoldsCanonicalSingletons`).
- The M-5 source comment is honest: one source (the README), indicators checked by decryption.

## Revisión Ingeniero Móvil Senior

- `DisposableEffect(vm)` with `onDispose { vm.clearRefused() }` (ClassicLabScreen.kt, below `GuardEffects(s.guard, lockAfterMs)`); `refused` is Compose state holding an enum only, no secret, no saved state.
- `chooseTab` clears `refused` (unchanged); both are asserted in PlatformSourcesTest (N-4 line).
- No new permission, I/O or dependency.

## Textos para el Abogado Digital (revisión final)

Changed in this round (ClassicTexts.kt):
- CLASSIC_LETTERS_ONLY: «Solo se cambian las letras A-Z. La «ñ», las letras con tilde habituales, los números y los signos pasan sin cambiar. Antes de cifrar, el texto se pasa a la forma normalizada (NFC), que une la tilde escrita aparte con su letra.»
- ENIGMA_LETTERS_ONLY: «Solo cuentan las letras A-Z: las minúsculas se leen como mayúsculas y salen en mayúsculas. Lo demás (la «ñ», las letras con tilde habituales, los números, los espacios) pasa sin cambiar y no hace girar los rotores. Antes de cifrar, el texto se pasa a la forma normalizada (NFC), que une la tilde escrita aparte con su letra.»
- No forbidden word in either. Earlier-round strings (ENIGMA_INTRO, CLASSIC_VALUE_TOO_LONG) are unchanged.
