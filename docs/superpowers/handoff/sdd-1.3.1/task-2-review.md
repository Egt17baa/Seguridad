# Task 2 review (CRY-A01), `9567564..d981e72`

### Spec Compliance

✅ **Matches the brief and the owner decision.** Checked against the diff, plus targeted reads outside it for the named risks.

- ✅ `vault/MasterPolicy.kt`: `MIN_MASTER = 10` (moved out of `ui/screens/VaultBackup.kt`), `MASTER_TOO_SHORT` (value pinned to «Mínimo 10 caracteres»), `MASTER_TOO_WEAK` (word for word the text of point 1 in `04-cripto-parteA.md`), and `newMasterProblem` (length first, then `estimateBits < WEAK_BITS`, else `null`). `MasterPolicy.kt:31-35`.
- ✅ Threshold is the one of «Salud»: same function and constant (`Health.kt:13,42,82`).
- ✅ **No path that sets a NEW master skips the rule.** `store.create` has one UI call site, `VaultScreen.kt:268`, behind the button at `:259` (`problem == null`). `store.changeMaster` has one, `VaultBackup.kt:124`, inside the `"master"` dialog, which passes `newPassword = true` (`:114`) and gates on `problem == null` (`:228`). `VaultCrypto.create` is reached only from `VaultStore.kt:354` and `VaultCrypto.changeMaster` only from `VaultStore.kt:661`. `PasswordDialog(` has exactly two callers (`VaultBackup.kt:109,154`).
- ✅ **Import and unlock did not get stricter.** The `"import"` dialog does not pass `newPassword` (`VaultBackup.kt:154-160`), so `problem` is `null` and `ok` is still `a.length >= MIN_MASTER && (!askTwice || a == b)` (`:227-228`). Unlock and `importReplace` are not in the diff. No other file used `MIN_MASTER` (grep over `app/src`).
- ✅ **The message shows while typing, in both screens.** `CreateVault`: `if (a.isNotEmpty()) problem?.let { Text(it, color = NegativeColor) }` (`VaultScreen.kt:245-246`); `PasswordDialog`: the same (`VaultBackup.kt:238`). The button also waits for `null` (`VaultScreen.kt:259`, `VaultBackup.kt:228`).
- ✅ Label «Contraseña maestra (mínimo $MIN_MASTER caracteres)» unchanged (`VaultScreen.kt:243`). `VaultStore` and `Health.kt` untouched. No format, permission, dependency, network or component change.
- ✅ **Texts:** both are `const val`s (`MasterPolicy.kt:10,13`), contain no forbidden word (checked against `ForbiddenWords.kt`), and `SourceTextsTest` scans all of `app/src/main/java`. Both are listed for the Abogado Digital.
- ✅ **Generator coverage meets and exceeds the brief.** 200 five-word phrases per list (EFF, BIP-39 es, BIP-39 en) × 4 separators (`MasterPolicyTest.kt:82`), capitals/number variants (`:95`), and 200+200 passwords of 16 characters (`:108`). The four separators match `SecurityToolsScreen.kt:975`. Note the in-app Generator only loads the EFF list (`SecurityToolsScreen.kt:939`), so the Spanish list is extra coverage, not a screen path.
- ✅ Source-text test covers every item of the brief (`MasterPolicyTest.kt:145-170`).
- ⚠️ Not visible from the diff: that the red text really renders while typing, the layout of the long `MASTER_TOO_WEAK` in the dialog, and TalkBack. Only the owner's phone test shows that; the JVM tests only pin the wiring as source text. I did not re-run the suite (as instructed); the counts 1227/1222 are the implementer's claim.
- ⚠️ My strength figures below come from a line-by-line Python transliteration of `Health.kt:82-122` (not from running the Kotlin). It reproduces the report's own «Barcelona1» figure (59.5 bits, inside the test's 50-65 bounds), so I trust it, but a one-line focused test would confirm it (see Important 1).

### Strengths

- The rule is a pure function in `vault/` with no Android types, so `jvm-check` tests it (`MasterPolicy.kt:31`). `MIN_MASTER` has one definition and the screens import it.
- `PasswordDialog` gains an explicit `newPassword: Boolean = false` (`VaultBackup.kt:219`): no caller changes by accident and the import dialog cannot be hardened by omission. This is exactly the design in the spec.
- The source-text test is solid: it strips comment lines (`MasterPolicyTest.kt:134`) so a comment cannot satisfy it, scopes the `newPassword` check to each dialog's own argument list (`:139-143`), and counts `length >= MIN_MASTER` inside and outside `PasswordDialog` (`:157-165`). The implementer mutation-tested it both ways.
- Good TDD evidence (compile-RED, then 4 behavioural RED, then GREEN), and the KDoc says up front that the estimate has no dictionary and pins «Barcelona1» as the accepted limit (`MasterPolicy.kt:25-29`, test `:57`).
- Honest Concerns section (Generator with digits-only, stale `jvm-check` up-to-date check, transient lowercase copy).

