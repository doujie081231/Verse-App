package com.verse.explorer.core.resource

/**
 * MC 版本归一化与「适用版本」分组 —— 与 PCL 的实现逐条对齐：
 *
 * - `ModMinecraft.McInstanceInfo.IsFormatFit`      → [isFormatFit]
 * - `ModMinecraft.McInstanceInfo.VersionToDrop`    → [versionToDrop]
 * - `ModMinecraft.McInstanceInfo.DropToVersion`    → [dropToVersion]
 * - `PageDownloadCompDetail.GetGroupedVersionName` → [groupedName]
 *
 * PCL 的筛选维度是 **Drop 桶**（1.20 / 1.21 / 26.2），不是逐个补丁号：
 * 1.x 一律按 minor 折叠（1.20.1 与 1.20.4 同为 drop 200 → "1.20"），
 * 25 以后按 major.minor（26.2 → 262）。这样任何文件的任一 gameVersion 都能落进某个桶，
 * 不会出现「版本列表里有 1.12.2 却找不到对应标签」的漏项。
 */
object McVersionGroups {

    /** 1.x 系列（含 1.20.1 这种三段式） */
    private val FIT_1X = Regex("""^1\.\d""")

    /** 25 及以上的新版本号，如 26.2 */
    private val FIT_MODERN = Regex("""^[2-9]\d\.\d+""")

    /** 快照标签 */
    const val SNAPSHOT = "快照版"

    /** 远古版本（1.12 以前）折叠后的标签 */
    const val ANCIENT = "远古版"

    /** 无法识别 */
    const val OTHER = "其他"

    /** 文件没有任何可识别版本时的占位（PCL 同名字符串） */
    const val UNKNOWN = "未知版本"

    /** 折叠远古版的阈值：drop < 120 即 1.12 之前（PCL 同值） */
    private const val ANCIENT_DROP_THRESHOLD = 120

    /**
     * 是否是合法的 MC 版本号：`1.x…`，或 major > 25 的新式版本（26.1 / 26.2 …）。
     * 快照（24w14a）、加载器名（forge/fabric）、空值都返回 false。
     */
    fun isFormatFit(version: String?): Boolean {
        if (version.isNullOrBlank()) return false
        if (FIT_1X.containsMatchIn(version)) return true
        val matched = FIT_MODERN.find(version) ?: return false
        val major = matched.value.substringBefore('.').toDoubleOrNull() ?: return false
        return major > 25.0
    }

    /**
     * 版本 → Drop 序数（PCL `VersionToDrop`）：
     * 1.x → minor × 10；major ≥ 25 → major × 10 + minor；其余（含快照）→ 0。
     *
     * @param allowSnapshot true 时允许带 `-` 的后缀（`1.20-pre1` 这类），按 `-` 之前的部分计算
     */
    fun versionToDrop(version: String?, allowSnapshot: Boolean = false): Int {
        if (version.isNullOrBlank()) return 0
        if (!allowSnapshot && version.contains('-')) return 0
        val segments = version.substringBefore('-').split('.')
        if (segments.size < 2) return 0
        val major = segments[0].toDoubleOrNull()?.toInt() ?: return 0
        val minor = segments[1].toDoubleOrNull()?.toInt() ?: return 0
        if (major == 1) return minor * 10
        if (major < 25) return 0
        return major * 10 + minor
    }

    /** Drop 序数 → 展示名（PCL `DropToVersion`）：262 → "26.2"，200 → "1.20" */
    fun dropToVersion(drop: Int): String =
        if (drop >= 250) "${drop / 10}.${drop % 10}" else "1.${drop / 10}"

    /**
     * 分组名（PCL `GetGroupedVersionName`）：
     * 1. 含 "w" → 快照版
     * 2. 非合法版本号，或 foldOld 且 drop < 120 → 远古版
     * 3. groupedByDrop → Drop 桶名（1.20 / 26.2）
     * 4. 否则原样返回（精确小版本）
     */
    fun groupedName(name: String?, groupedByDrop: Boolean, foldOld: Boolean): String {
        if (name.isNullOrBlank()) return OTHER
        if (name.contains("w")) return SNAPSHOT
        if (!isFormatFit(name) ||
            (foldOld && versionToDrop(name, allowSnapshot = true) < ANCIENT_DROP_THRESHOLD)
        ) {
            return ANCIENT
        }
        if (groupedByDrop) return dropToVersion(versionToDrop(name, allowSnapshot = true))
        return name
    }

    /** 把一组版本号按从新到旧排序（PCL 的 GameVersions 也是降序） */
    fun sortDescending(versions: List<String>): List<String> =
        versions.sortedWith(versionComparator())

    /** 版本号比较器：数字段逐段比较，非数字（快照）排最后 */
    fun versionComparator(): Comparator<String> = Comparator { a, b ->
        val pa = numericParts(a)
        val pb = numericParts(b)
        if (pa == null && pb == null) a.compareTo(b)
        else if (pa == null) 1
        else if (pb == null) -1
        else compareParts(pb, pa)
    }

    private fun numericParts(version: String): List<Int>? {
        val core = version.substringBefore('-')
        if (!core.firstOrNull()?.isDigit().let { it == true }) return null
        return core.split('.').map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    private fun compareParts(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = (a.getOrNull(i) ?: 0) - (b.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }
}
