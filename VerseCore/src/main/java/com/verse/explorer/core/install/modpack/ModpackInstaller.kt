package com.verse.explorer.core.install.modpack

import android.util.Log
import com.tungsten.fclcore.download.DefaultCacheRepository
import com.tungsten.fclcore.download.DefaultDependencyManager
import com.tungsten.fclcore.game.DefaultGameRepository
import com.tungsten.fclcore.game.GameDirectoryType
import com.tungsten.fclcore.mod.Modpack
import com.tungsten.fclcore.mod.ModpackProvider
import com.tungsten.fclcore.mod.modrinth.ModrinthModpackProvider
import com.tungsten.fclcore.task.AsyncTaskExecutor
import com.tungsten.fclcore.task.FetchTask
import com.tungsten.fclcore.task.Schedulers
import com.tungsten.fclcore.task.Task
import com.tungsten.fclcore.task.TaskListener
import com.verse.explorer.core.config.GameConfig
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
 *
 *                 供 UI 将事件归并到对应阶段行
 * @param name     当前任务名 / 正在下载的文件名
 * @param progress 当前任务进度 0..1
 * @param message  附加信息（任务 message，通常为空）
 * @param kind     任务状态
 * @param countDone 计数行已完成文件数（计数事件携带，-1 表示非计数事件）：
 *                  单阶段多文件时 UI 按 n/total 计数展示行进度，不再绑定单文件进度
 * @param countTotal 计数行总文件数（任务 properties["total"] 事件携带，-1 表示非计数事件）
 * @param rowDone  是否为阶段行整体完成（非单文件任务结束时置 true，
 *                 避免同阶段并行文件个别完成就把整行标成已完成）
 */
data class ModpackTaskEvent(
    val key: String,
    val label: String,
    val name: String = "",
    val progress: Float = 0f,
    val message: String = "",
    val kind: ModpackTaskKind,
    val countDone: Int = -1,
    val countTotal: Int = -1,
    val rowDone: Boolean = false
)

class ModpackInstallCancelledException : Exception("整合包安装已取消")

