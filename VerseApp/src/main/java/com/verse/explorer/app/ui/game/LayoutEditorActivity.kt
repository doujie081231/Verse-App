package com.verse.explorer.app.ui.game

import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.ui.theme.VerseExplorerTheme
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.customcontrols.ControlData
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData
import net.kdt.pojavlaunch.customcontrols.ControlLayout
import net.kdt.pojavlaunch.customcontrols.EditorExitable
import net.kdt.pojavlaunch.customcontrols.buttons.ControlJoystick
import net.kdt.pojavlaunch.prefs.LauncherPreferences

/**
 * 独立触控布局编辑器：无需启动游戏即可编辑本地布局 json。
 * 画布承载编辑态 ControlLayout（拖动移动、角柄缩放、点按弹出属性面板、
 * 悬浮 ActionRow），顶部工具栏提供 返回/添加按键/添加方向控制键/保存。
 * 从「设置 → 触控布局 → 本地布局」行的「编辑」入口进入。
 */
class LayoutEditorActivity : ComponentActivity() {

    private lateinit var controlLayout: ControlLayout
    private lateinit var layoutPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Tools.init(this)
        LauncherPreferences.init(this)
        // 布局表达式变量 ${screen_width}/${screen_height} 依赖 CallbackBridge 物理尺寸
        org.lwjgl.glfw.CallbackBridge.physicalHeight = resources.displayMetrics.heightPixels
        org.lwjgl.glfw.CallbackBridge.physicalWidth = resources.displayMetrics.widthPixels

        layoutPath = intent.getStringExtra(EXTRA_LAYOUT_PATH)
            ?: (Tools.CTRLMAP_PATH + "/Default.json")

        val root = FrameLayout(this)
        root.setBackgroundColor(VerseColors.PcBgPrimary.toArgb())

        controlLayout = ControlLayout(this)
        var loaded = true
        try {
            controlLayout.loadLayout(layoutPath)
        } catch (e: Exception) {
            loaded = false
            android.widget.Toast.makeText(this, "布局加载失败：${e.message}", android.widget.Toast.LENGTH_LONG).show()
            // 回退空白布局：编辑器保持可用（添加控件/另存新文件），不再因空数据崩溃
            runCatching { controlLayout.loadLayout(net.kdt.pojavlaunch.customcontrols.CustomControls()) }
        }
        controlLayout.setControlVisible(true)
        controlLayout.setModifiable(true)
        root.addView(
            controlLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val overlay = ComposeView(this)
        overlay.setContent {
            VerseExplorerTheme {
                EditorUi(
                    loaded = loaded,
                    onAddButton = { addControl(isJoystick = false) },
                    onAddJoystick = { addControl(isJoystick = true) },
                    onSave = { controlLayout.openSaveDialog(null) },
                    onBack = { confirmExit() }
                )
            }
        }
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)
    }

    private fun addControl(isJoystick: Boolean) {
        if (isJoystick) {
            controlLayout.addJoystickButton(ControlJoystickData())
        } else {
            controlLayout.addControlButton(ControlData())
        }
        controlLayout.setModifiable(true)
        val target = controlLayout.getButtonChildren().lastOrNull {
            val v = it.getControlView()
            if (isJoystick) v is ControlJoystick else v != null && v !is ControlJoystick
        }
        if (target != null) controlLayout.editControlButton(target)
    }

    private fun confirmExit() {
        if (controlLayout.isModified()) {
            controlLayout.askToExit(object : EditorExitable {
                override fun exitEditor() { finish() }
            })
        } else {
            finish()
        }
    }

    @androidx.compose.runtime.Composable
    private fun EditorUi(
        loaded: Boolean,
        onAddButton: () -> Unit,
        onAddJoystick: () -> Unit,
        onSave: () -> Unit,
        onBack: () -> Unit
    ) {
        BackHandler { confirmExit() }
        Box(Modifier.fillMaxSize()) {
            if (!loaded) {
                Text(
                    "原布局加载失败，已切换为空白布局，保存时请使用新文件名",
                    color = VerseColors.PcTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                )
            }
            // 顶部工具栏：悬浮胶囊，不遮挡画布整行
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .background(VerseColors.PcBgCard.copy(alpha = 0.92f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PcButton(text = "返回", onClick = onBack, variant = PcButtonVariant.SECONDARY)
                PcButton(text = "添加按键", onClick = onAddButton, variant = PcButtonVariant.OUTLINE)
                PcButton(text = "添加方向控制键", onClick = onAddJoystick, variant = PcButtonVariant.OUTLINE)
                PcButton(text = "保存", onClick = onSave, variant = PcButtonVariant.PRIMARY)
            }
        }
    }

    companion object {
        const val EXTRA_LAYOUT_PATH = "layoutPath"

        fun start(context: android.content.Context, layoutPath: String) {
            val intent = android.content.Intent(context, LayoutEditorActivity::class.java)
            intent.putExtra(EXTRA_LAYOUT_PATH, layoutPath)
            context.startActivity(intent)
        }
    }
}
