package com.verse.explorer.launcher.renderer.config

/**
 * 配置值 ⇄ 渲染器配置文件之间的纯函数映射。
 *
 * 拆出来是为了让"写进驱动的到底是什么"可以单测：给同一组值，
 * 输出必须稳定，且不能吞掉用户在文件里手写的键。
 */
object RendererConfigJson {

    /** 只保留 [ConfigTarget.Json] 的字段，按规范顺序输出 */
    fun jsonValues(spec: RendererConfigSpec, values: Map<String, Any>): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for (field in spec.fields) {
            val key = field.jsonKey() ?: continue
            val value = values[field.id] ?: field.defaultValue
            out[key] = value
        }
        return out
    }

    /**
     * 把受管键合并进已有配置文件。
     *
     * 规则：受管键以 [managed] 为准，其余键（用户手写、其它工具写入）原样保留 ——
     * config.json 是和 MG 官方插件共用的文件，删别人的键会直接破坏对方的配置。
     */
    fun mergeInto(existing: Map<String, Any?>, managed: Map<String, Any>): Map<String, Any?> {
        val merged = LinkedHashMap<String, Any?>(existing)
        for ((key, value) in managed) {
            merged[key] = value
        }
        return merged
    }
}
