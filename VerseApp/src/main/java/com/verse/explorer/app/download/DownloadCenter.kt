package com.verse.explorer.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.verse.explorer.app.MainActivity
import com.verse.explorer.app.VerseApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 下载中心条目状态 */
enum class DownloadCenterState { RUNNING, WAIT_INSTALL }

/**
 * 下载中心对外可见的一条下载。
 * @param key 业务键（如 "modpack:<projectId>:<versionId>"），页面按 key 订阅自己的下载
 * @param progress 0..1；-1 表示总量未知（不确定进度）
 * @param speed 字节/秒
 */
data class DownloadCenterItem(
    val id: Long,
    val key: String,
    val title: String,
    val progress: Float = -1f,
    val speed: Long = 0,
    val state: DownloadCenterState = DownloadCenterState.RUNNING,
    val error: String? = null,
    /** 已下载字节（供下载弹窗展示 "已下载/总大小"） */
    val downloaded: Long = 0,
    /** 总字节；0 表示未知 */
    val total: Long = 0,
    /** 项目图标（模组/整合包封面），下载浮层卡片与模组列表保持一致 */
    val iconUrl: String? = null,
    /**
     * 独占任务（版本下载 / 整合包下载）：运行期间禁止提交其他任何下载；
     * 普通任务（模组等）在独占任务运行期间也不允许开始。
     */
    val exclusive: Boolean = false,
    /** 任务所属页面的导航路由；小窗点击任务时跳回该页面重新弹出下载弹窗 */
    val route: String? = null
)

/**
 * 全局下载中心：非阻塞下载的注册中心。
 * 资源/整合包下载不再随页面协程执行，而是提交到此处（应用级协程），
 * 页面只做订阅者，退出页面下载不中断；
 * 下载期间启动前台服务（进度通知）保活，后台/锁屏不中断；
 * 任务链结束（成功/失败/取消）自动移除并在全部结束后停止服务；
 * 带 installAction 的条目（整合包场景）成功后保留为"待安装"，由用户点击安装或丢弃。
 */
object DownloadCenter {

    internal const val NOTIFICATION_ID = 2301
    private const val CHANNEL_ID = "verse_download"
    private const val WAKE_LOCK_TAG = "Verse:download"
    private const val WIFI_LOCK_TAG = "Verse:download"

    private val _items = MutableStateFlow<List<DownloadCenterItem>>(emptyList())
    val items: StateFlow<List<DownloadCenterItem>> = _items.asStateFlow()

    /**
     * 已被「最小化下载」的弹窗 key 集合（版本安装 / 整合包安装）。
     * key 与队列条目 key 一致；页面据此隐藏弹窗，小窗点击任务时恢复并导航回页面。
     */
    private val _minimizedKeys = MutableStateFlow<Set<String>>(emptySet())
    val minimizedKeys: StateFlow<Set<String>> = _minimizedKeys.asStateFlow()

    fun minimize(key: String) {
        _minimizedKeys.update { it + key }
        com.verse.explorer.core.log.DownloadLog.write("center", "弹窗最小化: $key")
    }

    fun restoreMinimized(key: String) {
        _minimizedKeys.update { it - key }
    }

    fun isMinimized(key: String): Boolean = _minimizedKeys.value.contains(key)

    /**
     * 小窗「展开」跳回页面时按路由恢复弹窗：同一页面的下载条目 key 与
     * 安装镜像条目 key 不同（整合包），按 route 统一清掉该页面的最小化记录。
     */
    fun restoreMinimizedForRoute(route: String?) {
        if (route == null) return
        _minimizedKeys.update { keys ->
            keys.filterNot { key -> _items.value.any { it.route == route && it.key == key } }.toSet()
        }
    }

    private data class Entry(
        val id: Long,
        val key: String,
        val title: String,
        val installAction: Runnable?,
        val cleanupAction: Runnable?,
        val onComplete: ((success: Boolean, error: String?) -> Unit)?,
        val job: Job
    )

    private val entries = LinkedHashMap<Long, Entry>()
    /** 各条目最近一次进度上报时间（节流，避免高频刷新整表） */
    private val lastReportAt = HashMap<Long, Long>()
    private var idCounter = 0L
    private var permissionRequested = false

    // 唤醒锁引用计数：有任务时持有，全部结束释放（后台/锁屏下载不中断）
    private val lockMonitor = Any()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private var lockCount = 0

