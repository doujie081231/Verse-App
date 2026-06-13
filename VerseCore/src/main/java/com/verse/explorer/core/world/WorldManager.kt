package com.verse.explorer.core.world

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class WorldInfo(
    val name: String,
    val folderName: String,
    val lastPlayed: Long,
    val gameMode: GameMode,
    val difficulty: Difficulty,
    val seed: Long?,
    val size: Long,
    val iconPath: String? = null
)

enum class GameMode(val displayName: String) {
    SURVIVAL("生存模式"),
    CREATIVE("创造模式"),
    ADVENTURE("冒险模式"),
    SPECTATOR("旁观模式")
}

enum class Difficulty(val displayName: String) {
    PEACEFUL("和平"),
    EASY("简单"),
    NORMAL("普通"),
    HARD("困难")
}

data class BackupInfo(
    val id: String,
    val worldName: String,
    val timestamp: Long,
    val size: Long,
    val description: String = ""
)

class WorldManager(private val savesDir: File, private val backupsDir: File) {
    
    init {
        if (!savesDir.exists()) savesDir.mkdirs()
        if (!backupsDir.exists()) backupsDir.mkdirs()
    }
    
    fun getWorlds(): List<WorldInfo> {
        if (!savesDir.exists()) return emptyList()
        
        return savesDir.listFiles()
            ?.filter { it.isDirectory && File(it, "level.dat").exists() }
            ?.map { folder -> parseWorldInfo(folder) }
            ?: emptyList()
    }
    
    private fun parseWorldInfo(folder: File): WorldInfo {
        val levelDat = File(folder, "level.dat")
        val iconFile = File(folder, "icon.png")
        
        return WorldInfo(
            name = folder.name,
            folderName = folder.name,
            lastPlayed = levelDat.lastModified(),
            gameMode = GameMode.SURVIVAL,
            difficulty = Difficulty.NORMAL,
            seed = null,
            size = calculateFolderSize(folder),
            iconPath = if (iconFile.exists()) iconFile.absolutePath else null
        )
    }
    
    private fun calculateFolderSize(folder: File): Long {
        if (!folder.exists()) return 0
        return folder.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }
    
    fun createBackup(worldName: String, description: String = ""): BackupInfo? {
        val worldDir = File(savesDir, worldName)
        if (!worldDir.exists()) return null
        
        val backupId = "${worldName}_${System.currentTimeMillis()}"
        val backupFile = File(backupsDir, "$backupId.zip")
        
        ZipOutputStream(backupFile.outputStream()).use { zipOut ->
            worldDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entry = ZipEntry(file.relativeTo(worldDir).path.replace('\\', '/'))
                    zipOut.putNextEntry(entry)
                    file.inputStream().use { input -> input.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }
        
        return BackupInfo(
            id = backupId,
            worldName = worldName,
            timestamp = System.currentTimeMillis(),
            size = backupFile.length(),
            description = description
        )
    }
    
    fun restoreBackup(backupId: String): Boolean {
        val backupFile = File(backupsDir, "$backupId.zip")
        if (!backupFile.exists()) return false
        
        val worldName = backupId.substringBeforeLast("_")
        val worldDir = File(savesDir, worldName)
        
        if (worldDir.exists()) {
            worldDir.deleteRecursively()
        }
        worldDir.mkdirs()
        
        ZipInputStream(backupFile.inputStream()).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val file = File(worldDir, entry.name)
                file.parentFile?.mkdirs()
                if (!entry.isDirectory) {
                    file.outputStream().use { output -> zipIn.copyTo(output) }
                }
                entry = zipIn.nextEntry
            }
        }
        
        return true
    }
    
    fun deleteBackup(backupId: String): Boolean {
        val backupFile = File(backupsDir, "$backupId.zip")
        return backupFile.exists() && backupFile.delete()
    }
    
    fun getBackups(): List<BackupInfo> {
        if (!backupsDir.exists()) return emptyList()
        
        return backupsDir.listFiles()
            ?.filter { it.extension == "zip" }
            ?.map { file ->
                val name = file.nameWithoutExtension
                val worldName = name.substringBeforeLast("_")
                BackupInfo(
                    id = name,
                    worldName = worldName,
                    timestamp = file.lastModified(),
                    size = file.length()
                )
            }
            ?: emptyList()
    }
    
    fun deleteWorld(worldName: String): Boolean {
        val worldDir = File(savesDir, worldName)
        return worldDir.exists() && worldDir.deleteRecursively()
    }
    
    fun renameWorld(oldName: String, newName: String): Boolean {
        val oldDir = File(savesDir, oldName)
        val newDir = File(savesDir, newName)
        
        if (!oldDir.exists() || newDir.exists()) return false
        
        return oldDir.renameTo(newDir)
    }
    
    fun exportWorld(worldName: String, targetFile: File): Boolean {
        val worldDir = File(savesDir, worldName)
        if (!worldDir.exists()) return false
        
        ZipOutputStream(targetFile.outputStream()).use { zipOut ->
            worldDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entry = ZipEntry(file.relativeTo(worldDir).path.replace('\\', '/'))
                    zipOut.putNextEntry(entry)
                    file.inputStream().use { input -> input.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }
        
        return true
    }
    
    fun importWorld(sourceFile: File): Boolean {
        if (!sourceFile.exists()) return false
        
        val worldName = sourceFile.nameWithoutExtension
        val worldDir = File(savesDir, worldName)
        
        if (worldDir.exists()) {
            worldDir.deleteRecursively()
        }
        worldDir.mkdirs()
        
        ZipInputStream(sourceFile.inputStream()).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val file = File(worldDir, entry.name)
                file.parentFile?.mkdirs()
                if (!entry.isDirectory) {
                    file.outputStream().use { output -> zipIn.copyTo(output) }
                }
                entry = zipIn.nextEntry
            }
        }
        
        return true
    }
}
