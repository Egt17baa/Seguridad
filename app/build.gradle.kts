import com.github.jk1.license.filter.LicenseBundleNormalizer
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
        versionName = "1.2.0"
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
            // Without an upload key the release build is signed with the debug key so it can be installed
            // for testing; Google Play rejects that signature, so a real upload always needs keystore.properties.
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
        // AGP's default patterns (copied from AGP 8.13) stop applying once the list is not empty, so they are kept here.
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

// Regression guard for Seguridad 1.0.0: androidx.biometric 1.1.0 alone brings androidx.fragment 1.2.5, whose
// FragmentActivity.startActivityForResult rejects the request codes of ActivityResultRegistry (>= 0x10000) with
// "Can only use lower 16 bits for requestCode". MainActivity is a FragmentActivity (BiometricPrompt needs one), so
// every file picker and the QR scanner failed on the phone. From 1.3.0 FragmentActivity uses the registry itself.
val checkFragmentVersion by tasks.registering {
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
val releaseAssets = objects.listProperty<Directory>()
androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    releaseAssets.add(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.ASSETS))
}
val checkAssets by tasks.registering {
    group = "verification"
    description = "Falla si el APK llevaría un archivo en assets/ que no sea de app/src/main/assets."
    val merged = releaseAssets
    val own = layout.projectDirectory.dir("src/main/assets").asFile
    val stamp = layout.buildDirectory.file("checkAssets/ok")
    // The merged assets already hold the app's own, so they are the only input.
    inputs.files(merged)
    outputs.file(stamp)
    doLast {
        fun relative(root: File) = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
        val foreign = merged.get().flatMap { relative(it.asFile) }.toSet() - relative(own)
        if (foreign.isNotEmpty()) {
            throw GradleException(
                "El APK llevaría assets que no son de la app: ${foreign.sorted().joinToString()}. Una biblioteca puede traer " +
                    "datos con otra licencia que la suya: revísala y, si no se usan, exclúyelos en androidResources.",
            )
        }
        stamp.get().asFile.writeText("ok\n")
        logger.lifecycle("assets/: solo archivos de app/src/main/assets")
    }
}
tasks.named("check") { dependsOn(checkAssets) }
