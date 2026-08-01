// Dependency-only bundle. The Room + KSP Gradle plugins are applied by the consuming
// module's own `plugins {}` block (e.g. id("androidx.room")) so they resolve at
// apply-time via pluginManagement and stay off build-logic's kotlin-dsl classpath.
// Coordinates kept in sync with gradle/libs.versions.toml (room=2.7.1).
dependencies {
    "implementation"("androidx.room:room-runtime:2.7.1")
    "implementation"("androidx.room:room-ktx:2.7.1")
    "ksp"("androidx.room:room-compiler:2.7.1")
}
