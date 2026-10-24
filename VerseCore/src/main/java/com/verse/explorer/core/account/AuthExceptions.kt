package com.verse.explorer.core.account

enum class AuthType {
    Access, Refresh
}

enum class AsyncStatus {
    GETTING_ACCESS_TOKEN,
    GETTING_XBL_TOKEN,
    GETTING_XSTS_TOKEN,
    AUTHENTICATE_MINECRAFT,
    VERIFY_GAME_OWNERSHIP,
    GETTING_PLAYER_PROFILE
}

class AuthHttpException(
    val httpStatus: Int,
    val errorCode: String?,
    message: String
) : Exception(message)

class CredentialsExpiredException : Exception("登录已过期，请重新登录")

class AuthenticationTimeoutException : Exception("网络访问超时，请检查网络环境。")

class XboxLoginException(val status: ExceptionStatus) : RuntimeException() {
    enum class ExceptionStatus {
        /** 封禁 */
        BANNED,

        /** 受到限制 */
        RESTRICTED,

        /** 未创建个人资料 */
        UNREGISTERED,

        /** 未同意服务条款 */
        NOT_ACCEPTED_SERVICE,

        /** 地区被禁止登陆 */
        BLOCKED_REGION,

        /** 未提供年龄证明 */
        REQUIRES_PROOF_OF_AGE,

        /** 达到游玩时间限制 */
        REACHED_PLAYTIME_LIMIT,

        /** 未成年 */
        UNDERAGE
    }

    fun toLocalMessage(): String = when (status) {
        ExceptionStatus.BANNED -> "您的账号因违反 Xbox 社区规范已被封禁，无法使用！"
        ExceptionStatus.RESTRICTED -> "您的账号目前受到限制，监护人尚未授权您进行在线游戏。"
        ExceptionStatus.UNREGISTERED -> "您的账号尚未创建 Xbox 个人资料，请前往 https://signup.live.com/signup 注册。"
        ExceptionStatus.NOT_ACCEPTED_SERVICE -> "您的账号尚未接受 Xbox 的服务条款。"
        ExceptionStatus.BLOCKED_REGION -> "您的账号所在的地区未被 Xbox 授权使用，登录尝试已被拦截。"
        ExceptionStatus.REQUIRES_PROOF_OF_AGE -> "您的账号需要提供年龄证明，请登录 https://login.live.com/login.srf 提供相关信息。"
        ExceptionStatus.REACHED_PLAYTIME_LIMIT -> "您的账号已达到游戏时间限制，已被禁止登录。"
        ExceptionStatus.UNDERAGE -> "账号的出生日期显示未满 18 岁，必须由成年人添加到家庭中才能继续使用。"
    }
}

class MinecraftProfileException(val status: ExceptionStatus) : RuntimeException() {
    enum class ExceptionStatus {
        /** 登陆过于频繁 */
        FREQUENT,

        /** IP 地址被禁止 */
        BLOCKED_IP,

        /** 未创建配置 */
        PROFILE_NOT_EXISTS
    }

    fun toLocalMessage(): String = when (status) {
        ExceptionStatus.FREQUENT -> "登录过于频繁，请稍后再试。"
        ExceptionStatus.BLOCKED_IP -> "当前 IP 地址已被禁止登陆。"
        ExceptionStatus.PROFILE_NOT_EXISTS -> "请先创建 Minecraft 玩家档案，然后再重新登陆！"
    }
}

class NotPurchasedMinecraftException : Exception("未检查到 Minecraft 配置信息，请前往 Minecraft 的官方网站购买。")

class EmptyProfileException : Exception("请先创建 Minecraft 玩家档案，然后再重新登陆！")
