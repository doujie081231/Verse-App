package com.verse.explorer.app.game

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * OptionsText.setOption 的纯文本行为覆盖：
 * 键不存在时追加、已存在时替换、其他行原样保留、幂等。
 */
class OptionsTextTest {

    @Test
    fun appendsKeyWhenAbsent() {
        val result = OptionsText.setOption("gamma:1.0\n", "preferredGraphicsBackend", "\"opengl\"")
        assertEquals("gamma:1.0\npreferredGraphicsBackend:\"opengl\"\n", result)
    }

    @Test
    fun replacesExistingKey() {
        val content = "gamma:1.0\npreferredGraphicsBackend:\"default\"\nrenderDistance:12\n"
        val result = OptionsText.setOption(content, "preferredGraphicsBackend", "\"vulkan\"")
        assertEquals("gamma:1.0\npreferredGraphicsBackend:\"vulkan\"\nrenderDistance:12\n", result)
    }

    @Test
    fun preservesOtherLines() {
        val content = "ao:true\nfov:70\n"
        val result = OptionsText.setOption(content, "mipmapLevels", "4")
        assertEquals("ao:true\nfov:70\nmipmapLevels:4\n", result)
    }

    @Test
    fun emptyContentGetsSingleLine() {
        assertEquals("lang:zh_cn\n", OptionsText.setOption("", "lang", "zh_cn"))
    }

    @Test
    fun idempotentOnSecondCall() {
        val once = OptionsText.setOption("a:1\n", "k", "v")
        val twice = OptionsText.setOption(once, "k", "v")
        assertEquals(once, twice)
    }

    @Test
    fun keyPrefixNotConfused() {
        // "graphicsBackend" 的替换不得误伤 "preferredGraphicsBackend" 行（行首锚定）
        val content = "preferredGraphicsBackend:\"default\"\n"
        val result = OptionsText.setOption(content, "graphicsBackend", "\"opengl\"")
        assertEquals("preferredGraphicsBackend:\"default\"\ngraphicsBackend:\"opengl\"\n", result)
    }
}
