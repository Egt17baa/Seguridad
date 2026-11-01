# SDD ledger — plan: docs/superpowers/plans/2026-11-01-clavero-1.3.1.md

Spec: docs/superpowers/specs/2026-11-01-clavero-1.3.1-design.md (owner decisions 1-11-2026: keep reading CriptoPanel formats, remove only the name; new master needs estimateBits >= 50).
Branch: ccr-a1a6eabb-765f9g. Plan base: 8d9bd4b (main 9504ce3 + audit reports + design/plan).

## Pre-flight scan

| Rows | Produces / consumes | Finding |
|---|---|---|
| T2 x T3 | both edit ui/screens/VaultScreen.kt (T2 CreateVault ~225-260, T3 UnlockVault ~290-300) | No conflict; sequential, line numbers shift. |
| T2 x T6 | VaultScreen.kt (T6 line 277 text) | No conflict; sequential. |
| T3 x T2 | T3 adds FINGERPRINT_STALE in VaultStore.kt; T2 adds texts in vault/MasterPolicy.kt | Independent. |
| T5 x T6 | T5 texts say «Clavero»; T6 test bans «criptopanel» literals except "criptopanel" and "criptopanel-vault" | Compatible. |
| T6 x T2-T5 | T6 AppNameTest scans every literal | New texts must not contain the name; none planned to. |
| T7 x Global | Global says --offline; T7 step 3 needs network to fetch R8 | Ruling below. |
| T1 self | tests (cache-completed chain refused, system never sees compromised chain, host passed) vs code (refuse before, base, refuse validated; host from handshakeSession) | Agree. |
| T2 self | tests (short/weak/strong, generator outputs pass, source test: master dialog newPassword=true, import not) vs code (newMasterProblem in CreateVault + PasswordDialog(newPassword)) | Agree. |
| T3 self | tests (other vault's DEK -> onWrongKey + FINGERPRINT_STALE; MasterRequired -> no onWrongKey; good DEK opens) vs code (catch CryptoFailure first) | Agree. |
| T4 self | tests (KeyParameter.key identity pinned; zeroed after aead incl. bad tag; vectors unchanged) vs code (finally kp.key.fill(0)) | Agree; helper shape left to implementer. |
| T5 self | tests (unsupported -> null, defaults when absent, "" = SHA1, fixtures supported, RFC 6238) vs code (hmacName nullable, isSupported, no coerceIn) | Agree. |
| T6 self | tests (no visible literal / published doc names CriptoPanel) vs edits (2 UI texts, README) | Agree. |
| T7 self | version bump + optional R8 pin with acceptance gate | Agree. |

- Ruling: T7 step 3 may run Gradle without --offline only to fetch the pinned R8 and write its checksums; every verification after it runs --offline — the plan's step 3 says so explicitly — cost if wrong: none, the gate in step 4 still applies.

## Tasks
Task 1: dispatched (base d228e41, implementer opus)
- Owner decision (1-11-2026, second answer): remove CriptoPanel entirely — Clavero stops reading CP1:, .cpenc and criptopanel-vault; the owner imports any CriptoPanel vault into 1.3.0 before updating. Spec «Decisiones del dueño» and «Fuera CriptoPanel», plan Global Constraints, Review Focus 5 and Task 6 rewritten (commit below). Tasks 1-5 must not touch CriptoPanel code; Task 6 removes it.
Task 1: implementer DONE_WITH_CONCERNS cbf3721 (AppContainer(trustedChainOf) param + SeguridadApp.kt wiring instead of androidTrustedChain inside AppContainer: AppContainer compiles on JVM)
- Ruling: accept Task 1 deviation (trustedChainOf injected through AppContainer's constructor, wired in SeguridadApp.kt, guarded by a PlatformSourcesTest) — AppContainer.kt is compiled by jvm-check and android/ is not, so the brief's literal wiring could not build; same pattern as revokeShare/backupFolder — cost if wrong: a refactor of the wiring only.
- Owner (1-11-2026): no vault in CriptoPanel, so nothing to import before 1.3.1.
Task 1: review Approved, spec ✅, 0 Critical/0 Important, 5 Minor (task-1-review.md): M1 X509TrustManagerExtensions ctor throw would crash instead of fail closed; M2 no test that the returned TrustedChain is used; M3 report overstates 1.3.0 peerHost dependency; M4 old CustomX509TrustManager lint on rewritten line; M5 HttpClientTest compares acceptedIssuers as ordered lists.
- Ruling: Task 1 minors go into a fix round now, not deferred — project rule (CLAUDE.md, owner decision: «Detalles menores: se arreglan todos, no se aplazan») overrides the skill's defer route — cost if wrong: one extra small round.
Task 1: fix round 1/5 (5 addressed, 0 open — M1 fail-closed ctor, M2 assertSame trustedChain, M3 report, M4 @Suppress, M5 sets; commits 4cc8fd2..f0a1b63; task-1-rereview-1.md)
Task 1: complete (commits d228e41..f0a1b63, review clean). Owner phone test mandatory: «Comprobar contraseña» and «Salud» with network.
Task 2: dispatched (base f0a1b63, implementer sonnet)
Task 2: implementer DONE d981e72 (concerns: generator non-default options can produce rejected masters; jvm-check ui source tests up-to-date caveat; estimateBits lowercase copy)
Task 2: review Needs fixes, spec ✅, 0 Critical, 1 Important (plan-mandated: «contraseña123» passes — non-ASCII pool bonus in estimateBits; plan Review Focus 2 example false), 5 Minor (KDoc digits-only wording, wrong test comment, duplicate advice on create screen, jvm-check ui source tests stale, error text not a live region). task-2-review.md
- Ruling: Task 2 Important 1 — keep the owner's rule unchanged (estimateBits >= 50, no dictionary; owner decision 1-11-2026); fix it truthfully: pin «contraseña123»/«Contraseña2024» as accepted in a test, state the non-ASCII bonus in the KDoc, and correct the plan's Review Focus 2 example and context.md (controller edit) — the spec binds the rule, the plan example was wrong — cost if wrong: the owner may want a stricter rule; offered to him as a follow-up (word-list check), not built.
- Ruling: Task 2 minors all go into fix round 1 (project rule), including the jvm-check inputs.dir for ui sources and liveRegion on the new error texts.
Task 2: fix round 1/5 implemented e76565a (Important 1 a/b + minors 2-6) — implementer finished before a container restart; its result message was lost, the commit and report are intact. Re-review of round 1 folded into the round-2 re-review (one scoped re-review over d981e72..round-2 head).
- Owner decision (1-11-2026, after Task 2 review): harden CRY-A01 — also refuse a new master whose core (lowercase, no accents, letters only) is one word of the «Salud» common list or of the three asset word lists (EFF, BIP-39 en/es). Same MASTER_TOO_WEAK text; Salud unchanged. Spec CRY-A01 and plan Task 2 steps 6-8 added; brief regenerated.
- Ruling: Task 2 round 2 = owner amendment + any round-1 gaps, dispatched to a fresh implementer (sonnet; the original cannot be messaged after the restart) with the brief and report file — cost if wrong: one more round.
Task 2: round 2 implementer DONE_WITH_CONCERNS 1be454e (round-1 findings verified fixed; masterCore/masterWords/loadMasterWords, ui MasterWords.kt; concern: 16-char Generator password refused ~1/27,600 when its letters spell a list word — accepted, fixed seed in tests)
Task 2: fix round 2/5 (all 6 round-1 findings + amendment steps 6-8 addressed, 0 Critical/Important; 2 new Minor: N1 stale wording «contraseña123 passes»/«siempre pasa» in plan, context, test name, report Abogado note; N2 refusal rates overstated ~2x; commits d981e72..1be454e; task-2-rereview-1.md)
- Controller fixed N1 in plan Review Focus 2-3 and context.md. Round 3: test rename, report note, N2 wording, plus out-of-scope jvm-check `inputs.dir(assets)` (same build file, one line).
Task 2: deferred minor (pre-existing, final review to triage): SecurityToolsScreen.kt:940 reads the EFF list on the main thread inside remember.
Task 2: round 3 implementer DONE b5c72b1 (N1 test name/report note, N2 rates, jvm-check assets input)
Task 2: fix round 3/5 (N1, N2, jvm-check assets input addressed, 0 open; commits 1be454e..b5c72b1; task-2-rereview-2.md). Note: the «~1/27,600» quoted above is superseded — exact figures 1 in 3,900 (12 chars), 61,000 (16), 1.1 million (20).
Task 2: complete (commits 9567564..b5c72b1, review clean). Abogado note to carry: task-2-report.md «Ronda 2» note (replaces round 1).
Task 3: dispatched (base b5c72b1, implementer sonnet)
Task 3: implementer DONE e435a96 (try around onWrongKey moved into launchUnlockWithKey for JVM testability; fingerprintStale flag hides the button even if disable fails; BOV-3 test now expects FINGERPRINT_STALE)
Task 3: review Approved, spec ✅, 0 Critical/0 Important, 7 Minor (task-3-review.md): M1 report misstates check order for VERSION_UNKNOWN; M2 comment/test motive for a throwing disable inexact; M3 wiring source test doesn't pin order nor `if (bio)`; M4 test names (BOV-3, «version-1»); M5 rotation during the notice loses FINGERPRINT_STALE text; M6 a really-throwing disable re-prompts each lock; M7 disable IPC on main thread (pre-existing).
- Ruling: Task 3 M1-M4 fixed in round 1; M5 and M6 accepted limits (same as BOV-3 error state; disable cannot throw in practice: edit{} apply + runCatching) — documented in KDoc and report, not built; M7 pre-existing, deferred to final review. FINGERPRINT_STALE wording («se cambió… o se importó…» stated as fact though a damaged vault gives the same) goes to the Abogado Digital review — cost if wrong: a text tweak.
Task 3: deferred minor (pre-existing): BiometricVault.disable Keystore IPC on the main thread (VaultScreen.kt:371 and now :320).
Task 3: round 1 implementer DONE 0389a1f (M1-M4 fixed, M5 KDoc, M6 report)
Task 3: fix round 1/5 (M1-M6 addressed; 2 new Minor one-line comments: VaultScreen.kt:308 still names the Keystore, VaultStoreTest.kt:1031 «see the next test» points 6 tests away; commits e435a96..0389a1f; task-3-rereview-1.md)
Task 3: fix round 2/5 (2 comment leftovers addressed, 0 open; commits 0389a1f..25a08cb; task-3-rereview-2.md)
Task 3: complete (commits 1430a1d..25a08cb, review clean). Owner phone test: stale fingerprint → FINGERPRINT_STALE and the button disappears. Abogado: FINGERPRINT_STALE wording (causes stated as fact).
Task 4: dispatched (base 25a08cb, implementer sonnet)
Task 4: implementer DONE_WITH_CONCERNS 490e973 (ChaCha20Poly1305.init keeps its own private key copy — not wipeable without reflection; bad-tag partial plaintext buffer not zeroed on BC paths)
Task 4: review Needs fixes, spec ✅, 0 Critical, 1 Important (false claim: ChaCha20Poly1305 private key copy and engine state CAN be overwritten with the public API — a zero-key re-init in decrypt mode), 5 Minor (bad-tag partial plaintext `out` not zeroed on BC paths; aead→chaChaPoly wiring unobserved; «same bytes» tests tautological and Twofish/Serpent/Camellia-GCM lack a Clavero-dialect known answer — Task 6 will delete the CriptoPanel cascade fixture; residual sentence incomplete; optional dedupe). task-4-review.md
- Ruling: Task 4 round 1 fixes Important 1 with the public-API zero-key re-init (pinned by a test-only reflection read), zeroes `out` on the error paths, adds Clavero-dialect known-answer vectors for Twofish/Serpent/Camellia-GCM and the cascade (protects Task 6), checks the aead ChaCha wiring, and completes the residual sentence. Minor 6 (dedupe blockGcm/chaChaPoly) not done — the ChaCha re-init makes a shared helper awkward; two short bodies — cost if wrong: small duplication.
Task 4: round 1 implementer DONE_WITH_CONCERNS 86b6eec (ChaCha zero-key re-init; runAead zeroes out on failure; fixed vectors for 6 algorithms incl. cascade in the Seguridad dialect; Twofish/Serpent cross-checked only against BC engines — sandbox denied PyPI)
- Ruling: accept the half cross-check of the Twofish/Serpent vectors — they are regression vectors that pin today's bytes (generated on d0702a3, identical on HEAD) before Task 6 deletes the CriptoPanel cascade fixture; BC's engines carry their own KATs — cost if wrong: none for compatibility (bytes are what 1.3.0 writes).
Task 4: fix round 1/5 (Important 1 + Minors 2-5 addressed; 1 new Minor comment: CryptoTest.kt:44-47 says Serpent has its own vectors in SeedCryptoTest — false, round trip only; nits in report/KDoc; commits 490e973..86b6eec; task-4-rereview-1.md)
Task 4: fix round 2/5 (3 comment findings addressed; commits 86b6eec..b208d9d; task-4-rereview-2.md). New nit: CryptoTest.kt:47-48 «for those two only the GCM composition is independent» is inexact for Twofish (raw engine has a published vector).
- Ruling: that one-clause comment nit goes to the final-review fix wave (still fixed before merge), not another round — rounds on comment wording were not converging — cost if wrong: none.
Task 4: complete (commits d0702a3..b208d9d, review clean). Residual-risk sentence for the final audit: task-4-report.md:160.
Task 5: dispatched (base after ledger commit, implementer sonnet)
