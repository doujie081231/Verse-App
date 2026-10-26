package com.verse.explorer.core.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 版本目录扫描：添加/导入外部游戏目录后能否识别出版本。
 *
 * 这里锁死两条与上游一致的容错规则——它们直接决定「添加文件夹后识别不到版本」：
 *  1. 版本描述文件优先 `<目录名>.json`，缺失时取目录内**唯一**的 json
 *     （其它启动器导出的版本目录常见名字不一致）；
 *  2. 不要求目录内必须有同名 jar（版本可能走 inheritsFrom 共享父版本 jar，
 *     或来自只带 json 的整合目录），能不能启动由启动链自己报错。
 */
class VersionScanTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun manager() = VersionManager(tmp.root)

    private fun versionsDir() = File(tmp.root, "versions")

    private fun makeVersionDir(name: String, jsonName: String, idInJson: String = name): File {
        val dir = File(versionsDir(), name)
        dir.mkdirs()
        File(dir, jsonName).writeText("""{"id":"$idInJson","type":"release"}""")
        return dir
    }

    @Test
    fun standardLayout_listed() {
        makeVersionDir("1.20.1", "1.20.1.json")
        val ids = manager().getLocalVersions().map { it.id }
        assertEquals(listOf("1.20.1"), ids)
    }

    @Test
    fun jsonNameMismatch_singleJsonFallback_listed() {
        // 目录 1.20.1-forge，json 叫 forge-47.2.0.json：唯一 json 兜底识别
        makeVersionDir("1.20.1-forge", "forge-47.2.0.json", idInJson = "1.20.1-forge-47.2.0")
        val list = manager().getLocalVersions()
        assertEquals(1, list.size)
        // 以目录名为准，避免启动/删除时按 id 找不到目录
        assertEquals("1.20.1-forge", list[0].id)
    }

    @Test
    fun multipleJsonsWithoutMatchingName_ignored() {
        val dir = File(versionsDir(), "broken").apply { mkdirs() }
        File(dir, "a.json").writeText("""{"id":"a"}""")
        File(dir, "b.json").writeText("""{"id":"b"}""")
        assertTrue(manager().getLocalVersions().isEmpty())
    }

    @Test
    fun versionWithoutJar_listed() {
        // 无 jar 也列出：是否可启动交给启动链判断
        makeVersionDir("1.19.4", "1.19.4.json")
        val ids = manager().getLocalVersions().map { it.id }
        assertTrue(ids.contains("1.19.4"))
    }

    @Test
    fun removedLeftoverStaysHidden() {
        makeVersionDir("keep", "keep.json")
        val leftover = File(versionsDir(), "keep_removed").apply { mkdirs() }
        File(leftover, "keep_removed.json").writeText("""{"id":"keep_removed"}""")
        val ids = manager().getLocalVersions().map { it.id }
        assertEquals(listOf("keep"), ids)
    }

    @Test
    fun emptyVersionsDir_emptyList() {
        versionsDir().mkdirs()
        assertTrue(manager().getLocalVersions().isEmpty())
        assertFalse(File(tmp.root, "versions").listFiles().orEmpty().any { it.isFile })
    }
}
