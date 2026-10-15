plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "cn.com.dcsgo.mihx.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    sourceSets {
        // m-4：把导出的 Room schema JSON 暴露为单测 assets，MigrationTestHelper 由此读取
        getByName("test") {
            assets.srcDir("$projectDir/schemas")
        }
    }
    testOptions {
        unitTests {
            // m-4：迁移回归用 Robolectric 跑（MigrationTestHelper 需要资源/上下文）
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.hilt.android)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    // m-4（评审 2026-10-15）：MigrationTestHelper 迁移回归（Robolectric JVM 环境）
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
