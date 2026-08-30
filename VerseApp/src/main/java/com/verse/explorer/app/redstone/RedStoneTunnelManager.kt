package com.verse.explorer.app.redstone

import android.content.Context
import com.google.gson.JsonParser
import com.verse.explorer.app.ui.VerseLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 红石联机（RedStone）隧道管理器
 *
 * 对接方式与 VersePC2（versepc2/src-tauri/src/redstone_online.rs）一致：
 * - 节点注册表：https://hongshi.site/newserver.json（name→address 映射），
 *   失败回退默认节点（南京）；
 * - 启动内核：`hongshi -server <节点> -port <本地MC端口> -status-file <状态文件>`；
 * - 状态：轮询状态文件（内核原子写）等待 status=open（30 秒超时，400ms 间隔）；
 * - 候选节点按序尝试：选中的节点在前，注册表其余节点去重追加，失败自动换下一个；
 * - 断线自动重连：内核异常退出时自动切换到剩余候选节点，最多 3 轮；
 * - 退出码：0=隧道被回收/关闭，1=创建失败，2=参数错误。
 *
 * 内核二进制以 libhongshi.so 打包在 jniLibs（arm64-v8a），安装后位于
 * nativeLibraryDir（Android 允许执行该目录）。
 */
object RedStoneTunnelManager {

    /** 隧道状态（与接入文档的状态文件/退出码语义对应） */
    sealed class State {
        data object Idle : State()

        /** 内核已拉起，等待状态文件 status=open */
        data class Starting(val node: String) : State()

        /** 隧道可用，联机地址 = server:port */
        data class Open(val server: String, val port: Int, val address: String, val node: String) : State()

        /** 正在自动切换节点重连（与 VersePC 的 redstone:reconnecting 对应） */
        data class Reconnecting(val reason: String, val attempt: Int, val maxAttempts: Int) : State()

        /** 隧道失效（status=closed 或重连次数用尽/用户关闭） */
        data class Closed(val message: String) : State()

        /** 创建失败或参数错误 */
        data class Failed(val message: String) : State()
    }

    data class RedStoneNode(val name: String, val address: String)

    /** 节点注册表（与 VersePC 的 REGISTRY_URL 一致） */
    const val REGISTRY_URL = "https://hongshi.site/newserver.json"

    /** 与 VersePC fetch_server_list 的回退默认节点一致 */
    val DEFAULT_NODE = RedStoneNode("南京", "nanjing.hongshi.site")

    // 与 VersePC 常量一致
    private const val STATUS_OPEN_TIMEOUT_MS = 30_000L
    private const val STATUS_POLL_INTERVAL_MS = 400L
    private const val NODE_SWITCH_INTERVAL_MS = 800L
    private const val DEFAULT_MAX_ATTEMPTS = 3

    private const val TAG = "RedStone"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _nodes = MutableStateFlow<List<RedStoneNode>>(listOf(DEFAULT_NODE))
    val nodes: StateFlow<List<RedStoneNode>> = _nodes.asStateFlow()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var stoppedByUser = false

    fun isAvailable(context: Context): Boolean =
        File(context.applicationInfo.nativeLibraryDir, "libhongshi.so").exists()

    fun workDir(context: Context): File =
        File(context.cacheDir, "redstone").apply { mkdirs() }

    private fun statusFile(context: Context): File =
        File(workDir(context), "tunnel.ini")

    private fun appendLog(line: String) {
        if (line.isBlank()) return
        synchronized(_logs) {
            _logs.value = (_logs.value + line).takeLast(60)
        }
        VerseLogger.info(line, TAG)
    }

