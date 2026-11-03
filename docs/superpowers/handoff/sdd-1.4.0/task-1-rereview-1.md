### Finding Verdicts
- **I1** — ADDRESSED. ON_DESTROY observer only records `flow.recreated` (BiometricVault.kt:349-350); the notice is posted from `ended()` via `shouldSayEnableInterrupted(recreated, stored)` (:195 in diff, KeyPlacement.kt:62), after the queue block ended, so `stored` is final. `ensureActive()` before `doFinal` (:458). KDoc updated (interrupted, enable, ended). Test KeyPlacementTest.theInterruptedNoticeOnlyWhenARecreationLeftNothingStored covers 4 cases.
- **M1** — ADDRESSED. `ticket.release()` before `job.cancel()` in the unlock ON_DESTROY observer (:566-571); KDoc of `unlock` updated.
- **M2** — ADDRESSED. KeyOpsQueueTest.anEnableAskedAfterADisableRunsAfterIt (log [disableStart, disableEnd, enable], not cancelled).

Named risks:
(a) No harmful overlap: Ticket.release is idempotent (AtomicBoolean) and compareAndSet(this, null) never frees a newer ticket (SingleRun.kt:16-18). A new unlock started after early release enters KeyOpsQueue, whose Mutex serializes it behind the cancelled block, so no two Cipher.init/prompts run together.
(b) No orphan key: `ensureActive()` throws CancellationException inside the ENABLE block; its `finally` (runEnable) wipes keys and `if (!flow.stored) deleteKey()` inside the queue; prefs are written only after doFinal, so none exist either.
(c) Notice preserved: recreated=true, stored=false -> `ended()` posts FINGERPRINT_ENABLE_INTERRUPTED (cancellation path still reaches `ended` through the finally in runEnable). Suppressed only when stored.

### New Breakage in the Fix Diff
None.

### Out-of-Scope Observations
None. (Residual, already in report: a late answer of the old prompt after early ticket release is ignored by suspendCancellableCoroutine; needs on-device test, as does rotating right after accepting the fingerprint.)

### Revisión experto-seguridad-cripto
Veracity of the M-4 message now tied to final state (BiometricVault.kt:195/349, KeyPlacement.kt:62). No key/prefs inconsistency (finally with deleteKey, :373-374 area). No new crypto, deps, permissions or logs.

### Revisión Ingeniero Móvil Senior
Main-thread-only fields (recreated, gone, prompt) read in observer and ended(); @Volatile stored/done across threads. Lifecycle and queue serialization correct; ticket release in ON_DESTROY lets the new activity's auto start queue instead of being dropped.

### Verdict
**Fix round:** All findings addressed, no new Critical/Important breakage.
