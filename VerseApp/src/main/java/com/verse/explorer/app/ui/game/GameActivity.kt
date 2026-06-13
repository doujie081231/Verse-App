package com.verse.explorer.app.ui.game

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.launcher.bridge.ZLBridge
import com.verse.explorer.launcher.bridge.ZLBridgeCallback
import com.verse.explorer.launcher.bridge.ZLBridgeContext
import com.verse.explorer.launcher.bridge.CallbackBridge
import com.verse.explorer.launcher.keycodes.LwjglGlfwKeycode
import com.verse.explorer.launcher.launch.LaunchConfiguration
import com.verse.explorer.launcher.launch.LaunchCallback
import com.verse.explorer.launcher.launch.MinecraftLauncher
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionType
import com.verse.explorer.core.game.GameProfile
import com.verse.explorer.core.java.JavaRuntimeManager
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.terracotta.TerracottaAndroidAPI
import java.io.File
import kotlin.concurrent.thread

class GameActivity : ComponentActivity(), ZLBridgeCallback {

    private var zlBridge: ZLBridge? = null
    private var minecraftLauncher: MinecraftLauncher? = null
    private var surfaceView: SurfaceView? = null

    private var javaHome: String? = null
    private var versionId: String? = null
    private var gameDirPath: String? = null
    private var javaVersion: Int = 0
    private var maxMemory: Int = 2048
    private var minMemory: Int = 512
    private var gcType: String = "G1GC"
    private var customJvmArgs: String = ""
    private var gameWidth: Int = 854
    private var gameHeight: Int = 480
    private var renderer: String = "gl4es"
    private var launchMethod: String = "pojav"
    private var username: String = "Player"
    private var uuid: String = "00000000000000000000000000000000"
    private var isMultiplayer: Boolean = false
    private var accessToken: String = "0"
    private var userType: String = "mojang"

    private var isBoatMode = false

    @Volatile
    private var isGameRunning = false
    private var isGrabbing = false
    @Volatile
    private var dependenciesReady = false

    private var touchDownTime = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isLongPress = false
    private val LONG_PRESS_THRESHOLD = 500L
    private val MOVE_THRESHOLD = 30f
    private val exitHandled = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var statusOverlay: LinearLayout? = null
    private var statusTitle: TextView? = null
    private var statusMessage: TextView? = null
    private var statusProgressBar: ProgressBar? = null
    private var statusLog: TextView? = null
    private var statusScrollView: ScrollView? = null
    private var exportLogButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            ZLBridgeContext.context = this

            javaHome = intent.getStringExtra("javaHome")
            versionId = intent.getStringExtra("versionId")
            gameDirPath = intent.getStringExtra("gameDir")

            javaVersion = intent.getIntExtra("javaVersion", 0)
            maxMemory = intent.getIntExtra("maxMemory", 2048)
            minMemory = intent.getIntExtra("minMemory", 512)
            gcType = intent.getStringExtra("gcType") ?: "G1GC"
            customJvmArgs = intent.getStringExtra("customJvmArgs") ?: ""
            gameWidth = intent.getIntExtra("width", 854)
            gameHeight = intent.getIntExtra("height", 480)
            renderer = intent.getStringExtra("renderer") ?: "gl4es"
            launchMethod = intent.getStringExtra("launchMethod") ?: "pojav"
            isBoatMode = launchMethod == "boat"
            username = intent.getStringExtra("username") ?: "Player"
            uuid = intent.getStringExtra("uuid") ?: "00000000000000000000000000000000"
            isMultiplayer = intent.getBooleanExtra("multiplayerMode", false)

            accessToken = intent.getStringExtra("accessToken") ?: "0"
            userType = intent.getStringExtra("userType") ?: "mojang"

            if (versionId == null || gameDirPath == null) {
                Toast.makeText(this, "启动参数错误", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setupImmersiveMode()

            initGameLog()

            if (isBoatMode) {
                appendStatusLog("使用 Boat 模式启动\n")
                initBoatLaunch()
            } else {
                initSurfaceView()
            }
        } catch (e: Exception) {
            appendStatusLog("onCreate error: ${e.message}\n")
            e.printStackTrace()
            finish()
        }
    }

