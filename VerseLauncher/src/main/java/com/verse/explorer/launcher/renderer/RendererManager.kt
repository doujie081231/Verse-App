package com.verse.explorer.launcher.renderer

import android.content.Context
import android.util.Log
import java.io.File

// 旧版兼容类型：GameWindow / MinecraftLauncher 仍引用此签名，保留至 MinecraftLauncher 迁移完成
@Deprecated("Use RendererManager.RendererConfig for FCL-style renderer configuration")
enum class RendererType(val displayName: String, val glLibName: String) {
    ZINK("Zink (Vulkan)", "libOSMesa_8.so"),
    VIRGL("VirGL (OpenGL)", "libOSMesa.so"),
    GL4ES("GL4ES (OpenGL 2)", "libgl4es_12.so"),
    MG("Mobile GL (Custom)", "libMGDex.so"),
    NGGL4ES("ng-GL4ES", "libgl4es_12.so"),
    VGPU("VGPU (OpenGL)", "libvgpu.so"),
    FREEDRENO("Freedreno (Turnip)", "libOSMesa_8.so")
}

// 旧版兼容数据类：GameWindow / MinecraftLauncher 仍引用此签名，保留至 MinecraftLauncher 迁移完成
@Deprecated("Use RendererManager.RendererConfig for FCL-style renderer configuration")
data class RendererConfig(
    val type: RendererType,
    val width: Int = 1280,
    val height: Int = 720
)

object RendererManager {

    private const val TAG = "RendererManager"

    const val ID_GL4ES = "gl4es"
    const val ID_NGGL4ES = "nggl4es"
    const val ID_ZINK = "zink"
    const val ID_FREEDRENO = "freedreno"
    const val ID_VIRGL = "virgl"
    const val ID_VGPU = "vgpu"
    const val ID_MOBILEGLUES = "mobileglues"

    data class RendererConfig(
        val displayName: String,
        val id: String,
        val glName: String,
        val eglName: String,
        val pojavRenderer: String,
        val envVars: Map<String, String?>,
        val requiredLibs: List<String>,
        val minMcVersion: String = "",
        val maxMcVersion: String = ""
    )

    val GL4ES = RendererConfig(
        displayName = "Holy-GL4ES",
        id = ID_GL4ES,
        glName = "libgl4es_114.so",
        eglName = "libEGL.so",
        pojavRenderer = "opengles2",
        envVars = mapOf(
            "POJAV_RENDERER" to "opengles2",
            "LIBGL_ES" to "2",
            "LIBGL_MIPMAP" to "3",
            "LIBGL_NORMALIZE" to "1",
            "LIBGL_NOINTOVLHACK" to "1",
            "LIBGL_NOERROR" to "1",
            "LIBGL_NAME" to "libgl4es_114.so",
            "LIBEGL_NAME" to "libEGL.so"
        ),
        requiredLibs = listOf("libgl4es_114.so"),
        minMcVersion = "",
        maxMcVersion = "1.21.4"
    )

