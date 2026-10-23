# Verse Explorer X 交接文档（给接手的 AI）

> 更新时间：2026-10-23（Asia/Shanghai）。
> 本文档由最近一次会话重写，合并了旧版交接文档（2026-10-16，2.4.70 时期）的核心信息与 2026-10-22～23 会话的全部记忆。旧文档的任务型内容已过期，其仍有效的环境/模拟器/历史结论保留在第 10 节。
>
> **接手阅读顺序：第 0 节 → 第 3 节 → 第 4 节 → 第 7 节 → 第 12 节。**

---

## 0. 当前状态速览

- 项目：Android Minecraft 启动器 **Verse Explorer X**，包名 `com.verse.explorer`，命名空间 `com.verse.explorer.app`。工作目录 `d:\Verse Explorer X\Verse Android`。
- 当前版本：**0.0.8（versionCode 37）**，见 [build.gradle.kts](<file:///d:/Verse Explorer X/Verse Android/VerseApp/build.gradle.kts#L27-L32>)。
- release 构建已设 `isDebuggable = false`，用于消除 Android 16/17 的「可调试应用 / 16 KB ELF 对齐」兼容性弹窗，见 [build.gradle.kts](<file:///d:/Verse Explorer X/Verse Android/VerseApp/build.gradle.kts#L56-L67>)。
- git：最后一次提交是 `9b834ef`（内测版 0.0.4，versionCode 30）。**0.0.5～0.0.8 的改动全部未提交**（工作区约 60 个文件修改）。未跟踪的重要新文件：
  - `VerseApp/src/main/java/com/verse/explorer/app/game/TouchControllerProxy.kt`
  - `VerseLauncher/src/main/java/com/verse/explorer/launcher/renderer/RendererPluginManager.kt`
  - `VerseApp/src/main/java/com/verse/explorer/app/documents/`
  - `AI交接文档.md`、`.verify/`、`promo-video/`、`soup/`
- 最新构建产物：`d:\Verse Explorer X\远程传输\Verse-0.0.8-relaunchfix-arm64-v8a.apk`（189,647,107 字节，2026-10-23 19:01），已签名。远程传输目录里目前只有这一个包。
- 真机：序列号 `3B1F5PE9ZUZ69NZH`（Android 16+，页面大小 4096）。adb 会中途掉线，重连后先 `adb devices` 确认再装。
- 当前进度：二次启动崩溃修复已完成并出包；**真机回归尚未做**，见第 7 节。

---

## 1. 项目结构与关键模块

| 模块 | 作用 |
|---|---|
| `VerseApp` | Compose UI、主页卡片网格、设置、资源下载、游戏 Activity、控制面板、崩溃分析页 |
| `VerseCore` | 版本管理、账户、Java 运行时、资源仓库、下载、整合包安装等核心逻辑 |
| `FclCore` | FCL 核心源码副本（`com.tungsten.fclcore` 等），包含 LWJGL 兼容层、SDL 桥、启动器基类 |
| `VerseLauncher` | 启动链：`DefaultLauncher`、`FCLauncher`、渲染器管理、环境变量注入 |
| `CardGrid` | 自包含的主页卡片网格引擎（推箱式缩放、拖动让位、最近空位打包） |
| `JvmLauncher` | JNI + OpenJDK JVM 启动/销毁（`jvmlauncher.cpp`） |
| `Terracotta` | 陶瓦联机相关 |
| `VerseApp/src/main/java/net/kdt/pojavlaunch/**` | Pojav 触控模块宿主适配层（ControlLayout/ControlButton/TouchCharInput 等） |
| `VerseApp/src/main/java/com/tungsten/fcl/**` | FCL 控制面板与设置（`MenuSetting`、`InGameControlOverlay` 等） |

参考源码：
- FCL 本地仓库：`.fcl-repo\FCL\...`（完整，可直接读，不必联网）。
- ZL2 本地源码：`d:\Verse Explorer X\zl2-src`（Zalith Launcher 2）。
- PCL：用户要求下载速度/部分交互以 PCL 为准，但代码注释不得写来源。

---

## 2. 环境、构建、签名、安装、发布

### 2.1 构建

```powershell
$env:JAVA_HOME="D:\verse-android-sdk\jdk17\jdk-17.0.2"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
# arm64 发布包
.\gradlew.bat -Darch=arm64 :VerseApp:assembleRelease --no-configuration-cache --console=plain
# x86_64 模拟器测试包（如仍需模拟器）
.\gradlew.bat -Darch=x86_64 :VerseApp:assembleRelease --no-configuration-cache --console=plain
```

- `JAVA_HOME` 必须是 `...\jdk17\jdk-17.0.2`，不是 `jdk17` 根目录。
- `-Darch=arm64` 生成 `VerseApp\build\outputs\apk\release\Verse-0.0.8-release-arm64-v8a.apk`。
- 不要用 `assembleArm64Release`（任务不存在）。
- 必须带 `--no-configuration-cache`：配置缓存会把 `arch=all` 的配置缓存住，导致没有 split 任务、产物架构不对。
- **每次关键改动/构建前递增 `versionCode`**（用户硬性要求），否则覆盖安装会失败或系统不替换。

### 2.2 签名（release 无内建签名配置）

```powershell
$bt="D:\verse-android-sdk\build-tools\35.0.0"
& "$bt\zipalign.exe" -f 4 "VerseApp\build\outputs\apk\release\Verse-0.0.8-release-arm64-v8a.apk" "$env:TEMP\a.apk"
& "$bt\apksigner.bat" sign --ks "VerseApp\release-new.keystore" --ks-key-alias verse --ks-pass pass:verse2026 --out "$env:TEMP\s.apk" "$env:TEMP\a.apk"
& "$bt\apksigner.bat" verify --print-certs "$env:TEMP\s.apk"
```

- keystore：`VerseApp\release-new.keystore`；alias `verse`；store 密码与 key 密码都是 `verse2026`。
- **不要写 `--key-pass pass:verse`**（曾因此签名失败）；省略 `--key-pass` 可正常签，key 密码与 store 密码一致。
- 签名后必须 `verify` 再分发：同一条 PowerShell 命令里后续 adb 失败时，`signed.apk` 可能损坏（EOCD not found）。
- 对外发布包补三方案签名：`--v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true`。
- 当前证书 SHA-256：`8325ef10569ffae560ad1d42b8d7e0b12bb68b6b2a2ee09af59b8a3aeb9ce783`。

### 2.3 安装

```powershell
adb devices
adb -s 3B1F5PE9ZUZ69NZH install -r "$env:TEMP\s.apk"
```

- adb 路径：`D:\verse-android-sdk\platform-tools\adb.exe`（不在 PATH 时用绝对路径）。
- 真机掉线是常态：长构建/长时间操作后先看 `adb devices`，掉线就把包复制到 `远程传输` 等重连。

### 2.4 产物放置规则（用户硬性要求）

- 设备不在本地/需远程下载时，构建产物放 `d:\Verse Explorer X\远程传输\`。
- **放新包前删除该目录里的旧构建**，文件名带版本号，使用已 zipalign + 已签名的包。
- 不要放在其他随手建的目录里。

### 2.5 发布

- 只有用户明确要求发布时才发布。发布前：升版本号 → arm64 release → 签名 → 清理注释 → 打包 → 上传。
- 用户要求：发布时使用镜像上传到仓库，无需代理。
- 发布链历史上用过：commit → tag → push（镜像）→ GitHub REST API 建 Release → `curl.exe --data-binary` 传 APK。GitHub 直连波动时曾走 Git Data API（blobs→trees→commits→PATCH refs），本机曾可用 21080 代理推送（仅命令参数，不改 git 配置）。
- 旧文档里的远端 main 哈希、Release ID、发布脚本路径均已过期，**不要照抄**；发布前重新确认远端状态。

---

## 3. 用户硬性规则（长期，必须遵守）

### 3.1 工作方式

1. **不要猜、不要自研**：问题必须看日志/实测定位；修启动、下载、安装、触控等优先以 FCL/ZL2/PCL 的真实实现为准。
2. 软件标准以 **Pojav 启动器** 为准则；修复问题参照 **PCL**；启动链/触控迁移参照 **FCL/ZL2**。
3. 软件问题必须用**模拟器或真机实测**验证，禁止纸上推断后直接交付。
4. 构建/安装/下载过程要**主动轮询状态**，不要后台静默执行。
5. 崩溃不要直接退出：必须显示**崩溃分析页**（右侧有重启、退出、导出日志等操作），样式与 ZL2 一致，分析逻辑用 VersePC2 的规则。
6. 任务完成后如用户要求则执行关机；不要自行决定。
7. 发布/提交 git 必须等用户明确要求；用户没要求就不要 commit/push/tag。

### 3.2 代码与注释

8. 代码注释用**中文**。
9. 注释里**不得出现**「参考 / 迁移自 / 移植自 / 借鉴 / 照搬 / 对位 ZL2/FCL/PCL」等来源字样。发布内测前必须清理干净。
10. 模组管理、账户模型动作等页面的注释要写「自研」，不要写来源。
11. 不要包含参考启动器的痕迹（包括注释、变量名、文件头说明）。
12. 不要写测试用的临时注释/调试代码到发布包里；诊断打点可留可删，但发布前检查。

### 3.3 构建与设备

13. 构建必须指定 `arm64` 架构（发布包）。
14. 关键改动必须递增 `versionCode`。
15. 测试用模拟器时不要用 900×1500 规格；模拟器数据盘已扩到 16GB（旧资料）。
16. 安装/构建产物放 `d:\Verse Explorer X\远程传输\`，删旧包。
17. 侧边栏/页面切换/液态玻璃等视觉要求见第 10 节历史结论，不要擅自改回旧样式。

### 3.4 已明确否决的方案（不要再做）

- 不要再内置 MobileGlues 渲染器；改为识别/导入**渲染器插件**。
- 不要再给游戏分辨率加「自定义宽高/分辨率规则」；用户明确选择**纯百分比滑条**（25%～300%）。
- 不要用 `Popup` 做设置页下拉（历史上偏移/漂移严重），一律用**内联展开**。
- 不要在启动器同进程里尝试二次创建 JVM；退出/重启必须**杀进程 + 冷启动**。
- 不要用 `runBlocking`/猜测方式补启动链；按 FCL 的 26.3 启动链缺啥补啥。

---

## 4. 最近会话改动明细（0.0.5～0.0.8）

### 4.1 忽略刘海后主页布局贴到最上面

**现象**：设置里打开「忽略刘海（全屏）」后，主页内容挤到屏幕最顶部，与关闭时不一致。

**根因**：全屏时状态栏隐藏，`WindowInsets.statusBars` 内边距归零，主页 `Column` 的 `statusBarsPadding` 失效。

**改动**：[HomeScreen.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/ui/HomeScreen.kt#L190-L212>)

```kotlin
val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
val topPadding = if (statusBarTop < 24.dp) 24.dp else statusBarTop
Column(modifier = Modifier.fillMaxSize().padding(top = topPadding) ...)
```

- 保留最小 24dp 顶部留白，全屏开关前后主页顶部间距一致。
- 同时 `MainActivity` 根 `Surface` 在忽略刘海模式下不加 `displayCutout` padding；关闭时按 cutout 内缩并填黑，见 [MainActivity.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/MainActivity.kt#L114-L121>)。

**验证**：已修改并核对；真机/模拟器视觉已验证（此前会话）。

### 4.2 后台问题反馈相关（旋转、触控、崩溃）

本版本线此前处理的后台 Android 反馈主要包括：

- **屏幕旋转**：主界面、GameActivity、CrashReportActivity、布局编辑器统一 `sensorLandscape`，避免横屏游戏中转竖屏导致布局错乱。见 [AndroidManifest.xml](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/AndroidManifest.xml#L62-L140>) 与 `MainActivity.requestedOrientation`。
- **触控/按键卡键**：按住按钮滑出边界后旧逻辑不再转发 UP 导致按键永久按下；已用「指针捕获集合」在 DOWN/POINTER_DOWN 记录指针 id，后续事件按捕获∪当前命中转发，POINTER_UP/UP/CANCEL 后再移除。相关在 `GameActivity.dispatchTouchEvent` 与 `GameInputHandler`。
- **崩溃分析**：`CrashReportActivity` 独立 `:crash` 进程，游戏崩溃与应用自身崩溃共用；`VerseApplication.isCrashProcess` 防递归。
- **TouchController 代理**：新增 [TouchControllerProxy.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/game/TouchControllerProxy.kt>)，通过 Unix socket 把指针转发给已安装的 TouchController 模组（`top.fifthlight.touchcontroller.proxy.client`），版本内存在对应模组时启动代理。真机效果尚未由本次会话验证。

### 4.3 移除内置 MobileGlues，改为渲染器插件体系

**用户要求**：移除启动器内置的 MG 渲染器；识别手机已安装的渲染器插件（例如用户自己安装的 MobileGlues APK），并支持导入。

**实现**：
- 删除内置 MG 相关代码/资源，`RendererManager`、`Renderer.java`、`VersionSettingsScreen`、`DefaultLauncher`、`FCLauncher` 不再提供内置 MG 选项。
- 新增 [RendererPluginManager.kt](<file:///d:/Verse Explorer X/Verse Android/VerseLauncher/src/main/java/com/verse/explorer/launcher/renderer/RendererPluginManager.kt#L22-L68>)：
  - 通过 `PackageManager.queryIntentActivities(Intent.ACTION_MAIN)` 扫描已安装应用（跳过自身与系统应用）。
  - 读取元数据：`fclPlugin_V2`（FCL 新格式）、`fclPlugin`（旧格式）、`zalithRendererPlugin`（读取 `renderer/des/pojavEnv` 等字段）。
  - 插件唯一 ID = 包名；`find()` 兼容旧设置里的 `mobileglues` 标识，映射到已安装的同类插件。
  - 每次启动前强制刷新，避免新装插件不可见。
- Manifest 增加包可见性 `queries`（ACTION_MAIN + 目录 VIEW），见 [AndroidManifest.xml](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/AndroidManifest.xml#L29-L38>)；导入 APK 使用 FileProvider 路径。
- 启动链注入插件：`nativeLibraryDir`、GL/EGL 库名、`pojavEnv` 环境变量；启动器渲染器下拉 = 内置渲染器 + 插件列表动态合并。

**验证**：模拟器用假 MG 插件验证识别与列表显示通过；真机安装/启动通过。**26.3 仍走 Vulkan/vkshim**，插件渲染器主要用于 GL 路径与后续扩展。

### 4.4 Android 16/17 兼容性弹窗（16 KB ELF 对齐）

**现象**：真机安装/启动时系统弹「Android 应用兼容性」/16 KB 页对齐警告。

**根因**：release 构建曾 `isDebuggable = true`，Android 16+ 对可调试应用弹该警告；同时部分预编译 native 库是 4 KB 对齐。

**处理**：
- `VerseApp/build.gradle.kts` 的 release 改为 `isDebuggable = false`，见 [build.gradle.kts](<file:///d:/Verse Explorer X/Verse Android/VerseApp/build.gradle.kts#L56-L67>)。
- 真机页面大小 = 4096，4 KB 对齐库可正常运行；弹窗消失。
- **长期问题**：要彻底消除 16 KB 对齐提示，需要 NDK r28+ 重建所有 native 库并做 16 KB 对齐；当前不是阻塞项，见第 8 节。

**验证**：真机安装后弹窗不再出现。

### 4.5 游戏内分辨率调整（纯百分比）

**用户要求**：参照 ZL2 给游戏内控制面板加分辨率调整；先按百分比调节，最终明确选择「纯百分比滑条」（不要自定义宽高、不要预设档位）。

**实现**：
- 设置存储：`MenuSetting.windowScale`（`menu_setting.json`，默认 `1.0`，范围 25%～300%），见 [MenuSetting.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/tungsten/fcl/setting/MenuSetting.kt#L20-L29>)。
- 尺寸解析：

```kotlin
fun resolveRenderSize(screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
    val factor = windowScale
    val width = ((screenWidth + cursorOffset) * factor).toInt().coerceAtLeast(1)
    val height = (screenHeight * factor).toInt().coerceAtLeast(1)
    return width to height
}
```

- 控制面板「分辨率」分组只有一个滑条「分辨率比例」，25～300%，实时写入 `windowScale` 并刷新，见 [InGameControlOverlay.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/ui/game/InGameControlOverlay.kt#L466-L476>)。
- `refreshWindowsSize()` 同步以下内容（缺一会黑块/错位/触控对不上），见 [InGameControlOverlay.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/ui/game/InGameControlOverlay.kt#L664-L690>)：
  - `FCLBridge.setScaleFactor(windowScale)`
  - `SurfaceTexture.setDefaultBufferSize(renderW, renderH)`
  - `CallbackBridge.windowWidth/Height`
  - `pushEventWindow`
  - SDL：`surfaceChanged` + `nativeResize`（SDL 渲染路径）
- 启动链第 9 步同样使用解析后的尺寸设置缓冲/窗口，见 [GameLaunchOrchestrator.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/game/GameLaunchOrchestrator.kt#L472-L505>)。
- 版本级设置里的「强制分辨率」仍支持 `FCLBridge.FORCE_RESOLUTION`，走固定宽高分支。

**状态**：代码完成，release 构建通过；**真机游戏内实际缩放效果待用户实测**。

### 4.6 改分辨率后触控坐标对不上

**根因**：渲染缓冲按比例缩小后，触摸事件仍按显示尺寸直接发给游戏；GUI 态 `pointerMapper` 没有乘「渲染尺寸 / 显示尺寸」。

**实现**：[GameActivity.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/ui/game/GameActivity.kt#L996-L1025>)

```kotlin
val resolved = MenuSetting.resolveRenderSize(metrics.widthPixels, metrics.heightPixels)
val scaleX = renderWidth.toFloat() / tv.width
val scaleY = renderHeight.toFloat() / tv.height
Pair(
    ((x - loc[0]) * scaleX).toInt().coerceAtLeast(0),
    ((y - loc[1]) * scaleY).toInt().coerceAtLeast(0)
)
```

- 事件到达时实时读取 `MenuSetting.windowScale`，滑条调整后立即生效。
- 支持 `FCLBridge.FORCE_RESOLUTION` 时使用强制宽高。
- `onSurfaceTextureSizeChanged` 同样使用 `resolveRenderSize(width, height)`，见 [GameActivity.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/ui/game/GameActivity.kt#L1057>)。

**状态**：代码完成；**真机触控对齐待实测**（尤其调 25%/300% 边界）。

### 4.7 二次启动崩溃

**现象**：退出游戏后再次启动，应用崩溃。

**根因**：JVM 在**同一进程内只能创建一次**。`JvmLauncher.nativeDestroyJVM` 只把 `g_jvmRunning` 复位，并不会真正 `DestroyJavaVM`（真正销毁发生在 `main()` 返回后），所以同进程再次 `JNI_CreateJavaVM` 必失败。ZL2 的策略是退出即杀进程，由系统重新拉起。

**实现**：
- 新增顶层函数 [LaunchRestarter.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/game/LaunchRestarter.kt#L14-L36>)：

```kotlin
fun scheduleColdStart(context: Context, intent: Intent, requestCode: Int) {
    val pending = PendingIntent.getActivity(
        context.applicationContext, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarm = context.applicationContext.getSystemService(AlarmManager::class.java)
    alarm?.setWindow(AlarmManager.RTC, System.currentTimeMillis() + 1200, 500, pending)
}

fun killProcessSoon(delayMillis: Long = 400L) {
    Handler(Looper.getMainLooper()).postDelayed({
        android.os.Process.killProcess(android.os.Process.myPid())
    }, delayMillis)
}
```

- 游戏正常退出（exit code 0）：`finishAffinity()` + `killProcessSoon()`，下次启动由全新进程承载新 JVM，见 [GameLaunchOrchestrator.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/game/GameLaunchOrchestrator.kt#L150-L165>)。
- 「重启游戏」：先用 `scheduleColdStart(...)` 挂起目标 Activity 的 `PendingIntent`，再 `killProcessSoon()`；请求码 `0x5647`。
- 崩溃分析页：
  - 「退出」→ `finishAffinity()` + `killProcessSoon()`，见 [CrashReportActivity.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/ui/game/CrashReportActivity.kt#L135-L143>)。
  - 「重新启动应用」→ `scheduleColdStart` + 杀进程，请求码 `0x5648`，见 [CrashReportActivity.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/ui/game/CrashReportActivity.kt#L204-L213>)。
- `jvmlauncher.cpp` 的 `nativeDestroyJVM` 保持仅复位标志，见 [jvmlauncher.cpp](<file:///d:/Verse Explorer X/Verse Android/JvmLauncher/src/main/cpp/jvmlauncher.cpp#L1408-L1415>)；不要改成真正销毁。

**已验证**：连续两次冷启动应用，PID 由 29548 → 30136，无崩溃。
**未验证**：真机上「退出游戏 → 再次启动」完整链路；崩溃页「重启游戏」的 AlarmManager 冷启动；这两项是接手后的第一优先测试。

---

## 5. 关键代码索引

| 功能 | 文件与位置 |
|---|---|
| 冷启动/杀进程 | [LaunchRestarter.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/game/LaunchRestarter.kt#L14-L36>) |
| 游戏退出/重启/渲染尺寸接入 | [GameLaunchOrchestrator.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/game/GameLaunchOrchestrator.kt#L150-L165>)、[第 9 步](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/game/GameLaunchOrchestrator.kt#L472-L505>) |
| 崩溃页重启/退出 | [CrashReportActivity.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/ui/game/CrashReportActivity.kt#L135-L143>)、[L204-L213](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/ui/game/CrashReportActivity.kt#L204-L213>) |
| 分辨率比例存储/解析 | [MenuSetting.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/tungsten/fcl/setting/MenuSetting.kt#L20-L29>)、`windowScale` 默认值 `L117` |
| 控制面板分辨率滑条/刷新 | [InGameControlOverlay.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/ui/game/InGameControlOverlay.kt#L466-L476>)、[L664-L690](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/ui/game/InGameControlOverlay.kt#L664-L690>) |
| 触控坐标映射 | [GameActivity.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/ui/game/GameActivity.kt#L996-L1025>)、[L1057](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/ui/game/GameActivity.kt#L1057>) |
| 主页顶部留白 | [HomeScreen.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/ui/HomeScreen.kt#L190-L212>) |
| 刘海策略/根容器 | [MainActivity.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/MainActivity.kt#L114-L121>)、[L228-L272](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/MainActivity.kt#L228-L272>) |
| 渲染器插件扫描 | [RendererPluginManager.kt](<file:///d:/Verse Explorer X/Verse Android/VerseLauncher/src/main/java/com/verse/explorer/launcher/renderer/RendererPluginManager.kt#L22-L68>) |
| 插件启动注入 | `VerseLauncher/.../renderer/RendererManager.kt`、`FclCore/.../FCLauncher.java`、`VerseLauncher/.../DefaultLauncher.java` |
| TouchController 代理 | [TouchControllerProxy.kt](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/java/com/verse/explorer/app/game/TouchControllerProxy.kt>) |
| JVM 销毁语义 | [jvmlauncher.cpp](<file:///d:/Verse Explorer X/Verse Android/JvmLauncher/src/main/cpp/jvmlauncher.cpp#L1408-L1415>) |
| 版本/构建配置 | [build.gradle.kts](<file:///d:/Verse Explorer X/Verse Android/VerseApp/build.gradle.kts#L27-L67>) |
| Manifest 方向/可见性 | [AndroidManifest.xml](<file:///d:/Verse Explorer X/Verse Android/VerseApp/src/main/AndroidManifest.xml#L29-L140>) |

---

## 6. 架构与启动链速记（避免误改）

- 启动器 UI 进程 → `GameLaunchController` 做文件/Java/版本检查 → `GameActivity` → `GameLaunchOrchestrator` 9 步启动（hook 初始化、环境变量、vkshim、SDL、JVM execute、Surface 接入）。
- JVM 与启动器**同进程**，游戏 `main()` 阻塞在 native 事件循环；`finish()` 打断不了游戏，只有杀进程才是真正退出（「强制退出」也是 `Process.killProcess(myPid())`）。
- MC 26.3 走 Vulkan/vkshim 链路（`libvkshim.so`、`VULKAN_PTR`、pipeline 缓存）。GL 渲染器切换对 26.3 不生效。
- SDL 渲染路径（26.3+）必须保证：
  1. `onSurfaceChanged` 同步预置 `CallbackBridge.windowWidth/Height`；
  2. `execute` 前预置 + 执行后 `pushEventWindow` 双保险；
  3. `SdlBridge.prepareSurface(this, surface, textureView.parent as ViewGroup)` 不能漏 layout（漏传会导致进世界约 40 秒后 JNI abort）。
- 输入层：按钮 → `ControlLayout`，空白区 → `GameInputHandler`；滑动过的手指绝不判成 tap；按住按钮滑出边界要用指针捕获集合继续转发 UP。
- `MenuSetting` 是 FCL 控制栏设置，`menu_setting.json` 持久化；改动键名/默认值会影响旧配置兼容。

---

## 7. 已验证 / 待验证

### 已在本轮或此前会话验证

- 主页忽略刘海后顶部不再贴顶。
- 渲染器插件在模拟器（假 MG 插件）上可识别、可显示在列表。
- 真机安装 0.0.8 包成功，Android 16/17 兼容性弹窗消失。
- 连续两次冷启动应用：PID 变化、无崩溃。
- 二次启动修复包已放远程传输目录。

### 待用户/真机验证（接手优先）

1. 真机：进入游戏 → 退出 → 再次启动，确认不崩溃、JVM 正常。
2. 真机：游戏内控制面板拖动「分辨率比例」25% / 100% / 200%，确认画面缩放正确、无黑块、触控位置对齐。
3. 真机：崩溃分析页点「重新启动应用」/「重启游戏」，确认系统冷启动拉起新进程。
4. 真机：TouchController 模组存在时指针转发是否正常（本次会话未实测）。
5. 真机：渲染器插件（用户安装的 MG）在版本设置下拉中出现并可启动（GL 路径）。
6. 如继续整合包安装线：旧版本 2.4.70 的尾部 401MB OOM 是否已解决没有本次证据，需重新验证（见第 10 节）。

---

## 8. 已知问题 / 风险

1. **16 KB ELF 对齐**：部分预编译 native 库（pojavexec、LWJGL natives 等）仍是 4 KB 对齐。真机页面 4096 可运行；Android 16/17 的兼容性弹窗已通过不可调试包规避，但要彻底合规需 NDK r28+ 重建所有 native 库。非当前阻塞项。
2. **JVM 单进程限制**：任何“同进程重启游戏”的尝试都会失败；必须坚持杀进程 + 冷启动。不要为了“体验更快”改回同进程重启。
3. **AlarmManager 冷启动时序**：`setWindow` 延迟 1200ms ±500ms；若系统省电策略严格，可能不触发。真机验证时若失败，检查：
   - PendingIntent 是否带 `FLAG_IMMUTABLE|FLAG_UPDATE_CURRENT`；
   - 目标 Activity 是否 `exported`/可被系统拉起；
   - 是否被厂商后台限制拦截（必要时引导用户允许自启动）。
4. **渲染器插件元数据差异**：FCL V2、旧 V1、ZL2 格式字段不统一；新插件若解析失败只打日志不崩溃。遇到新格式先抓 `RendererPlugin` 日志再补解析，不要猜字段。
5. **分辨率与强制分辨率并存**：版本设置里开了「强制分辨率」时，`windowScale` 滑条不生效（走 `FORCE_RESOLUTION_WIDTH/HEIGHT`）。这是预期行为，测试时注意区分。
6. **触控映射边界**：`coerceAtLeast(0)` 只防负数，未 clamp 到渲染宽高上限；若真机出现边缘越界，再补上限约束。
7. **adb 掉线/进程僵死**：模拟器进程易僵死（`adb devices` 为空）；真机长时间操作后掉线。产物先放远程传输，不要反复重构建。
8. **git 工作区很脏**：0.0.5～0.0.8 未提交；`.verify/`、`promo-video/`、`soup/` 等未跟踪目录可能是临时产物，提交前和用户确认，不要 `git add -A`。

---

## 9. 发布前注释清理清单

用户规则：**发布内测版时必须清理所有有关参考启动器的注释，不得残留**。

当前源码中仍命中的文件（`移植自/迁移自/原样移植/对位/照搬/复刻/1:1/与 ZL2/对齐 ZL2`，共 22 个文件、37 处）：

| 文件 | 命中数 |
|---|---|
| `VerseApp/.../ui/InstalledVersionsScreen.kt` | 9 |
| `VerseApp/.../ui/theme/Palette.kt` | 3 |
| `VerseApp/.../ui/components/Layouts.kt` | 3 |
| `VerseApp/.../ui/components/BackgroundCard.kt` | 3 |
| `VerseApp/.../ui/components/_SimpleRail.kt` | 1 |
| `VerseApp/.../ui/components/_Scroll.kt` | 1 |
| `VerseApp/.../ui/components/Constants.kt` | 1 |
| `VerseApp/.../ui/components/Buttons.kt` | 1 |
| `VerseApp/.../ui/components/PcComponents.kt` | 1 |
| `VerseApp/.../ui/components/ModpackImportFlow.kt` | 1 |
| `VerseApp/.../ui/game/GameActivity.kt` | 1（第 1004 行「与 ZL2 的 mapToGame 一致」） |
| `VerseApp/.../game/GameLaunchOrchestrator.kt` | 1（第 157 行「与 ZL2 的退出策略一致」） |
| `VerseApp/.../com/tungsten/fcl/setting/MenuSetting.kt` | 1（第 10 行「原样移植自 .fcl-temp/FCL」） |
| `VerseApp/.../com/tungsten/fcl/control/view/LogWindow.kt` | 1 |
| `VerseApp/.../com/tungsten/fcl/control/GestureMode.java` | 1 |
| `VerseApp/.../com/tungsten/fcl/control/MouseMoveMode.java` | 1 |
| `VerseApp/.../net/kdt/pojavlaunch/Tools.java` | 1 |
| `VerseApp/.../net/kdt/pojavlaunch/customcontrols/keyboard/TouchCharInput.java` | 1 |
| `VerseApp/.../com/mio/controlconverter/CcJson.kt` | 1 |
| `FclCore/.../com/tungsten/fcl/game/sdl/SdlBridge.kt` | 2 |
| `VerseLauncher/.../launcher/fcl/launch/DefaultLauncher.java` | 1 |
| `VerseCore/src/test/.../ModrinthParseTest.kt` | 1（仅测试，不打包，低优先） |

复查命令（PowerShell / git bash 均可，需 rg）：

```powershell
rg -n "(移植自|迁移自|原样移植|对位|照搬|复刻|1:1|与 ?ZL2|对齐 ?ZL2)" -g "*.{kt,java}"
```

注意：
- 还有大量「Pojav 触控模块迁移」类注释在 `net/kdt/pojavlaunch/**`、`FclCore/.../CallbackBridge.java` 等文件里；用户规则同样要求清理来源字样，发布前逐条改写为功能性描述。
- 不要误删功能性字符串：`getPojavEnv()`、`PojavBigCore`、`PCL2CE`（陶瓦房间类型常量）、`Pojav JRE`（资源名）等是代码/协议的一部分，不是注释。
- 清理后重新全量编译，避免误删 import（历史上删注释时误删过 `android.view.MotionEvent`）。

---

## 10. 测试手册（真机 / 模拟器）

### 10.1 真机

- 序列号：`3B1F5PE9ZUZ69NZH`。
- 安装：`adb -s 3B1F5PE9ZUZ69NZH install -r <signed.apk>`。
- 日志：

```powershell
$pid = (adb -s 3B1F5PE9ZUZ69NZH shell pidof com.verse.explorer).Trim()
adb -s 3B1F5PE9ZUZ69NZH logcat -d --pid=$pid | Select-String "GameLaunchOrchestrator|LaunchRestarter|RendererPlugin|Control|AndroidRuntime"
```

- 抓日志前先清空：`adb logcat -c`。
- 截图必须设备端保存再 pull（PowerShell 重定向会破坏 PNG）：

```powershell
adb -s 3B1F5PE9ZUZ69NZH shell "screencap -p /sdcard/s.png"
adb -s 3B1F5PE9ZUZ69NZH pull /sdcard/s.png "$env:TEMP\s.png"
```

### 10.2 模拟器 verse_test（旧资料，按需使用）

- 规格：x86_64、Android 15（API 35）、Pixel 6、1080×2400、数据分区已扩到 16GB。
- 启动命令（无窗口）：

```powershell
$env:ANDROID_SDK_ROOT="D:\verse-android-sdk"; $env:ANDROID_AVD_HOME="D:\verse-android-sdk\avd"
& "D:\verse-android-sdk\emulator\emulator.exe" -avd verse_test -no-window -gpu swiftshader_indirect -no-snapshot -no-audio -no-boot-anim
```

- **必须带 `-no-snapshot`**；模拟器曾因旧快照崩溃。若仍崩，删除 `snapshots\default_boot` 与 `cache.img.qcow2` 再试。
- 冷启动可能回滚旧快照，需重装当前 APK、重走协议/权限/setup。
- 全新安装 bypass：`adb shell run-as com.verse.explorer mkdir -p app_runtime/java/jre{8,17,21,25}/bin`（仅 x86_64 测试环境缺 assets JRE 时用）。
- 触控坐标：应用横屏运行，但 `input tap` 坐标空间与截图尺寸要以当次 `screencap` 实际宽高换算；旧笔记出现过 2400 宽与 2340 宽两种，先截图再换算，不要凭记忆点。
- 设置页 LazyColumn 滚动手势要落在内容区（右侧），滑在左侧栏无效。

### 10.3 关键日志 tag

`GameLaunchOrchestrator`、`LaunchRestarter`、`RendererPlugin`、`VerseLogger`、`Control`、`TouchDiag`、`CursorDiag`、`ModpackInstaller`、`SmartDownload`、`FCL`、`AndroidRuntime`、`libc`。

---

## 11. 历史遗留（旧版本线，供参考，不要照抄状态）

旧交接文档记录的是 **2.4.70（versionCode 206）** 时期，之后版本线切换为 `0.0.x` 内测版。以下结论仍可能有效，但相关代码可能已变，动到对应模块时先重新读代码：

- 整合包安装尾部曾出现固定 **401MB OOM**：任务链成功后、`installModpack` 尾部（`refreshVersions` / `saveModpackIcon`）抛 `OutOfMemoryError`，三次分配值完全相同；已加 `heapSnapshot` 打点定位。该问题在 0.0.x 是否已修复**无本次会话证据**，若用户再报整合包安装失败，先看 `ModpackInstaller` 日志与打点。
- 模拟器 data 分区扩到 16GB 的原因：FCL `FileDownloadTask.setCaching(true)` 会把每个 mod 在 `cache/files/<sha1>` 再存一份，双份存储会让 6GB 装不下大型整合包。
- 旧版下载优化结论仍有效：所有 OkHttp 客户端加 `ipv4Only()`、`connectTimeout ≤ 8s`、probe 窗口超时、下载竞速 winner 后只 cancel 不 join、Dispatcher `maxRequestsPerHost=128`、连接池 128、assets 官方源优先。
- 旧版 UI 结论：下拉一律内联展开（不要 Popup）；LazyColumn item 内不要嵌套 `verticalScroll`；卡片内容不要自建 `.clickable`（会吞 down 导致网格长按失效）；`CardGridState` 的 `Int.MAX_VALUE` 哨兵不能参与尺寸乘法。
- 液态玻璃：`io.github.kyant0:backdrop-android:2.0.0`；关闭玻璃时不要挂 `layerBackdrop` 采样层（低配性能）。
- 侧边栏：圆形按钮 40dp、左边距 12dp，展开胶囊 56dp 宽，水平中心都在 32dp；液态玻璃关闭时也必须用 `CircleShape` / `RoundedCornerShape(50)`，否则变方形。
- 模拟器/真机历史踩坑：PowerShell 不支持 `&&`（用 `;`）；`adb shell` 双引号里的 `$var` 会被本地展开；`screencap` 不要用 PowerShell 重定向；apksigner 后必须 verify。

---

## 12. 记忆文件（下一个 AI 可继续检索）

项目记忆目录：

```
c:\Users\huang\.trae-cn\memory\projects\-d-Verse-Explorer-X-Verse-Android--p2-9acc2bfde075c5175b93\
```

- `project_memory.md`：项目级长期规则、约束、教训（内容很多，动启动链/触控/下载前建议先 grep）。
- `20261013` ～ `20261023` 各日期目录：`topics.md`（主题级进展）+ `session_memory_<id>.jsonl`（消息级细节）。
- 用户画像：`c:\Users\huang\.trae-cn\memory\user_profile.md`（跨项目偏好与明确指令）。

快速检索示例（Grep 工具）：

- pattern：`渲染器插件|二次启动|windowScale|mapToGame|刘海|16 ?KB|AlarmManager`
- path：上述项目记忆目录
- output_mode：`content`，`-n: true`

---

## 13. 下一步建议（按序）

1. **真机回归**第 7 节 1～3 项（二次启动、分辨率+触控、崩溃页冷启动）。用日志确认：
   - 新进程 PID 与旧进程不同；
   - `GameLaunchOrchestrator` 第 9 步窗口尺寸与 `windowScale` 一致；
   - 无 `JNI_CreateJavaVM` 二次调用失败。
2. 发现问题先抓日志定位，不要猜；需要对照就读 `d:\Verse Explorer X\zl2-src` 或 `.fcl-repo`。
3. 完成第 9 节注释清理（发布前必做）。
4. 递增版本号（当前 0.0.8/37，下一版建议 0.0.9/38，具体以用户要求为准）。
5. 构建 arm64 release → zipalign → apksigner → verify → 复制到 `d:\Verse Explorer X\远程传输\`（删旧包）。
6. 向用户汇报验证结果与产物路径。**只有用户明确要求才 commit/push/tag/发布**。
7. 若用户要求发布：清理旧构建、更新后台更新配置、按镜像上传流程发 Release。

---

## 14. 一句话总结

当前版本 0.0.8 已包含：刘海主页留白、渲染器插件体系、16 KB 兼容性弹窗规避、纯百分比游戏分辨率、触控缩放映射、二次启动杀进程冷启动修复。代码已构建并出包，**剩余工作是真机回归 + 注释清理 + 按用户指令发版**。
