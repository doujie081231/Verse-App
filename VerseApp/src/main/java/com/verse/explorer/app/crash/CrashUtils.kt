// 对应 crash_analyzer/utils.rs — 正则工具与崩溃原因追加工具
// 注意：Rust 的 regex crate 不支持 lookbehind，原 lookbehind 模式需改写为捕获组

package com.verse.explorer.app.crash

import java.util.regex.Pattern

object CrashUtils {

    /** 正则匹配并返回第一个匹配项的完整文本 */
    fun regexSeek(text: String, pattern: String): String? {
        if (text.isEmpty()) {
            return null
        }
        return try {
            Regex(pattern).find(text)?.value
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 正则匹配并返回第一个捕获组的内容
     * 用于替代 lookbehind 的场景：`(?<=prefix)content` → 用 `(prefix)(content)` 捕获组
     */
    fun regexSeekGroup(text: String, pattern: String, group: Int): String? {
        if (text.isEmpty()) {
            return null
        }
        return try {
            val matcher = Pattern.compile(pattern).matcher(text)
            if (!matcher.find()) {
                return null
            }
            if (group > matcher.groupCount()) null else matcher.group(group)
        } catch (_: Exception) {
            null
        }
    }

    /** 正则匹配并返回所有匹配项的完整文本（多行匹配） */
    fun regexSeekAll(pattern: String, text: String): List<String> {
        if (text.isEmpty()) {
            return emptyList()
        }
        return try {
            Regex(pattern).findAll(text).map { it.value }.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 正则匹配并返回所有匹配项中指定捕获组的内容（多行匹配） */
    fun regexSeekAllGroup(pattern: String, text: String, group: Int): List<String> {
        if (text.isEmpty()) {
            return emptyList()
        }
        return try {
            val matcher = Pattern.compile(pattern).matcher(text)
            val out = ArrayList<String>()
            while (matcher.find()) {
                if (group > matcher.groupCount()) {
                    continue
                }
                matcher.group(group)?.let { out.add(it) }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 取文本中首个标记之后的部分 */
    fun afterLast(text: String, marker: String): String {
        val idx = text.lastIndexOf(marker)
        return if (idx >= 0) text.substring(idx + marker.length) else text
    }

    /** 取文本中 start 与 end 两个标记之间的部分 */
    fun between(text: String, start: String, end: String): String {
        val si = text.indexOf(start)
        if (si < 0) {
            return ""
        }
        val offset = si + start.length
        val ei = text.indexOf(end, offset)
        return if (ei >= 0) text.substring(offset, ei) else text.substring(offset)
    }

    /** 取文本在首个标记之前的部分 */
    fun beforeFirst(text: String, marker: String): String {
        val idx = text.indexOf(marker)
        return if (idx >= 0) text.substring(0, idx) else text
    }

    /** 正则检测文本是否匹配 */
    fun regexCheck(text: String, pattern: String): Boolean {
        if (text.isEmpty()) {
            return false
        }
        return try {
            Regex(pattern).containsMatchIn(text)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 追加崩溃原因，additional 去重后合并
     * [reasons]: 当前已有的原因 Map，key 为 CrashReason，value 为附加信息列表
     */
    fun appendReason(
        reasons: MutableMap<CrashReason, MutableList<String>>,
        reason: CrashReason,
        additional: List<String>?
    ) {
        val items = reasons[reason]
        if (items != null) {
            if (additional != null) {
                for (item in additional) {
                    if (item.isNotEmpty() && !items.contains(item)) {
                        items.add(item)
                    }
                }
            }
        } else {
            val newItems = additional?.filter { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()
            reasons[reason] = newItems
        }
    }

    /** 从单个附加信息构建 Vec */
    fun singleAdditional(s: String?): List<String>? {
        return s?.let { listOf(it) }
    }
}
