import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * 读取根目录 keystore.properties(已 gitignore):storeFile/storePassword/keyAlias/keyPassword。
 * 文件不存在时返回 null。release 默认回退 debug 签名以方便无密钥环境构建；
 * 若构建时传入 -PrequireReleaseSigning=true（发布/CI 场景），缺失密钥将直接构建失败，避免误出 debug 签名的“正式包”。
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
        versionCode = 29
        versionName = "3.5.1"

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

    // release 签名决策与 requireReleaseSigning 开关放在 buildTypes 之外：
    // verifyProductArchitecture 用正则按首个闭合花括号截断 release 块，块内嵌套花括号会把 isMinifyEnabled 检查截断
    val requireReleaseSigning = (project.findProperty("requireReleaseSigning") as? String)
        ?.toBooleanStrictOrNull() ?: false
    val releaseSigningConfig = run {
        val releaseConfig = signingConfigs.getByName("release")
        when {
            releaseConfig.storeFile != null -> releaseConfig
            requireReleaseSigning -> throw org.gradle.api.GradleException(
                "发布构建要求正式签名，但未找到 keystore.properties。" +
                    "请在发布/CI 环境提供签名配置，或本地用 ./gradlew assembleRelease -PrequireReleaseSigning=false 跳过。",
            )
            else -> {
                project.logger.warn(
                    "未找到 keystore.properties，release 回退使用 debug 签名（仅限本地验证，产物不可发布）。" +
                        "正式发布请传 -PrequireReleaseSigning=true。",
                )
                signingConfigs.getByName("debug")
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
            // 正式签名策略（P3-13）：决策逻辑在 release 块外（见上方 releaseSigningConfig），
            // 块内仅一行赋值——嵌套花括号会导致 verifyProductArchitecture 的正则检查截断失败
            signingConfig = releaseSigningConfig
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
    implementation(libs.workmanager)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
