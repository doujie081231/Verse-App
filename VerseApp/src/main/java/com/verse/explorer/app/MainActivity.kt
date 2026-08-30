package com.verse.explorer.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.setup.RuntimeSetupManager
import com.verse.explorer.app.ui.MainScreen
import com.verse.explorer.app.ui.RuntimeSetupScreen
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.app.ui.game.GameActivity
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.ui.theme.VerseExplorerTheme
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.java.JavaRuntimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    val jreReady = mutableStateOf(false)

    private val eulaAgreed = mutableStateOf(false)
    private val permissionGranted = mutableStateOf(false)
    private val setupComplete = mutableStateOf(false)
    private val stateChecked = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted || hasStoragePermission()) {
            permissionGranted.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 写入启动标记日志，确认 MainActivity.onCreate 被执行
        try {
            (applicationContext as? com.verse.explorer.app.VerseApplication)?.writeBootLog("MainActivity.onCreate started")
        } catch (_: Throwable) {}

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }

        val prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        eulaAgreed.value = prefs.getBoolean("isAgree", false)
        permissionGranted.value = hasStoragePermission()

        if (eulaAgreed.value && permissionGranted.value) {
            checkRuntimeAsync()
        }

        setContent {
            // 启动时应用已保存的个性化黑白主题（对齐 PC data-theme）
            LaunchedEffect(Unit) {
                VerseColors.applyTheme(SettingsRepository.getThemeColor())
            }
            VerseExplorerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent  // 背景交给 MainScreen（全景/玻璃），避免不透底遮住全景
                ) {
                    val showEula = !eulaAgreed.value
                    val showPermission = eulaAgreed.value && !permissionGranted.value
                    val showSetup = eulaAgreed.value && permissionGranted.value && stateChecked.value && !setupComplete.value
                    val showMain = eulaAgreed.value && permissionGranted.value && stateChecked.value && setupComplete.value
                    val showLoading = eulaAgreed.value && permissionGranted.value && !stateChecked.value

                    // 权限授予后自动触发运行时检查
                    LaunchedEffect(permissionGranted.value) {
                        if (permissionGranted.value && !stateChecked.value) {
                            checkRuntimeAsync()
                        }
                    }

                    // EULA 协议对话框 (FCL-style: 首次启动必须同意)
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

                    // 权限请求对话框 (FCL-style)
                    if (showPermission) {
                        PermissionRequestDialog(
                            onRequest = { requestStoragePermissionCompat() },
                            onCancel = { finish() }
                        )
                        return@Surface
                    }

                    // 前置运行时环境安装界面 (FCL-style: RuntimeFragment)
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
                                CircularProgressIndicator(color = VerseColors.PcAccent, modifier = Modifier.size(40.dp))
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
                            launchGame(versionId)
                        }
                    )
                }
            }
        }
    }

    @Volatile
    private var runtimeCheckRunning = false

    private fun checkRuntimeAsync() {
        if (runtimeCheckRunning || stateChecked.value) return
        runtimeCheckRunning = true
        GlobalScope.launch(Dispatchers.IO) {
            try {
                RuntimeSetupManager.loadPaths(this@MainActivity)
                val status = RuntimeSetupManager.checkAllComponents(this@MainActivity)
                val allReady = status.all { (component, ready) ->
                    component.isOptional || ready
                }
                if (allReady) {
                    setupComplete.value = true
                    jreReady.value = true
                    VerseLogger.info("所有运行时已就绪，跳过设置", "MainActivity")
                } else {
                    val missing = status.filter { !it.value }.keys.joinToString { it.displayName }
                    VerseLogger.info("缺少运行时: $missing", "MainActivity")
                }
                stateChecked.value = true
            } catch (e: Exception) {
                VerseLogger.error("运行时检查失败: ${e.message}", "MainActivity")
                stateChecked.value = true
            } finally {
                runtimeCheckRunning = false
            }
        }
    }

    fun launchGame(versionId: String?) {
        if (!jreReady.value) {
            Toast.makeText(this, "Java运行时尚未初始化完成，请稍候...", Toast.LENGTH_LONG).show()
            return
        }
        val settingsRepo = com.verse.explorer.app.data.SettingsRepository
        val gameDir = GameConfig.getGameDir(this)
        val javaVerSetting = settingsRepo.getJavaVersion()
        val javaVer = javaVerSetting.toIntOrNull() ?: 17

        val anyJavaInstalled = JavaRuntimeManager.hasJava(this)
        val specificJavaOk = javaVerSetting.isEmpty() || javaVerSetting.toIntOrNull() == null ||
            JavaRuntimeManager.hasJava(this, javaVer)

        if (!anyJavaInstalled) {
            Toast.makeText(this, "正在自动安装Java运行时，请稍候...", Toast.LENGTH_LONG).show()
            GlobalScope.launch(Dispatchers.IO) {
                val javaVersion = when {
                    javaVer <= 8 -> "java8"
                    javaVer >= 21 -> "java21"
                    else -> "java17"
                }
                val installResult = JavaRuntimeManager.installJre(
                    context = this@MainActivity,
                    javaVersion = javaVersion
                ) { progress ->
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, progress.message, Toast.LENGTH_SHORT).show()
                    }
                }
                runOnUiThread {
                    if (installResult.isSuccess) {
                        startGameWithJava(versionId, gameDir)
                    } else {
                        Toast.makeText(this@MainActivity, "Java安装失败，请到Java页面手动安装", Toast.LENGTH_LONG).show()
                    }
                }
            }
            return
        }

        startGameWithJava(versionId, gameDir)
    }

    private fun startGameWithJava(versionId: String?, gameDir: File) {
        var javaHome: String? = null

        val settingsRepo = com.verse.explorer.app.data.SettingsRepository
        val javaVerSetting = settingsRepo.getJavaVersion()
        val requiredJavaVer = javaVerSetting.toIntOrNull() ?: 17

        val allRuntimes = JavaRuntimeManager.getInstalledRuntimes(this)
        val preferredRuntime = allRuntimes.find { it.version == requiredJavaVer }
        if (preferredRuntime != null) {
            javaHome = preferredRuntime.path.absolutePath
        }

        val jreDir = File(gameDir, "jre")
        if (javaHome == null && jreDir.exists()) {
            jreDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val javaBin = File(dir, "bin/java")
                    if (javaBin.exists()) {
                        javaHome = dir.absolutePath
                    }
                }
            }
        }

        val internalJreDir = File(filesDir, "jre")
        if (javaHome == null && internalJreDir.exists()) {
            internalJreDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val javaBin = File(dir, "bin/java")
                    if (javaBin.exists()) {
                        javaHome = dir.absolutePath
                    }
                }
            }
        }

        if (javaHome == null) {
            val best = allRuntimes.maxByOrNull { it.version }
            if (best != null) {
                javaHome = best.path.absolutePath
            }
        }

        if (javaHome == null) {
            Toast.makeText(this, "Java安装失败，请到设置中手动安装", Toast.LENGTH_LONG).show()
            return
        }

        var targetVersion = versionId
        if (targetVersion.isNullOrEmpty()) {
            val versionsDir = File(gameDir, "versions")
            if (versionsDir.exists()) {
                targetVersion = versionsDir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedByDescending { it.lastModified() }
                    ?.firstOrNull()?.name
            }
        }

        if (targetVersion.isNullOrEmpty()) {
            Toast.makeText(this, "请先安装Minecraft版本", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("javaHome", javaHome)
        intent.putExtra("versionId", targetVersion)
        intent.putExtra("gameDir", gameDir.absolutePath)
        intent.putExtra("renderer", settingsRepo.getRenderer())
        startActivity(intent)
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissionCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                permissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // 当从设置页面返回时，重新检查权限状态 (FCL-style)
        if (eulaAgreed.value && !setupComplete.value) {
            val hasPerm = hasStoragePermission()
            if (hasPerm != permissionGranted.value) {
                permissionGranted.value = hasPerm
                VerseLogger.info("Permission state changed: $hasPerm", "MainActivity")
            }
        }
    }
}

