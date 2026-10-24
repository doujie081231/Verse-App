# 兼容契约

## 启动协议

普通入口、快捷入口和崩溃重启均使用 `LaunchRequestCodec` / `LaunchIntentCodec`。旧 Intent 不需要补齐新增字段；缺少必需项返回失败，不猜测版本或目录。

| 旧键 | 缺省值 |
| --- | --- |
| `versionId`、`gameDir` | 必需，沿用字符串协议 |
| `javaHome`、`authlibServerUrl` | null |
| `javaVersion` | 0 |
| `maxMemory` / `minMemory` | 2048 / 512 MB |
| `gcType`、`customJvmArgs` | `G1GC`、空字符串 |
| `width` / `height` | 854 / 480 |
| `renderer`、`launchMethod` | `gl4es`、`pojav` |
| `username`、`uuid` | `Player`、32 个 0 |
| `multiplayerMode` | false |
| `accessToken`、`userType` | `0`、`mojang` |

额外的 `TERRACOTTA_PLAYER`、联机字段和既有资源目录键保持可传递，解码器不把它们误当作必需字段。令牌不进入请求的 `toString` 或验证报告。

版本独立设置继续优先于全局设置；自动内存仍回到原有全局计算，空渲染器仍回退，独立配置中的空 JVM 参数表示主动清空。自动 Java 选择、版本继承判断和渲染器适配策略保留原有规则。

## 进程与原生接口

| 位置 | 用途 |
| --- | --- |
| 应用主进程中的 `GameActivity` | 当前游戏宿主与输入、Surface 集成 |
| `:crash` | 崩溃报告页面 |
| `:jvm` 的 `ProcessService` | 安装处理器；不是第二条游戏启动链 |
| `DownloadService` | 下载前台通知与进程优先级 |

保留原有 JNI 包名、类名与签名，尤其是 `FCLBridge`、两个 `CallbackBridge`、`ZLBridge`、`SdlBridge` 和 `org.libsdl.app`。`ZLBridge` 仍有 C++ 导出引用，不能因为旧 MinecraftLauncher 删除而顺带删掉。

`com.tungsten.fcl.setting.MenuSetting` 实际由 VerseApp 提供；Launcher 通过 `RenderSettings` 接口读取所需值，不能反向依赖 App。`net.kdt.pojavlaunch` 等包承担旧控件和宿主协议，保留在逐符号白名单中。

JVM 仍要求冷重启；Surface 销毁仍停止游戏。本次不更改窗口/Surface 策略或原生停止的语义，仅统一调用所有权和重复调用保护。

## 用户数据和渲染器

继续使用现有账号数据库、SharedPreferences、`menu_setting.json`、版本独立设置 JSON 及触控布局格式。版本、libraries、assets、natives 的相对目录保持不变，未迁移存储框架或用户文件。

运行时管理保留 Java 8 / 17 / 21 / 25。内置渲染器标识 `gl4es`、`nggl4es`、`zink`、`freedreno`、`virgl`、`vgpu` 和插件包名协议保留；GLFW/SDL 路径仍由既有规则选择。上述兼容项需要在支持相应 GPU/运行时的 ARM64 设备验证，构建成功不等同于所有组合均可运行。

## 删除旧链的核查范围

删除前检查了 Kotlin/Java 调用、Manifest、混淆规则、反射字符串和 C/C++ 引用。移除 GameService、MinecraftLauncher、独占的旧参数/类路径/环境辅助及 GameWindow，保留仍被主链、Java 页面和 JNI 使用的同名或兼容接口。App 自身的 `launch/JvmArgsBuilder.kt` 用于 Java 工具页面，仍然保留。
