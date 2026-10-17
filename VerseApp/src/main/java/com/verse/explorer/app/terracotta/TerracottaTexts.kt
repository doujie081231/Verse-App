package com.verse.explorer.app.terracotta

/**
 * 陶瓦联机文案
 *
 */
object TerracottaTexts {

    // ===== 通用 =====
    const val MULTIPLAYER = "多人联机"
    const val MENU = "联机菜单"
    const val MENU_OPEN = "打开"
    const val CONFIRM_POSITIVE = "确定"
    const val CONFIRM_NEGATIVE = "取消"
    const val BACK = "退出"

    fun metadata(terracottaVersion: String, easyTierVersion: String): String =
        "陶瓦版本：$terracottaVersion\nEasyTier 版本：$easyTierVersion"

    // ===== 设置页 =====
    const val ENABLE = "启用多人联机"
    const val ABOUT = "Terracotta | 陶瓦联机"
    const val ABOUT_EASYTIER = "关于 EasyTier"
    const val TUTORIAL_HOST_BTN = "房主教程"
    const val TUTORIAL_GUEST_BTN = "房客教程"
    const val EXPORT_LOG = "导出联机核心日志"
    const val EXPORT_LOG_BTN = "日志"
    const val EXPORT_LOG_DESC = "为分析错误提供更多信息"
    const val EXPORT_LOG_DIALOG_TITLE = "联机核心日志"
    const val EXPORT_LOG_DIALOG_EXPORT = "导出"
    const val EXPORT_LOG_DONE = "联机核心日志成功导出至日志文件夹"
    const val EXPORT_LOG_FAILED = "无法导出日志"
    const val EXPORT_LOG_SHARE = "分享联机核心日志"
    const val EXPORT_LOG_SHARE_NULL = "未找到联机核心日志，请先在游戏内联机菜单导出日志"
    const val PERMISSION_VPN = "请先授予应用 VPN 权限"
    const val USER_NOTICE_DESC = "您承诺，在多人联机全过程中，您将严格遵守您所在国家或地区的全部法律法规。"
    const val USER_NOTICE_CONFIRM =
        "用户须知\n\n陶瓦联机是第三方开源自由软件，您在使用过程中所遇到的问题请通过相关渠道进行反馈。\n" +
            "陶瓦联机使用 P2P 技术，联机成功后房间内用户之间将直接连接，不会使用第三方服务器对您的流量进行转发。" +
            "最终联机体验和参与联机者的网络情况有较大关系。\n" +
            "在多人联机全过程中，您必须严格遵守您所在国家与地区的全部法律法规。"

    // ===== 通知 =====
    const val NOTIFICATION_TITLE = "Terracotta | 陶瓦联机 正在运行"
    const val NOTIFICATION_CHANNEL = "陶瓦联机 VPN"
    const val NOTIFICATION_CHANNEL_DESC = "陶瓦联机 VPN 状态"

    fun notificationDesc(identity: String, state: String?): String =
        "身份：$identity，状态：${state ?: "未知"}"

    // ===== 状态 =====
    fun statusText(state: TerracottaState): String = when (state) {
        is TerracottaState.Waiting -> "等待中"
        is TerracottaState.HostScanning -> STATUS_HOST_SCANNING
        is TerracottaState.HostStarting -> STATUS_HOST_STARTING
        is TerracottaState.HostOK -> STATUS_HOST_OK
        is TerracottaState.GuestConnecting -> STATUS_GUEST_CONNECTING
        is TerracottaState.GuestStarting -> STATUS_GUEST_STARTING
        is TerracottaState.GuestOK -> STATUS_GUEST_OK
        is TerracottaState.ExceptionState -> STATUS_EXCEPTION
    }

    const val STATUS_HOST_SCANNING = "正在扫描局域网世界"
    const val STATUS_HOST_STARTING = "正在启动房间"
    const val STATUS_HOST_OK = "已启动房间"
    const val STATUS_GUEST_CONNECTING = "正在加入房间"
    const val STATUS_GUEST_STARTING = "正在加入房间"
    const val STATUS_GUEST_OK = "已加入房间"
    const val STATUS_EXCEPTION = "连接断开"

    // ===== 等待页 =====
    const val WAITING_HOST_TITLE = "我想当房主"
    const val WAITING_HOST_DESC = "创建房间并生成邀请码，与好友一起畅玩"
    const val WAITING_GUEST_TITLE = "我想当房客"
    const val WAITING_GUEST_DESC = "输入房主提供的邀请码加入游戏世界"
    const val WAITING_GUEST_PROMPT_TITLE = "请输入房主提供的邀请码"
    const val WAITING_GUEST_PROMPT_INVALID = "邀请码错误"
    const val WAITING_GUEST_PROMPT_TERRACOTTA_LEGACY = "旧版本 Terracotta 邀请码"
    const val WAITING_GUEST_PROMPT_PCL2CE = "旧版本 PCL CE 邀请码"
    const val WAITING_GUEST_PROMPT_SCAFFOLDING = "Scaffolding 邀请码"

    // ===== 房主 =====
    const val HOST_SCANNING_DESC = "请进入单人存档，按下 ESC 键，选择对局域网开放，点击创建局域网世界。"
    const val HOST_SCANNING_BACK = "这将同时停止扫描局域网世界。"
    const val HOST_STARTING_BACK = "这将会取消创建房间。"
    const val HOST_OK_CODE = "邀请码 (已自动复制到剪贴板)："
    const val HOST_OK_CODE_COPY = "复制邀请码"
    const val HOST_OK_CODE_COPY_TOAST = "已将邀请码复制到剪贴板"
    const val HOST_OK_CODE_DESC = "请提醒您的朋友在 FCL 或 HMCL 或 PCL CE 多人联机功能中选择房客模式，并输入该邀请码。"
    const val HOST_OK_BACK = "这将同时彻底关闭房间，其他房客将退出并不再能重新加入该房间。"