    /**
     * 提交一个下载任务到队列。
     *
     * @param task 下载体；onProgress 上报 (已下载, 总量, 速度)；isCancelled 返回 true 时
     *             任务体应尽快终止并返回 false（取消不靠协程中断阻塞 IO）
     * @return true 表示任务体自报成功
     */
    fun submit(
        key: String,
        title: String,
        installAction: Runnable? = null,
        cleanupAction: Runnable? = null,
        onComplete: ((success: Boolean, error: String?) -> Unit)? = null,
        iconUrl: String? = null,
        exclusive: Boolean = false,
        route: String? = null,
        task: suspend (
            onProgress: (downloaded: Long, total: Long, speed: Long) -> Unit,
            isCancelled: () -> Boolean
        ) -> Boolean
    ): DownloadCenterItem {
        val app = VerseApplication.instance
        val id = ++idCounter
        val item = DownloadCenterItem(id, key, title, iconUrl = iconUrl, exclusive = exclusive, route = route)

        synchronized(lastReportAt) { lastReportAt.clear() }
        _items.update { it + item }

        requestNotificationPermissionIfNeeded()
        com.verse.explorer.core.log.DownloadLog.write("center", "提交下载: $title (key=$key)")

        val handler = CoroutineExceptionHandler { _, e ->
            finish(id, success = false, error = e.message ?: e.javaClass.simpleName)
        }
        // 先注册条目再启动协程：极小任务可能在 launch 返回前完成，
        // 若完成时找不到条目会丢失完成回调
        val placeholderJob = Job()
        synchronized(entries) {
            entries[id] = Entry(id, key, title, installAction, cleanupAction, onComplete, placeholderJob)
        }
        val job = launchTask(handler) {
            val cancelled: () -> Boolean = { _items.value.firstOrNull { it.id == id } == null }
            val success = try {
                task(
                    { downloaded, total, speed -> reportProgress(id, downloaded, total, speed) },
                    cancelled
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.verse.explorer.core.log.DownloadLog.write("center", "下载异常: $title — ${e.javaClass.simpleName}: ${e.message}")
                finish(id, false, e.message ?: "下载失败")
                return@launchTask
            }
            if (cancelled()) {
                com.verse.explorer.core.log.DownloadLog.write("center", "下载取消: $title")
                finish(id, false, "已取消")
            } else if (success) {
                com.verse.explorer.core.log.DownloadLog.write("center", "下载完成: $title")
                finish(id, true, null)
            } else {
                com.verse.explorer.core.log.DownloadLog.write("center", "下载失败: $title")
                finish(id, false, "下载失败")
            }
        }
        synchronized(entries) {
            entries[id]?.let { entries[id] = it.copy(job = job) }
        }

        // 不再使用前台服务：Android 12+（尤其 Android 16）要求 startForegroundService 后
        // 服务必须在极短时间内 startForeground，否则系统抛
        // ForegroundServiceDidNotStartInTimeException 直接杀掉进程（实测 1.2.23 崩溃）。
        // 下载任务本来就跑在应用级协程里，配合下面的唤醒锁 + 进度通知即可，
        // 前台服务在这里只提供进程优先级，收益小于它带来的崩溃风险。
        acquireLocks(app)
        refreshNotification()
        return item
    }

    /** 执行"待安装"条目的安装动作并从面板移除 */
    fun install(item: DownloadCenterItem) {
        val entry = synchronized(entries) { entries.remove(item.id) } ?: return
        _items.update { list -> list.filterNot { it.id == item.id } }
        releaseLocks(VerseApplication.instance)
        entry.installAction?.run()
        refreshNotification()
    }

    /** 丢弃"待安装"条目：执行清理动作（删除临时文件等）并从面板移除 */
    fun discard(item: DownloadCenterItem) {
        val entry = synchronized(entries) { entries.remove(item.id) } ?: return
        _items.update { list -> list.filterNot { it.id == item.id } }
        releaseLocks(VerseApplication.instance)
        runCatching { entry.cleanupAction?.run() }
        refreshNotification()
    }

    /** 取消一条进行中的下载：条目移除后任务体的 isCancelled 轮询会终止下载 */
    fun cancel(item: DownloadCenterItem) {
        synchronized(entries) { entries[item.id] }?.job?.cancel()
        finish(item.id, false, "已取消")
    }

    /** 按 key 查找仍在队列中的条目 */
    fun findByKey(key: String): DownloadCenterItem? =
        _items.value.firstOrNull { it.key == key }

    /**
     * 独占规则：是否允许开始一个新的下载。
     * - exclusive 任务（版本/整合包）：任何任务在跑都不允许（含其他独占任务）；
     * - 普通任务（模组/光影等）：仅当有独占任务在跑时禁止。
     */
    fun canStart(exclusive: Boolean): Boolean {
        val running = _items.value.filter { it.state == DownloadCenterState.RUNNING }
        return if (exclusive) running.isEmpty() else running.none { it.exclusive }
    }

    /** 是否有独占任务（版本/整合包）正在下载 */
    fun hasExclusiveRunning(): Boolean =
        _items.value.any { it.exclusive && it.state == DownloadCenterState.RUNNING }

    /** 更新队列条目标题（版本/整合包安装阶段推进时同步显示当前阶段名） */
    fun updateTitle(key: String, title: String) {
        _items.update { list ->
            list.map { if (it.key == key && it.title != title) it.copy(title = title) else it }
        }
    }

    private fun entryJob(id: Long): Job? = synchronized(entries) { entries[id]?.job }

    private fun finish(id: Long, success: Boolean, error: String?) {
        // 带 installAction 的成功条目进入"待安装"：entry 保留到 install/discard 时才移除，
        // 否则待安装续接（页面自动触发）在 entries 中找不到条目，安装动作永远不会执行
        val entry = synchronized(entries) { entries[id] }
        val visible = _items.value.firstOrNull { it.id == id }
        val keepPending = success && entry?.installAction != null && visible != null
        if (entry != null && !keepPending) {
            synchronized(entries) { entries.remove(id) }
        }
        if (visible != null) {
            if (keepPending) {
                _items.update { list ->
                    list.map { if (it.id == id) it.copy(state = DownloadCenterState.WAIT_INSTALL, progress = 1f) else it }
                }
            } else {
                _items.update { list -> list.filterNot { it.id == id } }
                // 任务已结束，弹窗最小化记录一并清掉，避免残留影响下一次同名任务
                _minimizedKeys.update { it - visible.key }
            }
        } else {
            synchronized(entries) { entries.remove(id) }
        }
        if (!keepPending) {
            releaseLocks(VerseApplication.instance)
        }
        entry?.let { runCatching { it.onComplete?.invoke(success, error) } }
        refreshNotification()
    }

    /** 上报进度（300ms 节流），完成量按总量换算为 0..1，总量未知时保持 -1 */
    private fun reportProgress(id: Long, downloaded: Long, total: Long, speed: Long) {
        val now = System.currentTimeMillis()
        synchronized(lastReportAt) {
            val last = lastReportAt[id] ?: 0L
            if (now - last < 300) return
            lastReportAt[id] = now
        }
        val progress = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else -1f
        _items.update { list ->
            list.map {
                if (it.id == id) it.copy(progress = progress, speed = speed, downloaded = downloaded, total = total)
                else it
            }
        }
        refreshNotification()
    }

    /** 任务列表变化后刷新前台通知与聚合进度；全部完成后移除通知并停止服务 */
    private fun refreshNotification() {
        val context = VerseApplication.instance
        val list = _items.value
        if (list.isEmpty()) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } else if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                NotificationManagerCompat.from(context)
                    .notify(NOTIFICATION_ID, buildNotification(context, list))
            }
        }
    }

    /** 聚合进度：待安装条目按 100% 计入，未知的忽略，全部未知时返回 -1 */
    private fun aggregateProgress(list: List<DownloadCenterItem>): Float {
        val valid = list.map { if (it.state == DownloadCenterState.WAIT_INSTALL) 1f else it.progress }
            .filter { it >= 0 }
        return if (valid.isEmpty()) -1f else valid.average().toFloat().coerceIn(0f, 1f)
    }

    /** 构建下载进度通知（服务 startForeground 与任务变化刷新共用） */
    internal fun buildNotification(context: Context, list: List<DownloadCenterItem>): Notification {
        createNotificationChannel(context)
        val aggregate = aggregateProgress(list)
        val indeterminate = aggregate < 0
        val percent = if (indeterminate) 0 else (aggregate * 100).toInt().coerceIn(0, 100)
        val intent = Intent(context, MainActivity::class.java).apply {
            // 单顶复用已存在的启动器实例（热启动走 onNewIntent），避免叠加新实例
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("下载中心")
            // 服务可能被系统以空任务列表重新拉起，此时没有首项可取
            .setContentText(
                if (list.isEmpty()) "暂无下载任务"
                else "${list.first().title} $percent%"
            )
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, indeterminate)
            .build()
    }

    private fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "下载中心", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** Android 13+ 未授予通知权限时，借下载开始的时机发起一次授权请求（每次运行至多一次） */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || permissionRequested) return
        val activity = VerseApplication.instance.currentActivity ?: return
        if (ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) return
        permissionRequested = true
        ActivityCompat.requestPermissions(
            activity, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1002
        )
    }

    /** 有任务时持有部分唤醒锁 + Wi-Fi 高性能锁（引用计数），全部任务结束后释放 */
    private fun acquireLocks(context: Context) {
        synchronized(lockMonitor) {
            if (lockCount == 0) {
                wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                    .apply { acquire() }
                wifiLock = (context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager)
                    .createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG)
                    .apply { acquire() }
            }
            lockCount++
        }
    }

    private fun releaseLocks(context: Context) {
        synchronized(lockMonitor) {
            if (lockCount <= 0) return
            lockCount--
            if (lockCount == 0) {
                runCatching { wakeLock?.release() }
                runCatching { wifiLock?.release() }
                wakeLock = null
                wifiLock = null
            }
        }
    }

    /** 在应用级 IO 协程启动任务体（异常兜底交由 handler 完成） */
    private fun launchTask(
        handler: CoroutineExceptionHandler,
        block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit
    ): Job = VerseApplication.instance.appScope.launch(Dispatchers.IO + handler, block = block)
}
