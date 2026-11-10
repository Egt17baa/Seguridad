# Task 11 review: AGP 9 and the new R8 (cd65860..52017bb)

Reviewer's own evidence (8-11-2026) is in the scratchpad under `rv/`: `rv/vm/` (metadata diff and the sample downloads), `rv/cmp/` (APK, dex, resource and manifest comparisons), `rv/run/` (Gradle runs: `releaseRuntimeClasspath`, dry runs, the release without a key, the release build, `configuration.txt`, the gate).

### Spec Compliance

✅ **Matches the plan «### Task 11» and spec «### R8 y AGP 9».** One Important defect outside the build itself (I-1: the documented jvm-check command no longer works with the Gradle installed in the cloud sessions) and three report-accuracy minors.

- ✅ **Step 1, version:** AGP 9.5.0, the newest stable when the task was dispatched; developer.android.com/build/releases/gradle-plugin (fetched 8-11-2026) gives Gradle 9.6.0 as minimum and default, JDK 17, Build Tools 36.0.0, max API 37. 9.5.1 is out and lists «No public issues were marked as fixed»: R28 covers staying on 9.5.0.
- ✅ **Step 2, migration:**
  - `org.jetbrains.kotlin.android` is gone (`app/build.gradle.kts:6`, `gradle/libs.versions.toml:45-49`); built-in Kotlin compiles (`app/build/intermediates/built_in_kotlinc/…`).
  - `kotlin.jvm apply false` (`build.gradle.kts:3-6`) lifts AGP's KGP 2.2.10 to the catalogue's 2.4.21. The compiled `.kotlin_module` has metadata version 2.4.0, so the compiler really is 2.4.x.
  - `jvmTarget` 17: `MainActivity.class` in `built_in_kotlinc/release` is class-file major 61.
  - `ignoreAssetsPatterns`, `checkAssets`, `checkLicense`, `checkFragmentVersion`, signing: see «Build logic» below. All still run and still bite.
  - `gradle.properties` is unchanged: no `android.builtInKotlin=false`, `android.newDsl=false`, `android.r8.strictFullModeForKeepRules=false` or `android.r8.optimizedResourceShrinking=false`.
- ✅ **Step 3, metadata:** regenerated the README way (JDK 17, a fresh `GRADLE_USER_HOME`, the README task lists; `t11/scripts/write-fresh.sh`). My own parse of both files gives exactly the report's numbers: app 1043 → 777 sums (169 added, 435 removed, **0 changed**), components 539 → 414 (86 added, 211 removed, 28 with a different artifact set); jvm-check 130 → 128 sums (4 added, 6 removed, 0 changed), components 72 → 71. Origins and licences: see «Supply chain».
- ✅ **Step 4, verification:** R8 Kotlin-metadata warnings 78 (`t11/base-release.log`) → 0 (`t11/gate-release.log`, and my own release build `rv/run/release.txt`). Permissions, badging, manifest and `check-manifest.sh` identical (re-run by me, below).
- ✅ **Step 6:** commit `52017bb` «1.4.0: AGP 9 y el R8 nuevo».

### Strengths

- The wrapper is provably Gradle's own: JAR SHA-256 `497c8c2a…a9c7` and distribution SHA-256 `bbaeb2fe…9a01` match both gradle.org/release-checksums and the `.sha256` files on services.gradle.org. Running Gradle 9.6.0's own `wrapper` task in an empty directory gives the four committed files **byte for byte** (`cmp`: `gradlew`, `gradlew.bat`, the JAR and `gradle-wrapper.properties`).
- The metadata was treated as a security change. Every added sum was re-downloaded and matched (173/173, `t11/origins-all.txt`). No existing sum changed. The renamed KGP variant jars were traced one by one.
- The opt-out experiment measured what AGP 9's two new R8 defaults change, and then kept the defaults instead of silencing them.
- The two test edits make the source checks fail if the anchor disappears, which closes the old silent-pass hole: `substringAfter` of a missing anchor returned the whole file.
- My release build with `-PfirmaDePrueba=true` gives a `classes.dex` **byte for byte** equal to the implementer's gate (`cmp`), so the APK evidence is reproducible.

### Issues

#### Critical
None.

#### Important

**I-1. The project's documented jvm-check command now fails in cloud sessions: `gradle -p jvm-check test` with the installed Gradle 8.14.3.**
- **Where:**
  - `CLAUDE.md:129` («Compilar y probar»: `gradle -p jvm-check test --no-daemon --max-workers=1`);
  - the plan, `docs/superpowers/plans/2026-11-03-clavero-1.4.0.md:42` («Verification after every task»);
  - the cloud image's `/opt/gradle/bin/gradle`, which is Gradle 8.14.3.
