package com.verse.explorer.app.game

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 默认触控布局补丁测试。
 *
 * 迁移对象是老用户机器上已存在的 `Default.json`（不会再跟随 APK 更新），
 * 所以规则必须是幂等的：已有 ESC 就不动，缺了才插到左上角，并把原来占位的
 * F3 / F6 往下挪，避免叠在一起。
 */
class DefaultLayoutMigratorTest {

    private fun rootOf(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    private fun control(keycode: Int, y: String = "\${margin}", name: String = "K$keycode"): String =
        """{"bgColor":1291845632,"cornerRadius":0,"dynamicX":"${'$'}{margin}","dynamicY":"$y",
           "height":30,"isDynamicBtn":false,"isHideable":false,"isSwipeable":false,"isToggle":false,
           "keycodes":[$keycode,0,0,0],"name":"$name","opacity":1,"passThruEnabled":false,
           "strokeColor":-1,"strokeWidth":0,"width":80}""".trimIndent().replace("\n", "")

    private fun layoutOf(vararg controls: String): String =
        """{"mControlDataList":[${controls.joinToString(",")}],"mDrawerDataList":[],"scaledAt":100,"version":4}"""

    @Test
    fun `缺 ESC 时插到左上角并下移 F3 与 F6`() {
        val root = rootOf(layoutOf(control(292, name = "调试"), control(294, y = "\${height} + \${margin}", name = "视角")))

        assertTrue(DefaultLayoutMigrator.migrate(root))

        val list = root.getAsJsonArray("mControlDataList")
        val names = list.map { it.asJsonObject.get("name").asString }
        assertEquals("ESC", names.first())

        val esc = list[0].asJsonObject
        assertEquals(DefaultLayoutMigrator.ESC_KEYCODE, esc.getAsJsonArray("keycodes")[0].asInt)
        assertEquals("\${margin}", esc.get("dynamicX").asString)
        assertEquals("\${margin}", esc.get("dynamicY").asString)

        val debug = list.first { it.asJsonObject.get("name").asString == "调试" }.asJsonObject
        assertEquals("\${height} + \${margin}", debug.get("dynamicY").asString)
        val perspective = list.first { it.asJsonObject.get("name").asString == "视角" }.asJsonObject
        assertEquals("\${height} * 2 + \${margin} * 2", perspective.get("dynamicY").asString)
    }

    @Test
    fun `已有 ESC 时保持原样`() {
        val root = rootOf(layoutOf(control(256, name = "ESC"), control(292)))

        assertFalse(DefaultLayoutMigrator.migrate(root))
        assertEquals(2, root.getAsJsonArray("mControlDataList").size())
    }

    @Test
    fun `没有控件列表时不报错也不改文件`() {
        val root = rootOf("""{"version":4}""")

        assertFalse(DefaultLayoutMigrator.migrate(root))
    }

    @Test
    fun `随包默认布局已包含左上角 ESC`() {
        val asset = File("src/main/assets/game/default_controls.json")
        assertTrue("缺少默认布局资源: ${asset.absolutePath}", asset.isFile)
        val root = JsonParser.parseString(asset.readText()).asJsonObject
        val list = root.getAsJsonArray("mControlDataList")

        val esc = list.map { it.asJsonObject }
            .firstOrNull { it.getAsJsonArray("keycodes")[0].asInt == DefaultLayoutMigrator.ESC_KEYCODE }
        assertNotNull("默认布局必须自带 ESC", esc)
        assertEquals("\${margin}", esc!!.get("dynamicX").asString)
        assertEquals("\${margin}", esc.get("dynamicY").asString)

        // 左上角不能被别的键占着：同一坐标只允许 ESC 自己
        val sameCorner = list.map { it.asJsonObject }.count {
            it.get("dynamicX").asString == "\${margin}" && it.get("dynamicY").asString == "\${margin}"
        }
        assertEquals("左上角存在重叠控件", 1, sameCorner)
    }
}
