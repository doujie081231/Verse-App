package com.verse.explorer.launcher.renderer.config

/**
 * 渲染器配置的三层解析：规范默认值 → 全局设置 → 版本级设置。
 *
 * 这里刻意不依赖 Android：输入是两张 Map，输出是一张 Map，
 * 顺序、类型收敛、脏值丢弃都能用纯 JVM 单测覆盖（见 RendererConfigResolverTest）。
 */
object RendererConfigResolver {

    /**
     * @param global 全局设置（用户在设置页改的）
     * @param version 版本级覆盖（只对当前版本生效，可为空）
     * @return 只包含 [spec] 声明过的键，值类型已按字段种类收敛
     */
    fun resolve(
        spec: RendererConfigSpec,
        global: Map<String, Any> = emptyMap(),
        version: Map<String, Any> = emptyMap()
    ): Map<String, Any> {
        val resolved = LinkedHashMap<String, Any>(spec.fields.size)
        for (field in spec.fields) {
            val value = sanitize(field, version[field.id])
                ?: sanitize(field, global[field.id])
                ?: field.defaultValue
            resolved[field.id] = value
        }
        return resolved
    }

    /**
     * 把存储里的值收敛成该字段合法的取值；不合法返回 null（调用方回退到下一层）。
     *
     * SharedPreferences 里的值可能来自旧版本、手工改过的文件或类型写错的迁移，
     * 这里做唯一的把关点，后面的 UI 和驱动都不用再判类型。
     */
    fun sanitize(field: RendererConfigField, raw: Any?): Any? {
        if (raw == null) return null
        return when (field.kind) {
            ConfigFieldKind.SWITCH -> when (raw) {
                is Boolean -> raw
                is String -> raw.toBooleanStrictOrNull()
                else -> null
            }
            ConfigFieldKind.CHOICE -> when (raw) {
                is Int -> raw.takeIf { candidate -> field.options.any { it.value == candidate } }
                is Number -> raw.toInt().takeIf { candidate -> field.options.any { it.value == candidate } }
                else -> null
            }
            ConfigFieldKind.NUMBER -> when (raw) {
                is Int -> raw.coerceInRange(field)
                is Number -> raw.toInt().coerceInRange(field)
                else -> null
            }
        }
    }

    private fun Int.coerceInRange(field: RendererConfigField): Int {
        val range = field.range ?: return this
        return coerceIn(range.first, range.last)
    }

    /**
     * 丢掉存储里 spec 已不认识的键。
     *
     * 字段删除或改名后，老值留在存储里不会生效，但会在下次"导出配置"时冒出来，
     * 所以每次读写前都按当前 spec 裁剪一次。
     */
    fun pruneStored(spec: RendererConfigSpec, stored: Map<String, Any>): Map<String, Any> {
        val known = spec.fields.mapTo(HashSet()) { it.id }
        return stored.filterKeys { it in known }
    }

    /**
     * 值发生变化的键（用于判断"是否有自定义"，界面显示"已修改 N 项"）。
     */
    fun changedFields(spec: RendererConfigSpec, values: Map<String, Any>): List<String> =
        spec.fields.filter { values[it.id] != it.defaultValue }.map { it.id }

    /**
     * 早期默认开启、现在默认关闭的本地开关。
     *
     * 这类值在存储里与"用户主动开启"无法区分，而继续生效会让新默认形同虚设
     * （典型表现：升级后兼容层仍然挂着，图形库被换成兼容层，26.3 起直接建不起后端）。
     * 升级时清掉，用户真需要就在界面上重新打开。
     */
    val LEGACY_DEFAULT_ON_KEYS = listOf("sfpewLayer")

    /**
     * 丢弃"旧默认值"留下的键：只清 `true`，`false` 与其它字段原样保留。
     *
     * 纯函数，便于在 JVM 上覆盖迁移规则，不需要 Android 环境。
     */
    fun dropLegacyDefaults(stored: Map<String, Any>): Map<String, Any> {
        if (stored.isEmpty()) return stored
        val stale = LEGACY_DEFAULT_ON_KEYS.filter { stored[it] == true }
        if (stale.isEmpty()) return stored
        return stored.filterKeys { it !in stale }
    }
}
