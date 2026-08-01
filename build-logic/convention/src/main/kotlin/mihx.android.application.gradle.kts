import mihx.convention.VerifyProductArchitecture

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    id("com.diffplug.spotless")
}

android {
    namespace = "cn.com.dcsgo.mihx"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 33
        targetSdk = 36
        vectorDrawables { useSupportLibrary = true }
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
