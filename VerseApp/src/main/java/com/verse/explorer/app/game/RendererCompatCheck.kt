package com.verse.explorer.app.game

import android.content.Context
import com.tungsten.fclcore.util.versioning.GameVersionNumber
import com.verse.explorer.launcher.renderer.RendererManager
import com.verse.explorer.launcher.renderer.RendererManager.RendererConfig
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/**
 * 渲染器 ↔ 模组的兼容性检查。
 *
 * Sodium 从 0.6 起改用 GL 3.3 core 管线（区块几何用 uint 顶点数据 + UBO 传参），
 * 官方文档明确写明：**不支持 GL4ES / ANGLE 这类 OpenGL 转译层**，转译层没有实现所需功能，
 * 且存在无法解决的底层驱动问题。
 *
 * 真机证据（反馈 d9e32afa6eff29ec9f，26.2 Fabulously Optimized + NGGL4ES/Krypton）：
 * ```
 *   OpenGL Version: 3.1 §aKrypton Wrapper §bRelease 0.4.5
 *   ERROR: 3:53: '*' : no operation '*' exists that takes a left-hand operand of type
 *                     '3-component vector of uint' and a right operand of type 'const float'
 *   ERROR: 0:52: 'u_SectionTimeInfo' : undeclared identifier
 *   Couldn't compile pipeline sodium:pipeline/solid_terrain: vertex shader ... was invalid
 *   准备生成区域 100% → SIGSEGV → JVM 退出码 6
 * ```
 * 转译层翻不出 uint / UBO，管线对象是坏的，随后在原生侧跑飞 —— 这个组合不是「偶尔出事」，
 * 是每次进世界必崩。所以启动前拦住并给出可执行的换法，而不是让用户看闪退。
 *
 * 放行的例外：Mesa 系（Zink / Freedreno / VirGL）是真正的 OpenGL 实现，GL 版本 4.x；
 * MobileGlues 插件同样不是 gl4es 那套转译路径，实测可跑 Sodium，不做拦截。
 */
object RendererCompatCheck {

    data class Issue(val title: String, val detail: String, val action: String) {
        /** 汇总成启动检查错误行（多行展示） */
        fun toMessage(): String = "$title\n$detail\n$action"
    }

    private data class SodiumInfo(val jarName: String, val version: String?)

    /**
     * @return 不兼容时返回具体说明；兼容或无法判断时返回 null（放行）
     */
    fun check(context: Context, gameDir: File, versionId: String, rendererId: String): Issue? {
        // 渲染器未单独设置（空串）时按默认渲染器判断：默认就是 GL4ES
        val renderer = if (rendererId.isBlank()) RendererManager.getDefaultRenderer()
        else RendererManager.getRendererById(context, rendererId)
        if (renderer == null || !isTranslationLayer(renderer)) return null

        val modsDir = File(gameDir, "mods")
        val sodium = findSodium(modsDir) ?: return null

        val mcVersion = McVersionResolver.resolve(gameDir, versionId) ?: return null
        if (!requiresModernGl(sodium, mcVersion)) return null

        val glCap = glVersionCap(renderer)
        return Issue(
            title = "渲染器与 Sodium 不兼容，无法进入世界",
            detail = "Sodium${sodium.version?.let { " $it" } ?: ""}（${sodium.jarName}）需要 OpenGL 3.3 以上的核心管线，" +
                "当前渲染器 ${renderer.displayName} 最高只提供 OpenGL $glCap。" +
                "gl4es 这类转译层无法翻译 Sodium 的 uint/UBO 着色器，" +
                "进入世界时管线编译失败会直接崩溃（SIGSEGV）。",
            action = "请在「版本设置 → 渲染器」改用 Zink 或 MobileGlues（渲染器插件）后重新启动；" +
                "或在 mods 里移除 Sodium。"
        )
    }

