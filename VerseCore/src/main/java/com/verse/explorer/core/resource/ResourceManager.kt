package com.verse.explorer.core.resource

import java.io.File

data class ResourcePackInfo(
    val name: String,
    val fileName: String,
    val description: String,
    val format: Int,
    val fileSize: Long,
    val enabled: Boolean = false
)

data class ShaderPackInfo(
    val name: String,
    val fileName: String,
    val fileSize: Long,
    val enabled: Boolean = false
)

class ResourceManager(private val resourcePacksDir: File, private val shaderPacksDir: File) {
    
    init {
        if (!resourcePacksDir.exists()) resourcePacksDir.mkdirs()
        if (!shaderPacksDir.exists()) shaderPacksDir.mkdirs()
    }
    
    fun getResourcePacks(): List<ResourcePackInfo> {
        if (!resourcePacksDir.exists()) return emptyList()
        
        return resourcePacksDir.listFiles()
            ?.filter { it.extension == "zip" }
            ?.map { file -> parseResourcePack(file) }
            ?: emptyList()
    }
    
    private fun parseResourcePack(file: File): ResourcePackInfo {
        return ResourcePackInfo(
            name = file.nameWithoutExtension,
            fileName = file.name,
            description = "Resource pack",
            format = 0,
            fileSize = file.length()
        )
    }
    
    fun getShaderPacks(): List<ShaderPackInfo> {
        if (!shaderPacksDir.exists()) return emptyList()
        
        return shaderPacksDir.listFiles()
            ?.filter { it.extension == "zip" }
            ?.map { file ->
                ShaderPackInfo(
                    name = file.nameWithoutExtension,
                    fileName = file.name,
                    fileSize = file.length()
                )
            }
            ?: emptyList()
    }
    
    fun enableResourcePack(fileName: String): Boolean {
        val file = File(resourcePacksDir, fileName)
        return file.exists()
    }
    
    fun disableResourcePack(fileName: String) {
        
    }
    
    fun enableShaderPack(fileName: String): Boolean {
        val file = File(shaderPacksDir, fileName)
        return file.exists()
    }
    
    fun disableShaderPack() {
        
    }
    
    fun deleteResourcePack(fileName: String): Boolean {
        val file = File(resourcePacksDir, fileName)
        return file.exists() && file.delete()
    }
    
    fun deleteShaderPack(fileName: String): Boolean {
        val file = File(shaderPacksDir, fileName)
        return file.exists() && file.delete()
    }
    
    fun importResourcePack(sourceFile: File): Boolean {
        if (!sourceFile.exists()) return false
        
        val targetFile = File(resourcePacksDir, sourceFile.name)
        sourceFile.copyTo(targetFile, overwrite = true)
        return true
    }
    
    fun importShaderPack(sourceFile: File): Boolean {
        if (!sourceFile.exists()) return false
        
        val targetFile = File(shaderPacksDir, sourceFile.name)
        sourceFile.copyTo(targetFile, overwrite = true)
        return true
    }
}