// ==================== EULA 协议对话框 (FCL-style) ====================

@Composable
private fun EulaAgreementDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.PcBgPrimary),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.72f),
            shape = RoundedCornerShape(20.dp),
            color = VerseColors.PcBgCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "用户协议与免责声明",
                    style = MaterialTheme.typography.titleLarge,
                    color = VerseColors.PcTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请仔细阅读以下协议，继续使用即表示您同意所有条款",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VerseColors.PcTextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 协议内容
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            VerseColors.PcBgHover,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = """
本软件为 Minecraft 第三方启动器，与 Mojang / Microsoft 无关。

• 本软件不会修改 Minecraft 的任何源代码
• 本软件不提供任何盗版 Minecraft 副本
• 使用本软件需要拥有合法的 Minecraft 账号
• 本软件开源免费，禁止用于商业用途
• 运行 Minecraft 需要安装 Java 运行时环境
• 下载的游戏文件来自 Mojang 官方服务器
• 用户需自行承担使用本软件的所有风险
• 开发者不对任何使用本软件造成的损失负责

继续使用即表示您已阅读并同意以上条款。
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = VerseColors.PcTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDisagree,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = VerseColors.PcTextSecondary
                        )
                    ) {
                        Text("不同意", fontSize = 14.sp)
                    }
                    Button(
                        onClick = onAgree,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VerseColors.PcAccent,
                            contentColor = VerseColors.PcAccentText
                        )
                    ) {
                        Text("同意并继续", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ==================== 权限请求对话框 (FCL-style) ====================

@Composable
private fun PermissionRequestDialog(
    onRequest: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.PcBgPrimary),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = VerseColors.PcBgCard
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "存储权限",
                    style = MaterialTheme.typography.titleLarge,
                    color = VerseColors.PcTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = """
Verse Explorer X 需要存储权限来：

• 保存 Minecraft 游戏文件和存档
• 管理模组、光影和资源包
• 下载和安装游戏版本

请授予"所有文件访问权限"以确保软件正常运行。
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VerseColors.PcTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = VerseColors.PcTextSecondary
                        )
                    ) {
                        Text("退出", fontSize = 14.sp)
                    }
                    Button(
                        onClick = onRequest,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VerseColors.PcAccent,
                            contentColor = VerseColors.PcAccentText
                        )
                    ) {
                        Text("授予权限", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}