package com.verse.explorer.app.horizon

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 合成版本（把数据生成器伪装成一个 MC 版本）的关键逻辑测试。
 *
 * 依赖 Maven 坐标反推：反推错了会去下载不存在的库，整条启动链路直接失败，
 * 所以这条规则必须有测试兜住。
 */
class HorizonDatagenVersionTest {

    @Test
    fun `从 jar 路径反推 Maven 坐标`() {
        assertEquals(
            "com.google.code.gson:gson:2.13.2",
            HorizonDatagenVersion.mavenName("com/google/code/gson/gson/2.13.2/gson-2.13.2.jar")
        )
        assertEquals(
            "org.apache.logging.log4j:log4j-core:2.24.3",
            HorizonDatagenVersion.mavenName("libraries/org/apache/logging/log4j/log4j-core/2.24.3/log4j-core-2.24.3.jar")
        )
    }

    @Test
    fun `非 jar 或层级不足时返回 null`() {
        assertNull(HorizonDatagenVersion.mavenName("com/google/gson.jar"))
        assertNull(HorizonDatagenVersion.mavenName("libraries/a/b/c/readme.txt"))
        assertNull(HorizonDatagenVersion.mavenName(""))
    }

    @Test
    fun `版本 JSON 继承 26-3 且主类指向数据生成器`() {
        val json = HorizonDatagenVersion.buildVersionJson(
            listOf(
                "net/minecraft/server/26.3/server.jar",
                "libraries/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar"
            )
        )

        assertEquals("horizon-datagen", json.get("id").asString)
        assertEquals("26.3", json.get("inheritsFrom").asString)
        assertEquals(HorizonDatagenVersion.WRAPPER_MAIN, json.get("mainClass").asString)

        val args = json.getAsJsonObject("arguments").getAsJsonArray("game").map { it.asString }
        assertEquals(listOf("--reports", "--output", "out"), args)

        val names = json.getAsJsonArray("libraries").map { it.asJsonObject.get("name").asString }
        assertTrue(names.contains("net.minecraft:server:26.3"))
        assertTrue(names.contains("com.google.code.gson:gson:2.13.2"))
    }

    @Test
    fun `坐标无法反推的库被跳过而不是写坏 name`() {
        val json = HorizonDatagenVersion.buildVersionJson(listOf("libraries/broken.jar"))

        assertEquals(0, json.getAsJsonArray("libraries").size())
    }

    @Test
    fun `生成的 JSON 能被重新解析`() {
        val json = HorizonDatagenVersion.buildVersionJson(
            listOf("libraries/a/b/c/1.0/c-1.0.jar")
        )
        val reparsed = JsonParser.parseString(json.toString()).asJsonObject

        assertEquals("horizon-datagen", reparsed.get("id").asString)
        assertEquals("a.b:c:1.0", reparsed.getAsJsonArray("libraries")[0].asJsonObject.get("name").asString)
    }
}
