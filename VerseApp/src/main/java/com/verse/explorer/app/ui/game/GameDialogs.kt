package com.verse.explorer.app.ui.game

import android.view.View
import android.widget.Toast
import com.verse.explorer.app.redstone.RedStoneTunnelDialog
import com.verse.explorer.app.redstone.RedStoneTunnelManager
import com.verse.explorer.app.terracotta.TerracottaMultiplayerDialog

/**
 * 游戏内控制面板的对话框与快捷输入入口。
 * 联机对话框打开期间置位 multiplayerDialogOpen，避免触摸继续透传给游戏；
 * 红石内核缺失时只做提示，不打开对话框。
 */

    /** 控制面板「陶瓦联机」入口：直接唤出游戏内陶瓦联机对话框（未启用时提示去开启） */
internal fun GameActivity.showMultiplayerEntry() {
        if (!getSharedPreferences("third_party", android.content.Context.MODE_PRIVATE).getBoolean("terracotta", false)) {
            Toast.makeText(this, "请先在联机页的「陶瓦联机」中开启多人联机", Toast.LENGTH_LONG).show()
            return
        }
        multiplayerDialogOpen = true
        val dialog = TerracottaMultiplayerDialog(this)
        dialog.onDismissed = { multiplayerDialogOpen = false }
        dialog.show()
    }

    /** 控制面板「红石联机」入口：唤出游戏内红石隧道对话框（红石内核缺失时提示） */
internal fun GameActivity.showRedstoneDialog() {
        if (!RedStoneTunnelManager.isAvailable(this)) {
            Toast.makeText(this, "当前设备没有红石内核（仅支持 arm64 设备）", Toast.LENGTH_LONG).show()
            return
        }
        multiplayerDialogOpen = true
        val dialog = RedStoneTunnelDialog(this)
        dialog.onDismissed = { multiplayerDialogOpen = false }
        dialog.show()
    }

internal fun GameActivity.showQuickInput() {
        touchCharInput?.let {
            it.visibility = View.VISIBLE
            it.bringToFront()
            it.requestFocus()
            it.switchKeyboardState()
        }
    }
