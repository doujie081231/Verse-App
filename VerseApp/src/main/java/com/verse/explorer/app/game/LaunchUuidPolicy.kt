package com.verse.explorer.app.game

import java.util.UUID

/** 启动账号 UUID 解析：32 位紧凑格式展开；其它输入按名称生成稳定 UUID。 */
internal object LaunchUuidPolicy {

    fun parse(raw: String): UUID {
        val compact = raw.replace("-", "")
        if (compact.length != 32) {
            return UUID.nameUUIDFromBytes(raw.toByteArray())
        }
        return UUID.fromString(
            compact.substring(0, 8) + "-" +
                compact.substring(8, 12) + "-" +
                compact.substring(12, 16) + "-" +
                compact.substring(16, 20) + "-" +
                compact.substring(20)
        )
    }
}
