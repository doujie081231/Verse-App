package com.verse.explorer.core.resource

/**
 * 统一的资源详情模型（Modrinth / CurseForge 双源映射后的公共结构）。
 */
data class ResourceDetail(
    val id: String = "",
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val body: String = "",
    val iconUrl: String? = null,
    val downloads: Long = 0,
    val followers: Long = 0,
    val categories: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val gameVersions: List<String> = emptyList(),
    val license: String = "",
    val sourceUrl: String = "",
    val issuesUrl: String = "",
    val wikiUrl: String = "",
    val discordUrl: String = "",
    val gallery: List<ResourceGallery> = emptyList(),
    val dateCreated: String = "",
    val dateModified: String = "",
    val source: String = "modrinth",
    val projectType: String = ""
)

/** 画廊图片（对齐 Modrinth project.gallery：url + title） */
data class ResourceGallery(
    val url: String = "",
    val title: String = ""
)

/**
 * 统一的版本文件模型。
 */
data class ResourceFile(
    val url: String = "",
    val filename: String = "",
    val size: Long = 0,
    val primary: Boolean = false,
    val sha1: String = ""
)

/**
 * 统一的资源版本模型。
 */
data class ResourceVersion(
    val id: String = "",
    val name: String = "",
    val versionNumber: String = "",
    val datePublished: String = "",
    val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val releaseType: String = "release",
    val downloads: Long = 0,
    val changelog: String = "",
    val primaryFile: ResourceFile? = null,
    val files: List<ResourceFile> = emptyList(),
    val dependencies: List<ResourceDependency> = emptyList(),
    val source: String = "modrinth"
)

/** 版本依赖（对齐 Modrinth version.dependencies） */
data class ResourceDependency(
    val projectId: String = "",
    val versionId: String = "",
    val fileName: String = "",
    val type: String = "required"
)

/**
 * 搜索分页结果（对齐 PC searchMods：limit/offset 分页，total 用于上一页/下一页 disabled）。
 */
data class ResourceSearchResult(
    val hits: List<ModrinthSearchHit> = emptyList(),
    val total: Int = 0
)