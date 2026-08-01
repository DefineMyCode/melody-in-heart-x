plugins {
    id("mihx.android.library")
    alias(libs.plugins.ksp)
    id("mihx.hilt")
}

android {
    namespace = "cn.com.dcsgo.mihx.core.common"
}

dependencies {
    implementation(libs.kotlinxCoroutinesAndroid)
    implementation(libs.coreKtx)
    implementation(libs.hiltAndroid)
}
