package com.verse.explorer.app.device

import androidx.annotation.Keep
import com.verse.explorer.app.ui.VerseLogger
import java.io.File

private const val TAG = "VulkanChecker"

/**
 * 设备的原始 Vulkan 支持情况
 */
@Keep
data class VulkanCapabilities(
    val apiVersionMajor: Int,
    val apiVersionMinor: Int,
    val apiVersionPatch: Int,
    val extensions: List<String>,
    val features: Map<String, Boolean>,
    /** 检测时是否实际经自定义驱动（Turnip）加载；请求 Turnip 但加载失败回落系统加载器时为 false */
    val usedCustomDriver: Boolean = false
) {
    /** Vulkan 版本字符串 */
    val versionString: String
        get() = "$apiVersionMajor.$apiVersionMinor.$apiVersionPatch"

    /** 检查 Vulkan 版本是否至少为 1.2 */
    val isVersionSupported: Boolean
        get() = apiVersionMajor > 1 || (apiVersionMajor == 1 && apiVersionMinor >= 2)
}

@Keep
fun interface VulkanLogCallback {
    fun log(level: String, message: String)
}

@Keep
object VulkanChecker {
    init {
        try {
            System.loadLibrary("vulkan_check")
            nativeSetLogCallback { level, msg ->
                when (level) {
                    "INFO" -> VerseLogger.info(msg, TAG)
                    "WARN" -> VerseLogger.warn(msg, TAG)
                    else -> VerseLogger.error(msg, TAG)
                }
            }
        } catch (e: UnsatisfiedLinkError) {
            VerseLogger.warn("Failed to load vulkan_check library: ${e.message}", TAG)
        }
    }

    /**
     * 查询设备 Vulkan 支持情况，[useTurnip] 时经 liblinkerhook 加载 [driverPath] 下的 Turnip 驱动
     * @return 如果不支持 Vulkan 或初始化失败，返回 null
     */
    fun checkCapabilities(
        useTurnip: Boolean,
        driverPath: String?,
        cacheDir: File?
    ): VulkanCapabilities? {
        return try {
            // native 侧把该目录作为驱动补丁文件的输出目录，不存在时 dlopen 补丁会直接失败
            cacheDir?.mkdirs()
            nativeCheckVulkan(useTurnip, driverPath, cacheDir?.absolutePath)
        } catch (e: UnsatisfiedLinkError) {
            VerseLogger.warn("Native library or method not found: ${e.message}", TAG)
            null
        } catch (e: Exception) {
            VerseLogger.warn("Native check failed: ${e.message}", TAG)
            null
        } finally {
            cacheDir?.deleteRecursively()
        }
    }

    @JvmStatic
    private external fun nativeSetLogCallback(callback: VulkanLogCallback)

    @JvmStatic
    private external fun nativeCheckVulkan(
        useTurnip: Boolean,
        driverPath: String?,
        cacheDir: String?
    ): VulkanCapabilities?
}
