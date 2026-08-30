package com.verse.explorer.launcher.launch

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream

internal class GameArgsParser(private val context: Context) {

    companion object {
        private const val TAG = "MinecraftLauncher"
    }

    fun parseGameArgsFromVersionJson(versionDir: File, mcVersion: String): List<String> {
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
                // 合并父版本(原版)与当前版本(加载器)的参数，并按 key 去重。
                // 原版 arguments.game 含 --username/--gameDir/--width/--height 等，
                // 加载器 JSON 的 arguments 也会继承到这些键；只保留各自的独占键，
                // 否则 ModLauncher 收到重复 --gameDir 触发 jopt MultipleArgumentsForOptionException。
                return dedupeArgsByKey(parentArgs, currentArgs)
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

    /** 按参数键合并去重：后出现的键值优先覆盖（加载器独占参数如 --launchTarget 保留）。 */
    private fun dedupeArgsByKey(vararg lists: List<String>): List<String> {
        val result = mutableListOf<String>()
        val seenKeys = mutableSetOf<String>()
        for (list in lists) {
            var i = 0
            while (i < list.size) {
                val token = list[i]
                if (token.startsWith("--")) {
                    if (seenKeys.add(token)) {
                        result.add(token)
                        if (i + 1 < list.size && !list[i + 1].startsWith("--")) {
                            result.add(list[i + 1])
                            i++
                        }
                    } else {
                        // key 已存在：跳过其值，避免重复
                        if (i + 1 < list.size && !list[i + 1].startsWith("--")) {
                            i++
                        }
                    }
                } else {
                    result.add(token)
                }
                i++
            }
        }
        return result
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

    fun buildFclGameArguments(config: LaunchConfiguration, gameVersion: String?): List<String> {
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
            // 收集 base args 中已存在的参数键（如 --username/--gameDir），
            // 版本 JSON 的 arguments.game 会带回与 base 完全相同的键（如
            // --username/--version/--gameDir/--width/--height），必须成对跳过，
            // 否则 ModLauncher/jopt-simple 报 MultipleArgumentsForOptionException。
            val existingKeys = mutableSetOf<String>()
            var i = 0
            while (i < args.size) {
                if (args[i].startsWith("--")) existingKeys.add(args[i])
                i++
            }
            var j = 0
            while (j < parsedArgs.size) {
                val token = parsedArgs[j]
                if (token.startsWith("--")) {
                    if (token !in existingKeys) {
                        args.add(token)
                        if (j + 1 < parsedArgs.size && !parsedArgs[j + 1].startsWith("--")) {
                            args.add(parsedArgs[j + 1])
                            j++
                        } else if (j + 1 < parsedArgs.size && parsedArgs[j + 1].startsWith("--")) {
                            // 无值参数（如 --demo），仅加入键本身
                        }
                    } else {
                        // 键已存在：若后面跟着一个非 -- 的值（该值的键上文已生成），
                        // 跳过这对参数，避免 jopt-simple 收到重复键值
                        if (j + 1 < parsedArgs.size && !parsedArgs[j + 1].startsWith("--")) {
                            j++ // 跳过该重复键的值
                        }
                    }
                }
                j++
            }
        }

        // 最终兜底：整体按 key 去重。无论重复来自 base/parsedArgs 的何种组合，
        // ModLauncher/jopt-simple 都要求每个 option 只出现一次，否则抛
        // MultipleArgumentsForOptionException（如 --gameDir 出现两次）。
        return dedupeArgsByKey(args)
    }

    fun compareGameVersion(a: String, b: String): Int {
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

    /**
     * 按 MC 版本生成一份最简 log4j2 配置文件，返回其路径。
     * 目前复用同一份配置；后续可按版本拆分。
     */
    fun ensureLog4j2Config(config: LaunchConfiguration): File {
        val log4j2Dir = File(context.filesDir, "log4j2")
        log4j2Dir.mkdirs()
        val configFile = File(log4j2Dir, "log4j2.xml")
        if (!configFile.exists() || configFile.length() == 0L) {
            try {
                configFile.writeText(
                    """<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
    <Appenders>
        <Console name="SysOut" target="SYSTEM_OUT">
            <PatternLayout pattern="[%d{HH:mm:ss}] [%t/%level]: %msg%n" />
        </Console>
    </Appenders>
    <Loggers>
        <Root level="info">
            <AppenderRef ref="SysOut" />
        </Root>
    </Loggers>
</Configuration>
"""
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write log4j2 config: ${e.message}")
            }
        }
        return configFile
    }

    fun deployGameAsset(context: Context, assetName: String, targetDir: File): File? {
        if (!targetDir.exists()) targetDir.mkdirs()
        val targetFile = File(targetDir, assetName)

        var assetSize = -1L
        try {
            context.assets.openFd("game/$assetName").use { afd ->
                assetSize = afd.length
            }
        } catch (_: Exception) {}

        if (targetFile.exists() && targetFile.length() == assetSize && assetSize > 1024) {
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
            Log.d(TAG, "Deployed game asset: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            targetFile
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deploy game asset $assetName: ${e.message}")
            null
        }
    }
}
