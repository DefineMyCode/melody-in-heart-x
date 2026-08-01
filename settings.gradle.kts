pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex(".*android.*")
                includeGroupByRegex(".*google.*")
                includeGroupByRegex(".*kotlin.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "melody-in-heart-x"

// ---- Application shell ----
include(":app")

// ---- Core layer ----
include(":core:model")
include(":core:common")
include(":core:ui")

// ---- Domain + Data ----
include(":domain")
include(":data")

// ---- Player (Media3) ----
include(":player")

// ---- Feature modules ----
include(":feature:home")
include(":feature:playlist")
include(":feature:user")
include(":feature:lyrics")
include(":feature:player")
include(":feature:settings")

// ---- Benchmark ----
include(":benchmark")
