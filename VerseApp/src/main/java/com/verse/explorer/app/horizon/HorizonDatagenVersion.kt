package com.verse.explorer.app.horizon

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File

/**
 * 把「跑官方数据生成器」伪装成一个普通的 Minecraft 版本。
 *
 * 为什么这么绕：Android 禁止 exec 私有目录的可执行文件，而启动器**已经验证过**能跑 Java 的路径
 * 只有正常启动链路（FCLauncher → dlopen libjvm → JNI 建 JVM）。所以不去另造 JVM 启动器，
 * 而是造一个 `inheritsFrom: 26.3` 的版本：继承 26.3 的全部库与资源，只把 mainClass 换成
 * `net.minecraft.data.Main`、参数换成 `--reports --output out`，用同一条链路跑。
 */
object HorizonDatagenVersion {

    const val VERSION_ID = "horizon-datagen"
    /** 包装类：丢弃启动器注入的游戏参数，只把 --reports/--output 传给数据生成器 */
    const val WRAPPER_MAIN = "verse.horizon.DatagenWrapper"
    /** 包装 jar 在 assets 里的名字，运行期拷进 libraries 后加入 classpath */
    const val WRAPPER_ASSET = "horizon-datagen-wrapper.jar"
    const val WRAPPER_LIB_PATH = "libraries/verse/horizon/datagen-wrapper/1.0/datagen-wrapper-1.0.jar"
    const val PARENT_ID = "26.3"

    /**
     * 由 jar 在 libraries 下的相对路径反推 Maven 坐标。
     *
     * `libraries/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar`
     *   → `com.google.code.gson:gson:2.13.2`
     * 反推失败返回 null（调用方跳过该库）。
     */
    fun mavenName(relativePath: String): String? {
        val path = relativePath.replace('\\', '/').removePrefix("libraries/")
        val segments = path.split('/')
        if (segments.size < 3) return null
        val file = segments.last()
        if (!file.endsWith(".jar")) return null
        val version = segments[segments.size - 2]
        val artifact = segments[segments.size - 3]
        val group = segments.dropLast(3).joinToString(".")
        if (group.isBlank() || artifact.isBlank() || version.isBlank()) return null
        return "$group:$artifact:$version"
    }

    /**
     * 生成版本 JSON。
     *
     * @param libraryRelativePaths 已落到 libraries 目录的 jar 相对路径（含 server jar）
     */
    fun buildVersionJson(
        libraryRelativePaths: List<String>,
        parentId: String? = PARENT_ID,
        outputDir: String? = null
    ): JsonObject {
        val libraries = JsonArray()
        for (rel in libraryRelativePaths) {
            val name = if (rel.contains("net/minecraft/server/")) {
                "net.minecraft:server:$PARENT_ID"
            } else {
                mavenName(rel) ?: continue
            }
            libraries.add(JsonObject().apply {
                addProperty("name", name)
                add("downloads", JsonObject().apply {
                    add("artifact", JsonObject().apply {
                        addProperty("path", rel.removePrefix("libraries/"))
                        // 文件已存在时安装器不会下载；给个官方地址只为满足字段
                        addProperty("url", "https://libraries.minecraft.net/" + rel.removePrefix("libraries/"))
                    })
                })
            })
        }
        return JsonObject().apply {
            addProperty("id", VERSION_ID)
            // 父版本存在时继承它的库与资源；找不到就退化成独立版本（只靠下面这些依赖）
            if (parentId != null) addProperty("inheritsFrom", parentId)
            addProperty("mainClass", WRAPPER_MAIN)
            if (parentId == null) {
                add("javaVersion", JsonObject().apply {
                    addProperty("component", "jdk")
                    addProperty("majorVersion", 25)
                })
            }
            addProperty("type", "release")
            add("arguments", JsonObject().apply {
                // 真正传给数据生成器的参数由包装类决定；这里只用来注入输出目录的绝对路径
                add("game", JsonArray())
                if (outputDir != null) {
                    add("jvm", JsonArray().apply {
                        add("-Dverse.datagen.output=$outputDir")
                    })
                }
            })
            add("libraries", libraries)
        }
    }

    /** 写出 versions/horizon-datagen/horizon-datagen.json */
    fun writeVersionFile(versionsDir: File, json: JsonObject): File {
        val dir = File(versionsDir, VERSION_ID).apply { mkdirs() }
        val file = File(dir, "$VERSION_ID.json")
        file.writeText(json.toString())
        return file
    }
}
