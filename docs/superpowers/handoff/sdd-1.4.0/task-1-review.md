### Spec Compliance
- ❌ One issue found. Everything else matches the brief and the spec section «`BiometricVault` en un solo hilo».
  - **Misunderstood/regressed:** the brief says the enable flow "keeps its behaviour". M-4 ("Nothing was stored", `BiometricVault.kt:271`) no longer holds when the activity is re-created after the fingerprint has been accepted but before `doFinal` and the prefs write finish. `doFinal` now runs on the queue thread, so that window is open to `ON_DESTROY`. See I1.
  - Requirements met, with evidence:
    - `KeyOpsQueue(dispatcher)` exposes `run(kind, block)` and `cancelEnable()` (`KeyOpsQueue.kt:33,49`).
    - A `Mutex` covers the whole operation, and the block runs on the dispatcher given (`KeyOpsQueue.kt:41`).
    - DISABLE calls `cancelEnable()` before it takes the lock (`KeyOpsQueue.kt:34`).
    - The single-thread executor dispatcher is at `BiometricVault.kt:180`.
    - The ENABLE operation covers `clear`, key generation and `Cipher.init`, the prompt on Main, and `doFinal` plus the prefs (`BiometricVault.kt:364-384`, `:426`, `:448-459`).
    - DISABLE is `suspend` and `NonCancellable` (`BiometricVault.kt:163-166`).
    - The UNLOCK operation covers the prefs read, `Cipher.init`, the prompt and `doFinal` (`unlockSteps`).
    - `enabled()` and `keyLevel()` are unchanged.
    - A cancelled enable is wiped in the block's `finally` and again in `ended()` (`:373-374`, `:497`). It never marks StrongBox through `AUTH_OR_FINAL`: `ended` calls `note(FAILED)`, and `shouldMarkStrongBoxFailed(AUTH_OR_FINAL, FAILED)` is false (`KeyPlacement.kt:52-55`).
  - The six callers all await `disable`:
    - `VaultScreen.kt:170-173` (menu, now in `scope.launch`, R1);
    - `:283` (before `store.create`);
    - `:323-326` (through `onWrongKey: suspend`, `VaultStore.kt:105`), awaited before `onError(FINGERPRINT_STALE)` (`VaultStore.kt:113-118`);
    - `:380-384` (before `store.destroy()`);
    - `VaultBackup.kt:131` and `:175`, after saving, inside `NonCancellable` and unchanged.
  - The CRY-A02 order is respected at every call site.
  - **Justified extra:** the `onWrongKey` signature change in `VaultStore.kt`. It is the smallest way to apply R1 at `:323`, whose only caller is `VaultScreen.kt:323`.
- ⚠️ Cannot verify from the diff:
  - **The spec's "Tests de orden" at the `BiometricVault` level** ("no queda una clave sin preferencias ni unas preferencias sin clave" after each case). They are only covered abstractly in `KeyOpsQueueTest`. The real invariant depends on Keystore and BiometricPrompt and needs the phone tests: enable then disable right away; disable during key generation and during the prompt; rotate at each step (M-4); unlock while disabling.
  - **Concern 1 (late answer after rotation is now ignored):** this depends on how `androidx.biometric` 1.1.0 hands over the ViewModel callback. Rotate with the unlock prompt shown, on the phone.
  - **A prompt that never calls back while the activity stays alive.** Before, it only held the unlock guard. Now it also holds the queue, so every DISABLE waits behind it: «Borrar bóveda», «Crear», the master change in `NonCancellable` (whose `pw.fill` waits), and import. I found no concrete library path that does this: `canPrompt` and `authenticate` run in the same Main slice (`:426`, `:598`), and the queue stops two `BiometricPrompt`s from replacing each other's callback. It is a phone check: background the app with each prompt shown, return, then use «Borrar bóveda» or «Activar huella».

### Strengths
- **The queue is small and correct.**
  - Each `run` gets its own `coroutineScope`, so `cancelEnable` cancels only the operation and the caller stays active to clean up (`KeyOpsQueue.kt:36-45`).
  - ENABLEs are registered before the lock, so a DISABLE also drops one that is still waiting.
  - `withContext` inside `withLock` holds the lock until the cancelled block's `finally` has run (`:41`).
  - A fair `Mutex` means an ENABLE asked after a DISABLE runs after it.
- **There is no re-entrancy deadlock.**
  - Inside operations, the private `clear()` replaces `disable` (`:369`, `:578`, `:592`).
  - `onKey`, `onError` and `onDone` run after the operation has left the queue (`:540-546`, `:494-503`), so `onWrongKey` → `disable` cannot wait on itself.
