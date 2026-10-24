package com.verse.explorer.app.ui.game

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.verse.explorer.app.ui.VerseLogger

/**
 * 把游戏内控制面板挂到当前窗口：提示层与面板层都铺满全屏、初始隐藏，
 * 面板的回调分别接到输入处理器（ESC、取消手势、物理鼠标同步）、
 * 截图保存与退出流程上。重复调用不会重复挂载。
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
    addContentView(hint, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    addContentView(panel, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    hint.visibility = View.GONE
    panel.visibility = View.GONE
    inGameControl = overlay
    // 演示遮罩：游戏画面出现后自动弹出双指下滑提示（git 恢复）
    overlay.showHint()
    // 控制面板可经右侧竖胶囊悬浮入口唤出（记忆规范：竖着的整个胶囊容器内放圆形图标按钮）。
    // 【已按要求移除菜单按钮，控制面板仅通过双指下滑手势唤出】
    VerseLogger.info("控制面板已挂载（双指下滑唤出）", "Control")
    }
