package com.verse.explorer.core.install.modpack

import android.util.Log
import com.tungsten.fclcore.download.DefaultCacheRepository
import com.tungsten.fclcore.download.DefaultDependencyManager
import com.tungsten.fclcore.game.DefaultGameRepository
import com.tungsten.fclcore.mod.Modpack
import com.tungsten.fclcore.mod.ModpackProvider
import com.tungsten.fclcore.mod.modrinth.ModrinthModpackProvider
import com.tungsten.fclcore.task.AsyncTaskExecutor
import com.tungsten.fclcore.task.Schedulers
import com.tungsten.fclcore.task.Task
import com.tungsten.fclcore.task.TaskListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

/** 阶段行状态 */
enum class ModpackTaskKind { RUNNING, DONE, FAILED }

/**
 * FCL 安装任务阶段事件。
 *
 * @param key      阶段行唯一 key（"stage:fcl.install.game:1.21.1" / "task:xxx.jar" 等），
 *                 供 UI 将事件归并到对应阶段行
 * @param label    本地化阶段名（对齐 FCL 启动器任务显示："下载资源文件"、"安装 Fabric x"、"解压整合包"…）
 * @param name     当前任务名 / 正在下载的文件名
 * @param progress 当前任务进度 0..1
 * @param message  附加信息（任务 message，通常为空）
 * @param kind     任务状态
 */
data class ModpackTaskEvent(
    val key: String,
    val label: String,
    val name: String = "",
    val progress: Float = 0f,
    val message: String = "",
    val kind: ModpackTaskKind
)

class ModpackInstallCancelledException : Exception("整合包安装已取消")

/**
 * 整合包安装——全量使用 FCL（fclcore）原版链路，不自研：
 * - 读取 mrpack manifest：尝试各 ModpackProvider（Modrinth 优先）
 * - 安装：Modpack.getInstallTask() → ModrinthInstallTask
 *   （内部 = 安装游戏本体 `dependencyManager.gameBuilder()`
 *     + ModpackInstallTask 解压 overrides 到游戏目录
 *     + MinecraftInstanceTask 生成 modpack 配置
 *     + ModrinthCompletionTask 解析依赖并下载 mods）
 *
 * 与 FCL 的差异仅为宿主适配：安装器下载源用 BMCLAPI；版本名由调用方指定
 * （默认取 manifest 的 name）；进度以 FCL TaskListener 事件形式暴露给 UI，
 * 供弹窗逐阶段展示（对齐 FCL 启动器的任务列表显示）。
 */
object ModpackInstaller {

    private const val BMCL = "https://bmclapi2.bangbang93.com"
    private const val TAG = "ModpackInstaller"

    @Volatile
    private var cancelled = false

    @Volatile
    private var executorRef: AsyncTaskExecutor? = null

    /** 已挂进度监听的 task 身份集合（防止重复挂监听） */
    private val progressListenerKeys = ConcurrentHashMap.newKeySet<String>()

    /** 取消当前安装（文件下载阶段由调用方取消协程处理） */
    fun cancel() {
        cancelled = true
        try {
            executorRef?.cancel()
        } catch (_: Exception) {
        }
    }

    /**
     * 尝试用各 ModpackProvider 识别整合包文件，返回 FCL Modpack 对象。
     * 不支持的格式（手动整合包等）抛 UnsupportedModpackException。
     */
    fun readModpack(zipFile: File, charset: Charset = Charsets.UTF_8): Modpack {
        // 顺序与 FCL ModpackHelper.readModpackManifest 一致
        val providers: List<ModpackProvider> = listOf(
            com.tungsten.fclcore.mod.mcbbs.McbbsModpackProvider.INSTANCE,
            com.tungsten.fclcore.mod.curse.CurseModpackProvider.INSTANCE,
            ModrinthModpackProvider.INSTANCE,
            com.tungsten.fclcore.mod.multimc.MultiMCModpackProvider.INSTANCE,
            com.tungsten.fclcore.mod.server.ServerModpackProvider.INSTANCE
        )
        try {
            ZipFile(zipFile).use { zip ->
                for (provider in providers) {
                    try {
                        val pack = provider.readManifest(zip, zipFile.toPath(), charset)
                        Log.i(TAG, "整合包格式识别: ${provider.getName()} (${pack.name} ${pack.version})")
                        return pack
                    } catch (_: Exception) {
                        // 尝试下一个 provider
                    }
                }
            }
        } catch (_: Exception) {
        }
        throw IOException("无法识别的整合包格式: ${zipFile.name}")
    }

