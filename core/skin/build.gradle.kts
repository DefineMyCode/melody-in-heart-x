plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "cn.com.dcsgo.mihx.core.skin"
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
}

dependencies {
    // 皮肤描述是纯数据模型 + 解析/校验，刻意只依赖 org.json 与 core:model。
    // 不依赖 compose：描述必须能在设备端被无 UI 地解析与校验（也便于单测）。
    implementation(libs.org.json)
    implementation(project(":core:model"))
    testImplementation(libs.junit)
}
