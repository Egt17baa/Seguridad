# Task 3 review: Laboratorio clásico (César, Vigenère, Enigma), e107b20..082df93

Reviewer: task reviewer (spec + quality), read-only. One commit, `082df93`, 12 files, +892.

### Spec Compliance

- ✅ **Spec compliant.** Every item in the brief is in the diff:
  - Interfaces: `caesar` (Caesar.kt:8), `vigenere` (Vigenere.kt:8), `Rotor`/`Reflector` with the brief's wirings and notches, checked letter by letter (Enigma.kt:4-16), `EnigmaSettings` with its checks (Enigma.kt:23-41), `Enigma.press/process/positions` (Enigma.kt:52-109), `letterCounts` and `indexOfCoincidence` (Frequency.kt:4-25).
  - Every test the brief asks for is in ClassicTest.kt: enigmaAaaaa, doubleStep, enigmaIsReciprocal, noLetterEncryptsToItself, historicalMessage, nonLettersPassThrough, caesarRoundTrip, vigenereKnown, both IC tests and settingsValidation.
  - Screen: four tabs (ClassicLabScreen.kt:90-95); the machine's own names (Walzenlage, Umkehrwalze, Ringstellung, Grundstellung, Steckerbrett, in ClassicTexts.kt:37-45); the notice word for word (ClassicTexts.kt:7-8, shown at ClassicLabScreen.kt:153); nothing stored and no vault use.
  - Route (AppNavHost.kt:252) and Tools entry (ToolsScreen.kt:49), with the title and subtitle the brief gives.
  - Commit message, author and trailer as required, and pushed to `origin/ccr-a1a6eabb-765f9g` (checked with `git log -1` and `git branch -r --contains`).
- Extras, all justified by the Task 2 pattern or by testability:
  - `EnigmaInput.kt` (parses the form into errors, so `EnigmaSettings.require` never throws from the UI);
  - `ClassicLabSession.kt` (the `GuardedSession`);
  - `ClassicTexts.kt` (the texts, readable from the JVM);
  - two tests the brief did not ask for: lowercase, and ring vector `UBDZG`.
- No-copied-code rule: nothing comes from an importer. The Barbarossa texts are historical message data, not code. The Enigma code is a plain reading of the documented mechanism.
- ⚠️ **Cannot verify from the diff:** how the screen behaves on a device. The implementer had no emulator; Compose was only compiled and linted. The controller or owner should try on the phone: paste over 20 000 characters, rotate, lock the vault from the notification, leave for more than the lock time, and use Copiar.

### Strengths

- **The Enigma is correct, and I checked it independently.** I wrote a separate model in Python from the historical description: the pawl and notch formulation, ring offset `pos − ring`, and plugboard in and out (`/tmp/claude-0/-home-user-Seguridad/afa106da-72c3-5be8-bbce-c61e4a0844be/scratchpad/enigma.py`). It reproduces every vector:
  - `AAAAA` → `BDZGO`;
  - the double step `ADU` → `ADV`, `AEW`, `BFX`;
  - rings 1 1 2 → `UBDZG`;
  - both Barbarossa bodies at BLA and LSD, giving the German plaintext the test asserts.
  
  My model also decrypts the **published indicator groups**: at WXC, `KCH` → `BLA`, and at CRS, `YPJ` → `LSD`. So the key data in the test is trustworthy, even though its cited source is a secondary simulator README.
- **The Kotlin stepping matches the real machine** (Enigma.kt:70-79). The middle rotor's own notch moves both the middle and the left rotor (the double step). Otherwise the right rotor's notch moves the middle one. The right rotor always moves. The left rotor's notch is rightly ignored. The inverse tables and `floorMod` are correct (Enigma.kt:56-57, 82-86).
- **Hostile input fails safely everywhere I checked:**
  - Vigenère with a key that has no A–Z letters: the screen catches it before calling (ClassicLabScreen.kt:192), and the function itself `require`s a letter (Vigenere.kt:10). No division by zero and no loop.
  - Caesar shift: at most 6 characters, `toIntOrNull`, and only 0..25 accepted (ClassicLabScreen.kt:168-173).
  - The Enigma form: repeated rotors, odd or self plug tokens, more than 13 plugs, repeated letters, rings out of range and bad positions all become error texts (EnigmaInput.kt:13-26). After those checks, `EnigmaSettings.init` cannot throw.
  - Surrogate pairs, RTL marks, «ñ» and precomposed accents pass through character by character without stepping.
