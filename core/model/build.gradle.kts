plugins {
    id("mihx.jvm.library")
}

dependencies {
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinxSerializationJson)
    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotlinxCoroutinesTest)
}
