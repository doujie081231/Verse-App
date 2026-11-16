package com.verse.explorer.launcher.renderer.config

import android.content.Context
import android.util.Log
import com.verse.explorer.launcher.renderer.RendererManager
import org.json.JSONObject
import java.io.File

/**
 * 渲染器配置的对外入口：解析当前生效值、启动前把值落到驱动能读到的位置。
 *
 * 启动链路只需要调 [writeLaunchConfig] 一次；失败不阻断启动，只记日志。
 */
object RendererConfigSupport {

    private const val TAG = "RendererConfig"

    /** MobileGlues 驱动的默认配置目录（与官方插件一致） */
    private const val MOBILE_GLUES_PRIMARY_DIR = "/sdcard/MG"

    private const val SFPEW_LIBRARY = "libSimpleFPEWrapper.so"

    /** 兼容层默认值由「开」改为「关」的一次性迁移标记 */
    private const val FLAG_SFPEW_DEFAULT_OFF = "migrated:sfpew-default-off-v1"

    /** 自带固定管线实现的模组：它们和兼容层抢同一批 GL 入口，必须让路 */
    private val FPE_EMULATOR_MOD_IDS = listOf("angelica", "actinium")

    private val MOD_METADATA_FILES = listOf("fabric.mod.json", "quilt.mod.json")

    private val MOD_ID_PATTERN = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")

    data class Resolved(
        val spec: RendererConfigSpec?,
        val values: Map<String, Any>
    ) {
        val configurable: Boolean get() = spec != null
    }

    /** 该渲染器在当前版本下要下发的环境变量（Env 型字段） */
    fun launchEnv(context: Context, rendererId: String, versionId: String?): Map<String, String> {
        val resolved = resolve(context, rendererId, versionId)
        val spec = resolved.spec ?: return emptyMap()
        return RendererConfigEnv.build(spec, resolved.values)
    }

    /** 解析某个渲染器在指定版本下生效的配置（版本级 > 全局 > 默认） */
    fun resolve(context: Context, rendererId: String, versionId: String?): Resolved {
        val spec = RendererConfigSpecs.of(rendererId)
            ?: return Resolved(null, emptyMap())
        val store = RendererConfigStore(context)
        // 兼容层旧默认值（true）在存储里无法与用户主动开启区分，升级时统一清掉，
        // 让「默认关闭」真正生效；用户需要时在界面上重新打开即可。
        store.migrateAll(FLAG_SFPEW_DEFAULT_OFF, RendererConfigResolver::dropLegacyDefaults)
        val global = store.loadGlobal(spec.rendererId, spec)
        val version = versionId?.let { store.loadVersion(it, spec.rendererId, spec) } ?: emptyMap()
        return Resolved(spec, RendererConfigResolver.resolve(spec, global, version))
    }

    /**
     * SimpleFPEWrapper 兼容层的启用判定。
     *
     * 三个条件同时满足才挂载：用户在「渲染器配置」里开着、兼容层库确实在包里、
     * 当前版本没有自带 FPE 实现的模组（ANGELICA / ACTINIUM 会与兼容层抢同一批入口）。
     */
    data class SfpewDecision(val enabled: Boolean, val libraryPath: String?)

    fun sfpewDecision(
        context: Context,
        rendererId: String,
        versionId: String?,
        gameDir: File?
    ): SfpewDecision {
        val values = resolve(context, rendererId, versionId).values
        if (values["sfpewLayer"] != true) return SfpewDecision(false, null)
        val library = File(context.applicationInfo.nativeLibraryDir, SFPEW_LIBRARY)
        if (!library.isFile) {
            Log.w(TAG, "SimpleFPEWrapper 库缺失: ${library.absolutePath}")
            return SfpewDecision(false, null)
        }
        val modsDir = gameDir?.let { File(it, "mods") }
        if (modsDir != null && hasFpeEmulatorMods(modsDir)) {
            Log.i(TAG, "检测到自带 FPE 的模组，跳过兼容层")
            return SfpewDecision(false, null)
        }
        return SfpewDecision(true, library.absolutePath)
    }

