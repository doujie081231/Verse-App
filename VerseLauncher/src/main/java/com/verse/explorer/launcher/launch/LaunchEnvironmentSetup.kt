package com.verse.explorer.launcher.launch

import android.content.Context
import android.os.Build
import android.util.Log
import com.verse.explorer.launcher.renderer.RendererManager
import com.verse.explorer.jvmlauncher.JvmLauncherAPI
import java.io.File

internal class LaunchEnvironmentSetup(private val context: Context) {

    companion object {
        private const val TAG = "MinecraftLauncher"
    }

    /** 根据 RendererManager 的 RendererConfig 设置渲染器环境变量 */
    fun setupRendererEnvironment(
        config: LaunchConfiguration,
        rendererConfig: RendererManager.RendererConfig
    ): Boolean {
        return try {
            val envMap = mutableMapOf<String, String?>()
            RendererManager.applyRendererEnvironment(
                envMap,
                rendererConfig,
                context.cacheDir.absolutePath,
                File(context.cacheDir, ".virgl_test").absolutePath
            )
            // SDL 需要独立的 GL/EGL 库绝对路径：
            // MC 26.x RenderPearl 校验 SDL_GL_GetProcAddress("glGetError") 与 LWJGL provider
            // 同源，SDL 侧必须解析自渲染器 GL 库（NGGL4ES=libng_gl4es.so）。
            val rendererLibPath = context.applicationInfo.nativeLibraryDir
            val egl = envMap["POJAVEXEC_EGL"]
            if (egl != null && !egl.startsWith("/")) {
                // 仅当 EGL 库真实打包在 native 目录内才给 SDL 绝对路径（如 ANGLE/Mesa）；
                // 系统 Mali libEGL.so 不在目录内，交 SDL 按自身默认解析
                val candidate = File(rendererLibPath, egl)
                if (candidate.isFile()) {
                    envMap["SDL_EGL_LIBRARY"] = candidate.absolutePath
                }
            }
            val gl = rendererConfig.glName
            if (gl.isNotEmpty()) {
                envMap["SDL_OPENGL_LIBRARY"] =
                    if (gl.startsWith("/")) gl else File(rendererLibPath, gl).absolutePath
            }
            envMap.forEach { (key, value) ->
                if (!value.isNullOrEmpty()) {
                    JvmLauncherAPI.nativeSetenv(key, value)
                }
            }
            Log.d(TAG, "Renderer environment configured for ${rendererConfig.id}: SDL_OPENGL_LIBRARY=${envMap["SDL_OPENGL_LIBRARY"]}, SDL_EGL_LIBRARY=${envMap["SDL_EGL_LIBRARY"]}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "setupRendererEnvironment failed: ${e.message}\n${e.stackTraceToString()}")
            false
        }
    }

    /** 公共环境变量 */
    fun setupCommonEnvironment(config: LaunchConfiguration): Boolean {
        return try {
            val nativeLibDir = config.nativesDir.absolutePath
            val runtimeDir = context.getDir("runtime", 0).absolutePath
            val cacheDir = context.cacheDir.absolutePath

            // 核心路径
            JvmLauncherAPI.nativeSetenv("HOME", config.gameDir.absolutePath)
            JvmLauncherAPI.nativeSetenv("JAVA_HOME", config.javaHome.absolutePath)
            JvmLauncherAPI.nativeSetenv("FCL_NATIVEDIR", nativeLibDir)
            JvmLauncherAPI.nativeSetenv("POJAV_NATIVEDIR", nativeLibDir)
            JvmLauncherAPI.nativeSetenv("TMPDIR", cacheDir)
            val currentPath = System.getenv("PATH") ?: ""
            JvmLauncherAPI.nativeSetenv("PATH", "${config.javaHome.absolutePath}/bin:$currentPath")

            // LD_LIBRARY_PATH - 原生库加载关键
            val modRuntimeDir = context.getDir("runtime_mod", 0).absolutePath
            val ldLibPath = buildFclLibraryPath(
                javaPath = config.javaHome.absolutePath,
                nativeLibDir = nativeLibDir,
                jnaDir = File(runtimeDir, "jna").absolutePath,
                isJava8 = config.javaVersion <= 8,
                nativesDir = config.nativesDir.absolutePath,
                modRuntimeDir = modRuntimeDir
            )
            JvmLauncherAPI.nativeSetenv("LD_LIBRARY_PATH", ldLibPath)

            // 其他
            JvmLauncherAPI.nativeSetenv("DRIVER_PATH", config.nativesDir.absolutePath)
            JvmLauncherAPI.nativeSetenv("MOD_ANDROID_RUNTIME", modRuntimeDir)
            JvmLauncherAPI.nativeSetenv("POJAV_BIG_CORE_AFFINITY", "1")
            JvmLauncherAPI.nativeSetenv("FORCE_VSYNC", "false")
            JvmLauncherAPI.nativeSetenv("VULKAN_DRIVER_SYSTEM", if (config.isVulkanDriverSystem) "1" else "0")

            Log.d(TAG, "Common environment configured: FCL_NATIVEDIR=$nativeLibDir, JAVA_HOME=${config.javaHome.absolutePath}, DRIVER_PATH=${config.nativesDir.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "setupCommonEnvironment failed: ${e.message}\n${e.stackTraceToString()}")
            false
        }
    }

    fun buildFclLibraryPath(
        javaPath: String,
        nativeLibDir: String,
        jnaDir: String,
        isJava8: Boolean,
        nativesDir: String = "",
        modRuntimeDir: String = ""
    ): String {
        val jvmLibDir = findJvmLibDir(javaPath, isJava8)
        val javaLibDir = findJavaLibDir(javaPath, isJava8)
        val jliLibDir = if (File("$javaPath$javaLibDir/jli/libjli.so").exists()) "$javaLibDir/jli" else javaLibDir

        val parts = mutableListOf<String>()
        parts.add("$javaPath$javaLibDir")
        parts.add("$javaPath$jliLibDir")
        if (isJava8) {
            parts.add("$javaPath$javaLibDir$jvmLibDir")
            parts.add("$javaPath/jre$javaLibDir$jvmLibDir")
            parts.add("$javaPath/jre$javaLibDir")
        } else {
            parts.add("$javaPath$javaLibDir$jvmLibDir")
        }
        val is64Bit = android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        val libDir = if (is64Bit) "lib64" else "lib"
        parts.add("/system/$libDir")
        parts.add("/vendor/$libDir")
        parts.add("/vendor/$libDir/hw")
        parts.add("/system_ext/$libDir")
        parts.add(jnaDir)
        parts.add(nativeLibDir)
        if (nativesDir.isNotEmpty()) parts.add(nativesDir)
        if (modRuntimeDir.isNotEmpty()) parts.add(modRuntimeDir)

        return parts.joinToString(":")
    }

    private fun findJavaLibDir(javaPath: String, isJava8: Boolean): String {
        val baseLib = if (isJava8) "$javaPath/jre/lib" else "$javaPath/lib"
        val dir = File(baseLib)
        if (dir.exists()) {
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory && (f.name == "aarch64" || f.name == "arm" || f.name == "amd64" || f.name == "i386")) {
                    return if (isJava8) "/jre/lib/${f.name}" else "/lib/${f.name}"
                }
            }
        }
        return if (isJava8) "/jre/lib" else "/lib"
    }

