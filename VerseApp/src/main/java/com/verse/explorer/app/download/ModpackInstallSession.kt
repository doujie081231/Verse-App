package com.verse.explorer.app.download

import android.content.Context
import com.verse.explorer.app.VerseApplication
import com.verse.explorer.app.ui.components.ModpackInstallPhase
import com.verse.explorer.app.ui.components.ModpackInstallUiState
import com.verse.explorer.app.ui.components.ModpackRowStatus
import com.verse.explorer.app.ui.components.applyTaskEvent
import com.verse.explorer.app.ui.components.withFixedStageRows
import com.verse.explorer.core.install.modpack.ModpackInstallCancelledException
import com.verse.explorer.core.install.modpack.ModpackInstaller
import com.verse.explorer.core.install.modpack.ModpackTaskEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 整合包下载/安装会话：把原 ModDetailScreen 页内的弹窗状态
 * （modpackState / 事件通道 / zip 与图标缓存）提升为应用级单例，
 * 页面退出后安装与弹窗状态仍然存活 —— 这是「最小化下载」后能从小窗
 * 点回页面重新弹出弹窗的前提。
 *
 * 下载阶段（.mrpack 文件）仍由页面提交到下载中心；安装阶段从这里
 * 提交一个独占镜像条目，让下载小窗像版本安装一样展示总安装百分比。
 */
object ModpackInstallSession {

    private val _state = MutableStateFlow(ModpackInstallUiState())
    val state = _state.asStateFlow()

    /** 安装链路是否在跑（含下载阶段的弹窗可见期） */
    private val _installing = MutableStateFlow(false)
    val installing = _installing.asStateFlow()

    /** 事件通道（页面退出也不丢事件） */
    private val events = Channel<ModpackTaskEvent>(Channel.UNLIMITED)

    /** 本次会话归属的整合包项目 id（modpack_detail/{projectId}），用于弹窗归属判定 */
    var projectId: String? = null
        private set

    /** 弹窗最小化/恢复用的 key（= 下载中心安装镜像条目 key） */
    var dialogKey: String? = null
        private set

    /** 页面导航路由（modpack_detail/...），小窗点击任务跳回 */
    var route: String? = null
        private set

    /** 本次安装的整合包 zip 与图标缓存（结束/失败/取消后删除） */
    private var zipFile: java.io.File? = null
    private var iconFile: java.io.File? = null

    /** 安装完成回调（页面刷新版本列表等），主线程调用 */
    var onFinished: ((success: Boolean, error: String?) -> Unit)? = null

    init {
        VerseApplication.instance.appScope.launch {
            for (ev in events) {
                _state.update { it.applyTaskEvent(ev) }
            }
        }
    }

    /** 是否归属指定项目（避免在其他资源详情页误渲染本会话弹窗） */
    fun belongsTo(projectId: String): Boolean = this.projectId == projectId

    /**
     * 下载阶段开始时调用：初始化弹窗（DOWNLOAD_FILE 阶段）并记录归属。
     * 下载条目本身由页面提交（key = modpack:<projectId>:<versionId>）。
     */
    fun beginDownloadPhase(
        projectId: String,
        route: String?,
        title: String,
        iconUrl: String?
    ) {
        this.projectId = projectId
        this.route = route
        this.dialogKey = "modpack-install:$projectId"
        _state.value = ModpackInstallUiState(
            visible = true,
            title = title,
            iconUrl = iconUrl,
            phase = ModpackInstallPhase.DOWNLOAD_FILE
        )
    }

    /** 下载阶段进度同步（页面订阅下载中心条目后调用） */
    fun syncDownloadProgress(progress: Float, speed: Long, text: String) {
        _state.update {
            it.copy(
                fileProgress = progress.coerceAtLeast(0f),
                downloadSpeed = speed,
                fileText = text
            )
        }
    }

    /** 下载阶段失败/被取消（下载中心 onComplete 上报），弹窗转入对应终态 */
    fun markDownloadFinished(cancelled: Boolean, error: String) {
        _state.update {
            if (!it.visible) it
            else it.copy(
                phase = if (cancelled) ModpackInstallPhase.CANCELLED else ModpackInstallPhase.FAILED,
                error = if (cancelled) null else error
            )
        }
    }

