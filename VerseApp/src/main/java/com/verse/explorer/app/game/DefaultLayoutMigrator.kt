package com.verse.explorer.app.game

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * 已部署的默认触控布局补丁。
 *
 * 布局文件在首次启动时从 assets 复制到 `controlmap/Default.json`，之后不再跟随 APK 更新
 * （用户的调整不能被覆盖）。新增默认按键只能走一次性迁移：读现有 JSON、补上缺的控件、
 * 把位置冲突的旧控件挪开，然后写回。标记文件保证只执行一次。
 *
 * 迁移只动 `Default.json`，用户另存的命名布局不受影响。
 */
object DefaultLayoutMigrator {

    /** ESC 的 GLFW 键码 */
    const val ESC_KEYCODE = 256

    private const val DEBUG_KEYCODE = 292
    private const val PERSPECTIVE_KEYCODE = 294

    /**
     * 保证 `Default.json` 里有 ESC，并把它放在左上角。
     *
     * @return true 表示文件被改写
     */
    fun ensureEscButton(file: File): Boolean {
        if (!file.isFile) return false
        val root = runCatching {
            JsonParser.parseString(file.readText()).asJsonObject
        }.getOrNull() ?: return false
        if (!migrate(root)) return false
        file.writeText(root.toString())
        return true
    }

    /**
     * 纯函数版本：直接改传入的 JSON 对象，便于在 JVM 上覆盖迁移规则。
     *
     * @return true 表示发生了修改
     */
    fun migrate(root: JsonObject): Boolean {
        val list = root.getAsJsonArray("mControlDataList") ?: return false
        val controls = list.mapNotNull { it as? JsonObject }
        if (controls.isEmpty()) return false
        // 已经有 ESC 就不重复添加（用户自己加过也算）
        if (controls.any { keycodeOf(it) == ESC_KEYCODE }) return false

        for (control in controls) {
            when (keycodeOf(control)) {
                // 左上角原先是 F3，往下挪一格；F6 再挪一格，左列保持等距小键
                DEBUG_KEYCODE -> control.addProperty("dynamicY", "\${height} + \${margin}")
                PERSPECTIVE_KEYCODE -> control.addProperty("dynamicY", "\${height} * 2 + \${margin} * 2")
            }
        }

        val merged = JsonArray()
        merged.add(newEscControl())
        for (control in controls) merged.add(control)
        root.add("mControlDataList", merged)
        return true
    }

    /** 新按钮沿用默认小键样式，只改名称、位置、尺寸与键码 */
    private fun newEscControl(): JsonObject = JsonObject().apply {
        addProperty("bgColor", 1291845632)
        addProperty("cornerRadius", 0)
        addProperty("dynamicX", "\${margin}")
        addProperty("dynamicY", "\${margin}")
        addProperty("height", 30)
        addProperty("isDynamicBtn", false)
        addProperty("isHideable", false)
        addProperty("isSwipeable", false)
        addProperty("isToggle", false)
        add("keycodes", JsonArray().apply {
            add(ESC_KEYCODE)
            add(0)
            add(0)
            add(0)
        })
        addProperty("name", "ESC")
        addProperty("opacity", 1)
        addProperty("passThruEnabled", false)
        addProperty("strokeColor", -1)
        addProperty("strokeWidth", 0)
        addProperty("width", 50)
    }

    private fun keycodeOf(control: JsonObject): Int? = runCatching {
        control.getAsJsonArray("keycodes")?.firstOrNull()?.asInt
    }.getOrNull()
}
