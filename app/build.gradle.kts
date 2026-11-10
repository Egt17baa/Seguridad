import com.github.jk1.license.filter.LicenseBundleNormalizer
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // No org.jetbrains.kotlin.android: AGP 9 compiles Kotlin itself (built-in Kotlin).
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.license.report)
}

fun loadProps(name: String) = Properties().apply {
    rootProject.file(name).takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Upload key for Google Play: keystore.properties (never committed) or, on CI, environment variables.
val keystoreProps = loadProps("keystore.properties")
fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "SEGURIDAD_KEYSTORE_FILE")
// Without an upload key, a release APK or AAB is only built on request (-PfirmaDePrueba=true), signed with the public
// debug key: anyone can sign an "update" with that key, so it must never be distributed by mistake. See checkReleaseKey.
val firmaDePrueba = providers.gradleProperty("firmaDePrueba").orNull == "true"

android {
    namespace = "app.seguridad"
    compileSdk = 36

    defaultConfig {
        // Fixed for ever once on Google Play. The Kotlin namespace stays app.seguridad: only the installed identity changes.
        applicationId = "io.github.egt17baa.seguridad"
        minSdk = 26
        targetSdk = 36
        // Every Play upload needs a higher versionCode: pass -PversionCode=N (CI uses the run number).
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "1.4.2"
        // SHA-256 of the certificate in debug.keystore (public test key), for verifying the installed debug build.
        buildConfigField("String", "DEBUG_CERT_SHA256", "\"C0014DF66F103A436485115FE0880512C9F05DB39E0E3A94D659997EE9411834\"")
    }

    signingConfigs {
        // Shared debug key (not secret, never accepted by Play) so local and CI test builds update each other.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "SEGURIDAD_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SEGURIDAD_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SEGURIDAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without an upload key the release build is signed with the debug key, so lintRelease, checkAssets and the
            // other checks still configure; packaging it fails in checkReleaseKey unless -PfirmaDePrueba=true is given.
            // Google Play rejects that signature, so a real upload always needs keystore.properties (or the CI secrets).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // AGP's default patterns (copied from AGP 8.13, the same in AGP 9.5.0) stop applying once the list is not empty, so
        // they are kept here.
        ignoreAssetsPatterns += "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~".split(':')
        // OkHttp 5 ships Mozilla's Public Suffix List (MPL-2.0) as an asset. It reads it only to parse cookies and in
        // HttpUrl.topPrivateDomain(): the app uses CookieJar.NO_COOKIES and never calls topPrivateDomain(). Without the
        // file both throw IllegalStateException on the phone (the JVM tests still find it in okhttp-jvm). See checkAssets.
        ignoreAssetsPatterns += "!PublicSuffixDatabase.list"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.bouncycastle)
    implementation(libs.androidx.biometric)
    // MainActivity is a FragmentActivity (for BiometricPrompt): fragment >= 1.3.0 or no picker or scanner opens.
    // biometric 1.1.0 alone resolves fragment 1.2.5; checkFragmentVersion (below) fails the build if that comes back.
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.zxing.embedded)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

// Licence gate: checkLicense fails if any library shipped in the release APK has a licence
// outside config/allowed-licenses.json (Apache-2.0, MIT, BSD-2/3-Clause, ISC, CC BY). Copyleft and proprietary fail.
licenseReport {
    configurations = arrayOf("releaseRuntimeClasspath")
    filters = arrayOf(LicenseBundleNormalizer())
    allowedLicensesFile = rootProject.file("config/allowed-licenses.json")
}

// A10e: packaging a release APK or AAB without the upload key fails at execution time, so configuring the release
// variant (lintRelease, checkLicense, checkAssets) still works without it. Only the packaging tasks depend on it.
val checkReleaseKey = tasks.register("checkReleaseKey") {
    group = "verification"
    description = "Falla si se empaqueta una release sin clave de subida y sin -PfirmaDePrueba=true."
    val missingKey = releaseStoreFile == null && !firmaDePrueba
    doLast {
        if (missingKey) {
            throw GradleException(
                "Sin clave de subida: usa keystore.properties o -PfirmaDePrueba=true para un APK de prueba firmado con la " +
                    "clave pública de depuración",
            )
        }
    }
}
val releasePackagingTasks = setOf("packageRelease", "packageReleaseBundle", "packageReleaseUniversalApk")
tasks.matching { it.name in releasePackagingTasks }.configureEach { dependsOn(checkReleaseKey) }

