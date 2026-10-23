package com.verse.explorer.app.game

import android.system.Os
import com.verse.explorer.app.ui.VerseLogger
import top.fifthlight.touchcontroller.proxy.client.LauncherProxyClient
import top.fifthlight.touchcontroller.proxy.client.android.transport.UnixSocketTransport
import java.io.File

/**
 * TouchController 模组控制代理：模组启动时连接本机 Unix 套接字，
 * 启动器把手指坐标按屏幕比例转发过去，由模组在游戏内绘制并处理虚拟按键。
 * 套接字名称通过环境变量告知模组，因此必须在 Java 虚拟机启动前调用 [start]。
 */
object TouchControllerProxy {

    private const val TAG = "TouchController"
    private const val SOCKET_NAME = "VerseExplorerX"

    @Volatile
    private var client: LauncherProxyClient? = null

    /** 版本模组目录中是否存在 TouchController（mods/touchcontroller-*.jar） */
    fun isModPresent(gameDir: String, versionId: String, isolateGameDir: Boolean): Boolean {
        val modsDir = if (isolateGameDir) {
            File(gameDir, "versions/$versionId/mods")
        } else {
            File(gameDir, "mods")
        }
        return modsDir.listFiles()?.any {
            it.isFile && it.extension.equals("jar", true) &&
                it.name.contains("touchcontroller", ignoreCase = true)
        } == true
    }

    /** 启动代理客户端；重复调用直接复用已有连接 */
    fun start(): Boolean {
        if (client != null) return true
        return runCatching {
            val transport = UnixSocketTransport(SOCKET_NAME)
            Os.setenv("TOUCH_CONTROLLER_PROXY_SOCKET", SOCKET_NAME, true)
            // 仅声明触摸转发能力：文本输入桥（InputConnection）尚未接入，
            // 不声明 TEXT_STATUS，避免模组误以为启动器支持文字状态同步
            val proxy = LauncherProxyClient(
                transport = transport,
                capabilities = emptySet()
            )
            proxy.run()
            client = proxy
            VerseLogger.info("TouchController 控制代理已启动", TAG)
            true
        }.getOrElse {
            VerseLogger.warn("TouchController 控制代理启动失败: ${it.message}", TAG)
            false
        }
    }

    fun addPointer(pointerId: Int, x: Float, y: Float) {
        client?.addPointer(pointerId, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    }

    fun removePointer(pointerId: Int) {
        client?.removePointer(pointerId)
    }

    fun clearPointers() {
        runCatching { client?.clearPointer() }
    }

    fun isRunning(): Boolean = client != null
}
