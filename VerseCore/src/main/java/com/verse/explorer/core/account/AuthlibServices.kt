package com.verse.explorer.core.account

/**
 * 外置登录（authlib-injector）服务的进程级持有者。
 *
 * 由应用启动时初始化；核心层（账号头像仓库等）与 UI 层共用同一实例，
 * 避免核心代码反向依赖应用类的单例。
 */
object AuthlibServices {
    lateinit var authlibAuthenticator: AuthlibAuthenticator
}