// Regression guard for Seguridad 1.0.0: androidx.biometric 1.1.0 alone brings androidx.fragment 1.2.5, whose
// FragmentActivity.startActivityForResult rejects the request codes of ActivityResultRegistry (>= 0x10000) with
// "Can only use lower 16 bits for requestCode". MainActivity is a FragmentActivity (BiometricPrompt needs one), so
// every file picker and the QR scanner failed on the phone. From 1.3.0 FragmentActivity uses the registry itself.
val checkFragmentVersion = tasks.register("checkFragmentVersion") {
    group = "verification"
    description = "Falla si el APK llevaría androidx.fragment:fragment anterior a 1.3.0."
    val root = configurations.named("releaseRuntimeClasspath").flatMap { it.incoming.resolutionResult.rootComponent }
    doLast {
        val seen = mutableSetOf<ComponentIdentifier>()
        val queue = ArrayDeque(listOf(root.get()))
        var fragment: String? = null
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (!seen.add(c.id)) continue
            val id = c.moduleVersion
            if (id != null && id.group == "androidx.fragment" && id.name == "fragment") fragment = id.version
            c.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { queue.add(it.selected) }
        }
        val found = fragment ?: throw GradleException("androidx.fragment:fragment no está en releaseRuntimeClasspath.")
        val parts = found.split('.', '-').take(3).map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val (major, minor) = parts + listOf(0, 0)
        if (major < 1 || (major == 1 && minor < 3)) {
            throw GradleException(
                "androidx.fragment:fragment $found es anterior a 1.3.0: su FragmentActivity rechaza los códigos de petición " +
                    "de ActivityResultRegistry y el escáner QR y los selectores de archivos fallan. Declara fragment-ktx >= 1.3.0 " +
                    "en gradle/libs.versions.toml.",
            )
        }
        logger.lifecycle("androidx.fragment:fragment $found (>= 1.3.0)")
    }
}
tasks.named("check") { dependsOn(checkFragmentVersion) }

// Licence gate for data files: checkLicense reads each library's licence, but a library can ship an asset under another
// one (OkHttp 5 brings Mozilla's Public Suffix List, MPL-2.0, excluded above). Every asset of the release APK must come
// from app/src/main/assets, so an asset a new library adds fails the build until its licence has been reviewed.
// Only assets/: Java resources of library jars are not checked (OkHttp's Android artifact ships the list as an asset).
// It also fails if a word list of the rule of a new master is missing from the APK (final review 1.3.1, M2): the app
// would then turn the word check off without a sign (loadMasterWords gives an empty set).
val releaseAssets = objects.listProperty<Directory>()
androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    releaseAssets.add(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.ASSETS))
}
val checkAssets = tasks.register("checkAssets") {
    group = "verification"
    description = "Falla si el APK llevaría un archivo en assets/ que no sea de app/src/main/assets, " +
        "o si le faltaría una lista de palabras de la contraseña maestra o de «Repartir un secreto»."
    val merged = releaseAssets
    // MASTER_WORD_LISTS in vault/MasterPolicy.kt (MasterPolicyTest checks that this list names them all), and the list of
    // «Repartir un secreto» (1.4.0, task 4), without which that tool cannot work.
    val wordLists = listOf("eff_large_wordlist.txt", "bip39_english.txt", "bip39_spanish.txt", "slip39_english.txt")
    val own = layout.projectDirectory.dir("src/main/assets").asFile
    val stamp = layout.buildDirectory.file("checkAssets/ok")
    // The merged assets already hold the app's own, so they are the only input.
    inputs.files(merged)
    outputs.file(stamp)
    doLast {
        fun relative(root: File) = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
        val packed = merged.get().flatMap { relative(it.asFile) }.toSet()
        val missing = wordLists - packed
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Al APK le faltarían listas de palabras: ${missing.joinToString()}. Sin ellas, la regla de una " +
                    "maestra nueva no reconoce una sola palabra, o «Repartir un secreto» no funciona: no las quites " +
                    "ni las excluyas en androidResources.",
            )
        }
        val foreign = packed - relative(own)
        if (foreign.isNotEmpty()) {
            throw GradleException(
                "El APK llevaría assets que no son de la app: ${foreign.sorted().joinToString()}. Una biblioteca puede traer " +
                    "datos con otra licencia que la suya: revísala y, si no se usan, exclúyelos en androidResources.",
            )
        }
        stamp.get().asFile.writeText("ok\n")
        logger.lifecycle("assets/: solo archivos de app/src/main/assets, con las cuatro listas de palabras")
    }
}
tasks.named("check") { dependsOn(checkAssets) }

// The unit tests that read files outside the compiled sources declare them as inputs, so a change in only one of them
// reruns the tests instead of leaving them UP-TO-DATE (branch review C of 1.4.0, M-6): the published texts
// (PublishedTextsTest, FinalLegalReviewTest, AppNameTest, ImportTextsTest, with the Play «Novedades» drafts of the
// handoffs), the build, the manifest, the strings and the workflow (AppNameTest, SourceTextsTest, PlatformSourcesTest),
// the sources read without their comments, and the assets. The same list is in jvm-check/build.gradle.kts. Plain files,
// resolved at configuration time: configuration-cache safe.
tasks.withType<Test>().configureEach {
    inputs.files(
        "../README.md", "../NOTICE", "../SECURITY.md",
        "../docs/privacidad.html", "../docs/ficha-google-play.md", "../docs/PUBLICAR.md",
        "../docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md",
        "../.github/workflows/build-apk.yml",
        "build.gradle.kts", "src/main/AndroidManifest.xml",
    ).withPropertyName("textsReadByTests").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(fileTree("../docs/superpowers/handoff") { include("*-traspaso.md") })
        .withPropertyName("handoffsReadByTests").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/res").withPropertyName("resReadByTests").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/assets").withPropertyName("assetsReadByTests").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/java").withPropertyName("sourcesReadByTests").withPathSensitivity(PathSensitivity.RELATIVE)
}
