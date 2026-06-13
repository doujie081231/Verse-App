package com.verse.explorer.launcher.launch

import android.content.Context
import android.os.Build
import android.util.Log
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.game.GameProfile
import com.verse.explorer.launcher.bridge.ZLBridge
import com.verse.explorer.terracotta.TerracottaAndroidAPI
import java.io.File
import java.io.FileOutputStream
import java.util.TimeZone
import java.util.Locale

data class LaunchConfiguration(
    val version: MinecraftVersion,
    val profile: GameProfile,
    val javaHome: File,
    val gameDir: File,
    val assetsDir: File,
    val librariesDir: File,
    val nativesDir: File,
    val width: Int = 854,
    val height: Int = 480,
    val maxMemory: Int = 2048,
    val minMemory: Int = 512,
    val fullscreen: Boolean = false,
    val demo: Boolean = false,
    val serverAddress: String? = null,
    val serverPort: Int? = null,
    val gcType: String = "G1GC",
    val customJvmArgs: String = "",
    val renderer: String = "zink",
    val javaVersion: Int = 17,
    val beGesture: Boolean = true,
    val debugLog: Boolean = false,
    val isVulkanDriverSystem: Boolean = false,
    val gameArguments: String = "",
    val javaArguments: List<String> = emptyList(),
    val accessToken: String = "0",
    val userType: String = "mojang"
)

data class LaunchProcess(
    val pid: Int,
    val process: Process? = null
)

class MinecraftLauncher(private val context: Context, private val zlBridge: ZLBridge? = null) {

    companion object {
        private const val TAG = "MinecraftLauncher"
    }

    private var currentProcess: LaunchProcess? = null
    private var launchCallback: LaunchCallback? = null

    fun setLaunchCallback(callback: LaunchCallback) {
        this.launchCallback = callback
    }

