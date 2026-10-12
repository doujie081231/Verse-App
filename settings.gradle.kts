pluginManagement {
    repositories {
        // ??????(CI ????);??????????????
        // KSP 2.3.x ????? Maven Central,???????
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
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
