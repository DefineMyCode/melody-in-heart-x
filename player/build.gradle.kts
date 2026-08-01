plugins {
    id("mihx.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("mihx.hilt")
}

android {
    namespace = "cn.com.dcsgo.mihx.player"
    lint {
        // :player is the Media3 kernel and intentionally uses @UnstableApi APIs; opt in
        // project-wide (see lint.xml) so UnsafeOptInUsageError does not fire on every usage.
        lintConfig = file("lint.xml")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":domain"))

    // Media3 types are part of :player's public API (the Service subclass, MediaController,
    // MediaItem, ExoPlayer) and must be 'api' so consumers (:app, :feature:player) can resolve
    // them on their classpath — otherwise lint fails with "must extend android.app.Service".
    api(libs.media3Exoplayer)
    api(libs.media3Session)
    api(libs.media3Common)
    api(libs.media3UiCompose)
    implementation(libs.jellyfinFfmpeg)
    implementation(libs.kotlinxCoroutinesAndroid)
    implementation(libs.hiltAndroid)
}
