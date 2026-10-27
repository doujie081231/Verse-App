package com.verse.explorer.app.game

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/**
 * 启动账号 UUID 解析测试。
 *
 * 32 位无连字符 UUID 必须转换成标准带连字符形式；非 32 位输入按名称
 * 生成稳定 UUID（离线账号兼容路径），不能抛异常终止启动。
 */
class LaunchUuidPolicyTest {

    @Test
    fun compactUuid_isExpanded() {
        assertEquals(
            UUID.fromString("12345678-1234-1234-1234-1234567890ab"),
            LaunchUuidPolicy.parse("123456781234123412341234567890ab")
        )
    }

    @Test
    fun hyphenatedUuid_isKept() {
        assertEquals(
            UUID.fromString("12345678-1234-1234-1234-1234567890ab"),
            LaunchUuidPolicy.parse("12345678-1234-1234-1234-1234567890ab")
        )
    }

    @Test
    fun shortName_isStableNameUuid() {
        assertEquals(
            UUID.nameUUIDFromBytes("Steve".toByteArray()),
            LaunchUuidPolicy.parse("Steve")
        )
    }
}
