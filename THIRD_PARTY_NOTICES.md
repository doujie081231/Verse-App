# 第三方来源索引

本文为仓库内已有来源说明提供入口，不替代源码中的版权或许可证声明，也不为整个仓库指定新的统一许可证。

| 组件或适配层 | 代码与现有说明 |
| --- | --- |
| FCL 核心的本地分支 | `FclCore/`；结构与改动记录见 [VERSE-CHANGES.md](FclCore/VERSE-CHANGES.md) |
| FCL/HMCL 启动与工具代码 | `VerseLauncher/src/main/java/com/verse/explorer/launcher/fcl/` 及 `FclCore/` 中的相关实现；多处文件保留 Hello Minecraft! Launcher 的版权与 GPL 声明 |
| PojavLauncher 输入和控件适配 | `VerseApp/src/main/java/net/kdt/pojavlaunch/` 及相关桥接代码；保留原有来源标注与兼容包名 |
| SDL Android 集成 | `FclCore/src/main/java/org/libsdl/app/`；文件头包含 SDL 来源和 zlib 许可说明 |
| Terracotta Android 接口 | `Terracotta/src/main/java/net/burningtnt/terracotta/` 与该模块的原生库 |
| 预编译原生库 | 各模块的 `src/main/jniLibs/`；[现有来源记录](VerseLauncher/src/main/jniLibs/README.md)记载了部分库的取用来源 |
| 运行时与游戏依赖 | `VerseApp/libs/`、`VerseApp/src/main/assets/app_runtime/` 和各模块 Gradle 依赖声明 |

源码文件头、库发布物携带的声明以及对应依赖的许可文件应一并保留。不同预编译库可能使用不同许可证，不能将某一个项目的许可证直接当作所有二进制组件的统一许可。

新增或更新第三方组件时，记录上游项目、版本或提交、许可信息，以及本地适配位置。仓库尚无根目录统一许可证文件；确定整体发布许可和补齐依赖声明时，需要逐项核对现有源码与发布物中的信息。
