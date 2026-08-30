package com.verse.explorer.core.resource

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 本地复现 App 的 Modrinth 版本接口解析链路（与 ModrinthApi.getProjectVersions 完全一致），
 * 用真实接口返回的 JSON 验证：gson 解析 + ResourceVersion 映射是否抛异常。
 */
class ModrinthParseTest {

    private fun gson() = GsonBuilder().serializeNulls().create()

    @Test
    fun parseIndiumVersions() {
        val json = File("D:/temp_verse_check/in_versions.json").readText()
        val listType = object : TypeToken<List<ModrinthVersion>>() {}.type
        val list: List<ModrinthVersion> = gson().fromJson(json, listType)

        assertTrue("should parse 42 versions", list.size == 42)
        val withDeps = list.filter { !it.dependencies.isNullOrEmpty() }
        assertTrue("indium versions should carry dependencies", withDeps.size > 5)
        // 复刻 ResourceRepository 的映射（全兜底版）
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
        val parsed = gson().fromJson<List<ModrinthVersion>>("", listType)
        println("fromJson(\"\") -> $parsed")
        val parsed2 = gson().fromJson<List<ModrinthVersion>>("null", listType)
        println("fromJson(null 字面量) -> $parsed2")
    }
}