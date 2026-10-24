package com.verse.explorer.app.ui.components

import com.verse.explorer.app.install.*
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.app.appContainer
import com.verse.explorer.core.version.VersionManager
import kotlinx.coroutines.launch
import java.io.File

/**
 * 整合包本地 / 外部分享导入流程（与 ModDetailScreen 的整合包安装链路一致：
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
    val modpackController = remember(context.applicationContext) { context.appContainer.modpackTask("local-import") }
    val installTaskState by modpackController.state.collectAsState()
    val importer = remember(context.applicationContext) { context.appContainer.modpackImports }
    val importState by importer.state.collectAsState()

    // 待安装的缓存整合包文件 + manifest 标题（默认命名）
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var defaultName by remember { mutableStateOf("") }
    var showNamingDialog by remember { mutableStateOf(false) }

    // 安装状态（复用 ModpackInstallUiState 归并逻辑 + ModpackInstallDialog UI）
    var modpackState by remember { mutableStateOf(ModpackInstallUiState()) }

    // 事件通道 → 状态归并（必须在主线程，复用 ModDetailScreen 的接线）
    LaunchedEffect(installTaskState) {
        if (installTaskState.visible) modpackState = installTaskState
        if (installTaskState.phase == ModpackInstallPhase.DONE) onImported(installTaskState.title)
    }

    // 来源到达：经下载中心复制到缓存并识别格式 → 弹命名弹窗
    // （复制任务在应用级协程执行，进度走下载中心通知，退出页面不中断）
    LaunchedEffect(importSource) {
        val uri = importSource ?: return@LaunchedEffect
        importer.submit(uri)
        onHandled()
    }
    LaunchedEffect(importState) {
        importState.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); importer.consumed() }
        importState.file?.let {
            pendingFile = it
            defaultName = importState.title
            showNamingDialog = true
        }
    }

    // 安装执行（与 ModDetailScreen 的安装段一致，仅源为本地文件）
    fun startInstall(name: String) {
        val file = pendingFile ?: return
        if (modpackState.visible) return
        if (modpackController.start(GameConfig.getGameDir(context.applicationContext), file, name, name)) importer.consumed()
    }

    fun cancelInstall() {
        modpackController.cancel()
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
            run { modpackController.dismiss(); modpackState = modpackState.copy(visible = false) }
            return
        }
        val name = defaultName
        val gameDir = GameConfig.getGameDir(context.applicationContext)
        if (modpackController.requiresNewName(gameDir, name)) {
            showNamingDialog = true
            return
        }
        modpackController.start(gameDir, file, name, name)
    }

    if (showNamingDialog) {
        VersionNamingDialog(
            gameVersion = defaultName,
            selectedLoaderId = null,
            conflictsWith = { VersionManager(GameConfig.getGameDir(context)).versionIdConflicts(it) },
            onDismiss = {
                showNamingDialog = false
                importer.discard()
                pendingFile = null
            },
            onConfirm = { name ->
                showNamingDialog = false
                startInstall(name)
            }
        )
    }

    // 安装进度弹窗（对齐版本下载：分阶段任务流）
    if (modpackState.visible) {
        val running = modpackState.phase == ModpackInstallPhase.INSTALLING
        Dialog(onDismissRequest = {
            if (!running) run { modpackController.dismiss(); modpackState = modpackState.copy(visible = false) }
        }) {
            ModpackInstallDialog(
                state = modpackState,
                onCancel = { cancelInstall() },
                onRetry = { retryInstall() },
                onClose = { run { modpackController.dismiss(); modpackState = modpackState.copy(visible = false) } }
            )
        }
    }
}

/** 清理安装介质（与版本下载一致：完成/失败/取消后删除，不残留） */
private fun cleanupZip(file: File) {
    runCatching { if (file.exists()) file.delete() }
}
