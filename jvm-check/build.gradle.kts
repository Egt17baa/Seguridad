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

tasks.test { testLogging { events("passed", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL } }
