package com.verse.explorer.app.update

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.verse.explorer.app.MainActivity
import com.verse.explorer.app.R
import com.verse.explorer.app.data.UpdateChecker
import kotlinx.coroutines.runBlocking

/**
 * 版本更新通知：定时在后台检查官网更新配置，有新版本就发通知（应用未打开也生效）。
 *
 * 触发链：AlarmManager 每 6 小时发一次广播 → [UpdateCheckReceiver] 请求配置 →
 * 比本机 versionCode 新且未通知过该版本 → 发通知；开机后由 [UpdateBootReceiver] 重新排程。
 */
object UpdateNotifier {

    const val CHANNEL_ID = "verse_update"
    private const val CHANNEL_NAME = "版本更新"
    private const val NOTIFICATION_ID = 0x5645
    private const val PREFS = "update_notify"
    private const val KEY_LAST_NOTIFIED = "last_notified_version"
    private const val REQUEST_CODE = 0x5645

    /** 检查间隔：6 小时（系统会做非精确合并，不影响时效） */
    private const val INTERVAL_MS = 6 * 60 * 60 * 1000L

    /** 通知渠道（Android 8+ 必须） */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "检测到启动器有新版本时通知"
        }
        manager.createNotificationChannel(channel)
    }

    /** 通知权限是否已授予（Android 13+ 需要运行时授权） */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /** 弹出「有新版本」通知；点击打开启动器 */
    fun notifyUpdate(context: Context, info: UpdateChecker.UpdateInfo) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val detail = info.changelog.firstOrNull()?.takeIf { it.isNotBlank() }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATE, true)
        }
        val pending = PendingIntent.getActivity(
            context, REQUEST_CODE, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Verse Explorer X ${info.versionName} 已发布")
            .setContentText("快去更新体验吧！")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    detail?.let { "$it\n快去更新体验吧！" } ?: "快去更新体验吧！"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    /** 已通知过的版本号（避免同一版本重复打扰） */
    fun lastNotifiedVersion(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LAST_NOTIFIED, 0)

    fun markNotified(context: Context, versionCode: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LAST_NOTIFIED, versionCode).apply()
    }

    /**
     * 后台检查一次：有新版本且该版本还没通知过就发通知。
     * 不依赖界面，广播接收器与前台启动都能调用。
     */
    fun checkAndNotify(context: Context) {
        val app = context.applicationContext
        val info = runBlocking { UpdateChecker.check() } ?: return
        if (info.versionCode <= lastNotifiedVersion(app)) return
        notifyUpdate(app, info)
        markNotified(app, info.versionCode)
    }

    /** 排定周期性后台检查（幂等，重复调用只会覆盖同一个 PendingIntent） */
    fun schedule(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = alarmIntent(context)
        val first = System.currentTimeMillis() + INTERVAL_MS
        runCatching {
            manager.setInexactRepeating(
                AlarmManager.RTC, first, INTERVAL_MS, pending
            )
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { manager.cancel(alarmIntent(context)) }
    }

    private fun alarmIntent(context: Context): PendingIntent {
        val intent = Intent(context, UpdateCheckReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 打开启动器时用于定位到更新提示的 extra */
    const val EXTRA_OPEN_UPDATE = "open_update_dialog"
}

/** 定时检查广播：应用未运行时也会被系统唤起 */
class UpdateCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        Thread {
            try {
                UpdateNotifier.checkAndNotify(context)
            } catch (_: Throwable) {
                // 后台检查失败静默：下次定时再试，不能因网络异常弹崩溃
            } finally {
                runCatching { pending.finish() }
            }
        }.start()
    }
}

/** 开机后重新排程（AlarmManager 的定时任务在重启后会被清空） */
class UpdateBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            UpdateNotifier.schedule(context)
        }
    }
}
