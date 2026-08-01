plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Mirror the root settings classpath so the Kotlin Gradle plugin (and its
    // `org.jetbrains.kotlin.plugin.compose` marker) is visible to kotlin-dsl's plugin
    // resolution for precompiled script plugins.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    // Kotlin-2.0-compatible plugin artifacts stay on build-logic's compile classpath.
    // Hilt/Room/KSP are applied via `apply(plugin = ...)` in the convention plugins so
    // they resolve at apply-time through pluginManagement and never land on build-logic's
    // kotlin-dsl classpath (Hilt 2.57's Gradle plugin ships Kotlin 2.2 metadata, which the
    // Kotlin 2.0 kotlin-dsl cannot read). Spotless is Kotlin-2.0-safe, so it stays here.
    implementation(libs.androidGradlePlugin)
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.kotlinComposeCompilerGradlePlugin)
    implementation(libs.spotlessGradlePlugin)
}
