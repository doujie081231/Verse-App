package com.verse.explorer.app.data.version

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.verse.explorer.core.version.Artifact
import com.verse.explorer.core.version.Library
import com.verse.explorer.core.version.LibraryDownloads
import java.io.File

class VersionJsonResolver(private val log: (String) -> Unit) {

    fun resolveVersionJson(gameDir: File, versionId: String): JsonObject? {
        val versionsRootDir = File(gameDir, "versions")
        var jsonFile = File(versionsRootDir, "$versionId/$versionId.json")

        val resolved = mutableSetOf<String>()
        var currentJson: JsonObject? = null

        while (jsonFile.exists() && resolved.add(jsonFile.parentFile?.name ?: "")) {
            val json = try {
                com.google.gson.GsonBuilder().setLenient().create()
                    .fromJson(jsonFile.readText(), JsonObject::class.java)
            } catch (e: Exception) {
                log("解析JSON失败: ${jsonFile.path} - ${e.message}\n")
                break
            }

            if (currentJson == null) {
                currentJson = json
            } else {
                for ((key, value) in json.entrySet()) {
                    if (key == "libraries" && currentJson!!.has("libraries")) {
                        val merged = JsonArray()
                        val childLibs = currentJson!!.getAsJsonArray("libraries")
                        val parentLibs = value.asJsonArray
                        childLibs.forEach { merged.add(it) }
                        val childNames = childLibs.mapNotNull {
                            it.asJsonObject?.get("name")?.asString
                        }.toSet()
                        parentLibs.forEach { lib ->
                            val name = lib.asJsonObject?.get("name")?.asString
                            if (name == null || name !in childNames) {
                                merged.add(lib)
                            }
                        }
                        currentJson!!.add("libraries", merged)
                    } else if (!currentJson!!.has(key)) {
                        currentJson!!.add(key, value)
                    }
                }
            }

            val inheritsFrom = json.get("inheritsFrom")?.asString
            if (inheritsFrom.isNullOrEmpty() || inheritsFrom == versionId) break
            jsonFile = File(versionsRootDir, "$inheritsFrom/$inheritsFrom.json")
        }

        return currentJson
    }

    fun parseLibrariesFromJson(libsArray: JsonArray): List<Library> {
        val libraries = mutableListOf<Library>()

        for (libElement in libsArray) {
            try {
                val lib = libElement.asJsonObject
                val name = lib.get("name")?.asString ?: continue

                val rules = lib.getAsJsonArray("rules")
                if (rules != null) {
                    var libAllowed = true
                    for (ruleElement in rules) {
                        val rule = ruleElement.asJsonObject
                        val action = rule.get("action")?.asString ?: continue
                        val os = rule.getAsJsonObject("os")
                        val features = rule.getAsJsonObject("features")
                        if (features != null) continue
                        if (os != null) {
                            val osName = os.get("name")?.asString ?: ""
                            if (osName == "linux" || osName == "osx" || osName.isEmpty()) {
                                libAllowed = action == "allow"
                            }
                        } else {
                            libAllowed = action == "allow"
                        }
                    }
                    if (!libAllowed) continue
                }

                val downloads = lib.getAsJsonObject("downloads")
                val artifact = downloads?.getAsJsonObject("artifact")

                val artifactObj = if (artifact != null) {
                    Artifact(
                        path = artifact.get("path")?.asString ?: "",
                        sha1 = artifact.get("sha1")?.asString ?: "",
                        size = artifact.get("size")?.asLong ?: 0L,
                        url = artifact.get("url")?.asString ?: ""
                    )
                } else {
                    val url = lib.get("url")?.asString
                    if (url != null && name.count { it == ':' } >= 2) {
                        val parts = name.split(":")
                        val group = parts[0].replace(".", "/")
                        val art = parts[1]
                        val ver = parts[2]
                        val path = "$group/$art/$ver/$art-$ver.jar"
                        Artifact(
                            path = path, url = "${url}$path"
                        )
                    } else null
                }

                val classifiers = mutableMapOf<String, Artifact>()
                val classifiersJson = downloads?.getAsJsonObject("classifiers")
                if (classifiersJson != null) {
                    for ((key, value) in classifiersJson.entrySet()) {
                        try {
                            val c = value.asJsonObject
                            classifiers[key] = Artifact(
                                path = c.get("path")?.asString ?: "",
                                sha1 = c.get("sha1")?.asString ?: "",
                                size = c.get("size")?.asLong ?: 0L,
                                url = c.get("url")?.asString ?: ""
                            )
                        } catch (_: Exception) {}
                    }
                }

                val nativesMap = mutableMapOf<String, String>()
                val nativesJson = lib.getAsJsonObject("natives")
                if (nativesJson != null) {
                    for ((key, value) in nativesJson.entrySet()) {
                        nativesMap[key] = value.asString
                    }
                }

                libraries.add(Library(
                    name = name,
                    downloads = LibraryDownloads(
                        artifact = artifactObj,
                        classifiers = classifiers.ifEmpty { null }
                    ),
                    natives = nativesMap.ifEmpty { null }
                ))
            } catch (_: Exception) {}
        }

        return libraries
    }

    fun findInheritedVersionJar(versionId: String, gameDir: File): File? {
        val versionDir = File(gameDir, "versions/$versionId")
        val jsonFile = File(versionDir, "$versionId.json")
        if (!jsonFile.exists()) return null
        try {
            val jsonText = jsonFile.readText()
            val jarMatch = Regex("\"jar\"\\s*:\\s*\"([^\"]+)\"").find(jsonText)
            val inheritMatch = Regex("\"inheritsFrom\"\\s*:\\s*\"([^\"]+)\"").find(jsonText)
            val parentId = jarMatch?.groupValues?.get(1) ?: inheritMatch?.groupValues?.get(1)
            if (parentId != null) {
                val parentDir = File(gameDir, "versions/$parentId")
                val parentJar = File(parentDir, "$parentId.jar")
                if (parentJar.exists() && parentJar.length() > 1024) return parentJar
                val parentJars = parentDir.listFiles()?.filter {
                    it.name.endsWith(".jar") && it.length() > 1024
                }
                if (parentJars != null && parentJars.isNotEmpty()) {
                    return parentJars.first()
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
