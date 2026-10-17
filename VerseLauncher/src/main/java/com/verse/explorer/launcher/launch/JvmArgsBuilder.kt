package com.verse.explorer.launcher.launch

import android.content.Context
import android.os.Build
import android.util.Log
import com.verse.explorer.launcher.renderer.RendererManager
import java.io.File
import java.util.Locale
import java.util.TimeZone

internal class JvmArgsBuilder(
    private val context: Context,
    private val environmentSetup: LaunchEnvironmentSetup,
    private val classpathBuilder: ClasspathBuilder,
    private val gameArgsParser: GameArgsParser
) {

    companion object {
        private const val TAG = "MinecraftLauncher"
    }

    fun buildFullArgs(
        config: LaunchConfiguration,
        effectiveRenderer: RendererManager.RendererConfig = RendererManager.getDefaultRenderer()
    ): List<String> {
        val args = mutableListOf<String>()

        val isJava8 = config.javaVersion <= 8
        val nativeLibDir = config.nativesDir.absolutePath
        val runtimeDir = context.getDir("runtime", 0)
        val jnaDir = File(runtimeDir, "jna")
        val cacheDir = context.cacheDir.absolutePath + "/Verse"
        val rendererId = effectiveRenderer.id
        File(cacheDir).mkdirs()

        //Cacio Java args
        environmentSetup.addCacioArgs(args, config, isJava8, nativeLibDir)

        //关键系统属性（必须在 -Xmx/-Xms 之前）
        args.add("-Djdk.lang.Process.launchMechanism=FORK")
        args.add("-Dglfwstub.initEgl=false")
        args.add("-Dswing.defaultlaf=javax.swing.plaf.nimbus.NimbusLookAndFeel")

        val resolvFile = File(context.filesDir, "resolv.conf")
        if (!resolvFile.exists()) {
            try {
                resolvFile.parentFile?.mkdirs()
                resolvFile.createNewFile()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create resolv.conf: ${e.message}")
            }
        }
        args.add("-Dext.net.resolvPath=${resolvFile.absolutePath}")

        val log4j2File = gameArgsParser.ensureLog4j2Config(config)
        args.add("-Dlog4j.configurationFile=${log4j2File.absolutePath}")

        //自定义 JVM args
        if (config.customJvmArgs.isNotBlank()) {
            args.addAll(config.customJvmArgs.split("\\s+".toRegex()).filter { it.isNotBlank() })
        }

        //额外的 Java arguments (从版本配置传入)
        config.javaArguments.forEach { arg ->
            if (arg.isNotBlank()) args.add(arg)
        }

        //memory settings
        if (config.maxMemory > 0) args.add("-Xmx${config.maxMemory}m")
        if (config.minMemory > 0 && config.minMemory <= config.maxMemory) args.add("-Xms${config.minMemory}m")

        //GC settings
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

        // 已移除 -XX:TieredStopAtLevel=1（保留 C2 完整 JIT）。
        // C1-only 会让区块构建/光照等热循环慢数倍；本机 6 份 hs_err 均无 C2 aarch64 断言。

        //gl4es 会触发 System.gc()，禁用以避免卡顿
        if (rendererId == RendererManager.ID_GL4ES || rendererId == RendererManager.ID_VGPU) {
            args.add("-XX:+DisableExplicitGC")
        }

        //java.home
        args.add("-Djava.home=${config.javaHome.absolutePath}")

        //encoding
        args.add("-Dfile.encoding=UTF-8")
        if (config.javaVersion < 19) {
            args.add("-Dsun.stdout.encoding=UTF-8")
            args.add("-Dsun.stderr.encoding=UTF-8")
        } else {
            args.add("-Dstdout.encoding=UTF-8")
            args.add("-Dstderr.encoding=UTF-8")
        }

        //Log4j RCE mitigation
        args.add("-Djava.rmi.server.useCodebaseOnly=true")
        args.add("-Dcom.sun.jndi.rmi.object.trustURLCodebase=false")
        args.add("-Dcom.sun.jndi.cosnaming.object.trustURLCodebase=false")
        args.add("-Dlog4j2.formatMsgNoLookups=true")

        //thread stack for 32-bit
        if (Build.SUPPORTED_32_BIT_ABIS.isNotEmpty() && Build.SUPPORTED_64_BIT_ABIS.isEmpty()) {
            args.add("-Xss1m")
        }

        //core flags
        args.add("-Dminecraft.client.jar=${classpathBuilder.getVersionJar(config)}")
        args.add("-XX:ActiveProcessorCount=${Runtime.getRuntime().availableProcessors()}")
        args.add("-Dfml.ignoreInvalidMinecraftCertificates=true")
        args.add("-Dfml.ignorePatchDiscrepancies=true")
        args.add("-Dfml.earlyprogresswindow=false")

        // 运行时系统属性
        args.add("-Djava.io.tmpdir=$cacheDir")

        // glfwstub 窗口尺寸（LWJGL 读取）
        args.add("-Dglfwstub.windowWidth=${config.width}")
        args.add("-Dglfwstub.windowHeight=${config.height}")

        // oshi: disable problematic Linux-specific checks on Android
        args.add("-Doshi.os.prefix=Android")
        args.add("-Doshi.arch=${Build.SUPPORTED_64_BIT_ABIS.firstOrNull() ?: "aarch64"}")

        //OS identity
        args.add("-Dos.name=Linux")
        args.add("-Dos.version=Android-${Build.VERSION.RELEASE}")

        //LWJGL library paths
        args.add("-Dorg.lwjgl.system.stackSize=3072")
        args.add("-Dorg.lwjgl.util.Debug=true")
        args.add("-Dorg.lwjgl.util.DebugLoader=true")
        val glLibName = effectiveRenderer.glName
        // 系统库使用 patched 的 liblwjgl.so（提供完整的 MemoryUtil/FFICIF/JNI.invoke 等系统原生方法），
        // 避免手写 stub 缺失 FFICIF 等结构体访问方法导致 UnsatisfiedLinkError。
        // glfw 保留自定义 libverseexec.so（提供基于 bionic 的 EGL/GLFW 实现与 CallbackBridge 钩子）。
        args.add("-Dorg.lwjgl.system.libname=liblwjgl.so")
        args.add("-Dorg.lwjgl.glfw.libname=libverseexec.so")
        args.add("-Dorg.lwjgl.opengl.libname=$glLibName")
        args.add("-Dorg.lwjgl.freetype.libname=libfreetype.so")
        args.add("-Dorg.lwjgl.vulkan.libname=libvulkan.so")
        args.add("-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared")

        //Library path
        val modRuntimeDir = context.getDir("runtime_mod", 0).absolutePath

        val libraryPath = environmentSetup.buildFclLibraryPath(
            javaPath = config.javaHome.absolutePath,
            nativeLibDir = nativeLibDir,
            jnaDir = jnaDir.absolutePath,
            isJava8 = isJava8,
            nativesDir = config.nativesDir.absolutePath,
            modRuntimeDir = modRuntimeDir
        )
        args.add("-Djava.library.path=$libraryPath")
        // LWJGL 3.4.x 的 SharedLibraryLoader 会在此目录创建临时文件验证可写性
        // cacheDir 在某些Android设备上 SUID 位导致 Files.isWritable() 返回 false
        // 使用 nativesDir（应用 filesDir 子目录）确保可写
        args.add("-Dorg.lwjgl.system.SharedLibraryExtractPath=${config.nativesDir.absolutePath}")
        args.add("-Dorg.lwjgl.librarypath=$libraryPath")

        //misc
        args.add("-Dloader.disable_forked_guis=true")
        args.add("-Duser.home=${config.gameDir.absolutePath}")
        args.add("-Duser.language=${System.getProperty("user.language", "zh")}")
        args.add("-Duser.country=${Locale.getDefault().country}")
        args.add("-Duser.timezone=${TimeZone.getDefault().id}")
        args.add("-Dsodium.checks.issue2561=false")
        args.add("-Dcpu.name=${environmentSetup.getSocName()}")

        //JNA boot path
        args.add("-Djna.boot.library.path=${jnaDir.absolutePath}")
        args.add("-Djna.nosys=true")
        args.add("-Djna.nounpack=true")
        args.add("-Djna.tmpdir=$cacheDir")

        //Fix 1.7.2 Forge
        val gameVersion = resolveGameVersion(config)
        if (gameVersion == "1.7.2") {
            args.add("-Dsort.patch=true")
        }

        // V3.6: MioLibPatcher + MioLaunchWrapper（完整 Mio 基础设施）
        val pluginsDir = File(context.filesDir, "plugins")
        if (!pluginsDir.exists()) pluginsDir.mkdirs()

        val mioLibPatcherJar = gameArgsParser.deployGameAsset(context, "MioLibPatcher.jar", pluginsDir)
        if (mioLibPatcherJar != null) {
            args.add("-javaagent:${mioLibPatcherJar.absolutePath}")
            Log.d(TAG, "MioLibPatcher javaagent: ${mioLibPatcherJar.absolutePath}")
        } else {
            Log.w(TAG, "MioLibPatcher.jar not found, continuing without javaagent transform")
        }

        val mioLaunchWrapperJar = gameArgsParser.deployGameAsset(context, "MioLaunchWrapper.jar", pluginsDir)
        if (mioLaunchWrapperJar != null) {
            Log.d(TAG, "MioLaunchWrapper deployed: ${mioLaunchWrapperJar.absolutePath}")
        } else {
            Log.w(TAG, "MioLaunchWrapper.jar not found")
        }

        val bootstrapJar = gameArgsParser.deployGameAsset(context, "verse-bootstrap.jar", pluginsDir)
            ?: gameArgsParser.deployGameAsset(context, "game/verse-bootstrap.jar", pluginsDir)
        if (bootstrapJar != null) {
            Log.d(TAG, "verse-bootstrap.jar deployed: ${bootstrapJar.absolutePath}")
        } else {
            Log.w(TAG, "verse-bootstrap.jar not found, VerseBootstrap main class will not be available")
        }

        //mod loader env markers (空实现)
        addModLoaderMarkers(config, args)

        //add-opens/add-exports for Java 17+
        if (!isJava8) {
            addJavaModuleArgs(args, config)
        }

        //beGesture injector
        if (config.beGesture) {
            val injectorArg = getInjectorArg(config)
            if (injectorArg != null) {
                args.add("-Dfcl.injector=$injectorArg")
            }
        }

        //build classpath — 关键！LWJGL jar 排在最前面
        val classpath = classpathBuilder.buildFclClasspath(config, runtimeDir)
        args.add("-cp")
        args.add(classpath)

        // V3.6: VerseBootstrap 作为主类，真实主类作为第一个参数
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

        //game arguments
        args.addAll(gameArgsParser.buildFclGameArguments(config, gameVersion))

        //如果有自定义游戏参数，追加
        if (config.gameArguments.isNotBlank()) {
            args.addAll(config.gameArguments.split("\\s+".toRegex()).filter { it.isNotBlank() })
        }

        return args
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
}
