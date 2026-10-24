package com.verse.explorer.core.resource

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地复现 App 的 Modrinth 版本接口解析链路（gson 解析 + ResourceVersion 映射），
 * 用真实接口返回的 JSON 验证解析与映射不抛异常、字段不丢失。
 * 测试数据固定为仓库内资源，任何机器/CI 上的结果一致。
 */
class ModrinthParseTest {

    private fun gson() = GsonBuilder().serializeNulls().create()

    private fun loadVersionsJson(): String =
        javaClass.getResourceAsStream("/modrinth/in_versions.json")!!.bufferedReader().use { it.readText() }

    @Test
    fun parseIndiumVersions() {
        val json = loadVersionsJson()
        val listType = object : TypeToken<List<ModrinthVersion>>() {}.type
        val list: List<ModrinthVersion> = gson().fromJson(json, listType)

        assertTrue("should parse 42 versions", list.size == 42)
        val withDeps = list.filter { !it.dependencies.isNullOrEmpty() }
        assertTrue("indium versions should carry dependencies", withDeps.size > 5)
        // 与 ResourceRepository 的映射逻辑保持一致（全兜底版）
        val mapped = list.mapNotNull { v ->
            val files = v.files.orEmpty()
            val primary = files.firstOrNull { it.primary } ?: files.firstOrNull()
            runCatching {
                ResourceVersion(
                    id = v.id ?: "",
                    name = v.name ?: "",
                    versionNumber = v.versionNumber ?: "",
                    datePublished = v.datePublished ?: "",
                    gameVersions = v.gameVersions.orEmpty(),
                    loaders = v.loaders.orEmpty(),
                    releaseType = v.versionType ?: "release",
                    downloads = v.downloads.toLong(),
                    changelog = v.changelog ?: "",
                    primaryFile = primary?.let {
                        ResourceFile(it.url ?: "", it.filename ?: "", it.size, it.primary, it.hashes?.get("sha1") ?: "")
                    },
                    files = files.map {
                        ResourceFile(it.url ?: "", it.filename ?: "", it.size, it.primary, it.hashes?.get("sha1") ?: "")
                    },
                    dependencies = v.dependencies.orEmpty().map {
                        ResourceDependency(it.projectId ?: "", it.versionId ?: "", it.fileName ?: "", it.dependencyType ?: "required")
                    },
                    source = "modrinth"
                )
            }.getOrNull()
        }
        assertTrue("mapping should keep all versions", mapped.size == 42)
        assertTrue("mapped should keep dependencies",
            mapped.any { it.dependencies.isNotEmpty() })
    }

    @Test
    fun fromJsonOnEmptyString() {
        // 复现 getProjectVersions 在 http body 为空时 gson 的行为（App 端曾报 "Failed to load versions: null"）
        val listType = object : TypeToken<List<ModrinthVersion>>() {}.type
        assertNull("空串应解析为 null", gson().fromJson<List<ModrinthVersion>>("", listType))
        assertNull("null 字面量应解析为 null", gson().fromJson<List<ModrinthVersion>>("null", listType))
    }

    @Test
    fun fromJsonValidArray() {
        // 正常数组入口：解析后元素数量与依赖字段可访问
        val listType = object : TypeToken<List<ModrinthVersion>>() {}.type
        val list: List<ModrinthVersion> = gson().fromJson(loadVersionsJson(), listType)
        assertEquals(42, list.size)
        assertTrue("每个版本都应有 id", list.all { !it.id.isNullOrEmpty() })
    }
}
