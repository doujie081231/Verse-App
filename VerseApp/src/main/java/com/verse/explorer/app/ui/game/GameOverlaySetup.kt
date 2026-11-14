package com.verse.explorer.app.ui.game

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.tungsten.fcl.setting.MenuSetting
import com.verse.explorer.app.ui.VerseLogger

/**
 * 把游戏内控制面板挂到当前窗口：提示层与面板层都铺满全屏、初始隐藏，
 * 面板的回调分别接到输入处理器（ESC、取消手势、物理鼠标同步）、
 * 截图保存与退出流程上。重复调用不会重复挂载。
 *
 * 菜单悬浮球（参考 ZL2 DraggableGameBall）作为同层的第三个子视图挂在最上方，
 * 由 MenuSetting.showMenuBall 控制显隐；启用时不再弹双指下滑演示遮罩。
 */
internal fun GameActivity.mountInGameControlOverlay() {
    if (inGameControl != null) return
    val overlay = InGameControlOverlay(
        activity = this,
        onEsc = {
            inputHandler.onKeyDown(KeyEvent.KEYCODE_ESCAPE, null)
            inputHandler.onKeyUp(KeyEvent.KEYCODE_ESCAPE, null)
        },
        onScreenshot = {
            val bmp = textureView?.bitmap
            val path = InGameControlOverlay.saveScreenshot(bmp, this)
            Toast.makeText(
                this,
                if (path != null) "截图已保存: $path" else "截图失败",
                Toast.LENGTH_LONG
            ).show()
        },
        onExitGame = {
            VerseLogger.info("从控制面板触发退出游戏", "Control")
            stopGame()
            finish()
        },
        onCancelGameGesture = {
            inputHandler.cancelGesture()
        },
        onPhysicalMouseChanged = {
            inputHandler.syncCapturedPointer()
        }
    )
    val hint = overlay.createHintView()
    val panel = overlay.createPanelView()
    val ball = overlay.createMenuBallView()
    val fullScreen = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    addContentView(hint, fullScreen)
    addContentView(panel, fullScreen)
    addContentView(
        ball,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )
    hint.visibility = View.GONE
    panel.visibility = View.GONE
    ball.visibility = View.GONE
    inGameControl = overlay
    // 启用悬浮球后双指下滑停用，演示遮罩也不该再出现
    if (!MenuSetting.showMenuBall) overlay.showHint()
    overlay.syncMenuBallVisibility()
    VerseLogger.info(
        if (MenuSetting.showMenuBall) "控制面板已挂载（菜单悬浮球唤出）"
        else "控制面板已挂载（双指下滑唤出）",
        "Control"
    )
}
