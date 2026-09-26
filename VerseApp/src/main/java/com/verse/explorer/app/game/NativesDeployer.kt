package com.verse.explorer.app.game

import android.content.Context
import android.os.Build
import com.verse.explorer.launcher.fcl.util.FCLPath
import java.io.File
import java.util.jar.JarFile

class NativesDeployer(private val context: Context, private val log: (String) -> Unit) {

    fun copyNativesToInternalStorage(externalNativesDir: File, javaHome: String?, librariesDir: File? = null): File {
        val internalNativesDir = File(context.filesDir, "natives")
        internalNativesDir.mkdirs()

        var copied = 0

        // LWJGL 使用 Pojav/FCL 的安卓 patched 原生库（由 lwjgl3-natives-release.aar 打包进 APK），
        // 这些库基于 Android bionic 编译，不依赖 glibc，无需清理、无需 glibc stub。
        // 提取 APK 中打包的 patched LWJGL 及全部所需原生库。
        copied += extractNativesFromApk(internalNativesDir, log)

        try {
            val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
            // 复制 APK 打包的全部原生库（含 Pojav patched LWJGL、渲染器、JNA 等），
            // 这些库基于 Android bionic 编译，可直接加载。
            nativeLibDir.listFiles()?.forEach { srcFile ->
                if (srcFile.isFile && srcFile.name.endsWith(".so")) {
                    val dstFile = File(internalNativesDir, srcFile.name)
                    if (dstFile.exists() && dstFile.length() == srcFile.length()) return@forEach
                    try {
                        srcFile.inputStream().use { input ->
                            dstFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        dstFile.setExecutable(true, false)
                        dstFile.setReadable(true, false)
                        copied++
                    } catch (e: Exception) {
                        log("复制 ${srcFile.name} 失败: ${e.message}\n")
                    }
                }
            }
        } catch (e: Exception) {
            log("复制APK native库失败: ${e.message}\n")
        }

        // externalNativesDir 中的历史遗留库不再复制（LWJGL 使用 APK patched 版）；
        // 仅补一个例外：libjemalloc.so（LWJGL jemalloc 绑定，原版 Minecraft 初始化
        // org.lwjgl.system.jemalloc.JEmalloc 时按名加载，APK 未打包，NeoForge 不触发
        // 该路径所以此前未暴露）。它由 NativeExtractionCoordinator 从
        // lwjgl-jemalloc-natives-linux.jar 提取到外部 natives 目录，此处同步到内部。
        try {
            val jemallocSrc = File(externalNativesDir, "libjemalloc.so")
            val jemallocDst = File(internalNativesDir, "libjemalloc.so")
            if (jemallocSrc.exists() && jemallocSrc.isFile && jemallocSrc.length() > 1024 && !jemallocDst.exists()) {
                jemallocSrc.inputStream().use { input ->
                    jemallocDst.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                jemallocDst.setExecutable(true, false)
                jemallocDst.setReadable(true, false)
                copied++
                log("复制 libjemalloc.so 到内部存储\n")
            }
        } catch (e: Exception) {
            log("复制 libjemalloc.so 失败: ${e.message}\n")
        }

        ensureJnaNativeLib(internalNativesDir)

        if (javaHome != null) {
            try {
                val jreDir = File(javaHome)
                val libFolder = findJreLibFolder(jreDir)
                if (libFolder != null) {
                    val freetypeSrc = File(libFolder, "libfreetype.so")
                    if (freetypeSrc.exists()) {
                        val freetypeDst = File(internalNativesDir, "libfreetype.so")
                        // 注意：不再从 JRE 复制 libfreetype.so
                        // LWJGL 3.4.1 会从 lwjgl-freetype-3.4.1-natives-linux.jar 提取正确版本
                        // 仅在目标文件不存在时才复制（作为 fallback）
                        if (!freetypeDst.exists()) {
                            freetypeSrc.inputStream().use { input ->
                                freetypeDst.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            freetypeDst.setExecutable(true, false)
                            freetypeDst.setReadable(true, false)
                            copied++
                            log("从JRE复制libfreetype.so到内部存储（fallback）\n")
                        }
                    }
                }
            } catch (e: Exception) {
                log("复制JRE libfreetype失败: ${e.message}\n")
            }
        }

        log("Natives复制到内部存储: $copied 个文件 -> ${internalNativesDir.absolutePath}\n")
        return internalNativesDir
    }

    fun findJreLibFolder(jreDir: File): File? {
        val libDir = File(jreDir, "lib")
        if (!libDir.exists()) return null
        val archSubdirs = listOf("aarch64", "amd64", "arm", "i386")
        for (sub in archSubdirs) {
            val subDir = File(libDir, sub)
            if (subDir.exists() && File(subDir, "libfreetype.so").exists()) {
                return subDir
            }
        }
        if (File(libDir, "libfreetype.so").exists()) {
            return libDir
        }
        return null
    }

    private fun extractNativesFromApk(nativesDir: File, log: (String) -> Unit): Int {
        var extracted = 0
        try {
            val apkFile = File(context.applicationInfo.sourceDir)
            if (!apkFile.exists()) {
                log("APK 文件不存在: ${apkFile.absolutePath}\n")
                return 0
            }

            val packagedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            val targetAbi = Build.SUPPORTED_ABIS?.firstOrNull { it in packagedAbis } ?: "arm64-v8a"

            // 提取 APK 打包的全部原生库，包括 Pojav/FCL 的安卓 patched LWJGL（liblwjgl*.so、
            // libfreetype.so、libshaderc.so），这些库基于 Android bionic 编译，可直接加载。
            // 此方法会跳过 libglfw.so（由 org.lwjgl.glfw.CallbackBridge 软件模拟，无需原生库）。

            java.util.zip.ZipFile(apkFile).use { zip ->
                val libPrefix = "lib/$targetAbi/"
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.name.startsWith(libPrefix)) continue
                    if (!entry.name.endsWith(".so")) continue
                    if (entry.isDirectory) continue

                    val libName = entry.name.substringAfterLast("/")
                    if (libName == "libglfw.so") continue

                    val dstFile = File(nativesDir, libName)

                    if (dstFile.exists() && dstFile.length() == entry.size) continue

                    zip.getInputStream(entry).use { input ->
                        dstFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    dstFile.setExecutable(true, false)
                    dstFile.setReadable(true, false)
                    extracted++
                }
            }
            log("从APK提取 $targetAbi 原生库: $extracted 个文件（含Pojav patched LWJGL）\n")
        } catch (e: Exception) {
            log("从APK提取原生库失败: ${e.message}\n")
        }
        return extracted
    }

    fun ensureJnaNativeLib(nativesDir: File) {
        val jnaLibFile = File(nativesDir, "libjnidispatch.so")
        // 必须与 JvmArgsBuilder/ClasspathBuilder 使用同一个目录：context.getDir("runtime", 0)/jna
        // getDir("runtime", 0) 实际路径是 /data/user/0/<pkg>/app_runtime/jna
        val jnaRuntimeDir = File(context.getDir("runtime", 0), "jna")
        val jnaDirLib = File(jnaRuntimeDir, "libjnidispatch.so")

        val alreadyExists = jnaLibFile.exists() && jnaLibFile.length() > 1024
        val jnaDirExists = jnaDirLib.exists() && jnaDirLib.length() > 1024

        if (alreadyExists && jnaDirExists) return

        var extractSuccess = false

        val targetAbi = Build.SUPPORTED_ABIS?.firstOrNull { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") } ?: "arm64-v8a"

        if (!alreadyExists) {
            // 使用 FCL 自带的 Android 兼容版 jna.jar 提取 libjnidispatch.so。
            // FCL 的 jna.jar 为 7.x（native 7.0.4），其 linux-* 路径下的 native 是 FCL 为 Android bionic 定制编译、可直接加载。
            // 注意：classpath 中的 MC jna jar 会被 DefaultLauncher 替换为本机 jna.jar，保证 Java 层与 native 版本一致。
            if (!extractSuccess) {
                try {
                    val jnaJar = File(jnaRuntimeDir, "jna.jar")
                    if (jnaJar.exists()) {
                        JarFile(jnaJar).use { jar ->
                            val jnaEntries = when (targetAbi) {
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
                            for (name in jnaEntries) {
                                val entry = jar.getJarEntry(name) ?: continue
                                jar.getInputStream(entry).use { input ->
                                    jnaLibFile.outputStream().use { output -> input.copyTo(output) }
                                }
                                jnaLibFile.setExecutable(true, false)
                                jnaLibFile.setReadable(true, false)
                                extractSuccess = true
                                log("从jna.jar提取libjnidispatch.so ($targetAbi): ${jnaLibFile.length()} bytes [entry=$name]\n")
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    log("从jna.jar提取libjnidispatch.so失败: ${e.message}\n")
                }
            }

            if (!extractSuccess) {
                try {
                    val apkNativeDir = File(context.applicationInfo.nativeLibraryDir)
                    val apkJnaLib = File(apkNativeDir, "libjnidispatch.so")
                    if (apkJnaLib.exists() && apkJnaLib.length() > 1024) {
                        apkJnaLib.inputStream().use { input ->
                            jnaLibFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        jnaLibFile.setExecutable(true, false)
                        jnaLibFile.setReadable(true, false)
                        extractSuccess = true
                        log("从APK复制libjnidispatch.so: ${jnaLibFile.length()} bytes\n")
                    } else {
                        log("APK中libjnidispatch.so不存在: ${apkJnaLib.absolutePath} (exists=${apkJnaLib.exists()}, size=${apkJnaLib.length()})\n")
                    }
                } catch (e: Exception) {
                    log("从APK复制libjnidispatch.so异常: ${e.message}\n")
                }
            }

            if (!extractSuccess) {
                try {
                    context.assets.open("natives/libjnidispatch.so").use { input ->
                        jnaLibFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    jnaLibFile.setExecutable(true, false)
                    jnaLibFile.setReadable(true, false)
                    extractSuccess = true
                    log("从assets提取libjnidispatch.so: ${jnaLibFile.length()} bytes\n")
                } catch (e: Exception) {
                    log("从assets提取libjnidispatch.so失败: ${e.message}\n")
                }
            }
        } else {
            extractSuccess = true
        }

        if (!extractSuccess) {
            log("警告: libjnidispatch.so 未找到！JNA可能无法加载！\n")
            return
        }

        if (!jnaDirExists) {
            try {
                jnaDirLib.parentFile?.mkdirs()
                File(nativesDir, "libjnidispatch.so").inputStream().use { input ->
                    jnaDirLib.outputStream().use { output -> input.copyTo(output) }
                }
                jnaDirLib.setExecutable(true, false)
                jnaDirLib.setReadable(true, false)
                log("复制libjnidispatch.so到jna目录: ${jnaDirLib.length()} bytes [jna.boot.library.path固定]\n")
            } catch (e: Exception) {
                log("复制libjnidispatch.so到jna目录失败: ${e.message}\n")
            }
        }
    }

    fun extractDefaultNatives(nativesDir: File) {
        try {
            val archPaths = listOf(
                "natives/arm64-v8a",
                "natives/armeabi-v7a",
                "natives/x86_64",
                "natives/x86"
            )
            var extracted = 0
            for (archPath in archPaths) {
                try {
                    val files = context.assets.list(archPath) ?: continue
                    for (file in files) {
                        try {
                            context.assets.open("$archPath/$file").use { input ->
                                val target = File(nativesDir, file)
                                if (!target.exists()) {
                                    target.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                    extracted++
                                }
                            }
                        } catch (e: Exception) {
                            log("提取 $file 失败: ${e.message}\n")
                        }
                    }
                } catch (_: Exception) {}
            }
            if (extracted == 0) {
                try {
                    val rootFiles = context.assets.list("natives") ?: emptyArray()
                    for (file in rootFiles) {
                        if (file.endsWith(".so") && !file.startsWith("liblwjgl")) {
                            try {
                                context.assets.open("natives/$file").use { input ->
                                    val target = File(nativesDir, file)
                                    if (!target.exists()) {
                                        target.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                        extracted++
                                    }
                                }
                            } catch (e: Exception) {
                                log("提取 $file 失败: ${e.message}\n")
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            log("默认原生库提取: $extracted 个文件\n")
        } catch (e: Exception) {
            log("默认原生库提取异常: ${e.message}\n")
        }
    }

    /**
     * 部署 FCL 启动链路所需的运行时文件：
     *  - MioLaunchWrapper.jar / MioLibPatcher.jar → FCLPath.PLUGIN_DIR
     *  - lwjgl.jar → FCLPath.LWJGL_DIR（缺失时从 assets 回退）
     *  - caciocavallo 8/17 jar → FCLPath.CACIOCAVALLO_*_DIR（缺失时从 assets 回退）
     *  - resolv.conf → FCLPath.JAVA_PATH
     */
    fun deployFclRuntime(gameDir: File) {
        try {
            if (FCLPath.FILES_DIR == null) {
                FCLPath.loadPaths(context, gameDir.absolutePath)
            }

            var deployed = 0
            // 1. Mio wrapper / patcher 插件
            val pluginDir = File(FCLPath.PLUGIN_DIR)
            pluginDir.mkdirs()
            deployed += copyAssetIfMissing("game/MioLaunchWrapper.jar", File(pluginDir, "MioLaunchWrapper.jar"))
            deployed += copyAssetIfMissing("game/MioLibPatcher.jar", File(pluginDir, "MioLibPatcher.jar"))

            // 2. lwjgl 栈对齐 FCL 1.3.3.5 官方自洽套装：assets/app_runtime/lwjgl/3.4.1/
            // 多 jar（GLFW 在 merged-modules，含 Preedit 回调与 glfwGetMonitorName，
            // internalWindowSizeChanged(J)V，配 libpojavexec 80096 + liblwjgl*.so 3.4.1）。
            // 每次启动清空旧 jar 后从 APK 全量重建，避免历史 jar 掺入 classpath
            val lwjglDir = File(FCLPath.LWJGL_DIR)
            lwjglDir.mkdirs()
            lwjglDir.listFiles()?.filter { it.name.endsWith(".jar") }?.forEach { it.delete() }
            val lwjglAssetNames: Array<String> = context.assets.list("app_runtime/lwjgl") ?: emptyArray()
            for (name in lwjglAssetNames.filter { it.endsWith(".jar") }.sorted()) {
                deployed += copyAssetIfMissing("app_runtime/lwjgl/$name", File(lwjglDir, name))
            }
            // 防呆：FCL 官方 3.4.1 多 jar 套装必须存在（GLFW/Preedit 在 merged-modules）；
            // 缺失会导致 26.2 启动失败（KeyboardHandler NoClassDefFoundError / pojavexec JNI_OnLoad 失败）
            val lwjglMainJar = File(lwjglDir, "lwjgl.jar")
            if (!lwjglMainJar.exists() || lwjglMainJar.length() < 100_000) {
                log("警告: FCL 3.4.1 lwjgl 套装缺失（lwjgl.jar=${if (lwjglMainJar.exists()) lwjglMainJar.length() else 0} bytes），26.2 将无法启动！\n")
            }

            // 3. caciocavallo 17
            val cacio17Dir = File(FCLPath.CACIOCAVALLO_17_DIR)
            cacio17Dir.mkdirs()
            val cacio17Files = listOf("cacio-agent.jar", "cacio-shared-1.19.1-SNAPSHOT.jar", "cacio-tta-1.19.1-SNAPSHOT.jar")
            for (name in cacio17Files) {
                if (!File(cacio17Dir, name).exists() || File(cacio17Dir, name).length() < 1024) {
                    deployed += copyAssetIfMissing("app_runtime/caciocavallo17/$name", File(cacio17Dir, name))
                }
            }

            // 4. caciocavallo 8
            val cacio8Dir = File(FCLPath.CACIOCAVALLO_8_DIR)
            cacio8Dir.mkdirs()
            val cacio8Files = listOf(
                "cacio-androidnw-1.10-SNAPSHOT.jar",
                "cacio-shared-1.10-SNAPSHOT.jar",
                "ResConfHack.jar"
            )
            for (name in cacio8Files) {
                if (!File(cacio8Dir, name).exists() || File(cacio8Dir, name).length() < 1024) {
                    deployed += copyAssetIfMissing("app_runtime/caciocavallo/$name", File(cacio8Dir, name))
                }
            }

            // 5. resolv.conf（FCL 参数 -Dext.net.resolvPath 引用）
            val resolvFile = File(FCLPath.JAVA_PATH, "resolv.conf")
            if (!resolvFile.exists()) {
                try {
                    resolvFile.parentFile?.mkdirs()
                    resolvFile.writeText("nameserver 1.1.1.1\nnameserver 1.0.0.1\n")
                } catch (e: Exception) {
                    log("写入 resolv.conf 失败: ${e.message}\n")
                }
            }

            log("FCL 运行时部署完成: $deployed 个文件\n")
        } catch (e: Exception) {
            log("FCL 运行时部署异常: ${e.message}\n")
        }
    }

    private fun copyAssetIfMissing(assetPath: String, dest: File): Int {
        try {
            if (dest.exists() && dest.length() >= 1024) return 0
            dest.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.setReadable(true, false)
            log("部署 FCL 资源: $assetPath -> ${dest.name} (${dest.length()} bytes)\n")
            return 1
        } catch (e: Exception) {
            log("部署 FCL 资源失败 $assetPath: ${e.message}\n")
            return 0
        }
    }

    fun deployLwjglToExternalStorage(gameDir: File) {
        try {
            val packagedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            val targetAbi = Build.SUPPORTED_ABIS?.firstOrNull { it in packagedAbis } ?: "arm64-v8a"
            val lwjglArch = when (targetAbi) {
                "arm64-v8a" -> "arm64"
                "armeabi-v7a" -> "arm32"
                "x86_64" -> "x64"
                "x86" -> "x86"
                else -> "arm64"
            }
            val lwjglVersion = "3.3.6-snapshot"
            val lwjglDir = File(gameDir, ".lwjgl/$lwjglVersion/$lwjglArch")
            if (!lwjglDir.exists()) lwjglDir.mkdirs()

            val apkFile = File(context.applicationInfo.sourceDir)
            if (!apkFile.exists()) return

            val libPrefix = "lib/$targetAbi/"
            val lwjglLibs = listOf(
                "liblwjgl.so", "liblwjgl_nanovg.so", "liblwjgl_opengl.so",
                "liblwjgl_stb.so", "liblwjgl_tinyfd.so", "liblwjgl_vma.so",
                "libfreetype.so", "libshaderc.so"
            )

            java.util.zip.ZipFile(apkFile).use { zip ->
                for (libName in lwjglLibs) {
                    val entryName = libPrefix + libName
                    val entry = zip.getEntry(entryName) ?: continue
                    val dstFile = File(lwjglDir, libName)
                    if (dstFile.exists() && dstFile.length() == entry.size) continue
                    zip.getInputStream(entry).use { input ->
                        dstFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    dstFile.setExecutable(true, false)
                    dstFile.setReadable(true, false)
                }
            }
            log("部署LWJGL库到外部存储: $lwjglDir ($lwjglArch)\n")
        } catch (e: Exception) {
            log("部署LWJGL库到外部存储失败: ${e.message}\n")
        }
    }
}
