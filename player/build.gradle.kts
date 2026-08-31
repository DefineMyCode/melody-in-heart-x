plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "cn.com.dcsgo.mihx.player"
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
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.media3.common)
    implementation(libs.media3.session)
    implementation(libs.exoplayer)
    implementation(libs.media3.ffmpeg.decoder)
    implementation(libs.tensorflow.lite)
    implementation(libs.hilt.android)
    implementation(libs.org.json)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
}
