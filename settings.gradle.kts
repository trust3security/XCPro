pluginManagement {
    includeBuild("build-logic")
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
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
include(":core:flight")
include(":core:geometry")
include(":core:time")
include(":core:ui")
include(":feature:igc")
include(":feature:forecast")
include(":feature:flight-runtime")
include(":feature:livefollow")
include(":feature:map")
include(":feature:map-runtime")
include(":feature:tasks")
include(":feature:weather")
include(":feature:traffic")
include(":feature:profile")
include(":feature:variometer")
include(":feature:weglide")
