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
}
