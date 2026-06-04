pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Koml"

include(":core")
include(":engine-llama")
include(":storage")
include(":download")
include(":registry")
include(":samples-android")
include(":samples-desktop")
