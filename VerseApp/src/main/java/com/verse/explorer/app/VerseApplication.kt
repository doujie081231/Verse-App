package com.verse.explorer.app

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.core.account.AccountManager
import com.verse.explorer.core.account.AuthlibAuthenticator
import com.verse.explorer.core.account.MicrosoftAuthenticator
import com.verse.explorer.core.database.AppDatabase
import com.verse.explorer.core.java.JavaRuntimeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VerseApplication : Application(), ImageLoaderFactory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 应用级协程作用域：供跨页面长任务（如整合包安装）使用，不随页面销毁而取消 */
    val appScope: CoroutineScope get() = applicationScope

    /** 当前前台 Activity（供下载中心发起 Android 13 通知权限请求等一次性 UI 操作） */
    @Volatile
    var currentActivity: android.app.Activity? = null
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // 崩溃页独立进程不注册 handler：页面自身若再崩溃会无限递归
        if (!isCrashProcess()) registerCrashHandler()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("User-Agent", "VerseExplorerX/1.0 (Android)")
                                .build()
                        )
                    }
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
            }
            .crossfade(true)
            .components {
                // GIF 动图解码器：背景图片支持动态 GIF
                add(GifDecoder.Factory())
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        if (isCrashProcess()) return // 崩溃页进程：跳过数据库/JRE 等重初始化
        writeBootLog("Application.onCreate started")
        instance = this
        // 跟踪前台 Activity（下载中心通知权限请求等一次性 UI 操作使用）
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) { currentActivity = activity }
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {
                if (currentActivity === activity) currentActivity = null
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
        // 陶瓦联机：提供应用级 Context 供后台推送 VPN 通知状态
        com.verse.explorer.app.terracotta.TerracottaController.appContext = this
        com.verse.explorer.app.install.FclCoreHost.init(this)
        SettingsRepository.init(this)
        // 下载诊断日志（模组/版本/加载器/整合包）挂载 download.log
        com.verse.explorer.core.log.DownloadLog.init(this)
        database = Room.databaseBuilder(this, AppDatabase::class.java, "verse_explorer.db")
            .fallbackToDestructiveMigration()
            .build()
        accountManager = AccountManager(database.accountDao())
        microsoftAuthenticator = MicrosoftAuthenticator()
        authlibAuthenticator = AuthlibAuthenticator()
        writeBootLog("Application.onCreate completed, starting JRE init")
        initAllJreRuntimes()
        cleanupDownloadResidue()
    }

    /** 启动时清理上次中断留下的下载残留：整合包 .part 半截文件与缓存目录孤儿文件 */
    private fun cleanupDownloadResidue() {
        applicationScope.launch {
            runCatching {
                val gameDir = com.verse.explorer.core.config.GameConfig.getGameDir(this@VerseApplication)
                // 整合包目录：删除中断下载留下的 .part 半截文件（坏 zip 会让重试识别失败）
                File(gameDir, "modpacks").listFiles { f -> f.isFile && f.name.endsWith(".part") }
                    ?.forEach { runCatching { it.delete() } }
                // 资源下载缓存目录：清理超过一天的孤儿缓存文件（正常流程结束即清理，
                // 只有进程被杀才会残留）
                val cacheAssets = File(cacheDir, "assets")
                val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                cacheAssets.listFiles { f -> f.isFile && f.lastModified() < cutoff }
                    ?.forEach { runCatching { it.delete() } }
            }
        }
    }

    /** 崩溃分析页独立进程名（AndroidManifest :crash） */
    private fun isCrashProcess(): Boolean =
        try {
            val pn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                java.io.File("/proc/self/cmdline").readText().trim('\u0000')
            }
            pn.endsWith(":crash")
        } catch (_: Throwable) {
            false
        }

    private fun registerCrashHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(object : Thread.UncaughtExceptionHandler {
            override fun uncaughtException(thread: Thread, throwable: Throwable) {
                if (throwable is CancellationException) return
                writeCrashLogToFile(thread, throwable)
                try {
                    VerseLogger.error(
                        "UncaughtException on thread=" + thread.name + ": " + throwable.message + "\n" + throwable.stackTraceToString()
                    )
                } catch (t: Throwable) {
                    try {
                        Log.e("VerseApp", "UncaughtException on thread=" + thread.name, throwable)
                    } catch (_: Throwable) {}
                }
                // 页面由系统在新进程中恢复（intent 已送达 system_server）
                try {
                    com.verse.explorer.app.ui.game.CrashReportActivity.showForApp(this@VerseApplication)
                } catch (_: Throwable) {}
                try {
                    Handler(Looper.getMainLooper()).postDelayed({
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }, 300)
                } catch (_: Throwable) {}
                // 不转交 previousHandler：崩溃页已接管
            }
        })
    }

    fun writeBootLog(message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val line = "[$timestamp] $message\n"
            val logFile = File(getExternalFilesDir(null) ?: filesDir, "verse_boot.log")
            logFile.appendText(line)
            Log.i("VerseApp", "BootLog: $message")
        } catch (_: Throwable) {}
    }

    private fun writeCrashLogToFile(thread: Thread, throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val sw = StringWriter()
            sw.write("===== Verse Crash =====\n")
            sw.write("Time: $timestamp\n")
            sw.write("Thread: ${thread.name}\n")
            sw.write("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})\n")
            try {
                sw.write("App Version: ${packageManager.getPackageInfo(packageName, 0)?.versionName ?: "unknown"}\n")
            } catch (_: Throwable) {
                sw.write("App Version: unknown\n")
            }
            sw.write("\nException: ${throwable.javaClass.name}\n")
            sw.write("Message: ${throwable.message}\n")
            sw.write("\nStacktrace:\n")
            throwable.printStackTrace(PrintWriter(sw))
            var cause = throwable.cause
            var depth = 0
            while (cause != null && depth < 10) {
                sw.write("\nCaused by: ${cause.javaClass.name}: ${cause.message}\n")
                cause.printStackTrace(PrintWriter(sw))
                cause = cause.cause
                depth++
            }
            sw.write("\n===== End of Crash =====\n\n")
            val crashText = sw.toString()
            val externalDir = getExternalFilesDir(null) ?: filesDir
            val crashFile = File(externalDir, "verse_crash.log")
            crashFile.appendText(crashText)
            Log.i("VerseApp", "Crash log written to: ${crashFile.absolutePath}")
            try {
                val privateLog = File(filesDir, "verse_crash.log")
                privateLog.appendText(crashText)
            } catch (_: Exception) {}
        } catch (_: Throwable) {}
    }

    private fun initAllJreRuntimes() {
        applicationScope.launch {
            val versionKeys = listOf("java8", "java17", "java21", "java25")
            Log.i("VerseApp", "开始自动初始化所有JRE运行时: ${versionKeys.joinToString()}")
            // 各版本并行安装，避免某个版本解压校验失败走网络下载而阻塞后续版本。
            // 游戏启动依赖 java17，必须先保证它不被其它版本拖住。
            versionKeys.forEach { key ->
                applicationScope.launch {
                    try {
                        val result = JavaRuntimeManager.installJre(this@VerseApplication, key) { progress ->
                            Log.d("VerseApp", "JRE $key: ${progress.status} ${progress.progress}% - ${progress.message}")
                        }
                        result.fold(
                            onSuccess = { dir -> Log.i("VerseApp", "JRE $key 初始化完成: ${dir.absolutePath}") },
                            onFailure = { e -> Log.w("VerseApp", "JRE $key 初始化失败: ${e.message}") }
                        )
                    } catch (e: Exception) {
                        Log.e("VerseApp", "JRE $key 初始化异常: ${e.message}")
                    }
                }
            }
            Log.i("VerseApp", "所有JRE运行时初始化流程调度结束")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.coroutineContext[Job]?.cancel()
    }

    companion object {
        lateinit var instance: VerseApplication
            private set
        lateinit var database: AppDatabase
            private set
        lateinit var accountManager: AccountManager
            private set
        lateinit var microsoftAuthenticator: MicrosoftAuthenticator
            private set
        lateinit var authlibAuthenticator: AuthlibAuthenticator
            private set
    }
}
