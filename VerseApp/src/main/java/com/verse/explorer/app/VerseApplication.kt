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
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        if (isCrashProcess()) return // 崩溃页进程：跳过数据库/JRE 等重初始化
        writeBootLog("Application.onCreate started")
        instance = this
        // 陶瓦联机：提供应用级 Context 供后台推送 VPN 通知状态
        com.verse.explorer.app.terracotta.TerracottaController.appContext = this
        // fclcore（FCL download 模块）宿主接入：FCLApp/FCLPath/处理器链路
        com.verse.explorer.app.install.FclCoreHost.init(this)
        SettingsRepository.init(this)
        database = Room.databaseBuilder(this, AppDatabase::class.java, "verse_explorer.db")
            .fallbackToDestructiveMigration()
            .build()
        accountManager = AccountManager(database.accountDao())
        microsoftAuthenticator = MicrosoftAuthenticator()
        authlibAuthenticator = AuthlibAuthenticator()
        writeBootLog("Application.onCreate completed, starting JRE init")
        initAllJreRuntimes()
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
                // ZL2 模式：捕获后展示崩溃分析页（应用不直接闪退），
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
            sw.write("===== Verse Explorer X Crash =====\n")
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
