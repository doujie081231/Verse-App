package com.verse.explorer.app.ui.components

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import com.verse.explorer.app.download.DownloadCenter
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.install.modpack.ModpackInstallCancelledException
import com.verse.explorer.core.install.modpack.ModpackInstaller
import com.verse.explorer.core.install.modpack.ModpackTaskEvent
import com.verse.explorer.core.version.VersionManager
import com.tungsten.fclcore.task.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File

/**
 * 整合包本地 / 外部分享导入流程（复刻 ModDetailScreen 的整合包安装链路：
 *
 * 流程：
 * 1. importSource（SAF 选中或微信等 ACTION_SEND 分享的 content:// Uri）非空时
 *    把内容复制到缓存目录，用 ModpackInstaller.readModpack 识别格式并取标题为默认命名；
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

    // 来源到达：经下载中心复制到缓存并识别格式 → 弹命名弹窗
    // （复制任务在应用级协程执行，进度走下载中心通知，退出页面不中断）
    val importFileRef = remember { java.util.concurrent.atomic.AtomicReference<File?>(null) }
    val importTitleRef = remember { java.util.concurrent.atomic.AtomicReference<String?>(null) }
    LaunchedEffect(importSource) {
        val uri = importSource ?: return@LaunchedEffect
        onHandled()
        val appContext = context.applicationContext
        DownloadCenter.submit(
            key = "modpack_import",
            title = "导入整合包",
            task = { onProgress, _ ->
                val file = copyImportToCache(appContext, uri, onProgress)
                if (file == null) {
                    // 复制失败（文件不可读）
                    importFileRef.set(null)
                    false
                } else {
                    val title = try {
                        ModpackInstaller.readModpack(file).name.trim()
                    } catch (e: Exception) {
                        VerseLogger.warn("整合包格式识别失败: ${e.message}", "ModpackImport")
                        ""
                    }
                    importFileRef.set(file)
                    importTitleRef.set(title)
                    title.isNotBlank()
                }
            },
            onComplete = { success, _ ->
                Schedulers.androidUIThread().execute {
                    val file = importFileRef.getAndSet(null)
                    val title = importTitleRef.getAndSet(null) ?: ""
                    if (!success || file == null) {
                        Toast.makeText(context, "无法读取整合包文件", Toast.LENGTH_SHORT).show()
                    } else if (title.isEmpty()) {
                        // 无法识别的整合包格式：直接报错并清理
                        Toast.makeText(context, "无法识别的整合包格式", Toast.LENGTH_SHORT).show()
                        runCatching { file.delete() }
                    } else {
                        pendingFile = file
                        defaultName = title
                        showNamingDialog = true
                    }
                }
            }
        )
    }

    // 安装执行（与 ModDetailScreen 的安装段一致，仅源为本地文件）
    fun startInstall(name: String) {
        val file = pendingFile ?: return
        if (modpackState.visible) return
        modpackState = ModpackInstallUiState(visible = true, title = defaultName).withFixedStageRows()
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

    /**
     * 重试安装：重名版本先清理半成品目录（modpack.json 缺失 = 上次安装未完成）；
     * 真已装版本（modpack.json 存在）走命名弹窗换名，避免直接撞"版本已存在"。
     */
    fun retryInstall() {
        val file = pendingFile
        if (file == null || !file.exists()) {
            // 失败/取消后安装介质已清理（不留残留），本地导入需重新选择文件
            Toast.makeText(context, "安装文件已清理，请重新选择整合包", Toast.LENGTH_SHORT).show()
            modpackState = modpackState.copy(visible = false)
            return
        }
        val name = defaultName
        val gameDir = GameConfig.getGameDir(context.applicationContext)
        if (VersionManager(gameDir).versionIdConflicts(name)) {
            val repo = com.tungsten.fclcore.game.DefaultGameRepository(gameDir)
            runCatching { repo.refreshVersions() }
            if (repo.isModpack(name)) {
                showNamingDialog = true
                return
            }
            runCatching { repo.removeVersionFromDisk(name) }
        }
        modpackState = ModpackInstallUiState(visible = true, title = name).withFixedStageRows()
        modpackJob = scope.launch {
            val gameDir2 = GameConfig.getGameDir(context.applicationContext)
            val result = ModpackInstaller.installModpack(gameDir2, file, name) { ev -> modpackChannel.trySend(ev) }
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

/** 把 content:// 或 file:// Uri 复制到缓存目录（保留扩展名，供 ModpackInstaller 按 zip 读取；带进度上报） */
private fun copyImportToCache(
    context: Context,
    uri: Uri,
    onProgress: (downloaded: Long, total: Long, speed: Long) -> Unit = { _, _, _ -> }
): File? {
    return try {
        val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "import.zip"
        val ext = displayName.substringAfterLast('.', "zip").takeIf { it.isNotBlank() } ?: "zip"
        val dest = File(context.cacheDir, "import_modpack_${System.currentTimeMillis()}.$ext")
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { ins ->
            val total = try { ins.available().toLong() } catch (_: Exception) { -1L }
            var copied = 0L
            var lastReport = 0L
            dest.outputStream().use { output ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (ins.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    copied += bytesRead
                    val now = System.currentTimeMillis()
                    if (now - lastReport > 300) {
                        onProgress(copied, total, 0)
                        lastReport = now
                    }
                }
                onProgress(copied, copied, 0)
            }
        }
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
