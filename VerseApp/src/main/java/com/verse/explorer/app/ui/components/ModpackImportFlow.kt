package com.verse.explorer.app.ui.components

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.install.modpack.ModpackInstallCancelledException
import com.verse.explorer.core.install.modpack.ModpackInstaller
import com.verse.explorer.core.install.modpack.ModpackTaskEvent
import com.verse.explorer.core.version.VersionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 整合包本地 / 外部分享导入流程（复刻 ModDetailScreen 的整合包安装链路：
 * FCL 原版 ModpackInstaller 引擎 + VersionNamingDialog 命名 + ModpackInstallDialog 阶段弹窗）。
 *
 * 流程：
 * 1. importSource（SAF 选中或微信等 ACTION_SEND 分享的 content:// Uri）非空时
 *    把内容复制到缓存目录，用 ModpackInstaller.readModpack 识别格式并取标题为默认命名；
 * 2. 弹 VersionNamingDialog（默认名 = manifest 标题，复刻 FCL RemoteModpackPage）；
 * 3. 确认后走 FCL 安装任务链，ModpackInstallDialog 逐阶段展示（对齐版本下载弹窗）；
 * 4. 完成/失败/取消后删除缓存 zip（与版本下载安装一致，不留安装介质）。
 *
 * @param importSource 待导入整合包来源 Uri（null 时不动作）
 * @param onHandled    来源消费完成回调（复制完成/失败后调用方清除 Uri 状态）
 * @param onImported   安装完成回调（版本名，供调用方刷新已安装列表）
 */
