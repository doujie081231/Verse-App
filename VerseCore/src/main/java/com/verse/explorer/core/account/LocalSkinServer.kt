package com.verse.explorer.core.account

import android.util.Base64
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * 极简本地皮肤服务。
 *
 * 为什么需要它：离线账号没有服务端身份，游戏读皮肤的方式是「拿一个 URL 去下载 PNG」，
 * 不认本地文件路径。所以要把本地皮肤变成游戏能访问的 URL —— 在 127.0.0.1 上开一个
 * 只读端口把 PNG 吐出来，再把该 URL 写进 `--userProperties` 的 textures 里。
 *
 * 边界说明：这只影响**客户端看到的自己**（单机 / 别人装了同款皮肤的情况除外）。
 * 联机时其他玩家看到的皮肤由服务器决定，客户端无法改变。
 */
object LocalSkinServer {

    private val files = ConcurrentHashMap<String, File>()

    @Volatile
    private var socket: ServerSocket? = null

    @Volatile
    private var port: Int = 0

    /**
     * 发布一个皮肤文件，返回可被游戏下载的 URL；失败返回 null（调用方退回默认皮肤）。
     * 同一个 key 重复发布会覆盖，端口只起一次。
     */
    @Synchronized
    fun publish(key: String, file: File): String? {
        if (!file.exists() || file.length() == 0L) return null
        files[key] = file
        ensureStarted()
        val p = port
        if (p <= 0) return null
        return "http://127.0.0.1:$p/skin/$key.png"
    }

    /** 构造 authlib 的 textures 明文（base64 后交给 --userProperties） */
    fun buildTexturesValue(
        uuid: String,
        name: String,
        url: String,
        slim: Boolean
    ): String {
        val model = if (slim) """, "metadata": {"model": "slim"}""" else ""
        val json = buildString {
            append("""{"timestamp":""").append(System.currentTimeMillis())
            append(""","profileId":"""").append(uuid).append('"')
            append(""","profileName":"""").append(name).append('"')
            append(""","textures":{"SKIN":{"url":"""").append(url).append('"')
            append(model).append("}}}")
        }
        return Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun ensureStarted() {
        val current = socket
        if (current != null && !current.isClosed) return

        runCatching {
            val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
            socket = server
            port = server.localPort
            thread(isDaemon = true, name = "verse-local-skin") {
                while (!server.isClosed) {
                    val client = runCatching { server.accept() }.getOrNull() ?: break
                    runCatching { handle(client) }
                }
            }
        }.onFailure {
            port = 0
        }
    }

    private fun handle(client: Socket) {
        client.use { c ->
            c.soTimeout = 5000
            val reader = c.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return
            // 丢掉请求头（我们只需要 path）
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }

            val path = requestLine.split(' ').getOrNull(1).orEmpty()
            val key = path.removePrefix("/skin/").removeSuffix(".png")
            val file = files[key]

            val out = c.getOutputStream()
            if (file == null || !file.exists()) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            } else {
                val bytes = file.readBytes()
                val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: image/png\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
                out.write(header.toByteArray())
                out.write(bytes)
            }
            out.flush()
        }
    }
}
