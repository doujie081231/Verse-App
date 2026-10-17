package com.verse.explorer.app.terracotta

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.verse.explorer.app.R
import net.burningtnt.terracotta.TerracottaAndroidAPI
import java.io.IOException

/**
 * 陶瓦联机 VPN 服务
 *
 * 并以前台通知展示联机身份与状态；通知被用户划掉后重新发出（REPOST）。
 */
@SuppressLint("VpnServicePolicy")
class TerracottaVPNService : VpnService() {

    companion object {
        const val ACTION_START = "net.burningtnt.terracotta.action.START"
        const val ACTION_STOP = "net.burningtnt.terracotta.action.STOP"
        const val ACTION_REPOST = "net.burningtnt.terracotta.action.REPOST"
        const val ACTION_UPDATE_STATE = "net.burningtnt.terracotta.action.UPDATE_STATE"
        const val EXTRA_STATE_TEXT = "terracotta_state_text"

        private const val TAG = "TerracottaVPNService"
        private const val CHANNEL_ID = "terracotta_vpn_channel"
        private const val VPN_NOTIFICATION_ID = 1
        private const val EXTRA_FROM_DELETE = "from_delete"
        private const val VPN_REPOST_CODE = 40001

        @Volatile
        private var running = false

        fun isRunning(): Boolean = running
    }

    private var notificationManager: NotificationManager? = null
    private var currentStateText: String? = null
    private var isStopping = false
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        val action = intent?.action
        Log.d(TAG, "onStartCommand, action = $action")

        if (notificationManager == null) {
            notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        }

        if (ACTION_STOP == action) {
            isStopping = true
            cleanup()
            stopForeground(true)
            stopSelf()
            return Service.START_NOT_STICKY
        }

        if (ACTION_UPDATE_STATE == action) {
            if (intent?.hasExtra(EXTRA_STATE_TEXT) == true) {
                currentStateText = intent.getStringExtra(EXTRA_STATE_TEXT)
            }
            if (!isStopping) {
                buildVpnNotification()?.let {
                    notificationManager?.notify(VPN_NOTIFICATION_ID, it)
                }
            }
            return Service.START_STICKY
        }

        val fromDelete = intent?.getBooleanExtra(EXTRA_FROM_DELETE, false) ?: false

        if (ACTION_REPOST == action && fromDelete && !isStopping) {
            Log.d(TAG, "Repost VPN notification after user cleared it.")
            if (intent.hasExtra(EXTRA_STATE_TEXT)) {
                currentStateText = intent.getStringExtra(EXTRA_STATE_TEXT)
            }
            val notification = buildVpnNotification() ?: return Service.START_NOT_STICKY
            startForeground(VPN_NOTIFICATION_ID, notification)
            return Service.START_STICKY
        }

        isStopping = false

        createNotificationChannelIfNeeded()

        val notification = buildVpnNotification() ?: return Service.START_NOT_STICKY
        startForeground(VPN_NOTIFICATION_ID, notification)

        val vpnBuilder = Builder().setSession("Terracotta Connection")
        try {
            vpnBuilder.addDisallowedApplication(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
        }

        return try {
            val request = TerracottaAndroidAPI.getPendingVpnServiceRequest()
            vpnInterface = request.startVpnService(vpnBuilder)
            Service.START_STICKY
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start VPN", e)
            cleanup()
            stopForeground(true)
            stopSelf()
            Service.START_NOT_STICKY
        }
    }

    override fun onRevoke() {
        Log.w(TAG, "onRevoke(): preempted; tearing down.")
        isStopping = true
        TerracottaController.setWaiting(this, false)
        cleanup()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        isStopping = true
        TerracottaController.setWaiting(this, false)
        cleanup()
        super.onDestroy()
    }

    private fun createNotificationChannelIfNeeded() {
        val manager = notificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            TerracottaTexts.NOTIFICATION_CHANNEL,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = TerracottaTexts.NOTIFICATION_CHANNEL_DESC
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildVpnNotification(): Notification? {
        val mode = TerracottaController.mode ?: return null
        val modeText = if (mode == TerracottaController.TerracottaMode.HOST) {
            TerracottaTexts.playerKind(ProfileKind.HOST)
        } else {
            TerracottaTexts.playerKind(ProfileKind.GUEST)
        }
        var stateText = currentStateText
        if (stateText == null) {
            val state = TerracottaController.state
            if (state !is TerracottaState.Waiting) {
                stateText = TerracottaTexts.statusText(state)
            }
        }
        val contentText = TerracottaTexts.notificationDesc(modeText, stateText)
        val builder = Notification.Builder(this, CHANNEL_ID)

        val deleteIntent = Intent(this, TerracottaVPNService::class.java)
            .setAction(ACTION_REPOST)
            .putExtra(EXTRA_FROM_DELETE, true)
            .putExtra(EXTRA_STATE_TEXT, currentStateText)
        val deletePendingIntent = PendingIntent.getService(
            this,
            VPN_REPOST_CODE,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(TerracottaTexts.NOTIFICATION_TITLE)
            .setContentText(contentText)
            .setWhen(System.currentTimeMillis())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setDeleteIntent(deletePendingIntent)
        return builder.build()
    }

    private fun cleanup() {
        notificationManager?.cancel(VPN_NOTIFICATION_ID)
        try {
            vpnInterface?.close()
        } catch (_: IOException) {
        }
        vpnInterface = null
        running = false
    }
}
