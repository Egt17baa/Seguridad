# Task 11 re-review 1 (fix commit 6212a47, documents only)

**Verdict:** fix round 1/5 (4 addressed, 0 open; no new issues; one informative note on plan lines from Tasks 1 and 2, which are history).

Scope: I-1, M-1, M-2 and M-3 of `task-11-review.md`, checked against my evidence in the scratchpad `rv/`. Diff: `review-t11-fix.diff` (`82615ac..6212a47`).

### I-1: the documented jvm-check command, ✅ addressed

- `CLAUDE.md:129` is now `./gradlew -p jvm-check test --no-daemon --max-workers=1`, with the comment that the wrapper brings Gradle 9.6 and that an installed Gradle 8 fails dependency verification.
- The plan, `2026-11-03-clavero-1.4.0.md:42` («Verification after every task»), uses `./gradlew -p jvm-check test`, citing R29.
- Ruling R29 (`progress.md`) says to use the wrapper from Task 11 on and never to add the `-gradle813` sums back.
- **The documented command works:** `./gradlew -p jvm-check cleanTest test --no-daemon --max-workers=2` (8-11-2026, `rv/run/rereview-jvm2.txt`) gave BUILD SUCCESSFUL in 1m 8s, with **1746 tests and 0 failures**, and no «Dependency verification failed».
- **Other live documents:**
  - `README.md:90-92` gives `./gradlew -p jvm-check test` first. The installed-`gradle` line is conditional on «con un Gradle 9.6.x instalado», which is correct.
  - `docs/PUBLICAR.md:171` already used `./gradlew -p jvm-check test`.
  - `.github/pull_request_template.md:7` already used `./gradlew -p jvm-check test`.
  - There is no 1.4.0 handoff document yet. Task 12's section of the plan has no Gradle command.
  - Nothing live still tells people to run an installed `gradle -p jvm-check`.
- **Informative:** the plan's `:118` (Task 1) and `:166` (Task 2) still say `gradle -p jvm-check test --tests …`. Those are the RED steps of tasks finished before Task 11. The same holds for the reports of Tasks 1 to 10, which record what was run at the time. R29's «from here» covers them, so this is history and needs no change. If the plan is ever reused as a template, change those two lines then.

### M-1: licence counts, ✅ addressed

`task-11-report.md:91-94` now reads 72 Apache-2.0, 5 BSD-3-Clause, 4 Bouncy Castle Licence and 5 EPL-2.0, which adds up to 86. It names `guava-parent` (Apache-2.0) as missing from `t11/licences-app.txt`. This matches my count from the component diff (`rv/vm/`).

### M-2: renamed Kotlin plugin jars, ✅ addressed

`task-11-report.md:76` now says three in jvm-check and four in the app file. It names compose-compiler, fus-statistics, kotlin-gradle-plugin-api and kotlin-serialization, with `kotlin-gradle-plugin-2.4.21-gradle96.jar` as the only different file. This matches the `SAME-SHA-AS-OLD` output of `rv/scripts/vmd.py`.

### M-3: what strict mode removes, ✅ addressed

`task-11-report.md:158` and `:207` now list the same four no-argument constructors as my comparison of the opt-out APK with the gate APK (`rv/scripts/noarg.py` on `rv/cmp/dexdump-opt.txt` and `dexdump-gate-out.txt`): `IconCompat`, `RemoteActionCompat`, `CustomVersionedParcelable` and `MinimumInteractiveModifier`. They also give the reason it is harmless: the constructors are inlined where the objects are created, the reflection goes through the kept `Parcelizer.read`, and Clavero parcels none of these classes. That is consistent with the `IconCompatParcelizer.read` bytecode and the versionedparcelable rule at `configuration.txt:943`.

### Revisión experto-seguridad-cripto

- The fix is documentation only: no file under `app/`, `gradle/`, `jvm-check/` or `.github/` changed (`git diff --stat 4c54b41..6212a47`).
- The fail-closed dependency verification stays as it is, and R29 forbids weakening it (`progress.md`, R29).
- The corrected security claim (M-3, `task-11-report.md:207`) now matches the dex evidence.

### Revisión Ingeniero Móvil Senior

- `CLAUDE.md:129` and `plan:42` give the command that works with the wrapper's Gradle 9.6.0. I ran it: 1746 tests, 0 failures (`rv/run/rereview-jvm2.txt`).
- `README.md:90-92`, `docs/PUBLICAR.md:171` and `.github/pull_request_template.md:7` agree.

### Textos para el Abogado Digital (revisión final)

- **UI texts:** none.
- **Documentation:** one technical line in `CLAUDE.md:129` (a command and a comment), with no legal content. The licence counts the Abogado should use are in `task-11-report.md:91-94`.

### Assessment

**Approved.** Every finding from the Task 11 review is addressed, and none is open.
