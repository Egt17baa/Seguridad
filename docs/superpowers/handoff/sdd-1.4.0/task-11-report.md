# Task 11 report: AGP 9 and the new R8

**Status:** DONE_WITH_CONCERNS (the concerns are at the end; none blocks the task).

**Base:** `cd65860`, branch `ccr-d1e75160-p5f4wc`. The first implementer left the work uncommitted when the container restarted. This implementer checked every change again from the start. It also re-ran the lost CI simulation, the full gate and the comparisons on 8-11-2026.

## Versions and sources

| Piece | Before | After | Source (checked 8-11-2026) |
|---|---|---|---|
| Android Gradle plugin | 8.13.0 | **9.5.0** | developer.android.com/build/releases/gradle-plugin: AGP 9.5 needs Gradle 9.6.0 (minimum and default), JDK 17 and Build Tools 36.0.0, and supports up to API 37 |
| Gradle (wrapper) | 8.14.6 | **9.6.0** | gradle.org/release-checksums and services.gradle.org/distributions |
| R8 (inside `builder-9.5.0.jar`) | 8.13.x | **9.5.28** | `java -cp builder-9.5.0.jar com.android.tools.r8.R8 --version`. developer.android.com/build/kotlin-support asks for at least R8 9.1.29 for Kotlin 2.4 |
| Kotlin Gradle plugin | 2.4.21 (`kotlin-android`) | 2.4.21 (AGP's built-in Kotlin, raised from 2.2.10 by `kotlin.jvm apply false`) | `buildEnvironment`: `kotlin-gradle-plugin:2.2.10 -> 2.4.21` |

**Wrapper checks:**
- `gradle-wrapper.jar` SHA-256 is `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`. That is the «Wrapper JAR Checksum» of 9.6.0 on gradle.org/release-checksums and in `gradle-9.6.0-wrapper.jar.sha256`.
- `distributionSha256Sum` is `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`. That is the «Binary-only (-bin) ZIP Checksum» on the same page and in `gradle-9.6.0-bin.zip.sha256`.
- The four wrapper files (`gradlew`, `gradlew.bat`, the JAR and `gradle-wrapper.properties`) are **byte for byte** what Gradle 9.6.0 writes. They were compared with `gradle wrapper --gradle-version 9.6.0 --gradle-distribution-sha256-sum …`, run in an empty project.

## Changes (file:line and why)

- **`gradle/libs.versions.toml:2`:** `agp = "9.5.0"`. The `kotlin-android` plugin alias is removed, because AGP 9 compiles Kotlin itself.
- **`build.gradle.kts:3-6`:**
  - `kotlin.android` is replaced by `alias(libs.plugins.kotlin.jvm) apply false`, which declares the plugin without applying it.
  - Why: AGP 9's built-in Kotlin brings KGP 2.2.10. Declaring the plugin puts the catalogue's KGP 2.4.21 on the build classpath. The Kotlin compiler is then the same version as the Compose and serialization compiler plugins (2.4.21), as in 1.3.1.
  - `kotlin.jvm` is not applied to any project.
- **`app/build.gradle.kts:6`:** the `kotlin.android` plugin is removed. The comment says why.
- **`app/build.gradle.kts:78-79`:**
  - Only the comment changes. The 9.5.0 default `ignoreAssetsPatterns` are the same as in 8.13, so the copied list stays.
  - `!PublicSuffixDatabase.list` stays (`:84`). `checkAssets` passes, and the release APK's `assets/` holds only `app/src/main/assets`.
- **`app/build.gradle.kts:131,151,191`:** `val x by tasks.registering { }` becomes `val x = tasks.register("x") { }` for `checkReleaseKey`, `checkFragmentVersion` and `checkAssets`.
  - Why: Gradle 9.6 deprecates the `registering` delegates: «Use 'val task = tasks.register(name) { }' instead. See the Gradle 9.6 upgrading guide». They would break in Gradle 10.
  - Task names, groups, descriptions and bodies do not change.
- **Unchanged in `app/build.gradle.kts`:**
  - `jvmTarget` 17 (`:90`, inside `kotlin { compilerOptions }`, which AGP 9 still accepts). The compiled classes are class-file major 61, that is Java 17 (`built_in_kotlinc/release/.../MainActivity.class`).
  - `compileOptions` 17.
  - the signing setup (`:39-66`).
  - `versionCode` from `-PversionCode` (`:34`).
  - `checkLicense`.
  - the release `isMinifyEnabled` and `isShrinkResources`.
- **`app/src/test/java/app/seguridad/PlatformSourcesTest.kt:217-220` and `app/src/test/java/app/seguridad/vault/MasterPolicyTest.kt:185-188`:**
  - Both tests read `app/build.gradle.kts` and cut the `checkAssets` body after the text `val checkAssets by tasks.registering`. That text no longer exists.
  - The anchor becomes `val checkAssets = tasks.register("checkAssets")`.
  - Each test also gets a new `assertTrue(anchor in gradle)`. Before, a missing anchor made `substringAfter` return the whole file, so the test could pass by accident.
  - The security assertions do not change:
    - the four word lists of the master rule and `slip39_english.txt` must be inside the task body;
    - `if (missing.isNotEmpty()) { throw GradleException`.

    The edit makes the tests **stricter**, not weaker.
- **`gradle/wrapper/*`, `gradlew`, `gradlew.bat`:** Gradle 9.6.0's own wrapper (see above).
  - `gradle-wrapper.properties` gains `retries=0` and `retryBackOffMs=500`, which the 9.6.0 `wrapper` task writes.
  - `validateDistributionUrl=true` and `distributionSha256Sum` stay.
- **`.github/workflows/build-apk.yml:68-71,237,246,301`:** the wrapper-JAR checksum of the three jobs (`build`/`build-firma`, `jvm-check`, `play-bundle`) becomes the 9.6.0 one. The comments are updated too. Nothing else in the workflow changes: actions, permissions, `if` conditions and secrets are the same.
- **`README.md:91`:** «con un Gradle 9.6.x instalado».
- **`gradle.properties`:** not changed. AGP 9's new defaults stay on, with no opt-out (see «Opt-out experiment» below).
- **`gradle/verification-metadata.xml` and `jvm-check/gradle/verification-metadata.xml`:** regenerated as in the README (see the next section).

## Verification metadata (a security change)

Both files were regenerated as in README «Verificación de dependencias»:
- JDK 17;
- a new empty `GRADLE_USER_HOME` for each project;
- `rm -rf build app/build jvm-check/build`;
- the README's exact task lists.

In `write-fresh.log`, the write passed on the first try.

| File | `<component>` | `<sha256>` |
|---|---|---|
| `gradle/verification-metadata.xml` | 539 → 414: 86 added, 211 removed, 28 whose artifact set changed | 1043 → 777: **169 added, 435 removed, 0 changed** |
| `jvm-check/gradle/verification-metadata.xml` | 72 → 71: 0 added, 1 removed (`bcprov-jdk18on:1.80`), 4 changed | 130 → 128: **4 added, 6 removed, 0 changed** |

- **No sum of an existing artifact changed.**
  - The «changed» components only lost entries, for POMs or AARs no longer resolved.
  - Or the KGP variant JAR was renamed `-gradle813.jar` → `-gradle96.jar`. Three of those keep the same SHA-256. `kotlin-gradle-plugin-2.4.21-gradle96.jar` is a different file, the variant built for Gradle 9.6.
- **Origin of every added sum:** each one was downloaded again from the repositories that `settings.gradle.kts` uses, and its SHA-256 was compared (`scripts/origins.sh`, output `t11/origins-all.txt`). Of the 173 added sums (169 app + 4 jvm-check):
  - 99 match `dl.google.com/dl/android/maven2` (google());
  - 74 match the Maven Central mirror `maven-central.storage-download.googleapis.com`;
  - none fail and none were not found.
- **What the new components are:** all of them are build tooling that the AGP 9.5.0 and Gradle 9.6.0 upgrade needs:
  - AGP 9.5.0 and its tools 32.5.0 (`builder`, `lint`, `sdk-common`, `aapt2 9.5.0-16666295`, `bundletool 1.18.3`, `manifest-merger`, …);
  - their transitive libraries: Guava 33.4.0, commons-codec/compress/lang3, ASM 9.9, Groovy 4.0.29, BC 1.80.2 and `kotlin-stdlib`/`reflect` 2.2.10/2.3.21 for AGP;
  - the Unified Test Platform of AGP 9 (`android-test-engine` 1.0.8, JUnit Platform 1.13.3), in the configuration `unified-test-platform-gradle-work-action`;
  - the `org.jetbrains.kotlin.jvm` plugin marker 2.4.21.
- **`material3-desktop:1.3.2` and `material-icons-core-desktop:1.7.8`:** only the `*LintChecksClasspath` configurations resolve them (`:app:dependencies`, `t11/wt-deps.log`). AGP 9's lint resolves the JVM variant of the KMP libraries there to look for lint checks. Not in the APK.
- **`releaseRuntimeClasspath` (what goes into the APK) is identical** to BASE (`t11/deps-base-rrc.txt` = `t11/deps-new-rrc.txt`).
  - No app dependency is added or changed.
  - Bouncy Castle 1.80.2 is in AGP's build classpath only. The app keeps BC 1.86.
- **Licences of the 86 new components** (`t11/licences-app.txt`, from each POM or its parent):
  - 81 Apache-2.0;
  - BSD-3-Clause (ASM);
  - Bouncy Castle Licence (MIT-style; build classpath only);
  - EPL-2.0 for the JUnit 5 Platform. That is AGP's test runner, build tooling only, like JUnit 4 (EPL-1.0), which the project already uses for tests.
  - The parents Guava, commons and `org.apache:apache` are Apache-2.0.

  None is GPL, AGPL or LGPL, and none goes into the APK. `checkLicense` passes on `releaseRuntimeClasspath`.
- **CI with empty caches and without `--write-verification-metadata`** (lost run, re-run, `t11/ci2/`):
  - JDK 17;
  - `GRADLE_USER_HOME` new and empty for each project (`ci2/ci-app` and `ci2/ci-jvm`).

  All green, with no «Dependency verification failed» and no «Checksums are missing»:
  - `:app:testDebugUnitTest :app:lintRelease :app:checkLicense :app:checkFragmentVersion :app:checkAssets`;
  - `:app:assembleRelease -PversionCode=86 -PfirmaDePrueba=true`;
  - `:app:bundleRelease`;
  - `-p jvm-check test` with the JDK 21 toolchain (`-Porg.gradle.java.installations.fromEnv=JAVA_HOME_21_X64 -Porg.gradle.java.installations.auto-download=false`, JDK 21 at `/usr/lib/jvm/java-21-openjdk-amd64`). One HTTP 429 from the mirror, then OK on retry;
  - `:app:lintDebug`.

## Gate (8-11-2026, `t11/gate-*.log`)

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=2`: BUILD SUCCESSFUL.
  - **1751 app tests**, 0 failures, 0 errors, 0 skipped, the same as BASE.
- `./gradlew -p jvm-check test --no-daemon --max-workers=2`: BUILD SUCCESSFUL.
  - **1746 jvm-check tests**, 0 failures, the same as BASE.
- `./gradlew :app:assembleRelease -PfirmaDePrueba=true --no-daemon`: BUILD SUCCESSFUL.
- `./gradlew :app:checkReleaseKey` without a key and without `-PfirmaDePrueba`: it still fails, with «Sin clave de subida…».

## R8 warnings

| | BASE (AGP 8.13, R8 8.13) | Now (AGP 9.5.0, R8 9.5.28) |
|---|---|---|
| `WARNING: R8: An error occurred when parsing kotlin metadata…` | **78** (`t11/base-release.log`) | **0** (`t11/gate-release.log`) |
| Other R8 warnings | 0 | 0 (no «async parsing» warning, unlike the 1.3.1 attempt with R8 9.1.31 on AGP 8.13) |

The only compiler warnings are two `UNNECESSARY_NOT_NULL_ASSERTION`s, at `PhotoCleanScreen.kt:238` and `RedactScreen.kt:365`. Both were **already in BASE** (`base-release.log:69-70`) and are not part of this task.

## APK comparisons (release with `-PfirmaDePrueba=true`, BASE `t11/base-out/` vs now `t11/gate-out/`)

- `aapt2 dump permissions`: **identical**, for release and for debug.
- `aapt2 dump badging`: **identical**:
  - `package io.github.egt17baa.seguridad`;
  - `versionName 1.3.1`;
  - `minSdk 26`, `targetSdk 36`, `compileSdk 36`;
  - label «Clavero».
- `aapt2 dump xmltree AndroidManifest.xml` (release): **identical**.
- `scripts/check-manifest.sh --release`: OK. `scripts/check-manifest.sh` (debug): OK, with the same `PreviewActivity` line as BASE (debug only).
- `apksigner verify`: v2, with the same debug certificate `c0014df6…1834` as BASE.

| | BASE | Now | Delta |
|---|---|---|---|
| release APK | 3 050 852 B | 3 004 922 B | −45 930 B (−1.5 %) |
| `classes.dex` | 4 552 080 B | 4 384 268 B | −167 812 B (−3.7 %) |
| class definitions in the dex | 4 807 | 4 553 | −254 |
| `assets/dexopt/baseline.prof` | 6 539 B | 9 974 B | rewritten by the new R8, as in 1.3.1's R8 9.1 attempt |
| resource entries (`aapt2 dump resources`) | 1 162 | 977 | −185, from AGP 9's optimized resource shrinking |
| debug APK | 17 651 078 B | 17 700 339 B | +49 261 B |

The release `classes.dex` from this gate is byte for byte the one from the first implementer's run (`t11/new-out/`).

**Resources removed by the optimized resource shrinking:** the 185 entries are styles, attributes, dimensions, notification drawables and layouts, and strings. They belong to libraries such as AppCompat, core notifications and the `fragment` animators. None is from the app (`app/src/main/res` keeps everything).
- The removed strings `close_drawer`, `close_sheet`, `navigation_menu`, `range_start`/`range_end`, `snackbar_pane_title` and `tooltip_*` belong to `compose-ui`.
- `compose-material` (1.x), which is not in the APK, and `material3/NavigationDrawerKt`, which the app does not use and BASE's R8 had already removed, are the only code that reads them.
- Material3's `SnackbarHost`, which the app uses, does not read `snackbar_pane_title`.
- The app does not call `getIdentifier` or `Class.forName` (grep of `app/src/main/java`).

**Opt-out experiment (`t11/gradle.properties.optout`, `t11/optout-release.log`):** the first implementer built once with `android.r8.optimizedResourceShrinking=false` and `android.r8.strictFullModeForKeepRules=false`, to measure what AGP 9's two new R8 defaults change. Result:
- the dex differs by 484 bytes;
- strict full mode drops only one extra class, `androidx.appcompat.widget.ActivityChooserView$InnerLayout`. Neither BASE nor the new APK contains `ActivityChooserView` itself or its layout, so nothing can inflate it;
- the APK is 61 793 B larger without the resource shrinking.

AGP 9.5 also says `optimizedResourceShrinking=false` «is deprecated». So **`gradle.properties` carries no opt-out**: the defaults are kept, and nothing is hidden.

## Lint

- `lint-results-debug.txt`: **0 errors, 18 warnings**, against the known 17 (16 + the AGP `NewerVersionAvailable`, ruling R17).
- Every warning is about versions or dates, and none is about code:
  - `AndroidGradlePluginVersion` ×2: AGP 9.5.1 and Gradle 9.9.0 exist;
  - `GradleDependency` ×7;
  - `NewerVersionAvailable` ×6;
  - `OldTargetApi` ×1: lint 32.5 knows API 37;
  - `ObsoleteSdkInt` ×1 and `DiscouragedApi` ×1, both already there.
- The counts depend on the day and on the network, because they are online version checks. None of them points at a source file.

## Concerns

1. **AGP 9.5.1 exists:**
   - the release notes say «No public issues were marked as fixed in AGP 9.5.1»;
   - the controller's target was 9.5.0, so I kept it;
   - moving to 9.5.1 means regenerating both metadata files and running the gate again;
   - better as a separate decision, maybe in 1.4.1.

   Gradle 9.9.0 (and 9.8.2 on the checksum page) also exist. AGP 9.5's minimum and default is 9.6.0.
2. **No automated test covers the minified APK.** The release dex changes because of:
   - R8 9.5 itself;
   - the optimized resource shrinking;
   - a new `baseline.prof`.

   The owner's phone test of the signed 1.4.0 APK should go through the paths that use reflection or load things by name:
   - a new vault, and opening and importing a copy (`kotlinx.serialization`);
   - the QR scanner (zxing);
   - the fingerprint;
   - a snackbar message (`SnackbarHost`);
   - sharing a file (`FileProvider`).
3. **The Kotlin compiler is the KGP 2.4.21 chosen by `kotlin.jvm apply false`.** If a later AGP raises its own KGP, Gradle takes the higher of the two. The catalogue's `kotlin` must stay at or above AGP's KGP, or the Compose and serialization compiler plugins stop matching the compiler.
4. **The R8 inside `builder-9.5.0.jar`** declares, as before, licences of the code it bundles (GPL with Classpath Exception, LGPL; see the 1.3.1 Task 7 report). It is a build tool: it does not go into the APK and `checkLicense` does not see it. Nothing changes here from AGP 8.13.

## Revisión experto-seguridad-cripto

- **Supply chain:** every added SHA-256 was matched against google() or the Maven Central mirror (`t11/origins-all.txt`: 173/173 OK). No existing sum changed. `verify-metadata` stays `true` (`gradle/verification-metadata.xml:4`). The wrapper JAR and distribution sums match gradle.org (`gradle/wrapper/gradle-wrapper.properties:4`; CI `.github/workflows/build-apk.yml:71,237,301`). The wrapper files are byte for byte Gradle 9.6.0's.
- **No new dependency in the APK:** `releaseRuntimeClasspath` is identical. BC stays 1.86 (`gradle/libs.versions.toml`). Crypto code, formats, keys and rules are not touched (no file under `app/src/main` changed).
- **Release key:**
  - `checkReleaseKey` still fails without a key (`app/build.gradle.kts:131`, run on 8-11-2026);
  - the signing config is unchanged (`app/build.gradle.kts:39-66`);
  - the CI keeps the secrets only in the packaging step (`build-apk.yml`, only the checksum lines changed).
- **APK surface:** the permissions, the manifest and `check-manifest.sh` are identical to BASE. `checkAssets` passes, so no third-party asset is in the APK.
- **Tests:** the two test edits make them stricter (`PlatformSourcesTest.kt:219`, `MasterPolicyTest.kt:187`). They keep the master-rule and SLIP-39 list assertions and the `throw GradleException` check.
- **R8 full mode:** strict keep rules drop nothing that the app or its libraries load by reflection (the only extra class removed is `ActivityChooserView$InnerLayout`, unreachable). `proguard-rules.pro` gives the `kotlinx.serialization` keep rules with explicit members, so the strict mode does not touch them.

## Revisión Ingeniero Móvil Senior

- **Built-in Kotlin:**
  - AGP 9's way, without the `kotlin-android` plugin (`app/build.gradle.kts:6`);
  - the `kotlin { compilerOptions { jvmTarget } }` block still configures it (`:88-92`; bytecode major 61);
  - the Compose and serialization compiler plugins are 2.4.21, the same as the compiler (`build.gradle.kts:3-6`).
- **Gradle 9.6:** no deprecation from the build scripts: the `registering` delegates are gone (`app/build.gradle.kts:131,151,191`). The configuration avoids eager realisation as before (`tasks.register`, providers).
- **R8 9.5.28:** it reads Kotlin 2.4 metadata (78 → 0 warnings). The release is 1.5 % smaller and has 254 fewer classes. `baseline.prof` is regenerated.
- **Resources:** the optimized shrinking removes only library resources that the kept code does not reference. App resources stay. The debug build, which tests and lint use, is not shrunk.
- **CI:**
  - the same commands pass with empty caches on JDK 17, and jvm-check on the JDK 21 toolchain (`t11/ci2/`);
  - `versionCode` from the run number still works (`-PversionCode=86` in the simulation; badging shows `versionCode='1'` without it);
  - the `bundleRelease` of `play-bundle` passes.

## Textos para el Abogado Digital (revisión final)

- **UI texts:** none new or changed.
- **Documentation:** one changed line in `README.md:91`, «# o, con un Gradle 9.6.x instalado:» (technical, no legal content).
- **For information:** the R8 licence note (concern 4) is the same as in 1.3.1.
