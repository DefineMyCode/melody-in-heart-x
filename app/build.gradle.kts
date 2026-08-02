plugins {
    id("mihx.android.application")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("mihx.hilt")
}

android {
    namespace = "cn.com.dcsgo.mihx"

    defaultConfig {
        applicationId = "cn.com.dcsgo.mihx"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    // Core
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":player"))

    // Features
    implementation(project(":feature:home"))
    implementation(project(":feature:playlist"))
    implementation(project(":feature:user"))
    implementation(project(":feature:lyrics"))
    implementation(project(":feature:player"))
    implementation(project(":feature:settings"))

    // Compose / Material3
    implementation(platform(libs.composeBom))
    implementation(libs.activityCompose)
    implementation(libs.composeMaterial3)
    implementation(libs.composeMaterial3AdaptiveNavSuite)
    implementation(libs.composeMaterialIconsExtended)
    implementation(libs.composeUi)
    implementation(libs.composeUiToolingPreview)
    implementation(libs.composeFoundation)
    implementation(libs.composeRuntime)

    // Navigation
    implementation(libs.navigationCompose)

    // Lifecycle / Hilt
    implementation(libs.hiltAndroid)
    implementation(libs.hiltNavigationCompose)
    implementation(libs.lifecycleRuntimeKtx)
    implementation(libs.lifecycleViewmodelCompose)
    implementation(libs.lifecycleRuntimeCompose)
    implementation(libs.coreKtx)
    implementation(libs.coreSplashscreen)
    implementation(libs.kotlinxCoroutinesAndroid)
}
