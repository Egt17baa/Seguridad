# Task 3 report: Laboratorio clásico (commit 082df93)

## Implemented
- `tools/classic/Caesar.kt`, `Vigenere.kt`, `Enigma.kt`, `Frequency.kt` (pure Kotlin, in jvm-check), plus `EnigmaInput.kt` (form parsing), `ClassicLabSession.kt` (GuardedSession), `ClassicTexts.kt`.
- `ui/screens/ClassicLabScreen.kt`: tabs César, Vigenère, Enigma, Análisis; ViewModel + guarded session; route `tools/classic` in `AppNavHost.kt`; entry in `ToolsScreen.kt` («Laboratorio clásico» / «César, Vigenère y la máquina Enigma»).
- Enigma written from the historical spec (wirings, notches, step before encryption, middle-rotor double step, ring offset). Lowercase letters are read as uppercase and come out uppercase; other characters pass through and do not step (KDoc + test `lowercaseIsReadAsUppercaseAndComesOutUppercase`). César/Vigenère keep case.
- Guard: screen takes `vault`, `lockAfterMs`, `awayInOtherApp`; `watchAway`, `GuardEffects`, `ToolColumn(..., s.guard)`, `TOOLS_AWAY_WIPE_NOTE`. Every typed field (texts, shift, Vigenère key, rings, positions, plugs) lives in the session and is reset on wipe; tab, mode, rotors and reflector are chips (choices) and stay, which the KDoc says. Nothing in `rememberSaveable`. `onCleared` wipes.
- Bounds: `MAX_CLASSIC_CHARS` = 20 000 per text (a longer paste is refused with a note, not cut), 200 for keys/rings/positions/plugs, 6 for the shift. Analysis and Enigma run on the bounded text.
- Copy goes only through `copySensitive`. Notice verbatim in a card. No logs, no storage.
- Análisis: counts, index of coincidence (4 decimals), references EN 0.0667 / ES 0.0775 / uniform 0.0385, a rule-of-thumb reading, and a frequency list with bars.

## Historical message
Operation Barbarossa, 7 July 1941, parts 1 and 2. Source: https://github.com/pgrodrigues/node-enigma-m3/blob/main/README.md (section «Operation Barbarossa, 1941»; it reproduces the message from the Enigma literature). Settings: Enigma I, reflector B, rotors II IV V, rings B U L (2 21 12), plugs AV BS CG DL FU HZ IN KM OW RX. **Tested: the body, both ways, at its message key (part 1 BLA, part 2 LSD).** The indicator group is NOT tested (the source does not give it). The README is a simulator page, not a primary source; the German plaintext emerging from decryption (AUFKL XABTE ILUNG ... / DREIG EHTLA NGSAM ...) confirms the key independently. I did not reach Weierud/Crypto Museum for this message (cryptomuseum.com/crypto/enigma/msg/p1030681.htm is an M4 message, not supported).

## Tests
`ClassicTest` (17 tests): enigmaAaaaa (AAAAA→BDZGO), doubleStep (ADV, AEW, BFX), enigmaIsReciprocal (200 random settings, up to 13 plugs), noLetterEncryptsToItself, historicalMessage, nonLettersPassThrough, lowercase, ringChangesTheOutput (AAAAA with rings 1 1 2 → UBDZG), reflector C and plugs, caesarRoundTrip, vigenereKnown (LXFOPVEFRNHR), IC english (±0.01) and uniform, letterCounts, settingsValidation (duplicate rotor, plug letter, 14 plugs, ring range, positions), form parsing, session wipe. `PlatformSourcesTest`: new source test for the guard, bounds, clearing copy, notice, ToolsScreen entry.
- Results: `:app:testDebugUnitTest` 1352 tests, 0 failures; assembleDebug, lintDebug, checkLicense, checkFragmentVersion, checkAssets BUILD SUCCESSFUL; `gradle -p jvm-check test` BUILD SUCCESSFUL. Lint: 0 errors; no warning in any new file (the 16 warnings listed are version-catalog/manifest ones, more than the "4" baseline because the network is reachable and "newer version" checks run; none from this task).
- TDD honesty: I wrote the implementation before the test file by mistake; the first test compile failed on imports, not on the code. RED evidence by mutation: replacing `val middleOnNotch = pos[1] == notch[1]` with `false` gave `ClassicTest > doubleStep FAILED (15 tests completed, 1 failed)`; restored, GREEN. The first IC test (a repetitive sample) also failed (RED) and was replaced by a natural passage.

## Files
Created: app/src/main/java/app/seguridad/tools/classic/{Caesar,Vigenere,Enigma,Frequency,EnigmaInput,ClassicLabSession,ClassicTexts}.kt; app/src/main/java/app/seguridad/ui/screens/ClassicLabScreen.kt; app/src/test/java/app/seguridad/tools/classic/ClassicTest.kt. Modified: ui/AppNavHost.kt, ui/screens/ToolsScreen.kt, app/src/test/.../PlatformSourcesTest.kt.

## Self-review / concerns
- Rotor choice and reflector are not wiped (choices, not typed); the wipe note says "Lo que escribes", so it stays true. The Enigma key fields (rings, positions, plugs) are wiped to defaults, so a user who leaves for a minute must retype them: accepted.
- Text uses «Enigma deshace lo que hizo»; no forbidden word (SourceTextsTest passed).
- Unicode letters other than ASCII are not counted nor shifted (documented).
- No UI test on a device (no emulator); Compose code compiled and linted only.

