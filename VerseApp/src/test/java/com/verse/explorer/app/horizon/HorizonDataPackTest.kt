package com.verse.explorer.app.horizon

import com.google.gson.JsonParser
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 派生包生成逻辑测试。
 *
 * 这里覆盖的是「手机端复刻上游 install_datagen_reports」的三段纯逻辑：
 * 版本 JSON 取 server.jar、components 合并成 items.json、reports 目录打成 zip。
 * 真实跑 Java 数据生成器那段需要真机 JRE，不在单测范围内。
 */
class HorizonDataPackTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `版本 JSON 能取到 server jar`() {
        val json = JsonParser.parseString(
            """
            {"id":"26.3",
             "downloads":{
               "server":{"size":65335772,"sha1":"3368","url":"https://piston-data.mojang.com/v1/objects/33/server.jar"},
               "client":{"size":43500000,"sha1":"e877","url":"https://piston-data.mojang.com/v1/objects/e8/client.jar"}
             }}
            """.trimIndent()
        ).asJsonObject

        val jar = HorizonDataPack.parseServerJar(json)

        assertNotNull(jar)
        assertEquals("26.3", jar!!.id)
        assertEquals(65335772L, jar.size)
        assertTrue(jar.url.endsWith("server.jar"))
    }

    @Test
    fun `没有 server 下载项时返回 null`() {
        val json = JsonParser.parseString("""{"id":"26.3","downloads":{}}""").asJsonObject

        assertNull(HorizonDataPack.parseServerJar(json))
    }

    @Test
    fun `components 合并成 items json`() {
        val a = JsonParser.parseString(
            """{"components":{"minecraft:max_stack_size":64,"minecraft:food":{"nutrition":4}}}"""
        ).asJsonObject
        val b = JsonParser.parseString(
            """{"components":{"minecraft:max_stack_size":16}}"""
        ).asJsonObject

        val merged = JsonParser.parseString(
            HorizonDataPack.buildItemsJson(listOf("apple" to a, "bucket" to b))
        ).asJsonObject

        assertEquals(2, merged.size())
        // 与上游一致：id 直接映射 components 本体，不再嵌套一层 "components"
        assertEquals(64, merged.getAsJsonObject("minecraft:apple")
            .get("minecraft:max_stack_size").asInt)
        assertEquals(16, merged.getAsJsonObject("minecraft:bucket")
            .get("minecraft:max_stack_size").asInt)
    }

    @Test
    fun `reports 目录打成派生包`() {
        val reports = temp.newFolder("reports")
        File(reports, "registries.json").writeText("""{"minecraft:item":{}}""")
        File(reports, "commands.json").writeText("""[{"name":"help"}]""")

        val items = File(reports, "minecraft/components/item").apply { mkdirs() }
        File(items, "apple.json").writeText("""{"components":{"minecraft:food":{}}}""")
        File(items, "stone.json").writeText("""{"components":{"minecraft:block":{}}}""")

        val biomes = File(reports, "minecraft/biome_parameters").apply { mkdirs() }
        File(biomes, "overworld.json").writeText("""{"temperature":0.8}""")
        File(biomes, "nether.json").writeText("""{"temperature":2.0}""")

        val dest = File(temp.newFolder("out"), "horizon-derived.zip")
        val count = HorizonDataPack.buildDerivedZip(reports, dest)

        assertEquals(5, count)
        ZipFile(dest).use { zip ->
            val names = zip.entries().toList().map { it.name }.sorted()
            assertEquals(
                listOf(
                    "biome-parameters/nether.json",
                    "biome-parameters/overworld.json",
                    "commands.json",
                    "items.json",
                    "registries.json"
                ).sorted(),
                names
            )
            val itemsJson = zip.getInputStream(zip.getEntry("items.json"))
                .bufferedReader().readText()
            assertTrue(itemsJson.contains("minecraft:apple"))
            assertTrue(itemsJson.contains("minecraft:stone"))
        }
    }

    @Test
    fun `缺少 components 时不出包`() {
        val reports = temp.newFolder("emptyReports")
        File(reports, "registries.json").writeText("{}")
        val dest = File(temp.newFolder("emptyOut"), "horizon-derived.zip")

        assertEquals(0, HorizonDataPack.buildDerivedZip(reports, dest))
        assertTrue("报告不完整时不应留下半成品 zip", !dest.exists() || dest.length() == 0L)
    }
}