    // 完全对齐 FCL 1.3.3.5 Renderer.ID_NGGL4ES + FCLauncher.addRendererEnv：
    //   Renderer.java: eglName="libEGL.so"（系统 Mali EGL），glName="libng_gl4es.so"
    //   pojavEnv: LIBGL_USE_MC_COLOR=1, LIBGL_GL=31, LIBGL_ES=3, LIBGL_NORMALIZE=1, LIBGL_NOERROR=1,
    //             LIBGL_NAME=libng_gl4es.so, LIBEGL_NAME=libEGL.so
    //   FCLauncher: POJAVEXEC_EGL=eglName=libEGL.so, POJAV_RENDERER=opengles3, DLOPEN=libspirv-cross-c-shared.so
    // 之前误用 ANGLE（libEGL_angle.so + libGLESv2_angle.so）作为 EGL/GLES，与 FCL 的系统 Mali 驱动不同，
    // 且 libng_gl4es.so 是 2026-04 旧构建；FCL 1.3.3.5 用 2026-08 新构建。水面贴图错乱与滑动卡顿
    // 的对照差异即在此（FCL 同机正常）。
    val NGGL4ES = RendererConfig(
        displayName = "NGGL4ES/Krypton Wrapper",
        id = ID_NGGL4ES,
        glName = "libng_gl4es.so",
        eglName = "libEGL.so",
        pojavRenderer = "opengles3",
        envVars = mapOf(
            "POJAV_RENDERER" to "opengles3",
            "LIBGL_USE_MC_COLOR" to "1",
            "DLOPEN" to "libspirv-cross-c-shared.so",
            "LIBGL_GL" to "31",
            "LIBGL_ES" to "3",
            "LIBGL_NORMALIZE" to "1",
            "LIBGL_NOINTOVLHACK" to "1",
            "LIBGL_NOERROR" to "1",
            "POJAVEXEC_EGL" to "libEGL.so",
            "LIBGL_NAME" to "libng_gl4es.so",
            "LIBEGL_NAME" to "libEGL.so"
        ),
        requiredLibs = listOf("libng_gl4es.so", "libspirv-cross-c-shared.so"),
        minMcVersion = "",
        maxMcVersion = ""
    )

    val ZINK = RendererConfig(
        displayName = "Zink",
        id = ID_ZINK,
        glName = "libglxshim.so",
        eglName = "libEGL_mesa.so",
        pojavRenderer = "opengles3_desktopgl_zink_kopper",
        envVars = mapOf(
            "POJAV_RENDERER" to "opengles3_desktopgl_zink_kopper",
            "LIBGL_ES" to "3",
            "POJAVEXEC_EGL" to "libEGL_mesa.so",
            "MESA_GLSL_CACHE_DIR" to "",
            "MESA_GL_VERSION_OVERRIDE" to "4.6",
            "MESA_GLSL_VERSION_OVERRIDE" to "460",
            "force_glsl_extensions_warn" to "true",
            "allow_higher_compat_version" to "true",
            "allow_glsl_extension_directive_midshader" to "true",
            "MESA_LOADER_DRIVER_OVERRIDE" to "zink",
            "VTEST_SOCKET_NAME" to "",
            "LIBGL_NAME" to "libglxshim.so",
            "LIBEGL_NAME" to "libEGL_mesa.so"
        ),
        requiredLibs = listOf("libglxshim.so", "libEGL_mesa.so", "libcutils.so", "libglapi.so", "libzink_dri.so"),
        minMcVersion = "",
        maxMcVersion = ""
    )

    val FREEDRENO = RendererConfig(
        displayName = "Freedreno",
        id = ID_FREEDRENO,
        glName = "libOSMesa_8.so",
        eglName = "libEGL.so",
        pojavRenderer = "gallium_freedreno",
        envVars = mapOf(
            "POJAV_RENDERER" to "gallium_freedreno",
            "MESA_LOADER_DRIVER_OVERRIDE" to "zink",
            "MESA_GLSL_CACHE_DIR" to "",
            "MESA_GL_VERSION_OVERRIDE" to "4.6",
            "MESA_GLSL_VERSION_OVERRIDE" to "460",
            "force_glsl_extensions_warn" to "true",
            "allow_higher_compat_version" to "true",
            "allow_glsl_extension_directive_midshader" to "true",
            "VTEST_SOCKET_NAME" to "",
            "LIBGL_NAME" to "libOSMesa_8.so",
            "LIBEGL_NAME" to "libEGL.so"
        ),
        requiredLibs = listOf("libOSMesa_8.so", "libvulkan_freedreno.so"),
        minMcVersion = "",
        maxMcVersion = ""
    )

