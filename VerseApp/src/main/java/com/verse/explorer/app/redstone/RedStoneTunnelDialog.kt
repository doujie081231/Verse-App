package com.verse.explorer.app.redstone

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import com.verse.explorer.app.ui.theme.VerseColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 游戏内红石联机菜单
 *
 * 与陶瓦的 TerracottaMultiplayerDialog 对应的经游戏内入口：
 * 玩家在游戏中（对局域网开放后）直接拉起/管理红石隧道，
 * 无需退出游戏回到启动器页面。
 */
class RedStoneTunnelDialog(private val activity: Activity) : Dialog(activity) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 关闭时的外部回调（与陶瓦对话框一致，供 GameActivity 清理输入互斥） */
    var onDismissed: (() -> Unit)? = null

    private lateinit var statusContainer: LinearLayout
    private lateinit var configContainer: LinearLayout
    private lateinit var chipsRow: LinearLayout
    private lateinit var nodeEdit: EditText
    private lateinit var portEdit: EditText
    private lateinit var logsText: TextView

    private val selectedNodeName
        get() = RedStoneTunnelManager.nodes.value.find { it.address == currentNode() }?.name

    init {
        setCancelable(false)
        setupWindow()

        RedStoneTunnelManager.refreshNodes()

        scope.launch {
            RedStoneTunnelManager.state.collect { renderState(it) }
        }
        scope.launch {
            RedStoneTunnelManager.logs.collect { renderLogs(it) }
        }
        scope.launch {
            RedStoneTunnelManager.nodes.collect { renderChips(it) }
        }

        setOnDismissListener {
            scope.cancel()
            onDismissed?.invoke()
        }
    }

    private fun setupWindow() {
        val width = (activity.resources.displayMetrics.widthPixels * 0.7).toInt()
        val height = (activity.resources.displayMetrics.heightPixels * 0.9).toInt()
        window?.setLayout(width, height)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground(VerseColors.PcBgSecondary.toArgb(), 16.dp(context))
            setPadding(20.dp(context), 16.dp(context), 20.dp(context), 16.dp(context))
        }
        setContentView(root)

        root.addView(TextView(context).apply {
            text = "红石联机"
            setTextColor(VerseColors.PcTextPrimary.toArgb())
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        })

        statusContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp(context) }
        }
        root.addView(statusContainer)

        configContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp(context) }
        }
        root.addView(configContainer)

        buildConfigUI()

        root.addView(TextView(context).apply {
            text = "最近日志"
            setTextColor(VerseColors.PcTextSecondary.toArgb())
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp(context) }
        })
        logsText = TextView(context).apply {
            setTextColor(VerseColors.PcTextMuted.toArgb())
            textSize = 10f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { topMargin = 6.dp(context) }
        }
        root.addView(logsText)

        root.addView(TextView(context).apply {
            text = "退出"
            setTextColor(VerseColors.PcTextPrimary.toArgb())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(16.dp(context), 8.dp(context), 16.dp(context), 8.dp(context))
            background = cardBackground(VerseColors.PcBgTertiary.toArgb(), 8.dp(context))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp(context) }
            setOnClickListener { dismiss() }
        })

        renderState(RedStoneTunnelManager.state.value)
        renderLogs(RedStoneTunnelManager.logs.value)
        renderChips(RedStoneTunnelManager.nodes.value)
    }

    /** 配置区：节点输入 + 快捷节点 + 端口 + 创建/关闭按钮（只在状态允许时显示） */
    private fun buildConfigUI() {
        configContainer.removeAllViews()

        val context = context
        val canConfigure = RedStoneTunnelManager.state.value.let {
            it is RedStoneTunnelManager.State.Idle ||
                it is RedStoneTunnelManager.State.Closed ||
                it is RedStoneTunnelManager.State.Failed
        }

        configContainer.addView(TextView(context).apply {
            text = if (canConfigure) "创建隧道" else "隧道运行中"
            setTextColor(VerseColors.PcTextPrimary.toArgb())
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        })

        if (canConfigure) {
            configContainer.addView(EditText(context).apply {
                hint = "中转节点" + (selectedNodeName?.let { "（$it）" } ?: "")
                setText(currentNode())
                setSingleLine(true)
                setTextColor(VerseColors.PcTextPrimary.toArgb())
                setHintTextColor(VerseColors.PcTextMuted.toArgb())
                background = cardBackground(VerseColors.PcBgTertiary.toArgb(), 8.dp(context))
                setPadding(12.dp(context), 10.dp(context), 12.dp(context), 10.dp(context))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8.dp(context) }
            }.also { nodeEdit = it })

            chipsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 6.dp(context) }
            }
            configContainer.addView(chipsRow)

            configContainer.addView(EditText(context).apply {
                hint = "本地 MC 端口（对局域网开放后游戏显示的端口）"
                setText("25565")
                setSingleLine(true)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setTextColor(VerseColors.PcTextPrimary.toArgb())
                setHintTextColor(VerseColors.PcTextMuted.toArgb())
                background = cardBackground(VerseColors.PcBgTertiary.toArgb(), 8.dp(context))
                setPadding(12.dp(context), 10.dp(context), 12.dp(context), 10.dp(context))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8.dp(context) }
            }.also { portEdit = it })

            configContainer.addView(TextView(context).apply {
                text = "好友无需任何安装，在多人游戏中直接连接生成的地址。"
                setTextColor(VerseColors.PcTextMuted.toArgb())
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 6.dp(context) }
            })

            configContainer.addView(actionCard("创建隧道", "连接中转节点并生成联机地址") {
                RedStoneTunnelManager.start(
                    context,
                    nodeEdit.text.toString().trim(),
                    portEdit.text.toString().toIntOrNull() ?: 25565
                )
            })
        } else {
            configContainer.addView(TextView(context).apply {
                text = "关闭后地址立即失效，好友连接会断开。"
                setTextColor(VerseColors.PcTextMuted.toArgb())
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 6.dp(context) }
            })
            configContainer.addView(actionCard("关闭隧道", "停止红石内核并关闭房间") {
                RedStoneTunnelManager.stop()
            })
        }
    }

    /** 状态区（按状态流重建） */
    private fun renderState(state: RedStoneTunnelManager.State) {
        statusContainer.removeAllViews()
        val context = context

        fun card(text: String, color: androidx.compose.ui.graphics.Color, bgAlpha: Float = 0.10f) {
            statusContainer.addView(TextView(context).apply {
                this.text = text
                setTextColor(color.toArgb())
                textSize = 12f
                background = cardBackground(color.copy(alpha = bgAlpha).toArgb(), 10.dp(context))
                setPadding(14.dp(context), 10.dp(context), 14.dp(context), 10.dp(context))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            })
        }

        when (state) {
            is RedStoneTunnelManager.State.Starting -> {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 6.dp(context), 0, 6.dp(context))
                }
                row.addView(ProgressBar(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        18.dp(context), 18.dp(context)
                    ).apply { marginEnd = 10.dp(context) }
                })
                row.addView(TextView(context).apply {
                    text = "正在连接节点 ${state.node}..."
                    setTextColor(VerseColors.PcTextSecondary.toArgb())
                    textSize = 13f
                })
                statusContainer.addView(row)
            }
            is RedStoneTunnelManager.State.Reconnecting -> {
                card(
                    "${state.reason}（${state.attempt}/${state.maxAttempts}）",
                    VerseColors.PcOrange
                )
            }
            is RedStoneTunnelManager.State.Open -> {
                statusContainer.addView(TextView(context).apply {
                    text = "隧道已建立，联机地址（点击复制）"
                    setTextColor(VerseColors.PcTextSecondary.toArgb())
                    textSize = 11f
                })
                statusContainer.addView(TextView(context).apply {
                    text = state.address
                    setTextColor(VerseColors.PcGreen.toArgb())
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 4.dp(context), 0, 0)
                    setOnClickListener {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("redstone_address", state.address))
                        Toast.makeText(context, "已复制联机地址", Toast.LENGTH_SHORT).show()
                    }
                })
                statusContainer.addView(TextView(context).apply {
                    text = "好友在多人游戏中直接连接该地址；房主需保持「对局域网开放」。"
                    setTextColor(VerseColors.PcTextMuted.toArgb())
                    textSize = 11f
                    setPadding(0, 6.dp(context), 0, 0)
                })
            }
            is RedStoneTunnelManager.State.Closed -> card(state.message, VerseColors.PcOrange)
            is RedStoneTunnelManager.State.Failed -> card(state.message, VerseColors.PcRed)
            RedStoneTunnelManager.State.Idle -> {}
        }

        // 状态变化后配置区按钮含义不同，重建
        val configMarker = state::class.simpleName
        if (configMarker != (configContainer.tag as? String)) {
            configContainer.tag = configMarker
            val nodeText = if (::nodeEdit.isInitialized) nodeEdit.text.toString() else currentNode()
            val portText = if (::portEdit.isInitialized) portEdit.text.toString() else "25565"
            buildConfigUI()
            if (::nodeEdit.isInitialized) nodeEdit.setText(nodeText)
            if (::portEdit.isInitialized) portEdit.setText(portText)
            renderChips(RedStoneTunnelManager.nodes.value)
        }
    }

    /** 快捷节点行（来自节点注册表） */
    private fun renderChips(nodes: List<RedStoneTunnelManager.RedStoneNode>) {
        if (!::chipsRow.isInitialized) return
        chipsRow.removeAllViews()
        val context = context
        nodes.forEach { n ->
            val selected = currentNode() == n.address
            val bg = if (selected) {
                VerseColors.PcAccent.copy(alpha = 0.12f).toArgb()
            } else {
                VerseColors.PcBgTertiary.toArgb()
            }
            val fg = (if (selected) VerseColors.PcAccent else VerseColors.PcTextSecondary).toArgb()
            chipsRow.addView(TextView(context).apply {
                text = n.name
                textSize = 11f
                setTextColor(fg)
                setPadding(10.dp(context), 4.dp(context), 10.dp(context), 4.dp(context))
                background = cardBackground(bg, 6.dp(context))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 6.dp(context) }
                setOnClickListener { nodeEdit.setText(n.address) }
            })
        }
    }

    private fun renderLogs(logs: List<String>) {
        if (!::logsText.isInitialized) return
        logsText.text = logs.takeLast(20).joinToString("\n")
    }

    private fun currentNode(): String =
        if (::nodeEdit.isInitialized) nodeEdit.text.toString().trim()
        else RedStoneTunnelManager.DEFAULT_NODE.address

    private fun actionCard(title: String, sub: String, onClick: () -> Unit): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(context), 10.dp(context), 14.dp(context), 10.dp(context))
            background = cardBackground(VerseColors.PcBgTertiary.toArgb(), 10.dp(context))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp(context) }
            setOnClickListener { onClick() }
            isClickable = true
            isFocusable = true
        }
        box.addView(TextView(context).apply {
            text = title
            setTextColor(VerseColors.PcAccent.toArgb())
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(context).apply {
            text = sub
            setTextColor(VerseColors.PcTextMuted.toArgb())
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 3.dp(context) }
        })
        return box
    }

    companion object {
        private fun Int.dp(context: Context): Int =
            (this * context.resources.displayMetrics.density).toInt()

        private fun cardBackground(color: Int, radiusPx: Int): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx.toFloat()
                setColor(color)
            }
    }
}