- **Length caps come before any work.** `limited()` refuses the new value before it is set, and the old value stays (ClassicLabScreen.kt:123-131). So the Enigma, analysis and Caesar/Vigenère runs never see more than 20 000 characters, and the cost on each change is linear and tiny.
- **The guard pattern of Task 2 is followed completely:** `watchAway` (:117), `GuardEffects` and `ToolColumn(..., s.guard)` (:149-150), `TOOLS_AWAY_WIPE_NOTE` (:206), the wipe in `onCleared` (:138), every typed field reset in `forget()` (ClassicLabSession.kt:45-55), no `rememberSaveable`, and a source test for all of it (PlatformSourcesTest.kt, new test).
- **Copying can only go through `copySensitive`** (ClassicLabScreen.kt:238). The two `SelectionContainer`s are inside `ToolColumn`'s `NoCopy` (SecurityToolsScreen.kt:219, the named risk «system Copy outside copySensitive», checked). Typed text does not feed the keyboard's learning, because `NoLearningInput` covers every field of the app (NoLearningInput.kt:13-28, checked for the claim «no se guarda en el móvil»).
- The screen says what it does not protect (CLASSIC_NOTICE), as the plan requires. No logs, no I/O, no new dependency or permission.

### Issues

#### Critical (Must Fix)

None.

#### Important (Should Fix)

**I-1. `ENIGMA_INTRO` gives the wrong reason for a historical fact** (ClassicTexts.kt:34-36).
- The text says «Cada pulsación hace girar los rotores antes de cifrar, y **por eso** una letra nunca sale como ella misma.» That is false.
- A letter never encrypts to itself because of the **reflector**: the current comes back by a different path, so the substitution is an involution with no fixed points. The stepping is why the same letter gives a different output at each press.
- Why it matters: this is a teaching tool, and the project's truthfulness rule forbids a text that says something false. The KDoc in Enigma.kt:43-48 gets it right; the UI text does not.
- Fix, for example: «Enigma I y M3 con sus cableados históricos. Cada pulsación hace girar los rotores antes de cifrar, así que la misma letra cambia a cada pulsación. El reflector devuelve la corriente por otro camino, y por eso una letra nunca sale como ella misma.»
- If a test pins the text, update it. The new text goes to the Abogado Digital list.

#### Minor (Nice to Have; the project rule says every one gets fixed)

**M-1. The Enigma form accepts non-ASCII input as letters and digits** (EnigmaInput.kt:16, 20, 14).
- `String.uppercase()` does full Unicode case mapping, and the check runs on the result. I checked with `java` in the scratchpad that it does `toUpperCase(Locale.ROOT)`. So:
  - the plug token «ﬁ» (U+FB01) becomes `FI` and is taken as the plug F–I; «ﬆ» becomes `ST`;
  - positions «Aß» become `ASS` and are accepted.
- `toIntOrNull` also accepts other Unicode digits, so rings «١ ١ ١» (Arabic-Indic) are taken as 1 1 1.
- No crash: the result is still valid A–Z or 1..26. But the form accepts input that its labels («letras de la A a la Z», «números enteros») say it refuses, and the owner asked for no errors on hostile input.
- Fix:
  - check the raw characters (`it in 'A'..'Z' || it in 'a'..'z'`) before mapping each one with `uppercaseChar()`;
  - accept rings only when they match `[0-9]{1,2}`;
  - add these cases to `formBuildsOrSaysWhy`.

