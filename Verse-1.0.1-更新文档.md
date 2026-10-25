# Verse Explorer X 1.0.1 更新文档

版本：1.0.1（versionCode 53）
构建：arm64-v8a release
包名：com.verse.explorer
APK：Verse-1.0.1-arm64-v8a.apk

## 一、键鼠完整适配

参考 FCL / ZL2 的输入实现，补齐安卓端外接键鼠能力：

- 实体鼠标左键 / 右键 / 中键完整上报，不再只能移动虚拟光标
- 鼠标滚轮上下滚动接入游戏
- 鼠标悬停移动（HOVER_MOVE）与屏幕绝对坐标映射
- 连接实体鼠标时启用指针捕获，隐藏系统光标，避免与游戏内光标重叠
- 抓取态与 GUI 态都支持相对移动，主菜单和游戏内均可正常操作
- 保留「实体鼠标控制」开关，未开启时也自动识别实体鼠标的按键事件
- 蓝牙鼠标与 USB 鼠标统一走 `SOURCE_MOUSE` 判定

## 二、模拟键盘（迁移自 ZL2 布局）

- 游戏内控制面板新增「模拟键盘」入口
- 覆盖 Esc、F1-F12、数字行、字母行、符号键、Shift/Ctrl/Alt、方向键
- 按键按下 / 抬起分别发送 GLFW 事件，支持按住组合键（如 F3 + H）
- 解决手机没有实体键盘时无法触发功能键的问题

## 三、启动与进程

- 游戏页改为独立 `:game` 进程，退出游戏只结束游戏进程，启动器主界面保留
- 修复「退出游戏后启动器一起退出」的问题

## 四、模组与整合包

- 模组兼容性预检改为「提示不阻断」：预检误判不再阻止正常整合包启动
- Modrinth 整合包文件在所有源 404 时跳过该文件继续安装（对齐 ZL2 行为），不再整包安装失败
- 库文件下载增加一次补下载，镜像瞬时波动不会直接导致安装失败
- 低版本存储权限：`WRITE_EXTERNAL_STORAGE` 覆盖到 Android 10（API 29），授权后不再卡权限页

## 五、界面与交互

- 修复从版本/详情页返回主页后卡片挤在左上角的问题：
  - 新版本卡片默认水平居中放置，不再落到 (0,0)
  - 已落坏的版本卡片会在进入主页时自动移回可见位置
  - 版本卡片记录同步等待主页布局播种完成，避免竞态
- 版本设置补充 Java 25 选项，自动安装分支同步支持
- 保存设置后增加「设置已保存」提示
- 液态玻璃开启时侧边栏文字加粗，提升可读性
- 主页卡片与版本列表相关布局保持居中

## 六、下载地址

- GitHub（完整安装包，推荐）：
  https://github.com/doujie081231/Verse-App/releases/download/v1.0.1/Verse-1.0.1-arm64-v8a.apk
- Gitee 发布页（因单文件限制拆成两块上传）：
  https://gitee.com/doujie081231/verse-apk/releases/tag/v1.0.1
- Gitee 分卷合并方式：
  - Windows：`copy /b Verse-1.0.1-arm64-v8a.apk.part1+Verse-1.0.1-arm64-v8a.apk.part2 Verse-1.0.1-arm64-v8a.apk`
  - Linux / macOS / Termux：`cat Verse-1.0.1-arm64-v8a.apk.part1 Verse-1.0.1-arm64-v8a.apk.part2 > Verse-1.0.1-arm64-v8a.apk`
- 完整包 SHA-256：`085f726c1bda0bc3642a117219d957ce60405381a1142fcc56bdb6d4d7495ec6`
- 服务器 `https://verselauncher.cn/api/app-update` 仅返回版本检测信息（versionCode 53 / 1.0.1）与上述 GitHub 下载直链

## 七、安装说明

- 覆盖安装：与 1.0.0 使用同一签名证书，可直接覆盖，versionCode 53
- 全新安装：首次启动需同意协议并授予存储权限
- 设备要求：Android 8.0+（minSdk 26），arm64-v8a

## 八、已知限制

- Minecraft 26.3 需要 Vulkan 1.2 或可用 OpenGL 3.3 后端；部分 GPU（如 Adreno 642L）硬件能力不足，无法运行，属于设备限制
- 天玑等部分平台使用 MG/OpenGL 渲染 26.3 仍可能因驱动能力失败，建议使用支持 Vulkan 1.2 的设备
