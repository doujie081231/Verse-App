package com.verse.explorer.app.game

import org.json.JSONObject
import java.io.File

/**
 * 解析版本实例对应的**真实 Minecraft 版本号**。
 *
 * 实例目录名 / 版本 id 不一定是 MC 版本号：整合包实例叫「Fabulously Optimized」「RLCraft」，
 * 直接用 id 解析主版本号会得到 0，所有按 26.x 门控的逻辑随之失效。
 *
 * 真机证据（反馈 d9e32afa6eff29ec9f，1.2.7，26.2 Fabulously Optimized）：
 * ```
 *   versionId=Fabulously Optimized
 *   [8/9 JVM启动] 非 arm64/26.x：跳过 Vulkan 环境注入
 * ```
 * 设备是 arm64、版本是 26.2，却被判定成「非 26.x」，Vulkan 环境（驱动路径 / vkshim）
 * 一项都没注入，游戏回落到 gl4es 的 OpenGL 3.1，Sodium 着色器编译失败后 SIGSEGV。
 *
 * 解析顺序与启动器的版本继承关系一致：
 * 1. 版本 json 的 `clientVersion`（Fabric/Quilt 写入的真实 MC 版本）
 * 2. `inheritsFrom` 链路上的同一字段
 * 3. 版本 json 的 `id`
 * 4. 版本 id 里的版本号片段（如 `1.20.1-forge-47.0.1` → `1.20.1`）
 */
object McVersionResolver {

    private val VERSION_TOKEN = Regex("""(\d+\.\d+(?:\.\d+)?)""")

    /** 最多走 6 层继承，避免异常的 inheritsFrom 环导致死循环 */
    private const val MAX_DEPTH = 6

    fun resolve(gameDir: File, versionId: String): String? {
        var current = versionId
        val visited = LinkedHashSet<String>()
        while (current.isNotBlank() && visited.size < MAX_DEPTH && visited.add(current)) {
            val json = readVersionJson(gameDir, current) ?: break
            json.optString("clientVersion").takeIf { it.isNotBlank() }?.let { return it }
            val parent = json.optString("inheritsFrom").takeIf { it.isNotBlank() }
            val id = json.optString("id").takeIf { it.isNotBlank() }
            when {
                parent != null -> current = parent
                id != null && id != current -> current = id
                else -> break
            }
        }
        // 兜底：直接从版本 id 里抠版本号
        return VERSION_TOKEN.find(versionId)?.groupValues?.get(1)
    }

    /** 主版本号（"26.2" → 26，"1.21.1" → 1）；无法解析返回 0 */
    fun major(version: String?): Int {
        val text = version?.trim().orEmpty()
        if (text.isEmpty()) return 0
        return text.split('.')
            .firstOrNull()
            ?.takeWhile { it.isDigit() }
            ?.toIntOrNull()
            ?: 0
    }

    private fun readVersionJson(gameDir: File, id: String): JSONObject? = runCatching {
        val file = File(File(gameDir, "versions/$id"), "$id.json")
        if (!file.isFile || file.length() <= 0) null
        else JSONObject(file.readText())
    }.getOrNull()
}
