plugins {
    id("mihx.jvm.library")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinxCoroutinesCore)
}
