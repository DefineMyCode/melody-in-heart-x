package mihx.convention

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * verifyProductArchitecture — enforces the product architecture gate (rules A1..A7).
 * Runs as part of `check`. Fails the build on any violation.
 */
abstract class VerifyProductArchitecture : DefaultTask() {

    @get:InputFiles
    abstract val sources: Property<ConfigurableFileTree>

    @get:InputFiles
    abstract val manifests: Property<ConfigurableFileTree>

    @get:Input
    abstract val modulePath: Property<String>

    @TaskAction
    fun execute() {
        val violations = mutableListOf<String>()
        val path = modulePath.get()
        val type = moduleType(path)

        // A1 — unified root package
        sources.get().files.filter { it.name.endsWith(".kt") }.forEach { file ->
            val pkg = file.readText().lineSequence()
                .firstOrNull { it.trim().startsWith("package ") }
            if (pkg != null && !pkg.contains("package cn.com.dcsgo.mihx")) {
                violations += "[A1] ${file.path}: package must be under cn.com.dcsgo.mihx"
            }
        }

        // A2 — feature must not depend on :data (dependency inversion)
        if (type == "feature") {
            sources.get().files.forEach { file ->
                if (file.readText().contains("import cn.com.dcsgo.mihx.data")) {
                    violations += "[A2] ${file.path}: feature must not import cn.com.dcsgo.mihx.data"
                }
            }
        }

        // A3 — feature/domain must not import room/datastore implementations
        if (type == "feature" || type == "domain") {
            sources.get().files.forEach { file ->
                val t = file.readText()
                if (t.contains("import androidx.room") || t.contains("import androidx.datastore")) {
                    violations += "[A3] ${file.path}: must not import androidx.room / androidx.datastore"
                }
            }
        }

        // A5 — queue code must not dedupe by Song.id
        if (type == "player" || type == "coreModel" || type == "domain") {
            sources.get().files.forEach { file ->
                val t = file.readText()
                if (t.contains(".distinct(") || t.contains(".associateBy(") || t.contains(".toSet(")) {
                    violations += "[A5] ${file.path}: queue code must not dedupe by Song.id (.distinct/.associateBy/.toSet)"
                }
            }
        }

        // A4 — PlayerUiState construction only in ControllerPlaybackStateSynchronizer
        // (the data-class declaration file PlayerUiState.kt is excluded)
        val sync = sources.get().files.filter { it.name == "ControllerPlaybackStateSynchronizer.kt" }
        sources.get().files.filter { it.name.endsWith(".kt") && it.name != "PlayerUiState.kt" }.forEach { file ->
            if (file.readText().contains("PlayerUiState(") && file !in sync) {
                violations += "[A4] ${file.path}: PlayerUiState must only be constructed in ControllerPlaybackStateSynchronizer"
            }
        }

        // A6 — no INTERNET permission
        manifests.get().files.forEach { file ->
            if (file.readText().contains("android.permission.INTERNET")) {
                violations += "[A6] ${file.path}: must not declare INTERNET permission"
            }
        }

        // A7 — no NotificationCompat.Builder (use Media3 default notification)
        sources.get().files.forEach { file ->
            if (file.readText().contains("NotificationCompat.Builder")) {
                violations += "[A7] ${file.path}: must not use NotificationCompat.Builder"
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "verifyProductArchitecture FAILED:\n" + violations.joinToString("\n")
            )
        }
    }

    private fun moduleType(path: String): String = when {
        path == ":app" -> "app"
        path.startsWith(":core:model") -> "coreModel"
        path.startsWith(":core:common") -> "coreCommon"
        path.startsWith(":core:ui") -> "coreUi"
        path == ":domain" -> "domain"
        path == ":data" -> "data"
        path == ":player" -> "player"
        path.startsWith(":feature:") -> "feature"
        path == ":benchmark" -> "benchmark"
        else -> "other"
    }
}