    private fun initGameLog() {
        try {
            val gameDir = File(gameDirPath!!)
            VerseLogger.init(gameDir)
            VerseLogger.info("GameActivity 启动", "GameActivity")
            VerseLogger.info("版本: $versionId, Java: $javaVersion, 启动方式: $launchMethod", "GameActivity")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSurfaceView() {
        surfaceView = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    appendStatusLog("Surface created\n")
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    appendStatusLog("Surface changed: ${width}x${height}\n")
                    if (!isGameRunning && !dependenciesReady) {
                        dependenciesReady = true
                        checkJavaAndLaunch(holder.surface, width, height)
                    }
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    appendStatusLog("Surface destroyed\n")
                    isGameRunning = false
                    dependenciesReady = false
                }
            })
        }

        val layout = FrameLayout(this)
        layout.addView(surfaceView)

        createStatusOverlay()
        layout.addView(statusOverlay)

        exportLogButton = createExportLogButton()
        val btnParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            bottomMargin = (16 * resources.displayMetrics.density).toInt()
            rightMargin = (16 * resources.displayMetrics.density).toInt()
        }
        exportLogButton?.layoutParams = btnParams
        layout.addView(exportLogButton)

        setContentView(layout)

        surfaceView?.setOnTouchListener { _, event ->
            handleTouchEvent(event)
            true
        }
    }

    private fun initBoatLaunch() {
        createStatusOverlay()

        val layout = FrameLayout(this)
        layout.addView(statusOverlay)

        exportLogButton = createExportLogButton()
        val btnParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            bottomMargin = (16 * resources.displayMetrics.density).toInt()
            rightMargin = (16 * resources.displayMetrics.density).toInt()
        }
        exportLogButton?.layoutParams = btnParams
        layout.addView(exportLogButton)

        setContentView(layout)

        runOnUiThread {
            statusTitle?.text = "Boat 模式启动"
            statusMessage?.text = "正在准备...\n版本: $versionId\n启动方式: Boat (JVMLoader)"
            statusProgressBar?.isIndeterminate = true
        }

        thread(name = "BoatCheckThread") {
            try {
                val detectedJava = autoDetectJavaVersion()
                appendStatusLog("Boat模式 - 自动检测需要Java $detectedJava...\n")

                val runtimes = try {
                    JavaRuntimeManager.getInstalledRuntimes(this@GameActivity)
                } catch (e: Exception) {
                    appendStatusLog("扫描Java目录异常: ${e.message}\n")
                    emptyList()
                }

                appendStatusLog("找到 ${runtimes.size} 个运行时\n")
                runtimes.forEach { rt ->
                    appendStatusLog("  - Java ${rt.version} @ ${rt.path.absolutePath}\n")
                }

                val matchingRuntime = selectBestRuntime(runtimes, detectedJava)

                if (matchingRuntime == null) {
                    appendStatusLog("未找到兼容的Java运行时！需要Java $detectedJava+\n")
                    runOnUiThread {
                        statusTitle?.text = "未找到Java运行时"
                        statusMessage?.text = "需要Java $detectedJava+，当前: ${runtimes.map { "Java ${it.version}" }.joinToString()}"
                        Toast.makeText(this@GameActivity, "需要Java $detectedJava+", Toast.LENGTH_LONG).show()
                    }
                    return@thread
                }

                javaHome = matchingRuntime.path.absolutePath
                javaVersion = matchingRuntime.version
                appendStatusLog("选择Java运行时: Java ${matchingRuntime.version} @ $javaHome\n")

                runOnUiThread {
                    statusTitle?.text = "正在启动游戏..."
                    statusMessage?.text = "Boat模式 - JVMLoader"
                    statusProgressBar?.progress = 100
                }
                Thread.sleep(400)
                prepareBoatLaunch()
            } catch (e: Exception) {
                appendStatusLog("Boat启动检查异常: ${e.message}\n")
                runOnUiThread {
                    statusTitle?.text = "启动失败"
                    statusMessage?.text = e.message
                    Toast.makeText(this@GameActivity, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun prepareBoatLaunch() {
        if (isGameRunning || javaHome == null) return

        val jreDir = File(javaHome!!)
        val gameDir = File(gameDirPath!!)
        val librariesDir = File(gameDir, "libraries")
        val assetsDir = File(gameDir, "assets")
        val externalNativesDir = File(gameDir, "natives")
        externalNativesDir.mkdirs()

        val nativesExist = externalNativesDir.exists() && (externalNativesDir.listFiles()?.size ?: 0) > 0
        if (!nativesExist) {
            appendStatusLog("Boat模式 - 原生库缺失，尝试从assets提取...\n")
            try {
                extractDefaultNatives(externalNativesDir)
            } catch (e: Exception) {
                appendStatusLog("Boat模式 - 原生库提取失败: ${e.message}\n")
            }
        }

        val internalNativesDir = copyNativesToInternalStorage(externalNativesDir)
        appendStatusLog("Boat模式 - 使用内部存储natives: ${internalNativesDir.absolutePath}\n")

        // PC端风格: 使用resolveVersionJson合并全部继承链JSON
        val resolvedJson = resolveVersionJson(versionId!!)
        var mainClass = "net.minecraft.client.main.Main"
        var versionLibraries = emptyList<com.verse.explorer.core.version.Library>()
        var assetIndexId = versionId
        var actualJarId = versionId  // 实际JAR文件名对应的版本ID

        if (resolvedJson != null) {
            try {
                mainClass = resolvedJson.get("mainClass")?.asString ?: mainClass
                assetIndexId = resolvedJson.getAsJsonObject("assetIndex")?.get("id")?.asString ?: versionId!!
                val libsArray = resolvedJson.getAsJsonArray("libraries")
                if (libsArray != null) {
                    versionLibraries = parseLibrariesFromJson(libsArray)
                }
                // PC端风格: 版本JSON中可能有jar字段指定继承的JAR ID
                actualJarId = resolvedJson.get("jar")?.asString ?: versionId!!
            } catch (e: Exception) {
                appendStatusLog("警告: 版本JSON解析异常: ${e.message}\n")
            }
        } else {
            appendStatusLog("警告: 版本JSON不存在，尝试直接读取...\n")
            val versionJson = File(gameDir, "versions/$versionId/$versionId.json")
            if (versionJson.exists()) {
                try {
                    val json = com.google.gson.GsonBuilder().setLenient().create().fromJson(
                        versionJson.readText(), com.google.gson.JsonObject::class.java
                    )
                    mainClass = json.get("mainClass")?.asString ?: mainClass
                    assetIndexId = json.getAsJsonObject("assetIndex")?.get("id")?.asString ?: versionId!!
                    val libsArray = json.getAsJsonArray("libraries")
                    if (libsArray != null) {
                        versionLibraries = parseLibrariesFromJson(libsArray)
                    }
                    actualJarId = json.get("jar")?.asString ?: versionId!!
                } catch (e: Exception) {
                    appendStatusLog("警告: 版本JSON解析异常: ${e.message}\n")
                }
            }
        }

        val version = MinecraftVersion(
            id = versionId!!, type = VersionType.RELEASE, url = "", releaseTime = "",
            mainClass = mainClass, libraries = versionLibraries,
            assetIndex = com.verse.explorer.core.version.AssetIndex(
                id = assetIndexId ?: versionId!!, url = "", totalSize = 0, known = false
            )
        )

        val settingsRepo = com.verse.explorer.app.data.SettingsRepository(this)
        val storedUuid = settingsRepo.getCustomSetting("player_uuid")
            ?: java.util.UUID.randomUUID().toString().also { settingsRepo.setCustomSetting("player_uuid", it) }
        val playerUuid = java.util.UUID.fromString(storedUuid)

        val profile = GameProfile(
            id = playerUuid.toString(), name = username,
            versionId = versionId!!, gameDir = gameDir
        )

        val maxMem = settingsRepo.getMaxMemory()
        val minMem = settingsRepo.getMinMemory()
        val gc = if (settingsRepo.getGcType().isNotEmpty()) settingsRepo.getGcType() else gcType
        val customArgs = settingsRepo.getCustomJvmArgs()

        val config = LaunchConfiguration(
            version = version, profile = profile, javaHome = jreDir,
            gameDir = gameDir, assetsDir = assetsDir, librariesDir = librariesDir,
            nativesDir = internalNativesDir, width = gameWidth, height = gameHeight,
            maxMemory = maxMem, minMemory = minMem, fullscreen = true,
            gcType = gc, customJvmArgs = customArgs, renderer = renderer,
            javaVersion = javaVersion, accessToken = accessToken, userType = userType
        )

        thread(name = "BoatLaunchThread") {
            try {
                appendStatusLog("Boat模式 - 启动JVM (JVMLoader)...\n")
                minecraftLauncher = MinecraftLauncher(this@GameActivity, null)

                minecraftLauncher!!.setLaunchCallback(object : LaunchCallback {
                    override fun onGameExit(code: Int) {
                        isGameRunning = false
                        appendStatusLog("游戏进程退出 (code=$code)\n")
                        runOnUiThread { onExit(code) }
                    }
                })

                val launchProcess = minecraftLauncher!!.launch(config, "boat")

                if (launchProcess != null) {
                    isGameRunning = true
                    appendStatusLog("Boat游戏启动成功 (PID=${launchProcess.pid})\n")
                } else {
                    appendStatusLog("Boat JVM启动失败！\n")
                    runOnUiThread {
                        Toast.makeText(this@GameActivity, "Boat启动失败：JVM无法创建", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                appendStatusLog("Boat启动异常: ${e.message}\n")
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@GameActivity, "Boat启动错误: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun createStatusOverlay() {
        statusOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FAFAFA"))
            setPadding(48, 48, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        statusTitle = TextView(this).apply {
            text = "正在准备启动..."
            setTextColor(Color.parseColor("#1A1A2E"))
            textSize = 20f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        statusOverlay?.addView(statusTitle)

        statusMessage = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#787890"))
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }
        statusOverlay?.addView(statusMessage)

        statusProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                6
            ).apply { topMargin = 24 }
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#5D8C3E"))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E5EA"))
        }
        statusOverlay?.addView(statusProgressBar)

        statusScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { topMargin = 20 }
        }

        statusLog = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#4A4A5E"))
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setLineSpacing(0f, 1.15f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        statusScrollView?.addView(statusLog)
        statusOverlay?.addView(statusScrollView)

        statusOverlay?.visibility = View.VISIBLE
    }

    private fun createExportLogButton(): Button {
        val btn = Button(this).apply {
            text = "导出日志"
            setTextColor(Color.parseColor("#5D8C3E"))
            setBackgroundColor(Color.parseColor("#F0F7EC"))
            textSize = 12f
            setPadding(24, 12, 24, 12)
            setOnClickListener { exportLog() }
        }
        return btn
    }

    private fun resolveVersionJson(versionId: String): com.google.gson.JsonObject? {
        val gameDir = File(gameDirPath!!)
        val versionsRootDir = File(gameDir, "versions")
        var jsonFile = File(versionsRootDir, "$versionId/$versionId.json")

        val resolved = mutableSetOf<String>()
        var currentJson: com.google.gson.JsonObject? = null

        while (jsonFile.exists() && resolved.add(jsonFile.parentFile?.name ?: "")) {
            val json = try {
                com.google.gson.GsonBuilder().setLenient().create()
                    .fromJson(jsonFile.readText(), com.google.gson.JsonObject::class.java)
            } catch (e: Exception) {
                appendStatusLog("解析JSON失败: ${jsonFile.path} - ${e.message}\n")
                break
            }

            if (currentJson == null) {
                currentJson = json
            } else {
                for ((key, value) in json.entrySet()) {
                    if (key == "libraries" && currentJson!!.has("libraries")) {
                        val merged = com.google.gson.JsonArray()
                        val childLibs = currentJson!!.getAsJsonArray("libraries")
                        val parentLibs = value.asJsonArray
                        childLibs.forEach { merged.add(it) }
                        val childNames = childLibs.mapNotNull {
                            it.asJsonObject?.get("name")?.asString
                        }.toSet()
                        parentLibs.forEach { lib ->
                            val name = lib.asJsonObject?.get("name")?.asString
                            if (name == null || name !in childNames) {
                                merged.add(lib)
                            }
                        }
                        currentJson!!.add("libraries", merged)
                    } else if (!currentJson!!.has(key)) {
                        currentJson!!.add(key, value)
                    }
                }
            }

            val inheritsFrom = json.get("inheritsFrom")?.asString
            if (inheritsFrom.isNullOrEmpty() || inheritsFrom == versionId) break
            jsonFile = File(versionsRootDir, "$inheritsFrom/$inheritsFrom.json")
        }

        return currentJson
    }

    private fun getRequiredJavaVersion(versionId: String): Int {
        val gameDir = File(gameDirPath!!)
        val versionsRootDir = File(gameDir, "versions")
        val jsonFile = File(versionsRootDir, "$versionId/$versionId.json")
        val versionJson = if (jsonFile.exists()) resolveVersionJson(versionId) else null

        if (versionJson != null) {
            val javaVersionObj = versionJson.getAsJsonObject("javaVersion")
            if (javaVersionObj != null) {
                val majorVersion = javaVersionObj.get("majorVersion")?.asInt
                if (majorVersion != null && majorVersion > 0) {
                    appendStatusLog("版本JSON javaVersion.majorVersion=$majorVersion ✅\n")
                    return majorVersion
                }
            }

            val complianceLevel = versionJson.get("complianceLevel")?.asInt
            if (complianceLevel != null && complianceLevel > 0) {
                appendStatusLog("版本JSON complianceLevel=$complianceLevel\n")
                return complianceLevel
            }
        }

        appendStatusLog("版本JSON无javaVersion/complianceLevel，解析版号推断...\n")
        return inferJavaVersionFromId(versionId)
    }

    private fun inferJavaVersionFromId(mcVersionStr: String): Int {
        if (mcVersionStr.isBlank()) return 17

        if (mcVersionStr.toIntOrNull() != null) {
            val ver = mcVersionStr.toInt()
            return when { ver >= 25 -> 25; ver >= 21 -> 21; ver >= 17 -> 17; else -> 8 }
        }

        val parts = mcVersionStr.split(".")
        if (parts.size < 2) {
            val ver = mcVersionStr.toIntOrNull() ?: 17
            return when { ver >= 25 -> 25; ver >= 21 -> 21; ver >= 17 -> 17; else -> 8 }
        }

        val major = parts[0].toIntOrNull() ?: 1
        val minor = parts[1].toIntOrNull() ?: 0
        val patch = if (parts.size >= 3) {
            parts[2].split("-").first().toIntOrNull() ?: 0
        } else 0

        val isForge = mcVersionStr.contains("forge", ignoreCase = true) ||
                      mcVersionStr.contains("fabric", ignoreCase = true) ||
                      mcVersionStr.contains("quilt", ignoreCase = true) ||
                      mcVersionStr.contains("neoforged", ignoreCase = true)

        if (isForge && major == 1 && minor == 16 && patch >= 5) {
            appendStatusLog("推断: Forge 1.16.5+ → Java 11\n")
            return 11
        }

        if (isForge && major == 1 && minor == 16) {
            appendStatusLog("推断: Forge 1.16 → Java 8\n")
            return 8
        }

        if (major != 1) {
            return when { major >= 22 -> 25; major >= 18 -> 21; major >= 13 -> 17; else -> 8 }
        }

        return when {
            minor >= 20 && patch >= 5 -> {
                appendStatusLog("推断: 1.20.5+ → Java 21\n")
                21
            }
            minor >= 30 -> {
                appendStatusLog("推断: 1.30+ → Java 25\n")
                25
            }
            minor >= 25 -> {
                appendStatusLog("推断: 1.25+ → Java 25\n")
                25
            }
            minor >= 22 -> {
                appendStatusLog("推断: 1.22+ → Java 25\n")
                25
            }
            minor >= 21 -> {
                appendStatusLog("推断: 1.21+ → Java 21\n")
                21
            }
            minor >= 17 -> {
                appendStatusLog("推断: 1.17+ → Java 17\n")
                17
            }
            minor >= 16 && isForge -> {
                appendStatusLog("推断: Forge 1.16+ → Java 8\n")
                8
            }
            else -> {
                appendStatusLog("推断: 默认 → Java 8\n")
                8
            }
        }
    }

    private fun autoDetectJavaVersion(): Int {
        if (javaVersion > 0) {
            appendStatusLog("传入Java版本指示: $javaVersion (仅供参考)\n")
        }

        return getRequiredJavaVersion(versionId ?: "1.0")
    }

    private fun selectBestRuntime(runtimes: List<com.verse.explorer.core.java.JavaRuntime>, requiredVersion: Int): com.verse.explorer.core.java.JavaRuntime? {
        val exactMatch = runtimes.find { it.version == requiredVersion }
        if (exactMatch != null) {
            appendStatusLog("精确匹配: Java ${exactMatch.version}\n")
            return exactMatch
        }
        val minHigher = runtimes.filter { it.version >= requiredVersion }.minByOrNull { it.version }
        if (minHigher != null) {
            appendStatusLog("选择最小兼容版本: Java ${minHigher.version}\n")
            return minHigher
        }
        val maxAvailable = runtimes.maxByOrNull { it.version }
        if (maxAvailable != null) {
            appendStatusLog("无兼容版本，使用最高可用: Java ${maxAvailable.version} (可能不兼容!)\n")
            return maxAvailable
        }
        return null
    }

    private fun checkJavaAndLaunch(surface: Surface, width: Int, height: Int) {
        exitHandled.set(false)
        thread(name = "JavaCheckThread") {
            try {
                runOnUiThread {
                    statusTitle?.text = "正在自动检测Java..."
                    statusMessage?.text = "扫描已安装的Java运行时..."
                    statusProgressBar?.isIndeterminate = true
                    statusOverlay?.visibility = View.VISIBLE
                }

                val detectedJava = autoDetectJavaVersion()
                appendStatusLog("自动检测需要的Java版本: $detectedJava\n")

                val runtimes = try {
                    JavaRuntimeManager.getInstalledRuntimes(this@GameActivity)
                } catch (e: Exception) {
                    appendStatusLog("扫描Java目录异常: ${e.message}\n")
                    emptyList()
                }

                appendStatusLog("找到 ${runtimes.size} 个已安装运行时:\n")
                runtimes.forEach { rt ->
                    appendStatusLog("  - Java ${rt.version} @ ${rt.path.absolutePath}\n")
                }

                val matchingRuntime = selectBestRuntime(runtimes, detectedJava)
                if (matchingRuntime == null) {
                    appendStatusLog("未找到任何Java运行时！\n")
                    runOnUiThread {
                        statusTitle?.text = "未找到Java运行时"
                        statusMessage?.text = "需要Java $detectedJava+，请先安装Java运行时"
                        Toast.makeText(this@GameActivity, "未找到Java运行时，请先安装Java", Toast.LENGTH_LONG).show()
                    }
                    return@thread
                }
                javaHome = matchingRuntime.path.absolutePath
                javaVersion = matchingRuntime.version
                appendStatusLog("自动选择: Java ${matchingRuntime.version} @ $javaHome\n")

                if (javaHome == null || !isValidJreDir(File(javaHome!!))) {
                    appendStatusLog("找不到有效的Java目录！\n")
                    runOnUiThread {
                        statusTitle?.text = "Java检测失败"
                        statusMessage?.text = "Java目录无效，请重新安装"
                        Toast.makeText(this@GameActivity, "Java运行时无效", Toast.LENGTH_LONG).show()
                    }
                    return@thread
                }

                val jreLibDir = File(javaHome!!, "lib")
                val libjliFound = findFileInDir(jreLibDir, "libjli.so")
                val libjvmFound = findFileInDir(jreLibDir, "libjvm.so")
                if (!libjliFound || !libjvmFound) {
                    appendStatusLog("JRE不完整！\n")
                    appendStatusLog("libjli.so: ${if (libjliFound) "✓" else "✗ 缺失"}\n")
                    appendStatusLog("libjvm.so: ${if (libjvmFound) "✓" else "✗ 缺失"}\n")
                    appendStatusLog("JRE目录内容: ${jreLibDir.absolutePath}\n")
                    jreLibDir.listFiles()?.forEach {
                        appendStatusLog("  ${it.name} (dir=${it.isDirectory})\n")
                    }
                    val serverDir = File(jreLibDir, "server")
                    if (serverDir.exists()) {
                        appendStatusLog("server目录内容:\n")
                        serverDir.listFiles()?.forEach {
                            appendStatusLog("    ${it.name}\n")
                        }
                    }
                    runOnUiThread {
                        statusTitle?.text = "JRE不完整"
                        statusMessage?.text = "缺少 ${if (!libjliFound) "libjli.so " else ""}${if (!libjvmFound) "libjvm.so " else ""}"
                        Toast.makeText(this@GameActivity, "JRE不完整，请重新安装Java运行时", Toast.LENGTH_LONG).show()
                    }
                    return@thread
                }

                appendStatusLog("Java运行时验证通过: $javaHome\n")
                appendStatusLog("libjli.so ✓, libjvm.so ✓\n")

                runOnUiThread {
                    statusTitle?.text = "正在启动游戏..."
                    statusMessage?.text = ""
                    statusProgressBar?.progress = 100
                }
                Thread.sleep(400)
                prepareAndLaunch(surface, width, height)
            } catch (e: Exception) {
                appendStatusLog("启动检查异常: ${e.message}\n")
                e.printStackTrace()
                runOnUiThread {
                    statusTitle?.text = "启动失败"
                    statusMessage?.text = e.message
                    Toast.makeText(this@GameActivity, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun findFileInDir(dir: File, name: String): Boolean {
        if (!dir.exists()) return false
        dir.listFiles()?.forEach {
            if (it.isFile && it.name == name) return true
            if (it.isDirectory && findFileInDir(it, name)) return true
        }
        return false
    }

    private fun findInheritedVersionJar(versionId: String, gameDir: File): File? {
        val versionDir = File(gameDir, "versions/$versionId")
        val jsonFile = File(versionDir, "$versionId.json")
        if (!jsonFile.exists()) return null
        try {
            val jsonText = jsonFile.readText()
            val jarMatch = Regex("\"jar\"\\s*:\\s*\"([^\"]+)\"").find(jsonText)
            val inheritMatch = Regex("\"inheritsFrom\"\\s*:\\s*\"([^\"]+)\"").find(jsonText)
            val parentId = jarMatch?.groupValues?.get(1) ?: inheritMatch?.groupValues?.get(1)
            if (parentId != null) {
                val parentDir = File(gameDir, "versions/$parentId")
                val parentJar = File(parentDir, "$parentId.jar")
                if (parentJar.exists() && parentJar.length() > 1024) return parentJar
                val parentJars = parentDir.listFiles()?.filter {
                    it.name.endsWith(".jar") && it.length() > 1024
                }
                if (parentJars != null && parentJars.isNotEmpty()) {
                    return parentJars.first()
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun isValidJreDir(dir: File): Boolean {
        val javaBin = File(dir, "bin/java")
        val javaSh = File(dir, "bin/java.sh")
        if (!javaBin.exists() && !javaSh.exists()) return false
        val libDir = File(dir, "lib")
        if (!libDir.exists()) return false
        return true
    }

    private fun copyNativesToInternalStorage(externalNativesDir: File): File {
        val internalNativesDir = File(filesDir, "natives")
        internalNativesDir.mkdirs()

        var copied = 0

        internalNativesDir.listFiles()?.forEach { existing ->
            if (existing.isFile && (existing.name.startsWith("liblwjgl") || existing.name == "libglfw.so")) {
                if (existing.delete()) {
                    appendStatusLog("清理残留库: ${existing.name}\n")
                }
            }
        }

        try {
            val nativeLibDir = File(applicationInfo.nativeLibraryDir)
            nativeLibDir.listFiles()?.forEach { srcFile ->
                if (srcFile.isFile && srcFile.name.endsWith(".so")) {
                    val dstFile = File(internalNativesDir, srcFile.name)
                    try {
                        srcFile.inputStream().use { input ->
                            dstFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        dstFile.setExecutable(true, false)
                        dstFile.setReadable(true, false)
                        copied++
                        if (srcFile.name == "libglfw.so") {
                            appendStatusLog("已部署自定义 libglfw.so (${dstFile.length()} bytes)\n")
                        }
                    } catch (e: Exception) {
                        appendStatusLog("复制 ${srcFile.name} 失败: ${e.message}\n")
                    }
                }
            }
        } catch (e: Exception) {
            appendStatusLog("复制APK native库失败: ${e.message}\n")
        }

        externalNativesDir.listFiles()?.forEach { srcFile ->
            if (srcFile.isFile && srcFile.name.endsWith(".so")) {
                if (srcFile.name.startsWith("liblwjgl") || srcFile.name == "libglfw.so") {
                    appendStatusLog("跳过GLIBC库: ${srcFile.name}\n")
                    return@forEach
                }
                val dstFile = File(internalNativesDir, srcFile.name)
                if (!dstFile.exists() || dstFile.length() != srcFile.length()) {
                    try {
                        srcFile.inputStream().use { input ->
                            dstFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        dstFile.setExecutable(true, false)
                        dstFile.setReadable(true, false)
                        copied++
                    } catch (_: Exception) {}
                }
            }
        }

        ensureJnaNativeLib(internalNativesDir)

        if (javaHome != null) {
            try {
                val jreDir = File(javaHome!!)
                val libFolder = findJreLibFolder(jreDir)
                if (libFolder != null) {
                    val freetypeSrc = File(libFolder, "libfreetype.so")
                    if (freetypeSrc.exists()) {
                        val freetypeDst = File(internalNativesDir, "libfreetype.so")
                        if (!freetypeDst.exists()) {
                            freetypeSrc.inputStream().use { input ->
                                freetypeDst.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            freetypeDst.setExecutable(true, false)
                            freetypeDst.setReadable(true, false)
                            copied++
                            appendStatusLog("从JRE复制libfreetype.so到内部存储\n")
                        }
                    }
                }
            } catch (e: Exception) {
                appendStatusLog("复制JRE libfreetype失败: ${e.message}\n")
            }
        }

        appendStatusLog("Natives复制到内部存储: $copied 个文件 -> ${internalNativesDir.absolutePath}\n")
        return internalNativesDir
    }

    private fun findJreLibFolder(jreDir: File): File? {
        val libDir = File(jreDir, "lib")
        if (!libDir.exists()) return null
        val archSubdirs = listOf("aarch64", "amd64", "arm", "i386")
        for (sub in archSubdirs) {
            val subDir = File(libDir, sub)
            if (subDir.exists() && File(subDir, "libfreetype.so").exists()) {
                return subDir
            }
        }
        if (File(libDir, "libfreetype.so").exists()) {
            return libDir
        }
        return null
    }

    private fun ensureJnaNativeLib(nativesDir: File) {
        val jnaLibFile = File(nativesDir, "libjnidispatch.so")
        val jnaDirLib = File(filesDir, "runtime/jna/libjnidispatch.so")

        val alreadyExists = jnaLibFile.exists() && jnaLibFile.length() > 1024
        val jnaDirExists = jnaDirLib.exists() && jnaDirLib.length() > 1024

        if (alreadyExists && jnaDirExists) return

        var extractSuccess = false
        var extractedBytes: Long = 0

        if (!alreadyExists) {
            try {
                val jnaJar = File(filesDir, "runtime/jna/jna.jar")
                if (jnaJar.exists()) {
                    java.util.jar.JarFile(jnaJar).use { jar ->
                        val entries = listOf(
                            "com/sun/jna/android-aarch64/libjnidispatch.so",
                            "com/sun/jna/android-arm/libjnidispatch.so",
                            "com/sun/jna/linux-aarch64/libjnidispatch.so",
                            "com/sun/jna/linux-arm/libjnidispatch.so"
                        )
                        for (name in entries) {
                            val entry = jar.getJarEntry(name) ?: continue
                            jar.getInputStream(entry).use { input ->
                                jnaLibFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            jnaLibFile.setExecutable(true, false)
                            jnaLibFile.setReadable(true, false)
                            extractSuccess = true
                            extractedBytes = jnaLibFile.length()
                            appendStatusLog("从jna.jar提取libjnidispatch.so: ${jnaLibFile.length()} bytes\n")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                appendStatusLog("从jna.jar提取libjnidispatch.so失败: ${e.message}\n")
            }

            if (!extractSuccess) {
                try {
                    val apkNativeDir = File(applicationInfo.nativeLibraryDir)
                    val apkJnaLib = File(apkNativeDir, "libjnidispatch.so")
                    if (apkJnaLib.exists() && apkJnaLib.length() > 1024) {
                        apkJnaLib.inputStream().use { input ->
                            jnaLibFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        jnaLibFile.setExecutable(true, false)
                        jnaLibFile.setReadable(true, false)
                        extractSuccess = true
                        extractedBytes = jnaLibFile.length()
                        appendStatusLog("从APK复制libjnidispatch.so: ${jnaLibFile.length()} bytes\n")
                    } else {
                        appendStatusLog("APK中libjnidispatch.so不存在: ${apkJnaLib.absolutePath} (exists=${apkJnaLib.exists()}, size=${apkJnaLib.length()})\n")
                    }
                } catch (e: Exception) {
                    appendStatusLog("从APK复制libjnidispatch.so异常: ${e.message}\n")
                }
            }

            if (!extractSuccess) {
                try {
                    assets.open("natives/libjnidispatch.so").use { input ->
                        jnaLibFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    jnaLibFile.setExecutable(true, false)
                    jnaLibFile.setReadable(true, false)
                    extractSuccess = true
                    extractedBytes = jnaLibFile.length()
                    appendStatusLog("从assets提取libjnidispatch.so: ${jnaLibFile.length()} bytes\n")
                } catch (e: Exception) {
                    appendStatusLog("从assets提取libjnidispatch.so失败: ${e.message}\n")
                }
            }
        } else {
            extractSuccess = true
            extractedBytes = jnaLibFile.length()
        }

        if (!extractSuccess) {
            appendStatusLog("警告: libjnidispatch.so 未找到！JNA可能无法加载！\n")
            return
        }

        if (!jnaDirExists) {
            try {
                jnaDirLib.parentFile?.mkdirs()
                File(nativesDir, "libjnidispatch.so").inputStream().use { input ->
                    jnaDirLib.outputStream().use { output -> input.copyTo(output) }
                }
                jnaDirLib.setExecutable(true, false)
                jnaDirLib.setReadable(true, false)
                appendStatusLog("复制libjnidispatch.so到jna目录: ${jnaDirLib.length()} bytes [jna.boot.library.path固定]\n")
            } catch (e: Exception) {
                appendStatusLog("复制libjnidispatch.so到jna目录失败: ${e.message}\n")
            }
        }
    }

    private fun extractDefaultNatives(nativesDir: File) {
        try {
            val archPaths = listOf(
                "natives/arm64-v8a",
                "natives/armeabi-v7a",
                "natives/x86_64",
                "natives/x86"
            )
            var extracted = 0
            for (archPath in archPaths) {
                try {
                    val files = assets.list(archPath) ?: continue
                    for (file in files) {
                        try {
                            assets.open("$archPath/$file").use { input ->
                                val target = File(nativesDir, file)
                                if (!target.exists()) {
                                    target.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                    extracted++
                                }
                            }
                        } catch (e: Exception) {
                            appendStatusLog("提取 $file 失败: ${e.message}\n")
                        }
                    }
                } catch (_: Exception) {}
            }
            if (extracted == 0) {
                try {
                    val rootFiles = assets.list("natives") ?: emptyArray()
                    for (file in rootFiles) {
                        if (file.endsWith(".so") && !file.startsWith("liblwjgl")) {
                            try {
                                assets.open("natives/$file").use { input ->
                                    val target = File(nativesDir, file)
                                    if (!target.exists()) {
                                        target.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                        extracted++
                                    }
                                }
                            } catch (e: Exception) {
                                appendStatusLog("提取 $file 失败: ${e.message}\n")
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            appendStatusLog("默认原生库提取: $extracted 个文件\n")
        } catch (e: Exception) {
            appendStatusLog("默认原生库提取异常: ${e.message}\n")
        }
    }

    private fun findJavaHome(): String? {
        val runtimes = JavaRuntimeManager.getInstalledRuntimes(this)
        val best = runtimes.maxByOrNull { it.version }
        if (best != null && isValidJreDir(best.path)) {
            return best.path.absolutePath
        }
        return null
    }

    private fun exportLog() {
        try {
            val logDir = File(getExternalFilesDir(null), "VerseExplorerX/logs")
            logDir.mkdirs()
            val logFile = File(logDir, "verse_debug_${System.currentTimeMillis()}.log")
            val sb = StringBuilder()
            sb.appendLine("=== Verse Explorer X Debug Log ===")
            sb.appendLine("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            sb.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            sb.appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            sb.appendLine("ABI: ${android.os.Build.SUPPORTED_ABIS?.joinToString()}")
            sb.appendLine()
            
            sb.appendLine("=== Internal Storage ===")
            val internalNatives = File(filesDir, "natives")
            sb.appendLine("Internal natives dir: ${internalNatives.absolutePath}")
            sb.appendLine("Exists: ${internalNatives.exists()}")
            internalNatives.listFiles()?.forEach { sb.appendLine("  ${it.name} (${it.length()} bytes)") }
            sb.appendLine()
            
            val internalTmp = File(filesDir, "tmp")
            sb.appendLine("Internal tmp dir: ${internalTmp.absolutePath}")
            sb.appendLine("Exists: ${internalTmp.exists()}")
            sb.appendLine()
            
            val internalRuntime = File(filesDir, "runtime")
            sb.appendLine("Internal runtime dir: ${internalRuntime.absolutePath}")
            sb.appendLine("Exists: ${internalRuntime.exists()}")
            internalRuntime.walkTopDown().maxDepth(3).forEach { 
                sb.appendLine("  ${it.absolutePath} ${if (it.isDirectory) "/" else "(${it.length()} bytes)"}")
            }
            sb.appendLine()
            
            val pluginsDir = File(filesDir, "plugins")
            sb.appendLine("Plugins dir: ${pluginsDir.absolutePath}")
            sb.appendLine("Exists: ${pluginsDir.exists()}")
            pluginsDir.listFiles()?.forEach { sb.appendLine("  ${it.name} (${it.length()} bytes)") }
            sb.appendLine()
            
            sb.appendLine("=== JRE ===")
            if (javaHome != null) {
                val jreDir = File(javaHome!!)
                sb.appendLine("JRE dir: ${jreDir.absolutePath}")
                sb.appendLine("Exists: ${jreDir.exists()}")
                val libDir = File(jreDir, "lib")
                sb.appendLine("lib/ exists: ${libDir.exists()}")
                val freetype = File(libDir, "libfreetype.so")
                sb.appendLine("libfreetype.so: ${freetype.exists()} (${if (freetype.exists()) freetype.length() else 0} bytes)")
                val aarch64Dir = File(libDir, "aarch64")
                sb.appendLine("lib/aarch64/ exists: ${aarch64Dir.exists()}")
                if (aarch64Dir.exists()) {
                    val ft2 = File(aarch64Dir, "libfreetype.so")
                    sb.appendLine("  libfreetype.so: ${ft2.exists()} (${if (ft2.exists()) ft2.length() else 0} bytes)")
                }
            }
            sb.appendLine()
            
            sb.appendLine("=== External Storage ===")
            val gameDir = File(getExternalFilesDir(null), "VerseExplorerX")
            sb.appendLine("Game dir: ${gameDir.absolutePath}")
            sb.appendLine("Exists: ${gameDir.exists()}")
            val extNatives = File(gameDir, "natives")
            sb.appendLine("External natives: ${extNatives.absolutePath}")
            sb.appendLine("Exists: ${extNatives.exists()}")
            extNatives.listFiles()?.forEach { sb.appendLine("  ${it.name} (${it.length()} bytes)") }
            sb.appendLine()
            
            sb.appendLine("=== Game Log ===")
            val gameLogFile = File(gameDir, "logs/game.log")
            if (gameLogFile.exists()) {
                val fullLog = gameLogFile.readText()
                if (fullLog.length > 50000) {
                    sb.appendLine(fullLog.takeLast(30000))
                    sb.appendLine()
                    sb.appendLine("... (日志共 ${fullLog.length} 字符, 已截取末尾 30000 字符)")
                } else {
                    sb.appendLine(fullLog)
                }
            } else {
                sb.appendLine("No game.log found")
            }
            sb.appendLine()
            
            sb.appendLine("=== Status Log ===")
            sb.appendLine(statusLog?.text ?: "empty")
            sb.appendLine()
            
            sb.appendLine("=== Native Debug Log ===")
            val nativeLogFile = File(getExternalFilesDir(null), "VerseExplorerX/logs/native_debug.log")
            if (nativeLogFile.exists()) {
                try {
                    val nativeLog = nativeLogFile.readText()
                    if (nativeLog.length > 80000) {
                        sb.appendLine(nativeLog.takeLast(50000))
                        sb.appendLine()
                        sb.appendLine("... (原生日志共 ${nativeLog.length} 字符, 已截取末尾 50000 字符)")
                    } else {
                        sb.appendLine(nativeLog)
                    }
                } catch (e: Exception) {
                    sb.appendLine("读取失败: ${e.message}")
                }
            } else {
                sb.appendLine("No native_debug.log found at ${nativeLogFile.absolutePath}")
            }
            
            logFile.writeText(sb.toString())
            Toast.makeText(this, "日志已导出到: ${logFile.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出日志失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun appendStatusLog(message: String) {
        VerseLogger.info(message.trimEnd(), "GameActivity")
        runOnUiThread {
            statusLog?.append(message)
            statusScrollView?.post {
                statusScrollView?.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun prepareAndLaunch(surface: Surface, width: Int, height: Int) {
        if (isGameRunning || javaHome == null) return

        val jreDir = File(javaHome!!)
        val gameDir = File(gameDirPath!!)
        val javaHomeDir = jreDir
        val librariesDir = File(gameDir, "libraries")
        val assetsDir = File(gameDir, "assets")
        val nativesDir = File(gameDir, "natives")
        nativesDir.mkdirs()

        val versionJson = File(gameDir, "versions/$versionId/$versionId.json")

        if (!versionJson.exists()) {
            appendStatusLog("版本JSON缺失: ${versionJson.absolutePath}\n")
            runOnUiThread {
                statusTitle?.text = "游戏文件缺失"
                statusMessage?.text = "版本JSON文件不存在，请先下载游戏版本"
                statusOverlay?.visibility = View.VISIBLE
                Toast.makeText(this@GameActivity, "请先下载游戏版本", Toast.LENGTH_LONG).show()
            }
            return
        }

        val versionJar = File(gameDir, "versions/$versionId/$versionId.jar")
        var foundJar = versionJar.exists() && versionJar.length() > 1024
        if (!foundJar) {
            val inheritedJar = findInheritedVersionJar(versionId!!, gameDir)
            if (inheritedJar != null) {
                appendStatusLog("找到继承JAR: ${inheritedJar.absolutePath}\n")
                foundJar = true
            }
        }
        if (!foundJar) {
            appendStatusLog("版本客户端缺失: ${versionJar.absolutePath} (${if (versionJar.exists()) versionJar.length() else "不存在"})\n")
            runOnUiThread {
                statusTitle?.text = "游戏文件缺失"
                statusMessage?.text = "客户端JAR文件不存在，请先下载游戏版本"
                statusOverlay?.visibility = View.VISIBLE
                Toast.makeText(this@GameActivity, "请先下载游戏版本", Toast.LENGTH_LONG).show()
            }
            return
        }

        val nativesExist = nativesDir.exists() && (nativesDir.listFiles()?.size ?: 0) > 0
        if (!nativesExist) {
            appendStatusLog("原生库缺失，尝试从assets提取...\n")
            try {
                extractDefaultNatives(nativesDir)
            } catch (e: Exception) {
                appendStatusLog("原生库提取失败: ${e.message}\n")
            }
            val afterExtract = nativesDir.listFiles()?.size ?: 0
            if (afterExtract == 0) {
                appendStatusLog("原生库仍为空！\n")
                runOnUiThread {
                    statusTitle?.text = "原生库缺失"
                    statusMessage?.text = "原生库文件不存在，请先下载游戏版本"
                    statusOverlay?.visibility = View.VISIBLE
                    Toast.makeText(this@GameActivity, "原生库缺失，请先下载游戏版本", Toast.LENGTH_LONG).show()
                }
                return
            }
            appendStatusLog("原生库提取完成: $afterExtract 个文件\n")
        }

        val internalNativesDir = copyNativesToInternalStorage(nativesDir)
        appendStatusLog("使用内部存储natives: ${internalNativesDir.absolutePath}\n")
        var mainClass = "net.minecraft.client.main.Main"
        var versionLibraries = emptyList<com.verse.explorer.core.version.Library>()
        var assetIndexId = versionId

        // PC端风格: 使用resolveVersionJson合并继承链
        val resolvedJson = resolveVersionJson(versionId!!)
        if (resolvedJson != null) {
            try {
                mainClass = resolvedJson.get("mainClass")?.asString ?: mainClass
                assetIndexId = resolvedJson.getAsJsonObject("assetIndex")?.get("id")?.asString ?: versionId
                val libsArray = resolvedJson.getAsJsonArray("libraries")
                if (libsArray != null) {
                    versionLibraries = parseLibrariesFromJson(libsArray)
                }
                appendStatusLog("版本JSON解析完成 (继承链): mainClass=$mainClass, ${versionLibraries.size}个库\n")
            } catch (e: Exception) {
                appendStatusLog("警告: 版本JSON解析异常: ${e.message}\n")
            }
        } else if (versionJson.exists()) {
            appendStatusLog("警告: resolveVersionJson返回null, 回退到直接读取...\n")
            try {
                val json = com.google.gson.GsonBuilder().setLenient().create().fromJson(
                    versionJson.readText(), com.google.gson.JsonObject::class.java
                )
                mainClass = json.get("mainClass")?.asString ?: mainClass
                assetIndexId = json.getAsJsonObject("assetIndex")?.get("id")?.asString ?: versionId
                val libsArray = json.getAsJsonArray("libraries")
                if (libsArray != null) {
                    versionLibraries = parseLibrariesFromJson(libsArray)
                }
            } catch (e: Exception) {
                appendStatusLog("警告: 版本JSON解析异常: ${e.message}\n")
            }
        }

        val version = MinecraftVersion(
            id = versionId!!, type = VersionType.RELEASE, url = "", releaseTime = "",
            mainClass = mainClass, libraries = versionLibraries,
            assetIndex = com.verse.explorer.core.version.AssetIndex(
                id = assetIndexId ?: versionId!!, url = "", totalSize = 0, known = false
            )
        )

        val settingsRepo = com.verse.explorer.app.data.SettingsRepository(this)
        val storedUuid = settingsRepo.getCustomSetting("player_uuid")
            ?: java.util.UUID.randomUUID().toString().also { settingsRepo.setCustomSetting("player_uuid", it) }
        val playerUuid = java.util.UUID.fromString(storedUuid)

        val profile = GameProfile(
            id = playerUuid.toString(), name = username,
            versionId = versionId!!, gameDir = gameDir
        )

        val maxMem = settingsRepo.getMaxMemory()
        val minMem = settingsRepo.getMinMemory()
        val gc = if (settingsRepo.getGcType().isNotEmpty()) settingsRepo.getGcType() else gcType
        val customArgs = settingsRepo.getCustomJvmArgs()

        val config = LaunchConfiguration(
            version = version, profile = profile, javaHome = javaHomeDir,
            gameDir = gameDir, assetsDir = assetsDir, librariesDir = librariesDir,
            nativesDir = internalNativesDir, width = gameWidth, height = gameHeight,
            maxMemory = maxMem, minMemory = minMem, fullscreen = true,
            gcType = gc, customJvmArgs = customArgs, renderer = renderer,
            javaVersion = javaVersion, accessToken = accessToken, userType = userType
        )

        appendStatusLog("设置ZLBridge...\n")
        val gameLogDir = File(gameDir!!, "logs")
        gameLogDir.mkdirs()
        val gameLogFile = File(gameLogDir, "game.log")
        if (gameLogFile.exists()) {
            gameLogFile.delete()
            appendStatusLog("清除旧日志\n")
        }
        zlBridge = ZLBridge(this@GameActivity)
        zlBridge!!.setLogPath(gameLogFile.absolutePath)
        zlBridge!!.setGameDir(gameDir.absolutePath)
        zlBridge!!.setJava(javaVersion.toString())
        VerseLogger.info("ZLBridge log path: ${gameLogFile.absolutePath}", "GameActivity")
        com.verse.explorer.launcher.bridge.CallbackBridge.setFCLBridge(zlBridge)

        // Execute bridge on UI thread since it accesses the Surface
        runOnUiThread {
            try {
                zlBridge!!.execute(surface, this@GameActivity)

                appendStatusLog("设置启动环境 (FCL-style)...\n")
                try {
                    val nativeLibDir = applicationInfo.nativeLibraryDir
                    com.verse.explorer.app.setup.RuntimeSetupManager.loadPaths(this@GameActivity)
                    val runtimeDir = this@GameActivity.getDir("runtime", 0).absolutePath
                    val javaPath = File(runtimeDir, "java").absolutePath

                    zlBridge!!.setupEnvironment(
                        javaHome = javaHomeDir.absolutePath,
                        gameDir = gameDir.absolutePath,
                        nativesDir = internalNativesDir.absolutePath,
                        nativeLibDir = nativeLibDir
                    )

                    appendStatusLog("==================== Start DLOPEN ====================\n")
                    appendStatusLog("Renderer: ${renderer}\n")
                    appendStatusLog("Device: ${android.os.Build.DEVICE}\n")
                    appendStatusLog("Architecture: ${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}\n")
                    appendStatusLog("CPU: ${android.os.Build.HARDWARE}\n")
                    appendStatusLog("Android SDK: ${android.os.Build.VERSION.SDK_INT}\n")
                    appendStatusLog("Language: ${java.util.Locale.getDefault()}\n")
                    appendStatusLog("环境设置完成 (FCL-style)\n")
                } catch (e: Exception) {
                    appendStatusLog("警告: 环境设置异常: ${e.message}\n")
                }

                // Now launch JVM
                thread(name = "GameLaunchThread") {
                    try {
                        val launchStartTime = System.currentTimeMillis()
                        appendStatusLog("启动JVM... [V2.7] ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")
                        
                        appendStatusLog("[1/7] 创建 MinecraftLauncher...\n")
                        minecraftLauncher = MinecraftLauncher(this, zlBridge)
                        appendStatusLog("[1/7] MinecraftLauncher 创建完成\n")

                        appendStatusLog("[2/7] 设置原生日志路径...\n")
                        try {
                            val nativeLogDir = File(getExternalFilesDir(null), "VerseExplorerX/logs")
                            nativeLogDir.mkdirs()
                            val nativeLogFile = File(nativeLogDir, "native_debug.log")
                            TerracottaAndroidAPI.nativeSetLogPath(nativeLogFile.absolutePath)
                            VerseLogger.info("Native log path: ${nativeLogFile.absolutePath}", "GameActivity")
                        } catch (e: Exception) {
                            VerseLogger.warn("Failed to set native log path: ${e.message}", "GameActivity")
                        }

                        appendStatusLog("[3/7] 预加载原生库...\n")
                        // Pre-load pojavexec (contains all LWJGL JNI + GLFW implementation)
                        try {
                            System.loadLibrary("pojavexec")
                            appendStatusLog("  ✓ libpojavexec.so\n")
                            VerseLogger.info("libpojavexec.so pre-loaded successfully", "GameActivity")
                        } catch (e: UnsatisfiedLinkError) {
                            appendStatusLog("  ✗ libpojavexec.so: ${e.message}\n")
                            VerseLogger.warn("libpojavexec.so load failed: ${e.message}", "GameActivity")
                        }

                        // Pre-load Android JNA native to prevent GLIBC self-extract
                        try {
                            System.loadLibrary("jnidispatch")
                            appendStatusLog("  ✓ libjnidispatch.so\n")
                            VerseLogger.info("Android libjnidispatch.so pre-loaded successfully", "GameActivity")
                        } catch (e: UnsatisfiedLinkError) {
                            appendStatusLog("  ✗ libjnidispatch.so: ${e.message}\n")
                            VerseLogger.warn("libjnidispatch.so load failed: ${e.message}", "GameActivity")
                        }

                        // Pre-load Android LWJGL native to replace GLIBC version
                        try {
                            System.loadLibrary("lwjgl")
                            appendStatusLog("  ✓ liblwjgl.so\n")
                            VerseLogger.info("Android liblwjgl.so pre-loaded successfully", "GameActivity")
                        } catch (e: UnsatisfiedLinkError) {
                            appendStatusLog("警告: liblwjgl.so 加载失败 (将使用natives): ${e.message}\n")
                            VerseLogger.warn("liblwjgl.so load failed, will use natives: ${e.message}", "GameActivity")
                        }

                        appendStatusLog("[4/7] 设置启动回调...\n")
                        minecraftLauncher!!.setLaunchCallback(object : com.verse.explorer.launcher.launch.LaunchCallback {
                            override fun onGameExit(code: Int) {
                                if (!exitHandled.compareAndSet(false, true)) return
                                isGameRunning = false
                                appendStatusLog("游戏进程退出 (code=$code)\n")
                                runOnUiThread { onExit(code) }
                            }
                        })

                        appendStatusLog("[5/7] 调用 launch()...\n")
                        VerseLogger.info("Calling launch() at ${System.currentTimeMillis()}", "GameActivity")
                        val launchProcess = minecraftLauncher!!.launch(config, launchMethod)
                        val launchElapsed = System.currentTimeMillis() - launchStartTime
                        appendStatusLog("[6/7] launch() 返回: ${if (launchProcess != null) "成功" else "null"} (${launchElapsed}ms)\n")
                        VerseLogger.info("launch() returned: ${launchProcess != null}, elapsed=${launchElapsed}ms", "GameActivity")

                        if (launchProcess != null) {
                            isGameRunning = true
                            appendStatusLog("游戏启动成功 (PID=${launchProcess.pid})\n")
                            runOnUiThread {
                                statusOverlay?.visibility = View.GONE
                            }
                            
                            thread(name = "GameExitMonitor") {
                                try {
                                    while (minecraftLauncher?.isRunning() == true) {
                                        Thread.sleep(500)
                                    }
                                    if (!exitHandled.compareAndSet(false, true)) return@thread
                                    val exitCode = zlBridge?.getExitCode() ?: TerracottaAndroidAPI.nativeGetExitCode()
                                    appendStatusLog("游戏已退出 (monitor: code=$exitCode)\n")
                                    VerseLogger.info("Game exited (monitor) with code=$exitCode", "GameActivity")
                                    runOnUiThread {
                                        isGameRunning = false
                                        statusOverlay?.visibility = View.VISIBLE
                                        statusTitle?.text = "游戏已退出"
                                        statusMessage?.text = "退出码: $exitCode"
                                        if (exitCode != 0) {
                                            Toast.makeText(this@GameActivity, "游戏崩溃退出 (代码: $exitCode)", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(this@GameActivity, "游戏已退出", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    appendStatusLog("退出监控异常: ${e.message}\n")
                                }
                            }
                        } else {
                            appendStatusLog("[7/7] JVM启动失败！launch()返回null\n")
                            appendStatusLog("JRE目录: ${jreDir.absolutePath}\n")
                            appendStatusLog("JRE存在: ${jreDir.exists()}\n")
                            val libjli = File(jreDir, "lib/libjli.so")
                            appendStatusLog("libjli.so: ${libjli.exists()} (${if (libjli.exists()) libjli.length() else 0})\n")
                            val nativeLogFile = File(getExternalFilesDir(null), "VerseExplorerX/logs/native_debug.log")
                            appendStatusLog("原生日志: ${nativeLogFile.absolutePath}\n")
                            appendStatusLog("原生日志存在: ${nativeLogFile.exists()}\n")
                            if (nativeLogFile.exists()) {
                                try {
                                    val tail = nativeLogFile.readLines().takeLast(20)
                                    appendStatusLog("=== 原生日志最后20行 ===\n")
                                    tail.forEach { appendStatusLog("  $it\n") }
                                } catch (_: Exception) {}
                            }

                            runOnUiThread {
                                Toast.makeText(this@GameActivity, "启动失败：JVM无法创建", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        appendStatusLog("启动异常: ${e.javaClass.simpleName}: ${e.message}\n")
                        e.printStackTrace()
                        VerseLogger.error("Launch exception: ${e.javaClass.simpleName}: ${e.message}\n${e.stackTraceToString()}", "GameActivity")
                        runOnUiThread {
                            Toast.makeText(this@GameActivity, "启动错误: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                appendStatusLog("桥接异常: ${e.message}\n")
                e.printStackTrace()
            }
        }
    }

    private fun parseLibrariesFromJson(libsArray: com.google.gson.JsonArray): List<com.verse.explorer.core.version.Library> {
        val libraries = mutableListOf<com.verse.explorer.core.version.Library>()

        for (libElement in libsArray) {
            try {
                val lib = libElement.asJsonObject
                val name = lib.get("name")?.asString ?: continue

                val rules = lib.getAsJsonArray("rules")
                if (rules != null) {
                    var libAllowed = true
                    for (ruleElement in rules) {
                        val rule = ruleElement.asJsonObject
                        val action = rule.get("action")?.asString ?: continue
                        val os = rule.getAsJsonObject("os")
                        val features = rule.getAsJsonObject("features")
                        if (features != null) continue
                        if (os != null) {
                            val osName = os.get("name")?.asString ?: ""
                            if (osName == "linux" || osName == "osx" || osName.isEmpty()) {
                                libAllowed = action == "allow"
                            }
                        } else {
                            libAllowed = action == "allow"
                        }
                    }
                    if (!libAllowed) continue
                }

                val downloads = lib.getAsJsonObject("downloads")
                val artifact = downloads?.getAsJsonObject("artifact")

                val artifactObj = if (artifact != null) {
                    com.verse.explorer.core.version.Artifact(
                        path = artifact.get("path")?.asString ?: "",
                        sha1 = artifact.get("sha1")?.asString ?: "",
                        size = artifact.get("size")?.asLong ?: 0L,
                        url = artifact.get("url")?.asString ?: ""
                    )
                } else {
                    val url = lib.get("url")?.asString
                    if (url != null && name.count { it == ':' } >= 2) {
                        val parts = name.split(":")
                        val group = parts[0].replace(".", "/")
                        val art = parts[1]
                        val ver = parts[2]
                        val path = "$group/$art/$ver/$art-$ver.jar"
                        com.verse.explorer.core.version.Artifact(
                            path = path, url = "${url}$path"
                        )
                    } else null
                }

                val classifiers = mutableMapOf<String, com.verse.explorer.core.version.Artifact>()
                val classifiersJson = downloads?.getAsJsonObject("classifiers")
                if (classifiersJson != null) {
                    for ((key, value) in classifiersJson.entrySet()) {
                        try {
                            val c = value.asJsonObject
                            classifiers[key] = com.verse.explorer.core.version.Artifact(
                                path = c.get("path")?.asString ?: "",
                                sha1 = c.get("sha1")?.asString ?: "",
                                size = c.get("size")?.asLong ?: 0L,
                                url = c.get("url")?.asString ?: ""
                            )
                        } catch (_: Exception) {}
                    }
                }

                val nativesMap = mutableMapOf<String, String>()
                val nativesJson = lib.getAsJsonObject("natives")
                if (nativesJson != null) {
                    for ((key, value) in nativesJson.entrySet()) {
                        nativesMap[key] = value.asString
                    }
                }

                libraries.add(com.verse.explorer.core.version.Library(
                    name = name,
                    downloads = com.verse.explorer.core.version.LibraryDownloads(
                        artifact = artifactObj,
                        classifiers = classifiers.ifEmpty { null }
                    ),
                    natives = nativesMap.ifEmpty { null }
                ))
            } catch (_: Exception) {}
        }

        return libraries
    }

    private fun stopGame() {
        isGameRunning = false
        minecraftLauncher?.forceStop()
        zlBridge?.destroyJVM()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouchEvent(event: MotionEvent) {
        val bridge = zlBridge ?: return

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownTime = System.currentTimeMillis()
                touchDownX = event.x
                touchDownY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                isLongPress = false
                val x = event.x.toInt()
                val y = event.y.toInt()
                bridge.pushEventPointer(x, y)
                bridge.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_1, true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val px = event.getX(pointerIndex).toInt()
                val py = event.getY(pointerIndex).toInt()
                bridge.pushEventPointer(px, py)
                bridge.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_3, true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchDownX
                val dy = event.y - touchDownY
                if (!isLongPress && System.currentTimeMillis() - touchDownTime > LONG_PRESS_THRESHOLD
                    && (dx * dx + dy * dy) < MOVE_THRESHOLD * MOVE_THRESHOLD) {
                    isLongPress = true
                    bridge.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_1, false)
                    bridge.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_2, true)
                }
                if (isGrabbing) {
                    val deltaX = (event.x - lastTouchX).toInt()
                    val deltaY = (event.y - lastTouchY).toInt()
                    lastTouchX = event.x
                    lastTouchY = event.y
                    CallbackBridge.sendCursorDelta(deltaX.toDouble(), deltaY.toDouble())
                } else {
                    bridge.pushEventPointer(event.x.toInt(), event.y.toInt())
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isGrabbing) {
                    bridge.pushEventPointer(event.x.toInt(), event.y.toInt())
                }
                if (isLongPress) {
                    bridge.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_2, false)
                } else {
                    bridge.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_1, false)
                }
                isLongPress = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val upIndex = event.actionIndex
                val upX = event.getX(upIndex).toInt()
                val upY = event.getY(upIndex).toInt()
                bridge.pushEventPointer(upX, upY)
                bridge.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_3, false)
            }
        }
    }

    private fun setupImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveMode()
    }

    override fun onDestroy() {
        stopGame()
        CallbackBridge.nativeSetInputReady(false)
        CallbackBridge.setFCLBridge(null)
        ZLBridgeContext.context = null
        VerseLogger.close()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupImmersiveMode()
    }

    override fun onLog(log: String) {
        VerseLogger.info(log.replace("\n", ""), "Minecraft")
    }

    override fun onExit(code: Int) {
        isGameRunning = false
        VerseLogger.info("Game exit callback triggered: code=$code", "GameActivity")
        runOnUiThread {
            statusOverlay?.visibility = View.VISIBLE
            statusTitle?.text = "游戏已退出 (代码: $code)"
            statusMessage?.text = if (code != 0) "游戏异常退出" else "游戏正常退出"
            if (code != 0) {
                Toast.makeText(this, "游戏异常退出 (代码: $code)", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "游戏已退出", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val bridge = zlBridge ?: return super.onKeyDown(keyCode, event)
        val glfwKey = androidKeyToGlfw(keyCode)
        if (glfwKey != LwjglGlfwKeycode.GLFW_KEY_UNKNOWN) {
            bridge.pushEventKey(glfwKey, event?.unicodeChar ?: 0, true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val bridge = zlBridge ?: return super.onKeyUp(keyCode, event)
        val glfwKey = androidKeyToGlfw(keyCode)
        if (glfwKey != LwjglGlfwKeycode.GLFW_KEY_UNKNOWN) {
            bridge.pushEventKey(glfwKey, event?.unicodeChar ?: 0, false)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun androidKeyToGlfw(keyCode: Int): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> LwjglGlfwKeycode.GLFW_KEY_SPACE
            KeyEvent.KEYCODE_A -> LwjglGlfwKeycode.GLFW_KEY_A
            KeyEvent.KEYCODE_B -> LwjglGlfwKeycode.GLFW_KEY_B
            KeyEvent.KEYCODE_C -> LwjglGlfwKeycode.GLFW_KEY_C
            KeyEvent.KEYCODE_D -> LwjglGlfwKeycode.GLFW_KEY_D
            KeyEvent.KEYCODE_E -> LwjglGlfwKeycode.GLFW_KEY_E
            KeyEvent.KEYCODE_F -> LwjglGlfwKeycode.GLFW_KEY_F
            KeyEvent.KEYCODE_G -> LwjglGlfwKeycode.GLFW_KEY_G
            KeyEvent.KEYCODE_H -> LwjglGlfwKeycode.GLFW_KEY_H
            KeyEvent.KEYCODE_I -> LwjglGlfwKeycode.GLFW_KEY_I
            KeyEvent.KEYCODE_J -> LwjglGlfwKeycode.GLFW_KEY_J
            KeyEvent.KEYCODE_K -> LwjglGlfwKeycode.GLFW_KEY_K
            KeyEvent.KEYCODE_L -> LwjglGlfwKeycode.GLFW_KEY_L
            KeyEvent.KEYCODE_M -> LwjglGlfwKeycode.GLFW_KEY_M
            KeyEvent.KEYCODE_N -> LwjglGlfwKeycode.GLFW_KEY_N
            KeyEvent.KEYCODE_O -> LwjglGlfwKeycode.GLFW_KEY_O
            KeyEvent.KEYCODE_P -> LwjglGlfwKeycode.GLFW_KEY_P
            KeyEvent.KEYCODE_Q -> LwjglGlfwKeycode.GLFW_KEY_Q
            KeyEvent.KEYCODE_R -> LwjglGlfwKeycode.GLFW_KEY_R
            KeyEvent.KEYCODE_S -> LwjglGlfwKeycode.GLFW_KEY_S
            KeyEvent.KEYCODE_T -> LwjglGlfwKeycode.GLFW_KEY_T
            KeyEvent.KEYCODE_U -> LwjglGlfwKeycode.GLFW_KEY_U
            KeyEvent.KEYCODE_V -> LwjglGlfwKeycode.GLFW_KEY_V
            KeyEvent.KEYCODE_W -> LwjglGlfwKeycode.GLFW_KEY_W
            KeyEvent.KEYCODE_X -> LwjglGlfwKeycode.GLFW_KEY_X
            KeyEvent.KEYCODE_Y -> LwjglGlfwKeycode.GLFW_KEY_Y
            KeyEvent.KEYCODE_Z -> LwjglGlfwKeycode.GLFW_KEY_Z
            KeyEvent.KEYCODE_0 -> LwjglGlfwKeycode.GLFW_KEY_0
            KeyEvent.KEYCODE_1 -> LwjglGlfwKeycode.GLFW_KEY_1
            KeyEvent.KEYCODE_2 -> LwjglGlfwKeycode.GLFW_KEY_2
            KeyEvent.KEYCODE_3 -> LwjglGlfwKeycode.GLFW_KEY_3
            KeyEvent.KEYCODE_4 -> LwjglGlfwKeycode.GLFW_KEY_4
            KeyEvent.KEYCODE_5 -> LwjglGlfwKeycode.GLFW_KEY_5
            KeyEvent.KEYCODE_6 -> LwjglGlfwKeycode.GLFW_KEY_6
            KeyEvent.KEYCODE_7 -> LwjglGlfwKeycode.GLFW_KEY_7
            KeyEvent.KEYCODE_8 -> LwjglGlfwKeycode.GLFW_KEY_8
            KeyEvent.KEYCODE_9 -> LwjglGlfwKeycode.GLFW_KEY_9
            KeyEvent.KEYCODE_ENTER -> LwjglGlfwKeycode.GLFW_KEY_ENTER
            KeyEvent.KEYCODE_TAB -> LwjglGlfwKeycode.GLFW_KEY_TAB
            KeyEvent.KEYCODE_DEL -> LwjglGlfwKeycode.GLFW_KEY_BACKSPACE
            KeyEvent.KEYCODE_ESCAPE -> LwjglGlfwKeycode.GLFW_KEY_ESCAPE
            KeyEvent.KEYCODE_SHIFT_LEFT -> LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT
            KeyEvent.KEYCODE_SHIFT_RIGHT -> LwjglGlfwKeycode.GLFW_KEY_RIGHT_SHIFT
            KeyEvent.KEYCODE_CTRL_LEFT -> LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL
            KeyEvent.KEYCODE_CTRL_RIGHT -> LwjglGlfwKeycode.GLFW_KEY_RIGHT_CONTROL
            KeyEvent.KEYCODE_ALT_LEFT -> LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT
            KeyEvent.KEYCODE_ALT_RIGHT -> LwjglGlfwKeycode.GLFW_KEY_RIGHT_ALT
            KeyEvent.KEYCODE_CAPS_LOCK -> LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK
            KeyEvent.KEYCODE_NUM_LOCK -> LwjglGlfwKeycode.GLFW_KEY_NUM_LOCK
            KeyEvent.KEYCODE_SCROLL_LOCK -> LwjglGlfwKeycode.GLFW_KEY_SCROLL_LOCK
            KeyEvent.KEYCODE_F1 -> LwjglGlfwKeycode.GLFW_KEY_F1
            KeyEvent.KEYCODE_F2 -> LwjglGlfwKeycode.GLFW_KEY_F2
            KeyEvent.KEYCODE_F3 -> LwjglGlfwKeycode.GLFW_KEY_F3
            KeyEvent.KEYCODE_F4 -> LwjglGlfwKeycode.GLFW_KEY_F4
            KeyEvent.KEYCODE_F5 -> LwjglGlfwKeycode.GLFW_KEY_F5
            KeyEvent.KEYCODE_F6 -> LwjglGlfwKeycode.GLFW_KEY_F6
            KeyEvent.KEYCODE_F7 -> LwjglGlfwKeycode.GLFW_KEY_F7
            KeyEvent.KEYCODE_F8 -> LwjglGlfwKeycode.GLFW_KEY_F8
            KeyEvent.KEYCODE_F9 -> LwjglGlfwKeycode.GLFW_KEY_F9
            KeyEvent.KEYCODE_F10 -> LwjglGlfwKeycode.GLFW_KEY_F10
            KeyEvent.KEYCODE_F11 -> LwjglGlfwKeycode.GLFW_KEY_F11
            KeyEvent.KEYCODE_F12 -> LwjglGlfwKeycode.GLFW_KEY_F12
            KeyEvent.KEYCODE_DPAD_UP -> LwjglGlfwKeycode.GLFW_KEY_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> LwjglGlfwKeycode.GLFW_KEY_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> LwjglGlfwKeycode.GLFW_KEY_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> LwjglGlfwKeycode.GLFW_KEY_RIGHT
            KeyEvent.KEYCODE_INSERT -> LwjglGlfwKeycode.GLFW_KEY_INSERT
            KeyEvent.KEYCODE_FORWARD_DEL -> LwjglGlfwKeycode.GLFW_KEY_DELETE
            KeyEvent.KEYCODE_MOVE_HOME -> LwjglGlfwKeycode.GLFW_KEY_HOME
            KeyEvent.KEYCODE_MOVE_END -> LwjglGlfwKeycode.GLFW_KEY_END
            KeyEvent.KEYCODE_PAGE_UP -> LwjglGlfwKeycode.GLFW_KEY_PAGE_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> LwjglGlfwKeycode.GLFW_KEY_PAGE_DOWN
            KeyEvent.KEYCODE_APOSTROPHE -> LwjglGlfwKeycode.GLFW_KEY_APOSTROPHE
            KeyEvent.KEYCODE_COMMA -> LwjglGlfwKeycode.GLFW_KEY_COMMA
            KeyEvent.KEYCODE_MINUS -> LwjglGlfwKeycode.GLFW_KEY_MINUS
            KeyEvent.KEYCODE_PERIOD -> LwjglGlfwKeycode.GLFW_KEY_PERIOD
            KeyEvent.KEYCODE_SLASH -> LwjglGlfwKeycode.GLFW_KEY_SLASH
            KeyEvent.KEYCODE_SEMICOLON -> LwjglGlfwKeycode.GLFW_KEY_SEMICOLON
            KeyEvent.KEYCODE_EQUALS -> LwjglGlfwKeycode.GLFW_KEY_EQUAL
            KeyEvent.KEYCODE_LEFT_BRACKET -> LwjglGlfwKeycode.GLFW_KEY_LEFT_BRACKET
            KeyEvent.KEYCODE_RIGHT_BRACKET -> LwjglGlfwKeycode.GLFW_KEY_RIGHT_BRACKET
            KeyEvent.KEYCODE_BACKSLASH -> LwjglGlfwKeycode.GLFW_KEY_BACKSLASH
            KeyEvent.KEYCODE_GRAVE -> LwjglGlfwKeycode.GLFW_KEY_GRAVE_ACCENT
            else -> LwjglGlfwKeycode.GLFW_KEY_UNKNOWN
        }
    }

    override fun onCursorModeChange(mode: Int) {
        isGrabbing = mode == ZLBridge.CursorDisabled
    }

    override fun onGraphicOutput() {}
}
