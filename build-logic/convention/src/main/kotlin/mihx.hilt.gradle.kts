// Dependency-only bundle. The Hilt + KSP Gradle plugins are applied by the consuming
// module's own `plugins {}` block (e.g. id("com.google.dagger.hilt.android")) so they
// resolve at apply-time via pluginManagement and never land on build-logic's kotlin-dsl
// classpath (Hilt 2.57 ships Kotlin 2.2 metadata, incompatible with the Kotlin 2.0
// kotlin-dsl used to compile these convention plugins).
// Coordinates kept in sync with gradle/libs.versions.toml (hilt=2.57).
dependencies {
    "implementation"("com.google.dagger:hilt-android:2.57")
    "ksp"("com.google.dagger:hilt-compiler:2.57")
}
