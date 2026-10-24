package com.verse.explorer.app.ui.game

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

/**
 * 控制面板设置行构建器：把「标签 + 控件」这类重复行的排版集中一处，
 * 各分区只描述业务含义，不再重复尺寸与配色。尺寸按屏幕密度换算，
 * 颜色由面板在构造时传入，保持与控制面板整体配色一致。
 */
internal class ControlRowBuilder(
    private val activity: GameActivity,
    private val accent: Int,
    private val rowBg: Int,
    private val textColor: Int
) {
    private val density = activity.resources.displayMetrics.density

    private fun dp(v: Float): Int = (v * density).toInt()

    // ===================== 通用行构建器 =====================

    /** 分区标题（Verse 卡片分组：3dp 主题色竖条 + 13sp 加粗标题，分组间留白） */
    internal fun sectionHeader(header: String): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10f); bottomMargin = dp(4f) }
            addView(View(activity).apply {
                setBackgroundColor(accent)
                layoutParams = LinearLayout.LayoutParams(dp(3f), dp(14f)).apply { marginEnd = dp(6f) }
            })
            addView(TextView(activity).apply {
                text = header
                setTextColor(textColor)
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                isSingleLine = true
            })
        }
    }

    /** 对齐设置行（Verse 行卡片：圆角深灰底、标签 13sp 靠左 + 控件靠右） */
    internal fun alignedRow(label: String, control: View): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10f).toFloat()
                setColor(rowBg)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3f); bottomMargin = dp(3f) }
            addView(TextView(activity).apply {
                text = label
                setTextColor(textColor)
                textSize = 13f
                isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { gravity = Gravity.CENTER_VERTICAL }
            })
            addView(control)
        }
    }

    internal fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val sw = Switch(activity).apply {
            isChecked = checked
            textSize = 12f
            trackTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }
        return alignedRow(label, sw)
    }

    internal fun spinnerRow(
        label: String,
        items: List<String>,
        initialIndex: Int,
        onSelected: (Int) -> Unit
    ): View {
        val spinner = Spinner(activity).apply {
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, items).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            this.adapter = adapter
            if (initialIndex in items.indices) setSelection(initialIndex)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                // AdapterView 在 attach 时会自动回调一次 onItemSelected；该次并非用户操作，
                // 若触发 loadLayout 会重载当前布局并把摇杆 detach（库内 100ms 回调时父容器
                // 已为 null → NPE 崩溃），因此首回调只记录、仅在位置变化时视为用户选择
                private var lastSelected: Int? = null

                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val last = lastSelected
                    lastSelected = position
                    if (last == null || last == position) return
                    onSelected(position)
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        return alignedRow(label, spinner)
    }

    internal fun seekbarRow(
        label: String,
        min: Int,
        max: Int,
        initial: Int,
        suffix: String,
        onChanged: (Int) -> Unit
    ): View {
        val valueText = TextView(activity).apply {
            setTextColor(textColor)
            textSize = 12f
            isSingleLine = true
            text = "$initial$suffix"
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3f); bottomMargin = dp(3f) }
            addView(alignedRow(label, valueText))
            addView(SeekBar(activity).apply {
                this.min = min
                this.max = max
                progress = initial.coerceIn(min, max)
                progressTintList = ColorStateList.valueOf(accent)
                thumbTintList = ColorStateList.valueOf(accent)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                        valueText.text = "$progress$suffix"
                        if (fromUser) onChanged(progress)
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
            })
        }
        return container
    }

    /** 按钮行（Verse 主按钮：标签左 + 主题色填充圆角按钮右） */
    internal fun buttonRow(label: String, buttonText: String, onClick: () -> Unit): View {
        val btn = TextView(activity).apply {
            text = buttonText
            setTextColor(Color.parseColor("#141414"))
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            isSingleLine = true
            gravity = Gravity.CENTER
            setPadding(dp(16f), dp(7f), dp(16f), dp(7f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8f).toFloat()
                setColor(accent)
            }
            setOnClickListener { onClick() }
        }
        return alignedRow(label, btn)
    }
}
