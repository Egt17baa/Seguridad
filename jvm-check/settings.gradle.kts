// JVM-only verification harness: compiles the platform-independent sources of
// :app and runs its unit tests using Maven Central only (no Android SDK).
pluginManagement { repositories { gradlePluginPortal(); maven("https://maven-central.storage-download.googleapis.com/maven2/"); mavenCentral() } }
dependencyResolutionManagement {
    repositories { maven("https://maven-central.storage-download.googleapis.com/maven2/"); mavenCentral() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
rootProject.name = "jvm-check"