    private fun findJvmLibDir(javaPath: String, isJava8: Boolean): String {
        val javaLibDir = findJavaLibDir(javaPath, isJava8)
        val basePath = "$javaPath$javaLibDir"
        return if (File("$basePath/server/libjvm.so").exists()) "/server" else "/client"
    }

    fun getSocName(): String {
        return try {
            Build.HARDWARE ?: Build.BOARD ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    /** CacioCavallo bootclasspath 与 AWT 配置参数 */
    fun addCacioArgs(args: MutableList<String>, config: LaunchConfiguration, isJava8: Boolean, nativeLibDir: String) {
        val runtimeDir = context.getDir("runtime", 0)

        args.add("-Djava.awt.headless=false")
        args.add("-Dcacio.managed.screensize=${config.width}x${config.height}")
        args.add("-Dcacio.font.fontmanager=sun.awt.X11FontManager")
        args.add("-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler")
        if (isJava8) {
            args.add("-Dawt.toolkit=net.java.openjdk.cacio.ctc.CTCToolkit")
            args.add("-Djava.awt.graphicsenv=net.java.openjdk.cacio.ctc.CTCGraphicsEnvironment")
            val cacio8Dir = File(runtimeDir, "caciocavallo")
            val bootJars = listOf(
                File(cacio8Dir, "cacio-androidnw-1.10-SNAPSHOT.jar"),
                File(cacio8Dir, "cacio-shared-1.10-SNAPSHOT.jar"),
                File(cacio8Dir, "ResConfHack.jar")
            ).filter { it.exists() && it.length() > 1024 }
            if (bootJars.isNotEmpty()) {
                args.add("-Xbootclasspath/p:${bootJars.joinToString(":") { it.absolutePath }}")
            }
        } else {
            args.add("-Dawt.toolkit=com.github.caciocavallosilano.cacio.ctc.CTCToolkit")
            args.add("-Djava.awt.graphicsenv=com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment")
            val cacio17Dir = File(runtimeDir, "caciocavallo17")
            val cacioAgent = File(cacio17Dir, "cacio-agent.jar")
            if (cacioAgent.exists() && cacioAgent.length() > 1024) {
                args.add("-javaagent:${cacioAgent.absolutePath}")
            }
            val bootJars = listOf(
                File(cacio17Dir, "cacio-agent.jar"),
                File(cacio17Dir, "cacio-shared-1.19.1-SNAPSHOT.jar"),
                File(cacio17Dir, "cacio-tta-1.19.1-SNAPSHOT.jar")
            ).filter { it.exists() && it.length() > 1024 }
            if (bootJars.isNotEmpty()) {
                args.add("-Xbootclasspath/a:${bootJars.joinToString(":") { it.absolutePath }}")
            }
        }
    }
}
