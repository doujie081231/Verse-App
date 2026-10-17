package com.verse.explorer.app.download

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 下载保活前台服务：仅承载前台通知与进程优先级，
 * 下载任务本身由下载中心与应用级协程执行。
 * 任务全部完成后由下载中心停止本服务。
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val list = DownloadCenter.items.value
        startForeground(DownloadCenter.NOTIFICATION_ID, DownloadCenter.buildNotification(this, list))
        if (list.isEmpty()) {
            // 服务启动前任务已全部完成（极小任务快速完成）：立即移除通知并停止
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }
}
