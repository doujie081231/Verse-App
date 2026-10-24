package com.verse.explorer.core.game

import java.io.File

data class GameProfile(
    val id: String,
    val name: String,
    val versionId: String,
    val gameDir: File,
    val javaDir: File? = null,
    val javaArgs: String? = null,
    val gameArgs: String? = null,
    val resolution: Pair<Int, Int>? = null,
    val lastPlayed: Long = 0,
    val iconPath: String? = null
)

data class GameSettings(
    val fullscreen: Boolean = false,
    val width: Int = 854,
    val height: Int = 480,
    val renderDistance: Int = 12,
    val maxFps: Int = 60,
    val vsync: Boolean = true,
    val graphicsMode: Int = 0,
    val ao: Int = 2,
    val mipmapLevels: Int = 4,
    val anisotropicFiltering: Boolean = true
)

class GameProfileManager(private val gameDir: File) {
    
    private val profiles = mutableListOf<GameProfile>()
    
    fun loadProfiles(): List<GameProfile> {
        profiles.clear()
        
        val profilesFile = File(gameDir, "launcher_profiles.json")
        if (!profilesFile.exists()) {
            return profiles.toList()
        }
        
        try {
            val json = profilesFile.readText()
            val jsonObj = org.json.JSONObject(json)
            val profilesArr = jsonObj.optJSONArray("profiles") ?: return profiles.toList()
            for (i in 0 until profilesArr.length()) {
                val p = profilesArr.getJSONObject(i)
                val profile = GameProfile(
                    id = p.optString("id", ""),
                    name = p.optString("name", ""),
                    versionId = p.optString("versionId", ""),
                    gameDir = File(p.optString("gameDir", gameDir.absolutePath)),
                    javaDir = p.optString("javaDir", null)?.let { File(it) },
                    javaArgs = p.optString("javaArgs", null),
                    gameArgs = p.optString("gameArgs", null),
                    resolution = if (p.has("width") && p.has("height")) Pair(p.getInt("width"), p.getInt("height")) else null,
                    lastPlayed = p.optLong("lastPlayed", 0),
                    iconPath = p.optString("iconPath", null)
                )
                profiles.add(profile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return profiles.toList()
    }
    
    fun saveProfiles() {
        try {
            val profilesFile = File(gameDir, "launcher_profiles.json")
            val jsonObj = org.json.JSONObject()
            val profilesArr = org.json.JSONArray()
            for (profile in profiles) {
                val p = org.json.JSONObject()
                p.put("id", profile.id)
                p.put("name", profile.name)
                p.put("versionId", profile.versionId)
                p.put("gameDir", profile.gameDir.absolutePath)
                profile.javaDir?.let { p.put("javaDir", it.absolutePath) }
                profile.javaArgs?.let { p.put("javaArgs", it) }
                profile.gameArgs?.let { p.put("gameArgs", it) }
                profile.resolution?.let { p.put("width", it.first); p.put("height", it.second) }
                p.put("lastPlayed", profile.lastPlayed)
                profile.iconPath?.let { p.put("iconPath", it) }
                profilesArr.put(p)
            }
            jsonObj.put("profiles", profilesArr)
            profilesFile.writeText(jsonObj.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun createProfile(
        name: String,
        versionId: String,
        customGameDir: File? = null
    ): GameProfile {
        val profile = GameProfile(
            id = System.currentTimeMillis().toString(),
            name = name,
            versionId = versionId,
            gameDir = customGameDir ?: gameDir
        )
        profiles.add(profile)
        return profile
    }
    
    fun deleteProfile(profileId: String) {
        profiles.removeAll { it.id == profileId }
        saveProfiles()
    }
    
    fun getProfile(profileId: String): GameProfile? {
        return profiles.find { it.id == profileId }
    }
    
    fun updateProfile(profile: GameProfile) {
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            profiles[index] = profile
        }
    }
}
