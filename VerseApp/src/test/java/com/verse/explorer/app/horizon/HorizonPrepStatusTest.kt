package com.verse.explorer.app.horizon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 引擎进度解析测试。
 *
 * nativeStatus() 返回制表符分隔的七个字段（stage / files / filesTotal / bytes / bytesTotal / state / error），
 * 准备页的进度条与文案完全依赖它；字段对不上时必须安静降级而不是崩。
 */
class HorizonPrepStatusTest {

    @Test
    fun `按字节算进度`() {
        val snapshot = HorizonPrep.parseStatus("download\t120\t300\t524288000\t1048576000\trun\t")

        assertEquals("run", snapshot!!.state)
        assertEquals(0.5f, snapshot.fraction, 0.001f)
        assertTrue("应该带 MB 文案", snapshot.label.contains("MB"))
    }

    @Test
    fun `字节总量为 0 时回落到文件数`() {
        val snapshot = HorizonPrep.parseStatus("unpack\t25\t50\t0\t0\trun\t")

        assertEquals(0.5f, snapshot!!.fraction, 0.001f)
        assertTrue(snapshot.label.contains("个文件"))
    }

    @Test
    fun `done 与 error 状态原样透出`() {
        assertEquals("done", HorizonPrep.parseStatus("ready\t1\t1\t1\t1\tdone\t")!!.state)

        val failed = HorizonPrep.parseStatus("download\t0\t0\t0\t0\terror\t磁盘空间不足")!!
        assertEquals("error", failed.state)
        assertEquals("磁盘空间不足", failed.error)
    }

    @Test
    fun `字段不足或为空时返回 null`() {
        assertNull(HorizonPrep.parseStatus(null))
        assertNull(HorizonPrep.parseStatus(""))
        assertNull(HorizonPrep.parseStatus("download\t1\t2"))
    }

    @Test
    fun `阶段名映射成中文`() {
        // 阶段名本身可能含空格（如「Horizon 数据」），用前缀断言而不是截断
        assertTrue(HorizonPrep.parseStatus("download\t1\t2\t0\t0\trun\t")!!.label
            .startsWith("下载游戏文件"))
        assertTrue(HorizonPrep.parseStatus("derived\t1\t2\t0\t0\trun\t")!!.label
            .startsWith("Horizon 数据"))
    }
}
