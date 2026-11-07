package com.verse.explorer.app.game

import android.content.Context
import com.tungsten.fclauncher.bridge.FCLBridge
import com.verse.explorer.app.device.VulkanCheckManager
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.core.data.VersionSettingsRepository.VersionSettings
import com.verse.explorer.jvmlauncher.JvmLauncherAPI
import java.io.File

/** 在游戏进程内按顺序配置原生日志、SDL 钩子和 Vulkan 驱动。 */
internal class NativeLaunchEnvironment(private val context: Context) {
    suspend fun prepare(
        gameDir: File,
        nativesDir: File,
        fclVersionId: String,
        mcVersionMajor: Int,
        verSettings: VersionSettings
    ) {
        // 诊断：JVM 的 stdout/stderr 在 Android 上默认丢弃（fd 指向 /dev/null），
        // 重定向到文件便于排查启动期错误（走外部存储，便于通过文件管理器/远程工具取回日志）。
        // 逐调用 try-catch：任何失败都必须落到 launch.log，杜绝被吞掉后再猜
        try {
            System.loadLibrary("versejvm")
            VerseLogger.info("[8/9 JVM启动] loadLibrary(versejvm) ok", TAG)
        } catch (t: Throwable) {
            VerseLogger.error("[8/9 JVM启动] loadLibrary(versejvm) 失败: $t", TAG)
        }
        // 诊断日志目录：提前计算（与下方 initializeHooks 无关，供 native 日志使用）
        val diagDir = LaunchLogPolicy.diagDir(context.getExternalFilesDir(null))
        if (diagDir != null) diagDir.mkdirs()
        // 执行 bytehook_init(BYTEHOOK_MODE_AUTOMATIC) → create_sdl_hooks（SDL_InitSubSystem
        // launcher 集成/窗口复用/EGL 兼容/Vulkan loader 一致性）+ create_sdl_dlopen_hooks
        // （dlsym 出口把 SDL 符号换成代理，LWJGL 经 dlopen+dlsym 解析的调用同样走代理）。
        // 注：自研 versesdl（hook SDL_GL_GetProcAddress/dlsym）已移除 —— 其 bytehook_init
        // 一次失败（MEMORY_ALLOCATION_FAILED）就把 bytehook 状态锁死，后续任何 init 均返回
        try {
            System.loadLibrary("pojavexec")
            FCLBridge.initializeHooks()
            VerseLogger.info("[8/9 JVM启动] FCLBridge.initializeHooks() ok（SDL hooks 已激活）", TAG)
        } catch (t: Throwable) {
            VerseLogger.error("[8/9 JVM启动] FCLBridge.initializeHooks() 失败: $t", TAG)
        }
        try {
            JvmLauncherAPI.nativeSetenv("VERSE_NATIVES_DIR", nativesDir.absolutePath)
            // native FLOGI 日志：外部可访问路径，Android 10+ 的 /sdcard 根目录不可写，
            // 必须用应用专属外部目录（getExternalFilesDir）。
            val logDir = LaunchLogPolicy.baseLogDir(diagDir, gameDir)
            val nativeLogs = LaunchLogPolicy.nativeLogs(logDir)
            JvmLauncherAPI.nativeSetLogPath(
                nativeLogs.native.absolutePath
            )
            JvmLauncherAPI.nativeRedirectStdio(
                nativeLogs.jvmOut.absolutePath
            )
            // glfw_hook（EGL/窗口创建/渲染库加载）的文件日志：崩溃现场最后操作点
            JvmLauncherAPI.nativeSetenv(
                "VERSE_GLFW_LOG_PATH",
                nativeLogs.glfw.absolutePath
            )
            // MC 26.3 SDL 渲染路径：RenderPearl 校验 LWJGL 的 GL provider 与
            // SDL_GL_GetProcAddress 返回的 glGetError 是否同一实现。LWJGL 侧经
            // -Dorg.lwjgl.opengl.libname 加载 libng_gl4es.so，而 SDL3 Android 后端
            // 的 SDL_GL_GetProcAddress 走系统 eglGetProcAddress（Mali 驱动实现），
            // 两地址必然不一致 → glGetError mismatch。versesdl 在 dlsym 出口把
            // SDL_GL_GetProcAddress 的 gl* 函数重定向到 libng_gl4es.so。
            // versesdl 的 bytehook_init 返回 ALREADY_CALLED(4) 时继续装 hook（不锁死）。
            JvmLauncherAPI.nativeSetenv(
                "VERSE_SDL_LOG_PATH",
                nativeLogs.sdl.absolutePath
            )
            JvmLauncherAPI.nativeSetenv(
                "VERSE_APP_LIB_DIR",
                context.applicationInfo.nativeLibraryDir
            )
            try {
                System.loadLibrary("versesdl")
                VerseLogger.info("[8/9 JVM启动] loadLibrary(versesdl) ok（SDL GL 对齐 hooks 已激活）", TAG)
            } catch (t: Throwable) {
                VerseLogger.error("[8/9 JVM启动] loadLibrary(versesdl) 失败: $t", TAG)
            }
            // 26.2+ 的版本初始化阶段会加载 Vulkan 查询设备能力，与渲染器是不是 OpenGL 无关，
            // 所以必须**无条件**按缓存评估一次设备能力，刷新 vkshim 门控
            // （VulkanCheckManager.needsVulkanShim）。以前只在 Vulkan 渲染器时评估，
            // MobileGlues/OpenGL 的设备永远拿到 shim=0，驱动缺 divisor/fillModeNonSolid 时
            // 就会拿到缺扩展的系统加载器 → 调用未实现入口 → SIGSEGV(pc=0)/退出码 6。
            // FCL 对 26.2+ 一律先做这一步能力评估（它还会据此拦截不支持 26.3 的机器）。
            runCatching {
                if (VulkanCheckManager.hasVulkanBackend(fclVersionId)) {
                    val useTurnip = !(if (!verSettings.usesGlobal) verSettings.vkDriverSystem
                    else SettingsRepository.getVkDriverSystem())
                    val driverPath = if (useTurnip) context.applicationInfo.nativeLibraryDir else null
                    VulkanCheckManager.ensureSupported(
                        context, fclVersionId, useTurnip, driverPath
                    )
                    VerseLogger.info(
                        "Vulkan 能力评估: shim=${VulkanCheckManager.needsVulkanShim}" +
                            "（版本=${fclVersionId}, turnip=$useTurnip）", TAG
                    )
                }
            }.onFailure { VerseLogger.warn("Vulkan 能力评估失败（仅影响 vkshim 门控）: ${it.message}", TAG) }

            // RenderPearl 的 Vulkan 后端（26.2+）要求 VK_EXT_vertex_attribute_divisor：
            // LWJGL/SDL 的 Vulkan 加载统一包装到 libvkshim.so（VULKAN_PTR）合成该扩展。
            // 仅 arm64-v8a 真机路径启用（模拟器 x86_64 / OpenGL 后端不受影响）。
            val vulkanEnv = LaunchDevicePolicy.vulkanEnvironment(
                mcVersionMajor = mcVersionMajor,
                supported64BitAbis = android.os.Build.SUPPORTED_64_BIT_ABIS.toList(),
                vkDriverSystem = if (!verSettings.usesGlobal) verSettings.vkDriverSystem
                else SettingsRepository.getVkDriverSystem(),
                needsVulkanShim = VulkanCheckManager.needsVulkanShim,
                pipelineCache = SettingsRepository.getVersexPipelineCache(),
                nativeLibraryDir = context.applicationInfo.nativeLibraryDir
            )
            if (vulkanEnv != null) {
                // "1"=系统驱动；"0"=Turnip，versesdl 加载链经 liblinkerhook 把 loader 的驱动
                // 加载请求转向 DRIVER_PATH/libvulkan_freedreno.so（不适用设备探测失败自动回落）
                JvmLauncherAPI.nativeSetenv(
                    "VULKAN_DRIVER_SYSTEM", if (vulkanEnv.driverSystem) "1" else "0"
                )
                JvmLauncherAPI.nativeSetenv(
                    "DRIVER_PATH", vulkanEnv.driverPath
                )
                // 补齐——divisor 合成或 fillModeNonSolid 降级模拟——时才启用包装层；
                // 无条件开启会让缺口不可补的设备（驱动 <1.2）在游戏内加载 Vulkan 句柄时崩溃）
                JvmLauncherAPI.nativeSetenv(
                    "VKSHIM_ENABLE", if (vulkanEnv.shimEnabled) "1" else "0"
                )
                VerseLogger.info(
                    "[8/9 JVM启动] VKSHIM_ENABLE=${if (vulkanEnv.shimEnabled) "1" else "0"}" +
                        "（needsVulkanShim=${vulkanEnv.shimEnabled}）",
                    TAG
                )
                // VerseX 渲染器（设置页「VerseX 渲染器/Pipeline 缓存」）：M1 为 pipeline 对象级缓存，
                // 经环境变量传给 vkshim（默认开，VERSERX_PIPELINE_CACHE=0 关闭）
                JvmLauncherAPI.nativeSetenv(
                    "VERSERX_PIPELINE_CACHE",
                    if (vulkanEnv.pipelineCache) "1" else "0"
                )
                if (vulkanEnv.shimEnabled) {
                    try {
                        org.libsdl.app.SDL.nativeEnableVulkanShim()
                        VerseLogger.info("[8/9 JVM启动] vkshim Vulkan 包装层已启用（VULKAN_PTR 指向 libvkshim.so）", TAG)
                    } catch (t: Throwable) {
                        VerseLogger.error("[8/9 JVM启动] nativeEnableVulkanShim 失败: $t", TAG)
                    }
                } else {
                    VerseLogger.info("[8/9 JVM启动] vkshim 包装层未启用（设备缺口不可补或无缺口）", TAG)
                }
            } else {
                VerseLogger.info("[8/9 JVM启动] 非 arm64/26.x：跳过 Vulkan 环境注入", TAG)
            }
            VerseLogger.info("[8/9 JVM启动] native 日志: ${logDir.absolutePath}", TAG)
        } catch (t: Throwable) {
            VerseLogger.error("[8/9 JVM启动] native 配置失败: $t", TAG)
        }
    }

    private companion object {
        const val TAG = "GameLaunchOrchestrator"
    }
}
