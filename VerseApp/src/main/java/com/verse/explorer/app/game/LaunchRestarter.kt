package com.verse.explorer.app.game

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.verse.explorer.app.domain.model.launch.GameLaunchParams
import com.verse.explorer.app.ui.game.GameActivity

/**
 * 冷启动调度：先在系统闹钟里挂起目标 Activity 的 PendingIntent，
 * 再结束当前进程。进程销毁后闹钟仍会触发，由系统拉起全新进程，
 * 避免在已初始化过 JVM 的进程里再次创建 JVM。
 */
fun scheduleColdStart(context: Context, intent: Intent, requestCode: Int) {
    val pending = PendingIntent.getActivity(
        context.applicationContext,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarm = context.applicationContext.getSystemService(AlarmManager::class.java)
    // 留出进程收尾时间；setWindow 无需精确闹钟权限
    alarm?.setWindow(AlarmManager.RTC, System.currentTimeMillis() + 1200, 500, pending)
}

/** 延迟结束当前进程，确保日志与界面状态先落盘 */
fun killProcessSoon(delayMillis: Long = 400L) {
    Handler(Looper.getMainLooper()).postDelayed({
        android.os.Process.killProcess(android.os.Process.myPid())
    }, delayMillis)
}

class LaunchRestarter(private val activity: ComponentActivity) {

    fun buildRestartIntent(params: GameLaunchParams): Intent {
        return Intent(activity, GameActivity::class.java).apply {
            putExtra("versionId", params.versionId)
            putExtra("gameDir", params.gameDirPath)
            putExtra("javaHome", params.javaHome)
            putExtra("javaVersion", params.javaVersion)
            putExtra("maxMemory", params.maxMemory)
            putExtra("minMemory", params.minMemory)
            putExtra("gcType", params.gcType)
            putExtra("customJvmArgs", params.customJvmArgs)
            putExtra("width", params.gameWidth)
            putExtra("height", params.gameHeight)
            putExtra("renderer", params.renderer)
            putExtra("launchMethod", params.launchMethod)
            putExtra("username", params.username)
            putExtra("uuid", params.uuid)
            putExtra("multiplayerMode", params.isMultiplayer)
            putExtra("accessToken", params.accessToken)
            putExtra("userType", params.userType)
            putExtra("authlibServerUrl", params.authlibServerUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    fun showMissingParamsToast() {
        Toast.makeText(activity, "无法重启：缺少启动参数", Toast.LENGTH_SHORT).show()
    }
}