    /**
     * 该渲染器是否走 OpenGL 转译层（GL4ES / FPE / VGPU 一类）。
     * Mesa 系（env 里带 MESA_GL_VERSION_OVERRIDE）返回 false。
     */
    fun isTranslationLayer(renderer: RendererConfig): Boolean {
        val env = renderer.envVars
        if (env.keys.any { it.equals("MESA_GL_VERSION_OVERRIDE", ignoreCase = true) }) return false
        // NGGL4ES 声明 LIBGL_GL=31；出现这个键就以它为准
        env.entries.firstOrNull { it.key.equals("LIBGL_GL", ignoreCase = true) }
            ?.value?.toIntOrNull()
            ?.let { return it < 33 }
        // 没有 LIBGL_GL 的老 gl4es / FPE / VGPU：GLES2 转译，GL 版本上限 2.x
        val glName = renderer.glName.lowercase()
        return listOf("gl4es", "fpew", "vgpu").any { glName.contains(it) }
    }

    /** 转译层对外声明的 GL 版本上限 */
    private fun glVersionCap(renderer: RendererConfig): String {
        val raw = renderer.envVars.entries
            .firstOrNull { it.key.equals("LIBGL_GL", ignoreCase = true) }
            ?.value?.toIntOrNull() ?: 20
        return if (raw >= 30) "${raw / 10}.${raw % 10}" else raw.toString()
    }

    /** 从 mods 目录里找 Sodium，并读出它的版本（fabric.mod.json / quilt.mod.json） */
    private fun findSodium(modsDir: File): SodiumInfo? {
        val jars = modsDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("jar", true) } ?: return null
        var fallback: SodiumInfo? = null
        for (jar in jars) {
            val fileName = jar.name
            if (!fileName.contains("sodium", ignoreCase = true)) continue
            // 以 mod 元数据为准：fabric.mod.json / quilt.mod.json 的 id 必须是 sodium 本体
            val version = readModVersion(jar, "sodium")
            if (version != null) return SodiumInfo(fileName, version)
            // 元数据读不到时按文件名兜底，但要排除 sodium-extra / reeses-sodium-options 这类附属模组
            if (fileName.startsWith("sodium-", ignoreCase = true) &&
                !fileName.startsWith("sodium-extra", ignoreCase = true)
            ) {
                if (fallback == null) fallback = SodiumInfo(fileName, null)
            }
        }
        return fallback
    }

    /** 读取模组元数据里的 version；id 不匹配返回 null */
    private fun readModVersion(jar: File, expectedId: String): String? = runCatching {
        var found: String? = null
        ZipFile(jar).use { zip ->
            for (entryName in listOf("fabric.mod.json", "quilt.mod.json")) {
                val entry = zip.getEntry(entryName) ?: continue
                val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val obj = JSONObject(text)
                val quiltLoader = obj.optJSONObject("quilt_loader")
                val id = obj.optString("id").ifBlank { quiltLoader?.optString("id").orEmpty() }
                if (!id.equals(expectedId, ignoreCase = true)) continue
                found = obj.optString("version")
                    .ifBlank { quiltLoader?.optString("version").orEmpty() }
                    .ifBlank { null }
                break
            }
        }
        found
    }.getOrNull()

    /**
     * Sodium 是否要求 GL 3.3 core 管线。
     * 0.6 起改用新管线；版本读不到时按「MC 1.21.2 及以后」判断（Sodium 0.6+ 自该版本起发布）。
     */
    private fun requiresModernGl(sodium: SodiumInfo, mcVersion: String): Boolean {
        val match = Regex("""(\d+)\.(\d+)""").find(sodium.version.orEmpty())
        if (match != null) {
            val major = match.groupValues[1].toIntOrNull() ?: 0
            val minor = match.groupValues[2].toIntOrNull() ?: 0
            return major > 0 || minor >= 6
        }
        return runCatching { GameVersionNumber.compare(mcVersion, "1.21.2") >= 0 }.getOrDefault(false)
    }
}
