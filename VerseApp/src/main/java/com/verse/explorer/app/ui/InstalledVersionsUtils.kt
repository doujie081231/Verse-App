package com.verse.explorer.app.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionType
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun versionTypeLabel(version: MinecraftVersion): String {
    return when (version.type) {
        VersionType.SNAPSHOT -> "快照"
        VersionType.OLD_ALPHA -> "Old Alpha"
        VersionType.OLD_BETA -> "Old Beta"
        else -> "正式版"
    }
}

internal fun settingsLastVersion(): String? = SettingsRepository.getCustomSetting("last_version")

/** 是否为模组版本：版本 json 识别出加载器，或 id 命中加载器/整合包关键词 */
internal fun isModdedVersion(version: MinecraftVersion): Boolean {
    if (!version.loaderHint.isNullOrBlank()) return true
    val lower = version.id.lowercase()
    return lower.contains("fabric") || lower.contains("forge") || lower.contains("neoforge") ||
        lower.contains("optifine") || lower.contains("liteloader") || lower.contains("modpack") || lower.contains("整合")
}

/**
 * 重命名版本目录：目录与 <id>.json 改名，版本 json 内的 id 字段同步更新，
 * 目标名已存在或目录缺失时抛错由调用方提示。
 */
internal fun renameVersion(context: Context, oldId: String, newId: String) {
    val versionsDir = GameConfig.getVersionsDir(GameConfig.getGameDir(context))
    val oldDir = File(versionsDir, oldId)
    val newDir = File(versionsDir, newId)
    require(oldDir.isDirectory) { "版本目录不存在" }
    require(!newDir.exists()) { "目标名称已存在" }
    val invalidChars = Regex("""[\\/:*?"<>|]""")
    require(!invalidChars.containsMatchIn(newId)) { "名称含非法字符" }

    val oldJson = File(oldDir, "$oldId.json")
    if (oldJson.isFile) {
        val text = oldJson.readText()
        val updated = Regex("""("id"\s*:\s*")(${Regex.escape(oldId)})(")""")
            .replace(text) { m -> "${m.groupValues[1]}$newId${m.groupValues[3]}" }
        oldJson.writeText(updated)
        if (!oldJson.renameTo(File(oldDir, "$newId.json"))) {
            oldJson.writeText(text)
            error("版本 json 改名失败")
        }
    }
    if (!oldDir.renameTo(newDir)) {
        error("版本目录改名失败")
    }
}

/** 递归压缩目录（entry 名相对 rootDir） */
internal fun zipDirectory(dir: File, rootDir: File, zip: ZipOutputStream) {
    dir.listFiles()?.forEach { f ->
        if (f.isDirectory) {
            zipDirectory(f, rootDir, zip)
        } else {
            val entryName = f.relativeTo(rootDir).invariantSeparatorsPath
            zip.putNextEntry(ZipEntry(entryName))
            f.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

internal fun toast(context: Context, text: String) {
    android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
}

/** SAF 目录 URI → 共享存储绝对路径；非共享存储目录返回 null */
internal fun treeUriToPath(uri: Uri): String? {
    if (uri.authority != "com.android.externalstorage.documents") return null
    val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val parts = docId.split(":")
    if (parts.size < 2) return null
    val volume = parts[0]
    val relative = parts[1]
    val base = if (volume.equals("primary", ignoreCase = true)) "/storage/emulated/0" else "/storage/$volume"
    return if (relative.isBlank()) base else "$base/$relative"
}

/** 清理计划：下载残留项 + 缺少版本 json 的孤立版本目录 */
internal data class CleanupPlan(
    val residue: List<File>,
    val orphanVersions: List<File>
)

private val RESIDUE_SUFFIXES = listOf(".part", ".tmp", ".download")

internal fun buildCleanupPlan(gameDir: File): CleanupPlan {
    val residue = mutableListOf<File>()
    listOf("modpacks", "mods", "resourcepacks", "shaderpacks", "versions")
        .forEach { collectResidue(File(gameDir, it), residue) }
    val versionsDir = GameConfig.getVersionsDir(gameDir)
    val orphan = versionsDir.listFiles { f -> f.isDirectory }
        ?.filterNot { File(it, "${it.name}.json").isFile }
        ?: emptyList()
    return CleanupPlan(residue, orphan)
}

/** 递归收集下载残留：分块缓存目录与 .part/.tmp/.download 半截文件（跳过 assets/libraries） */
private fun collectResidue(dir: File, out: MutableList<File>) {
    dir.listFiles()?.forEach { f ->
        when {
            f.isDirectory && f.name == ".download_chunks" -> out.add(f)
            f.isDirectory && f.name != "assets" && f.name != "libraries" -> collectResidue(f, out)
            f.isFile && RESIDUE_SUFFIXES.any { f.name.endsWith(it) } -> out.add(f)
        }
    }
}
