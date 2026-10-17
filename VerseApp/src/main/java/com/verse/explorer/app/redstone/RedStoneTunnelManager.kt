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
import java.util.concurrent.TimeUnit

/**
 * 红石联机（RedStone）隧道管理器
 *
 * 对接方式与官方 HongshiAndroid 客户端一致（Rust 内核 libhongshic.so）：
 * - 启动内核：`libhongshi.so -t <节点> -p <本地MC端口> -v`；
 * - stdout 解析 `endpoint=<host>:<port>`（隧道就绪标志），30 秒超时；
 * - 候选节点按序尝试：选中的节点在前，注册表其余节点去重追加，失败自动换下一个；
 * - 断线自动重连：内核异常退出时自动切换到剩余候选节点，最多 3 轮；
 * - 节点注册表：https://hongshi.site/server.json（name→address 映射），失败回退默认节点。
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

        data class Reconnecting(val reason: String, val attempt: Int, val maxAttempts: Int) : State()

        /** 隧道失效（status=closed 或重连次数用尽/用户关闭） */
        data class Closed(val message: String) : State()

        /** 创建失败或参数错误 */
        data class Failed(val message: String) : State()
    }

    data class RedStoneNode(val name: String, val address: String)

    /** 节点注册表（官方 HongshiAndroid 客户端使用的地址：hongshi.site/server.json） */
    const val REGISTRY_URL = "https://hongshi.site/server.json"

    /** 注册表不可用时的回退默认节点（与官方 server.json 的南京节点一致） */
    val DEFAULT_NODE = RedStoneNode("南京", "nj.hongshi.site")

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

    private fun appendLog(line: String) {
        if (line.isBlank()) return
        synchronized(_logs) {
            _logs.value = (_logs.value + line).takeLast(60)
        }
        VerseLogger.info(line, TAG)
    }

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

        val candidates = mutableListOf(node.trim())
        _nodes.value.forEach { n ->
            if (n.address.isNotEmpty() && candidates[0] != n.address && !candidates.contains(n.address)) {
                candidates.add(n.address)
            }
        }
        appendLog("候选节点: ${candidates.joinToString(", ")}")

        scope.launch { runTunnel(context, binary, candidates, localPort) }
    }

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
            Thread.sleep(NODE_SWITCH_INTERVAL_MS)
        }
        if (!stoppedByUser) {
            _state.value = State.Failed("所有节点连接失败：$lastError")
        }
    }

    /**
     * 成功返回进程对象；失败返回 null（原因写入状态/日志）。
     */
    private suspend fun tryStartNode(context: Context, binary: File, node: String, localPort: Int): Process? {
        workDir(context).mkdirs()

        _state.value = State.Starting(node)
        // 诊断日志：完整命令行与内核文件状态（定位创建失败的具体环节）
        // 注意：不可加 -v（clap 会把它当 version 标志，打印版本后直接退出码 0）
        appendLog("启动内核: ${binary.absolutePath} -t $node -p $localPort")
        appendLog(
            "内核文件: 存在=${binary.exists()} 大小=${binary.length()} " +
                "可执行=${binary.canExecute()} 工作目录=${workDir(context).absolutePath}"
        )
        appendLog("中转服务器: $node  本地端口: $localPort")

        val processBuilder = ProcessBuilder(
            binary.absolutePath,
            "-t", node,
            "-p", localPort.toString()
        ).directory(workDir(context))
        processBuilder.redirectErrorStream(true)

        val newProcess: Process = try {
            processBuilder.start()
        } catch (e: IOException) {
            VerseLogger.error("启动内核失败: ${e.message}", TAG)
            appendLog("启动内核失败: ${e.javaClass.simpleName}: ${e.message}")
            _state.value = State.Failed("启动内核失败: ${e.message}")
            return null
        }
        process = newProcess

        // stdout 逐行读：全部入日志，同时解析 endpoint=<host>:<port>（隧道就绪标志）
        val endpoint = java.util.concurrent.atomic.AtomicReference<Pair<String, Int>?>(null)
        scope.launch {
            try {
                newProcess.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        appendLog(line ?: "")
                        if (endpoint.get() == null && line != null) {
                            val m = Regex("endpoint=(\\S+):(\\d+)").find(line!!)
                            if (m != null) {
                                endpoint.set(m.groupValues[1] to m.groupValues[2].toInt())
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 等待 endpoint 出现（30 秒超时）
        val deadline = System.currentTimeMillis() + STATUS_OPEN_TIMEOUT_MS
        while (newProcess.isAlive && endpoint.get() == null) {
            if (System.currentTimeMillis() >= deadline) {
                newProcess.destroy()
                appendLog("等待隧道开启超时（30s），节点无 endpoint 输出")
                _state.value = State.Failed("等待隧道开启超时（节点 $node）")
                return null
            }
            Thread.sleep(STATUS_POLL_INTERVAL_MS)
        }

        val open = endpoint.get()
        if (open != null) {
            val address = "${open.first}:${open.second}"
            appendLog("隧道已就绪，地址: $address")
            _state.value = State.Open(open.first, open.second, address, node)
            return newProcess
        }

        // 内核提前退出（无 endpoint）
        val code = runCatching { newProcess.waitFor() }.getOrDefault(-1)
        process = null
        appendLog("节点 $node 失败: 内核退出码 $code")
        return null
    }

    /**
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
                _state.value = State.Closed("连接已断开（内核退出码 $code），重连次数已达上限")
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
