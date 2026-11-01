// The R8 bundled with AGP 8.13.0 (8.13.x) reads Kotlin metadata up to 2.3 and warns on every class compiled by Kotlin 2.4.
// R8 9.1.29 and later read it (https://developer.android.com/build/kotlin-support). Build tool only: it never ends up in the
// APK, and its SHA-256 is pinned in gradle/verification-metadata.xml. Drop it once AGP bundles an R8 at least this new.
buildscript {
    repositories {
        google()
    }
    dependencies {
        classpath("com.android.tools:r8:9.1.31")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.license.report) apply false
}