- **What happens (run on 8-11-2026, `rv/run/installed-gradle.txt`):** `gradle -p jvm-check help` fails at configuration with «Dependency verification failed for configuration 'classpath'». Four artifacts fail: `kotlin-gradle-plugin-2.4.21-gradle813.jar`, `-api-…-gradle813.jar`, `fus-statistics-…-gradle813.jar` and `kotlin-serialization-…-gradle813.jar`. Under Gradle 8.x, KGP resolves its `-gradle813` variant, and the regenerated `jvm-check/gradle/verification-metadata.xml` has only the `-gradle96` variants.
- **Why it matters:** the failure is the correct, fail-closed behaviour, and the implementer rightly ran `./gradlew -p jvm-check test`. But `CLAUDE.md` is what every implementer and reviewer follows, and the plan line is Task 12's gate. Task 12, and every later session that copies the command, will get a red jvm-check that has nothing to do with the code. Someone could then be tempted to «fix» it by adding the `gradle813` sums back. `README.md:91-92` was updated («con un Gradle 9.6.x instalado»), but `CLAUDE.md` was not.
- **Fix:**
  - `CLAUDE.md:129` → `./gradlew -p jvm-check test --no-daemon --max-workers=1`, with a short note: the wrapper brings Gradle 9.6.0, and an installed Gradle 8.x fails dependency verification on purpose. The controller decides whether this goes now or in Task 12's `CLAUDE.md` pass, but before Task 12's gate.
  - A ledger line stating that the plan's «Verification after every task» jvm-check command is `./gradlew -p jvm-check test` from Task 11 on.
  - Do **not** add the `-gradle813` sums back.

#### Minor (all to be fixed)

**M-1. Report, licence counts: «81 Apache-2.0» is wrong.**
- **Where:** `task-11-report.md:90-95`.
- **What:** the 86 new components (my diff of `<component>` keys) split into:
  - 72 Apache-2.0: 65 that declare it in their own POM (`t11/licences-app.txt`), plus 7 through their parent. The 7 are Guava, commons-codec, commons-compress and commons-lang3 (shown as «-»), commons-parent 71 and 72, and `guava-parent`, which is missing from `licences-app.txt`, so that file has 85 rows, not 86;
  - 5 BSD-3-Clause (ASM);
  - 4 Bouncy Castle Licence (`bc-jdk18on-bom`, `bcprov`, `bcpkix`, `bcutil` 1.80.2);
  - 5 EPL-2.0 (`junit-bom` 5.11.0-M2 and 5.13.3, `junit-platform-commons/engine/launcher` 1.13.3).

  81 + 5 + 4 + 5 ≠ 86.
- **Failure scenario:** the Abogado Digital's final licence review, or the 1.4.0 audit, copies a count that does not add up.
- **Fix:** replace the list with the four numbers above (72/5/4/5), and add the `guava-parent` row (Apache-2.0) to the evidence file or name it in the report.

**M-2. Report, renamed KGP jars: «Three of those keep the same SHA-256» is only true for jvm-check.**
- **Where:** `task-11-report.md:76`.
- **What:**
  - In `gradle/verification-metadata.xml`, five KGP variant jars move from `-gradle813` to `-gradle96`. **Four** keep the same SHA-256: `compose-compiler-gradle-plugin`, `fus-statistics-gradle-plugin`, `kotlin-gradle-plugin-api` and `kotlin-serialization`. Only `kotlin-gradle-plugin-2.4.21-gradle96.jar` is a new file (`d0a11912…575b`). I downloaded and hashed it from the Maven Central mirror: it matches.
  - In jvm-check it is three plus `kotlin-gradle-plugin`.
- **Fix:** say it per file.