**M-2. A decomposed accent (NFD) does not «pasar sin cambiar»** (Caesar.kt:11, Vigenere.kt:13, Enigma.kt:89-95).
- Take «é» typed or pasted as `e` + U+0301. Its base `e` is shifted: Caesar 3 gives «h́». In the Enigma tab it is encrypted and steps the rotors.
- That contradicts CLASSIC_LETTERS_ONLY («las letras con tilde … pasan sin cambiar», ClassicTexts.kt:24-25) and ENIGMA_LETTERS_ONLY («las tildes … no hace girar los rotores», ClassicTexts.kt:51-53).
- Text pasted from a macOS or iOS source is often NFD.
- Fix: normalise each text field to NFC before processing (`java.text.Normalizer`, which also works in jvm-check), with a test. If a letter has no precomposed form, the base still changes; the text can say so, or keep the claim to «las letras con tilde habituales».

**M-3. A paste over the limit into a key field is refused silently** (ClassicLabScreen.kt:168, 187, 269, 276, 283).
- These fields call `limited(null, …)`, so `refused` stays `null` and no note appears. The field just does not change.
- The text fields show CLASSIC_TOO_LONG. The model, HashLab, shows a `TooLongNote` under every field (HashLabScreen.kt:302-379).
- Fix: give the key fields `Field` entries (SHIFT, KEY, RINGS, POSITIONS, PLUGS) and show the note under each.

**M-4. Part of the Enigma key survives the guard** (ClassicLabScreen.kt:109-110; ClassicLabSession.kt:45-55).
- The rotor order (Walzenlage) and the reflector are part of the key, but they live in the ViewModel and not in the session, so a lock or time away does not reset them.
- `TOOLS_AWAY_WIPE_NOTE` («Lo que escribes aquí y el resultado se borran…», ToolTexts.kt:628-630) stays literally true, because chips are not typed. But the «PIN y códigos» rule means the key goes. Keeping two of the five key parts is inconsistent, and resetting them costs nothing.
- Fix: put `rotors` and `reflector` in `ClassicLabSession` with defaults (I II III, B), reset them in `forget()`, and extend `sessionWipeEmptiesWhatWasTyped`.
- The tab and the encrypt/decrypt mode are not key and can stay.

