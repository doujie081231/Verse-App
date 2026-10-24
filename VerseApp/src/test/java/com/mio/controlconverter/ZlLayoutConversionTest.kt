package com.mio.controlconverter

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ZL2 控制布局 ↔ FCL 布局的格式互转测试。
 *
 * 这条链路是「把 ZL2 里做好的布局拿到 Verse 用」的唯一通道：
 * 1. 导入时 `convertZlToFcl` 转成 FCL 仓库格式落盘；
 * 2. 游戏加载时 `RepoLayoutConverter` 再转成应用运行格式。
 * 这里锁定第 1 步的输入输出契约，避免上游格式变化后静默坏掉。
 */
class ZlLayoutConversionTest {

    /** 最小可用的 ZL2 v12 布局：一个 W 键 + 一个方向摇杆 */
    private val zl2Sample = """
        {
          "info": {
            "name": {"default": "测试布局", "matchQueue": []},
            "author": {"default": "tester", "matchQueue": []},
            "description": {"default": "", "matchQueue": []},
            "versionCode": 1,
            "versionName": "1.0"
          },
          "layers": [
            {
              "name": "默认",
              "uuid": "layer-1",
              "hide": false,
              "hideWhenMouse": false,
              "hideWhenGamepad": false,
              "visibilityType": "always",
              "normalButtons": [
                {
                  "text": {"default": "W", "matchQueue": []},
                  "uuid": "btn-w",
                  "position": {"x": 4000, "y": 8000},
                  "buttonSize": {
                    "type": "dp",
                    "widthDp": 50.0,
                    "heightDp": 50.0,
                    "widthPercentage": 500,
                    "heightPercentage": 500,
                    "widthReference": "screen_width",
                    "heightReference": "screen_height"
                  },
                  "textAlignment": "center",
                  "visibilityType": "always",
                  "clickEvents": [{"type": "key", "key": "GLFW_KEY_W"}],
                  "isSwipple": false,
                  "isPenetrable": false,
                  "isToggleable": false
                }
              ],
              "textBoxes": [],
              "joystickButtons": []
            }
          ],
          "styles": [],
          "joystickStyles": [],
          "editorVersion": 12
        }
    """.trimIndent()

    @Test
    fun isZl2Layout_detectsV12Layout() {
        assertTrue(CcConverter.isZl2Layout(zl2Sample))
    }

    @Test
    fun isZl2Layout_returnsFalseForInvalidJson() {
        assertFalse(CcConverter.isZl2Layout("{ this is not json"))
        // 其它格式（既无 layers 也无 viewGroups）不应当被当作 ZL2 布局
        assertFalse(CcConverter.isZl2Layout("""{"foo": 1}"""))
    }

    @Test
    fun convertZlToFcl_producesFclRepositoryFormat() {
        val zl = JsonParser.parseString(zl2Sample).asJsonObject

        val fcl = CcConverter.convertZlToFcl(zl, stripMeta = true)

        // FCL 仓库格式的判别字段
        assertTrue("输出缺少 viewGroups", fcl.has("viewGroups"))
        assertTrue("输出缺少 controllerVersion", fcl.has("controllerVersion"))
        // 转换后应当能被识别为 FCL 布局，形成闭环
        assertTrue(CcConverter.isFclLayout(fcl.toString()))
    }

    @Test
    fun roundTrip_keepsLayoutConvertible() {
        val zl = JsonParser.parseString(zl2Sample).asJsonObject
        val fcl = CcConverter.convertZlToFcl(zl, stripMeta = true)

        val back = CcConverter.convertFclToZl(fcl, stripMeta = true)

        // 往返后仍是合法 ZL2 布局
        assertTrue(CcConverter.isZl2Layout(back.toString()))
        assertEquals(12, back.get("editorVersion").asInt)
        assertTrue("往返后层被清空", back.getAsJsonArray("layers").size() >= 1)
    }

    @Test
    fun convertZlToFcl_keepsButtonCount() {
        val zl = JsonParser.parseString(zl2Sample).asJsonObject
        val fcl = CcConverter.convertZlToFcl(zl, stripMeta = true)

        val groups = fcl.getAsJsonArray("viewGroups")
        assertTrue(groups.size() >= 1)

        // FCL 仓库格式：viewGroups[] → viewData(对象) → buttonList[]
        var buttonCount = 0
        groups.forEach { group ->
            val viewData = group.asJsonObject.getAsJsonObject("viewData") ?: return@forEach
            viewData.getAsJsonArray("buttonList")?.let { buttonCount += it.size() }
        }
        assertEquals("按钮数量应与源布局一致", 1, buttonCount)
    }
}