    val VIRGL = RendererConfig(
        displayName = "VirGL",
        id = ID_VIRGL,
        glName = "libOSMesa_81.so",
        eglName = "libEGL.so",
        pojavRenderer = "gallium_virgl",
        envVars = mapOf(
            "POJAV_RENDERER" to "gallium_virgl",
            "OSMESA_NO_FLUSH_FRONTBUFFER" to "1",
            "MESA_LOADER_DRIVER_OVERRIDE" to "zink",
            "MESA_GLSL_CACHE_DIR" to "",
            "MESA_GL_VERSION_OVERRIDE" to "4.3",
            "MESA_GLSL_VERSION_OVERRIDE" to "430",
            "force_glsl_extensions_warn" to "true",
            "allow_higher_compat_version" to "true",
            "allow_glsl_extension_directive_midshader" to "true",
            "VTEST_SOCKET_NAME" to "",
            "LIBGL_NAME" to "libOSMesa_81.so",
            "LIBEGL_NAME" to "libEGL.so"
        ),
        requiredLibs = listOf("libOSMesa_81.so"),
        minMcVersion = "",
        maxMcVersion = ""
    )

    val VGPU = RendererConfig(
        displayName = "VGPU",
        id = ID_VGPU,
        glName = "libvgpu.so",
        eglName = "libEGL.so",
        pojavRenderer = "opengles2_vgpu",
        envVars = mapOf(
            "POJAV_RENDERER" to "opengles2_vgpu",
            "LIBGL_ES" to "2",
            "LIBGL_MIPMAP" to "3",
            "LIBGL_NORMALIZE" to "1",
            "LIBGL_NOINTOVLHACK" to "1",
            "LIBGL_NOERROR" to "1",
            "LIBGL_NAME" to "libvgpu.so",
            "LIBEGL_NAME" to "libEGL.so"
        ),
        requiredLibs = listOf("libvgpu.so"),
        minMcVersion = "",
        maxMcVersion = "1.16.5"
    )

    // 对齐 MobileGlues 官方插件 V2.0.0（MobileGL-Dev/MobileGlues-release）AndroidManifest 的
    //   FCL 插件元数据：renderer="MobileGlues:libmobileglues.so:libmobileglues.so"（glName 与
    //   eglName 均为 libmobileglues.so，MobileGlues 自带 EGL 实现），pojavEnv="LIBGL_ES=3:
    //   POJAV_RENDERER=opengles3:POJAVEXEC_EGL=libmobileglues.so:LIBGL_EGL=libmobileglues.so:
    //   MG_COUNT_LAUNCH=1"，des="MobileGlues (OpenGL 4.0, 1.17+)"，minMCver=1.17。
    //   LIBGL_NAME 同步取 glName，与其余渲染器配置风格一致。
    //   MG 配置目录默认 /sdcard/MG（无 config.json 时驱动用内置默认值，源码 settings.cpp 走
    //   "Failed to load config. Use default config." 路径）；MG_ANGLE_DIR 不设置，驱动按 soname
    //   在进程内搜索 ANGLE（本工程已打包 libEGL_angle.so/libGLESv2_angle.so）。
    val MOBILEGLUES = RendererConfig(
        displayName = "MobileGlues",
        id = ID_MOBILEGLUES,
        glName = "libmobileglues.so",
        eglName = "libmobileglues.so",
        pojavRenderer = "opengles3",
        envVars = mapOf(
            "POJAV_RENDERER" to "opengles3",
            "POJAVEXEC_EGL" to "libmobileglues.so",
            "LIBGL_EGL" to "libmobileglues.so",
            "LIBGL_NAME" to "libmobileglues.so",
            "LIBGL_ES" to "3",
            "MG_COUNT_LAUNCH" to "1"
        ),
        requiredLibs = listOf("libmobileglues.so"),
        minMcVersion = "1.17",
        maxMcVersion = ""
    )

    private val allRenderers: List<RendererConfig> = listOf(
        GL4ES,
        NGGL4ES,
        MOBILEGLUES,
        ZINK,
        FREEDRENO,
        VIRGL,
        VGPU
    )

    fun allRenderers(): List<RendererConfig> = allRenderers

    fun availableRenderers(context: Context): List<RendererConfig> {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        return allRenderers.filter { renderer ->
            areRequiredLibsPresent(nativeLibDir, renderer)
        }
    }

