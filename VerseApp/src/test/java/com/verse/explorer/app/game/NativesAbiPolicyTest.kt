package com.verse.explorer.app.game

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 原生库目标 ABI 测试。
 *
 * APK 只打包 arm64/armv7/x86/x86_64；真机 ABI 列表里可能还有其它值，
 * 必须按设备顺序挑第一个已打包 ABI，未知设备回退 arm64。
 */
class NativesAbiPolicyTest {

    @Test
    fun arm64Device_picksArm64() {
        assertEquals("arm64-v8a", NativesAbiPolicy.resolve(listOf("arm64-v8a")))
    }

    @Test
    fun x86_64Emulator_picksX86_64() {
        assertEquals("x86_64", NativesAbiPolicy.resolve(listOf("x86_64")))
    }

    @Test
    fun followDeviceOrder() {
        assertEquals(
            "x86_64",
            NativesAbiPolicy.resolve(listOf("mips64", "x86_64", "arm64-v8a"))
        )
    }

    @Test
    fun unknownAbis_fallBackToArm64() {
        assertEquals("arm64-v8a", NativesAbiPolicy.resolve(listOf("mips", "riscv64")))
    }

    @Test
    fun arm64JnaEntries_haveAndroidFirst() {
        assertEquals(
            listOf(
                "com/sun/jna/android-aarch64/libjnidispatch.so",
                "com/sun/jna/linux-aarch64/libjnidispatch.so"
            ),
            NativesAbiPolicy.jnaEntries("arm64-v8a")
        )
    }

    @Test
    fun x86_64JnaEntries_includeAllFallbacks() {
        assertEquals(
            listOf(
                "com/sun/jna/android-x86-64/libjnidispatch.so",
                "com/sun/jna/linux-x86-64/libjnidispatch.so",
                "com/sun/jna/linux-amd64/libjnidispatch.so"
            ),
            NativesAbiPolicy.jnaEntries("x86_64")
        )
    }
}
