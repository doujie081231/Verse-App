package com.verse.explorer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.verse.explorer.launcher.launch.LaunchConfiguration
import com.verse.explorer.launcher.launch.MinecraftLauncher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Deprecated(
    message = "GameService is not used by the launch flow. Use GameActivity directly.",
    replaceWith = ReplaceWith("GameActivity")
)
class GameService : Service() {
    
    private val binder = GameBinder()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var launcher: MinecraftLauncher? = null
    private var isRunning = false
    
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState
    
    companion object {
        const val CHANNEL_ID = "verse_game_channel"
        const val NOTIFICATION_ID = 1001
        
        fun start(context: Context) {
            val intent = Intent(context, GameService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, GameService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        launcher = MinecraftLauncher(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onDestroy() {
        super.onDestroy()
        stopGame()
        scope.cancel()
    }
    
    fun launchGame(config: LaunchConfiguration): Boolean {
        if (isRunning) return false
        
        return try {
            launcher?.setLaunchCallback(object : com.verse.explorer.launcher.launch.LaunchCallback {
                override fun onGameExit(code: Int) {
                    isRunning = false
                    _gameState.value = GameState(isRunning = false)
                }
            })

            val process = launcher?.launch(config)
            if (process != null) {
                isRunning = true
                _gameState.value = GameState(
                    isRunning = true,
                    pid = process.pid
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    fun stopGame() {
        launcher?.stop()
        isRunning = false
        _gameState.value = GameState(isRunning = false)
    }
    
    fun forceStopGame() {
        launcher?.forceStop()
        isRunning = false
        _gameState.value = GameState(isRunning = false)
    }
    
    fun isGameRunning(): Boolean = isRunning
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Game Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Minecraft game running service"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Verse Explorer X")
            .setContentText("Game is running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    inner class GameBinder : Binder() {
        fun getService(): GameService = this@GameService
    }
}

data class GameState(
    val isRunning: Boolean = false,
    val pid: Int = 0,
    val fps: Int = 0,
    val memoryUsed: Long = 0
)