    fun launch(config: LaunchConfiguration, launchMethod: String = "pojav"): LaunchProcess? {
        if (currentProcess != null && isRunning()) {
            Log.w(TAG, "Game already running")
            return null
        }

        try {
            val t0 = System.currentTimeMillis()
            
            // V3.7: Set debug log path to app's private directory (no permissions needed)
            // This allows viewing logs directly in-app without adb
            val logPath = File(context.filesDir, "verse_debug.log").absolutePath
            TerracottaAndroidAPI.nativeSetLogPath(logPath)
            Log.i(TAG, "Debug log: $logPath")
            Log.i(TAG, "[launch] nativeSetLogPath done (${System.currentTimeMillis()-t0}ms)")

            // FCL-style: 在 launchJVM 之前设置渲染器相关的 env vars
            Log.i(TAG, "[launch] setupCommonEnvironment...")
            setupCommonEnvironment(config)
            Log.i(TAG, "[launch] setupCommonEnvironment done (${System.currentTimeMillis()-t0}ms)")
            
            Log.i(TAG, "[launch] setupRendererEnvironment...")
            setupRendererEnvironment(config)
            Log.i(TAG, "[launch] setupRendererEnvironment done (${System.currentTimeMillis()-t0}ms)")

            Log.i(TAG, "[launch] buildFullArgs...")
            val args = buildFullArgs(config)
            Log.i(TAG, "[launch] buildFullArgs done: ${args.size} args (${System.currentTimeMillis()-t0}ms)")

            Log.d(TAG, "=== Launching Minecraft (FCL-style) ===")
            Log.d(TAG, "Method: $launchMethod")
            Log.d(TAG, "Java: ${config.javaHome.absolutePath}")
            Log.d(TAG, "Version: ${config.version.id}")
            Log.d(TAG, "Game dir: ${config.gameDir.absolutePath}")
            Log.d(TAG, "Natives: ${config.nativesDir.absolutePath}")
            Log.d(TAG, "Renderer: ${config.renderer}")
            Log.d(TAG, "Total args: ${args.size}")
            args.forEachIndexed { i, arg -> Log.d(TAG, "  argv[$i] = $arg") }

            Log.i(TAG, "[launch] Calling launchJVM ($launchMethod)...")
            val nativeResult = when {
                launchMethod == "boat" -> {
                    Log.d(TAG, "Using Boat (JVMLoader) launch method")
                    val jvmLoader = JVMLoader(context)
                    jvmLoader.setExitCallback(object : JVMLoaderCallback {
                        override fun onExit(code: Int) {
                            currentProcess = null
                            launchCallback?.onGameExit(code)
                        }
                    })
                    jvmLoader.launchJVM(
                        jrePath = config.javaHome.absolutePath,
                        args = args.toTypedArray()
                    )
                }
                zlBridge != null -> {
                    Log.d(TAG, "Using Pojav (ZLBridge) launch method")
                    Log.i(TAG, "[launch] Calling zlBridge.launchJVM...")
                    val r = zlBridge.launchJVM(
                        jrePath = config.javaHome.absolutePath,
                        args = args.toTypedArray()
                    )
                    Log.i(TAG, "[launch] zlBridge.launchJVM returned: $r (${System.currentTimeMillis()-t0}ms)")
                    r
                }
                else -> {
                    Log.d(TAG, "Using fallback JVMLoader launch method")
                    val jvmLoader = JVMLoader(context)
                    jvmLoader.setExitCallback(object : JVMLoaderCallback {
                        override fun onExit(code: Int) {
                            currentProcess = null
                            launchCallback?.onGameExit(code)
                        }
                    })
                    jvmLoader.launchJVM(
                        jrePath = config.javaHome.absolutePath,
                        args = args.toTypedArray()
                    )
                }
            }

            if (nativeResult == 0) {
                Log.i(TAG, "JVM launched successfully (startup confirmed by native layer)")
                val pid = zlBridge?.getPid() ?: -1
                currentProcess = LaunchProcess(pid)
                return currentProcess
            }

            val errorMsg = when (nativeResult) {
                -1 -> "JVM启动参数错误或JRE路径不存在"
                -2 -> "JVM启动超时: JRE环境初始化失败 (检查Java安装和原生库)"
                -10 -> "无法加载libjli.so (JRE可能不完整)"
                -11 -> "无法找到JLI_Launch函数 (JRE版本不兼容)"
                else -> "JVM启动失败, 错误码: $nativeResult"
            }
            Log.e(TAG, errorMsg)
            return null

        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "原生库缺失 (UnsatisfiedLinkError): ${e.message}", e)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Minecraft启动异常: ${e.message}", e)
            return null
        }
    }

    // ==================== FCL-style 渲染器环境变量 ====================

    /** 照搬 FCLauncher.addRendererEnv() 和 addCommonEnv() */
    private fun setupRendererEnvironment(config: LaunchConfiguration) {
        try {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir

            // FCL-style: POJAV_RENDERER — 告诉游戏使用哪个渲染器
            val pojavRenderer = when (config.renderer) {
            "gl4es" -> "opengles2"
            "vgpu" -> "opengles2_vgpu"
            "nggl4es" -> "opengles3"
            "virgl" -> "gallium_virgl"
            "zink" -> "vulkan_zink"
            "freedreno" -> "gallium_freedreno"
            else -> "opengles2"
        }
        TerracottaAndroidAPI.nativeSetenv("POJAV_RENDERER", pojavRenderer)
        Log.d(TAG, "POJAV_RENDERER=$pojavRenderer")

        // FCL-style: LIBGL_NAME and LIBEGL_NAME — critical for gl4es to find renderer libs
        // FCL sets these in addRendererEnv() alongside LIBGL_* vars
        when (config.renderer) {
            "gl4es", "vgpu" -> {
                TerracottaAndroidAPI.nativeSetenv("LIBGL_NAME", "libgl4es.so")
                TerracottaAndroidAPI.nativeSetenv("LIBEGL_NAME", "libEGL.so")
                TerracottaAndroidAPI.nativeSetenv("LIBGL_ES", "2")
                TerracottaAndroidAPI.nativeSetenv("LIBGL_MIPMAP", "3")
                TerracottaAndroidAPI.nativeSetenv("LIBGL_NORMALIZE", "1")
                TerracottaAndroidAPI.nativeSetenv("LIBGL_NOINTOVLHACK", "1")
                TerracottaAndroidAPI.nativeSetenv("LIBGL_NOERROR", "1")
            }
            "nggl4es" -> {
                TerracottaAndroidAPI.nativeSetenv("LIBGL_NAME", "libng_gl4es.so")
                TerracottaAndroidAPI.nativeSetenv("LIBEGL_NAME", "libEGL.so")
                TerracottaAndroidAPI.nativeSetenv("POJAVEXEC_EGL", "libEGL.so")
                TerracottaAndroidAPI.nativeSetenv("LIBGL_USE_MC_COLOR", "1")
                TerracottaAndroidAPI.nativeSetenv("LIBGL_GL", "31")
                TerracottaAndroidAPI.nativeSetenv("LIBGL_ES", "3")
                TerracottaAndroidAPI.nativeSetenv("LIBGL_NORMALIZE", "1")
                TerracottaAndroidAPI.nativeSetenv("LIBGL_NOINTOVLHACK", "1")
                TerracottaAndroidAPI.nativeSetenv("LIBGL_NOERROR", "1")
                TerracottaAndroidAPI.nativeSetenv("DLOPEN", "libspirv-cross-c-shared.so")
            }
            "virgl", "zink", "freedreno" -> {
                TerracottaAndroidAPI.nativeSetenv("MESA_GLSL_CACHE_DIR", context.cacheDir.absolutePath)
                val glVersion = if (config.renderer == "virgl") "4.3" else "4.6"
                val glslVersion = if (config.renderer == "virgl") "430" else "460"
                TerracottaAndroidAPI.nativeSetenv("MESA_GL_VERSION_OVERRIDE", glVersion)
                TerracottaAndroidAPI.nativeSetenv("MESA_GLSL_VERSION_OVERRIDE", glslVersion)
                TerracottaAndroidAPI.nativeSetenv("force_glsl_extensions_warn", "true")
                TerracottaAndroidAPI.nativeSetenv("allow_higher_compat_version", "true")
                TerracottaAndroidAPI.nativeSetenv("allow_glsl_extension_directive_midshader", "true")
                val driverOverride = when (config.renderer) {
                    "virgl" -> "virpipe"
                    "freedreno" -> "freedreno"
                    else -> "zink"
                }
                TerracottaAndroidAPI.nativeSetenv("MESA_LOADER_DRIVER_OVERRIDE", driverOverride)
                TerracottaAndroidAPI.nativeSetenv("VTEST_SOCKET_NAME", File(context.cacheDir, ".virgl_test").absolutePath)
                if (config.renderer == "virgl") {
                    TerracottaAndroidAPI.nativeSetenv("OSMESA_NO_FLUSH_FRONTBUFFER", "1")
                }
                if (config.renderer == "zink") {
                    TerracottaAndroidAPI.nativeSetenv("MESA_NO_ERROR", "1")
                    TerracottaAndroidAPI.nativeSetenv("ZINK_DESCRIPTORS", "lazy")
                }
            }
            else -> {
                TerracottaAndroidAPI.nativeSetenv("LIBGL_NAME", "libgl4es.so")
                TerracottaAndroidAPI.nativeSetenv("LIBEGL_NAME", "libEGL.so")
                TerracottaAndroidAPI.nativeSetenv("LIBGL_ES", "2")
                TerracottaAndroidAPI.nativeSetenv("LIBGL_NORMALIZE", "1")
                TerracottaAndroidAPI.nativeSetenv("LIBGL_NOINTOVLHACK", "1")
                TerracottaAndroidAPI.nativeSetenv("LIBGL_NOERROR", "1")
            }
        }

        // FCL-style: MESA / VIRGL 相关
        if (config.renderer == "virgl" || config.renderer == "zink") {
            TerracottaAndroidAPI.nativeSetenv("GALLIUM_VIRGL", "1")
        }

        // FCL-style: native library path 在 setupEnvironment 中处理
        Log.d(TAG, "Renderer environment configured for ${config.renderer}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup renderer environment: ${e.message}", e)
        }
    }

    /** FCL addCommonEnv() - 公共环境变量 */
    private fun setupCommonEnvironment(config: LaunchConfiguration) {
        try {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val runtimeDir = context.getDir("runtime", 0).absolutePath
            val cacheDir = context.cacheDir.absolutePath

            // FCL-style: core paths
            TerracottaAndroidAPI.nativeSetenv("HOME", config.gameDir.absolutePath)
            TerracottaAndroidAPI.nativeSetenv("JAVA_HOME", config.javaHome.absolutePath)
            TerracottaAndroidAPI.nativeSetenv("FCL_NATIVEDIR", nativeLibDir)
            TerracottaAndroidAPI.nativeSetenv("POJAV_NATIVEDIR", nativeLibDir)
            TerracottaAndroidAPI.nativeSetenv("TMPDIR", cacheDir)
            val currentPath = System.getenv("PATH") ?: ""
            TerracottaAndroidAPI.nativeSetenv("PATH", "${config.javaHome.absolutePath}/bin:$currentPath")

            // FCL-style: LD_LIBRARY_PATH - critical for native library loading
            val ldLibPath = buildFclLibraryPath(
                javaPath = config.javaHome.absolutePath,
                nativeLibDir = nativeLibDir,
                jnaDir = File(runtimeDir, "jna").absolutePath,
                isJava8 = config.javaVersion <= 8,
                nativesDir = config.nativesDir.absolutePath,
                modRuntimeDir = context.getDir("runtime_mod", 0).absolutePath
            )
            TerracottaAndroidAPI.nativeSetenv("LD_LIBRARY_PATH", ldLibPath)

            // FCL-style: misc
            TerracottaAndroidAPI.nativeSetenv("FORCE_VSYNC", "false")
            TerracottaAndroidAPI.nativeSetenv("DRIVER_PATH", "")

            Log.d(TAG, "Common environment configured: FCL_NATIVEDIR=$nativeLibDir, JAVA_HOME=${config.javaHome.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup common environment: ${e.message}", e)
        }
    }

    // ==================== FCL DefaultLauncher.generateCommandLine() ====================

    private fun buildFullArgs(config: LaunchConfiguration): List<String> {
        val args = mutableListOf<String>()

        val isJava8 = config.javaVersion <= 8
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val runtimeDir = context.getDir("runtime", 0)
        val jnaDir = File(runtimeDir, "jna")
        val cacheDir = context.cacheDir.absolutePath + "/Verse"
        File(cacheDir).mkdirs()

        // FCL-style: Cacio Java args
        addCacioArgs(args, config, isJava8, nativeLibDir)

        // FCL-style: 自定义 JVM args
        if (config.customJvmArgs.isNotBlank()) {
            args.addAll(config.customJvmArgs.split("\\s+".toRegex()).filter { it.isNotBlank() })
        }

        // FCL-style: 额外的 Java arguments (从版本配置传入)
        config.javaArguments.forEach { arg ->
            if (arg.isNotBlank()) args.add(arg)
        }

        // FCL-style: memory settings
        if (config.maxMemory > 0) args.add("-Xmx${config.maxMemory}m")
        if (config.minMemory > 0 && config.minMemory <= config.maxMemory) args.add("-Xms${config.minMemory}m")

        // FCL-style: GC settings
        val gcArg = when (config.gcType.lowercase()) {
            "g1gc", "g1" -> "-XX:+UseG1GC"
            "zgc", "z" -> "-XX:+UseZGC"
            "shenandoah", "shen" -> "-XX:+UseShenandoahGC"
            "epsilon" -> "-XX:+UseEpsilonGC"
            "serial" -> "-XX:+UseSerialGC"
            "parallel" -> "-XX:+UseParallelGC"
            "cms" -> "-XX:+UseConcMarkSweepGC"
            else -> ""
        }
        if (gcArg.isNotEmpty()) args.add(gcArg)

        // FCL-style: gl4es 会触发 System.gc()，禁用以避免卡顿
        if (config.renderer == "gl4es" || config.renderer == "vgpu") {
            args.add("-XX:+DisableExplicitGC")
        }

        // FCL-style: java.home
        args.add("-Djava.home=${config.javaHome.absolutePath}")

        // FCL-style: encoding
        args.add("-Dfile.encoding=UTF-8")
        if (config.javaVersion < 19) {
            args.add("-Dsun.stdout.encoding=UTF-8")
            args.add("-Dsun.stderr.encoding=UTF-8")
        } else {
            args.add("-Dstdout.encoding=UTF-8")
            args.add("-Dstderr.encoding=UTF-8")
        }

        // FCL-style: Log4j RCE mitigation
        args.add("-Djava.rmi.server.useCodebaseOnly=true")
        args.add("-Dcom.sun.jndi.rmi.object.trustURLCodebase=false")
        args.add("-Dcom.sun.jndi.cosnaming.object.trustURLCodebase=false")
        args.add("-Dlog4j2.formatMsgNoLookups=true")

        // FCL-style: thread stack for 32-bit
        if (Build.SUPPORTED_32_BIT_ABIS.isNotEmpty() && Build.SUPPORTED_64_BIT_ABIS.isEmpty()) {
            args.add("-Xss1m")
        }

        // FCL-style: core flags
        args.add("-Dminecraft.client.jar=${getVersionJar(config)}")
        args.add("-XX:ActiveProcessorCount=${Runtime.getRuntime().availableProcessors()}")
        args.add("-Dfml.ignoreInvalidMinecraftCertificates=true")
        args.add("-Dfml.ignorePatchDiscrepancies=true")
        args.add("-Dfml.earlyprogresswindow=false")

        // FCL-style: FCL specific system properties
        val javaPath = File(runtimeDir, "java")
        args.add("-Dext.net.resolvPath=${javaPath.absolutePath}/resolv.conf")
        args.add("-Djava.io.tmpdir=$cacheDir")

        // FCL-style: glfwstub window size (FCL LWJGL reads these)
        args.add("-Dglfwstub.windowWidth=${config.width}")
        args.add("-Dglfwstub.windowHeight=${config.height}")

        // oshi: disable problematic Linux-specific checks on Android
        args.add("-Doshi.os.prefix=Android")
        args.add("-Doshi.arch=${Build.SUPPORTED_64_BIT_ABIS.firstOrNull() ?: "aarch64"}")

        // FCL-style: OS identity
        args.add("-Dos.name=Linux")
        args.add("-Dos.version=Android-${Build.VERSION.RELEASE}")

        // FCL-style: LWJGL library paths
        args.add("-Dorg.lwjgl.system.stackSize=3072")
        val glLibName = getGlLibName(config.renderer, nativeLibDir)
        args.add("-Dorg.lwjgl.system.libname=$nativeLibDir/libpojavexec.so")
        args.add("-Dorg.lwjgl.glfw.libname=$nativeLibDir/libpojavexec.so")
        args.add("-Dorg.lwjgl.opengl.libname=$glLibName")
        args.add("-Dorg.lwjgl.freetype.libname=$nativeLibDir/libfreetype.so")
        args.add("-Dorg.lwjgl.vulkan.libname=libvulkan.so")
        args.add("-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared")

        // FCL-style: Library path
        val modRuntimeDir = context.getDir("runtime_mod", 0).absolutePath

        val libraryPath = buildFclLibraryPath(
            javaPath = config.javaHome.absolutePath,
            nativeLibDir = nativeLibDir,
            jnaDir = jnaDir.absolutePath,
            isJava8 = isJava8,
            nativesDir = config.nativesDir.absolutePath,
            modRuntimeDir = modRuntimeDir
        )
        args.add("-Djava.library.path=$libraryPath")
        args.add("-Dorg.lwjgl.system.SharedLibraryExtractPath=$cacheDir")
        args.add("-Dorg.lwjgl.librarypath=$libraryPath")

        // FCL-style: misc
        args.add("-Dloader.disable_forked_guis=true")
        args.add("-Duser.home=${config.gameDir.absolutePath}")
        args.add("-Duser.language=${System.getProperty("user.language", "zh")}")
        args.add("-Duser.country=${Locale.getDefault().country}")
        args.add("-Duser.timezone=${TimeZone.getDefault().id}")
        args.add("-Dsodium.checks.issue2561=false")
        args.add("-Dcpu.name=${getSocName()}")

        // FCL-style: JNA boot path
        args.add("-Djna.boot.library.path=${jnaDir.absolutePath}")
        args.add("-Djna.nosys=true")
        args.add("-Djna.nounpack=true")
        args.add("-Djna.tmpdir=$cacheDir")

        // FCL-style: Fix 1.7.2 Forge
        val gameVersion = resolveGameVersion(config)
        if (gameVersion == "1.7.2") {
            args.add("-Dsort.patch=true")
        }

        // V3.6: MioLibPatcher + MioLaunchWrapper (FCL-style complete Mio infrastructure)
        val pluginsDir = File(context.filesDir, "plugins")
        if (!pluginsDir.exists()) pluginsDir.mkdirs()

        val mioLibPatcherJar = deployGameAsset(context, "MioLibPatcher.jar", pluginsDir)
        if (mioLibPatcherJar != null) {
            args.add("-javaagent:${mioLibPatcherJar.absolutePath}")
            Log.d(TAG, "MioLibPatcher javaagent: ${mioLibPatcherJar.absolutePath}")
        } else {
            Log.w(TAG, "MioLibPatcher.jar not found, continuing without javaagent transform")
        }

        val mioLaunchWrapperJar = deployGameAsset(context, "MioLaunchWrapper.jar", pluginsDir)
        if (mioLaunchWrapperJar != null) {
            Log.d(TAG, "MioLaunchWrapper deployed: ${mioLaunchWrapperJar.absolutePath}")
        } else {
            Log.w(TAG, "MioLaunchWrapper.jar not found")
        }

        val bootstrapJar = deployGameAsset(context, "verse-bootstrap.jar", pluginsDir)
            ?: deployGameAsset(context, "game/verse-bootstrap.jar", pluginsDir)
        if (bootstrapJar != null) {
            Log.d(TAG, "verse-bootstrap.jar deployed: ${bootstrapJar.absolutePath}")
        } else {
            Log.w(TAG, "verse-bootstrap.jar not found, VerseBootstrap main class will not be available")
        }

        // FCL-style: mod loader env markers (空实现)
        addModLoaderMarkers(config, args)

        // FCL-style: add-opens/add-exports for Java 17+
        if (!isJava8) {
            addJavaModuleArgs(args, config)
        }

        // FCL-style: beGesture injector
        if (config.beGesture) {
            val injectorArg = getInjectorArg(config)
            if (injectorArg != null) {
                args.add("-Dfcl.injector=$injectorArg")
            }
        }

        // FCL-style: build classpath — 关键！LWJGL jar 排在最前面
        val classpath = buildFclClasspath(config, runtimeDir)
        args.add("-cp")
        args.add(classpath)

        // V3.6: FCL-style — VerseBootstrap as main class, real main class as first arg
        val mcMainClass = if (config.version.mainClass.isNotEmpty()) config.version.mainClass
            else "net.minecraft.client.main.Main"
        val bootstrapAvailable = bootstrapJar != null && bootstrapJar.exists() && bootstrapJar.length() > 1024
        if (bootstrapAvailable) {
            args.add("com.verse.explorer.launcher.bootstrap.VerseBootstrap")
            args.add(mcMainClass)
        } else {
            Log.w(TAG, "VerseBootstrap not available, using main class directly: $mcMainClass")
            args.add(mcMainClass)
        }

        // FCL-style: game arguments
        args.addAll(buildFclGameArguments(config, gameVersion))

        // FCL-style: 如果有自定义游戏参数，追加
        if (config.gameArguments.isNotBlank()) {
            args.addAll(config.gameArguments.split("\\s+".toRegex()).filter { it.isNotBlank() })
        }

        return args
    }

    // ==================== FCL 参数构建辅助方法 ====================

    private fun addCacioArgs(args: MutableList<String>, config: LaunchConfiguration, isJava8: Boolean, nativeLibDir: String) {
        args.add("-Djava.awt.headless=false")
        args.add("-Dcacio.managed.screensize=${config.width}x${config.height}")
        args.add("-Dcacio.font.fontmanager=sun.awt.X11FontManager")
        args.add("-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler")
        args.add("-Dswing.defaultlaf=javax.swing.plaf.nimbus.NimbusLookAndFeel")
        if (isJava8) {
            args.add("-Dawt.toolkit=net.java.openjdk.cacio.ctc.CTCToolkit")
            args.add("-Djava.awt.graphicsenv=net.java.openjdk.cacio.ctc.CTCGraphicsEnvironment")
        } else {
            args.add("-Dawt.toolkit=com.github.caciocavallosilano.cacio.ctc.CTCToolkit")
            args.add("-Djava.awt.graphicsenv=com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment")
            val runtimeDir = context.getDir("runtime", 0)
            val cacioAgent = File(runtimeDir, "caciocavallo17/cacio-agent.jar")
            if (cacioAgent.exists() && cacioAgent.length() > 1024) {
                args.add("-javaagent:${cacioAgent.absolutePath}")
            }
        }
    }

    private fun buildFclLibraryPath(
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

    private fun getGlLibName(renderer: String, nativeLibDir: String): String {
        // FCL-style: GL library name for -Dorg.lwjgl.opengl.libname
        // Must match LIBGL_NAME env var, matches FCL's GLHelper.getGLName()
        return when (renderer) {
            "gl4es" -> "libgl4es.so"
            "vgpu" -> "libvgpu.so"
            "nggl4es" -> "libng_gl4es.so"
            "virgl" -> "libpojavexec.so"
            "zink" -> "libpojavexec.so"
            "freedreno" -> "libpojavexec.so"
            "mg" -> "libmg.so"
            else -> "libgl4es.so"
        }
    }

    private fun getSocName(): String {
        return try {
            Build.HARDWARE ?: Build.BOARD ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun addModLoaderMarkers(config: LaunchConfiguration, args: MutableList<String>) {
        try {
            val versionDir = File(config.gameDir, "versions/${config.version.id}")
            val versionJson = File(versionDir, "${config.version.id}.json")
            if (!versionJson.exists()) return

            val json = org.json.JSONObject(versionJson.readText())
            var mainClass = json.optString("mainClass", "")

            if (mainClass.isEmpty() && json.has("inheritsFrom")) {
                val parentId = json.getString("inheritsFrom")
                val parentDir = File(config.gameDir, "versions/$parentId")
                val parentJson = File(parentDir, "$parentId.json")
                if (parentJson.exists()) {
                    val parentObj = org.json.JSONObject(parentJson.readText())
                    mainClass = parentObj.optString("mainClass", "")
                }
            }

            if (mainClass.isEmpty()) return

            when (mainClass) {
                "net.minecraft.launchwrapper.Launch",
                "cpw.mods.fml.relauncher.FMLServerLaunchWrapper",
                "net.minecraftforge.fml.relauncher.ServerLaunchWrapper",
                "optifine.OptiFineForgeTweaker",
                "net.neoforged.fml.relauncher.ServerLaunchWrapper" -> {
                    args.add("-Dfml.ignoreInvalidMinecraftCertificates=true")
                    args.add("-Dfml.ignorePatchDiscrepancies=true")
                }
                "net.fabricmc.loader.impl.launch.knot.KnotClient" -> {
                    args.add("-Dfabric.dli.main=$mainClass")
                }
                "org.quiltmc.loader.impl.launch.knot.KnotClient" -> {
                    args.add("-Dquilt.dli.main=$mainClass")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect mod loader: ${e.message}")
        }
    }

    private fun addJavaModuleArgs(args: MutableList<String>, config: LaunchConfiguration) {
        if (config.javaVersion in 17..23) {
            args.add("-Djava.security.manager=allow")
        }
        args.add("--add-exports=java.desktop/java.awt=ALL-UNNAMED")
        args.add("--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED")
        args.add("--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED")
        args.add("--add-exports=java.desktop/sun.java2d=ALL-UNNAMED")
        args.add("--add-exports=java.desktop/java.awt.dnd.peer=ALL-UNNAMED")
        args.add("--add-exports=java.desktop/sun.awt=ALL-UNNAMED")
        args.add("--add-exports=java.desktop/sun.awt.event=ALL-UNNAMED")
        args.add("--add-exports=java.desktop/sun.awt.datatransfer=ALL-UNNAMED")
        args.add("--add-exports=java.desktop/sun.font=ALL-UNNAMED")
        if (config.javaVersion <= 23) {
            args.add("--add-exports=java.base/sun.security.action=ALL-UNNAMED")
        }
        args.add("--add-opens=java.base/java.util=ALL-UNNAMED")
        args.add("--add-opens=java.desktop/java.awt=ALL-UNNAMED")
        args.add("--add-opens=java.desktop/sun.font=ALL-UNNAMED")
        args.add("--add-opens=java.desktop/sun.java2d=ALL-UNNAMED")
        args.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED")
        args.add("--add-opens=java.base/java.net=ALL-UNNAMED")
        if (config.javaVersion <= 23) {
            args.add("--add-opens=java.base/sun.security.action=ALL-UNNAMED")
        }
        args.add("--add-opens=java.base/java.lang=ALL-UNNAMED")
        args.add("--add-opens=java.base/java.nio=ALL-UNNAMED")
        args.add("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
        args.add("--add-opens=java.base/java.io=ALL-UNNAMED")
        args.add("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED")
        if (config.javaVersion >= 19) {
            args.add("--enable-native-access=ALL-UNNAMED")
        }
    }

    private fun getInjectorArg(config: LaunchConfiguration): String? {
        val pluginDir = File(context.filesDir, "plugins")
        val injectorJar = File(pluginDir, "authlib-injector.jar")
        return if (injectorJar.exists()) injectorJar.absolutePath else null
    }

    private fun resolveGameVersion(config: LaunchConfiguration): String? {
        return config.version.id
    }

    // ==================== FCL-style 类路径构建 ====================

    private fun buildFclClasspath(config: LaunchConfiguration, runtimeDir: File): String {
        val classpath = mutableListOf<String>()

        // FCL-style: LWJGL jar 排在最前面！
        val lwjglDir = File(runtimeDir, "lwjgl")
        val lwjglJar = File(lwjglDir, "lwjgl.jar")
        if (lwjglJar.exists() && lwjglJar.length() > 1024) {
            classpath.add(lwjglJar.absolutePath)
        } else {
            val lwjgl3Jar = File(runtimeDir, "lwjgl3/lwjgl.jar")
            if (lwjgl3Jar.exists() && lwjgl3Jar.length() > 1024) {
                classpath.add(lwjgl3Jar.absolutePath)
            } else {
                Log.w(TAG, "LWJGL override JAR not found at ${lwjglJar.absolutePath} or ${lwjgl3Jar.absolutePath}")
            }
        }

        // 版本 JAR
        val versionJar = findVersionJar(config)
        if (versionJar != null) {
            classpath.add(versionJar.absolutePath)
        }

        // 图书馆 JAR — 按顺序添加（过滤 LWJGL native JARs，保留 API JARs）
        config.version.libraries.forEach { library ->
            if (library.name.startsWith("org.lwjgl:lwjgl") &&
                library.name.contains("-natives-")) return@forEach
            library.downloads?.artifact?.let { artifact ->
                val libFile = File(config.librariesDir, artifact.path)
                if (libFile.exists() && libFile.length() > 0) {
                    classpath.add(libFile.absolutePath)
                }
            }
        }

        // FCL-style: JNA jar from runtime/jna
        val jnaJar = File(runtimeDir, "jna/jna.jar")
        if (jnaJar.exists() && jnaJar.length() > 1024) {
            classpath.add(jnaJar.absolutePath)
        } else {
            val jnaLibDir = File(config.librariesDir, "net/java/dev/jna/jna")
            if (jnaLibDir.exists()) {
                jnaLibDir.walkTopDown().forEach { file ->
                    if (file.name.endsWith(".jar") && file.length() > 1024) {
                        if (file.absolutePath !in classpath) {
                            classpath.add(file.absolutePath)
                        }
                    }
                }
            }
        }

        // FCL-style: CacioCavallo JAR from runtime
        val isJava8 = config.javaVersion <= 8
        val cacioDir = File(runtimeDir, if (isJava8) "caciocavallo" else "caciocavallo17")
        if (cacioDir.exists()) {
            cacioDir.listFiles()?.filter { it.name.endsWith(".jar") && it.length() > 1024 }?.forEach { jar ->
                if (jar.name != "cacio-agent.jar" && jar.absolutePath !in classpath) {
                    classpath.add(jar.absolutePath)
                }
            }
        }

        // V3.6: MioLaunchWrapper.jar added to classpath for mio.Wrapper main class
        val mioLaunchWrapperJar = File(context.filesDir, "plugins/MioLaunchWrapper.jar")
        if (mioLaunchWrapperJar.exists() && mioLaunchWrapperJar.length() > 1024) {
            classpath.add(mioLaunchWrapperJar.absolutePath)
        }

        val pluginsDir = File(context.filesDir, "plugins")
        val bootstrapJar = File(pluginsDir, "verse-bootstrap.jar")
        if (bootstrapJar.exists() && bootstrapJar.length() > 1024) {
            classpath.add(bootstrapJar.absolutePath)
        }

        val cp = classpath.joinToString(File.pathSeparator)
        Log.d(TAG, "Classpath: $cp")
        return cp
    }

    private fun findVersionJar(config: LaunchConfiguration): File? {
        val versionDir = File(config.gameDir, "versions/${config.version.id}")
        val exactJar = File(versionDir, "${config.version.id}.jar")
        if (exactJar.exists() && exactJar.length() > 1024) return exactJar

        val jars = versionDir.listFiles()?.filter { it.name.endsWith(".jar") && it.length() > 1024 }
        if (!jars.isNullOrEmpty()) {
            val namedJar = jars.find { it.nameWithoutExtension == config.version.id }
            if (namedJar != null) return namedJar
            val clientJar = jars.find { it.name.equals("client.jar", ignoreCase = true) }
            if (clientJar != null) return clientJar
            return jars.maxByOrNull { it.length() }
        }

        val jsonFile = File(versionDir, "${config.version.id}.json")
        if (jsonFile.exists()) {
            try {
                val jsonText = jsonFile.readText()
                val inheritMatch = Regex("\"inheritsFrom\"\\s*:\\s*\"([^\"]+)\"").find(jsonText)
                val parentId = inheritMatch?.groupValues?.get(1)
                if (parentId != null) {
                    val parentDir = File(config.gameDir, "versions/$parentId")
                    val parentJar = File(parentDir, "$parentId.jar")
                    if (parentJar.exists() && parentJar.length() > 1024) return parentJar
                }
            } catch (_: Exception) {}
        }
        return null
    }

    // ==================== FCL-style 游戏参数 ====================

    private fun parseGameArgsFromVersionJson(versionDir: File, mcVersion: String): List<String> {
        val versionJson = File(versionDir, "$mcVersion.json")
        if (!versionJson.exists()) return emptyList()
        return try {
            val json = org.json.JSONObject(versionJson.readText())
            val args = parseGameArgsFromJson(json)

            if (json.has("inheritsFrom")) {
                val parentId = json.getString("inheritsFrom")
                val parentDir = File(versionDir.parentFile, parentId)
                val parentArgs = parseGameArgsFromVersionJson(parentDir, parentId)
                val currentArgs = if (args.isNotEmpty()) args
                    else if (json.has("minecraftArguments"))
                        json.getString("minecraftArguments").split(" ")
                    else emptyList()
                return (parentArgs + currentArgs).distinct()
            }

            if (args.isNotEmpty()) return args

            if (json.has("minecraftArguments")) {
                return json.getString("minecraftArguments").split(" ")
            }
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseGameArgsFromJson(json: org.json.JSONObject): List<String> {
        if (!json.has("arguments")) return emptyList()
        val gameArgs = json.getJSONObject("arguments").optJSONArray("game") ?: return emptyList()
        val args = mutableListOf<String>()
        for (i in 0 until gameArgs.length()) {
            val element = gameArgs.get(i)
            if (element is String) {
                args.add(element)
            } else if (element is org.json.JSONObject) {
                if (evaluateRules(element)) {
                    val value = element.get("value")
                    if (value is org.json.JSONArray) {
                        for (j in 0 until value.length()) {
                            if (value.get(j) is String) args.add(value.getString(j))
                        }
                    } else if (value is String) {
                        args.add(value)
                    }
                }
            }
        }
        return args
    }

    private fun evaluateRules(ruleObj: org.json.JSONObject): Boolean {
        val rules = ruleObj.optJSONArray("rules") ?: return true
        if (rules.length() == 0) return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            val action = rule.optString("action", "allow")
            var matches = true

            if (rule.has("os")) {
                val os = rule.getJSONObject("os")
                val osName = os.optString("name", "")
                if (osName.isNotEmpty()) {
                    val currentOs = "linux"
                    matches = matches && (osName == currentOs)
                }
                if (os.has("arch")) {
                    val osArch = os.getString("arch")
                    val currentArch = if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "aarch64" else "arm"
                    matches = matches && (osArch == currentArch)
                }
            }

            if (rule.has("features")) {
                val features = rule.getJSONObject("features")
                val keys = features.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val required = features.getBoolean(key)
                    val supported = key == "has_custom_resolution"
                    matches = matches && (!required || supported)
                }
            }

            if (matches) {
                allowed = action == "allow"
            }
        }
        return allowed
    }

    private fun buildFclGameArguments(config: LaunchConfiguration, gameVersion: String?): List<String> {
        val args = mutableListOf<String>()

        args.add("--username")
        args.add(config.profile.name)

        args.add("--version")
        args.add(config.version.id)

        args.add("--gameDir")
        args.add(config.gameDir.absolutePath)

        args.add("--assetsDir")
        args.add(config.assetsDir.absolutePath)

        val assetIndex = config.version.assetIndex?.id ?: config.version.id
        args.add("--assetIndex")
        args.add(assetIndex)

        args.add("--uuid")
        args.add(config.profile.id.replace("-", ""))

        args.add("--accessToken")
        args.add(config.accessToken)

        args.add("--userType")
        args.add(config.userType)

        args.add("--versionType")
        args.add(config.version.type.name.lowercase())

        if (config.fullscreen) {
            args.add("--fullscreen")
        }

        args.add("--width")
        args.add(config.width.toString())

        args.add("--height")
        args.add(config.height.toString())

        if (config.demo) {
            args.add("--demo")
        }

        config.serverAddress?.let { address ->
            if (gameVersion != null && compareGameVersion(gameVersion, "1.20") < 0) {
                args.add("--server")
                args.add(address)
                args.add("--port")
                args.add((config.serverPort ?: 25565).toString())
            } else if (gameVersion != null) {
                args.add("--quickPlayMultiplayer")
                val port = config.serverPort ?: 25565
                args.add("$address:$port")
            }
        }

        val versionDir = File(config.gameDir, "versions/${config.version.id}")
        val parsedArgs = parseGameArgsFromVersionJson(versionDir, config.version.id)
        if (parsedArgs.isNotEmpty()) {
            val existingKeys = mutableSetOf<String>()
            var i = 0
            while (i < args.size) {
                if (args[i].startsWith("--")) existingKeys.add(args[i])
                i++
            }
            var j = 0
            while (j < parsedArgs.size) {
                val token = parsedArgs[j]
                if (token.startsWith("--") && token !in existingKeys) {
                    args.add(token)
                    if (j + 1 < parsedArgs.size && !parsedArgs[j + 1].startsWith("--")) {
                        args.add(parsedArgs[j + 1])
                        j++
                    }
                }
                j++
            }
        }

        return args
    }

    private fun compareGameVersion(a: String, b: String): Int {
        return try {
            val partsA = a.split(".").map { it.toIntOrNull() ?: 0 }
            val partsB = b.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(partsA.size, partsB.size)) {
                val va = partsA.getOrElse(i) { 0 }
                val vb = partsB.getOrElse(i) { 0 }
                if (va != vb) return va - vb
            }
            0
        } catch (_: Exception) { 0 }
    }

    private fun getVersionJar(config: LaunchConfiguration): File {
        val exactJar = File(config.gameDir, "versions/${config.version.id}/${config.version.id}.jar")
        if (exactJar.exists()) return exactJar
        val found = findVersionJar(config)
        return found ?: exactJar
    }

    private fun deployGameAsset(context: Context, assetName: String, targetDir: File): File? {
        if (!targetDir.exists()) targetDir.mkdirs()
        val targetFile = File(targetDir, assetName)
        if (targetFile.exists() && targetFile.length() > 1024) {
            return targetFile
        }
        return try {
            context.assets.open("game/$assetName").use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var len: Int
                    while (input.read(buffer).also { len = it } != -1) {
                        output.write(buffer, 0, len)
                    }
                    output.flush()
                }
            }
            Log.d(TAG, "Deployed game asset: ${targetFile.absolutePath}")
            targetFile
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deploy game asset $assetName: ${e.message}")
            null
        }
    }

    // ==================== 生命周期 ====================

    fun stop() {
        zlBridge?.destroyJVM()
        currentProcess = null
    }

    fun forceStop() {
        zlBridge?.destroyJVM()
        currentProcess = null
    }

    fun isRunning(): Boolean = zlBridge?.isJVMRunning() == true

    fun getPid(): Int? = currentProcess?.pid
}

interface LaunchCallback {
    fun onGameExit(code: Int)
}