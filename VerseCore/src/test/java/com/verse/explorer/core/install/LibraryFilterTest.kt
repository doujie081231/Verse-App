package com.verse.explorer.core.install

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 库筛选规则的平台判定测试。
 *
 * 覆盖启动/安装链最易回归的一段纯逻辑：版本清单里 libraries[].rules 的
 * allow/disallow 与 os.name 组合判定，以及缺 downloads 时按 maven 坐标补地址。
 */
class LibraryFilterTest {

    private fun rule(action: String, osName: String? = null, features: JsonObject? = null): JsonObject {
        val rule = JsonObject()
        rule.addProperty("action", action)
        if (osName != null) {
            val os = JsonObject()
            os.addProperty("name", osName)
            rule.add("os", os)
        }
        if (features != null) rule.add("features", features)
        return rule
    }

    /** evaluateRules 的入参是已解析的规则列表 */
    private fun ruleList(vararg items: JsonObject): List<JsonObject> = items.toList()

    /** 版本清单里的是 JsonArray，用于 filterValidLibraries 用例 */
    private fun rulesArray(vararg items: JsonObject): JsonArray {
        val array = JsonArray()
        items.forEach { array.add(it) }
        return array
    }

    @Test
    fun emptyRules_allowed() {
        // 无 rules 视为全平台放行
        assertTrue(LibraryFilter.evaluateRules(ruleList()))
    }

    @Test
    fun allowWithoutOs_allowed() {
        assertTrue(LibraryFilter.evaluateRules(ruleList(rule("allow"))))
    }

    @Test
    fun allowLinux_allowed() {
        // 安卓按 linux 处理
        assertTrue(LibraryFilter.evaluateRules(ruleList(rule("allow", "linux"))))
    }

    @Test
    fun allowWindows_denied() {
        // 只允许 windows 的库在安卓上不可用
        assertFalse(LibraryFilter.evaluateRules(ruleList(rule("allow", "windows"))))
    }

    @Test
    fun disallowLinux_denied() {
        assertFalse(LibraryFilter.evaluateRules(ruleList(rule("disallow", "linux"))))
    }

    @Test
    fun disallowThenAllow_firstMatchWins() {
        // 注意：本实现按顺序取「第一条命中当前平台的规则」即返回，
        // 不是 MC 原版的「后者覆盖前者」。官方版本清单几乎全是单规则，
        // 所以未暴露问题；若遇到多规则库，请按此行为排查（见测试报告后的已知差异）。
        assertFalse(
            LibraryFilter.evaluateRules(
                ruleList(rule("disallow", "linux"), rule("allow", "linux"))
            )
        )
    }

    @Test
    fun filterValidLibraries_withoutDownloads_buildsMavenUrl() {
        // 缺 downloads.artifact 时按 group:artifact:version 拼出 path 与 url
        // 注意：url 末尾斜杠不归一化，拼接结果会出现双斜杠（服务器普遍容忍，历史行为）
        val lib = JsonObject()
        lib.addProperty("name", "org.ow2.asm:asm:9.6")
        lib.addProperty("url", "https://maven.example.com/")
        val versionJson = JsonObject()
        versionJson.add("libraries", rulesArray(lib))

        val result = LibraryFilter.filterValidLibraries(versionJson)

        assertEquals(1, result.size)
        val artifact = result[0].getAsJsonObject("artifact")
        assertEquals("org/ow2/asm/asm/9.6/asm-9.6.jar", artifact.get("path").asString)
        assertEquals("https://maven.example.com//org/ow2/asm/asm/9.6/asm-9.6.jar", artifact.get("url").asString)
    }

    @Test
    fun filterValidLibraries_skipsDeniedLibrary() {
        // rules 判定为不允许的库不进入下载列表
        val lib = JsonObject()
        lib.addProperty("name", "com.example:win-only:1.0")
        lib.addProperty("url", "https://maven.example.com/")
        lib.add("rules", rulesArray(rule("allow", "windows")))
        val versionJson = JsonObject()
        versionJson.add("libraries", rulesArray(lib))

        assertTrue(LibraryFilter.filterValidLibraries(versionJson).isEmpty())
    }
}
