package net.kdt.pojavlaunch.customcontrols

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 布局仓库格式的控件组切换绑定解析。
 *
 * 仓库里绝大多数多控件组布局（如 Master全功能键盘pro：42 个控件组、19 个切换键）
 * 把 bindViewGroup 放在 pressEvent 上，clickEvent 是空的。
 * 之前只读 clickEvent，导入后所有切换键都没绑定 → 所有面板都打不开，
 * 真机表现为「进游戏控件点不动」。
 */
class RepoLayoutPressBindTest {

    private fun event(pressBind: String = "[]", clickBind: String = "[]") =
        JsonParser.parseString(
            """
            {
              "pressEvent": { "outputKeycodes": [], "bindViewGroup": $pressBind },
              "clickEvent": { "outputKeycodes": [], "bindViewGroup": $clickBind }
            }
            """.trimIndent()
        ).asJsonObject

    @Test
    fun `pressEvent 的 bindViewGroup 会被读出`() {
        val root = event(pressBind = """["g2"]""")
        val press = root.getAsJsonObject("pressEvent")
        assertEquals(listOf("g2"), RepoLayoutConverter.readBindGroups(press))
    }

    @Test
    fun `clickEvent 的 bindViewGroup 仍按点击绑定读取`() {
        val root = event(clickBind = """["g1"]""")
        val click = root.getAsJsonObject("clickEvent")
        assertEquals(listOf("g1"), RepoLayoutConverter.readBindGroups(click))
        assertNull(RepoLayoutConverter.readBindGroups(root.getAsJsonObject("pressEvent")))
    }

    @Test
    fun `多个目标控件组按顺序保留`() {
        val press = event(pressBind = """["g1","g2","g3"]""").getAsJsonObject("pressEvent")
        assertEquals(listOf("g1", "g2", "g3"), RepoLayoutConverter.readBindGroups(press))
    }

    @Test
    fun `普通按键没有绑定时返回 null`() {
        val press = event().getAsJsonObject("pressEvent")
        assertNull(RepoLayoutConverter.readBindGroups(press))
    }
}
