# Task 2 re-review 2 (CRY-A01), fix round 3 `1be454e..b5c72b1` (`74b8f4a` is controller docs)

Scope: N1, N2 and the `jvm-check` assets input of `task-2-rereview-1.md`, inside the fix diff only. I read the diff file once and the «Ronda 3» section of the report. I made targeted reads of HEAD (read-only) and ran no git commands, no gradle and no tests. The only computation was a Python check in `-I` mode over `app/src/main/assets`, to confirm the corrected figures and the longest known word. I take the test counts (1239 jvm-check, 1244 app) and the gradle outputs as the report gives them.

## Finding Verdicts

| Finding | Verdict | Evidence |
|---|---|---|
| **N1 (a): test name and message say «the estimate alone»** | **ADDRESSED** | `MasterPolicyTest.kt:90` is now `…so the estimate alone accepts contrasena123 and Contrasena2024`. The message at `:94` is «is accepted by the estimate alone (no word lists)». The comment at `:96` and the KDocs at `:59` and `:83` say the same. The KDoc at `:87-88` points to the amendment. No «pass» / «accepted today» is left about `contraseña123` in the file. The remaining «pass» hits are about other cases (`:126` is `masterCore("pass word-1!")`, `:195` is two words together, `:354` is the screens passing the words). A name with only a comma is a valid backtick name, and the report's global run shows `MasterPolicyTest: 31`. |
| **N1 (b): report Abogado note replaces round 1's «contraseña123 passes»** | **ADDRESSED** | Round 1 note (report `:192`): the «contraseña123 y Contraseña2024 pasan» list is gone, and a sentence says the round-2 note replaces its description of the rule. Round 2 note (report `:361`): self-contained, begins «Esta nota reemplaza a la de la ronda 1». Rule: 10 characters, ≥ 50 bits and not a single word of the app lists, only when creating or changing the master. «contraseña123», «Contraseña2024», «Password1!», «Ábaco12345» are refused. «Barcelona1» and two words together pass. Opening and importing are outside the rule. The policy and the listing must not promise more. The report's `:422` confirms the round-2 note is the current one. |
| **N1 (c): plan and context.md (controller, `74b8f4a`)** | **ADDRESSED** | Plan Review Focus 2 and 3 in the diff (`plans/2026-11-01-clavero-1.3.1.md:177-180`) now say «contraseña123 también se rechaza al crear o cambiar… una copia con esa maestra se sigue importando» and «las frases siempre; una contraseña de 16 caracteres, salvo en menos de 1 de cada 25 000 casos». `context.md:40-41` has the same text. «Menos de 1 de cada 25 000» is true at 16 characters (1 in 61,000). It is not true at 12, but the sentence is about 16. |
| **N2: refusal figures** | **ADDRESSED** | `MasterPolicy.kt:97-100`: «about 1 time in 3,900 at 12 characters, 1 in 61,000 at 16 and 1 in 1.1 million at 20 (the default length)», and «no known word has more than 10: «contrasena», from «Salud»; the lists have 9 at most». `MasterPolicyTest.kt:289-292`: «1 password in 61,000 of 16 characters» (3,900 at 12, 1.1 million at 20) and «about one run in 308». Report Concern 1 (`:332`) and `:253` are corrected, and Concern 1 gives the cause of the old error. I recomputed with the Generator's real algorithm (4 forced positions, 12 free positions that are a letter with probability 52/89, 10,937 cores): 1 in 3,897 at 12, 1 in 61,458 at 16, 1 in 1,128,353 at 20, and 200 draws fail 1 run in 307.8. By length: 214 / 1020 / 1924 / 2253 / 2097 / 1872 / 1556 words of 3 to 9 letters, and one of 10 (`contrasena`). All match. The «few letters (3 to 5)» wording holds: that range is about 99%, 97.5% and 95% of the refusal mass at 12, 16 and 20 characters. No «27,600» or «one run in 140» is left in `app/` or `jvm-check/`. |
| **Out-of-scope item: `inputs.dir("../app/src/main/assets")`** | **ADDRESSED** | `jvm-check/build.gradle.kts:47-50`: `inputs.dir("../app/src/main/assets").withPropertyName("appAssets").withPathSensitivity(PathSensitivity.RELATIVE)`, the same style as the entry for `ui/` at `:43-46`. The directory exists, and the tests read it at `MasterPolicyTest.kt:246`. The report shows the three states (`:387-395`): test runs after the change, `UP-TO-DATE` with no change, and it runs after an `echo "" >>` on `bip39_english.txt`, which is then restored with `git checkout`. The output is a condensed transcription, not a raw log, but the sequence is the right one and I have no doubt about the API. |

**Covering tests and output:** N1 and N2 are text changes with no behaviour to test. The renamed test is covered by the global runs in the report (`:400-403`: 1239 PASSED / 0 FAILED jvm-check, with `MasterPolicyTest: 31`; 1244 tests with 0 failures/errors/skips in app, lint 0 errors). The assets input has its own three-step output. Round 3 adds and removes no tests (counts unchanged from round 2).

## New Breakage in the Fix Diff

None. The diff changes only KDoc, a test name, an assertion message, comments, controller docs and one `inputs.dir`. `MasterPolicy.kt:101-106` (the rule itself) is outside the hunk, and nothing in a string that reaches the user was touched.

## Out-of-Scope Observations

- Trivial: `docs/superpowers/handoff/sdd-1.3.1/progress.md:44` (tracked) still quotes «~1/27,600» as what the round-2 implementer reported. It is an append-only log. The next entries record N2 and its fix, so I would leave it, but whoever reads it alone could copy the old figure.
- Trivial: the report (not in git) keeps round-1 history with the old test name (`:143`, and the run output at `:159`) and `:196` («el test que fija «contraseña123»… como aceptadas», without «por la estimación sola»). They describe what round 1 did, and the notes the final audit and the Abogado Digital read (`:192`, `:332`, `:361`) are right. I would not touch them.
- Two KDoc lines in `MasterPolicyTest.kt` (`:59`, `:83`) are now about 140 characters. Other lines of the file are as long (`:69`), and lint passed.
- Carried over, unchanged: `ui/screens/SecurityToolsScreen.kt:940` reads the EFF list on the main thread inside `remember`. It is already recorded in `progress.md` as a deferred minor for the final review.

## Verdict

**Fix round:** All findings addressed, no new Critical/Important breakage. N1, N2 and the `jvm-check` assets input are closed, and I found no open item for round 3.

## Revisión experto-seguridad-cripto

- No change of rule, format, KDF, secrets, network or permissions. The statement about what the rule catches is now accurate: `contraseña123` is refused with the lists (`MasterPolicy.kt:76-79` and `:105`) and accepted by the estimate alone (`MasterPolicyTest.kt:90-97`). The two limits («Barcelona1», two words together) are still stated (`MasterPolicy.kt:86-93`).
- The corrected refusal rates matter for the audit: a master is refused only in about 1 case in 61,000 for the Generator's 16-character password, so the rule does not push anyone to a weaker choice.
- The Abogado note (`report:361`) no longer says anything false about the code and does not promise more than the rule does.

## Revisión Ingeniero Móvil Senior

- The assets input is relative and path-sensitive, like the `ui/` one. A change in only one list now reruns `MasterPolicyTest` locally, which is the intent. CI starts clean and was not affected.
- No change in UI, lifecycle or concurrency.

## Textos para el Abogado Digital (revisión final)

No new texts. The note to carry forward is the round-2 one (`task-2-report.md:361`), now self-contained and labelled as replacing round 1's.
