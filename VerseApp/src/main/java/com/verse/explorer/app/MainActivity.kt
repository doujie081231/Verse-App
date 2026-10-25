package com.verse.explorer.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.DisplayNotch
import com.verse.explorer.app.ui.MainScreen
import com.verse.explorer.app.ui.RuntimeSetupScreen
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.ui.theme.VerseExplorerTheme
import com.verse.explorer.app.permissions.observeAppPermissionRequests

class MainActivity : ComponentActivity() {

    val jreReady = mutableStateOf(false)

    /** 外部分享导入的整合包 Uri（微信等 ACTION_SEND → ModpackImportFlow） */
    val shareImportUri = mutableStateOf<android.net.Uri?>(null)

    private val eulaAgreed = mutableStateOf(false)
    private val permissionGranted = mutableStateOf(false)
    /** 运行时检查完成标志（MainLaunchCoordinator 读写） */
    internal val setupComplete = mutableStateOf(false)
    /** 启动页状态检查完成标志（MainLaunchCoordinator 读写） */
    internal val stateChecked = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted || hasStoragePermission(this)) {
            permissionGranted.value = true
        }
    }

    private val launchCoordinator = MainLaunchCoordinator(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeAppPermissionRequests()

        // 冷启动（分享点开应用未运行）：处理 ACTION_SEND 整合包
        handleShareIntent(intent) { uri -> shareImportUri.value = uri }

        // 写入启动标记日志，确认 MainActivity.onCreate 被执行
        try {
            (applicationContext as? com.verse.explorer.app.VerseApplication)?.writeBootLog("MainActivity.onCreate started")
        } catch (_: Throwable) {}

        // 横屏允许正反两个方向（设备翻转后界面跟随旋转，不锁定单一朝向）
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 启动器部分屏幕全屏/忽略刘海：按设置组合应用窗口显示模式（设置变更即时生效）
        applyWindowDisplayMode(
            this,
            SettingsRepository.getLauncherFullScreen(),
            SettingsRepository.getIgnoreNotch()
        )

        val prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        eulaAgreed.value = prefs.getBoolean("isAgree", false)
        permissionGranted.value = hasStoragePermission(this)

        if (eulaAgreed.value && permissionGranted.value) {
            launchCoordinator.checkRuntimeAsync()
        }

        setContent {
            // 观察启动器全屏与忽略刘海设置变化：变更即时应用到窗口
            LaunchedEffect(VersePersonalization.launcherFullScreen, DisplayNotch.ignoreNotch) {
                applyWindowDisplayMode(this@MainActivity, VersePersonalization.launcherFullScreen, DisplayNotch.ignoreNotch)
            }
            // 启动时应用已保存的个性化设置（颜色主题/深浅三态/背景等由主题层响应式读取）+ 自定义强调色
            LaunchedEffect(Unit) {
                DisplayNotch.load()
                VersePersonalization.load(applicationContext)
                VerseColors.customAccent = SettingsRepository.getThemeAccent()
                    .takeIf { it.isNotEmpty() }
                    ?.let { Color(android.graphics.Color.parseColor("#$it")) }
            }
            VerseExplorerTheme {
                Surface(
                    // 刘海避让用黑边：仅在既未开启「启动器全屏」也未开启「忽略刘海（全屏）」时，
                    // 根容器按 displayCutout 内缩并把挖孔区域填成纯黑；
                    // 启动器全屏本身即要求延伸至刘海区域，不再保留黑边。
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (DisplayNotch.ignoreNotch || VersePersonalization.launcherFullScreen) Modifier
                            else Modifier.windowInsetsPadding(WindowInsets.displayCutout)
                        ),
                    color = Color.Black
                ) {
                    val showEula = !eulaAgreed.value
                    val showPermission = eulaAgreed.value && !permissionGranted.value
                    val showSetup = eulaAgreed.value && permissionGranted.value && stateChecked.value && !setupComplete.value
                    val showMain = eulaAgreed.value && permissionGranted.value && stateChecked.value && setupComplete.value
                    val showLoading = eulaAgreed.value && permissionGranted.value && !stateChecked.value

                    // 权限授予后自动触发运行时检查
                    LaunchedEffect(permissionGranted.value) {
                        if (permissionGranted.value && !stateChecked.value) {
                            launchCoordinator.checkRuntimeAsync()
                        }
                    }

                    // 权限页显示期间轮询核对：部分机型授权后 isExternalStorageManager()
                    // 会延迟若干秒才变为 true，轮询可让用户无需手动重进应用即可进入主界面
                    LaunchedEffect(showPermission) {
                        if (!showPermission) return@LaunchedEffect
                        repeat(20) {
                            kotlinx.coroutines.delay(800)
                            if (hasStoragePermission(this@MainActivity)) {
                                permissionGranted.value = true
                                return@LaunchedEffect
                            }
                        }
                    }

                    if (showEula) {
                        EulaAgreementDialog(
                            onAgree = {
                                prefs.edit().putBoolean("isAgree", true).apply()
                                eulaAgreed.value = true
                            },
                            onDisagree = { finish() }
                        )
                        return@Surface
                    }

                    if (showPermission) {
                        PermissionRequestDialog(
                            onRequest = { requestStoragePermissionCompat(this@MainActivity, permissionLauncher) },
                            onRecheck = { permissionGranted.value = hasStoragePermission(this@MainActivity) },
                            onCancel = { finish() }
                        )
                        return@Surface
                    }

                    if (showSetup) {
                        RuntimeSetupScreen(
                            onSetupComplete = {
                                setupComplete.value = true
                                jreReady.value = true
                            }
                        )
                        return@Surface
                    }

                    // 加载中 — 等待运行时状态检查完成
                    if (showLoading) {
                        Box(modifier = Modifier.fillMaxSize().background(VerseColors.PcBgPrimary),
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                RoseLoader(modifier = Modifier.size(40.dp), size = 40.dp)
                                Spacer(Modifier.height(16.dp))
                                Text("正在检查运行环境...", color = VerseColors.PcTextSecondary, fontSize = 14.sp)
                            }
                        }
                        return@Surface
                    }

                    // 主界面
                    try {
                        (applicationContext as? com.verse.explorer.app.VerseApplication)?.writeBootLog("Showing MainScreen")
                    } catch (_: Throwable) {}
                    MainScreen(
                        jreReady = jreReady.value,
                        onLaunchGame = { versionId ->
                            launchCoordinator.launchGame(versionId)
                        },
                        shareImportUri = shareImportUri.value,
                        onShareImportHandled = { shareImportUri.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask 热启动：分享/打开方式到达时更新待导入 Uri
        handleShareIntent(intent) { uri -> shareImportUri.value = uri }
    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // 恢复前台时重申窗口显示模式（系统栏可能已被系统重置）
        applyWindowDisplayMode(
            this,
            VersePersonalization.launcherFullScreen,
            DisplayNotch.ignoreNotch
        )

        // 每次回到前台都重新核对存储权限：部分机型从授权页返回后 AppOps 状态更新有延迟，
        // 只在「未完成初始化」时检查会让已授权的用户一直卡在权限页
        if (eulaAgreed.value) {
            val hasPerm = hasStoragePermission(this)
            if (hasPerm != permissionGranted.value) {
                permissionGranted.value = hasPerm
                VerseLogger.info("Permission state changed: $hasPerm", "MainActivity")
            }
        }
    }
}
