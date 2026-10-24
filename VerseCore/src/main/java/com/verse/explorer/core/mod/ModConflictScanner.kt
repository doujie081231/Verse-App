package com.verse.explorer.core.mod

import com.google.gson.JsonParser
import java.io.File
import java.util.zip.ZipFile

/**
 * 本地模组元数据与冲突扫描。
 *
 * 解决两类安装期就能发现、但目前只能等游戏崩溃才知道的问题：
 * 1. **同一 modid 装了多个版本**（`xxx-1.0.jar` 与 `xxx-1.1.jar` 共存）——
 *    Mixin 重复注入或类冲突，表现是启动即崩；
 * 2. **缺少前置模组**（依赖声明了别的 modid 但目录里没有）——表现是进世界时报
 *    `Missing or unsupported mandatory dependencies`。
 *
 * 只读 zip 与文本，不依赖 Android，可直接单测。
 */
object ModConflictScanner {

    /** 平台自带 id，不算「缺失依赖」 */
    private val PLATFORM_IDS = setOf(
        "minecraft", "java", "fabricloader", "fabric", "quiltloader", "quilt",
        "forge", "neoforge", "liteloader", "mixinextras", "cloth_config", "cloth-config"
    )

    data class Dependency(val modId: String, val mandatory: Boolean)

    data class LocalMod(
        val file: File,
        val modId: String,
        val name: String,
        val version: String,
        /** fabric / forge / neoforge / quilt / unknown */
        val loader: String,
        val dependencies: List<Dependency>,
        val sha1: String = ""
    )

    data class Conflict(
        val kind: Kind,
        val modId: String,
        val detail: String,
        val files: List<String>
    ) {
        enum class Kind(val displayName: String) {
            DUPLICATE_MOD_ID("重复安装"),
            MISSING_DEPENDENCY("缺少前置"),
            UNREADABLE("无法识别")
        }
    }

    data class ScanResult(
        val mods: List<LocalMod>,
        val conflicts: List<Conflict>
    ) {
        val hasConflict: Boolean get() = conflicts.isNotEmpty()
    }

