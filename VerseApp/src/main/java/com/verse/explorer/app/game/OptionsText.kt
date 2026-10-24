package com.verse.explorer.app.game

/**
 * options.txt 文本操作纯函数集合。
 *
 * 从 OptionsInjector 抽出的无副作用逻辑（不碰 Context/文件系统），
 * 便于单元测试直接覆盖键值替换与追加行为。
 */
internal object OptionsText {

    /**
     * options.txt 精确替换/追加单个键。value 为原始 options 行等号后的字面量（字符串需自带引号）。
     * 只改目标键，不整写模板，规避 MC 26.2 对旧格式行 "Skipping bad option" 的启动期异常。
     */
    fun setOption(content: String, key: String, value: String): String {
        val regex = Regex("^$key:.*$", RegexOption.MULTILINE)
        val line = "${key}:$value"
        return if (regex.containsMatchIn(content)) {
            regex.replace(content, line)
        } else {
            (if (content.trimEnd().isEmpty()) content else content.trimEnd() + "\n") + line + "\n"
        }
    }
}
