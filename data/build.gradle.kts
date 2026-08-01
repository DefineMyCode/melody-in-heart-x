plugins {
    id("mihx.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.hilt)
    id("mihx.hilt")
    id("mihx.room")
}

android {
    namespace = "cn.com.dcsgo.mihx.data"
}

room {
    // Export Room database schemas so migrations can be validated against them.
    schemaDirectory("schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":domain"))

    implementation(libs.roomRuntime)
    implementation(libs.roomKtx)
    implementation(libs.datastorePreferences)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.kotlinxCoroutinesAndroid)
    implementation(libs.hiltAndroid)

    testImplementation(libs.androidxTestJUnit)
}
