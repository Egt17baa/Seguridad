# Task 3 re-review, round 2 (fix diff `0389a1f..25a08cb`)

Scope: the two leftovers in the «Verdict» of `task-3-rereview-1.md`, checked against the fix diff `review-0389a1f..25a08cb.diff` and the current working tree. Read-only: no git commands, no edits, no subagents, no test runs.

### Finding Verdicts (ADDRESSED | NOT ADDRESSED, file:line)

1. **VaultScreen.kt comment still claims the Keystore refused: ADDRESSED.** `app/src/main/java/app/seguridad/ui/screens/VaultScreen.kt:308` now reads `// A key that opened nothing (CRY-A02): the button is gone for this screen even if turning it off ever threw.` This is the defensive wording the finding asked for. A search for "Keystore" finds nothing in `VaultScreen.kt`, `VaultStore.kt` or `VaultStoreTest.kt`.

2. **VaultStoreTest.kt "(see the next test)" pointer: ADDRESSED.** `app/src/test/java/app/seguridad/vault/VaultStoreTest.kt:1031` now reads `(see «a version-3 vault asks for it before trying the key», below)`. The quoted text is the leading part of the test at `app/src/test/java/app/seguridad/vault/VaultStoreTest.kt:1094`: ``CRY-A02 with the master password due, a version-3 vault asks for it before trying the key, so another vault's key keeps the fingerprint for now``. The quoted phrase occurs only at lines 1031 and 1094, so the pointer resolves to the version-3 test. The text "next test" no longer appears in the file.

### New Breakage in the Fix Diff

None. The diff changes two comment lines only (`VaultScreen.kt:308` and `VaultStoreTest.kt:1031`). No executable line changed.
- `app/src/test/java/app/seguridad/tools/SourceTextsTest.kt:16` says comments are never scanned, so the new comment at `VaultScreen.kt:308` is not checked for forbidden words.
- No line-length rule is configured. There is no `.editorconfig`, and no detekt, ktlint or lint config sets a maximum. The test file already has 54 lines over 120 characters and `VaultScreen.kt` has 33, so the longer pointer line at `VaultStoreTest.kt:1031` breaks nothing.

### Verdict — **Fix round:** All findings addressed