**M-3. Report, opt-out experiment: strict full mode also removes four no-arg constructors, which the report does not list.**
- **Where:** `task-11-report.md:156-161` and «Revisión experto-seguridad-cripto», `:207` («strict keep rules drop nothing … the only extra class removed is `ActivityChooserView$InnerLayout`»).
- **What I found:** I compared, with `dexdump`, the no-arg constructors of every class whose name survives, in the opt-out APK (`t11/optout-out`) and the gate APK. Strict mode also removes `<init>()V`, inlined by R8 at its `new` sites, from four classes:
  - `androidx.core.graphics.drawable.IconCompat`;
  - `androidx.core.app.RemoteActionCompat`;
  - `androidx.versionedparcelable.CustomVersionedParcelable`;
  - `androidx.compose.material3.MinimumInteractiveModifier`.

  BASE (R8 8.13) keeps all four. The first three are kept only by `-keep class * implements androidx.versionedparcelable.VersionedParcelable`, without members (versionedparcelable 1.1.1 consumer rules, `rv/run/configuration.txt:943`). Under strict mode that rule no longer implies `<init>()`. The fourth is kept by Compose's `-keep,allowshrinking class * extends ModifierNodeElement` (`configuration.txt:737`), which is there for class-type comparisons, not instantiation.
- **Why there is no failure scenario:**
  - versionedparcelable reaches classes by reflection only through `…Parcelizer.read`/`write`, which stay (`-keep public class android.support.**Parcelizer { *; }`).
  - `read` builds the object directly. In the gate dex, `IconCompatParcelizer.read` does `new-instance IconCompat` and `invoke-direct Object.<init>`, followed by the field stores.
  - Clavero has no notifications, shortcuts or `RemoteAction` that would parcel these classes anyway.
  - `MinimumInteractiveModifier` is a singleton read through `sget-object`.
- **Fix:** correct the two sentences: «strict mode drops one extra class (`ActivityChooserView$InnerLayout`) and the no-arg constructors of these four classes; why that is harmless: …». The finding is about the accuracy of a security claim, not about the APK.

#### Informative (no change asked)

- **`kotlin-tooling-metadata.json` is no longer in the APK** (`rv/cmp/zip-*.txt`). The removed `kotlin-android` plugin used to add it, and it carried only build-tool versions. This is an improvement worth one line in the report's «APK comparisons». The other zip-entry differences are 39 `res/` files from the resource shrinking and the obfuscated `META-INF/services` names of the same two coroutines services (`AndroidDispatcherFactory`, `CoroutineExceptionHandler`), present in both.
- **R8 9.5 repackages obfuscated classes into the default package** (`i9`, `ic0` instead of `f8.r`). There is no security effect. Stack traces from the phone need the `mapping.txt` of the same build, as before.
- **Lint `OldTargetApi`** (API 37 is known to lint 32.5): a target-SDK decision for a later version, not this task. Remember the CLAUDE.md rule for each `targetSdk` bump (N-04, CRY-B01).
- **Concern 3 of the report (KGP coupling):** `kotlin-gradle-plugins-bom` 2.4.21 aligns KGP, its API and the compose and serialization plugins (`t11/buildenv-new.txt:219-236`). A later AGP that brings a higher KGP would move them together or fail loudly, so it cannot drift silently. Nothing to add.

### Supply chain (check 1, 2, 7)

- **Wrapper:**
  - `sha256sum gradle/wrapper/gradle-wrapper.jar` = `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`, which is the «Wrapper JAR Checksum» of 9.6.0 on gradle.org/release-checksums and `gradle-9.6.0-wrapper.jar.sha256`.
  - `distributionSha256Sum` = `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`, which is the «Binary-only (-bin) ZIP Checksum» and `gradle-9.6.0-bin.zip.sha256`. The wrapper downloaded 9.6.0 for my runs under that check.
  - `validateDistributionUrl=true` stays.
  - The CI checksum occurs exactly three times (`.github/workflows/build-apk.yml:71,237,301`), and the old one is gone.
  - The workflow diff is 6/6 lines: those three checksums and three comment lines (`:68-69`, `:246`). No action, permission, `if`, environment or secret changed.
