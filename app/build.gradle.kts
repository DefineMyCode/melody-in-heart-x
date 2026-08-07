plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

import java.util.Properties

/**
 * 读取根目录 keystore.properties(已 gitignore):storeFile/storePassword/keyAlias/keyPassword。
 * 文件不存在时返回 null,release 将回退使用 debug 签名(便于无密钥环境构建)。
 */
fun loadKeystoreProperties(): Map<String, String>? {
    val propsFile = rootProject.file("keystore.properties")
    if (!propsFile.exists()) return null
    val props = Properties().apply {
        propsFile.inputStream().use { load(it) }
    }
    return props.stringPropertyNames().associateWith { props.getProperty(it) }
}

android {
    namespace = "cn.com.dcsgo.mihx"
    compileSdk = 36

    defaultConfig {
        applicationId = "cn.com.dcsgo.mihx"
        minSdk = 33
        targetSdk = 36
        versionCode = 22
        versionName = "3.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 应用仅中文界面，裁剪依赖库翻译资源，显著缩小 resources.arsc
        resConfigs("zh", "en")
    }

    signingConfigs {
        create("release") {
            val keystore = loadKeystoreProperties()
            if (keystore != null) {
                storeFile = rootProject.file(keystore["storeFile"]!!)
                storePassword = keystore["storePassword"]
                keyAlias = keystore["keyAlias"]
                keyPassword = keystore["keyPassword"]
            }
        }
    }

    buildTypes {
        debug {
            // debug 使用独立包名，可和 release 同时安装在同一台设备上
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // 有 keystore.properties 时用正式签名;否则回退 debug 签名(便于无密钥环境构建)
            signingConfig = if (signingConfigs.getByName("release").storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // minSdk 33 起所有真实设备均为 64 位，release 只打 arm64-v8a（debug 保持全 ABI 兼容模拟器）
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            // benchmark 需要在真机（arm64）与 x86_64 模拟器上运行，保留两套 ABI
            ndk {
                abiFilters += "x86_64"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":player"))
    implementation(project(":feature:home"))
    implementation(project(":feature:playlist"))
    implementation(project(":feature:user"))
    implementation(project(":feature:lyrics"))
    implementation(project(":feature:player"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.profileinstaller)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
