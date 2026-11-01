# Task 2 re-review 1 (CRY-A01), fix `d981e72..1be454e` (round 1 `e76565a` + owner amendment `1be454e`; `20d8e69` docs)

Scope: the six findings of `task-2-review.md` and steps 6-8 of the brief (owner amendment), inside the fix diff only. I read the diff once and made targeted reads at HEAD for the named risks. I ran no git commands and no tests. I ran two Python checks in the scratchpad: the asset formats, and the exact refusal rate of the Generator plus two Monte Carlo runs of 2 million draws each. Test counts (1239 jvm-check / 1244 app) are the implementer's claim. The report gives the output for each covering test (RED, GREEN and global), so I take it as reported.

## Finding Verdicts

| Finding | Verdict | Evidence |
|---|---|---|
| **Important 1**: truthful limits, matching the NEW behaviour | **ADDRESSED** | KDoc `vault/MasterPolicy.kt:86-93`: estimate plus word list, «Barcelona1» passes because it is in no list, and the non-ASCII bonus «is why the word check exists». The whole-core limit is stated and «does not guarantee». `:75-79`: «contraseña123», «Password1!» and «Ábaco12345» are refused with `words`; without them the rule is the estimate. The KDoc no longer says «contraseña123 passes». Pinned: estimate alone at `MasterPolicyTest.kt:90-100`, whose KDoc at `:80-89` points to the amendment. Refused with the lists at `:179-185` and `:163-176`. Residual stale wording in the controller docs: see N1. |
| **Minor 2**: digits-only and «always» | **ADDRESSED** | `MasterPolicy.kt:95-97`: «passes in the tests», «sometimes refused up to about 24 characters». |
| **Minor 3**: test comment | **ADDRESSED** | `MasterPolicyTest.kt:295-306`: comment «letters and digits», and a real letters-only case (`digits = false, symbols = false`). |
| **Minor 4**: duplicate advice | **ADDRESSED** | `ui/screens/VaultScreen.kt:257-264`: the hint is inside `if (problem != MASTER_TOO_WEAK)`. The word branch returns the same constant (`MasterPolicy.kt:104`), so it is covered too. Test `MasterPolicyTest.kt:405`. |
| **Minor 5**: jvm-check reruns ui source tests | **ADDRESSED** | `jvm-check/build.gradle.kts:43-46`: `inputs.files(fileTree("../app/src/main/java") { include(androidOnly) })`, with `androidOnly` at `:7-12`. That covers the new `ui/screens/MasterWords.kt`. The report shows the UP-TO-DATE → probe → rerun output in both rounds. The optional aside (no test for a third `create`/`changeMaster` caller) was not added. A grep still finds exactly one of each (`VaultScreen.kt:279`, `VaultBackup.kt:128`). |
| **Minor 6**: live region | **ADDRESSED** | `VaultScreen.kt:254` and `VaultBackup.kt:246` use `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`. «No coinciden» is untouched. Test `MasterPolicyTest.kt:394`. Whether TalkBack really announces it stays in the owner's phone test. |
| **A. `masterCore`** | **ADDRESSED** | `MasterPolicy.kt:34-44`: `lowercase(Locale.ROOT)`, NFD, `\p{Mn}` stripped (`:23`), `Character.isLetter` per code point. Examples at `MasterPolicyTest.kt:121-130`, `tr-TR` check at `:132`. |
| **A. `masterWords`** | **ADDRESSED** | `MasterPolicy.kt:51-56`: every word passed through `masterCore`, empty ones dropped, plus `commonPasswords()` passed the same way. Tests `:143-154`. |
| **A. `newMasterProblem(candidate, words = emptySet())`** | **ADDRESSED** | `MasterPolicy.kt:101-106`: length, then estimate, then word. Order at `MasterPolicyTest.kt:187`. Without `words` it behaves as before (`:204-217`, 16 candidates, both with no argument and with `emptySet()`). |
| **A. `Health.kt`, «Salud» unchanged** | **ADDRESSED** | Only `internal fun commonPasswords(): Set<String> = COMMON` is added (`health/Health.kt:22-23`). `COMMON` stays `private`, and `estimateBits` (`:85-93`) is not in the diff. Test `:104`: the list is the one that gives 0 bits. |
| **Step 6 tests** | **ADDRESSED** | Every example in the brief is checked. «contraseña123», «Contraseña2024», «Password1!», «iloveyou12», «abandon1234» and «Ábaco12345» are refused (`:163-170`), and so is every EFF word of 7 letters or more followed by «123», as written and capitalised (`:171-176`). Generator phrases (200 × 3 lists × 4 separators) and the 16-character passwords use `assertAccepted`, which checks both with and without the words (`:249-253`, `:262`, `:275`, `:295`). The «Barcelona1» guard fails with a message if «barcelona» ever enters a list (`:199-200`). The source test checks the words reach `newMasterProblem` and the button waits for them (`:353-371`). |
| **Step 7: loading** | **ADDRESSED** | `ui/screens/MasterWords.kt:24-28`: `remember { mutableStateOf<Set<String>?>(null) }`, then `LaunchedEffect(Unit)` and `withContext(Dispatchers.IO)`, with no `rememberSaveable` or `Bundle`. Buttons: `VaultScreen.kt:270` and `VaultBackup.kt:235` (`words != null`). A failed read gives `emptySet()` (`MasterPolicy.kt:64-68`; test `:219-235`, which checks the streams close, a partial failure, an IOException and an IllegalStateException). The new limit is in the KDoc (`MasterPolicy.kt:91-92`). |
| **Import dialog unchanged** | **ADDRESSED** | `VaultBackup.kt:233-235`: `words` is `null` without `newPassword`, so nothing is loaded, and `ok` keeps `a.length >= MIN_MASTER`. The call site `:158` passes no `newPassword`. Tests `:353-371` and `:388`. |
| **Step 8** | **ADDRESSED** (claimed, with output) | The commit subject in the diff header matches the brief. 1239/1244 tests, 0 failures. `MasterPolicyTest` goes from 17 to 31. Mutation table in the report. |