    fun getRendererById(id: String): RendererConfig? {
        return allRenderers.find { it.id.equals(id, ignoreCase = true) }
    }

    fun getDefaultRenderer(): RendererConfig = GL4ES

    fun areRequiredLibsPresent(nativeLibDir: String, renderer: RendererConfig): Boolean {
        if (renderer.requiredLibs.isEmpty()) return true
        val dir = File(nativeLibDir)
        return renderer.requiredLibs.all { libName ->
            File(dir, libName).exists().also { exists ->
                Log.d(TAG, "Renderer ${renderer.id} lib check: $libName -> $exists")
            }
        }
    }

    fun areRequiredLibsPresent(context: Context, renderer: RendererConfig): Boolean {
        return areRequiredLibsPresent(context.applicationInfo.nativeLibraryDir, renderer)
    }

    fun resolveRendererWithFallback(context: Context, selectedId: String): RendererConfig {
        val selected = getRendererById(selectedId) ?: GL4ES
        return if (areRequiredLibsPresent(context, selected)) {
            selected
        } else {
            Log.w(TAG, "Renderer ${selected.displayName} library missing, fallback to GL4ES")
            GL4ES
        }
    }

    fun applyRendererEnvironment(
        baseEnv: MutableMap<String, String?>,
        renderer: RendererConfig,
        mesaGlslCacheDir: String? = null,
        vtestSocketName: String? = null
    ) {
        baseEnv["POJAV_RENDERER"] = renderer.pojavRenderer
        renderer.envVars.forEach { (key, value) ->
            when {
                key == "MESA_GLSL_CACHE_DIR" && value.isNullOrEmpty() && !mesaGlslCacheDir.isNullOrEmpty() -> {
                    baseEnv[key] = mesaGlslCacheDir
                }
                key == "VTEST_SOCKET_NAME" && value.isNullOrEmpty() && !vtestSocketName.isNullOrEmpty() -> {
                    baseEnv[key] = vtestSocketName
                }
                !value.isNullOrEmpty() -> {
                    baseEnv[key] = value
                }
            }
        }
    }

    // 旧版兼容方法：GameWindow 仍调用，保留至迁移完成
    @Deprecated("Renderer initialization is now handled by environment variables")
    fun initializeRenderer(surface: android.view.Surface, config: com.verse.explorer.launcher.renderer.RendererConfig) {
        Log.d(TAG, "Renderer ${config.type.displayName} initialized via environment variables")
    }

    @Deprecated("No-op for compatibility")
    fun cleanup() {
        Log.d(TAG, "Renderer cleanup complete")
    }

    @Deprecated("No-op for compatibility")
    fun isRendererAvailable(type: RendererType): Boolean {
        return true
    }

    @Deprecated("Use renderer.glName")
    fun getGlLibName(type: RendererType): String {
        return type.glLibName
    }
}

// 旧版兼容方法：MinecraftLauncher 当前代码仍调用，保留至迁移完成
@Deprecated("Use RendererManager.getRendererById / applyRendererEnvironment")
fun resolveRendererType(renderer: String): RendererType {
    return when (renderer.lowercase()) {
        "gl4es" -> RendererType.GL4ES
        "nggl4es" -> RendererType.NGGL4ES
        "zink" -> RendererType.ZINK
        "freedreno" -> RendererType.FREEDRENO
        "virgl" -> RendererType.VIRGL
        "vgpu" -> RendererType.VGPU
        else -> RendererType.GL4ES
    }
}

@Deprecated("Environment setup is now handled inside MinecraftLauncher.setupRendererEnvironment")
fun setupRendererEnvironment(
    config: com.verse.explorer.launcher.launch.LaunchConfiguration,
    rendererConfig: RendererConfig
): RendererConfig {
    // 实际渲染器环境变量设置已在 MinecraftLauncher 内部完成，此处仅保持兼容签名
    return rendererConfig
}
