package com.verse.explorer.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verse.explorer.core.mod.ModInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ModsUiState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val sortBy: SortMode = SortMode.NAME
)

enum class SortMode {
    NAME, ENABLED, DATE
}

class ModsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val modsDir = File(application.getExternalFilesDir(null), "minecraft/mods")
    
    private val _uiState = MutableStateFlow(ModsUiState())
    val uiState: StateFlow<ModsUiState> = _uiState.asStateFlow()
    
    private val _mods = MutableStateFlow<List<ModInfo>>(emptyList())
    val mods: StateFlow<List<ModInfo>> = _mods.asStateFlow()
    
    init {
        loadMods()
    }
    
    fun loadMods() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            if (!modsDir.exists()) {
                modsDir.mkdirs()
            }
            
            val loadedMods = mutableListOf<ModInfo>()
            
            modsDir.listFiles()?.filter { 
                it.extension == "jar" || it.extension == "disabled"
            }?.forEach { file ->
                val enabled = file.extension == "jar"
                val name = if (enabled) file.nameWithoutExtension else file.nameWithoutExtension
                val actualFile = if (enabled) file else File(file.parent, file.nameWithoutExtension)
                
                loadedMods.add(
                    ModInfo(
                        name = name,
                        version = "Unknown",
                        description = "",
                        authors = emptyList(),
                        fileName = file.name,
                        enabled = enabled
                    )
                )
            }
            
            _mods.value = sortMods(loadedMods, _uiState.value.sortBy)
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
    
    fun toggleMod(fileName: String, enable: Boolean) {
        val file = File(modsDir, fileName)
        if (!file.exists()) return
        
        val newFile = if (enable) {
            File(modsDir, file.nameWithoutExtension)
        } else {
            File(modsDir, "${file.nameWithoutExtension}.disabled")
        }
        
        if (file.renameTo(newFile)) {
            loadMods()
        }
    }
    
    fun deleteMod(fileName: String) {
        val file = File(modsDir, fileName)
        if (file.exists()) {
            file.delete()
            loadMods()
        }
    }
    
    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }
    
    fun setSortMode(mode: SortMode) {
        _uiState.value = _uiState.value.copy(sortBy = mode)
        _mods.value = sortMods(_mods.value, mode)
    }
    
    private fun sortMods(mods: List<ModInfo>, mode: SortMode): List<ModInfo> {
        return when (mode) {
            SortMode.NAME -> mods.sortedBy { it.name.lowercase() }
            SortMode.ENABLED -> mods.sortedByDescending { it.enabled }
            SortMode.DATE -> mods.sortedByDescending { it.fileName }
        }
    }
}