**M-5. The historical test leaves out the published indicator, and its source is secondary** (ClassicTest.kt:57-75).
- The indicator groups are published with the message, and my independent model confirms them: at Grundstellung WXC, `KCH` → `BLA`; at CRS, `YPJ` → `LSD`.
- Adding `assertEquals("BLA", machine("WXC").process("KCH"))` and `assertEquals("LSD", machine("CRS").process("YPJ"))` makes the test check the whole procedure, not only the body.
- The cited source (pgrodrigues/node-enigma-m3 README) is a simulator page. The KDoc should also cite one standard reproduction of the 1941 message from the Enigma literature (Sullivan and Weierud's material, or one of its usual reproductions), after checking it, and should not say the source lacks the indicator if the new citation has it.
- Body and key are already confirmed by the German plaintext, so this is Minor.

**M-6. Two `settingsValidation` cases are misleading** (ClassicTest.kt:148-149).
- Line 148 (`('A'..'N').map { it to it }`) builds 14 self-pairs, so it does not show which rule rejects it.
- Line 149 builds pairs with non-letters: `'A' + 14 + 13` is `'\\'`. Its `.take(14)` does nothing.
- 14 disjoint pairs cannot exist over 26 letters, so the size limit can only be tested through the size check that comes first. One comment saying so and a clean 14-pair list (`AB` repeated, as in `formBuildsOrSaysWhy`) would make the intent clear.

**Not findings, weighed:**
- **Lowercase comes out uppercase from Enigma:** the real machine had no case. It is documented (Enigma.kt:47-48, ENIGMA_LETTERS_ONLY) and tested. Accepted.
- **TDD order** (implementation before the tests, RED shown by mutating `middleOnNotch`): a process deviation, reported honestly. The vectors I checked independently cover the risk. Recorded, nothing to fix in code.
- **`indexOfCoincidence` counts the letters again** after `letterCounts` (ClassicLabScreen.kt:~320): linear and bounded, negligible.
- **Test-output noise:** the reported lint warnings (16, version catalog and manifest «newer version») are not from this task's files. The controller should confirm they match the network-reachable baseline.

### Assessment

**Task quality:** Needs fixes.

**Reasoning:** The cipher code is correct, which I verified independently, including the double step, the rings, the plugboard and the Barbarossa message with its indicators. It is bounded and follows the guard pattern. But one UI text states a false reason for Enigma's best-known property, and five minor input, consistency and test gaps remain, which the project rule says must all be fixed.

## Revisión experto-seguridad-cripto

- Wirings, notches and reflectors match the brief character for character (Enigma.kt:4-16). Stepping with the double step is at Enigma.kt:70-79, and the rotor path with ring offset at Enigma.kt:82-86. Every vector is confirmed by my separate Python model (scratchpad `enigma.py`): BDZGO; ADV, AEW, BFX; UBDZG; Barbarossa BLA and LSD bodies; indicators WXC/KCH→BLA and CRS/YPJ→LSD.
- No crypto claims: CLASSIC_NOTICE (ClassicTexts.kt:7-8) says they protect nothing, shown at ClassicLabScreen.kt:153. No `javax.crypto` and no randomness used.
- Wrong technical explanation in the UI: I-1 (ClassicTexts.kt:34-36). The reflector, not the stepping, is why no letter maps to itself.
- Input validation: Vigenère with an empty key is guarded twice (ClassicLabScreen.kt:192, Vigenere.kt:10). Enigma form validation is at EnigmaInput.kt:13-26, but Unicode case folding and digits widen what it accepts (M-1). NFD accents do not behave as the text says (M-2).
- Secret handling: typed fields are wiped (ClassicLabSession.kt:45-55); rotor order and reflector are not (M-4, ClassicLabScreen.kt:109-110). The clipboard is only `copySensitive` (ClassicLabScreen.kt:238), and `NoCopy` wraps the `SelectionContainer`s (SecurityToolsScreen.kt:219). No logs and no storage.
- Historical source: secondary but checked against data. The indicator should be added and a standard source cited (M-5).

## Revisión Ingeniero Móvil Senior

- **State:** ViewModel and `ComposeCells` (ClassicLabScreen.kt:104-140), never `rememberSaveable` (enforced in PlatformSourcesTest), and `onCleared` wipes (:138).
- **Guard:** `watchAway` (:117), `GuardEffects` and `ToolColumn` with the guard (:149-150), and `guard.typed()` on every edit (:124).
- **Performance:** caps checked before the value is set (:123-131). Results are `remember`ed on their inputs (:177, :195, :296). Enigma on 20 000 characters is about 200 k integer operations, fine on the main thread with no I/O, so no dispatcher is needed. The `Text` layout of 20 000 monospace characters is the heavier part; acceptable, same as HashLab.
- **Compose correctness:** the early `return` in `AnalysisTab` is legal in a non-inline `@Composable`. `remember(counts)` is stable because `counts` is itself remembered on the text. Chips write new list instances, so recomposition triggers.
- **UX gap:** a silent refusal on key fields (M-3).
- **Architecture:** the pure-Kotlin domain in `tools/classic` is built by jvm-check (jvm-check/build.gradle.kts:7-18 excludes only `ui`, `android` and the app classes). Files are small and single-purpose. ClassicLabScreen.kt is 347 lines, in line with HashLabScreen.
- **Not run on a device** (⚠️ above).

## Textos para el Abogado Digital (revisión final)

All are new, from `app/src/main/java/app/seguridad/tools/classic/ClassicTexts.kt` unless noted:

- CLASSIC_TITLE «Laboratorio clásico»; CLASSIC_SUBTITLE «César, Vigenère y la máquina Enigma» (also in ToolsScreen.kt:49, icon «🏛»).
- CLASSIC_NOTICE «Son cifrados históricos, rotos desde hace décadas: no protegen nada. Sirven para aprender cómo funcionaban.»
- CLASSIC_NOT_SAVED «Lo que escribes no se guarda en el móvil y la bóveda no interviene.»
- Tabs: «César», «Vigenère», «Enigma», «Análisis».
- «Cifrar», «Descifrar», «Texto», «Resultado», «Copiar».
- CLASSIC_TOO_LONG «Ese texto es demasiado largo y no se ha pegado.»
- CLASSIC_LETTERS_ONLY «Solo se cambian las letras A-Z. La «ñ», las letras con tilde, los números y los signos pasan sin cambiar.» (see M-2)
- CAESAR_SHIFT_LABEL «Desplazamiento (0 a 25)»; CAESAR_SHIFT_INVALID «El desplazamiento tiene que ser un número entero de 0 a 25.»
- VIGENERE_KEY_LABEL «Clave (letras A-Z)»; VIGENERE_KEY_INVALID «La clave necesita al menos una letra de la A a la Z.»; VIGENERE_NOTE «Las letras de la clave se repiten a lo largo del texto; los signos no gastan letras de la clave.»
- ENIGMA_INTRO «Enigma I y M3 con sus cableados históricos. Cada pulsación hace girar los rotores antes de cifrar, y por eso una letra nunca sale como ella misma.» (**false reason, I-1: to be replaced**)
- ENIGMA_ROTORS «Rotores (Walzenlage), de izquierda a derecha»; «Izquierdo», «Central», «Derecho».
- ENIGMA_REFLECTOR «Reflector (Umkehrwalze)».
- ENIGMA_RINGS_LABEL «Anillos (Ringstellung), tres números de 1 a 26»; ENIGMA_POSITIONS_LABEL «Posición inicial (Grundstellung), tres letras»; ENIGMA_PLUGS_LABEL «Tablero de conexiones (Steckerbrett), por ejemplo AV BS CG».
- ENIGMA_REPEATED_ROTOR «Cada rotor solo puede usarse una vez.»
- ENIGMA_RINGS_INVALID «Los anillos son tres números enteros de 1 a 26, por ejemplo 1 1 1.»
- ENIGMA_POSITIONS_INVALID «La posición inicial son tres letras de la A a la Z, por ejemplo AAA.»
- ENIGMA_PLUG_FORMAT «Cada conexión son dos letras distintas de la A a la Z, separadas por espacios.»
- ENIGMA_PLUG_REPEATED «Una letra solo puede estar en una conexión.»
- ENIGMA_PLUG_TOO_MANY «El tablero admite 13 conexiones como máximo.»
- ENIGMA_LETTERS_ONLY «Solo cuentan las letras A-Z: las minúsculas se leen como mayúsculas y salen en mayúsculas. Lo demás (la «ñ», las tildes, los números, los espacios) pasa sin cambiar y no hace girar los rotores.» (see M-2)
- ENIGMA_SAME_KEY «Para descifrar, escribe el texto cifrado con los mismos ajustes: Enigma deshace lo que hizo.»
- enigmaFinalPositions «Posición de los rotores al terminar: <posiciones>»
- ANALYSIS_INTRO «Cuenta las letras de un texto y mide su índice de coincidencia, para ver a qué se parece.»
- ANALYSIS_TEXT_LABEL «Texto a analizar»
- ANALYSIS_TOO_FEW «Hacen falta al menos dos letras A-Z para medir el índice de coincidencia.»
- ANALYSIS_REFERENCE «Referencia: inglés 0,067, español 0,078 y letras al azar 0,038. Con pocas letras la medida varía mucho.»
- ANALYSIS_LOOKS_NORMAL «Se parece al de un texto normal: es lo que da un texto sin cifrar o cifrado cambiando cada letra siempre por la misma (como César).»
- ANALYSIS_LOOKS_RANDOM «Se parece al de letras al azar: es lo que da un cifrado que cambia de alfabeto a cada letra (como Vigenère o Enigma).»
- ANALYSIS_IN_BETWEEN «Queda entre los dos: con este texto no se puede decir a cuál se parece.»
- ANALYSIS_FREQUENCIES «Letras, de la más a la menos frecuente»
- analysisLetters «Letras A-Z: <n>»; analysisIc «Índice de coincidencia: <valor>»
- Frequency rows (ClassicLabScreen.kt, `AnalysisTab`): letter, count, «<n,n> %» and a «█» bar.
- Reused, not new: TOOLS_AWAY_WIPE_NOTE.

I found no forbidden word in any new string: «imposible», «100 %», «seguro», «garantiza», «limpio», «de verdad», «anónimo», «sospechoso» and the rest of the list are absent. No third-party names.