## Revisión experto-seguridad-cripto
- Wirings/notches as in the brief: Enigma.kt:6-18; step with double step: Enigma.kt:70-79; test vectors ClassicTest.kt (doubleStep, enigmaAaaaa, historicalMessage). No own crypto claims: the notice says they protect nothing (ClassicTexts.kt:7-8; shown at ClassicLabScreen.kt:~130). IC references: Frequency.kt (EN 0.0667, ES 0.0775).
- Typed keys (Vigenère key, rings, plugs) are wiped by the guard: ClassicLabSession.kt:forget; no saved state (PlatformSourcesTest checks no `rememberSaveable`); clipboard via `copySensitive` only (ClassicLabScreen.kt Output).
## Revisión Ingeniero Móvil Senior
- State in a ViewModel with `ComposeCells`, wiped in `onCleared` and by `watchAway`; input bounded (`limited`, MAX_CLASSIC_CHARS) so recomposition work is O(20 000); results `remember`ed on their inputs; no I/O, so no dispatcher needed. Pure-Kotlin domain in tools/classic compiles in jvm-check.

## Textos para el Abogado Digital (revisión final)
All constants in app/src/main/java/app/seguridad/tools/classic/ClassicTexts.kt, verbatim there. Key ones: «Laboratorio clásico» / «César, Vigenère y la máquina Enigma»; «Son cifrados históricos, rotos desde hace décadas: no protegen nada. Sirven para aprender cómo funcionaban.»; «Lo que escribes no se guarda en el móvil y la bóveda no interviene.»; «Solo se cambian las letras A-Z. La «ñ», las letras con tilde, los números y los signos pasan sin cambiar.»; Enigma intro and «Solo cuentan las letras A-Z: las minúsculas se leen como mayúsculas y salen en mayúsculas. Lo demás (la «ñ», las tildes, los números, los espacios) pasa sin cambiar y no hace girar los rotores.»; analysis reference «Referencia: inglés 0,067, español 0,078 y letras al azar 0,038. Con pocas letras la medida varía mucho.» and the three readings of the index (ANALYSIS_LOOKS_*); field labels and error messages (CAESAR_*, VIGENERE_*, ENIGMA_*, CLASSIC_*, ANALYSIS_*). Reused: TOOLS_AWAY_WIPE_NOTE.

# Fix round 1 (review I-1, M-1 to M-6)
- I-1: ENIGMA_INTRO now says the stepping changes the letter at each press and the reflector is why no letter maps to itself. Test `enigmaIntroGivesTheReflectorAsTheReasonNoLetterMapsToItself`. New text for the Abogado Digital: «Enigma I y M3 con sus cableados históricos. Cada pulsación hace girar los rotores antes de cifrar, así que la misma letra cambia a cada pulsación. El reflector devuelve la corriente por otro camino, y por eso una letra nunca sale como ella misma.» New too: CLASSIC_VALUE_TOO_LONG «Ese valor es demasiado largo y no se ha pegado.»
- M-1: EnigmaInput.kt checks raw ASCII letters / `[0-9]{1,2}` before mapping. Tests in `formBuildsOrSaysWhy`: «ﬁ» plug, «Aß» positions, Arabic-Indic rings (and «001»).
- M-2: `nfc()` (java.text.Normalizer NFC) in caesar, vigenere, Enigma.process and letterCounts. Test `decomposedAccentPassesLikeThePrecomposedOne` (NFD «é» in the three ciphers; Enigma does not step; counts). Output of an NFD input is NFC; a letter with no precomposed form keeps its base (noted in KDoc).
- M-3: Field entries SHIFT, KEY, RINGS, POSITIONS, PLUGS and a `KeyTooLong` note under each key field; PlatformSourcesTest checks no `vm.limited(null` and 5 notes.
- M-4: rotors and reflector moved into `ClassicLabSession` (defaults I II III, B), reset in forget(); `sessionWipeEmptiesWhatWasTyped` extended.
- M-5: indicator asserts added (`WXC: KCH -> BLA`, `CRS: YPJ -> LSD`). I searched the web and cryptocellar.org pages and found no reproduction of this message with indicators; I keep the simulator-README citation, and the test comment says the indicators were checked by decryption and that no other source was read. The old "source has no indicator" claim is kept only as "README does not give the indicators", which is true of the README I read.
- M-6: settingsValidation now: self-pair, non-ASCII plug letter, lowercase plug, and 14 plugs (`List(14){'A' to 'B'}`, with a comment that the size check comes first).
- Commands: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=1` -> BUILD SUCCESSFUL in 5m 5s, 1354 tests, 0 failures, no lint warning in classic files; `gradle -p jvm-check test --no-daemon --max-workers=1` -> BUILD SUCCESSFUL in 1m 31s.

# Fix round 2 (re-review N-1 to N-4)
- N-1: CLASSIC_LETTERS_ONLY and ENIGMA_LETTERS_ONLY now promise unchanged only «la ñ, las letras con tilde habituales, los números y los signos» and say the text is first put in NFC form («que une la tilde escrita aparte con su letra»). New wording for the Abogado Digital is in ClassicTexts.kt. Test `normalisationAlsoFoldsCanonicalSingletons` pins the Kelvin sign U+212A: it becomes K (Caesar shift 1 gives L; Enigma steps once, AAA to AAB) and checks the texts mention NFC and «habituales».
- N-2: historicalMessage comment rewritten impersonally; cites only the README and says the indicators were checked by decryption.
- N-3: duplicate `'A' to 'A'` assertion removed.
- N-4: `chooseTab` already cleared the note; added `clearRefused()` called from `DisposableEffect(vm) { onDispose ... }` so it also goes on leaving the screen (bottom-tab switch). PlatformSourcesTest pins both.
- Commands: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1` -> BUILD SUCCESSFUL in 3m 25s (1355 tests, 0 failures); `gradle -p jvm-check test --no-daemon --max-workers=1` -> BUILD SUCCESSFUL in 1m 15s.
