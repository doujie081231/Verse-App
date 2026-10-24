# 架构与兼容边界

## 运行入口与进程

当前受维护的游戏主链为页面启动请求 → `GameActivity` → 游戏会话 → `DefaultLauncher` → FCL/JNI 桥。`GameService` 已弃用且未在 Manifest 注册，不作为备用启动入口。

游戏 JVM 仍遵循既有进程内单次初始化约束。游戏退出后的冷重启、Surface 销毁时停止游戏、`:crash` 崩溃页和 `:jvm` 安装处理器进程分工保持不变。架构整理不改变 JNI 包名、类名、方法签名或原生初始化顺序。

## 模块职责

`VerseApp` 负责界面、Android 生命周期、权限和依赖装配；`VerseCore` 定义业务数据和外部能力端口；`VerseLauncher` 实现启动、FCL 安装及运行时适配。原生实现继续位于 `JvmLauncher`、`FclCore` 和 `Terracotta`。

历史第三方兼容包可以保留必要的底层接口引用，新增业务代码通过端口调用。兼容标识、用户配置键、版本目录布局及插件协议均属于兼容契约。

## 配置和生命周期

全局设置与版本独立设置沿用当前各字段的优先级，Intent 解码保留原有键与默认值。账号令牌只作为启动输入传递，不写入架构诊断报告。

仓库和跨页面安装任务由应用作用域持有；ViewModel 由对应 Activity 的 ViewModelStore 管理；游戏资源由单次会话持有。停止和释放必须幂等，迟到回调不得重新激活已结束的会话。

## 漂移基线核查

漂移工具对 Java、Kotlin、XML 等文本统一按 LF 计算 SHA-256，对二进制保持原始字节。原有 18 处纯换行符差异不代表源码行为改变。

另两项原有差异已逐项阅读：`FCLBridge.java` 在启动线程前同时设置 native 与 Java 桥引用；`NeoForgeOldInstallTask.java` 使用宿主 Context、处理器锁和独立套接字端口执行安装处理器。这些均为当前已存在的宿主集成行为，本轮仅记录并更新基线，不修改其实现。新的基线格式为 schemaVersion 2，文本规范为 LF。
