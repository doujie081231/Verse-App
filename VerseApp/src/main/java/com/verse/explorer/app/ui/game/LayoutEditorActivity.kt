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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
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

        // 工具条状态：可拖动 + 可收起。
        // 原来固定在屏幕顶部，正压在布局顶部那排按键上（视角/调试等），挪不动也点不到，
        // 用户反馈「挡住屏幕上方空白，严重影响修改按键」：工具做成
        // 可拖动的悬浮件，随时挪开，也可以收成一个小球彻底让出画布。
        var expanded by remember { mutableStateOf(true) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var barSize by remember { mutableStateOf(IntSize.Zero) }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxWidthPx = constraints.maxWidth
            val maxHeightPx = constraints.maxHeight
            val marginPx = with(LocalDensity.current) { 8.dp.toPx() }

            /** 把工具条约束在屏幕内（按当前测量到的尺寸夹取） */
            fun clampOffset(raw: Offset): Offset {
                if (barSize.width == 0 || barSize.height == 0) return raw
                // 工具条以 TopCenter 为基准，offset.x 的正负各有一半可用空间
                val maxShiftX = ((maxWidthPx - barSize.width) / 2f - marginPx).coerceAtLeast(0f)
                val maxShiftY = (maxHeightPx - barSize.height - marginPx * 2).coerceAtLeast(0f)
                return Offset(
                    raw.x.coerceIn(-maxShiftX, maxShiftX),
                    raw.y.coerceIn(0f, maxShiftY)
                )
            }

            val dragModifier = Modifier.pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offset = clampOffset(offset + dragAmount)
                }
            }

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

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                    .onGloballyPositioned { barSize = it.size }
                    .background(VerseColors.PcBgCard.copy(alpha = 0.92f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 拖动手柄：按住这里拖，整条工具跟着走（按钮本身保持原来的点击行为）
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VerseColors.PcBgTertiary)
                        .then(dragModifier)
                        .clickable { expanded = !expanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.DragIndicator else Icons.Filled.Settings,
                        contentDescription = if (expanded) "收起工具条" else "展开工具条",
                        tint = VerseColors.PcTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (expanded) {
                    PcButton(text = "返回", onClick = onBack, variant = PcButtonVariant.SECONDARY)
                    PcButton(text = "添加按键", onClick = onAddButton, variant = PcButtonVariant.OUTLINE)
                    PcButton(text = "添加方向控制键", onClick = onAddJoystick, variant = PcButtonVariant.OUTLINE)
                    PcButton(text = "保存", onClick = onSave, variant = PcButtonVariant.PRIMARY)
                }
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
