plugins {
    id("mihx.android.library.compose")
}

android {
    namespace = "cn.com.dcsgo.mihx.core.ui"
}

dependencies {
    implementation(project(":core:model"))
    implementation(platform(libs.composeBom))
    implementation(libs.composeMaterial3)
    implementation(libs.composeUi)
    implementation(libs.composeFoundation)
    implementation(libs.composeRuntime)
    implementation(libs.composeUiToolingPreview)
    implementation(libs.kotlinxCoroutinesAndroid)
    implementation(libs.coilCompose)
}
