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

        // 清理旧的 LWJGL 官方库和相关依赖（3.3.6-snapshot 版本）
        // 这些库现在由 MC 自带的 LWJGL natives jar 提供（3.4.1 版本）
        val libsToClean = setOf(
            "liblwjgl.so", "liblwjgl_nanovg.so", "liblwjgl_opengl.so",
            "liblwjgl_stb.so", "liblwjgl_tinyfd.so", "liblwjgl_vma.so",
            "libfreetype.so", "libshaderc.so", "libglfw.so"
        )
        internalNativesDir.listFiles()?.forEach { existing ->
            if (existing.isFile && existing.name in libsToClean) {
                if (existing.delete()) {
                    log("清理残留库: ${existing.name}\n")
                }
            }
        }

        // 清理 SharedLibraryLoader 创建的空目录（如 3.4.1+2）
        internalNativesDir.listFiles()?.forEach { existing ->
            if (existing.isDirectory && existing.name.matches(Regex("\\d+\\.\\d+\\.\\d+\\+\\d+"))) {
                existing.deleteRecursively()
                log("清理 SharedLibraryLoader 残留目录: ${existing.name}\n")
            }
        }

        copied += extractNativesFromApk(internalNativesDir, log)

        // 从 MC 自带的 LWJGL 3.4.1 natives jar 中提取原生库
        // LWJGL 3.4.1 的 SharedLibraryLoader 在 Android 上可能因 Files.isWritable() 返回 false 而提取失败
        // 手动提取到 natives 目录，让 LWJGL 通过 java.library.path 直接加载
        if (librariesDir != null && librariesDir.exists()) {
            copied += extractLwjglNativesFromJars(librariesDir, internalNativesDir)
        }

        // 部署 glibc 兼容 stub 库
        // LWJGL 官方 natives（从 lwjgl-*-natives-linux.jar 提取）是用 glibc 工具链编译的，
        // 其 ELF 的 DT_NEEDED 段硬性依赖 libdl.so.2、libpthread.so.0 等版本化库名，
        // 而 Android bionic libc 没有这些版本化库名。
        // 复制 libglibc_stub.so 为各 glibc 版本化库名，让动态链接器能找到这些库，
        // 实际符号（dlopen、pthread_create 等）会从 Android 的 libc.so 中解析。
        copied += deployGlibcStubLibs(internalNativesDir)

        try {
            val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
            // 同样跳过 LWJGL 官方库，避免 3.3.6-snapshot 版本覆盖 MC 自带的 3.4.1
            val skipFromNativeLibDir = setOf(
                "liblwjgl.so", "liblwjgl_nanovg.so", "liblwjgl_opengl.so",
                "liblwjgl_stb.so", "liblwjgl_tinyfd.so", "liblwjgl_vma.so",
                "libfreetype.so", "libshaderc.so"
            )
            nativeLibDir.listFiles()?.forEach { srcFile ->
                if (srcFile.isFile && srcFile.name.endsWith(".so")) {
                    if (srcFile.name in skipFromNativeLibDir) return@forEach
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
                        if (srcFile.name == "libglfw.so") {
                            log("已部署自定义 libglfw.so (${dstFile.length()} bytes)\n")
                        }
                    } catch (e: Exception) {
                        log("复制 ${srcFile.name} 失败: ${e.message}\n")
                    }
                }
            }
        } catch (e: Exception) {
            log("复制APK native库失败: ${e.message}\n")
        }

        externalNativesDir.listFiles()?.forEach { srcFile ->
            if (srcFile.isFile && srcFile.name.endsWith(".so")) {
                if (srcFile.name.startsWith("liblwjgl") || srcFile.name == "libglfw.so" ||
                    srcFile.name == "libfreetype.so" || srcFile.name == "libshaderc.so") {
                    log("跳过GLIBC库: ${srcFile.name}\n")
                    return@forEach
                }
                val dstFile = File(internalNativesDir, srcFile.name)
                if (!dstFile.exists() || dstFile.length() != srcFile.length()) {
                    try {
                        srcFile.inputStream().use { input ->
                            dstFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        dstFile.setExecutable(true, false)
                        dstFile.setReadable(true, false)
                        copied++
                    } catch (_: Exception) {}
                }
            }
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

            // 跳过 LWJGL 官方库（liblwjgl*.so），这些库版本是 3.3.6-snapshot
            // MC 26.2+ 自带 LWJGL 3.4.1，版本不匹配会导致加载失败
            // LWJGL 3.4.1 会自动从 classpath 中的 lwjgl-3.4.1-natives-linux.jar 提取正确版本
            // 但保留 liblwjgl_loader.so（我们的自定义 loader）和 libglfw.so（自定义 GLFW 实现）
            val skipLibs = setOf(
                "liblwjgl.so", "liblwjgl_nanovg.so", "liblwjgl_opengl.so",
                "liblwjgl_stb.so", "liblwjgl_tinyfd.so", "liblwjgl_vma.so",
                "libfreetype.so", "libshaderc.so"
            )

            java.util.zip.ZipFile(apkFile).use { zip ->
                val libPrefix = "lib/$targetAbi/"
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.name.startsWith(libPrefix)) continue
                    if (!entry.name.endsWith(".so")) continue
                    if (entry.isDirectory) continue

                    val libName = entry.name.substringAfterLast("/")
                    if (libName in skipLibs) continue

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
            log("从APK提取 $targetAbi 原生库: $extracted 个文件（跳过LWJGL官方库）\n")
        } catch (e: Exception) {
            log("从APK提取原生库失败: ${e.message}\n")
        }
        return extracted
    }

    /**
     * 从 MC 自带的 LWJGL natives jar 中提取原生库到 natives 目录。
     *
     * LWJGL 3.4.x 的 SharedLibraryLoader 在 Android 上可能因 Files.isWritable() 返回 false
     * 而无法提取原生库（即使目录实际可写）。手动提取到 natives 目录后，
     * LWJGL 会通过 java.library.path 直接加载，跳过 SharedLibraryLoader。
     */
    private fun extractLwjglNativesFromJars(librariesDir: File, nativesDir: File): Int {
        var extracted = 0
        try {
            // 根据设备 ABI 选择对应的 natives 目录名
            val packagedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            val targetAbi = Build.SUPPORTED_ABIS?.firstOrNull { it in packagedAbis } ?: "arm64-v8a"
            val archDir = when (targetAbi) {
                "arm64-v8a" -> "arm64"
                "armeabi-v7a" -> "arm32"
                "x86_64" -> "x64"
                "x86" -> "x86"
                else -> "x64"
            }

            // 查找所有 LWJGL natives jar（文件名包含 -natives-linux）
            val lwjglNativesJars = mutableListOf<File>()
            librariesDir.walkTopDown().forEach { file ->
                if (file.name.endsWith("-natives-linux.jar") &&
                    file.absolutePath.contains("/org/lwjgl/") &&
                    file.length() > 1024) {
                    lwjglNativesJars.add(file)
                }
            }

            if (lwjglNativesJars.isEmpty()) {
                log("未找到 LWJGL natives jar\n")
                return 0
            }

            log("找到 ${lwjglNativesJars.size} 个 LWJGL natives jar，开始提取 ($archDir)\n")

            for (jarFile in lwjglNativesJars) {
                try {
                    JarFile(jarFile).use { jar ->
                        val entries = jar.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (entry.isDirectory) continue
                            if (!entry.name.endsWith(".so")) continue

                            // LWJGL natives jar 中的 .so 文件路径如 linux/x64/org/lwjgl/liblwjgl.so
                            // 检查路径中是否包含匹配的架构目录（/x64/、/arm64/ 等）
                            val archPattern = "/$archDir/"
                            if (!entry.name.contains(archPattern)) continue

                            val libName = entry.name.substringAfterLast("/")

                            val dstFile = File(nativesDir, libName)
                            if (dstFile.exists() && dstFile.length() == entry.size) continue

                            jar.getInputStream(entry).use { input ->
                                dstFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            dstFile.setExecutable(true, false)
                            dstFile.setReadable(true, false)
                            extracted++
                        }
                    }
                } catch (e: Exception) {
                    log("从 ${jarFile.name} 提取失败: ${e.message}\n")
                }
            }

            // 同时从 classpath 中非 LWJGL 的 natives jar 提取（如 JNA）
            // 查找 jna natives jar
            val jnaNativesJar = File(librariesDir, "net/java/dev/jna/jna/5.17.0/jna-5.17.0.jar")
            if (jnaNativesJar.exists() && jnaNativesJar.length() > 1024) {
                try {
                    JarFile(jnaNativesJar).use { jar ->
                        val jnaEntries = when (targetAbi) {
                            "arm64-v8a" -> listOf(
                                "com/sun/jna/android-aarch64/libjnidispatch.so",
                                "com/sun/jna/linux-aarch64/libjnidispatch.so"
                            )
                            "x86_64" -> listOf(
                                "com/sun/jna/android-x86-64/libjnidispatch.so",
                                "com/sun/jna/linux-x86-64/libjnidispatch.so",
                                "com/sun/jna/linux-amd64/libjnidispatch.so"
                            )
                            "armeabi-v7a" -> listOf(
                                "com/sun/jna/android-arm/libjnidispatch.so",
                                "com/sun/jna/linux-arm/libjnidispatch.so"
                            )
                            "x86" -> listOf(
                                "com/sun/jna/android-x86/libjnidispatch.so",
                                "com/sun/jna/linux-x86/libjnidispatch.so"
                            )
                            else -> emptyList()
                        }
                        for (name in jnaEntries) {
                            val entry = jar.getJarEntry(name) ?: continue
                            val dstFile = File(nativesDir, "libjnidispatch.so")
                            if (dstFile.exists() && dstFile.length() == entry.size) break
                            jar.getInputStream(entry).use { input ->
                                dstFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            dstFile.setExecutable(true, false)
                            dstFile.setReadable(true, false)
                            extracted++
                            break
                        }
                    }
                } catch (e: Exception) {
                    log("从 JNA jar 提取失败: ${e.message}\n")
                }
            }

            log("从 LWJGL natives jar 提取: $extracted 个文件 ($archDir)\n")
        } catch (e: Exception) {
            log("提取 LWJGL natives 失败: ${e.message}\n")
        }
        return extracted
    }

    /**
     * 部署 glibc 兼容 stub 库。
     *
     * LWJGL 官方 natives（从 lwjgl-*-natives-linux.jar 提取）是用 glibc 工具链编译的，
     * 其 ELF 的 DT_NEEDED 段硬性依赖 libdl.so.2、libpthread.so.0、libm.so.6、libgcc_s.so.1、
 * libstdc++.so.6 等版本化库名，而 Android bionic libc 没有这些版本化库名，
     * 导致 Android 动态链接器报 "library not found" 错误。
     *
     * 本方法读取 libglibc_stub.so（一个最小的 ELF 共享库），复制为各 glibc 版本化库名。
     * Android 动态链接器加载这些 stub 后，实际符号（dlopen、pthread_create、malloc 等）
     * 会从已加载的系统 libc.so / libm.so / libdl.so 解析。
     */
    private fun deployGlibcStubLibs(nativesDir: File): Int {
        var deployed = 0
        try {
            // 优先从 natives 目录读取 libglibc_stub.so（extractNativesFromApk 已提取）
            var stubSrc = File(nativesDir, "libglibc_stub.so")
            // 回退：从 APK 的 nativeLibraryDir 读取
            if (!stubSrc.exists() || stubSrc.length() < 512) {
                stubSrc = File(context.applicationInfo.nativeLibraryDir, "libglibc_stub.so")
            }
            // 回退：从 APK 文件中提取
            if (!stubSrc.exists() || stubSrc.length() < 512) {
                val apkFile = File(context.applicationInfo.sourceDir)
                if (apkFile.exists()) {
                    try {
                        val packagedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
                        val targetAbi = Build.SUPPORTED_ABIS?.firstOrNull { it in packagedAbis } ?: "arm64-v8a"
                        java.util.zip.ZipFile(apkFile).use { zip ->
                            val entry = zip.getEntry("lib/$targetAbi/libglibc_stub.so")
                            if (entry != null && entry.size > 512) {
                                val tmpFile = File(nativesDir, "libglibc_stub.so")
                                zip.getInputStream(entry).use { input ->
                                    tmpFile.outputStream().use { output -> input.copyTo(output) }
                                }
                                tmpFile.setExecutable(true, false)
                                tmpFile.setReadable(true, false)
                                stubSrc = tmpFile
                            }
                        }
                    } catch (e: Exception) {
                        log("从 APK 提取 libglibc_stub.so 失败: ${e.message}\n")
                    }
                }
            }
            if (!stubSrc.exists() || stubSrc.length() < 512) {
                log("libglibc_stub.so 不存在或过小: ${stubSrc.absolutePath} (exists=${stubSrc.exists()}, size=${stubSrc.length()})\n")
                return 0
            }

            // glibc 版本化库名列表，这些库在 Android bionic libc 中没有对应版本
            // 但 glibc 编译的 LWJGL natives 的 DT_NEEDED 段硬性依赖这些库名
            val glibcLibNames = listOf(
                "libdl.so.2",          // dlopen/dlclose/dlsym/dlerror/dladdr (Android: libc.so)
                "libpthread.so.0",     // pthread_create/pthread_mutex_lock 等 (Android: libc.so)
                "libm.so.6",           // sin/cos/floor 等 math 函数 (Android: libm.so)
                "libgcc_s.so.1",       // GCC 运行时库 (__stack_chk_fail 等)
                "libstdc++.so.6",      // C++ 标准库 (Android: libc++.so)
                "libc.so.6",           // glibc 主库 (Android: libc.so，符号会从系统 libc.so 解析)
                "ld-linux-x86-64.so.2",// glibc 动态链接器 (Android: linker64)
                "ld-linux-aarch64.so.1",// glibc 动态链接器 (Android: linker64)
                "ld-linux-armhf.so.3"  // glibc 动态链接器 (Android: linker)
            )

            for (libName in glibcLibNames) {
                val dstFile = File(nativesDir, libName)
                if (dstFile.exists() && dstFile.length() == stubSrc.length()) {
                    continue
                }
                try {
                    stubSrc.inputStream().use { input ->
                        dstFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    dstFile.setExecutable(true, false)
                    dstFile.setReadable(true, false)
                    deployed++
                } catch (e: Exception) {
                    log("部署 glibc stub $libName 失败: ${e.message}\n")
                }
            }

            log("部署 glibc stub 库: $deployed 个文件 (源: libglibc_stub.so ${stubSrc.length()} bytes)\n")
        } catch (e: Exception) {
            log("部署 glibc stub 库失败: ${e.message}\n")
        }
        return deployed
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

        if (!alreadyExists) {
            try {
                val jnaJar = File(jnaRuntimeDir, "jna.jar")
                if (jnaJar.exists()) {
                    JarFile(jnaJar).use { jar ->
                        val targetAbi = Build.SUPPORTED_ABIS?.firstOrNull { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") } ?: "arm64-v8a"
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

            // 2. lwjgl.jar
            val lwjglDir = File(FCLPath.LWJGL_DIR)
            lwjglDir.mkdirs()
            if (!File(lwjglDir, "lwjgl.jar").exists() || File(lwjglDir, "lwjgl.jar").length() < 1024) {
                deployed += copyAssetIfMissing("app_runtime/lwjgl/lwjgl.jar", File(lwjglDir, "lwjgl.jar"))
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
