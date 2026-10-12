pluginManagement {
    repositories {
        // KSP 2.3.x ??????????(??? google ???? 1.5.30),
        // ? GitHub google/ksp release artifacts.zip ????? Maven ????
        // ??????:dl.google.com ??????????(Read timed out),
        // ?????????? Google Maven / Maven Central ??
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
        maven("https://jitpack.io") // ?? com.github.steveice10:opennbt ? GitHub ????
    }
}

rootProject.name = "VerseExplorerX"

include(":VerseCore")
include(":VerseLibrary")
include(":VerseLauncher")
include(":VerseApp")
include(":JvmLauncher")
include(":LayerController")
include(":Terracotta")
include(":CardGrid")
include(":FclCore")