### Issues

#### Critical
None.

#### Important

**1. (Plan-mandated) The rule's real limit is much wider than the documented one, and a plan example is false. `contraseña123` passes.**
- Where: `Health.kt:83,89` (via `MasterPolicy.kt:33`); claims at `MasterPolicy.kt:25-29`, `MasterPolicyTest.kt:45-49`, and `context.md` point 2 («contraseña123» "no pasaría la regla nueva").
- What: `estimateBits` adds a 100-symbol pool for any non-ASCII character (`Health.kt:89`), so any Spanish word with `ñ` or an accent scores ~7 bits per character. The rejected examples in the test («contraseña», «Contraseña», «CONTRASEÑA») are rejected only because the exact word is in `COMMON` (`Health.kt:83`), not because of strength. With the same arithmetic as Kotlin (transliterated):
  - «contraseña1» 78.0 bits, **accepted**
  - «contraseña123» 78.0 bits, **accepted** (the plan says it would be refused)
  - «contraseñas» 76.8 bits, **accepted**
  - «Contraseña2024» 102.8 bits, **accepted**
  - «canción123» 56.7, «Password1!» 65.7, «Passw0rd!!» 65.7, «iloveyou12» 51.7, **accepted**
  - even ASCII «contrasenas» (11 lowercase letters) is 51.7, accepted
  - refused: «password123» 46.5, «qwerty12345», «letmein1234», all-same/sequence strings
- Why it matters: the CRY-A01 audit text starts with «contraseña». The user will see that exact word refused and may assume the rule is much stricter than it is. The KDoc says «removes the obvious cases» and names only the four that pass by `COMMON` or run-collapse. The final audit and the Abogado Digital revision rely on what the fix really does; the plan's own example of "a master the new rule refuses" is wrong. The behaviour matches the owner decision (`estimateBits >= 50`, no dictionary), so I am not asking for a different rule, only for the limit to be stated and pinned truthfully.
- How (no behaviour change needed): (a) add a test next to «Barcelona1» that pins «contraseña123» and «Contraseña2024» as accepted, with a comment that the non-ASCII bonus makes accented words look strong (the next person who changes `estimateBits` will see the effect on the master rule); (b) add one sentence to the KDoc («a word with ñ or an accent gets a large bonus: `contraseña123` passes»); (c) controller: correct `context.md` point 2 and the "obvious cases" wording in the notes for the Abogado Digital (the policy text must not promise that weak or common passwords are refused). Optional, owner/controller decision, not required here: fold accents (NFD + strip combining marks) inside `newMasterProblem` only. That lowers «contraseñas» to 51.7 (still accepted) and «contraseña123» to ~57, so it does not change the picture; the only real levers are a higher threshold or a dictionary, which the spec excludes.

#### Minor

**2. KDoc understates the digits-only case and says «always».** `MasterPolicy.kt:27-29`. The implementer's own measurement (report «Concerns» 1) is 662/2000 digit-only passwords of 16 refused, 44/2000 at 20, 2/2000 at 24, so «needs more than 16 characters to reach the threshold» is wrong in the direction that hides a refused Generator output. Reword to «a password of digits only is sometimes refused up to about 24 characters». The «always» for 5-word phrases is fine (worst case is far above 50 bits), but it rests on sampling for the 16-character password; say «in the tests» rather than «always».

**3. Wrong comment in the test.** `MasterPolicyTest.kt:114`: «Without symbols, and only letters», but `PasswordOptions(length = 16, symbols = false)` still has `digits = true`. Either fix the comment or pass `digits = false` too (that would add the real "only letters" case).

**4. Same advice shown twice on «Crea tu bóveda».** `VaultScreen.kt:246` (`MASTER_TOO_WEAK`, which ends «Usa una frase de 5 o 6 palabras…») and the fixed hint at `:250` («Consejo: usa una frase de 5 o 6 palabras…») are on screen together. Text is from the audit and the Abogado Digital will review it; cheap options: leave it and list it in the Abogado Digital note, or hide the hint while the error shows.

**5. Source-text tests can go stale in `jvm-check`.** The implementer's Concern 2 is real: `ui/**` is excluded from the `jvm-check` sources (`jvm-check/build.gradle.kts` `androidOnly`), so Gradle does not rerun `test` when only `ui/` files change. Suggest declaring the `ui/screens` directory as an `inputs.dir` of `tasks.test` there (also fixes `PlatformSourcesTest`/`DocumentUriTest`). Not for this task if the controller prefers to keep build files out of it; `cleanTest` as the report notes is the workaround. The test also never checks that a third caller of `store.create(`/`changeMaster(` in `ui/` goes through `newMasterProblem`; I verified by grep that there is none today.

**6. Accessibility.** The error is a plain `Text` after the field (`VaultScreen.kt:246`, `VaultBackup.kt:238`), not a live region and not tied to the field's error state, same as the existing «No coinciden». It is consistent with the app, and the report defers it to the pending TalkBack test; I note it so it is not lost.

