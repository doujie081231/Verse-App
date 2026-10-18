package com.verse.explorer.app.game

import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.verse.explorer.app.domain.model.launch.GameLaunchParams
import com.verse.explorer.app.ui.game.GameActivity

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
