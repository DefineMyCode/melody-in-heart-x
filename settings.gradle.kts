pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "melody-in-heart"
include(
    ":app",
    ":core:model",
    ":core:common",
    ":core:ui",
    ":domain",
    ":data",
    ":player",
    ":feature:home",
    ":feature:playlist",
    ":feature:user",
    ":feature:lyrics",
    ":feature:player",
    ":feature:settings",
    ":benchmark",
)
