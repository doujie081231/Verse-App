# FclCore 本地改动账本

> 目的：FclCore 是 FCL 启动器源码的 vendored 副本，与上游已永久分叉。
> 本文件记录「我们改了什么、为什么改、以后该怎么改」，避免每次改核心都要人工回忆。
>
> 建立时间：2026-10-24。基线提交：`a869dd0`。

---

## 1. 双轨结构（先看这个，避免改错文件）

启动链与安装链走的是**两套不同的代码**，同一个类名可能出现两次：

| 链路 | 代码位置 | 说明 |
|---|---|---|
| 安装 / 下载 / 模组 / 资源 | `FclCore/src/main/java/com/tungsten/fclcore/**` | 原版 FCL 逻辑，几乎不改 |
| 游戏启动 | `VerseLauncher/src/main/java/com/verse/explorer/launcher/fcl/**` | FCL 启动链的 Verse 分支（`DefaultLauncher` 等，本地 730 行 vs 上游 484 行） |
| 游戏 JVM 内注入 / 输入桥 | `FclCore/src/main/java/org/lwjgl/glfw/CallbackBridge.java`、`com/tungsten/fclauncher/FCLauncher.java` | 被启动链调用，本地改动最多 |
| SDL 状态桥 | `FclCore/src/main/java/com/tungsten/fcl/game/sdl/SdlBridge.kt` | 启动器与游戏 JVM 共享 |

**改启动行为 → 先看 `VerseLauncher` 的 fork；改安装/下载 → 看 `fclcore.*`。**
`GameLaunchOrchestrator` 里 `import` 的是 Verse fork 的 `DefaultLauncher`，不是 FclCore 的。

---

## 2. 改动记账（本地对上游的实质修改）

主要记录行为差异；涉及兼容条件或维护规则的注释修订也在此说明。

### 2.1 `com/tungsten/fclauncher/FCLauncher.java`（+238 / −122）

- **LWJGL 配对版 natives 注入**：按版本 JSON 解析出的 LWJGL 版本挑选对应 natives，
  与 patched `liblwjgl*.so` / `libpojavexec.so` 配对，避免版本错配导致启动即崩。
- **`addRendererEnv` 重构**：SDL 需要独立的 GL/EGL 库绝对路径，改为从渲染器库解析后注入
  `SDL_OPENGL_LIBRARY`，并让 versesdl 作为代理。
- **MobileGlues 驱动配置**：向 `/sdcard/MG/config.json` 写驱动配置（内置 MG 已移除，
  但插件式 MG 仍走这条路径）。
- **崩溃上报**：JVM 启动段 `catch Throwable` 后上报，避免异常被静默吞掉。

### 2.2 `org/lwjgl/glfw/CallbackBridge.java`（+218 / −196）

- **指针形状通道**：游戏侧 `glfwSetCursor` 上报形状，宿主据此绘制对应指针图片。
- **多路抓取监听**：支持多个抓取监听器（控制按钮 / 触摸板 / 快捷栏各自注册）。
- **输入增量**：鼠标与滚轮事件按增量转发，避免累计漂移。
- **SDL 双路**：SDL 路径与 GLFW 路径并存，按渲染方式择一生效。
- **pojavexec JNI 符号补齐**：`libpojavexec` 的 `JNI_OnLoad` 会 `GetStaticMethodID(onDirectInputEnable)`
  与 `CriticalNativeTest.testCriticalNative`，缺任一符号会导致输入注册全链失败、异常挂起。
- **SDL 启动集成**：在 SDL JNI 绑定前补发真实窗口尺寸。

### 2.3 `com/mio/data/Renderer.java`

- 上游**没有这个文件**（属于仅存在于 FclCore 的 62 个文件之一），本地仅有注释差异，
  用于标注渲染器枚举的语义，无行为改动。

### 2.4 注释维护

- `LibFilter.java`：将依赖替换说明改为与实际版本条件一致，删除无依据的 Java 17 字节码支持与 Android API 版本结论。仅修改注释，保留实现及第三方声明。
- 本次仅更新该文件的哈希。原有上游快照统计保持不变，其他基线差异需独立核查。

### 2.5 其余差异（未逐文件记账）

上游整体领先（仅上游存在的文件 79 个，多为 UI 层与新功能）。这些差异**不是我们改的**，
而是我们取用的快照本来就落后；不要试图反向合并。

---

## 3. 漂移基线（怎么量、量出来多少）

对比方法（排除构建产物，只比源码）：

```bash
diff -rq --exclude=build --exclude=.git \
  FclCore/src/main/java .fcl-repo/FCL/src/main/java \
  | awk '/^Only in FclCore/{a++} /^Only in .fcl-repo/{b++} /^Files/{c++} END{print "仅本地:",a,"仅上游:",b,"内容不同:",c}'
```

基线快照（提交 `a869dd0`，2026-10-24）：

| 指标 | 数量 |
|---|---|
| 仅存在于 FclCore 的文件 | 62 |
| 仅存在于上游快照的文件 | 79 |
| 同名但内容不同 | 115 |

**每次改动 FclCore 后重跑上表并更新这里**，这是判断「分叉扩大了多少」的唯一量化依据。

---

## 4. 改动规矩（硬性）

1. **能不改就不改**：新功能优先落在 `VerseLauncher` 的 fork 或 Verse 侧的 wrapper/adapter。
2. **必须改时最小化**：只加不改，不改上游方法签名，不动与需求无关的行。
3. **每次改动必须记账**：在本文件第 2 节补一条（改了什么 / 为什么 / 影响面），
   并在第 3 节更新漂移数字。
4. **不许为了"整洁"重构 FclCore**：格式化、重命名、删"看起来没用"的代码一律禁止——
   上游无法合并，任何无收益改动都会永久抬高维护成本。
5. **注释与命名**：本地补充注释使用中文，解释当前行为与兼容约束。保留上游版权、许可证和必要的来源标注；具体格式见[注释规范](../docs/COMMENT_STYLE.md)。

### 4.1 自动校验（规矩的执行手段）

规矩写在文档里没人执行等于没有，所以加了机器校验：

- 工具：`tools/fclcore_drift.py`
- 基线：`tools/fclcore-baseline.json`（702 个文件的 SHA-256，自包含，不依赖上游快照）

```bash
python tools/fclcore_drift.py --check    # 校验（CI 每次 push 自动跑）
python tools/fclcore_drift.py --update   # 有意改动后刷新基线
```

任何对 `FclCore/src` 的新增 / 修改 / 删除都会被列出来并以退出码 1 失败，
提示「去第 2 节记账 + 更新第 3 节数字 + 刷新基线」。CI 步骤名：
`Check FclCore drift`，排在单测与构建之前，改错 FclCore 会在 10 秒内被拦住。

> 为什么用哈希基线而不是 diff：`.fcl-repo`（上游快照）没有入库，CI 里没有对照物。
> 哈希基线只回答「FclCore 有没有被动过」，这正是规矩要守的东西。
> 漂移**规模**仍用第 3 节的 diff 统计（本地有上游快照时执行）。

---

## 5. 未来选项（当前不做）

- **上游合并**：不做。上游领先太多（79 个文件本地没有，115 个同名文件内容不同），
  逐文件合并的成本远高于收益，且会冲掉 CallbackBridge/FCLauncher 的全部本地改动。
- **反向贡献**：不做。Verse 的输入桥改动与 SDL 集成依赖本地 native 层，不具备通用性。
- **若将来必须跟进上游**：按文件逐个评估，从"安装/下载"这类低耦合模块开始，
  启动链与输入桥保持独立分叉。
