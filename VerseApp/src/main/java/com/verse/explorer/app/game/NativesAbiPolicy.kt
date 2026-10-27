package com.verse.explorer.app.game

/**
 * APK 原生库目标 ABI 与 JNA 条目选择。
 *
 * 纯逻辑：设备 ABI 列表由调用方从 Build 读取后传入。
 */
internal object NativesAbiPolicy {

    private val packagedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    /** 按设备顺序挑第一个 APK 实际打包的 ABI；未知设备按 arm64 处理 */
    fun resolve(supportedAbis: List<String>): String =
        supportedAbis.firstOrNull { it in packagedAbis } ?: "arm64-v8a"

    fun jnaEntries(abi: String): List<String> = when (abi) {
        "arm64-v8a" -> listOf(
            "com/sun/jna/android-aarch64/libjnidispatch.so",
            "com/sun/jna/linux-aarch64/libjnidispatch.so"
        )
        "armeabi-v7a" -> listOf(
            "com/sun/jna/android-arm/libjnidispatch.so",
            "com/sun/jna/linux-arm/libjnidispatch.so"
        )
        "x86_64" -> listOf(
            "com/sun/jna/android-x86-64/libjnidispatch.so",
            "com/sun/jna/linux-x86-64/libjnidispatch.so",
            "com/sun/jna/linux-amd64/libjnidispatch.so"
        )
        "x86" -> listOf(
            "com/sun/jna/android-x86/libjnidispatch.so",
            "com/sun/jna/linux-x86/libjnidispatch.so"
        )
        else -> listOf(
            "com/sun/jna/android-aarch64/libjnidispatch.so",
            "com/sun/jna/linux-aarch64/libjnidispatch.so"
        )
    }
}
