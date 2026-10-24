package com.verse.explorer.core.install

import com.google.gson.JsonObject

/**
 * 版本清单的库筛选：按 libraries[].rules 判断当前平台是否允许，
 * 并归一化出可下载的 artifact —— 缺 downloads 时按 maven 坐标拼出路径与地址。
 * 纯函数实现，不依赖安装器状态。
 */
internal object LibraryFilter {

    fun filterValidLibraries(versionJson: JsonObject): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        val libsArray = versionJson.get("libraries")?.asJsonArray ?: return result

        for (element in libsArray) {
            val lib = element.asJsonObject
            val name = lib.get("name")?.asString ?: ""

            val rules = lib.get("rules")?.asJsonArray
            if (rules != null) {
                val allowed = evaluateRules(rules.map { it.asJsonObject })
                if (!allowed) continue
            }

            val downloads = lib.get("downloads")?.asJsonObject
            val artifact = downloads?.get("artifact")?.asJsonObject

            if (artifact != null && artifact.has("url") && artifact.has("path")) {
                result.add(downloads)
            } else if (lib.has("name")) {
                val nameParts = name.split(":")
                if (nameParts.size >= 3) {
                    val group = nameParts[0].replace(".", "/")
                    val artifactName = nameParts[1]
                    val version = nameParts[2]
                    val basePath = "$group/$artifactName/$version/$artifactName-$version"
                    val libUrl = lib.get("url")?.asString
                    val jarPath = "$basePath.jar"
                    if (libUrl != null) {
                        val fakeDownloads = JsonObject()
                        val fakeArtifact = JsonObject()
                        fakeArtifact.addProperty("path", jarPath)
                        fakeArtifact.addProperty("url", "$libUrl/$jarPath")
                        fakeDownloads.add("artifact", fakeArtifact)
                        result.add(fakeDownloads)
                    }
                }
            }
        }

        return result
    }

    /** 判断一组 rules 在当前平台是否放行（空规则视为放行） */
    fun evaluateRules(rules: List<JsonObject>): Boolean {
        if (rules.isEmpty()) return true

        for (rule in rules) {
            val action = rule.get("action")?.asString ?: continue
            val os = rule.get("os")?.asJsonObject

            var osMatch = true
            if (os != null) {
                val osName = os.get("name")?.asString ?: ""
                osMatch = osName.isEmpty() || osName == "linux"
            }

            if (action == "allow") {
                if (osMatch) return true
            } else {
                if (osMatch) return false
            }
        }

        return false
    }
}
