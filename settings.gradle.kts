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

rootProject.name = "VerseExplorerX"

include(":VerseCore")
include(":VerseLibrary")
include(":VerseLauncher")
include(":VerseApp")
include(":Terracotta")
include(":LayerController")
