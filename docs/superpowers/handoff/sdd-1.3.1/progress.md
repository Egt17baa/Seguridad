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
