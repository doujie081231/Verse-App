package com.verse.explorer.app.horizon

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 父版本探测测试。
 *
 * 真机上用户明明装了 26.3，如果只按目录名精确匹配就会误判成「没安装」并卡住流程；
 * 这里覆盖三种情况：精确名、目录名不同但 JSON id 匹配、带后缀的派生 id。
 */
class HorizonParentDetectTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun versionDir(name: String, idInJson: String? = null): File {
        val dir = File(temp.root, name).apply { mkdirs() }
        if (idInJson != null) File(dir, "$name.json").writeText("""{"id":"$idInJson"}""")
        return dir
    }

    @Test
    fun `目录名精确匹配`() {
        versionDir("26.3")
        versionDir("1.21.4")

        assertEquals("26.3", HorizonPrep.detectParentVersion(temp.root))
    }

    @Test
    fun `目录名不同但 JSON id 是 26-3`() {
        versionDir("26.3 原版", idInJson = "26.3")

        assertEquals("26.3 原版", HorizonPrep.detectParentVersion(temp.root))
    }

    @Test
    fun `带后缀的派生 id 也能认出来`() {
        versionDir("26.3-optifine")

        assertEquals("26.3-optifine", HorizonPrep.detectParentVersion(temp.root))
    }

    @Test
    fun `没有 26-3 时返回 null 而不是抛异常`() {
        versionDir("1.20.1", idInJson = "1.20.1")

        assertNull(HorizonPrep.detectParentVersion(temp.root))
    }
}
