package com.verse.explorer.core.data

import android.content.Context
import com.google.gson.Gson
import com.verse.explorer.core.game.GameProfile
import java.io.File

class ProfileRepository(context: Context) {
    
    private val profilesFile = File(context.filesDir, "profiles.json")
    private val gson = Gson()
    
    fun loadProfiles(): List<GameProfile> {
        if (!profilesFile.exists()) return emptyList()
        
        return try {
            val json = profilesFile.readText()
            gson.fromJson(json, Array<GameProfile>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun saveProfiles(profiles: List<GameProfile>) {
        val json = gson.toJson(profiles)
        profilesFile.writeText(json)
    }
    
    fun addProfile(profile: GameProfile) {
        val profiles = loadProfiles().toMutableList()
        profiles.add(profile)
        saveProfiles(profiles)
    }
    
    fun updateProfile(profile: GameProfile) {
        val profiles = loadProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            profiles[index] = profile
            saveProfiles(profiles)
        }
    }
    
    fun deleteProfile(profileId: String) {
        val profiles = loadProfiles().filter { it.id != profileId }
        saveProfiles(profiles)
    }
    
    fun getProfile(profileId: String): GameProfile? {
        return loadProfiles().find { it.id == profileId }
    }
}
