import mihx.convention.VerifyProductArchitecture

plugins {
    kotlin("jvm")
    id("com.diffplug.spotless")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Spotless is Kotlin-2.0-safe; version kept in sync with gradle/libs.versions.toml (spotless=6.25.0, ktlint=1.3.0).
spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    kotlin {
        target("src/**/*.kt")
        ktlint("1.3.0")
            .setEditorConfigPath(project.rootDir.resolve(".editorconfig"))
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.3.0")
            .setEditorConfigPath(project.rootDir.resolve(".editorconfig"))
    }
}

val srcTree = fileTree("src") { include("**/*.kt") }
val manifestTree = fileTree("src") { include("**/AndroidManifest.xml") }
tasks.register<VerifyProductArchitecture>("verifyProductArchitecture") {
    sources.set(srcTree)
    manifests.set(manifestTree)
    modulePath.set(project.path)
}
tasks.named("check") {
    dependsOn("verifyProductArchitecture", "spotlessCheck")
}
