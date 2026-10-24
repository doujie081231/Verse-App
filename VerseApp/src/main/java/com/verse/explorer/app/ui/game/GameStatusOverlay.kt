package com.verse.explorer.app.ui.game

import android.annotation.SuppressLint
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verse.explorer.app.game.GameLogManager
import com.verse.explorer.app.ui.components.RoseCurveLoader
import com.verse.explorer.app.ui.theme.VerseColors

@SuppressLint("SetTextIsChecked")
class GameStatusOverlay(
    private val activity: ComponentActivity,
    private val logManager: GameLogManager
) {

    private var overlay: FrameLayout? = null
    private var contentRoot: LinearLayout? = null
    private var titleView: TextView? = null
    private var messageView: TextView? = null
    private var progressBar: ProgressBar? = null
    private var errorSection: LinearLayout? = null
    private var repairSection: LinearLayout? = null
    private var closeButton: TextView? = null
    private var onCloseAction: (() -> Unit)? = null

    fun createOverlay(): View {
        val root = FrameLayout(activity).apply {
            setBackgroundColor(0x40000000.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 居中圆角卡片
        val card = FrameLayout(activity).apply {
            val radiusPx = 24 * activity.resources.displayMetrics.density
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(VerseColors.PcBgSecondary.toArgb())
            }
            elevation = 24f
            layoutParams = FrameLayout.LayoutParams(
                (300 * activity.resources.displayMetrics.density).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }
        overlay = card

        // 卡片内部垂直内容
        val inner = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            setPadding(48, 40, 48, 40)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        contentRoot = inner
        card.addView(inner)

        // 右上角关闭按钮：仅在「等待游戏画面」阶段显示，玩家可收起卡片查看黑屏后的实际状态
        closeButton = TextView(activity).apply {
            text = "✕"
            setTextColor(VerseColors.PcTextMuted.toArgb())
            textSize = 16f
            gravity = Gravity.CENTER
            val sizePx = (32 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = (8 * resources.displayMetrics.density).toInt()
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
            setOnClickListener {
                val action = onCloseAction
                hide()
                action?.invoke()
            }
            visibility = View.GONE
        }
        card.addView(closeButton)

        inner.addView(FrameLayout(activity).apply {
            clipChildren = false
            val sizePx = (120 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        }.also { holder ->
            holder.addView(ComposeView(activity).apply {
                clipChildren = false
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }.also { cv ->
                cv.setContent {
                    RoseCurveLoader(
                        modifier = Modifier.fillMaxSize(),
                        color = VerseColors.PcAccent
                    )
                }
            })
        })

        titleView = TextView(activity).apply {
            text = "正在准备启动..."
            setTextColor(VerseColors.PcTextPrimary.toArgb())
            textSize = 18f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (16 * resources.displayMetrics.density).toInt() }
        }
        inner.addView(titleView)

        messageView = TextView(activity).apply {
            text = ""
            setTextColor(VerseColors.PcTextMuted.toArgb())
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }
        inner.addView(messageView)

        progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            val barHeightPx = (3 * activity.resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                barHeightPx
            ).apply { topMargin = 24 }
            max = 100
            progress = 0

            val progressGradient = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    VerseColors.PcAccent.toArgb(),
                    VerseColors.PcPurple.toArgb(),
                    0xFF06B6D4.toInt()
                )
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = barHeightPx / 2f
            }
            val clipDrawable = ClipDrawable(progressGradient, Gravity.LEFT, ClipDrawable.HORIZONTAL)
            val bgDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = barHeightPx / 2f
                setColor(VerseColors.PcBgTertiary.toArgb())
            }
            val layerDrawable = LayerDrawable(arrayOf(bgDrawable, clipDrawable))
            layerDrawable.setId(0, android.R.id.background)
            layerDrawable.setId(1, android.R.id.progress)
            progressDrawable = layerDrawable
        }

        // 进度条不再显示（保留字段以支持 setProgress/setIndeterminate 调用，仅不添加视图）

        root.addView(card)

        root.visibility = View.VISIBLE

        return root
    }

    fun createExportLogButton(): Button {
        return Button(activity).apply {
            text = "导出日志"
            setTextColor(VerseColors.PcAccent.toArgb())
            setBackgroundColor(VerseColors.PcBgSecondary.toArgb())
            textSize = 12f
            setPadding(24, 12, 24, 12)
            setOnClickListener { logManager.exportLog() }
        }
    }

    fun setTitle(text: String) {
        titleView?.text = text
    }

    fun setMessage(text: String?) {
        messageView?.text = text ?: ""
    }

    fun setProgress(progress: Int) {
        progressBar?.progress = progress
    }

    fun setIndeterminate(indeterminate: Boolean) {
        progressBar?.isIndeterminate = indeterminate
    }

    /** 是否显示右上角关闭按钮（等待游戏画面阶段可关闭卡片） */
    fun setClosable(closable: Boolean) {
        closeButton?.visibility = if (closable) View.VISIBLE else View.GONE
    }

    fun setOnClose(action: (() -> Unit)?) {
        onCloseAction = action
    }

    fun showError(title: String, message: String) {
        if (errorSection == null) {
            val errorBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * activity.resources.displayMetrics.density
                setColor(0xFFFEF2F2.toInt())
                setStroke(
                    (1 * activity.resources.displayMetrics.density).toInt(),
                    0xFFFECACA.toInt()
                )
            }
            errorSection = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = errorBg
                setPadding(32, 32, 32, 32)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 32 }
            }
            contentRoot?.addView(errorSection)
        }
        errorSection?.removeAllViews()
        errorSection?.addView(TextView(activity).apply {
            text = title
            setTextColor(VerseColors.PcRed.toArgb())
            textSize = 14f
            paint.isFakeBoldText = true
        })
        errorSection?.addView(TextView(activity).apply {
            text = message
            setTextColor(VerseColors.PcTextSecondary.toArgb())
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        })
        errorSection?.visibility = View.VISIBLE
    }

    fun hideError() {
        errorSection?.visibility = View.GONE
    }

    fun showRepair(message: String, onRepair: (() -> Unit)? = null) {
        if (repairSection == null) {
            val repairBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * activity.resources.displayMetrics.density
                setColor(0xFFEFF6FF.toInt())
                setStroke(
                    (1 * activity.resources.displayMetrics.density).toInt(),
                    0xFFBFDBFE.toInt()
                )
            }
            repairSection = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = repairBg
                setPadding(32, 32, 32, 32)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 32 }
            }
            contentRoot?.addView(repairSection)
        }
        repairSection?.removeAllViews()
        repairSection?.addView(TextView(activity).apply {
            text = message
            setTextColor(0xFF1E40AF.toInt())
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        repairSection?.addView(Button(activity).apply {
            text = "修复"
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6 * activity.resources.displayMetrics.density
                setColor(0xFF2563EB.toInt())
            }
            textSize = 13f
            setPadding(32, 16, 32, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
            setOnClickListener {
                if (onRepair != null) onRepair.invoke() else hideRepair()
            }
        })
        repairSection?.visibility = View.VISIBLE
    }

    fun hideRepair() {
        repairSection?.visibility = View.GONE
    }

    fun show() {
        overlay?.clearAnimation()
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.duration = 250
        overlay?.startAnimation(fadeIn)
        overlay?.visibility = View.VISIBLE
    }

    fun hide() {
        closeButton?.visibility = View.GONE
        overlay?.clearAnimation()
        val fadeOut = AlphaAnimation(1f, 0f)
        fadeOut.duration = 250
        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                overlay?.visibility = View.GONE
                overlay?.clearAnimation()
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        overlay?.startAnimation(fadeOut)
    }
}
