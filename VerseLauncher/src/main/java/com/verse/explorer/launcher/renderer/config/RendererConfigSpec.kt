package com.verse.explorer.launcher.renderer.config

/**
 * 渲染器配置字段的种类。
 *
 * 只用三种：开关、枚举、整数。渲染器参数本质上就是这几种，
 * 保证 UI 能用同一套控件把任意渲染器的配置渲染出来。
 */
enum class ConfigFieldKind { SWITCH, CHOICE, NUMBER }

/**
 * 配置值最终写到哪里。
 *
 * - [Json]：写进渲染器的配置文件（如 MobileGlues 的 config.json）
 * - [Env]：作为进程环境变量注入（少数驱动用环境变量而不是配置文件）
 */
sealed class ConfigTarget {
    data class Json(val key: String) : ConfigTarget()
    data class Env(val key: String) : ConfigTarget()
}

/** 枚举型字段的一个候选值 */
data class ConfigOption(val value: Int, val label: String)

/**
 * 一个可配置项。
 *
 * @param defaultValue 只允许 Boolean / Int，存进 SharedPreferences 时按 JSON 原样保存
 * @param group 界面上的分组标题，空串归到「通用」
 */
data class RendererConfigField(
    val id: String,
    val title: String,
    val description: String,
    val kind: ConfigFieldKind,
    val defaultValue: Any,
    val target: ConfigTarget,
    val options: List<ConfigOption> = emptyList(),
    val range: IntRange? = null,
    val group: String = ""
) {
    init {
        require(defaultValue is Boolean || defaultValue is Int) {
            "配置项 $id 的默认值必须是 Boolean 或 Int"
        }
        if (kind == ConfigFieldKind.CHOICE) {
            require(options.isNotEmpty()) { "枚举项 $id 必须给出候选值" }
        }
    }

    /** 该字段的 JSON 键；Env 目标没有 JSON 键，返回 null */
    fun jsonKey(): String? = (target as? ConfigTarget.Json)?.key
}

/**
 * 某个渲染器的完整配置规范。
 *
 * [version] 在字段增删后递增：升版本时会清掉存储里已废弃的键，
 * 避免老键一直留在用户配置里影响驱动。
 */
data class RendererConfigSpec(
    val rendererId: String,
    val version: Int,
    val fields: List<RendererConfigField>
) {
    private val byId: Map<String, RendererConfigField> = fields.associateBy { it.id }

    fun field(id: String): RendererConfigField? = byId[id]

    fun defaults(): Map<String, Any> = fields.associate { it.id to it.defaultValue }
}
