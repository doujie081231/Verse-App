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

        // 这些库基于 Android bionic 编译，不依赖 glibc，无需清理、无需 glibc stub。
        // 提取 APK 中打包的 patched LWJGL 及全部所需原生库。
        copied += extractNativesFromApk(internalNativesDir, log)

        try {
            val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
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
            log("从APK提取 $targetAbi 原生库: $extracted 个文件\n")
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
     * 部署运行时资源（LWJGL 套装、caciocavallo、Mio 插件等）。
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
            // 外置登录（皮肤站）启动注入依赖该 agent，随运行时资源一并部署
            deployed += copyAssetIfMissing("game/authlib-injector.jar", File(pluginDir, "authlib-injector.jar"))

            // 按版本子目录部署 3.3.3（旧 MC ≤26.1 用）与 3.4.1（26.2+ SDL 链用）各一套，
            // 每套含 jar + natives/<arch>/。DefaultLauncher 按版本 JSON 解析出的 LWJGL 版本选择目录。
            // 两个版本互不覆盖；更新版本时以 assets 为准整体重建该版本目录。
            val lwjglAssetDirs: Array<String>? = try {
                context.assets.list("app_runtime/lwjgl")
                    ?.filter { Regex("""\d+\.\d+\.\d+""").matches(it) }
                    ?.sorted()?.toTypedArray()
            } catch (e: Exception) {
                null
            }
            val lwjglVersions = lwjglAssetDirs ?: emptyArray<String>()
            for (verDirName in lwjglVersions) {
                deployed += deployLwjglVersionFromAssets(verDirName)
                deployed += ensureLwjglNativesForDevice(verDirName)
            }
            for (verDirName in lwjglVersions) {
                val lwjglMainJar = File(File(FCLPath.LWJGL_DIR, verDirName), "lwjgl.jar")
                if (!lwjglMainJar.exists() || lwjglMainJar.length() < 100_000) {
                    log("警告: $verDirName lwjgl 套装缺失（lwjgl.jar=${if (lwjglMainJar.exists()) lwjglMainJar.length() else 0} bytes）！\n")
                }
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

            val resolvFile = File(FCLPath.JAVA_PATH, "resolv.conf")
            if (!resolvFile.exists()) {
                try {
                    resolvFile.parentFile?.mkdirs()
                    resolvFile.writeText("nameserver 1.1.1.1\nnameserver 1.0.0.1\n")
                } catch (e: Exception) {
                    log("写入 resolv.conf 失败: ${e.message}\n")
                }
            }

            log("运行时部署完成: $deployed 个文件\n")
        } catch (e: Exception) {
            log("运行时部署异常: ${e.message}\n")
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
            log("部署资源: $assetPath -> ${dest.name} (${dest.length()} bytes)\n")
            return 1
        } catch (e: Exception) {
            log("部署资源失败 $assetPath: ${e.message}\n")
            return 0
        }
    }

    /**
     * 递归遍历 assets/app_runtime/lwjgl/<version>/，逐文件复制；已存在且大小一致则跳过。
     */
    private fun deployLwjglVersionFromAssets(versionDir: String): Int {
        var deployed = 0
        val baseAsset = "app_runtime/lwjgl/$versionDir"
        val destBase = File(File(FCLPath.LWJGL_DIR), versionDir)
        try {
            fun walk(relPath: String) {
                val assetPath = if (relPath.isEmpty()) baseAsset else "$baseAsset/$relPath"
                val children: Array<String>? = try {
                    context.assets.list(assetPath)
                } catch (e: Exception) {
                    null
                }
                if (children == null || children.isEmpty()) {
                    // 叶子文件
                    val dest = File(destBase, relPath)
                    // 旧裁剪版，旧版一旦落盘"存在即跳过"永不更新 → NoSuchFieldException 崩溃。
                    // 主 jar 每次启动强制用 assets 覆盖，其余文件仍"存在即跳过"。
                    if (dest.exists() && dest.length() > 0 && dest.name != "lwjgl.jar") return
                    dest.parentFile?.mkdirs()
                    context.assets.open(assetPath).use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (dest.name.endsWith(".so")) {
                        dest.setExecutable(true, false)
                    }
                    dest.setReadable(true, false)
                    log("部署资源: $assetPath -> $versionDir/${relPath.replace('\\', '/')} (${dest.length()} bytes)\n")
                    deployed++
                } else {
                    for (child in children) {
                        walk(if (relPath.isEmpty()) child else "$relPath/$child")
                    }
                }
            }
            walk("")
            // assets 为准清理残留：旧版本目录曾部署过官方 lwjgl-glfw/opengl/sdl 原版模块 jar
            // merged-modules 合并包；若存量设备不删除这些残留 jar，会和合并包一起进
            // classpath 造成类重复。删除 assets 中已不存在的顶层 .jar。
            try {
                val assetTop = context.assets.list(baseAsset)?.toSet() ?: emptySet()
                destBase.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".jar") && file.name !in assetTop) {
                        if (file.delete()) {
                            log("清理残留 LWJGL jar: $versionDir/${file.name}\n")
                            deployed++
                        }
                    }
                }
            } catch (e: Exception) {
                log("清理残留 LWJGL jar 失败 $versionDir: ${e.message}\n")
            }
        } catch (e: Exception) {
            log("部署资源失败 $baseAsset: ${e.message}\n")
        }
        return deployed
    }

    companion object {
        private val LWJGL_NATIVE_LIBS = listOf(
            "liblwjgl.so", "liblwjgl_nanovg.so", "liblwjgl_opengl.so",
            "liblwjgl_spng.so", "liblwjgl_stb.so", "liblwjgl_tinyfd.so",
            "liblwjgl_vma.so", "libfreetype.so", "libopenal.so", "libshaderc.so"
        )
    }

    /**
     * 其余 ABI（x86_64 模拟器等）从 APK 的 lib/<abi>/ 补齐同名配对原生库到
     * -Dorg.lwjgl.librarypath 指向的原生库与打包 jar 配对且架构匹配。
     */
    private fun ensureLwjglNativesForDevice(versionDir: String): Int {
        val packagedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        val targetAbi = Build.SUPPORTED_ABIS?.firstOrNull { it in packagedAbis } ?: "arm64-v8a"
        if (targetAbi == "arm64-v8a") return 0

        val nativesDir = File(File(FCLPath.LWJGL_DIR, versionDir), "natives/$targetAbi")
        if (File(nativesDir, "liblwjgl.so").exists() && File(nativesDir, "liblwjgl.so").length() > 0) return 0

        var deployed = 0
        try {
            nativesDir.mkdirs()
            val apkFile = File(context.applicationInfo.sourceDir)
            if (!apkFile.exists()) return 0
            val libPrefix = "lib/$targetAbi/"
            java.util.zip.ZipFile(apkFile).use { zip ->
                for (libName in LWJGL_NATIVE_LIBS) {
                    val entry = zip.getEntry(libPrefix + libName) ?: continue
                    val dst = File(nativesDir, libName)
                    if (dst.exists() && dst.length() == entry.size) continue
                    zip.getInputStream(entry).use { input ->
                        dst.outputStream().use { output -> input.copyTo(output) }
                    }
                    dst.setExecutable(true, false)
                    dst.setReadable(true, false)
                    deployed++
                }
            }
            log("补齐 LWJGL natives($targetAbi) -> $nativesDir: $deployed 个文件\n")
        } catch (e: Exception) {
            log("补齐 LWJGL natives $targetAbi 失败: ${e.message}\n")
        }
        return deployed
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