    /**
     * 扫描模组目录。无法识别的 jar 不阻断扫描，单独记一条 UNREADABLE。
     */
    fun scan(modsDir: File): ScanResult {
        val jars = modsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".jar", ignoreCase = true) && !it.name.endsWith(".disabled", true) }
            ?.sortedBy { it.name.lowercase() }
            ?: return ScanResult(emptyList(), emptyList())

        val mods = mutableListOf<LocalMod>()
        val unreadable = mutableListOf<File>()
        for (jar in jars) {
            val parsed = runCatching { readJar(jar) }.getOrNull()
            if (parsed == null) unreadable.add(jar) else mods.add(parsed)
        }

        val conflicts = mutableListOf<Conflict>()

        // 1) 同一 modId 多份
        mods.groupBy { it.modId }.filter { it.value.size > 1 }.forEach { (modId, list) ->
            conflicts += Conflict(
                kind = Conflict.Kind.DUPLICATE_MOD_ID,
                modId = modId,
                detail = "同一模组存在 ${list.size} 个版本，需只保留一个",
                files = list.map { it.file.name }
            )
        }

        // 2) 缺少必需前置
        val installedIds = mods.map { it.modId }.toSet()
        mods.forEach { mod ->
            mod.dependencies
                .filter { it.mandatory && it.modId.lowercase() !in PLATFORM_IDS }
                .filter { it.modId !in installedIds }
                .forEach { dep ->
                    conflicts += Conflict(
                        kind = Conflict.Kind.MISSING_DEPENDENCY,
                        modId = dep.modId,
                        detail = "「${mod.name}」需要前置 $dep，但未安装",
                        files = listOf(mod.file.name)
                    )
                }
        }

        // 3) 无法识别的 jar（可能是资源包/损坏文件）
        unreadable.forEach { file ->
            conflicts += Conflict(
                kind = Conflict.Kind.UNREADABLE,
                modId = "",
                detail = "无法识别的 jar（缺少 mod 元数据），可能不是模组文件",
                files = listOf(file.name)
            )
        }

        // 同一 modId 的缺依赖提示会重复出现，按 modId+detail 去重
        val dedup = conflicts.distinctBy { "${it.kind}-${it.modId}-${it.detail}" }
        return ScanResult(mods, dedup)
    }

    /** 读取单个 jar 的模组元数据；读不到返回 null */
    fun readJar(jar: File): LocalMod? {
        ZipFile(jar).use { zip ->
            // Fabric / Quilt：fabric.mod.json（Quilt 同样读取该文件）
            val fabricEntry = zip.getEntry("fabric.mod.json")
            if (fabricEntry != null) {
                val text = zip.getInputStream(fabricEntry).bufferedReader().use { it.readText() }
                val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                if (json != null) {
                    val id = json.get("id")?.takeIf { !it.isJsonNull }?.asString ?: return null
                    val version = json.get("version")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val name = json.get("name")?.takeIf { !it.isJsonNull }?.asString ?: id
                    val deps = mutableListOf<Dependency>()
                    json.getAsJsonObject("depends")?.entrySet()?.forEach { (depId, value) ->
                        // 值是字符串或数组都视为「必需」
                        if (!value.isJsonNull) deps += Dependency(depId, mandatory = true)
                    }
                    return LocalMod(
                        file = jar,
                        modId = id,
                        name = name,
                        version = version,
                        loader = "fabric",
                        dependencies = deps
                    )
                }
            }

            // Forge / NeoForge：META-INF/mods.toml 或 META-INF/neoforge.mods.toml
            val tomlEntry = zip.getEntry("META-INF/neoforge.mods.toml")
                ?: zip.getEntry("META-INF/mods.toml")
            if (tomlEntry != null) {
                val text = zip.getInputStream(tomlEntry).bufferedReader().use { it.readText() }
                return parseForgeToml(jar, text)
            }
        }
        return null
    }

    /**
     * 最小 TOML 解析：只取第一个 `[[mods]]` 段的 modId/displayName/version，
     * 以及 `[[dependencies.<modid>]]` 段里的 modId + mandatory。
     * 完整 TOML 解析器不在本模块职责内，够用即可。
     */
    private fun parseForgeToml(jar: File, text: String): LocalMod? {
        var section = ""
        var modId = ""
        var displayName = ""
        var version = ""
        val deps = mutableListOf<Dependency>()

        var depModId = ""
        var depMandatory = true

        fun flushDep() {
            if (depModId.isNotBlank()) deps += Dependency(depModId, depMandatory)
            depModId = ""
            depMandatory = true
        }

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("[[dependencies.") -> {
                    flushDep()
                    section = "dependencies"
                }

                line.startsWith("[[mods]]") -> {
                    flushDep()
                    section = "mods"
                }

                line.startsWith("[") -> {
                    // 其它段（description/credits 等）不关心
                    if (section == "dependencies") flushDep()
                    section = "other"
                }

                line.contains('=') -> {
                    val key = line.substringBefore('=').trim()
                    val value = line.substringAfter('=').trim().trim('"')
                    when (section) {
                        "mods" -> when (key) {
                            "modId" -> if (modId.isBlank()) modId = value
                            "displayName" -> if (displayName.isBlank()) displayName = value
                            "version" -> if (version.isBlank()) version = value
                        }

                        "dependencies" -> when (key) {
                            "modId" -> depModId = value
                            "mandatory" -> depMandatory = value.equals("true", true)
                        }
                    }
                }
            }
        }
        flushDep()

        if (modId.isBlank()) return null
        // 依赖里的 minecraft/forge 等平台 id 由 scan() 过滤
        return LocalMod(
            file = jar,
            modId = modId,
            name = displayName.ifBlank { modId },
            version = version,
            loader = if (text.contains("neoforge", true)) "neoforge" else "forge",
            dependencies = deps
        )
    }
}
