package net.kdt.pojavlaunch.customcontrols

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 控件组（选择布局）持久化回归：
 * 组信息不落盘时，转换后的布局再次加载会把所有组控件当成常驻，
 * 「切换控件组」的按钮就彻底失效（真机反馈 1.2.4）。
 */
class ControlGroupPersistenceTest {

    private val gson = Gson()

    private fun layoutJson(groups: String?): String {
        val groupsField = if (groups == null) "" else ",\"mControlGroups\":$groups"
        return """
            {"version":8,"mControlDataList":[],"mDrawerDataList":[],"mJoystickDataList":[]$groupsField}
        """.trimIndent()
    }

    @Test
    fun groupsSurviveSerialization() {
        val layout = gson.fromJson(
            layoutJson("""[{"id":"g1","name":"战斗布局","visible":false}]"""),
            CustomControls::class.java
        )
        assertNotNull(layout.mControlGroups)
        assertEquals(1, layout.mControlGroups.size)
        assertEquals("g1", layout.mControlGroups[0].id)
        assertFalse(layout.mControlGroups[0].visible)
    }

    @Test
    fun saveKeepsGroups() {
        val layout = CustomControls()
        val group = CustomControls.ControlGroupData()
        group.id = "g1"
        group.name = "战斗布局"
        group.visible = true
        layout.mControlGroups = mutableListOf(group)

        val json = gson.toJson(layout)
        assertTrue(json.contains("mControlGroups"))
        assertTrue(json.contains("\"g1\""))

        val back = gson.fromJson(json, CustomControls::class.java)
        assertEquals(1, back.mControlGroups.size)
        assertEquals("战斗布局", back.mControlGroups[0].name)
    }

    @Test
    fun memberListsAreNotPersisted() {
        // 组成员由 groupId 重建，落盘会把这些控件数据重复存一遍
        val layout = CustomControls()
        val group = CustomControls.ControlGroupData()
        group.id = "g1"
        layout.mControlGroups = mutableListOf(group)
        val json = gson.toJson(layout)
        assertFalse(json.contains("\"buttons\""))
        assertFalse(json.contains("\"joysticks\""))
    }

    @Test
    fun legacyLayoutWithoutGroups_isTolerated() {
        val layout = gson.fromJson(layoutJson(null), CustomControls::class.java)
        assertNull(layout.mControlGroups)
        // 兜底重建不能因缺组而崩溃
        layout.rebuildControlGroups()
    }
}
