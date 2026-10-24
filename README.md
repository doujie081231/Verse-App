# Verse Android

Verse 的 Android 客户端，使用 Kotlin、Java 与 Jetpack Compose 构建，包含 Minecraft 版本管理、账号登录、资源下载、运行时部署和游戏内操控。

## 代码结构

| 模块 | 职责 |
| --- | --- |
| `VerseApp` | 应用入口、Compose 页面、游戏界面与 Android 宿主集成 |
| `VerseCore` | 账号、版本、下载、安装、模组与资源管理 |
| `VerseLauncher` | 启动参数、类路径、渲染器和游戏启动链 |
| `JvmLauncher` | JVM 加载与原生接口 |
| `VerseLibrary` | 公共 Compose 组件与主题 |
| `LayerController` | 触控布局、控件数据与事件处理 |
| `CardGrid` | 卡片网格布局与拖动、缩放交互 |
| `Terracotta` | 联机核心的 Android 接口与原生库 |
| `FclCore` | FCL 核心的本地分支，以及下载、安装和输入桥接组件 |

安装、下载与游戏启动使用不同的适配层。修改启动行为前，先确认调用的是 `VerseLauncher` 中的启动实现，还是 `FclCore` 中的安装与下载实现；维护说明见 [FclCore 本地改动账本](FclCore/VERSE-CHANGES.md)。

## 本地构建

准备 JDK 17 和 Android SDK，并将 `JAVA_HOME` 指向 JDK。Android SDK 路径可通过未入库的 `local.properties` 设置：

```properties
sdk.dir=C:/Users/your-name/AppData/Local/Android/Sdk
```

当前工程配置使用 Gradle Wrapper 9.1.0、Android SDK 37、NDK `29.0.14206865` 和 CMake `3.22.1`。SDK 安装命令及 CI 执行顺序见 [构建工作流](.github/workflows/build-android.yml)。

在仓库根目录运行：

```powershell
# 运行应用与核心模块的本地单元测试。
.\gradlew.bat :VerseCore:testDebugUnitTest :VerseApp:testDebugUnitTest -Darch=arm64

# 构建 ARM64 调试包。
.\gradlew.bat :VerseApp:assembleDebug -Darch=arm64
```

Linux 和 macOS 使用 `./gradlew`。调试包输出到 `VerseApp/build/outputs/apk/debug/`。构建可能需要下载 Gradle、Android 依赖与原生构建工具。

## 维护与贡献

- [贡献指南](CONTRIBUTING.md)：修改范围、验证方式与提交要求。
- [注释规范](docs/COMMENT_STYLE.md)：说明设计原因、兼容约束与公共接口。
- [第三方来源索引](THIRD_PARTY_NOTICES.md)：定位上游源码、适配层与原生组件的已有声明。

第三方源码和依赖沿用各自的许可要求。提交变更时保留原有版权、许可证和来源说明；本仓库的维护文档不替代这些声明。
