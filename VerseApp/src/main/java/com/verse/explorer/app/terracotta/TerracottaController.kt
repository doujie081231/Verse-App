package com.verse.explorer.app.terracotta

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.verse.explorer.app.ui.VerseLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.burningtnt.terracotta.TerracottaAndroidAPI
import java.io.IOException
import java.io.Reader
import java.io.StringWriter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.LockSupport

/**
 * 陶瓦联机控制器
 *
 * 与 FCL 的 Terracotta 类对应：负责初始化原生后端、后台轮询状态、
 * 创建/加入房间、管理 VPN 服务，并把状态变化同步给联机菜单与 VPN 前台通知。
 *
 * 与 FCL 一致：后台轮询守护一旦启动便不会停止（setWaiting 只复位后端状态，
 * 不停止轮询）；VPN 通知的状态文案由状态监听器推送。
 */
object TerracottaController {

    enum class TerracottaMode { HOST, GUEST }

    private const val TAG = "TerracottaController"

    /** 与 FCL 的 TERRACOTTA_USER_NOTICE_VERSION 对应，用户须知更新时递增 */
    const val USER_NOTICE_VERSION = 1

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var initialized = false

    var nativeMetadata: TerracottaAndroidAPI.Metadata? = null
        private set

    var mode: TerracottaMode? = null
        private set

    var state: TerracottaState by mutableStateOf(TerracottaState.Waiting(-1))
        private set

    private var stateIndex = -1L
    private var pollJob: Job? = null

    /** 供经典 View 界面（游戏内联机菜单）注册的状态监听，回调固定在主线程执行 */
    private val stateListeners = CopyOnWriteArrayList<(TerracottaState) -> Unit>()

    /** VPN 通知状态同步是否启用（与 FCL 的 notificationListener 生命周期一致） */
    @Volatile
    private var notificationUpdatesActive = false

    fun getUserNoticeVersion(): Int = USER_NOTICE_VERSION

    fun isInitialized(): Boolean = initialized

    fun addStateListener(listener: (TerracottaState) -> Unit) {
        stateListeners.add(listener)
    }

    fun removeStateListener(listener: (TerracottaState) -> Unit) {
        stateListeners.remove(listener)
    }

    fun initialize(context: Context) {
        if (initialized) return
        try {
            nativeMetadata = TerracottaAndroidAPI.initialize(context) {
                startTerracottaVpn(context)
                notificationUpdatesActive = true
            }
            initialized = true
            VerseLogger.info("陶瓦联机初始化成功", TAG)
            startPolling()
        } catch (e: Throwable) {
            VerseLogger.error("陶瓦联机初始化失败: ${e.message}", TAG)
            initialized = false
        }
    }

    fun setWaiting(context: Context, manual: Boolean) {
        if (!initialized) return

        notificationUpdatesActive = false
        if (manual) stopTerracottaVpn(context)
        try {
            TerracottaAndroidAPI.setWaiting()
        } catch (e: Throwable) {
            VerseLogger.error("设置等待状态失败: ${e.message}", TAG)
        }
    }

    fun setScanning(room: String?, player: String?, extraNodes: List<String>?): Boolean {
        if (!initialized) return false
        if (state !is TerracottaState.Waiting) return false
        return try {
            mode = TerracottaMode.HOST
            TerracottaAndroidAPI.setScanning(room, player, extraNodes)
            true
        } catch (e: Throwable) {
            VerseLogger.error("创建房间失败: ${e.message}", TAG)
            false
        }
    }

    fun setGuesting(room: String, player: String?, extraNodes: List<String>?): Boolean {
        if (!initialized) return false
        if (state !is TerracottaState.Waiting) return false
        return try {
            mode = TerracottaMode.GUEST
            TerracottaAndroidAPI.setGuesting(room, player, extraNodes)
        } catch (e: Throwable) {
            VerseLogger.error("加入房间失败: ${e.message}", TAG)
            false
        }
    }

    fun parseRoomCode(room: String?): TerracottaAndroidAPI.RoomType? {
        if (!initialized || room == null) return null
        return try {
            TerracottaAndroidAPI.parseRoomCode(room)
        } catch (e: Throwable) {
            null
        }
    }

    fun getMetadata(): TerracottaAndroidAPI.Metadata =
        nativeMetadata ?: TerracottaAndroidAPI.Metadata("unknown", 0, "unknown")

    fun collectLogs(): String? {
        if (!initialized) return null
        return try {
            val reader: Reader = TerracottaAndroidAPI.collectLogs()
            StringWriter().use { writer ->
                reader.use { r ->
                    val buf = CharArray(4096)
                    var n: Int
                    while (r.read(buf).also { n = it } != -1) writer.write(buf, 0, n)
                }
                writer.toString()
            }
        } catch (e: IOException) {
            VerseLogger.error("收集日志失败: ${e.message}", TAG)
            "收集日志失败: ${e.message}"
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                try {
                    val json = TerracottaAndroidAPI.getState()
                    val stateObj = TerracottaState.parse(json)
                    if (stateObj.index > stateIndex) {
                        stateIndex = stateObj.index.toLong()
                        withContext(Dispatchers.Main) {
                            state = stateObj
                            stateListeners.forEach { it(stateObj) }
                            if (notificationUpdatesActive && stateObj !is TerracottaState.Waiting) {
                                updateVpnNotificationState(TerracottaTexts.statusText(stateObj))
                            }
                        }
                        VerseLogger.info("陶瓦状态: ${stateObj.stateName}", TAG)
                    }
                } catch (_: Throwable) {
                    // 忽略轮询过程中的瞬时错误
                }
                LockSupport.parkNanos(500_000)
            }
        }
    }

    private fun updateVpnNotificationState(stateText: String) {
        val context = appContext ?: return
        val intent = Intent(context, TerracottaVPNService::class.java)
            .setAction(TerracottaVPNService.ACTION_UPDATE_STATE)
            .putExtra(TerracottaVPNService.EXTRA_STATE_TEXT, stateText)
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * 应用级 Context，用于向 VPN 服务推送状态更新。
     * 由 Application.onCreate 注入。
     */
    @Volatile
    var appContext: Context? = null

    /**
     * 由 UI 层注入 ActivityResultLauncher，用于请求 VPN 授权。
     * 若未注入则视为拒绝。
     */
    @Volatile
    var vpnPrepareLauncher: ((Context, Intent) -> Unit)? = null

    fun handleVpnPrepareResult(context: Context, resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            startVpnForegroundService(context)
        } else {
            try {
                TerracottaAndroidAPI.getPendingVpnServiceRequest().reject()
            } catch (_: Throwable) {
            }
            setWaiting(context, true)
            Toast.makeText(context, TerracottaTexts.VPN_PERMISSION_REQUIRED, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startTerracottaVpn(context: Context) {
        val intent = VpnService.prepare(context)
        if (intent != null) {
            vpnPrepareLauncher?.invoke(context, intent)
                ?: handleVpnPrepareResult(context, Activity.RESULT_CANCELED)
        } else {
            startVpnForegroundService(context)
        }
    }

    private fun startVpnForegroundService(context: Context) {
        val intent = Intent(context, TerracottaVPNService::class.java)
            .setAction(TerracottaVPNService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun stopTerracottaVpn(context: Context) {
        if (TerracottaVPNService.isRunning()) {
            val intent = Intent(context, TerracottaVPNService::class.java)
                .setAction(TerracottaVPNService.ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    @Deprecated("此 API 仅用于调试")
    fun testNativePanic() {
        if (!initialized) return
        TerracottaAndroidAPI.panic()
    }
}