**Named risks, checked:**
- **NFC vs NFD:** `bip39_spanish.txt` is NFD: 334 of 2048 lines are NFD-only, none NFC-only, no BOM. The test literals are NFC, the keyboard form («Ábaco12345» appears twice in NFC and never in NFD), and `:124` adds the combining form. The normalization is the same on both sides (`MasterPolicy.kt:35`, `:53-54`). OK.
- **EFF format:** 7776 lines, one word each, 0 lines with digits. 4 are hyphenated (`drop-down` at `eff_large_wordlist.txt:2009`, …) and `masterCore` drops the hyphen. Dice numbers would have been stripped anyway. Pinned at `:156-161`. OK.
- **A load that never finishes:** every `Exception` becomes `emptySet()`, so the state is always set. An `Error` crashes rather than hangs. `LaunchedEffect` is cancelled when the screen leaves. The blocking read has no suspension point, so `catch (Exception)` swallows no cancellation. OK.
- **Rotation:** neither `<activity>` declares `configChanges` (`AndroidManifest.xml:19,29`). A rotation recreates the screen: the lists load again and the button is off for a few milliseconds. Nothing goes into `rememberSaveable`, and the lists are not secret. The password itself was already in `remember` before this change. OK.
- **New user-visible text:** none. The UI diff adds only comments, a `Modifier` and an `if` around the existing «Consejo» literal. `MASTER_TOO_WEAK` is byte-identical.
- **Generator output refused / fixed seed:** see N2 for the figures. The fixed seed is appropriate and hides no real flake. The only nondeterminism is the rule's own refusal rate, a documented product property. The refused case is pinned at `:311-316`. The phrase tests keep `SecureRandom` and cannot fail on the word side: at least 15 letters, and the longest word in the set is 10 («contrasena»). SHA1PRNG seeded before first use is deterministic on the SUN provider, which is used because no test registers BC first. The same pattern is already in `ContactsTest.kt:103`.

## New Breakage in the Fix Diff

No Critical or Important issues. Two new Minor issues:

- **N1 (Minor): «contraseña123 passes» and «siempre pasa» wording is stale after the amendment.**
  - `docs/superpowers/plans/2026-11-01-clavero-1.3.1.md:52` (Review Focus 2) and `context.md:40` say «Ojo: «contraseña123» sí pasa…». That is now false: the create and change screens refuse it. The import examples («password123», «aaaaaaaaaa») still hold.
  - Review Focus 3 (`plans:53`, `context.md:41`) says the Generator «siempre pasa». For a 16-character password it is now «almost always» (report Concern 1).
  - In the test, the name and message at `MasterPolicyTest.kt:90,94` («…pass», «is accepted today») describe only the estimate. The KDoc at `:87-88` explains this, but the message should say «by the estimate alone».
  - The report's round-1 Abogado note («pasan «Barcelona1», «contraseña123» y «Contraseña2024»») is followed by round 2 saying it «se suma al de la ronda 1». Round 2 replaces the «contraseña123» part; it does not add to it. The final audit and the Abogado Digital read these notes.
  - Fix: controller edits to the plan, context and report, plus a rename in the test.
