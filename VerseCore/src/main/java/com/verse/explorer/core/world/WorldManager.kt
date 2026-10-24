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

/**
 * 存档名与备份文件名的互转。
 *
 * 备份文件名格式固定为 `<世界名>_<13 位毫秒时间戳>.zip`。世界名本身可能含下划线
 * （如 `New_World`），因此不能用 `substringBeforeLast('_')` 反推——必须按末尾的
 * 13 位时间戳切分，否则会把 `New_World_1730...` 解析成 `New_World` 之外的错误结果。
 */
internal object WorldNames {

    private val BACKUP_ID = Regex("^(.*)_(\\d{13})$")

    fun worldNameOfBackup(backupId: String): String =
        BACKUP_ID.matchEntire(backupId)?.groupValues?.get(1)
            ?: backupId.substringBeforeLast('_', backupId)

    fun backupId(worldName: String, timestamp: Long): String = "${worldName}_$timestamp"
}

class WorldManager(private val savesDir: File, private val backupsDir: File) {

    init {
        if (!savesDir.exists()) savesDir.mkdirs()
        if (!backupsDir.exists()) backupsDir.mkdirs()
    }

    /** 存档列表，按最后修改时间倒序（最近玩过的在最上面） */
    fun getWorlds(): List<WorldInfo> {
        if (!savesDir.exists()) return emptyList()

        return savesDir.listFiles()
            ?.filter { it.isDirectory && File(it, "level.dat").exists() }
            ?.map { folder -> parseWorldInfo(folder) }
            ?.sortedByDescending { it.lastPlayed }
            ?: emptyList()
    }

    /**
     * 解析单个存档目录。世界名取目录名；游戏模式/难度/种子需要解析 level.dat 的 NBT，
     * 当前未实现，因此保持枚举默认值，界面不要展示这些字段以免误导。
     */
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

    /** 把整个世界目录打成 zip 存进备份目录 */
    fun createBackup(worldName: String, description: String = ""): BackupInfo? {
        val worldDir = File(savesDir, worldName)
        if (!worldDir.exists()) return null

        val timestamp = System.currentTimeMillis()
        val backupId = WorldNames.backupId(worldName, timestamp)
        val backupFile = File(backupsDir, "$backupId.zip")

        runCatching {
            zipDirectory(worldDir, backupFile)
        }.onFailure {
            backupFile.delete()
            return null
        }

        return BackupInfo(
            id = backupId,
            worldName = worldName,
            timestamp = timestamp,
            size = backupFile.length(),
            description = description
        )
    }

    /** 用备份覆盖当前存档目录（调用方必须先弹二次确认） */
    fun restoreBackup(backupId: String): Boolean {
        val backupFile = File(backupsDir, "$backupId.zip")
        if (!backupFile.exists()) return false

        val worldName = WorldNames.worldNameOfBackup(backupId)
        val worldDir = File(savesDir, worldName)

        if (worldDir.exists()) {
            worldDir.deleteRecursively()
        }
        worldDir.mkdirs()
        return unzipTo(backupFile, worldDir)
    }

    fun deleteBackup(backupId: String): Boolean {
        val backupFile = File(backupsDir, "$backupId.zip")
        return backupFile.exists() && backupFile.delete()
    }

    /** 备份列表，按时间倒序 */
    fun getBackups(): List<BackupInfo> {
        if (!backupsDir.exists()) return emptyList()

        return backupsDir.listFiles()
            ?.filter { it.extension == "zip" }
            ?.map { file ->
                val id = file.nameWithoutExtension
                BackupInfo(
                    id = id,
                    worldName = WorldNames.worldNameOfBackup(id),
                    timestamp = file.lastModified(),
                    size = file.length()
                )
            }
            ?.sortedByDescending { it.timestamp }
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
        if (newName.isBlank() || newName.contains('/') || newName.contains('\\')) return false

        return oldDir.renameTo(newDir)
    }

    fun exportWorld(worldName: String, targetFile: File): Boolean {
        val worldDir = File(savesDir, worldName)
        if (!worldDir.exists()) return false
        return runCatching { zipDirectory(worldDir, targetFile) }.isSuccess
    }

    /**
     * 导入 zip 世界。
     *
     * 目标目录已存在时：
     * - `overwrite = true` 直接覆盖（调用方必须先弹二次确认）；
     * - `overwrite = false`（默认）自动改用 `名字 (2)`、`名字 (3)`…，绝不静默丢数据。
     *
     * @return 实际写入的目录名；失败返回 null
     */
    fun importWorld(sourceFile: File, overwrite: Boolean = false): String? {
        if (!sourceFile.exists()) return null

        val baseName = sourceFile.nameWithoutExtension
        val worldName = when {
            overwrite -> baseName
            !File(savesDir, baseName).exists() -> baseName
            else -> uniqueWorldName(baseName)
        }
        val worldDir = File(savesDir, worldName)

        if (worldDir.exists()) {
            worldDir.deleteRecursively()
        }
        worldDir.mkdirs()

        val ok = unzipTo(sourceFile, worldDir)
        if (!ok) {
            worldDir.deleteRecursively()
            return null
        }
        // 解压后必须存在 level.dat，否则不是一个有效存档
        if (!File(worldDir, "level.dat").exists()) {
            worldDir.deleteRecursively()
            return null
        }
        return worldName
    }

    /** 生成不冲突的存档目录名：`名字 (2)`、`名字 (3)`… */
    fun uniqueWorldName(baseName: String): String {
        if (!File(savesDir, baseName).exists()) return baseName
        var index = 2
        while (index < 1000) {
            val candidate = "$baseName ($index)"
            if (!File(savesDir, candidate).exists()) return candidate
            index++
        }
        return "$baseName (${System.currentTimeMillis()})"
    }

    fun worldDirOf(worldName: String): File = File(savesDir, worldName)

    private fun zipDirectory(sourceDir: File, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        ZipOutputStream(targetFile.outputStream()).use { zipOut ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entry = ZipEntry(file.relativeTo(sourceDir).path.replace('\\', '/'))
                    zipOut.putNextEntry(entry)
                    file.inputStream().use { input -> input.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }
    }

    private fun unzipTo(zipFile: File, targetDir: File): Boolean = runCatching {
        ZipInputStream(zipFile.inputStream()).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                // 防止 zip 内的 ../ 路径逃逸到目标目录之外
                if (!file.canonicalPath.startsWith(targetDir.canonicalPath + File.separator)) {
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                    continue
                }
                file.parentFile?.mkdirs()
                if (!entry.isDirectory) {
                    file.outputStream().use { output -> zipIn.copyTo(output) }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }.isSuccess
}
