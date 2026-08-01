plugins {
    id("mihx.jvm.library")
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.javaxInject)
    implementation(libs.kotlinxSerializationJson)
    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinxCoroutinesTest)
}
