pluginManagement {
    repositories {
        // 官方仓库优先(镜像仅作国内补充);声明顺序经过 CI 验证
        // KSP 2.3.x 已发布到 Maven Central,gradlePluginPortal 作为兜底
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
        maven("https://jitpack.io") // 承载 com.github.steveice10:opennbt 等 GitHub 托管依赖
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