    // ===== 房客 =====
    const val GUEST_STARTING_BACK = "这不会影响其他房客加入当前房间。"
    const val GUEST_OK_ADDRESS =
        "请前往游戏主界面，选择多人游戏，双击进入陶瓦联机大厅。备用联机地址："
    const val GUEST_OK_ADDRESS_COPY = "复制备用联机地址"
    const val GUEST_OK_ADDRESS_COPY_TOAST = "已将备用联机地址复制到剪贴板"
    const val GUEST_OK_ADDRESS_DESC = "如非必要请勿使用此地址。"
    const val GUEST_OK_BACK = "这不会影响其他房客加入当前房间。"

    // ===== 异常 =====
    const val EXCEPTION_BACK = "可再试一次"

    fun exceptionText(type: TerracottaState.ExceptionState.ExceptionType): String = when (type) {
        TerracottaState.ExceptionState.ExceptionType.PING_HOST_FAIL ->
            "加入房间失败：房间已关闭或网络不稳定"
        TerracottaState.ExceptionState.ExceptionType.PING_HOST_RST ->
            "房间连接断开：房间已关闭或网络不稳定"
        TerracottaState.ExceptionState.ExceptionType.GUEST_ET_CRASH ->
            "加入房间失败：EasyTier 已崩溃，请向开发者反馈该问题"
        TerracottaState.ExceptionState.ExceptionType.HOST_ET_CRASH ->
            "创建房间失败：EasyTier 已崩溃，请向开发者反馈该问题"
        TerracottaState.ExceptionState.ExceptionType.PING_SERVER_RST ->
            "房间已关闭：您已退出游戏存档，房间已自动关闭"
        TerracottaState.ExceptionState.ExceptionType.SCAFFOLDING_INVALID_RESPONSE ->
            "协议错误：房主发送了错误的响应数据，请向开发者反馈该问题"
    }

    // ===== 难度 =====
    fun difficultyText(difficulty: TerracottaState.GuestStarting.Difficulty): String? = when (difficulty) {
        TerracottaState.GuestStarting.Difficulty.EASIEST -> "当前网络状态极好：稍等一下就成功！"
        TerracottaState.GuestStarting.Difficulty.SIMPLE -> "当前网络状态较好：建立连接需要一段时间……"
        TerracottaState.GuestStarting.Difficulty.MEDIUM -> "当前网络状态中等：已启用抗干扰备用线路，连接可能失败"
        TerracottaState.GuestStarting.Difficulty.TOUGH -> "当前网络状态极差：已启用抗干扰备用线路，连接可能失败"
        TerracottaState.GuestStarting.Difficulty.UNKNOWN -> null
    }

    const val DIFFICULTY_ESTIMATE_ONLY = "连接成功率由房主和房客的 NAT 类型推算得到，仅供参考。"

    // ===== 玩家 =====
    const val PLAYER_LIST = "玩家列表"
    const val PLAYER_ANONYMOUS = "匿名玩家"

    fun playerKind(kind: ProfileKind): String = when (kind) {
        ProfileKind.HOST -> "房主"
        ProfileKind.LOCAL -> "你"
        ProfileKind.GUEST -> "房客"
    }

    // ===== 教程 =====
    const val TUTORIAL_HOST =
        "房主使用方法：\n\n" +
            " - 在启动器多人联机主页阅读用户须知后启用联机功能\n" +
            " - 启动游戏，在游戏内的「联机」按钮中打开联机菜单\n" +
            " - 点击「我想当房主」，扫描游戏端口\n" +
            " - 进入存档，在游戏暂停菜单中打开「对局域网开放」选项\n" +
            " - 若弹出提示要求申请 VPN 权限，请授予，否则无法正常使用\n" +
            " - 稍等片刻将自动复制邀请码，若没有自动复制，也可以前往联机菜单复制\n" +
            " - 将邀请码发送给你的伙伴\n\n" +
            "注意事项：\n\n" +
            " - 该软件支持离线账户\n" +
            " - 该软件支持与 HMCL 和 PCL CE 等支持 Scaffolding 协议的软件互联"

    const val TUTORIAL_GUEST =
        "房客使用方法：\n\n" +
            " - 在启动器多人联机主页阅读用户须知后启用联机功能\n" +
            " - 启动游戏，在游戏内的「联机」按钮中打开联机菜单\n" +
            " - 点击「我想当房客」，在输入框内输入房主提供的邀请码，并耐心等待\n" +
            " - 若弹出提示要求申请 VPN 权限，请授予，否则无法正常使用\n" +
            " - 显示已加入房间后，在游戏主界面选择多人游戏并刷新，双击进入陶瓦联机大厅\n\n" +
            "注意事项：\n\n" +
            " - 该软件支持离线账户\n" +
            " - 该软件支持与 HMCL 和 PCL CE 等支持 Scaffolding 协议的软件互联\n" +
            " - 使用备用服务器地址加入游戏时，不支持离线账户，自行解决"

    // ===== 控制器错误提示 =====
    const val NOT_INITIALIZED = "陶瓦联机未初始化"
    const val VPN_PERMISSION_REQUIRED = "需要授予 VPN 权限才能使用陶瓦联机"
}
