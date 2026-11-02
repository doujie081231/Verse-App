package com.verse.explorer.core.resource

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * 读取模组 jar 里的 ModID，用于「这个前置是不是已经装了」的判定。
 *
 * 判定「同一个模组是否已安装」按 ModID，而不是文件名：
 *  1. `fabric.mod.json` → `id`（Fabric / Quilt）
 *  2. `META-INF/mods.toml` / `META-INF/neoforge.mods.toml` → `modId = "xxx"`（Forge / NeoForge）
 *  3. `mcmod.info` → `modid`（1.12 及更早的 Forge）
 *
 * 为什么不按文件名判重：同一个模组不同版本的文件名完全不同
 * （`sodium-fabric-0.5.11+mc1.20.1.jar` vs `sodium-fabric-0.6.0+mc1.20.1.jar`），
 * 按文件名判会把「已装」判成「没装」，导致重复安装两个同名模组。
 */
object ModJarInspector {

    /** path|size|lastModified → ModID 集合（避免重复解压同一个 jar） */
    private val cache = ConcurrentHashMap<String, Set<String>>()

    /** 一个 ModID 归一化：小写、去掉非字母数字（`Sodium` / `sodium` / `sodium-fabric` 视为同类比较） */
    fun normalize(id: String): String =
        id.trim().lowercase().filter { it.isLetterOrDigit() }

    /** 读取单个 jar 里声明的所有 ModID（未归一化，原始写法） */
    fun readModIds(jar: File): Set<String> {
        if (!jar.isFile || jar.length() == 0L) return emptySet()
        val key = "${jar.absolutePath}|${jar.length()}|${jar.lastModified()}"
        cache[key]?.let { return it }
        val ids = runCatching {
            ZipFile(jar).use { zip ->
                val out = LinkedHashSet<String>()
                // Fabric / Quilt
                zip.getEntry("fabric.mod.json")?.let { entry ->
                    val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    runCatching { JSONObject(text).optString("id") }
                        .getOrNull()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
                }
                // Forge 1.13+ / NeoForge
                listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml").forEach { name ->
                    zip.getEntry(name)?.let { entry ->
                        val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        MOD_ID_REGEX.findAll(text).forEach { m ->
                            m.groupValues[1].takeIf { it.isNotBlank() }?.let { out.add(it) }
                        }
                    }
                }
                // Forge 1.12-
                zip.getEntry("mcmod.info")?.let { entry ->
                    val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    runCatching {
                        val trimmed = text.trim()
                        val array = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).optJSONArray("modList")
                        array?.let { arr ->
                            for (i in 0 until arr.length()) {
                                arr.optJSONObject(i)?.optString("modid")
                                    ?.takeIf { it.isNotBlank() }?.let { out.add(it) }
                            }
                        }
                    }
                }
                out.filter { it.length in 1..64 }.toSet()
            }
        }.getOrDefault(emptySet())
        // 只缓存小结果，避免异常 jar 反复解压；最多 2000 条
        if (cache.size < 2000) cache[key] = ids
        return ids
    }

    /** 读取目录下所有 jar 的 ModID（归一化后的小写字母数字串） */
    fun installedModIds(modsDir: File?): Set<String> {
        if (modsDir == null || !modsDir.isDirectory) return emptySet()
        val jars = modsDir.listFiles { f -> f.isFile && f.name.endsWith(".jar", ignoreCase = true) }
            ?: return emptySet()
        val out = HashSet<String>()
        jars.forEach { jar ->
            readModIds(jar).forEach { out.add(normalize(it)) }
        }
        return out
    }

    /** 目录里是否已有「同一个模组」（按 ModID 判，忽略文件名与版本） */
    fun hasModId(modsDir: File?, modId: String, excludeFile: File? = null): Boolean {
        val want = normalize(modId)
        if (want.isEmpty()) return false
        if (modsDir == null || !modsDir.isDirectory) return false
        val jars = modsDir.listFiles { f -> f.isFile && f.name.endsWith(".jar", ignoreCase = true) }
            ?: return false
        return jars.any { jar ->
            if (excludeFile != null && jar.absolutePath == excludeFile.absolutePath) return@any false
            readModIds(jar).any { normalize(it) == want }
        }
    }

    /** toml 里的 modId 声明（含 `modId="x"` / `modId = 'x'`） */
    private val MOD_ID_REGEX = Regex("""modId\s*=\s*["']([^"']+)["']""")
}