- **N2 (Minor): the refusal frequencies are overstated by about 2×, in the cautious direction.**
  - Where: `MasterPolicy.kt:98` («about 1 time in 27,600»), `MasterPolicyTest.kt:289-291` («one run in 140»), report Concern 1.
  - What I computed: an exact count over the real 10,937 cores (by length: 214 of 3 letters, 1020 of 4, 1924 of 5, …), with the Generator's actual algorithm. That algorithm always forces one lowercase and one uppercase letter (`crypto/Generator.kt:36-37`), so 12 free positions have a letter with probability 52/89.
  - Results: about 1 in 61,000 at 16 characters, 1 in 3,900 at 12 and 1 in 1.1 million at 20 (the default). A run of 200 draws would fail about once in 308 runs. Two Monte Carlo runs of 2 million draws at 12 characters gave 1 in 3,534 and 1 in 3,922, which confirms the model. The implementer's figure looks like it treats each position independently.
  - The KDoc's «the longest word of the lists has 9» is also slightly off: the set includes «contrasena» (10) from «Salud». The argument still holds.
  - Fix: reword to «fewer than 1 in 25,000» or give the exact figures.

## Out-of-Scope Observations

- `jvm-check` `tasks.test` does not declare `../app/src/main/assets` as an input. `MasterPolicyTest` now depends heavily on the lists (`allWords`, `:156-161`), so an asset-only change leaves the test UP-TO-DATE locally. CI's clean builds are not affected. The fix is one line: `inputs.dir("../app/src/main/assets")`. This predates the diff: the Generator tests of `d981e72` already read assets.
- `ui/screens/SecurityToolsScreen.kt:940` reads the EFF list on the main thread inside `remember`. This predates the change and is not in the diff.

## Verdict

**Fix round: all findings addressed, no new Critical or Important breakage.** Two new Minor issues are open: N1 (stale wording, mostly in the controller's docs) and N2 (overstated figures). The project rule «Detalles menores: se arreglan todos» applies.

## Revisión experto-seguridad-cripto

- **Threat:** offline guessing against a `.sgbak` with a dictionary plus the usual decorations. The core-in-list check closes «palabra + números/símbolo» for about 10.9k words (`MasterPolicy.kt:101-106`), in line with NIST SP 800-63B §5.1.1.2 (refuse dictionary words and common passwords). The KDoc admits it is not a full dictionary (`:86-93`).
- **Normalization:** symmetric, independent of the phone's locale, NFD with `\p{Mn}` stripped. It is correct against the NFD Spanish list and NFC input. Compatibility forms (ligatures, full-width letters) are not folded; that only lets the owner pick a weaker master on purpose and is not a bypass of anything.
- **Only new masters:** the import, unlock, fingerprint and `importReplace` paths do not load or wait for the lists (`VaultBackup.kt:233-235`, `:158`). No change to formats or the KDF.
- **Fail-open to the estimate on a read failure:** this is the owner's design. Length and estimate still apply, and no other gate opens.
- **Secrets in memory:** no `rememberSaveable`, `Bundle` or log. The lists are not secret. The extra `String` copies from `masterCore` are declared (`MasterPolicy.kt:31-32`) and are the same residual as `estimateBits`.

## Revisión Ingeniero Móvil Senior

- **Architecture:** pure logic in `vault/` with an injected `open`, tested on the JVM (`MasterPolicy.kt:64`). The UI helper is small (`MasterWords.kt:24-28`).
- **Concurrency and lifecycle:** the work runs in `Dispatchers.IO` under `LaunchedEffect(Unit)`, and the state is written on the main thread. The conditional `rememberMasterWords()` in `PasswordDialog` is keyed to a parameter that is fixed for each call site, so slot placement is safe (`VaultBackup.kt:233`). Rotation reloads the lists, which is harmless.
- **UX:** the button waits only for the load, while the red text already shows the length and estimate. The hint is hidden under `MASTER_TOO_WEAK` (`VaultScreen.kt:258`). The polite live regions are on both problem texts.
- **Tests:** the fixed seed is appropriate (see N2 for the real rate). The source tests strip comments (`MasterPolicyTest.kt:331`). The rendering, the announcement and the timing of the button still need the owner's phone test (report Concern 5).

## Textos para el Abogado Digital (revisión final)

No new texts. The note to carry forward replaces round 1's: «10 caracteres, estimación ≥ 50 bits y que no sea una sola palabra de las listas de la app (comunes de «Salud», EFF, BIP-39 en/es), solo al crear la bóveda o cambiar la maestra». «contraseña123» is now refused. «Barcelona1» and two words together pass. The policy and the store listing must not promise more (see N1).