@Composable
fun ModpackImportFlow(
    importSource: Uri?,
    onHandled: () -> Unit,
    onImported: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 待安装的缓存整合包文件 + manifest 标题（默认命名）
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var defaultName by remember { mutableStateOf("") }
    var showNamingDialog by remember { mutableStateOf(false) }

    // 安装状态（复用 ModpackInstallUiState 归并逻辑 + ModpackInstallDialog UI）
    var modpackState by remember { mutableStateOf(ModpackInstallUiState()) }
    var modpackJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val modpackChannel = remember { Channel<ModpackTaskEvent>(Channel.UNLIMITED) }

    // 事件通道 → 状态归并（必须在主线程，复用 ModDetailScreen 的接线）
    LaunchedEffect(Unit) {
        for (ev in modpackChannel) {
            modpackState = modpackState.applyTaskEvent(ev)
        }
    }

    // 来源到达：复制到缓存 → 识别格式取标题 → 弹命名弹窗
    LaunchedEffect(importSource) {
        val uri = importSource ?: return@LaunchedEffect
        val file = withContext(Dispatchers.IO) { copyImportToCache(context, uri) }
        if (file == null) {
            Toast.makeText(context, "无法读取整合包文件", Toast.LENGTH_SHORT).show()
            onHandled()
            return@LaunchedEffect
        }
        val title = withContext(Dispatchers.IO) {
            try {
                ModpackInstaller.readModpack(file).name.trim()
            } catch (e: Exception) {
                VerseLogger.warn("整合包格式识别失败: ${e.message}", "ModpackImport")
                ""
            }
        }
        if (title.isEmpty()) {
            // 无法识别的整合包格式：与 FCL ModpackHelper 一致，直接报错并清理
            Toast.makeText(context, "无法识别的整合包格式", Toast.LENGTH_SHORT).show()
            withContext(Dispatchers.IO) { runCatching { file.delete() } }
            onHandled()
            return@LaunchedEffect
        }
        pendingFile = file
        defaultName = title
        showNamingDialog = true
        onHandled()
    }

    // 安装执行（照搬 ModDetailScreen.startModpackInstall 的安装段，仅源为本地文件）
    fun startInstall(name: String) {
        val file = pendingFile ?: return
        if (modpackState.visible) return
        modpackState = ModpackInstallUiState(visible = true, title = defaultName, phase = ModpackInstallPhase.INSTALLING)
        modpackJob = scope.launch {
            val gameDir = GameConfig.getGameDir(context.applicationContext)
            val result = ModpackInstaller.installModpack(
                gameDir = gameDir,
                zipFile = file,
                name = name
            ) { ev -> modpackChannel.trySend(ev) }
            result.fold(
                onSuccess = {
                    cleanupZip(file)
                    modpackState = modpackState.copy(phase = ModpackInstallPhase.DONE)
                    onImported(name)
                },
                onFailure = { e ->
                    cleanupZip(file)
                    if (e is ModpackInstallCancelledException || e is kotlinx.coroutines.CancellationException) {
                        modpackState = modpackState.copy(phase = ModpackInstallPhase.CANCELLED)
                    } else {
                        modpackState = modpackState.copy(
                            phase = ModpackInstallPhase.FAILED,
                            error = e.message ?: "整合包安装失败"
                        )
                    }
                }
            )
        }
    }

    fun cancelInstall() {
        ModpackInstaller.cancel()
        modpackJob?.cancel()
        modpackJob = null
        pendingFile?.let { cleanupZip(it) }
        pendingFile = null
        val ph = modpackState.phase
        if (ph == ModpackInstallPhase.INSTALLING) {
            modpackState = modpackState.copy(phase = ModpackInstallPhase.CANCELLED)
        }
    }

    fun retryInstall() {
        val file = pendingFile
        if (file == null || !file.exists()) {
            // 失败/取消后安装介质已清理（不留残留），本地导入需重新选择文件
            Toast.makeText(context, "安装文件已清理，请重新选择整合包", Toast.LENGTH_SHORT).show()
            modpackState = modpackState.copy(visible = false)
            return
        }
        val name = defaultName
        modpackState = ModpackInstallUiState(visible = true, title = name, phase = ModpackInstallPhase.INSTALLING)
        modpackJob = scope.launch {
            val gameDir = GameConfig.getGameDir(context.applicationContext)
            val result = ModpackInstaller.installModpack(gameDir, file, name) { ev -> modpackChannel.trySend(ev) }
            result.fold(
                onSuccess = {
                    cleanupZip(file)
                    modpackState = modpackState.copy(phase = ModpackInstallPhase.DONE)
                    onImported(name)
                },
                onFailure = { e ->
                    cleanupZip(file)
                    if (e is ModpackInstallCancelledException || e is kotlinx.coroutines.CancellationException) {
                        modpackState = modpackState.copy(phase = ModpackInstallPhase.CANCELLED)
                    } else {
                        modpackState = modpackState.copy(phase = ModpackInstallPhase.FAILED, error = e.message ?: "整合包安装失败")
                    }
                }
            )
        }
    }

    // ===== 命名弹窗（默认名 = manifest 标题，复刻 FCL RemoteModpackPage）=====
    if (showNamingDialog) {
        VersionNamingDialog(
            gameVersion = defaultName,
            selectedLoaderId = null,
            conflictsWith = { VersionManager(GameConfig.getGameDir(context)).versionIdConflicts(it) },
            onDismiss = {
                showNamingDialog = false
                pendingFile?.let { cleanupZip(it) }
                pendingFile = null
            },
            onConfirm = { name ->
                showNamingDialog = false
                startInstall(name)
            }
        )
    }

    // ===== 安装进度弹窗（对齐版本下载：分阶段任务流）=====
    if (modpackState.visible) {
        val running = modpackState.phase == ModpackInstallPhase.INSTALLING
        Dialog(onDismissRequest = {
            if (!running) modpackState = modpackState.copy(visible = false)
        }) {
            ModpackInstallDialog(
                state = modpackState,
                onCancel = { cancelInstall() },
                onRetry = { retryInstall() },
                onClose = { modpackState = modpackState.copy(visible = false) }
            )
        }
    }
}

/** 把 content:// 或 file:// Uri 复制到缓存目录（保留扩展名，供 ModpackInstaller 按 zip 读取） */
private fun copyImportToCache(context: Context, uri: Uri): File? {
    return try {
        val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "import.zip"
        val ext = displayName.substringAfterLast('.', "zip").takeIf { it.isNotBlank() } ?: "zip"
        val dest = File(context.cacheDir, "import_modpack_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        if (!dest.exists() || dest.length() == 0L) {
            dest.delete()
            return null
        }
        dest
    } catch (e: Exception) {
        VerseLogger.warn("复制导入整合包失败: ${e.message}", "ModpackImport")
        null
    }
}

/** 清理安装介质（与版本下载一致：完成/失败/取消后删除，不残留） */
private fun cleanupZip(file: File) {
    runCatching { if (file.exists()) file.delete() }
}
