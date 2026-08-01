plugins {
    id("mihx.android.library")
    alias(libs.plugins.ksp)
    id("mihx.hilt")
}

android {
    namespace = "cn.com.dcsgo.mihx.player"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":domain"))

    implementation(libs.media3Exoplayer)
    implementation(libs.media3Session)
    implementation(libs.media3Common)
    implementation(libs.media3UiCompose)
    implementation(libs.jellyfinFfmpeg)
    implementation(libs.kotlinxCoroutinesAndroid)
    implementation(libs.hiltAndroid)
}
