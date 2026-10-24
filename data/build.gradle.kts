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

// Room schema JSON 由主源集的 KSP 任务生成(kspXxxKotlin)，单测 assets 的合并必须排在它之后：
// 否则 mergeXxxUnitTestAssets 先跑完，新版本 schema(12.json 等) 还没落盘，
// MigrationTestHelper 读 assets 报 "Cannot find the schema file"。
// 2026-10-24 迁移 v11→v12 时首次踩到（merge 任务对已生成的旧 schema 是 UP-TO-DATE 的，
// 新文件出现也不会自动触发重跑）。
tasks.matching { it.name.matches(Regex("merge\\w+UnitTestAssets")) }.configureEach {
    val variant = name.removePrefix("merge").removeSuffix("UnitTestAssets")
    dependsOn("ksp${variant}Kotlin")
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