### Assessment

**Task quality: Needs fixes** (small, test/KDoc/notes only; no behaviour change).

**Reasoning:** The implementation is correct and complete against the brief: every new-master path is gated, import and unlock are untouched, the message shows while typing in both screens, and the tests cover the Generator and the wiring well. What is left is Important 1: the accepted limit is much wider than stated (`contraseña123` passes, contrary to `context.md`), so the KDoc, the pinned test and the notes for the final audit should say so before they are relied on.

### Revisión experto-seguridad-cripto

- **Threat covered:** an attacker with a `.sgbak` and unlimited offline guesses (`auditoria/04-cripto-parteA.md`, CRY-A01). The gate rejects below 50 bits, the same threshold as «Débil» in Salud (`MasterPolicy.kt:33`, `Health.kt:13,42`). It removes only the obvious passwords (see Important 1 for what still passes); it is a floor, not a strength guarantee, and the KDoc says so (`MasterPolicy.kt:25-29`), except for the non-ASCII hole.
- **Only new passwords:** `create` `VaultScreen.kt:268`; `changeMaster` `VaultBackup.kt:124`; unlock, fingerprint and `importReplace` (`VaultStore.kt:717,744`, called at `VaultBackup.kt:170`) are not gated, so every existing vault/copy still opens. No `VaultStore`, `Vault.kt`, `Crypto.kt` or format change in the diff; no old test data touched.
- **Secrets in memory:** no `rememberSaveable`, `Bundle` or log added; `pw.fill('\u0000')` unchanged (`VaultScreen.kt:272`, `VaultBackup.kt:147`). Residual: `estimateBits` makes a `lowercase` copy of the typed `String` on every recomposition (`Health.kt:83`, called from `VaultScreen.kt:245` and `VaultBackup.kt:227`). The text is already an immutable `String` in Compose state, so the copy adds no new class of exposure; same residual as CRY-A03, and the report declares it.
- **Estimate limits:** run collapse and the common-list zeroing work for the listed cases (`Health.kt:83,106-121`); the non-ASCII pool bonus (`Health.kt:89`) is the unlisted gap (Important 1). Spanish accented words are the app's main audience.
- **Generator output passes:** 5-word phrases, any separator, three lists (`MasterPolicyTest.kt:82`), capital/number variants (`:95`), 16-character passwords (`:108`). Digit-only and lowercase-only outputs can be refused (report «Concerns» 1); the refusal text points to a phrase, which is the right remedy.
- **Network/permissions/exports:** none added; `FLAG_SECURE` and `FilterObscuredTouches` untouched (`VaultBackup.kt:104`).
- **Texts for the Abogado Digital:** the policy and Play text must not say the app «checks that the master is strong»; the rule is «10 characters and a rough estimate of at least 50 bits, only when creating the vault or changing the master». The report's note 1 already asks for this check; add the limit from Important 1.

### Revisión Ingeniero Móvil Senior

- **Architecture:** the policy is a pure Kotlin function in `vault/` (`MasterPolicy.kt:31`), tested on the JVM; the UI only calls it. `MIN_MASTER` moved with both screens importing it (`VaultBackup.kt:33`, `VaultScreen.kt:77`). `vault` already depends on `health` elsewhere (`Contacts.kt` imports it), so no new layering problem.
- **Compose:** `PasswordDialog` keeps its state inward (`var a/b by remember`) and takes an explicit flag with a safe default (`VaultBackup.kt:219`). `problem` is derived in the body on each recomposition (`:227`, `VaultScreen.kt:245`), with no stored derived state, so it cannot go stale; the cost is one linear pass per keystroke, fine here. The `ok` expression (`VaultBackup.kt:228`) mixes two notions (`problem == null` vs length), which is the price of keeping the import untouched; acceptable and tested. `PasswordDialog` has no KDoc for the new flag; the in-code comment at `:225-226` carries it.
- **Lifecycle/rotation:** nothing new in `rememberSaveable`; the text stays in `remember` as before.
- **UX/accessibility:** red text shown from the first character («Mínimo 10 caracteres» for 1-9 characters, then the weak text), matching the spec. Duplicate advice on the create screen (Minor 4). No live region (Minor 6).
- **Tests:** behaviour tests run on the JVM; UI wiring is checked as source text because `ui/` is not compiled in `jvm-check` (`jvm-check/build.gradle.kts`). The staleness caveat is in Minor 5. Tests use `SecureRandom` with 2,400+ draws; the failure message prints the phrase, and the worst case is far above 50 bits, so I see no flakiness risk.
- **Build/lint:** unused-import risk checked: `MIN_MASTER` is used at `VaultBackup.kt:228` and `VaultScreen.kt:243`, `newMasterProblem` at `VaultBackup.kt:227` and `VaultScreen.kt:245`.
