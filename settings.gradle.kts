pluginManagement {
    repositories {
        // KSP 2.3.x 未同步到任何国内镜像（阿里云 google 镜像最高 1.5.30），
        // 从 GitHub google/ksp release artifacts.zip 解出的本地 Maven 仓库兜底
        maven("file:///C:/verse-local-maven")
        // 国内镜像优先：dl.google.com 在本机网络间歇性超时（Read timed out），
        // 阿里云镜像可稳定访问 Google Maven / Maven Central 内容
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
        maven("file:///C:/verse-local-maven")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
        maven("https://jitpack.io") // 解析 com.github.steveice10:opennbt 等 GitHub 托管依赖
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
include(":FclCore")
