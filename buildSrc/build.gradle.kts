plugins {
    kotlin("jvm") version "2.0.21"
}

repositories {
    mavenCentral()
}

// 统一 JVM target：CI/本机的 Gradle daemon 可能是 JDK 17 或 21，
// buildSrc 产物 target 17 两者均可加载（与项目 compileOptions 一致）
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // m-10（评审 2026-10-15）：verifyProductArchitecture 规则抽到 buildSrc 以便单测
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test> {
    useJUnit()
}