- **Metadata:**
  - `verify-metadata` is `true` and `verify-signatures` is `false` in both files, as before. There is no `<trusted-artifacts>`, `<trust>`, `<ignored-keys>` or `<trusted-keys>` in either file, before or after (the parser saw only `<configuration>` and `<components>`, and only `<sha256>` children).
  - I sample-verified **29** new sums myself (`rv/vm/sample-verify.txt`, 29/29 OK):
    - from `dl.google.com/dl/android/maven2`: `gradle-9.5.0.jar` (AGP), `builder-9.5.0.jar` (the R8 9.5.28 inside), `aapt2-9.5.0-16666295-linux.jar`, `bundletool-1.18.3.jar`, `apksig`, `signflinger`, `manifest-merger`, `intellij-core`, `kotlin-compiler` 32.5.0, `lint-checks`, `gradle-work-action`, `android-test-engine`, `com.android.application.gradle.plugin-9.5.0.pom`, `material3-desktop-1.3.2.jar` and `material-icons-core-desktop-1.7.8.jar`;
    - from the Maven Central mirror: `kotlin-gradle-plugin-2.4.21-gradle96.jar` (the one new KGP file), `compose-compiler-gradle-plugin` and `kotlin-serialization` `-gradle96`, `org.jetbrains.kotlin.jvm.gradle.plugin-2.4.21.pom`, `kotlin-stdlib-2.3.21`, `bcprov` and `bcpkix` 1.80.2, `guava-33.4.0-jre`, `groovy-4.0.29`, `asm-9.9`, `javapoet-1.13.0`, `commons-compress-1.27.1`, `junit-platform-launcher-1.13.3` and `junit-bom-5.11.0-M2.pom` (a milestone BOM, POM only: `commons-parent` 71 and 72 import it through `commons.junit.version`).
  - The jvm-check file's four added sums are the same values as the app file's.
  - The one jvm-check removal, `bcprov-jdk18on:1.80`, was a stale entry: no configuration of jvm-check resolves it under Gradle 9.6. Removing an entry can only narrow what is trusted.
