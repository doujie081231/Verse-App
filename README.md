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
