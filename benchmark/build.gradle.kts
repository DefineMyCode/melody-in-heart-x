plugins {
    alias(libs.plugins.androidTest)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
}

android {
    namespace = "cn.com.dcsgo.mihx.benchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 33
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = libs.versions.jvmTarget.get()
    }

    targetProjectPath = ":app"

    buildTypes {
        // com.android.test mirrors its build types from the target project (:app),
        // but that mirror is not available while this script is being evaluated.
        // Create the build types during evaluation (before finalizeDsl) if they
        // are missing, otherwise configure the already-present default ones.
        if (findByName("release") == null) {
            create("release") {
                isMinifyEnabled = true
                isShrinkResources = true
                matchingFallbacks += listOf("release")
            }
        } else {
            getByName("release") {
                isMinifyEnabled = true
                isShrinkResources = true
                matchingFallbacks += listOf("release")
            }
        }
        if (findByName("debug") == null) {
            create("debug") {
                isMinifyEnabled = false
                matchingFallbacks += listOf("debug")
            }
        } else {
            getByName("debug") {
                isMinifyEnabled = false
                matchingFallbacks += listOf("debug")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.benchmarkMacro)
    implementation(libs.androidxTestJUnit)
    implementation(libs.androidxTestRunner)
    implementation(libs.truth)
}
