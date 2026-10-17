package com.verse.explorer.launcher.launch

import android.content.Context
import android.util.Log
import java.io.File

internal class ClasspathBuilder(private val context: Context) {

    companion object {
        private const val TAG = "MinecraftLauncher"
    }

    fun buildFclClasspath(config: LaunchConfiguration, runtimeDir: File): String {
        val classpath = mutableListOf<String>()

        // 定制 LWJGL 模块 jar 集始终排在 classpath 最前面，遮蔽 MC 自带的官方 LWJGL。
        // 多 jar 套装（core/glfw/opengl/stb/vma/spvc/shaderc...）与 patched liblwjgl*.so 配对。
        // lwjgl-glfw.jar 提供 glfwGetMonitorName（MC 1.19.4+/26.x Monitor 必需）。
        val lwjglDir = File(runtimeDir, "lwjgl")
        val lwjglJars = lwjglDir.listFiles()?.filter { it.name.endsWith(".jar") && it.length() > 0 }?.sortedBy { it.name }
        if (!lwjglJars.isNullOrEmpty()) {
            lwjglJars.forEach { classpath.add(it.absolutePath) }
        } else {
            val lwjgl3Jar = File(runtimeDir, "lwjgl3/lwjgl.jar")
            if (lwjgl3Jar.exists() && lwjgl3Jar.length() > 1024) {
                classpath.add(lwjgl3Jar.absolutePath)
            } else {
                Log.w(TAG, "LWJGL override JARs not found at ${lwjglDir.absolutePath} or ${lwjgl3Jar.absolutePath}")
            }
        }

        // 版本 JAR
        val versionJar = findVersionJar(config)
        if (versionJar != null) {
            classpath.add(versionJar.absolutePath)
            println("[buildFclClasspath] versionJar added: ${versionJar.absolutePath} (${versionJar.length()} bytes)")
        } else {
            println("[buildFclClasspath] WARNING: versionJar is null! Not added to classpath.")
        }

        // 图书馆 JAR — 按顺序添加（过滤所有 native JARs，保留 API JARs）
        config.version.libraries.forEach { library ->
            // 跳过所有 natives jar（格式如 org.lwjgl:lwjgl:3.4.1:natives-linux）
            // LWJGL 3.4.1 发现有 natives jar 在 classpath 中会直接走 SharedLibraryLoader，
            // 不再查 java.library.path，导致在 Android 上提取失败
            if (library.name.contains(":natives-")) return@forEach
            library.downloads?.artifact?.let { artifact ->
                val libFile = File(config.librariesDir, artifact.path)
                if (libFile.exists() && libFile.length() > 0) {
                    classpath.add(libFile.absolutePath)
                }
            }
        }

        // JNA jar（runtime/jna）
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

        // CacioCavallo JAR（runtime）
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

    fun findVersionJar(config: LaunchConfiguration): File? {
        val versionDir = File(config.gameDir, "versions/${config.version.id}")
        val exactJar = File(versionDir, "${config.version.id}.jar")
        println("[findVersionJar] gameDir=${config.gameDir.absolutePath}, versionId=${config.version.id}")
        println("[findVersionJar] versionDir=${versionDir.absolutePath} (exists=${versionDir.exists()})")
        println("[findVersionJar] exactJar=${exactJar.absolutePath} (exists=${exactJar.exists()}, size=${if (exactJar.exists()) exactJar.length() else 0})")
        if (exactJar.exists() && exactJar.length() > 1024) return exactJar

        val allFiles = versionDir.listFiles()?.toList() ?: emptyList()
        println("[findVersionJar] all files in versionDir: ${allFiles.map { "${it.name}(${it.length()})" }}")
        val jars = allFiles.filter { it.name.endsWith(".jar") && it.length() > 1024 }
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
                // Check "jar" field (version JSON may specify a different jar version)
                val jarMatch = Regex("\"jar\"\\s*:\\s*\"([^\"]+)\"").find(jsonText)
                println("[findVersionJar] jar field=${jarMatch?.groupValues?.get(1)}")
                if (jarMatch != null) {
                    val jarId = jarMatch.groupValues[1]
                    val jarDir = File(config.gameDir, "versions/$jarId")
                    val jarFile = File(jarDir, "$jarId.jar")
                    println("[findVersionJar] jarFile=${jarFile.absolutePath} (exists=${jarFile.exists()})")
                    if (jarFile.exists() && jarFile.length() > 1024) return jarFile
                }
                // Check "inheritsFrom" field
                val inheritMatch = Regex("\"inheritsFrom\"\\s*:\\s*\"([^\"]+)\"").find(jsonText)
                val parentId = inheritMatch?.groupValues?.get(1)
                println("[findVersionJar] inheritsFrom=$parentId")
                if (parentId != null) {
                    val parentDir = File(config.gameDir, "versions/$parentId")
                    val parentJar = File(parentDir, "$parentId.jar")
                    println("[findVersionJar] parentJar=${parentJar.absolutePath} (exists=${parentJar.exists()})")
                    if (parentJar.exists() && parentJar.length() > 1024) return parentJar
                    // Also check parent's "jar" field
                    val parentJson = File(parentDir, "$parentId.json")
                    if (parentJson.exists()) {
                        val parentText = parentJson.readText()
                        val parentJarMatch = Regex("\"jar\"\\s*:\\s*\"([^\"]+)\"").find(parentText)
                        if (parentJarMatch != null) {
                            val parentJarId = parentJarMatch.groupValues[1]
                            val parentJarDir = File(config.gameDir, "versions/$parentJarId")
                            val parentJarFile = File(parentJarDir, "$parentJarId.jar")
                            println("[findVersionJar] parent jar field=$parentJarId, jarFile=${parentJarFile.absolutePath} (exists=${parentJarFile.exists()})")
                            if (parentJarFile.exists() && parentJarFile.length() > 1024) return parentJarFile
                        }
                    }
                }
                // Check if downloads.client exists in JSON
                val hasDownloads = jsonText.contains("\"downloads\"")
                val hasClient = jsonText.contains("\"client\"")
                println("[findVersionJar] JSON has downloads=$hasDownloads, has client=$hasClient")
            } catch (e: Exception) {
                println("[findVersionJar] ERROR reading JSON: ${e.message}")
            }
        } else {
            println("[findVersionJar] JSON file not found: ${jsonFile.absolutePath}")
        }
        println("[findVersionJar] WARNING: version JAR not found!")
        return null
    }

    fun getVersionJar(config: LaunchConfiguration): File {
        val exactJar = File(config.gameDir, "versions/${config.version.id}/${config.version.id}.jar")
        if (exactJar.exists() && exactJar.length() > 1024) return exactJar
        val found = findVersionJar(config)
        return found ?: exactJar
    }
}