    /**
     * 安装整合包到游戏目录（FCL 任务链 + 阶段事件回调）。
     *
     * @param gameDir 游戏根目录（versions/libraries 所在）
     * @param zipFile mrpack/zip 整合包文件
     * @param name    生成的游戏版本名（默认用 manifest name）
     * @param onEvent 阶段事件回调（可能来自任意线程，调用方需自行切回主线程）
     */
    suspend fun installModpack(
        gameDir: File,
        zipFile: File,
        name: String? = null,
        onEvent: (ModpackTaskEvent) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            cancelled = false
            progressListenerKeys.clear()
            emit(onEvent, "", "准备安装整合包")

            val modpack = readModpack(zipFile)

            val runName = name ?: modpack.name
            val repo = DefaultGameRepository(gameDir)
            repo.refreshVersions()

            val dm = DefaultDependencyManager(
                repo,
                VerseDownloadProvider(BMCL),
                DefaultCacheRepository(File(com.tungsten.fclauncher.utils.FCLPath.CACHE_DIR).toPath())
            )

            // FCL 原版安装任务：modpack.getInstallTask(dm, zipFile, name)
            // Modrinth → ModrinthInstallTask；CurseForge → CurseInstallTask（FCL 原生支持，
            // 不自研）；其他格式（mcbbs/multimc/server）FCL 不支持时由引擎抛原生错误。
            val installTask = modpack.getInstallTask(dm, zipFile, runName)

            val executor = AsyncTaskExecutor(installTask)
            executorRef = executor

            executor.addTaskListener(object : TaskListener() {
                override fun onRunning(task: Task<*>) {
                    if (!displayable(task)) return
                    val key = rowKey(task) ?: return
                    emitTask(onEvent, task, ModpackTaskKind.RUNNING)
                    attachProgressListener(task, onEvent)
                }

                override fun onPropertiesUpdate(task: Task<*>) {
                    if (!displayable(task)) return
                    val key = rowKey(task) ?: return
                    emitTask(onEvent, task, ModpackTaskKind.RUNNING)
                }

                override fun onFinished(task: Task<*>) {
                    if (!displayable(task)) return
                    val key = rowKey(task) ?: return
                    emitTask(onEvent, task, ModpackTaskKind.DONE)
                }

                override fun onFailed(task: Task<*>, throwable: Throwable) {
                    val key = rowKey(task) ?: return
                    onEvent(
                        ModpackTaskEvent(
                            key = key,
                            label = labelOf(task.inheritedStage ?: task.stage),
                            name = task.getName(),
                            progress = progressOf(task),
                            message = throwable.message ?: "任务失败",
                            kind = ModpackTaskKind.FAILED
                        )
                    )
                }
            })

            // 本 fork 的 AsyncTaskExecutor.test() 内部会无条件 start() 重跑整个任务图，
            // 只能对任务调用一次（与 FclLoaderInstaller 一致）。
            executor.test()

            if (cancelled) {
                executorRef = null
                throw ModpackInstallCancelledException()
            }
            executorRef = null
            executor.getException()?.let { throw it }

            repo.refreshVersions()

            if (!repo.hasVersion(runName)) {
                throw IOException("整合包安装结束但未生成版本 $runName")
            }
            Log.i(TAG, "整合包安装完成: $runName")
            // 安装期间下载的整合包文件清理由调用方决定
            Unit
        }
    }

    // ===== 事件暴露辅助 =====

    private fun emit(onEvent: (ModpackTaskEvent) -> Unit, stage: String, label: String, name: String = "") {
        onEvent(ModpackTaskEvent(stage, label, name, 0f, "", ModpackTaskKind.RUNNING))
    }

    /** 是否可作为阶段行展示：有 stage（含继承 stage）或设置了自定义任务名 */
    private fun displayable(task: Task<*>): Boolean {
        return task.stage != null || task.inheritedStage != null ||
            (task.getName().isNotBlank() && task.getName() != task.javaClass.name)
    }

    /** 阶段行 key：优先 stage，其次自定义任务名（如单个文件名） */
    private fun rowKey(task: Task<*>): String? {
        val stage = task.inheritedStage ?: task.stage
        return when {
            stage != null -> "stage:$stage"
            task.getName().isNotBlank() && task.getName() != task.javaClass.name -> "task:${task.getName()}"
            else -> null
        }
    }

    private fun progressOf(task: Task<*>): Float {
        return try {
            (task.progressProperty().get().toFloat()).coerceIn(0f, 1f)
        } catch (_: Exception) {
            0f
        }
    }

    private fun messageOf(task: Task<*>): String {
        return try {
            task.messageProperty().get() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun emitTask(
        onEvent: (ModpackTaskEvent) -> Unit,
        task: Task<*>,
        kind: ModpackTaskKind
    ) {
        val key = rowKey(task) ?: return
        onEvent(
            ModpackTaskEvent(
                key = key,
                label = labelOf(task.inheritedStage ?: task.stage),
                name = task.getName(),
                progress = progressOf(task),
                message = messageOf(task),
                kind = kind
            )
        )
    }

    /** 为运行中的任务挂进度监听（属性 set 在 Android 主线程分发，监听回调同样在主线程） */
    private fun attachProgressListener(task: Task<*>, onEvent: (ModpackTaskEvent) -> Unit) {
        val listenerKey = "${task.javaClass.name}@${System.identityHashCode(task)}"
        if (!progressListenerKeys.add(listenerKey)) return
        try {
            Schedulers.androidUIThread().execute {
                task.progressProperty().addListener { _, _, newValue ->
                    val key = rowKey(task) ?: return@addListener
                    onEvent(
                        ModpackTaskEvent(
                            key = key,
                            label = labelOf(task.inheritedStage ?: task.stage),
                            name = task.getName(),
                            progress = (newValue as? Number)?.toFloat()?.coerceIn(0f, 1f) ?: progressOf(task),
                            message = messageOf(task),
                            kind = ModpackTaskKind.RUNNING
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    /** stage 转本地化显示名（对齐 FCL 启动器任务文案） */
    private fun labelOf(stage: String?): String {
        if (stage == null || stage.isEmpty()) return "处理中"
        return when {
            stage.equals("fcl.modpack", ignoreCase = true) -> "解压整合包文件"
            stage.equals("fcl.modpack.download", ignoreCase = true) -> "下载整合包模组"
            stage.startsWith("fcl.install.game:") -> "安装 Minecraft ${stage.removePrefix("fcl.install.game:")}"
            stage.equals("fcl.install.assets", ignoreCase = true) -> "下载资源文件"
            stage.startsWith("fcl.install.forge:") -> "安装 Forge ${stage.removePrefix("fcl.install.forge:")}"
            stage.startsWith("fcl.install.neoforge:") -> "安装 NeoForge ${stage.removePrefix("fcl.install.neoforge:")}"
            stage.startsWith("fcl.install.fabric-loader:") -> "安装 Fabric ${stage.removePrefix("fcl.install.fabric-loader:")}"
            stage.startsWith("fcl.install.quilt-loader:") -> "安装 Quilt ${stage.removePrefix("fcl.install.quilt-loader:")}"
            stage.startsWith("fcl.install.optifine:") -> "安装 OptiFine ${stage.removePrefix("fcl.install.optifine:")}"
            stage.startsWith("fcl.install.") -> "安装加载器 ${stage.removePrefix("fcl.install.")}"
            else -> stage
        }
    }
}