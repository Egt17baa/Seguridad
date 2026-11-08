plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Android/Compose-only files; everything else in app/src/main is compiled here.
val androidOnly = listOf(
    "**/ui/**",
    "**/android/**",
    "**/MainActivity.kt",
    "**/SeguridadApp.kt",
)

sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java")
        kotlin.exclude(androidOnly)
    }
    test {
        kotlin.srcDir("../app/src/test/java")
        // Tests of Compose code (for example NoCopyMenuTest) need the Android build: :app:testDebugUnitTest runs them.
        kotlin.exclude("**/ui/**")
        resources.srcDir("../app/src/test/resources")
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bouncycastle)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test {
    testLogging { events("passed", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    // The source-text tests (MasterPolicyTest, PlatformSourcesTest, DocumentUriTest...) read the Android-only sources, which are
    // not compiled here. As inputs, a change in only one of them reruns the tests instead of leaving the task UP-TO-DATE.
    inputs.files(fileTree("../app/src/main/java") { include(androidOnly) })
        .withPropertyName("androidOnlySources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // MasterPolicyTest reads the word lists of the app (the same assets the screens load): a change in them reruns the tests.
    inputs.dir("../app/src/main/assets")
        .withPropertyName("appAssets")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // The tests that read files outside the sources: a change in only one of them reruns the tests (branch review C of
    // 1.4.0, M-6): the published texts (PublishedTextsTest, FinalLegalReviewTest, AppNameTest, ImportTextsTest, with the
    // Play «Novedades» drafts of the handoffs), the build, the manifest, the strings and the workflow (AppNameTest,
    // SourceTextsTest, PlatformSourcesTest). The same list is in app/build.gradle.kts. Plain files, resolved at
    // configuration time: configuration-cache safe.
    inputs.files(
        "../README.md", "../NOTICE", "../SECURITY.md",
        "../docs/privacidad.html", "../docs/ficha-google-play.md", "../docs/PUBLICAR.md",
        "../docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md",
        "../.github/workflows/build-apk.yml",
        "../app/build.gradle.kts", "../app/src/main/AndroidManifest.xml",
    ).withPropertyName("textsReadByTests").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(fileTree("../docs/superpowers/handoff") { include("*-traspaso.md") })
        .withPropertyName("handoffsReadByTests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("../app/src/main/res")
        .withPropertyName("appRes")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