    /**
     * 是否已安装自带固定管线实现的模组。
     *
     * 先读 jar 内的模组元数据（fabric.mod.json / quilt.mod.json 的 id），读不到再退回文件名。
     * 只读元数据里的 id 字段，不解压整个包；文件名兜底是为了覆盖元数据损坏或非标准打包的情况。
     */
    fun hasFpeEmulatorMods(modsDir: File): Boolean {
        val jars = modsDir.listFiles { f -> f.isFile && f.extension.equals("jar", true) }
            ?: return false
        return jars.any { jar ->
            val id = readModId(jar)
            if (id != null) {
                FPE_EMULATOR_MOD_IDS.any { it.equals(id, ignoreCase = true) }
            } else {
                val name = jar.name.lowercase()
                FPE_EMULATOR_MOD_IDS.any { name.contains(it) }
            }
        }
    }

    private fun readModId(jar: File): String? = runCatching {
        java.util.zip.ZipFile(jar).use { zip ->
            for (entry in MOD_METADATA_FILES) {
                val e = zip.getEntry(entry) ?: continue
                val text = zip.getInputStream(e).bufferedReader().use { it.readText() }
                // 只取 id 字段，不引入 JSON 解析依赖：元数据体积小、结构固定
                MOD_ID_PATTERN.find(text)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                    ?.let { return@use it }
            }
            null
        }
    }.getOrNull()

    /**
     * 启动前把配置写进驱动读取的位置。
     *
     * @return 写入的文件；渲染器不可配置或写入失败返回 null
     */
    fun writeLaunchConfig(context: Context, rendererId: String, versionId: String?): File? {
        val resolved = resolve(context, rendererId, versionId)
        val spec = resolved.spec ?: return null
        return when {
            spec.rendererId.equals(RendererManager.ID_MOBILEGLUES, ignoreCase = true) ->
                writeMobileGluesConfig(context, spec, resolved.values)
            else -> null
        }
    }

    /**
     * MobileGlues 的 config.json 是"读-改-写"：
     * 受管键写成界面上的选择，其它键（用户手写、官方插件写入）原样保留。
     */
    private fun writeMobileGluesConfig(
        context: Context,
        spec: RendererConfigSpec,
        values: Map<String, Any>
    ): File? {
        val managed = RendererConfigJson.jsonValues(spec, values)
        // 与 FCL 启动侧保持同一套落点顺序：先试 /sdcard/MG，不可写时退到应用外部私有目录
        // （那种情况下启动侧会注入 MG_DIR_PATH 指向同一个目录）。
        val candidates = listOf(
            File(MOBILE_GLUES_PRIMARY_DIR),
            appPrivateMobileGluesDir(context)
        ).distinctBy { it.absolutePath }
        for (dir in candidates) {
            val file = File(dir, "config.json")
            val written = runCatching {
                if (!dir.isDirectory && !dir.mkdirs()) return@runCatching false
                val merged = RendererConfigJson.mergeInto(readJson(file), managed)
                file.writeText(toJson(merged))
                true
            }.getOrElse { e ->
                Log.w(TAG, "写 MobileGlues 配置失败(${file.absolutePath}): ${e.message}")
                false
            }
            if (written) return file
        }
        return null
    }

    private fun appPrivateMobileGluesDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "MG")
    }

    private fun readJson(file: File): Map<String, Any?> {
        if (!file.isFile) return emptyMap()
        return runCatching {
            val json = JSONObject(file.readText())
            val out = LinkedHashMap<String, Any?>(json.length())
            for (key in json.keys()) {
                out[key] = json.opt(key)
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun toJson(values: Map<String, Any?>): String {
        val json = JSONObject()
        for ((key, value) in values) {
            if (value != null) json.put(key, value)
        }
        return json.toString(2)
    }
}