    /** 刷新节点列表（与 VersePC fetch_server_list 对应：注册表对象映射，失败回退默认） */
    fun refreshNodes() {
        scope.launch {
            val list = runCatching {
                val request = Request.Builder().url(REGISTRY_URL)
                    .header("User-Agent", "VerseExplorerX/1.0").build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body?.string() ?: return@runCatching null
                    val obj = JsonParser.parseString(body).asJsonObject
                    val result = mutableListOf<RedStoneNode>()
                    obj.entrySet().forEach { (name, addr) ->
                        val address = addr.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: ""
                        if (address.isNotEmpty()) result.add(RedStoneNode(name, address))
                    }
                    if (result.isNotEmpty()) result else null
                }
            }.getOrNull()
            _nodes.value = list ?: listOf(DEFAULT_NODE)
            if (list == null) {
                VerseLogger.warn("获取红石节点列表失败，使用默认节点", TAG)
            }
        }
    }

    /**
     * 拉起内核创建隧道（与 VersePC start_tunnel_inner 对应）：
     * 候选节点 = 选中的节点在前 + 注册表其余节点去重追加，按序尝试直到成功。
     */
    fun start(context: Context, node: String, localPort: Int) {
        stop()
        stoppedByUser = false

        val binary = File(context.applicationInfo.nativeLibraryDir, "libhongshi.so")
        if (!binary.exists()) {
            _state.value = State.Failed("当前设备没有红石内核（仅支持 arm64 设备）")
            return
        }
        if (localPort <= 0) {
            _state.value = State.Failed("游戏端口无效")
            return
        }

        // 候选节点（与 VersePC 对应：选中在前，其余去重追加）
        val candidates = mutableListOf(node.trim())
        _nodes.value.forEach { n ->
            if (n.address.isNotEmpty() && candidates[0] != n.address && !candidates.contains(n.address)) {
                candidates.add(n.address)
            }
        }
        appendLog("候选节点: ${candidates.joinToString(", ")}")

        scope.launch { runTunnel(context, binary, candidates, localPort) }
    }

    /** 按序尝试候选节点；成功后监听内核退出并自动切换剩余节点（与 VersePC 对应） */
    private suspend fun runTunnel(context: Context, binary: File, candidates: List<String>, localPort: Int) {
        var lastError = ""
        val tried = mutableListOf<String>()
        for (addr in candidates) {
            if (tried.contains(addr)) continue
            tried.add(addr)
            if (stoppedByUser) return
            appendLog("尝试节点: $addr")
            tryStartNode(context, binary, addr, localPort)?.let { process ->
                // 隧道就绪；启动自动重连监控
                monitorProcess(context, binary, process, addr, candidates - addr, localPort)
                return
            }
            lastError = "连接失败"
            // 短暂间隔再试下一个（与 VersePC 对应）
            Thread.sleep(NODE_SWITCH_INTERVAL_MS)
        }
        if (!stoppedByUser) {
            _state.value = State.Failed("所有节点连接失败：$lastError")
        }
    }

    /**
     * 在指定节点上启动一次隧道（与 VersePC try_start_node 对应）。
     * 成功返回进程对象；失败返回 null（原因写入状态/日志）。
     */
    private suspend fun tryStartNode(context: Context, binary: File, node: String, localPort: Int): Process? {
        // 清掉旧状态文件，避免读到上一次的隧道（与 VersePC 对应）
        statusFile(context).delete()
        workDir(context).mkdirs()

        _state.value = State.Starting(node)
        appendLog("中转服务器: $node  本地端口: $localPort")

        val processBuilder = ProcessBuilder(
            binary.absolutePath,
            "-server", node,
            "-port", localPort.toString(),
            "-status-file", statusFile(context).absolutePath
        ).directory(workDir(context))
        processBuilder.redirectErrorStream(true)

        val newProcess: Process = try {
            processBuilder.start()
        } catch (e: IOException) {
            VerseLogger.error("启动内核失败: ${e.message}", TAG)
            _state.value = State.Failed("启动内核失败: ${e.message}")
            return null
        }
        process = newProcess

        // 内核输出仅用于展示
        scope.launch {
            try {
                newProcess.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        appendLog(line ?: "")
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 轮询状态文件等待 status=open（30 秒超时，与 VersePC 对应）
        val deadline = System.currentTimeMillis() + STATUS_OPEN_TIMEOUT_MS
        while (newProcess.isAlive) {
            // 内核提前退出 → 启动失败（与 VersePC 的退出码语义对应）
            if (!newProcess.isAlive) break
            val open = parseOpenTunnel(statusFile(context))
            if (open != null) {
                val address = "${open.first}:${open.second}"
                appendLog("隧道已就绪，地址: $address")
                _state.value = State.Open(open.first, open.second, address, node)
                return newProcess
            }
            if (System.currentTimeMillis() >= deadline) {
                newProcess.destroy()
                appendLog("等待隧道开启超时")
                _state.value = State.Failed("等待隧道开启超时（节点 $node）")
                return null
            }
            Thread.sleep(STATUS_POLL_INTERVAL_MS)
        }

        // 内核提前退出
        val code = runCatching { newProcess.waitFor() }.getOrDefault(-1)
        process = null
        val msg = when (code) {
            0 -> "内核已退出：隧道被回收或服务器关闭"
            1 -> "隧道创建失败（服务器不可达/拒绝/无空闲端口）"
            2 -> "参数错误"
            else -> "内核异常退出，退出码 $code"
        }
        appendLog("节点 $node 失败: $msg")
        return null
    }

    /**
     * 内核退出监控与自动切换节点（与 VersePC 的 auto_reconnect 对应，最多 3 轮）。
     */
    private suspend fun monitorProcess(
        context: Context,
        binary: File,
        initial: Process,
        initialNode: String,
        reconnectNodes: List<String>,
        localPort: Int
    ) {
        var child = initial
        var currentNode = initialNode
        var remaining = reconnectNodes.toMutableList()
        var attemptsDone = 0
        while (true) {
            val code = runCatching { child.waitFor() }.getOrDefault(-1)
            process = null
            appendLog("内核退出，退出码: $code")

            if (stoppedByUser) {
                _state.value = State.Idle
                return
            }
            if (attemptsDone >= DEFAULT_MAX_ATTEMPTS || remaining.isEmpty()) {
                val reason = when (code) {
                    0 -> "房间已关闭（空转回收或服务器维护）"
                    1 -> "隧道创建失败（服务器不可达/拒绝/无空闲端口）"
                    2 -> "参数错误"
                    else -> "重连次数已达上限"
                }
                _state.value = State.Closed(reason)
                return
            }

            attemptsDone++
            val next = remaining.firstOrNull() ?: run {
                _state.value = State.Closed("无更多候选节点，重连停止")
                return
            }
            remaining = remaining.toMutableList().apply { remove(next) }
            _state.value = State.Reconnecting("连接断开，正在切换节点（$attemptsDone/$DEFAULT_MAX_ATTEMPTS）", attemptsDone, DEFAULT_MAX_ATTEMPTS)
            appendLog("自动切换节点: $next")

            val newProcess = tryStartNode(context, binary, next, localPort)
            if (newProcess != null) {
                appendLog("切换到节点 $next 成功")
                currentNode = next
                child = newProcess
            } else {
                currentNode = next
            }
        }
    }

    /** 读取状态文件，status=open 时返回 (server, port)（与 VersePC read_open_tunnel 对应） */
    fun parseOpenTunnel(file: File): Pair<String, Int>? {
        if (!file.exists()) return null
        val content = runCatching { file.readText() }.getOrNull() ?: return null
        var section = ""
        var status = ""
        var server = ""
        var port = ""
        for (raw in content.lines()) {
            val line = raw.substringBefore(';').trim()
            if (line.isEmpty()) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.trim('[', ']').trim()
                continue
            }
            if (section != "tunnel") continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim().lowercase(Locale.US)
            val value = line.substring(eq + 1).trim()
            when (key) {
                "status" -> status = value
                "server" -> server = value
                "port" -> port = value
            }
        }
        if (status.equals("open", ignoreCase = true)) {
            val p = port.toIntOrNull() ?: return null
            if (server.isEmpty()) return null
            return Pair(server, p)
        }
        return null
    }

    /** 停止内核（关闭房间，与 VersePC stop_tunnel_inner 对应） */
    fun stop() {
        stoppedByUser = true
        process?.destroy()
        process = null
        if (_state.value !is State.Idle) _state.value = State.Idle
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
