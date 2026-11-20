package com.verse.explorer.app.horizon

import android.content.Context
import com.google.gson.JsonParser
import com.verse.explorer.core.java.JavaRuntimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Rust 版前置准备：在手机上把 Horizon 需要的派生包生成出来。
 *
 * 上游 prepare 是 Rust 桌面二进制，手机跑不了，但它做的只是「下 server.jar → 跑官方数据生成器
 * → 挑四样产物打包」。启动器自带 java25 与下载能力，所以这段可以完整复刻，用户不需要电脑。
 *
 * 流程：26.3 版本 JSON → server.jar(62MB) → datagen（Java 25）→ horizon-derived.zip
 *      → 交给引擎 nativeStart 下载约 700MB 游戏文件。
 */
object HorizonPrep {

    enum class Stage { CHECK, VERSION_JSON, SERVER_JAR, DATAGEN, PACK, ENGINE, READY }

    data class Progress(
        val stage: Stage,
        val text: String,
        val fraction: Float
    )

    private const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private const val TARGET_VERSION = "26.3"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Horizon 的游戏家目录（已与启动器其它数据隔离） */
    fun home(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "horizon").apply { mkdirs() }
    }

    fun derivedZip(context: Context): File = File(home(context), "horizon-derived.zip")

    private const val PREFS = "horizon_state"
    private const val KEY_INSTALLED = "installed"

    /** 准备流程跑完一次后置位，避免每次都重新探测（探测要加载引擎 so） */
    fun markInstalled(context: Context, installed: Boolean = true) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_INSTALLED, installed).apply()
    }

    fun isMarkedInstalled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_INSTALLED, false)

    /** 已安装 = 标记过且引擎文件确实就绪（文件被清掉时能自愈回到未安装） */
    fun isInstalled(context: Context): Boolean = isMarkedInstalled(context) && isEngineReady(context)

    /** 派生包已就绪（引擎 nativeStart 会用它） */
    fun hasDerivedPack(context: Context): Boolean = derivedZip(context).isFile

    /** 引擎侧是否已准备完毕（游戏文件齐了） */
    fun isEngineReady(context: Context): Boolean =
        runCatching { dev.horizonlab.horizon.SetupActivity.nativeReady(home(context).absolutePath) == 1 }
            .getOrDefault(false)

    /**
     * 生成派生包。已存在则直接返回。
     */
    suspend fun ensureDerivedPack(
        context: Context,
        onProgress: (Progress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val zip = derivedZip(context)
            if (zip.isFile && zip.length() > 0) {
                onProgress(Progress(Stage.PACK, "派生包已就绪", 1f))
                return@runCatching zip
            }

            HorizonPrepLog.append(context, "开始准备：home=${home(context).absolutePath}")
            onProgress(Progress(Stage.VERSION_JSON, "读取 26.3 版本信息…", 0f))
            val versionJson = fetchVersionJson()
            val serverJarInfo = HorizonDataPack.parseServerJar(versionJson)
                ?: error("26.3 版本信息里没有 server.jar 下载地址")
            HorizonPrepLog.append(context, "server.jar: ${serverJarInfo.size / 1048576}MB ${serverJarInfo.url}")

            val work = File(home(context), "prep-work").apply { mkdirs() }
            val serverJar = File(work, "server.jar")

            if (!serverJar.isFile || serverJar.length() != serverJarInfo.size) {
                downloadServerJar(serverJarInfo, serverJar) { done, total ->
                    val fraction = if (total > 0) done.toFloat() / total else 0f
                    onProgress(
                        Progress(
                            Stage.SERVER_JAR,
                            "下载 server.jar ${done / 1048576} / ${total / 1048576} MB",
                            fraction
                        )
                    )
                }
            }

            val java = JavaRuntimeManager.getJavaExecutable(context, "java25")
                ?: error("没有找到 Java 25 运行时，请先在「Java」页安装")
            val jreHome = java.parentFile?.parentFile ?: error("Java 运行时目录异常：${java.absolutePath}")
            HorizonPrepLog.append(context, "jre: ${jreHome.absolutePath}")

            onProgress(Progress(Stage.DATAGEN, "解包服务端依赖…", 0f))
            val libRelativePaths = extractBundlerToLibraries(context, serverJar)
            HorizonPrepLog.append(context, "依赖库=${libRelativePaths.size} 个")

            // 合成一个 inheritsFrom 26.3 的版本，用正常启动链路跑数据生成器
            val gameDir = com.verse.explorer.core.config.GameConfig.getGameDir(context)
            val versionsDir = com.verse.explorer.core.config.GameConfig.getVersionsDir(gameDir)
            val parentId = detectParentVersion(versionsDir)
            HorizonPrepLog.append(
                context,
                if (parentId != null) "父版本=${parentId}（继承其库与资源）"
                else "未找到 26.3 安装，改用独立版本（仅用刚解出的 ${libRelativePaths.size} 个依赖）"
            )
            val datagenJson = HorizonDatagenVersion.buildVersionJson(libRelativePaths, parentId)
            HorizonDatagenVersion.writeVersionFile(
                com.verse.explorer.core.config.GameConfig.getVersionsDir(gameDir), datagenJson
            )
            HorizonPrepLog.append(context, "已写出 ${HorizonDatagenVersion.VERSION_ID} 版本（主类 net.minecraft.data.Main）")

            onProgress(Progress(Stage.DATAGEN, "正在生成游戏数据（约 2-5 分钟）…", 0f))
            val reports = File(gameDir, "out/reports")
            reports.deleteRecursively()
            com.verse.explorer.app.launch.GameLaunchController(context)
                .launchGame(HorizonDatagenVersion.VERSION_ID)
            HorizonPrepLog.append(context, "已通过正常启动链路拉起 datagen 版本")

            val deadline = System.currentTimeMillis() + 20 * 60 * 1000
            while (System.currentTimeMillis() < deadline) {
                if (File(reports, "registries.json").isFile) break
                Thread.sleep(1500)
            }
            if (!File(reports, "registries.json").isFile) {
                // datagen 是通过正常启动链路跑的，真正的异常在 FCL 的游戏日志里
                val fclLog = File(gameDir, "logs/latest.log")
                val tail = fclLog.takeIf { it.isFile }
                    ?.readLines()?.takeLast(40)?.joinToString("\n")
                    .orEmpty()
                if (tail.isNotBlank()) HorizonPrepLog.append(context, "datagen 日志尾部:\n$tail")
                error(
                    "数据生成失败（未产出 reports）。" +
                        if (tail.isBlank()) "未找到 logs/latest.log" else "日志尾部见下方"
                )
            }
            HorizonPrepLog.append(context, "datagen 产物就绪")

            onProgress(Progress(Stage.PACK, "打包派生数据…", 0.5f))
            HorizonPrepLog.append(context, "datagen 产物目录存在=${reports.isDirectory} ${reports.listFiles()?.size ?: 0} 项")
            val entries = HorizonDataPack.buildDerivedZip(reports, zip)
            HorizonPrepLog.append(context, "派生包条目=$entries size=${zip.length()}")
            if (entries == 0) error("数据生成没有产出可打包的文件，请查看日志")
            work.deleteRecursively()
            onProgress(Progress(Stage.PACK, "派生包已生成（$entries 个文件）", 1f))
            zip
        }
    }

    /** 交给引擎下载/解包约 700MB 游戏文件，进度来自 nativeStatus() */
    suspend fun runEngineSetup(
        context: Context,
        onProgress: (Progress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val home = home(context)
            if (isEngineReady(context)) {
                onProgress(Progress(Stage.READY, "游戏文件已就绪", 1f))
                return@runCatching
            }
            onProgress(Progress(Stage.ENGINE, "正在准备游戏文件（约 700MB）…", 0f))
            HorizonPrepLog.append(context, "调用引擎 nativeStart…")
            // nativeStart 会阻塞到准备结束；进度靠 nativeStatus 轮询
            Thread {
                runCatching {
                    dev.horizonlab.horizon.SetupActivity.nativeStart(home.absolutePath, "")
                }
            }.start()

            while (true) {
                val status = runCatching {
                    dev.horizonlab.horizon.SetupActivity.nativeStatus()
                }.getOrNull()
                val parsed = parseStatus(status)
                if (parsed != null) {
                    val (stageLabel, fraction, state, error) = parsed
                    when (state) {
                        "done" -> {
                            onProgress(Progress(Stage.READY, "游戏文件已就绪", 1f))
                            return@runCatching
                        }
                        "error" -> {
                            HorizonPrepLog.append(context, "引擎错误: ${error ?: stageLabel}")
                            error(error ?: "准备失败：$stageLabel")
                        }
                        else -> onProgress(Progress(Stage.ENGINE, stageLabel, fraction))
                    }
                }
                Thread.sleep(400)
                if (isEngineReady(context)) {
                    onProgress(Progress(Stage.READY, "游戏文件已就绪", 1f))
                    return@runCatching
                }
            }
            @Suppress("UNREACHABLE_CODE")
            Unit
        }
    }

    /** nativeStatus 的制表符格式：stage / files / filesTotal / bytes / bytesTotal / state / error */
    internal fun parseStatus(raw: String?): StatusSnapshot? {
        val parts = raw?.split("\t") ?: return null
        if (parts.size < 7) return null
        val files = parts[1].toLongOrNull() ?: 0L
        val filesTotal = parts[2].toLongOrNull() ?: 0L
        val bytes = parts[3].toLongOrNull() ?: 0L
        val bytesTotal = parts[4].toLongOrNull() ?: 0L
        val fraction = when {
            bytesTotal > 0 -> (bytes.toFloat() / bytesTotal).coerceIn(0f, 1f)
            filesTotal > 0 -> (files.toFloat() / filesTotal).coerceIn(0f, 1f)
            else -> 0f
        }
        val label = when (parts[0]) {
            "preparing" -> "准备中"
            "download" -> "下载游戏文件"
            "unpack" -> "解包"
            "sounds" -> "音效、字体与语言"
            "derived" -> "Horizon 数据"
            "ready" -> "就绪"
            else -> parts[0]
        }
        val detail = when {
            bytesTotal > 0 -> "$label ${bytes / 1048576} / ${bytesTotal / 1048576} MB"
            filesTotal > 0 -> "$label $files / $filesTotal 个文件"
            else -> label
        }
        return StatusSnapshot(detail, fraction, parts[5], parts[6].takeIf { it.isNotBlank() })
    }

    data class StatusSnapshot(
        val label: String,
        val fraction: Float,
        val state: String,
        val error: String?
    )

    // ---------- 内部 ----------

    private fun fetchVersionJson(): com.google.gson.JsonObject {
        val manifest = get(MANIFEST_URL)
        val versions = JsonParser.parseString(manifest).asJsonObject.getAsJsonArray("versions")
        val entry = versions.map { it.asJsonObject }
            .firstOrNull { it.get("id")?.asString == TARGET_VERSION }
            ?: error("官方清单里没有 $TARGET_VERSION")
        val url = entry.get("url")?.asString ?: error("版本条目缺少 json 地址")
        return JsonParser.parseString(get(url)).asJsonObject
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            return resp.body?.string() ?: error("空响应")
        }
    }

    private fun downloadServerJar(
        info: HorizonDataPack.ServerJar,
        dest: File,
        onProgress: (Long, Long) -> Unit
    ) {
        dest.parentFile?.mkdirs()
        val request = Request.Builder().url(info.url).build()
        client.newCall(request).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            val body = resp.body ?: error("空响应")
            val total = body.contentLength().takeIf { it > 0 } ?: info.size
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var done = 0L
                    var lastTick = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (done - lastTick >= 1024 * 1024) {
                            lastTick = done
                            onProgress(done, total)
                        }
                    }
                    onProgress(done, total)
                }
            }
        }
    }

    /**
     * 解包 Mojang bundler 到 gameDir/libraries，返回相对路径列表（供合成版本引用）。
     * 内层服务端 jar 落到 libraries/net/minecraft/server/26.3/server.jar。
     */
    /**
     * 在已安装版本里找 26.3：目录名可能是 26.3，也可能带后缀。
     * 找不到返回 null，由调用方退化成独立版本——不能因为这一步卡住整个准备流程。
     */
    internal fun detectParentVersion(versionsDir: java.io.File): String? {
        val candidates = versionsDir.listFiles()?.filter { it.isDirectory }.orEmpty()
        candidates.firstOrNull { it.name == HorizonDatagenVersion.PARENT_ID }?.let { return it.name }
        for (dir in candidates) {
            val json = File(dir, "${dir.name}.json").takeIf { it.isFile } ?: continue
            val id = runCatching {
                com.google.gson.JsonParser.parseString(json.readText())
                    .asJsonObject.get("id")?.asString
            }.getOrNull() ?: continue
            if (id == HorizonDatagenVersion.PARENT_ID) return dir.name
        }
        return candidates.firstOrNull { it.name.startsWith("${HorizonDatagenVersion.PARENT_ID}-") }?.name
    }

    private fun extractBundlerToLibraries(context: Context, serverJar: File): List<String> {
        val gameDir = com.verse.explorer.core.config.GameConfig.getGameDir(context)
        val librariesDir = com.verse.explorer.core.config.GameConfig.getLibrariesDir(gameDir)
        val out = mutableListOf<String>()
        java.util.zip.ZipFile(serverJar).use { zip ->
            for (entry in zip.entries()) {
                val name = entry.name
                if (entry.isDirectory) continue
                when {
                    name.startsWith("META-INF/libraries/") -> {
                        val rel = name.removePrefix("META-INF/")
                        val dest = File(gameDir, rel)
                        dest.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            dest.outputStream().use { input.copyTo(it) }
                        }
                        out += rel
                    }
                    name.startsWith("META-INF/versions/") && name.endsWith(".jar") -> {
                        val rel = "libraries/net/minecraft/server/${HorizonDatagenVersion.PARENT_ID}/server.jar"
                        val dest = File(gameDir, rel)
                        dest.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            dest.outputStream().use { input.copyTo(it) }
                        }
                        out += rel
                    }
                }
            }
        }
        // 包装 jar：丢弃启动器注入的游戏参数（否则 jopt-simple 直接抛 UnrecognizedOptionException）
        runCatching {
            val wrapperDest = File(gameDir, HorizonDatagenVersion.WRAPPER_LIB_PATH)
            wrapperDest.parentFile?.mkdirs()
            context.assets.open(HorizonDatagenVersion.WRAPPER_ASSET).use { input ->
                wrapperDest.outputStream().use { input.copyTo(it) }
            }
            out += HorizonDatagenVersion.WRAPPER_LIB_PATH
        }.onFailure {
            HorizonPrepLog.append("包装 jar 拷贝失败: ${it.message}")
        }
        HorizonPrepLog.append("libraries 目录: ${librariesDir.absolutePath}，写入 ${out.size} 个 jar")
        return out
    }
}