- **APK classpath:** `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` (my run) is identical to `t11/deps-base-rrc.txt`, apart from the wrapper's download banner. No artifact is added to or changed in what ships. Bouncy Castle is 1.86 in the APK; 1.80.2 is only on AGP's build classpath.
- **Licences of the build-only artifacts:** EPL-2.0 (JUnit Platform, AGP's UTP runner), the Bouncy Castle Licence (MIT-style) and R8's bundled GPL-with-Classpath-Exception/LGPL notices are all build tooling. None is in `releaseRuntimeClasspath`, so `checkLicense` passes, and none is in the APK (the `classes.dex` classes come from the same runtime classpath as BASE). Same situation as 1.3.1 (R28).

### Build logic (check 3)

- **`checkReleaseKey`:**
  - Without a key and without `-PfirmaDePrueba`, `:app:assembleRelease` fails in `:app:checkReleaseKey` with «Sin clave de subida…» (`rv/run/nokey.txt:16-17`), and so does `:app:bundleRelease` (`rv/run/nokey-bundle.txt:45`).
  - The dry runs show `checkReleaseKey` before `packageRelease` and `packageReleaseBundle` (`rv/run/dry-release.txt`, `dry-bundle.txt`): the AGP 9 task names in `releasePackagingTasks` (`app/build.gradle.kts:144`) still match.
- **`check`** still runs `checkAssets` and `checkFragmentVersion` (`rv/run/dry-check.txt`), after `mergeReleaseAssets`. `checkAssets` reads `SingleArtifact.ASSETS` (`app/build.gradle.kts:188-189`), and the release APK's `assets/` holds exactly the app's files plus `dexopt/` (which is added at packaging, after the check). `PublicSuffixDatabase.list` is absent. CI calls all four checks by name (`build-apk.yml:81`).
- **`tasks.register`** keeps the names, groups and bodies (`app/build.gradle.kts:131,151,191`).
- **Unchanged:** the signing config (`:40-67`), `versionCode` from `-PversionCode` (`:34`) and `DEBUG_CERT_SHA256` (`:37`). Both release APKs (BASE and gate, `-PfirmaDePrueba`) are v2-signed with certificate `c0014df6…1834`, the same value as `DEBUG_CERT_SHA256`.
- **Note:** AGP 9 creates unit tests only for the tested build type, so `test` now runs `testDebugUnitTest` only. CI already ran only `testDebugUnitTest`, so nothing is lost.

### Test edits (check 4)

`PlatformSourcesTest.kt:217-220` and `MasterPolicyTest.kt:186-191`:
- The same assertions as before: `slip39_english.txt` inside `wordLists`; the four master lists in the task body; `if (missing.isNotEmpty()) { throw GradleException`.
- Plus `assertTrue(anchor in gradle)`. This is stricter: before, a missing anchor made `substringAfter` return the whole file.

Nothing is weakened.

### Release behaviour (check 5)

- **Bouncy Castle:** the app uses only BC's lightweight API, by direct class reference (`grep '^import org.bouncycastle'`: digests, HKDF, Argon2, SCrypt, PBKDF2, AES/GCM/CBC, ChaCha20-Poly1305, Twofish/Serpent/Camellia, Ed25519, X25519, HPKE, X500Name). No `Security.addProvider` or `getInstance(…, "BC")`, and every JCA `getInstance` uses platform algorithms or `AndroidKeyStore`. There is no `Class.forName`, `ServiceLoader` or `getIdentifier` in `app/src/main/java`. So reachability, not names, decides what R8 keeps.
  - Several BC classes no longer exist under their own name (`Argon2Parameters`, `IETFUtils`, `rfc8032.Ed25519`, `X25519Field`…). Their members are still in the mapping, merged into other classes (for example, 22 `Argon2Parameters.` and 389 `Ed25519.` member lines in `t11/new-out/mapping.txt`). That is class merging by the new R8, not stripping.
- **kotlinx.serialization:** the same seven `$$serializer` classes, under their original names, in BASE and now (`app/proguard-rules.pro:6`, explicit `{ *; }`, so strict mode does not touch them; the library's own `kotlinx-serialization-r8.pro` and `-common.pro` are applied, `configuration.txt:857,889`).
- **Classes kept by name:** the same 155 in BASE and now, except `ActivityChooserView$InnerLayout` (unreachable, see the report). For no-arg constructors, see M-3.
- **Manifest components and startup initializers keep `<init>()V`** in the gate dex: `MainActivity`, `SeguridadApp`, `SecureCaptureActivity`, zxing's `CaptureActivity`, `FileProvider`, `InitializationProvider`, `ProfileInstallReceiver`, `EmojiCompatInitializer`, `ProcessLifecycleInitializer` and `ProfileInstallerInitializer`. androidx.startup's rule names `<init>()` explicitly (`configuration.txt:787-790`).
- **zxing:** `MultiFormatReader`, `QRCodeReader`, `QRCodeWriter` and `DefaultDecoderFactory` code is present: classes merged or renamed, 218 `QRCodeReader.` member lines. The readers are instantiated with `new`, not by name.
- **Resources:** 1 162 → 977 entries. None of the 185 removed is the app's (`app/src/main/res`: the launcher drawables and mipmaps, `xml/backup_rules`, `xml/data_extraction_rules`, `xml/share_paths`, `Theme.Seguridad` and all `values/strings.xml` names are still there). The removed ones are notification layouts and drawables, AppCompat styles and attrs, fragment animators, and nine compose-ui or core strings. The R8-based shrinker removes a resource only when no kept code references it, and nothing in the app looks resources up by name.
- **What I could not verify:** running the minified APK. No automated test does that. **Phone tests for the signed 1.4.0 APK** (ledger, Task 11, plus two):
  - a new vault;
  - import a copy and open it;
  - «Importar de otra app»;
  - the QR scanner and making a QR;
  - fingerprint enable and unlock;
  - a snackbar;
  - share a file (`FileProvider`);
  - the three new tools;
  - «Comprobar una copia»;
  - «¿Filtrada?» with network;
  - **also** «Tachar» and «Limpiar foto» (the image code R8 merged most, `ui.screens` −107 synthetic classes);
  - **also** a «Repartir un secreto» round trip (`Rs1024` was merged into its callers).

### Permissions, badging, manifest (check 6)

I re-ran `aapt2` 36.0.0 on `t11/base-out` and `t11/gate-out` (`rv/cmp/`):
- `dump permissions` (release and debug): identical. INTERNET, CAMERA, USE_BIOMETRIC, USE_FINGERPRINT and the `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- `dump badging` (release and debug): identical.
- `dump xmltree AndroidManifest.xml` (release): identical.
- `scripts/check-manifest.sh --release`: OK. The debug run is OK, with the usual `PreviewActivity` line.

My own release build (`rv/run/app-release.apk`, 3 004 922 B) has the same `classes.dex` as the gate.

### Report accuracy (check 8)

Checked and correct:
- the metadata counts;
- the 173 origins;
- 78 → 0 R8 warnings;
- the APK and debug sizes (3 050 852 → 3 004 922; 17 651 078 → 17 700 339);
- `classes.dex` 4 552 080 → 4 384 268;
- class definitions 4 807 → 4 553;
- `baseline.prof` 6 539 → 9 974;
- resources 1 162 → 977;
- opt-out APK +61 793 B, dex Δ 484 B;
- lint: 18 warnings, 0 errors, and the IDs as listed;
- CI simulation green, one 429 retried (`t11/ci2/ci-sim.out`).

Not correct: M-1, M-2, M-3.

### Revisión experto-seguridad-cripto

- **Threat model of this change:** a compromised or substituted build artifact (wrapper JAR, Gradle distribution, plugin or library) that ends up running in CI with the signing secrets, or inside the APK. Controls checked:
  - wrapper JAR pinned in CI (`build-apk.yml:71,237,301`) and identical to gradle.org's;
  - distribution pinned (`gradle/wrapper/gradle-wrapper.properties:4`);
  - every dependency pinned by SHA-256 (`gradle/verification-metadata.xml:4`, `verify-metadata` true), 29 new sums re-hashed by me from the origin repositories and 173/173 by the implementer;
  - nothing loosened: no trust or ignore rules, and `verify-signatures` is false as before.
- **No crypto, format, key or rule change:** no file under `app/src/main` changed. BC is 1.86 in the APK (`gradle/libs.versions.toml:14`; `releaseRuntimeClasspath` identical).
- **R8 and crypto:** BC is used by direct reference only. No provider is registered, and no algorithm is looked up by name from BC (JCA calls use the platform and `AndroidKeyStore`, `BiometricVault.kt:110,132,137`). R8's class merging cannot break a lookup that does not exist. kotlinx.serialization serializers are kept under their names (`app/proguard-rules.pro:3-10`).
- **Fail-closed verification:** the Gradle 8.14.3 failure in I-1 is dependency verification doing its job. The fix is the documentation, never the metadata.
- **Release-key gate intact:** `app/build.gradle.kts:131-145`; `rv/run/nokey.txt:16-17`; `rv/run/nokey-bundle.txt:45`.
- **Security claims in the report:** M-3 must be corrected. The conclusion (nothing reflective lost) holds, but the evidence sentence is incomplete.

### Revisión Ingeniero Móvil Senior

- **Built-in Kotlin:** done the AGP 9 way (`app/build.gradle.kts:4-10`). `kotlin { compilerOptions { jvmTarget } }` still applies (`:88-92`; major 61). The compiler is 2.4.x (`.kotlin_module` metadata 2.4.0), aligned with the compose and serialization plugins through `kotlin-gradle-plugins-bom` (`t11/buildenv-new.txt:219`).
- **New DSL and defaults:** no opt-out in `gradle.properties`.
  - The variant API is `androidComponents.onVariants` (`:188`), which the new DSL supports.
  - `buildConfig = true` is explicit (`:75`).
  - `proguard-android-optimize.txt` is the AGP 9.5.0 one (`configuration.txt:1`).
  - Unit tests now exist for debug only, matching CI.
- **Gradle 9.6:** `tasks.register` replaces the deprecated delegates (`:131,151,191`). No deprecation from the build scripts in `rv/run/release.txt`.
- **R8 9.5.28:** 0 metadata warnings; dex −3.7 %, APK −1.5 %; a new `baseline.prof`. Constructor inlining and class merging account for the class-count drop (M-3, «Release behaviour»).
- **Resources:** only library resources are removed. The debug build that tests and lint use is not shrunk.
- **CI:** the same commands pass with empty caches (`t11/ci2`). The documented local jvm-check command needs `./gradlew` (I-1).
- **Gate (reviewer's run, 8-11-2026):** `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:checkLicense :app:checkFragmentVersion :app:checkAssets --no-daemon --max-workers=2` gave BUILD SUCCESSFUL, with **1751** app tests, 0 failures, 0 errors and 0 skipped. `./gradlew -p jvm-check test` gave BUILD SUCCESSFUL, with **1746** tests and 0 failures (`rv/run/status`, `gate-app.txt`, `gate-jvm.txt`). Lint had 0 errors and 19 warnings: one more `NewerVersionAvailable` than the implementer's 18 a few hours earlier. It is the same online version check, so the count depends on the day, as the report says.

### Textos para el Abogado Digital (revisión final)

- **UI texts:** none new or changed.
- **Documentation:** `README.md:91` «# o, con un Gradle 9.6.x instalado:» (technical). If I-1 is fixed, a technical line in `CLAUDE.md` «Compilar y probar».
- **Licences, for the final review:** the build-only EPL-2.0 (JUnit Platform 1.13.3, AGP's test runner), the Bouncy Castle Licence 1.80.2 (AGP's classpath) and R8's bundled GPL-CE/LGPL notices. None is in the APK; `checkLicense` covers what ships (R28). Use the corrected counts of M-1.

### Assessment

**Needs fixes:** I-1 (documented jvm-check command) and M-1, M-2, M-3 (report accuracy).

The build change itself is sound and ready:
- the wrapper and distribution are Gradle's own;
- the metadata is sound;
- nothing is added to the APK;
- the checks are wired and bite;
- permissions and manifest are identical;
- R8 drops nothing that is reached by name.

The fixes are documentation-only, so a short re-review is enough.
