package com.verse.explorer.core.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 离线用户名规则：单人游戏会把离线名当作玩家名，含中文/空格/符号时
 * 本地服务器会以 "Invalid characters in username" 拒绝连接。
 */
class OfflineUsernameTest {

    @Test
    fun acceptsLettersDigitsAndUnderscore() {
        assertTrue(AccountManager.isValidOfflineUsername("Player"))
        assertTrue(AccountManager.isValidOfflineUsername("Steve_123"))
        assertTrue(AccountManager.isValidOfflineUsername("A"))
        assertTrue(AccountManager.isValidOfflineUsername("a_1_B2"))
    }

    @Test
    fun rejectsChineseSpacesAndSymbols() {
        assertFalse(AccountManager.isValidOfflineUsername("离茶"))
        assertFalse(AccountManager.isValidOfflineUsername("你好 世界"))
        assertFalse(AccountManager.isValidOfflineUsername("player!"))
        assertFalse(AccountManager.isValidOfflineUsername("玩家_1"))
    }

    @Test
    fun rejectsEmptyAndTooLongNames() {
        assertFalse(AccountManager.isValidOfflineUsername(""))
        assertFalse(AccountManager.isValidOfflineUsername("abcdefghijklmnopq"))
        assertTrue(AccountManager.isValidOfflineUsername("abcdefghijklmnop"))
    }
}
