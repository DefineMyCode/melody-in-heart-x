plugins {
    id("mihx.android.library.compose")
    alias(libs.plugins.ksp)
    id("mihx.hilt")
}

android {
    namespace = "cn.com.dcsgo.mihx.feature.player"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":domain"))
    implementation(project(":player"))

    implementation(platform(libs.composeBom))
    implementation(libs.activityCompose)
    implementation(libs.composeMaterial3)
    implementation(libs.composeUi)
    implementation(libs.composeFoundation)
    implementation(libs.composeRuntime)
    implementation(libs.composeUiToolingPreview)
    implementation(libs.composeMaterialIconsExtended)
    implementation(libs.navigationCompose)
    implementation(libs.lifecycleViewmodelCompose)
    implementation(libs.lifecycleRuntimeCompose)
    implementation(libs.kotlinxCoroutinesAndroid)
    implementation(libs.hiltAndroid)
    implementation(libs.hiltNavigationCompose)
}
