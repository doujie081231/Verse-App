# Verse Explorer X

**Verse Explorer X** 是一款运行于 Android 平台的 [Minecraft: Java Edition](https://www.minecraft.net/) 启动器，基于 FCL 内核深度定制重构，覆盖从版本下载、模组管理到联机游戏的全流程体验。

> 当前版本：**1.0.0 公测版**（arm64-v8a） · [下载最新版](https://github.com/doujie081231/Verse-App/releases/latest)

## ✨ 功能特性

### 版本与启动
- 原版全版本下载安装，Mojang 官方 + BMCLAPI 镜像多源竞速加速
- 一键安装 Forge / Fabric / NeoForge / Quilt / OptiFine 加载器
- 版本隔离、多游戏目录、版本重命名 / 导出 / 清理
- 启动链完整性校验：损坏的库文件自动重新下载

### 模组与资源
- Modrinth 模组浏览 / 搜索 / 下载，中文汉化名
- 模组更新检查与冲突检测（重复 modid、缺失前置）
- 材质包 / 光影包 / 数据包资源管理
- 整合包导入：CurseForge / Modrinth (mrpack)，支持外部分享一键导入

### 存档与联机
- 存档备份 / 恢复 / 导入 / 导出
- 陶瓦联机（Terracotta VPN）与局域网联机

### 账户与皮肤
- Microsoft 正版登录、外置登录（authlib-injector）、离线账户
- 皮肤上传与账户头像展示

### 画面与操控
- 内置渲染器 + 渲染器插件体系（自动识别已安装的渲染器插件）
- 虚拟触控按键、ZL2 布局格式互通、触控布局编辑器
- 游戏内控制面板：分辨率比例调节、控制台与实时日志

### 基础设施
- Java 运行时管理：Mojang JRE 自动下载、本机 Java 导入
- 下载中心：多任务并发、断点续传
- 崩溃分析与日志反馈
- **启动器内更新**：Gitee 主源 + GitHub 镜像，应用内下载安装

## 📥 下载安装

前往 [Releases](https://github.com/doujie081231/Verse-App/releases/latest) 下载 `Verse-x.y.z-arm64-v8a.apk` 安装即可。

- 系统要求：Android 8.0（API 26）及以上，arm64-v8a 设备
- 也可在启动器内「设置 → 检查更新」直接升级

## 🛠️ 从源码构建

```bash
git clone https://github.com/doujie081231/Verse-App.git
cd Verse-App
./gradlew -Darch=arm64 :VerseApp:assembleRelease
```

- 需要 JDK 17 与 Android SDK（AGP 8.13）
- 模块结构：`VerseApp`（UI/应用层）· `VerseCore`（数据/安装/启动编排）· `VerseLauncher`（启动 fork）· `FclCore`（FCL 内核 vendored）· `LayerController` / `Terracotta` 等

## 🧭 相关仓库

- 安装包镜像（Gitee）：[doujie081231/verse-apk](https://gitee.com/doujie081231/verse-apk)
- 官网与反馈：[verselauncher.cn](https://verselauncher.cn)

## 📄 开源协议

本项目基于 **[GPL-3.0](LICENSE)** 协议开源。

> This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

本项目使用了 [FCL (Fold Craft Launcher)](https://github.com/FCL-Team/FoldCraftLauncher) 的内核代码，同样以 GPL-3.0 协议开源，感谢 FCL-Team 及所有上游贡献者。

---

Verse 的 Android 客户端，使用 Kotlin、Java 与 Jetpack Compose 构建，包含 Minecraft 版本管理、账号登录、资源下载、运行时部署和游戏内操控。

## 代码结构

| 模块 | 职责 |
| --- | --- |
| `VerseApp` | 应用入口、Compose 页面、游戏界面与 Android 宿主集成 |
| `VerseCore` | 账号、版本、下载、安装、模组与资源管理 |
| `VerseLauncher` | 运行后端、FCL 安装适配、渲染器和原生桥封装 |
| `JvmLauncher` | JVM 加载与原生接口 |
| `VerseLibrary` | 公共 Compose 组件与主题 |
| `LayerController` | 触控布局、控件数据与事件处理 |
| `CardGrid` | 卡片网格布局与拖动、缩放交互 |
| `Terracotta` | 联机核心的 Android 接口与原生库 |
| `FclCore` | FCL 核心的本地分支，以及下载、安装和输入桥接组件 |

游戏启动统一经过 `LaunchRequest` → `GameSession` → Launcher 运行后端；Core 定义安装端口，Launcher 适配 FCL。模块关系与生命周期见 [架构说明](docs/ARCHITECTURE.md)，保留的协议见 [兼容契约](docs/COMPATIBILITY.md)。第三方实现的维护说明见 [FclCore 本地改动账本](FclCore/VERSE-CHANGES.md)。

## 本地构建

准备 JDK 17 和 Android SDK，并将 `JAVA_HOME` 指向 JDK。Android SDK 路径可通过未入库的 `local.properties` 设置：

```properties
sdk.dir=C:/Users/your-name/AppData/Local/Android/Sdk
```

当前工程配置使用 Gradle Wrapper 9.1.0、Android SDK 37、NDK `29.0.14206865` 和 CMake `3.22.1`。SDK 安装命令及 CI 执行顺序见 [构建工作流](.github/workflows/build-android.yml)。

在仓库根目录运行：

```powershell
# 检查架构边界、第三方漂移与检查器自身测试。
python -m unittest discover -s tools -p 'test_*.py'
python tools/architecture_check.py
python tools/fclcore_drift.py --check

# 运行核心、启动适配和应用模块的本地单元测试。
.\gradlew.bat :VerseCore:testDebugUnitTest :VerseLauncher:testDebugUnitTest :VerseApp:testDebugUnitTest -Darch=arm64

# 构建 ARM64 调试包。
.\gradlew.bat :VerseApp:assembleDebug -Darch=arm64
```

Linux 和 macOS 使用 `./gradlew`。调试包输出到 `VerseApp/build/outputs/apk/debug/`。构建可能需要下载 Gradle、Android 依赖与原生构建工具。

## 维护与贡献

- [贡献指南](CONTRIBUTING.md)：修改范围、验证方式与提交要求。
- [注释规范](docs/COMMENT_STYLE.md)：说明设计原因、兼容约束与公共接口。
- [第三方来源索引](THIRD_PARTY_NOTICES.md)：定位上游源码、适配层与原生组件的已有声明。
- [架构验证报告](docs/ARCHITECTURE_VALIDATION.md)：构建基线、自动化结果和待完成的真机验收。

第三方源码和依赖沿用各自的许可要求。提交变更时保留原有版权、许可证和来源说明；本仓库的维护文档不替代这些声明。