- **`CoroutineStart.UNDISPATCHED`** (`:360`, `:521`) makes the enable's registration synchronous and the unlock observer race-free. `job.isActive` is checked before the observer is added (`:548`).
- **BOV-3 still holds.** A key released by `doFinal` is handed over even when the outer job was cancelled after it (`:526-543`). It is zeroed on an `Error` (`:530-533`).
- **The tests catch real defects.** `operationsNeverOverlap` fails without the `Mutex`. `aCancelledEnableFinishesItsCleanupBeforeTheDisableRuns` fails if the lock is released before `finally`. `cancelledEnableReleasesTheQueue` checks that no virtual time passes. The `VaultStoreTest` addition shows that `onWrongKey` is awaited before the text (`VaultStoreTest.kt:1120-1133`).
- **Claims checked against the code:**
  - The 4 lint warnings are pre-existing. `app/build/reports/lint-results-debug.xml` (15:01, before the commit at 15:03) has GradleDependency ×2 in `libs.versions.toml`, DiscouragedApi in `AndroidManifest.xml` and ObsoleteSdkInt in `mipmap-anydpi-v26`. These are the same warnings recorded in 1.3.1 (`docs/superpowers/handoff/sdd-1.3.1/last-wave-report.md:54`), less NewerVersionAvailable, which needs the network and was not run offline.
  - The commit's author and trailer are correct (`git log -1 d59c743`).

### Issues
#### Critical (Must Fix)
None.

#### Important (Should Fix)
**I1. M-4 can say «se interrumpió… Vuelve a tocar «Activar huella»» when the fingerprint was in fact stored.** (`BiometricVault.kt:339-344` with `:448-459`)

- **What's wrong:** the observer decides "interrupted" from `!flow.done` on Main. `done` is now set by `decide()` on the queue thread, after `doFinal`, which can take hundreds of ms on StrongBox, and after the prefs write. Take a rotation after the fingerprint has been accepted but before `decide(stored = true)`:
  1. The observer posts `FINGERPRINT_ENABLE_INTERRUPTED` and cancels the job.
  2. `doFinal` and `prefs.edit` have no suspension point, so they still complete.
  3. `decide` stores `true`, because the observer never sets `done`.
  4. The key and the prefs stay, so the fingerprint is on.

  The new screen then tells the user it was interrupted and asks them to tap «Activar huella», while the menu shows «Desactivar huella». In 1.3.1 `doFinal`, the prefs write and `end()` all ran in the Main callback, which is atomic with respect to `ON_DESTROY`, so this could not happen.
- **Why it matters:**
  - It is a regression caused by this task's threading change.
  - It breaks the documented M-4 contract (`:271`, "Nothing was stored").
  - It breaks the project's rule that a text must not state something false (the reason O-1 was mandatory).
  - The state itself is consistent (key and prefs together), so this is not a security hole.
- **How to fix:**
  1. In the observer, record only `flow.recreated = !flow.done && activity.isChangingConfigurations`. Keep closing the prompt on that condition.
  2. Post the notice from `ended()`, which runs on Main once the operation has left the queue and `stored` is final: `if (flow.recreated && !flow.stored) interruption.post(FINGERPRINT_ENABLE_INTERRUPTED)`.
  3. Optionally, also check `ensureActive()` before `doFinal`, so that a cancel that has already arrived does not store anything.
  4. Put the decision in a pure helper in `tools/`, with a JVM test for its cases (recreated with nothing stored, recreated after storing, not recreated), as `shouldMarkStrongBoxFailed` already is.
  5. Update the KDoc at `:271`.

#### Minor (Nice to Have)
**M1. After a rotation with the unlock prompt shown, the new screen's automatic start can be refused silently.** (`BiometricVault.kt:518`, `:536`, `:549`)

- **What's wrong:** in 1.3.1, `ON_DESTROY` with the prompt shown freed the `SingleRun` ticket synchronously. Now `ON_DESTROY` only calls `job.cancel()`. The ticket is freed in `finally` after two Main dispatches and one queue-thread hop. The new activity's `LaunchedEffect(Unit) { if (bio) withFingerprint() }` (`VaultScreen.kt:330`) runs in its first frame, and if that frame comes before the unwinding finishes, `unlocking.start()` returns null and nothing is shown. If the old system prompt is still on screen, its answer reaches the cancelled continuation and is ignored. The user then has to tap «Usar huella». This is not a security problem.
- **How to fix:** call `ticket.release()` in the `ON_DESTROY` observer as well as `job.cancel()`. `Ticket.release` is idempotent (`SingleRun.kt:16-18`). The guard used to be held across a rotation only to stop two `Cipher.init` calls on StrongBox at once, and the queue now prevents that: the new unlock simply waits behind the cancelled one, which ends after its current Keystore step. Update the KDoc of `unlock` (`:509-516`) to match.

**M2. No test for "last intent wins" in the other direction.** (`KeyOpsQueueTest.kt`)

- **What's wrong:** no test shows that an ENABLE asked *after* a DISABLE is neither cancelled by it nor run before it. This holds today because `cancelEnable` only sees ENABLEs that are already registered (`KeyOpsQueue.kt:34`) and the `Mutex` is fair. A later refactor that moves registration or `cancelEnable` could break it without any test failing.
- **How to fix:** add `anEnableAskedAfterADisableRunsAfterIt`: start the DISABLE with `delay(10)`, launch the ENABLE, and assert the log is `[disableStart, disableEnd, enable]` and that the ENABLE was not cancelled.

