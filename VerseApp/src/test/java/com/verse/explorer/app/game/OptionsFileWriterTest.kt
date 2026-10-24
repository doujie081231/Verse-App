package com.verse.explorer.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/**
 * options.txt 写入结果测试（启动链第一道可测关卡）。
 *
 * 覆盖三条会在真机上直接表现为「启动异常」的写入契约：
 * 欢迎引导键幂等、语言按版本分档、渲染后端键只改目标键。
 */
class OptionsFileWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun runDir(): File = tmp.newFolder("run")

    private fun countOf(text: String, needle: String): Int =
        Regex(Regex.escape(needle)).findAll(text).count()

    @Test
    fun onboarding_createsFileWithFlag() {
        val dir = runDir()
        val options = File(dir, "options.txt")

        OptionsFileWriter.writeOnboardingSkip(listOf(options))

        assertTrue(options.exists())
        assertTrue(options.readText().contains("onboardAccessibility:false"))
    }

    @Test
    fun onboarding_isIdempotent() {
        val dir = runDir()
        val options = File(dir, "options.txt")
        options.writeText("renderDistance:12\n")

        OptionsFileWriter.writeOnboardingSkip(listOf(options))
        OptionsFileWriter.writeOnboardingSkip(listOf(options))

        // 已有该键就不再追加：两次调用后仍然只有一行
        assertEquals(1, countOf(options.readText(), "onboardAccessibility:"))
        // 原有键不受影响
        assertTrue(options.readText().contains("renderDistance:12"))
    }

    @Test
    fun onboarding_dedupsSameTarget() {
        val dir = runDir()
        val options = File(dir, "options.txt")

        OptionsFileWriter.writeOnboardingSkip(listOf(options, options, options))

        assertEquals(1, countOf(options.readText(), "onboardAccessibility:"))
    }

    @Test
    fun language_oldVersionSkipped() {
        val dir = runDir()
        // 1.0（2011-11）没有语言选项：不写文件、requiredLang 为 null
        val outcome = OptionsFileWriter.writeGameLanguage(dir, Instant.parse("2011-11-18T00:00:00Z"))

        assertNull(outcome.requiredLang)
        assertFalse(File(dir, "options.txt").exists())
    }

    @Test
    fun language_legacyVersionUsesUpperUnderscore() {
        val dir = runDir()
        val options = File(dir, "options.txt")
        options.writeText("lang:en_us\nrenderDistance:8\n")

        // 1.7.10（2014）用大写国家码 zh_CN
        val outcome = OptionsFileWriter.writeGameLanguage(dir, Instant.parse("2014-06-26T00:00:00Z"))

        assertEquals("zh_CN", outcome.requiredLang)
        assertEquals("en_us", outcome.previousLang)
        assertTrue(outcome.changed)
        assertFalse(outcome.usedYosbr)
        val text = options.readText()
        assertTrue(text.contains("lang:zh_CN"))
        assertFalse(text.contains("lang:en_us"))
        assertTrue(text.contains("renderDistance:8"))
    }

    @Test
    fun language_modernVersionUsesLowercase() {
        val dir = runDir()
        val options = File(dir, "options.txt")
        options.writeText("renderDistance:8\n")

        // 1.20+（2023）用小写 zh_cn，且没有 lang 行时追加
        val outcome = OptionsFileWriter.writeGameLanguage(dir, Instant.parse("2023-06-12T00:00:00Z"))

        assertEquals("zh_cn", outcome.requiredLang)
        assertEquals("none", outcome.previousLang)
        assertTrue(outcome.changed)
        assertTrue(options.readText().contains("lang:zh_cn"))
    }

    @Test
    fun language_alreadyCorrect_noChange() {
        val dir = runDir()
        val options = File(dir, "options.txt")
        options.writeText("lang:zh_cn\n")

        val outcome = OptionsFileWriter.writeGameLanguage(dir, Instant.parse("2023-06-12T00:00:00Z"))

        assertFalse(outcome.changed)
        assertEquals("zh_cn", outcome.previousLang)
        assertEquals(1, countOf(options.readText(), "lang:"))
    }

    @Test
    fun language_fallsBackToYosbrWhenOptionsMissing() {
        val dir = runDir()
        val yosbr = File(File(File(dir, "config"), "yosbr"), "options.txt")
        yosbr.parentFile.mkdirs()
        yosbr.writeText("fov:0.0\n")

        val outcome = OptionsFileWriter.writeGameLanguage(dir, Instant.parse("2023-06-12T00:00:00Z"))

        assertTrue(outcome.usedYosbr)
        assertEquals(yosbr.absolutePath, outcome.optionsFile.absolutePath)
        assertTrue(yosbr.readText().contains("lang:zh_cn"))
        // 不能凭空造出 runDir/options.txt，否则会绕过 Yosbr
        assertFalse(File(dir, "options.txt").exists())
    }

    @Test
    fun backend_mobileGluesWritesAllCandidateKeysAsOpengl() {
        val dir = runDir()
        val options = File(dir, "options.txt")
        options.writeText("renderDistance:12\n")

        val entries = OptionsFileWriter.backendEntries(isMobileGlues = true, backend = "opengl")
        OptionsFileWriter.writeBackend(dir, entries)

        val text = options.readText()
        listOf(
            "preferredGraphicsBackend", "graphicsApi", "graphicsBackend",
            "apiPreference", "renderBackend"
        ).forEach { key ->
            assertTrue("缺少候选键 $key", text.contains("$key:\"opengl\""))
        }
        // 无关设置保持原样
        assertTrue(text.contains("renderDistance:12"))
    }

    @Test
    fun backend_plainRendererWritesSingleKey() {
        val dir = runDir()

        OptionsFileWriter.writeBackend(dir, OptionsFileWriter.backendEntries(false, "vulkan"))

        val text = File(dir, "options.txt").readText()
        assertTrue(text.contains("preferredGraphicsBackend:\"vulkan\""))
        assertFalse(text.contains("graphicsApi:"))
    }

    @Test
    fun backend_overwritesExistingValueWithoutDuplicating() {
        val dir = runDir()
        val options = File(dir, "options.txt")
        options.writeText("preferredGraphicsBackend:\"default\"\n")

        OptionsFileWriter.writeBackend(dir, OptionsFileWriter.backendEntries(false, "opengl"))

        val text = options.readText()
        assertTrue(text.contains("preferredGraphicsBackend:\"opengl\""))
        assertFalse(text.contains("\"default\""))
        assertEquals(1, countOf(text, "preferredGraphicsBackend:"))
    }
}
