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
