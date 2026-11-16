package com.verse.explorer.launcher.renderer.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 渲染器配置解析与写盘映射测试。
 *
 * 这层是全纯的：输入两张 Map，输出一张 Map / 一份待写入的键值对，
 * 所以配置的优先级、类型收敛、脏值清理、以及"写驱动时不吞用户键"
 * 都能在 JVM 上跑，不依赖真机。
 */
class RendererConfigTest {

    private val spec = rendererSpecFixture()

    @Test
    fun `没有任何存储时全部取默认值`() {
        val resolved = RendererConfigResolver.resolve(spec)

        assertEquals(spec.fields.size, resolved.size)
        spec.fields.forEach { field ->
            assertEquals("字段 ${field.id} 应取默认值", field.defaultValue, resolved[field.id])
        }
    }

    @Test
    fun `全局设置覆盖默认值`() {
        val resolved = RendererConfigResolver.resolve(
            spec,
            global = mapOf("enableNoError" to 1, "enableANGLE" to 2)
        )

        assertEquals(1, resolved["enableNoError"])
        assertEquals(2, resolved["enableANGLE"])
        assertEquals(spec.field("fsr1Setting")!!.defaultValue, resolved["fsr1Setting"])
    }

    @Test
    fun `版本级设置优先于全局`() {
        val resolved = RendererConfigResolver.resolve(
            spec,
            global = mapOf("enableNoError" to 1),
            version = mapOf("enableNoError" to 2, "fsr1Setting" to 3)
        )

        assertEquals(2, resolved["enableNoError"])
        assertEquals(3, resolved["fsr1Setting"])
    }

    @Test
    fun `非法值回退到下一层而不是把脏值写进驱动`() {
        val resolved = RendererConfigResolver.resolve(
            spec,
            global = mapOf(
                "enableNoError" to 99,
                "enableExtTimerQuery" to "not-a-bool",
                "maxGlslCacheSize" to 9999,
                "unknownKey" to 1
            )
        )

        assertEquals("非法枚举应回退默认值", 3, resolved["enableNoError"])
        assertEquals("非法布尔应回退默认值", true, resolved["enableExtTimerQuery"])
        assertEquals("越界整数应按区间收敛", 512, resolved["maxGlslCacheSize"])
        assertFalse("未声明的键不应进入结果", resolved.containsKey("unknownKey"))
    }

    @Test
    fun `负数缓存上限按区间下限收敛`() {
        val resolved = RendererConfigResolver.resolve(spec, global = mapOf("maxGlslCacheSize" to -500))
        assertEquals(-1, resolved["maxGlslCacheSize"])
    }

    @Test
    fun `字符串形式的布尔值可以被接受`() {
        val resolved = RendererConfigResolver.resolve(spec, global = mapOf("enableExtComputeShader" to "true"))
        assertEquals(true, resolved["enableExtComputeShader"])
    }

    @Test
    fun `裁剪存储时丢弃已废弃的键`() {
        val stored = mapOf("enableNoError" to 1, "removedKey" to 5, "fsr1Setting" to 2)
        val pruned = RendererConfigResolver.pruneStored(spec, stored)

        assertEquals(setOf("enableNoError", "fsr1Setting"), pruned.keys)
    }

    @Test
    fun `写盘只输出 JSON 目标字段且顺序稳定`() {
        val values = mapOf("enableNoError" to 1, "enableANGLE" to 2)
        val json = RendererConfigJson.jsonValues(spec, values)

        assertEquals(1, json["enableNoError"])
        assertEquals(2, json["enableANGLE"])
        assertEquals(0, json["fsr1Setting"])
        assertEquals(spec.fields.count { it.jsonKey() != null }, json.size)
        assertEquals(
            "字段顺序应与规范一致",
            spec.fields.first { it.jsonKey() != null }.jsonKey(),
            json.keys.first()
        )
    }

    @Test
    fun `本地型开关不会写进驱动配置文件`() {
        val values = RendererConfigResolver.resolve(spec, global = mapOf("sfpewLayer" to false))
        val json = RendererConfigJson.jsonValues(spec, values)

        assertEquals("只存本地的开关不应出现在驱动配置里", false, values["sfpewLayer"])
        assertFalse(json.containsKey("sfpewLayer"))
    }

