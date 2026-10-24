package com.verse.explorer.app.ui.game

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import com.verse.explorer.app.crash.Pc2Analysis
import com.verse.explorer.app.crash.VersePc2CrashAnalyzer
import com.verse.explorer.app.game.killProcessSoon
import com.verse.explorer.app.game.scheduleColdStart
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.app.ui.theme.VerseColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashReportActivity : Activity() {

    private var exitCode = 0
    private var gameDirPath: String? = null
    private var appCrash = false
    private var analysis: Pc2Analysis? = null
    private var reportText = ""

    private lateinit var contentColumn: LinearLayout
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exitCode = intent.getIntExtra(EXTRA_CODE, 0)
        gameDirPath = intent.getStringExtra(EXTRA_GAME_DIR)
        appCrash = intent.getBooleanExtra(EXTRA_APP_CRASH, false)

        setupWindow()
        setContentView(buildLayout())
        if (appCrash) {
            renderAppCrash()
        } else {
            startAnalysis()
        }
    }

    private fun setupWindow() {
        window.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun card(color: Int, radiusDp: Float, paddingDp: Float): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = radiusDp * resources.displayMetrics.density
                setColor(color)
            }
            setPadding(
                (paddingDp * resources.displayMetrics.density).toInt(),
                (paddingDp * resources.displayMetrics.density).toInt(),
                (paddingDp * resources.displayMetrics.density).toInt(),
                (paddingDp * resources.displayMetrics.density).toInt()
            )
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = value
            setTextColor(color)
            textSize = size
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun buildLayout(): View {
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = 14 * density
                setColor(VerseColors.PcBgPrimary.toArgb())
            }
            setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
        }

        // 左侧：分析内容
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
        }
        contentColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(contentColumn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            marginEnd = (10 * density).toInt()
        })

        val actions = card(VerseColors.PcBgSecondary.toArgb(), 10f, 10f).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        actions.addView(text("崩溃分析", 16f, VerseColors.PcTextPrimary.toArgb(), bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, (4 * density).toInt(), 0, (10 * density).toInt())
        })

        fun actionButton(label: String, accent: Boolean, onClick: () -> Unit): Button =
            Button(this).apply {
                this.text = label
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (accent) VerseColors.PcAccentText.toArgb() else VerseColors.PcTextPrimary.toArgb())
                background = GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(if (accent) VerseColors.PcAccent.toArgb() else VerseColors.PcBgTertiary.toArgb())
                }
                setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8 * density).toInt() }
                setOnClickListener { onClick() }
            }

        actions.addView(
            if (appCrash) actionButton("重新启动应用", true) { restartApp() }
            else actionButton("重启游戏", true) { restartGame() }
        )
        actions.addView(actionButton("导出分析日志", false) { exportReport() })
        actions.addView(actionButton("复制分析结果", false) { copyReport() })
        actions.addView(actionButton("退出", false) {
            // 当前进程里的 JVM 已失效，结束进程避免下次在同一进程重复创建 JVM
            finishAffinity()
            killProcessSoon()
        })

        val tip = text(
            "提示：崩溃日志已保存，重启前可先导出分析结果。\n重启游戏将重新启动当前版本。",
            10f, VerseColors.PcTextMuted.toArgb()
        )
        actions.addView(tip)

        root.addView(actions, LinearLayout.LayoutParams((150 * density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT))
        return root
    }

    private fun startAnalysis() {
        contentColumn.addView(text("正在分析崩溃原因…", 14f, VerseColors.PcTextSecondary.toArgb()))
        Thread {
            val gameDir = gameDirPath?.let { File(it) }
            val debugLog = File(filesDir, "verse_debug.log")
            val result = runCatching { VersePc2CrashAnalyzer.analyze(gameDir, debugLog) }
                .getOrNull()
            runOnUiThread { render(result) }
        }.start()
    }

    /** 应用自身崩溃：读取 VerseApplication 写入的最新崩溃堆栈直接显示 */
    private fun renderAppCrash() {
        contentColumn.removeAllViews()
        val density = resources.displayMetrics.density

        contentColumn.addView(text("应用遇到内部错误", 20f, VerseColors.PcRed.toArgb(), bold = true))
        contentColumn.addView(
            text("应用已捕获本次崩溃，不会直接退出。可重启应用继续使用，或导出日志反馈。", 12f, VerseColors.PcTextMuted.toArgb()).apply {
                setPadding(0, (2 * density).toInt(), 0, (10 * density).toInt())
            }
        )

        val crashFile = File(getExternalFilesDir(null) ?: filesDir, "verse_crash.log")
        val stack = runCatching {
            if (crashFile.exists()) {
                val all = crashFile.readText()
                val idx = all.lastIndexOf("===== Verse Crash =====")
                if (idx >= 0) all.substring(idx).take(6000) else all.take(6000)
            } else ""
        }.getOrDefault("")
        reportText = stack.ifBlank { "未找到崩溃堆栈（verse_crash.log）" }

        contentColumn.addView(card(VerseColors.PcBgCard.toArgb(), 10f, 12f).apply {
            addView(text("崩溃堆栈（最新一次）", 14f, VerseColors.PcTextPrimary.toArgb(), bold = true).apply {
                setPadding(0, 0, 0, (6 * density).toInt())
            })
            addView(
                text(reportText, 10f, VerseColors.PcTextSecondary.toArgb()).apply {
                    typeface = Typeface.MONOSPACE
                }
            )
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (10 * density).toInt()
        })
    }

    /** 应用崩溃模式：以全新进程重启整个应用（回主页） */
    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: run {
            finishAffinity()
            killProcessSoon()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        scheduleColdStart(this, intent, APP_RESTART_REQUEST_CODE)
        finishAffinity()
        killProcessSoon()
    }

    private fun render(result: Pc2Analysis?) {
        contentColumn.removeAllViews()
        val density = resources.displayMetrics.density

        contentColumn.addView(text("游戏已崩溃", 20f, VerseColors.PcRed.toArgb(), bold = true))
        contentColumn.addView(text("退出码: $exitCode", 12f, VerseColors.PcTextMuted.toArgb()).apply {
            setPadding(0, (2 * density).toInt(), 0, (10 * density).toInt())
        })

        if (result == null) {
            contentColumn.addView(card(VerseColors.PcBgCard.toArgb(), 10f, 12f).apply {
                addView(text("崩溃分析失败，请导出日志反馈。", 13f, VerseColors.PcTextSecondary.toArgb()))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (10 * density).toInt()
            })
            return
        }
        analysis = result

        // 崩溃原因列表
        if (result.crashReasons.isNotEmpty()) {
            contentColumn.addView(card(VerseColors.PcBgCard.toArgb(), 10f, 12f).apply {
                addView(text("崩溃原因", 15f, VerseColors.PcTextPrimary.toArgb(), bold = true).apply {
                    setPadding(0, 0, 0, (6 * density).toInt())
                })
                result.crashReasons.forEach { (reason, additional) ->
                    addView(text("• ${reason.cn}", 13f, VerseColors.PcAccent.toArgb(), bold = true).apply {
                        setPadding(0, (3 * density).toInt(), 0, (1 * density).toInt())
                    })
                    additional.filter { it.isNotBlank() }.forEach { line ->
                        addView(text(line, 11f, VerseColors.PcTextSecondary.toArgb()).apply {
                            setPadding((12 * density).toInt(), 0, 0, (2 * density).toInt())
                        })
                    }
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (10 * density).toInt()
            })
        }

        // 建议与说明
        contentColumn.addView(card(VerseColors.PcBgCard.toArgb(), 10f, 12f).apply {
            addView(text("解决方案", 15f, VerseColors.PcTextPrimary.toArgb(), bold = true).apply {
                setPadding(0, 0, 0, (6 * density).toInt())
            })
            addView(text(result.detail, 13f, VerseColors.PcTextSecondary.toArgb()).apply {
                lineHeight = (18 * density).toInt()
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (10 * density).toInt()
        })

        // 分析使用的文件
        if (result.files.isNotEmpty()) {
            contentColumn.addView(card(VerseColors.PcBgCard.toArgb(), 10f, 12f).apply {
                addView(text("分析使用的文件", 13f, VerseColors.PcTextPrimary.toArgb(), bold = true).apply {
                    setPadding(0, 0, 0, (4 * density).toInt())
                })
                result.files.forEach { f ->
                    addView(text(f, 11f, VerseColors.PcTextMuted.toArgb()))
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (10 * density).toInt()
            })
        }

        buildReportText(result)
        scroll.post { scroll.scrollTo(0, 0) }
    }

    /** 拼接导出/复制的报告文本 */
    private fun buildReportText(result: Pc2Analysis) {
        val sb = StringBuilder()
        sb.appendLine("Verse 崩溃分析报告")
        sb.appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("退出码: $exitCode")
        sb.appendLine()
        if (result.crashReasons.isNotEmpty()) {
            sb.appendLine("== 崩溃原因 ==")
            result.crashReasons.forEach { (reason, additional) ->
                sb.appendLine("• ${reason.cn}")
                additional.filter { it.isNotBlank() }.forEach { sb.appendLine("  $it") }
            }
            sb.appendLine()
        }
        sb.appendLine("== 解决方案 ==")
        sb.appendLine(result.detail)
        sb.appendLine()
        sb.appendLine("== 分析文件 ==")
        result.files.forEach { sb.appendLine(it) }
        reportText = sb.toString()
    }

    private fun restartGame() {
        VerseLogger.info("崩溃页面：重启游戏", "CrashReport")
        val cb = onRestartRequested
        finish()
        cb?.invoke()
    }

    private fun exportReport() {
        val dir = File(getExternalFilesDir(null), "VerseExplorerX/crash_reports")
        dir.mkdirs()
        val file = File(dir, "crash_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt")
        runCatching {
            file.writeText(reportText.ifBlank { "（分析尚未完成）" })
            Toast.makeText(this, "已导出: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "导出失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyReport() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("verse_crash_report", reportText))
        Toast.makeText(this, "分析结果已复制", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val EXTRA_CODE = "exit_code"
        private const val EXTRA_GAME_DIR = "game_dir"
        private const val EXTRA_APP_CRASH = "app_crash"
        private const val APP_RESTART_REQUEST_CODE = 0x5648

        /** 重启回调：由 GameActivity 注册（崩溃页面本身不持有游戏启动参数） */
        @Volatile
        var onRestartRequested: (() -> Unit)? = null

        fun show(activity: Activity, exitCode: Int, gameDir: File?, onRestart: () -> Unit) {
            onRestartRequested = onRestart
            val intent = Intent(activity, CrashReportActivity::class.java).apply {
                putExtra(EXTRA_CODE, exitCode)
                putExtra(EXTRA_GAME_DIR, gameDir?.absolutePath)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        }

        /** 应用自身崩溃：显示堆栈页（从崩溃 handler 调用，进程即将结束） */
        fun showForApp(context: Context) {
            val intent = Intent(context, CrashReportActivity::class.java).apply {
                putExtra(EXTRA_APP_CRASH, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
        }
    }
}
