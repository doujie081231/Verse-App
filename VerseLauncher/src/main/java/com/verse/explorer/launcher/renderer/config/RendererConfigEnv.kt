package com.verse.explorer.launcher.renderer.config

/**
 * 把配置值翻译成进程环境变量。
 *
 * 与 [RendererConfigJson] 对称：一个负责写配置文件，一个负责拼环境变量，
 * 都是纯函数，能在 JVM 上覆盖"哪些键会下发、false 时怎么处理"这类细节。
 */
object RendererConfigEnv {

    /**
     * @return 环境变量表；只包含 [ConfigTarget.Env] 的字段
     */
    fun build(spec: RendererConfigSpec, values: Map<String, Any>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (field in spec.fields) {
            val key = (field.target as? ConfigTarget.Env)?.key ?: continue
            val value = values[field.id] ?: field.defaultValue
            when (value) {
                is Boolean -> {
                    if (value) out[key] = "1"
                    else if (!field.omitWhenFalse) out[key] = "0"
                }
                is Int -> out[key] = value.toString()
                else -> Unit
            }
        }
        return out
    }
}
