plugins {
    id("mihx.jvm.library")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.javaxInject)
    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinxCoroutinesTest)
}