/**
 * - 读取 mrpack manifest：尝试各 ModpackProvider（Modrinth 优先）
 * - 安装：Modpack.getInstallTask() → ModrinthInstallTask
 *   （内部 = 安装游戏本体 `dependencyManager.gameBuilder()`
 *     + ModpackInstallTask 解压 overrides 到游戏目录
 *     + MinecraftInstanceTask 生成 modpack 配置
 *     + ModrinthCompletionTask 解析依赖并下载 mods）
 *
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

    /** 各阶段行的文件完成计数（计数事件按阶段累加，保证 UI 收到的 n 单调递增） */
    private val stageCounters = ConcurrentHashMap<String, Int>()

    /** 取消当前安装（文件下载阶段由调用方取消协程处理） */
    fun cancel() {
        cancelled = true
        try {
            executorRef?.cancel()
        } catch (_: Exception) {
        }
    }

    /**
     * 不支持的格式（手动整合包等）抛 UnsupportedModpackException。
     */
    fun readModpack(zipFile: File, charset: Charset = Charsets.UTF_8): Modpack {
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
     * 整合包安装专用仓库：安装链全程将目标版本的运行目录固定为版本目录
     * （overrides 解压、整合包模组下载、saves/config 均随版本存放，与启动链的版本隔离一致；
     * 直接用基础仓库时运行目录会落到游戏根目录，模组会污染全局 mods）。
     */
    private class ModpackInstallGameRepository(base: File) : DefaultGameRepository(base) {
        override fun getGameDirectoryType(id: String?): GameDirectoryType = GameDirectoryType.VERSION_FOLDER

        override fun getRunDirectory(id: String): File = getVersionRoot(id)
    }

    /**
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
            stageCounters.clear()
            emit(onEvent, "", "准备安装整合包")

            val modpack = readModpack(zipFile)

            val runName = name ?: modpack.name
            com.verse.explorer.core.log.DownloadLog.modpack(
                "开始安装整合包: ${modpack.name} (${zipFile.name}, ${zipFile.length() / 1048576}MB), 目标版本=$runName"
            )

            val repo = ModpackInstallGameRepository(gameDir)
            repo.refreshVersions()
            try {
                installWithExecutor(repo, modpack, zipFile, runName, onEvent)
            } catch (e: Throwable) {
                Log.e(TAG, "installWithExecutor failed: ${e.javaClass.name}: ${e.message}", e)
                if (e !is ModpackInstallCancelledException) cleanupFailedVersion(repo, runName)
                throw e
            }

            // 尾部步骤逐步打点：此前 OOM 无堆栈且发生在任务链成功结束后，
            // 需要确认失败落在哪个尾部步骤（refreshVersions / hasVersion / saveModpackIcon）
            fun heapSnapshot(where: String) {
                val rt = Runtime.getRuntime()
                Log.i(
                    TAG,
                    "heap@$where used=${(rt.totalMemory() - rt.freeMemory()) / 1048576}MB " +
                        "free=${rt.freeMemory() / 1048576}MB total=${rt.totalMemory() / 1048576}MB max=${rt.maxMemory() / 1048576}MB"
                )
            }

            heapSnapshot("after-executor")
            repo.refreshVersions()
            heapSnapshot("after-refresh")

            if (!repo.hasVersion(runName)) {
                cleanupFailedVersion(repo, runName)
                throw IOException("整合包安装结束但未生成版本 $runName")
            }
            // 提取整合包作者图标（zip 根目录）到版本目录，供主页/已安装版本卡片展示
            heapSnapshot("before-icon")
            saveModpackIcon(zipFile, GameConfig.getVersionsDir(gameDir).resolve(runName))
            heapSnapshot("after-icon")
            Log.i(TAG, "整合包安装完成: $runName")
            com.verse.explorer.core.log.DownloadLog.modpack("整合包安装完成: $runName")
            // 安装期间下载的整合包文件清理由调用方决定
            Unit
        }.onFailure { e ->
            // OOM 等错误被 runCatching 吞掉堆栈，这里补记完整堆栈用于定位分配点
            Log.e(TAG, "installModpack failed: ${e.javaClass.name}: ${e.message}", e)
            com.verse.explorer.core.log.DownloadLog.modpack(
                "整合包安装失败: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    /** 构建并阻塞执行安装任务链（供 installModpack 统一做失败兜底清理） */
    private fun installWithExecutor(
        repo: DefaultGameRepository,
        modpack: Modpack,
        zipFile: File,
        runName: String,
        onEvent: (ModpackTaskEvent) -> Unit
    ) {
        val dm = DefaultDependencyManager(
            repo,
            VerseDownloadProvider(BMCL),
            DefaultCacheRepository(File(com.tungsten.fclauncher.utils.FCLPath.CACHE_DIR).toPath())
        )

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
                // 计数事件一：单文件任务完成后其计数包装任务回调一次（n/total 的 n）
                if (task is Task<*>.CountTask) {
                    val stage = task.countStage
                    val done = stageCounters.merge(stage, 1) { old, add -> old + add } ?: 1
                    if (done % 50 == 1) Log.i(TAG, "count $stage n=$done")
                    onEvent(
                        ModpackTaskEvent(
                            key = "stage:$stage",
                            label = labelOf(stage),
                            kind = ModpackTaskKind.RUNNING,
                            countDone = done
                        )
                    )
                    return
                }
                // 计数事件二：阶段任务在 execute 时声明本阶段文件总数（n/total 的 total）
                val total = (task.getProperties()["total"] as? Int) ?: -1
                if (total >= 0) {
                    val stage = task.inheritedStage ?: task.stage ?: return
                    Log.i(TAG, "declare total stage=$stage total=$total task=${task.javaClass.simpleName}")
                    onEvent(
                        ModpackTaskEvent(
                            key = "stage:$stage",
                            label = labelOf(stage),
                            name = task.getName(),
                            kind = ModpackTaskKind.RUNNING,
                            countTotal = total
                        )
                    )
                    return
                }
                if (!displayable(task)) return
                val key = rowKey(task) ?: return
                emitTask(onEvent, task, ModpackTaskKind.RUNNING)
            }

            override fun onFinished(task: Task<*>) {
                // 计数包装任务不参与阶段行状态：它随每个单文件完成而结束，
                // 若据此置行完成，首个文件下载完就会把整行虚标为已完成（总进度跳变）
                if (task is Task<*>.CountTask) return
                if (!displayable(task)) return
                val key = rowKey(task) ?: return
                // 单文件任务（下载任务）完成不算阶段行整体完成：
                // 同阶段并行多文件，个别完成不应把整行标成已完成；
                // 阶段宿主任务（资源下载/整合包解压/加载器安装等）结束才置行完成
                emitTask(onEvent, task, ModpackTaskKind.DONE, rowDone = task !is FetchTask<*>)
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
        executor.test()

        if (cancelled) {
            executorRef = null
            throw ModpackInstallCancelledException()
        }
        executorRef = null
        executor.getException()?.let { throw it }
    }

    /**
     * 失败/取消后清理本次安装产生的半成品版本目录：
     * 仅当 versions/<name>/modpack.json 不存在（说明安装未完成，非用户已装版本）时删除，
     * 避免中断残留的重名目录让下次重试直接报"版本已存在"。
     */
    private fun cleanupFailedVersion(repo: DefaultGameRepository, name: String) {
        try {
            if (repo.hasVersion(name) && !repo.isModpack(name)) {
                repo.removeVersionFromDisk(name)
                Log.i(TAG, "已清理中断安装的半成品版本: $name")
            } else if (!repo.hasVersion(name)) {
                // 版本未被仓库识别但目录可能已落盘（无版本 json 的半成品目录）
                val root = repo.getVersionRoot(name)
                val modpackJson = repo.getModpackConfiguration(name)
                if (root.isDirectory && !modpackJson.exists()) {
                    root.deleteRecursively()
                    Log.i(TAG, "已清理中断安装的残留版本目录: $name")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "清理半成品版本失败: ${e.message}")
        }
    }

    /**
     * 从整合包 zip 根目录提取作者提供的图标（CurseForge 根目录 "modpack icon.png"、
     * Modrinth 根目录 "icon.png" 等），写入 versions/<name>/modpack_icon.png。
     */
    private fun saveModpackIcon(zipFile: File, versionRoot: File) {
        try {
            ZipFile(zipFile).use { zip ->
                // 只枚举一次条目：Enumeration 每次从头遍历，
                // 若在序列 lambda 内重复获取会得到永不耗尽的首元素序列（死循环/内存爆涨）
                val rootFiles = zip.entries.asIterator().asSequence()
                    .filter { !it.isDirectory && !it.name.contains('/') && !it.name.contains('\\') }
                    .toList()
                val entry = rootFiles.firstOrNull { e ->
                    val n = e.name.lowercase()
                    (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp")) &&
                        (n.contains("icon") || n.contains("logo") || n.contains("pack"))
                } ?: rootFiles.firstOrNull { e -> e.name.lowercase().endsWith(".png") }
                if (entry != null) {
                    versionRoot.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        File(versionRoot, "modpack_icon.png").outputStream().use { input.copyTo(it) }
                    }
                    Log.i(TAG, "已保存整合包图标: ${entry.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "保存整合包图标失败: ${e.message}")
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
        kind: ModpackTaskKind,
        rowDone: Boolean = false
    ) {
        val key = rowKey(task) ?: return
        onEvent(
            ModpackTaskEvent(
                key = key,
                label = labelOf(task.inheritedStage ?: task.stage),
                name = task.getName(),
                progress = progressOf(task),
                message = messageOf(task),
                kind = kind,
                rowDone = rowDone
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