### Revisión experto-seguridad-cripto
- **Threat model:** the danger is operations interleaving on the single alias `seguridad_vault_biometric`: an orphan key, prefs without a key, a fingerprint that outlives a master change (CRY-A02), or a false «StrongBox falló».
  - The `Mutex` spans each whole operation (`KeyOpsQueue.kt:41`).
  - Enable is a single operation (`BiometricVault.kt:364-384`).
  - DISABLE cancels ENABLEs that are running or waiting (`KeyOpsQueue.kt:34`, `:49-52`), and the cleanup runs inside the queue (`BiometricVault.kt:373-374`).
- **Ordering of writes:**
  - Enable writes the prefs last, in one `edit` after `doFinal` (`:448-456`), and removes the key if it did not store (`:374`).
  - `clear` removes the prefs first and the key after (`:169-176`).
  - So the plain reads `enabled()` and `keyLevel()` (`SettingsScreen.kt:375-382`) see the state before or after an operation, never "enabled with no key". The worst case is a transient `KeyLevel.UNKNOWN`, which is cosmetic.
- **CRY-A02:** `disable` is never skipped (`NonCancellable`, `:165`) and is awaited:
  - before create (`VaultScreen.kt:283`) and before destroy (`:380-384`);
  - after saving on master change and import (`VaultBackup.kt:131`, `:175`);
  - before `FINGERPRINT_STALE` (`VaultStore.kt:113-118`).

  An `onWrongKey` that is cancelled or throws still shows the text (`catch (off: Exception)`, `VaultStore.kt:115`).
- **Key material:**
  - `dataKey` and `spare` are zeroed in the operation's `finally` (`:373`), again in `ended` (`:497`), and on the early guard return (`enable`, `fill(0)`).
  - The unlock key is handed over through `handOverKey` (`:543`, `KeyPlacement.kt:139-147`) or zeroed on an `Error` (`:530-533`).
  - Nothing secret is left unzeroed. `wrapped`, the IV and the `Cipher` objects hold no key material.
- **CryptoObject contract unchanged:** `Cipher.init` comes before `authenticate`, and `doFinal` runs on the cipher the prompt returns (`:426`, `:448`, unlock `doFinal`). Running `doFinal` on another thread than the prompt is harmless: 1.3.1 already ran `init` on `Dispatchers.Default` and `doFinal` on Main.
- **StrongBox marking:** a cancelled enable ends with `note(FAILED)` (`:496`). That never marks for `AUTH_OR_FINAL` (`KeyPlacement.kt:54`). A `KEY_SETUP` refusal is marked at `PENDING` on evidence, as before (`:402`).
- **Logs, dependencies and formats:** there is no `Log`, `println` or `printStackTrace` in the diff. No new dependency, permission, host or format. The alias and prefs names are unchanged.
- **Finding:** I1 is a veracity issue, not a confidentiality one: the state stays consistent.

### Revisión Ingeniero Móvil Senior
- **Threads:**
  - The Keystore work runs on the queue's single thread: key generation and init in `attempt`, `doFinal` and the prefs, unlock `init` and `doFinal`, and `clear`.
  - The UI and the prompts run on `Dispatchers.Main` (`:426`, `:433`, `promptGone`, unlock `withContext(Main)`).
  - `disable` suspends without blocking Main (`:165`).
  - `@Volatile` fields hand `decide` over to the observer (`EnableFlow`). The other fields are handed off through `withContext` resumption, which gives happens-before.
- **Lifecycle:**
  - `ON_DESTROY` cancels enable (`:344`) and unlock (`:549`).
  - `askFingerprint` ignores late answers (`answer.isActive`) and posts `cancelAuthentication` only to a live activity (`:249-259`).
  - Race windows opened by the threading change: I1 (M-4 text) and M1 (the unlock guard held across a rotation). Both come from work that used to be atomic on Main and is no longer.
- **Compose:**
  - Both callback callers now launch on the composition's `rememberCoroutineScope`, and their dependants run after the await (`VaultScreen.kt:170-173`, `:380-384`). R1 is respected.
  - No `rememberSaveable` or `Bundle` was added.
  - Double taps are guarded by `menu = false`, `confirmDestroy = false` and `busy = true`.
- **Lifecycle edge, concern 4:** if the screen dies between `disable` and `store.destroy()`, the vault is not deleted but the fingerprint is off. That is the safe direction, and it was already possible when `destroy` was cancelled.
- **Resources:** the executor thread lives for the whole process, which is documented (`:178-180`). `disable` uses `applicationContext`, so it does not leak the activity (`:164`).
- **Build:** no new lint warning (lint report, 15:01).

### Assessment
**Task quality:** Needs fixes

**Reasoning:** The queue, the six awaited callers and the CRY-A02 ordering are correct and well tested. One fix is needed before this can be trusted: moving `doFinal` off Main opened a rotation window in which M-4 tells the user the fingerprint was not enabled when it was (I1). M1 and M2 are small follow-ups.