    @Test
    fun `合并写入保留用户手写的键`() {
        val existing = mapOf<String, Any?>(
            "enableNoError" to 0,
            "userCustomKey" to "keep-me",
            "multidrawOrder" to listOf("native")
        )
        val merged = RendererConfigJson.mergeInto(existing, mapOf("enableNoError" to 3))

        assertEquals(3, merged["enableNoError"])
        assertEquals("受管键以外的键必须原样保留", "keep-me", merged["userCustomKey"])
        assertEquals(listOf("native"), merged["multidrawOrder"])
    }

    @Test
    fun `改动项统计只算偏离默认值的字段`() {
        val resolved = RendererConfigResolver.resolve(
            spec,
            global = mapOf("enableNoError" to 1, "fsr1Setting" to 4)
        )
        val changed = RendererConfigResolver.changedFields(spec, resolved)

        assertEquals(setOf("enableNoError", "fsr1Setting"), changed.toSet())
    }

    @Test
    fun `环境变量型字段的取值规则`() {
        val envOn = RendererConfigEnv.build(spec, RendererConfigResolver.resolve(spec))
        assertEquals("1", envOn["MG_COUNT_LAUNCH"])
        assertFalse("omitWhenFalse 的开关默认不应下发", envOn.containsKey("SFPEW_RELAXED_CONTEXT"))

        val off = RendererConfigResolver.resolve(
            spec,
            global = mapOf("countLaunch" to false, "relaxed" to true)
        )
        val envOff = RendererConfigEnv.build(spec, off)
        assertEquals("0", envOff["MG_COUNT_LAUNCH"])
        assertEquals("1", envOff["SFPEW_RELAXED_CONTEXT"])
    }

    @Test
    fun `环境变量只包含 Env 目标字段`() {
        val env = RendererConfigEnv.build(spec, RendererConfigResolver.resolve(spec))

        assertFalse("JSON 目标不应出现在环境变量里", env.containsKey("enableNoError"))
        assertFalse("本地目标不应出现在环境变量里", env.containsKey("sfpewLayer"))
        assertTrue(env.containsKey("MG_COUNT_LAUNCH"))
    }

    @Test
    fun `数组型配置按索引写出对应的预设顺序`() {
        val mg = RendererConfigSpecs.MOBILE_GLUES
        val full = RendererConfigResolver.resolve(mg, mapOf("multidrawOrder" to 0))
        val compat = RendererConfigResolver.resolve(mg, mapOf("multidrawOrder" to 1))

        @Suppress("UNCHECKED_CAST")
        val fullOrder = RendererConfigJson.jsonValues(mg, full)["multidrawOrder"] as List<String>

        @Suppress("UNCHECKED_CAST")
        val compatOrder = RendererConfigJson.jsonValues(mg, compat)["multidrawOrder"] as List<String>

        assertEquals("native", fullOrder.first())
        assertTrue("默认序应包含间接绘制", fullOrder.contains("multiindirect"))
        assertEquals(listOf("native", "multiarrays", "unroll"), compatOrder)
    }

    @Test
    fun `规范本身的字段 id 与键唯一`() {
        val ids = spec.fields.map { it.id }
        assertEquals(ids.size, ids.toSet().size)

        val jsonKeys = spec.fields.mapNotNull { it.jsonKey() }
        assertEquals(jsonKeys.size, jsonKeys.toSet().size)
    }

    @Test
    fun `未知渲染器没有配置规范`() {
        assertNull(RendererConfigSpecs.of("definitely-not-a-renderer"))
        assertTrue(RendererConfigSpecs.of("MobileGlues") != null)
    }

    @Test
    fun `迁移只清掉旧默认开启的兼容层状态`() {
        val stored = mapOf("sfpewLayer" to true, "enableNoError" to 3, "maxGlslCacheSize" to 64)
        val migrated = RendererConfigResolver.dropLegacyDefaults(stored)

        assertFalse("旧默认值 true 必须清掉，否则新默认形同虚设", migrated.containsKey("sfpewLayer"))
        assertEquals(3, migrated["enableNoError"])
        assertEquals(64, migrated["maxGlslCacheSize"])
    }

