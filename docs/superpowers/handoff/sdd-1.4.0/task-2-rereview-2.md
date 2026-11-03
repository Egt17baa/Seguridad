# Task 2 re-review, fix round 2 (8690564..66eedd9)

### Finding Verdicts
- New Breakage (TOOLS_AWAY_WIPE_NOTE overclaimed for the File tab): **Resolved.** `HashLabSession.forget()` now calls `onWipe()` (cancels the file job), `file.clear()` (name, hash, failed, busy reset, run id bumped) and `source = null`. `hasFile` is now `session.source != null`, so «Calcular otra vez» is off after a wipe. The note text is unchanged. Two new tests (`guardWipeAlsoDropsTheFileResultAndTheChosenSource` goes through the real guard; `guardWipeDuringAFileRunRefusesItsLateResult`) plus a source pin in `PlatformSourcesTest`. The report shows the covering run and the full suite/lint/jvm-check as BUILD SUCCESSFUL.

Named risks:
- (a) The hook runs inside `forget()`, called from the guard/`wipe()` on the main thread (viewModelScope / Compose effects / `onCleared`), the same thread that mutates `job` in `pick`/`cancel`, so there is no race. It is not re-entrant: it only cancels and nulls `job`, and does not call back into the session. It cannot cancel a later run's job: the wipe and a later `pick` are serialized on main, and `pick` assigns a new `job` after the wipe. The lambda reads `job` only when called, not during construction, so the declaration order is safe. `onCleared` cancels the job and then calls `wipe()`; the second cancel is a no-op.
- (b) `clear()` bumps `run`, so `named`/`finish` from the cancelled run find a different id and return false. The test asserts this. The cancelled coroutine's `catch (Exception)` path calls `finish(mine, null)`, which is refused too.
- (c) The URI is held as `Any?` in a session cell owned by the same ViewModel, with the same lifetime as the old `lastUri`, and it is nulled on wipe. The hook lambda captures the ViewModel from an object it already owns, so there is no cross-scope leak. The URI is not put in saved state.

### New Breakage in the Fix Diff
None Critical/Important.

### Out-of-Scope Observations
- `readBytes`/`totalBytes` and `fileAlgo` in the ViewModel are not reset by the wipe. They are not secrets and are only shown while a run is active, and the `run == mine` progress check keys off the ViewModel's own `run`, which `clear()` does not bump. The job is cancelled, so at most one last progress write could land. Cosmetic.
- The KDoc line in `HashLabSession` is long (cosmetic).

### Verdict
**Fix round:** All findings addressed, no new Critical/Important breakage
