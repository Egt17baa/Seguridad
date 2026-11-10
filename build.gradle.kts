plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 compiles Kotlin itself (built-in Kotlin) and only brings the Kotlin Gradle plugin 2.2.10. Declaring the Kotlin
    // plugin here, without applying it, puts the KGP of `kotlin` in gradle/libs.versions.toml on the build classpath, so the
    // Kotlin compiler is the same version as the Compose and serialization compiler plugins.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.license.report) apply false
}
