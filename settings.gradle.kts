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

rootProject.name = "XCPro"
include(":app")
include(":dfcards-library")
include(":core:common")
include(":core:geometry")
include(":core:time")
include(":core:ui")
include(":feature:map")
include(":feature:profile")
include(":feature:variometer")
