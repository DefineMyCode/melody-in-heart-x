import mihx.convention.VerifyProductArchitecture

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.compose")
    id("com.diffplug.spotless")
}

android {
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 33
        targetSdk = 36
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
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