    /**
     * 进入安装链路（下载完成后由「待安装」续接触发，可在页面外发生）。
     * 同时向下载中心提交独占镜像条目，驱动小窗的总安装百分比。
     */
    fun beginInstall(
        appContext: Context,
        file: java.io.File,
        name: String?,
        iconUrl: String?,
        zipCache: java.io.File?,
        iconCache: java.io.File?
    ) {
        zipFile = zipCache ?: file
        iconFile = iconCache
        _installing.value = true
        // 弹窗标题沿用下载阶段设定的项目标题；zip 直装（无下载阶段）时退回安装名
        _state.value = ModpackInstallUiState(
            visible = !DownloadCenter.isMinimized(dialogKey ?: ""),
            title = _state.value.title.ifBlank { name ?: "整合包" },
            iconUrl = iconUrl ?: _state.value.iconUrl,
            phase = ModpackInstallPhase.DOWNLOAD_FILE
        ).withFixedStageRows()

        val key = dialogKey
        if (key != null) {
            DownloadCenter.submit(
                key = key,
                title = "安装整合包 ${_state.value.title}",
                exclusive = true,
                route = route,
                task = { onProgress, isCancelled ->
                    try {
                        while (true) {
                            if (isCancelled()) {
                                ModpackInstaller.cancel()
                                return@submit false
                            }
                            val s = _state.value
                            // 总安装进度（固定权重加权）映射为固定总量上报
                            onProgress(
                                (s.overall.coerceIn(0f, 1f) * 1_000_000f).toLong(),
                                1_000_000L,
                                s.downloadSpeed
                            )
                            // 标题跟随当前运行中的阶段行，小窗能看到「正在下载哪一个」
                            val running = s.rows.firstOrNull { it.status == ModpackRowStatus.RUNNING }
                            DownloadCenter.updateTitle(
                                key,
                                (running?.label?.takeIf { it.isNotBlank() } ?: "安装整合包") +
                                    " · " + s.title
                            )
                            when (s.phase) {
                                ModpackInstallPhase.DONE -> return@submit true
                                ModpackInstallPhase.FAILED,
                                ModpackInstallPhase.CANCELLED -> return@submit false
                                else -> {}
                            }
                            delay(300)
                        }
                        false
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        ModpackInstaller.cancel()
                        throw e
                    }
                }
            )
        }

        VerseApplication.instance.appScope.launch {
            val gameDir = com.verse.explorer.core.config.GameConfig.getGameDir(appContext)
            val result = ModpackInstaller.installModpack(
                gameDir = gameDir,
                zipFile = file,
                name = name,
                iconFile = iconCache
            ) { ev -> events.trySend(ev) }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                finishInstall(result)
            }
        }
    }

    private fun finishInstall(result: Result<Unit>) {
        zipFile?.delete()
        zipFile = null
        iconFile?.delete()
        iconFile = null
        _installing.value = false
        result.fold(
            onSuccess = {
                _state.update { it.copy(phase = ModpackInstallPhase.DONE) }
                onFinished?.invoke(true, null)
            },
            onFailure = { e ->
                val cancelled = e is ModpackInstallCancelledException ||
                    e is kotlinx.coroutines.CancellationException
                _state.update {
                    if (cancelled) it.copy(phase = ModpackInstallPhase.CANCELLED)
                    else it.copy(
                        phase = ModpackInstallPhase.FAILED,
                        error = e.message ?: "整合包安装失败"
                    )
                }
                onFinished?.invoke(false, if (cancelled) "已取消" else e.message ?: "整合包安装失败")
            }
        )
    }

    /** 取消安装链路（下载中取消走下载中心 cancel，不经过这里） */
    fun cancelInstall() {
        ModpackInstaller.cancel()
        zipFile?.delete()
        zipFile = null
        iconFile?.delete()
        iconFile = null
        _installing.value = false
        _state.update {
            if (it.phase == ModpackInstallPhase.DOWNLOAD_FILE ||
                it.phase == ModpackInstallPhase.INSTALLING
            ) {
                it.copy(phase = ModpackInstallPhase.CANCELLED)
            } else it
        }
    }

    /** 关闭弹窗（完成/失败/取消后的「关闭」） */
    fun closeDialog() {
        _state.update { it.copy(visible = false) }
    }

    /** 重新显示弹窗（小窗「展开」跳回页面后由页面触发） */
    fun showDialog() {
        _state.update { it.copy(visible = true) }
    }

    /** 整个会话复位（页面重试流程重新走 beginDownloadPhase） */
    fun reset() {
        _state.value = ModpackInstallUiState()
        _installing.value = false
    }
}
