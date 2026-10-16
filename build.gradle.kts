import cn.com.dcsgo.mihx.verify.ProductArchitectureVerification
import com.diffplug.gradle.spotless.SpotlessExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlinGradle {
        target("*.gradle.kts", "gradle/**/*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

    spotless {
        kotlin {
            target("src/**/*.kt")
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            trimTrailingWhitespace()
            endWithNewline()
        }
        format("androidXml") {
            target("src/**/*.xml")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    tasks.configureEach {
        if (name == "check") {
            dependsOn("spotlessCheck")
        }
    }
}

tasks.register("verifyProductArchitecture") {
    group = "verification"
    description = "Checks module boundaries and release privacy rules for the product architecture."

    doLast {
        // m-10（评审 2026-10-15）：规则本体抽到 buildSrc（ProductArchitectureVerification），
        // 任务只做调度；违规收集式一次报出全部问题；规则行为由 :buildSrc:test 单测覆盖。
        val violations = ProductArchitectureVerification.verify(rootDir)
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Product architecture violations (${violations.size}):\n\n" +
                    violations.joinToString("\n\n"),
            )
        }
    }
}

tasks.configureEach {
    if (name == "check") {
        dependsOn(
            "spotlessCheck",
            "verifyProductArchitecture",
        )
        dependsOn(subprojects.map { "${it.path}:check" })
    }
}