    @Test
    fun `迁移保留用户主动关闭的兼容层状态`() {
        val migrated = RendererConfigResolver.dropLegacyDefaults(mapOf("sfpewLayer" to false))

        assertEquals(false, migrated["sfpewLayer"])
    }

    @Test
    fun `空配置迁移结果为空`() {
        assertTrue(RendererConfigResolver.dropLegacyDefaults(emptyMap()).isEmpty())
    }

    @Test
    fun `兼容层默认关闭`() {
        val field = RendererConfigSpecs.MOBILE_GLUES.field("sfpewLayer")

        assertEquals(false, field?.defaultValue)
        assertTrue("兼容层是本地开关，不能写进驱动配置", field?.target is ConfigTarget.Local)
    }
}

/**
 * 测试用的规范：结构上与 MobileGlues 一致（枚举 / 开关 / 整数 / 环境变量 / 本地开关、区间、分组），
 * 但字段更少，断言能写死具体值，改动生产字段时不会把测试打散。
 */
private fun rendererSpecFixture(): RendererConfigSpec = RendererConfigSpec(
    rendererId = "test-renderer",
    version = 1,
    fields = listOf(
        RendererConfigField(
            id = "enableNoError",
            title = "忽略错误",
            description = "",
            kind = ConfigFieldKind.CHOICE,
            defaultValue = 3,
            target = ConfigTarget.Json("enableNoError"),
            options = listOf(
                ConfigOption(0, "自动"),
                ConfigOption(1, "不忽略"),
                ConfigOption(2, "着色器"),
                ConfigOption(3, "全部")
            ),
            group = "兼容"
        ),
        RendererConfigField(
            id = "countLaunch",
            title = "统计",
            description = "",
            kind = ConfigFieldKind.SWITCH,
            defaultValue = true,
            target = ConfigTarget.Env("MG_COUNT_LAUNCH"),
            group = "性能"
        ),
        RendererConfigField(
            id = "relaxed",
            title = "宽松",
            description = "",
            kind = ConfigFieldKind.SWITCH,
            defaultValue = false,
            target = ConfigTarget.Env("SFPEW_RELAXED_CONTEXT"),
            group = "兼容",
            omitWhenFalse = true
        ),
        RendererConfigField(
            id = "sfpewLayer",
            title = "兼容层",
            description = "",
            kind = ConfigFieldKind.SWITCH,
            defaultValue = true,
            target = ConfigTarget.Local("sfpewLayer"),
            group = "兼容"
        ),
        RendererConfigField(
            id = "enableANGLE",
            title = "ANGLE",
            description = "",
            kind = ConfigFieldKind.CHOICE,
            defaultValue = 0,
            target = ConfigTarget.Json("enableANGLE"),
            options = listOf(
                ConfigOption(0, "关"),
                ConfigOption(1, "开"),
                ConfigOption(2, "强制关"),
                ConfigOption(3, "强制开")
            ),
            group = "兼容"
        ),
        RendererConfigField(
            id = "enableExtTimerQuery",
            title = "Timer",
            description = "",
            kind = ConfigFieldKind.SWITCH,
            defaultValue = true,
            target = ConfigTarget.Json("enableExtTimerQuery"),
            group = "扩展"
        ),
        RendererConfigField(
            id = "enableExtComputeShader",
            title = "Compute",
            description = "",
            kind = ConfigFieldKind.SWITCH,
            defaultValue = false,
            target = ConfigTarget.Json("enableExtComputeShader"),
            group = "扩展"
        ),
        RendererConfigField(
            id = "maxGlslCacheSize",
            title = "缓存",
            description = "",
            kind = ConfigFieldKind.NUMBER,
            defaultValue = 32,
            target = ConfigTarget.Json("maxGlslCacheSize"),
            range = -1..512,
            group = "性能"
        ),
        RendererConfigField(
            id = "fsr1Setting",
            title = "FSR",
            description = "",
            kind = ConfigFieldKind.CHOICE,
            defaultValue = 0,
            target = ConfigTarget.Json("fsr1Setting"),
            options = listOf(
                ConfigOption(0, "关"),
                ConfigOption(1, "超高质量"),
                ConfigOption(2, "质量"),
                ConfigOption(3, "均衡"),
                ConfigOption(4, "性能")
            ),
            group = "画质"
        )
    )
)
