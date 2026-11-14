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

    data class Resolved(
        val spec: RendererConfigSpec?,
        val values: Map<String, Any>
    ) {
        val configurable: Boolean get() = spec != null
    }

    /** 解析某个渲染器在指定版本下生效的配置（版本级 > 全局 > 默认） */
    fun resolve(context: Context, rendererId: String, versionId: String?): Resolved {
        val spec = RendererConfigSpecs.of(rendererId)
            ?: return Resolved(null, emptyMap())
        val store = RendererConfigStore(context)
        val global = store.loadGlobal(spec.rendererId, spec)
        val version = versionId?.let { store.loadVersion(it, spec.rendererId, spec) } ?: emptyMap()
        return Resolved(spec, RendererConfigResolver.resolve(spec, global, version))
    }

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
