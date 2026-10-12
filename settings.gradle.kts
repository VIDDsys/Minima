pluginManagement {
    repositories {
        google()
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

rootProject.name = "Minima"
include(":app")
include(":core:ui")
include(":core:data")
include(":core:network")
include(":feature:chat")
include(":feature:settings")
include(":core:agent")
