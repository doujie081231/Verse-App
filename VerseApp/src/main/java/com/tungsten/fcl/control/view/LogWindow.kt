package com.tungsten.fcl.control.view

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import java.util.ArrayList
import java.util.List

/**
 * 游戏内日志窗口（原样移植自 .fcl-temp/FCL/src/main/java/com/tungsten/fcl/control/view/LogWindow.java，
 * 去掉 fcllibrary FCLTextView 依赖，改用普通 TextView）。
 * appendLog 带 200ms/200 条防刷屏熔断，超过 100 行自动清屏。
 */
class LogWindow(context: Context) : ScrollView(context) {

    private val textView: TextView
    private var lineCount = 0
    private val timeList: MutableList<Long> = ArrayList()
    private val handler = Handler(Looper.getMainLooper())

    init {
        textView = TextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setTextColor(Color.BLACK)
            textSize = 15f
            setLineSpacing(0f, 1f)
        }
        setBackgroundColor(Color.argb(0xCC, 0xFF, 0xFF, 0xFF))
        addView(textView)
        visibility = View.GONE
    }

    fun setVisibility(visibility: Boolean) {
        handler.post {
            this.visibility = if (visibility) View.VISIBLE else View.GONE
            if (!visibility) {
                cleanLog()
            }
        }
    }

    fun appendLog(str: String) {
        if (visibility != View.VISIBLE) {
            return
        }
        lineCount++
        handler.post {
            if (lineCount < 100) {
                textView.append(str)
            } else {
                cleanLog()
            }
            fullScroll(View.FOCUS_DOWN)
        }
        val now = System.currentTimeMillis()
        synchronized(timeList) {
            timeList.removeIf { time -> now - time > 200 }
            timeList.add(now)
            if (timeList.size > 200) {
                setVisibility(false)
            }
        }
    }

    fun cleanLog() {
        textView.text = ""
        lineCount = 0
    }
}
