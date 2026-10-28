package com.tungsten.fclcore.task

import com.tungsten.fclcore.util.CacheRepository
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * 分段（多连接）下载的行为约束。
 *
 * 版本安装的资源文件此前是单连接串行下载，带宽跑不满。
 * 现在按 Range 拆段并发；这里用本地 HTTP 服务验证：
 * 1. 支持 Range 时确实发了多个分段请求，且内容与原文件逐字节一致；
 * 2. 服务端不支持 Range（返回 200 全量）时回退单连接，内容仍正确；
 * 3. 小文件不拆段（只发一次请求）。
 */
class SegmentedDownloadTest {

    private var server: MiniHttpServer? = null

    @Before
    fun setUp() {
        // 下载任务会走 ETag 缓存：给缓存仓库一个临时目录，索引才会初始化（JVM 环境无默认目录）
        CacheRepository.getInstance().changeDirectory(java.nio.file.Files.createTempDirectory("fcl-cache"))
    }

    @After
    fun tearDown() {
        server?.stop()
    }

    private fun payload(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

    private fun download(port: Int): ByteArray {
        val target = File.createTempFile("seg-dl", ".bin")
        target.deleteOnExit()
        FileDownloadTask(URL("http://127.0.0.1:$port/file"), target).execute()
        return target.readBytes()
    }

    @Test
    fun `支持 Range 时分段下载内容完全一致`() {
        val data = payload(3 * 1024 * 1024 + 123)
        val srv = MiniHttpServer(data, supportsRange = true).also { server = it; it.start() }
        val downloaded = download(srv.port)
        assertEquals(data.size, downloaded.size)
        assertArrayEquals(data, downloaded)
        assertTrue("大文件应当拆成多段请求，实际请求数=${srv.hits.get()}", srv.hits.get() > 1)
    }

    @Test
    fun `服务端忽略 Range 时回退单连接且内容正确`() {
        val data = payload(2 * 1024 * 1024 + 7)
        val srv = MiniHttpServer(data, supportsRange = false).also { server = it; it.start() }
        val downloaded = download(srv.port)
        assertArrayEquals(data, downloaded)
        assertTrue("回退后应有成功请求", srv.hits.get() >= 1)
    }

    @Test
    fun `小文件不拆段`() {
        val data = payload(64 * 1024)
        val srv = MiniHttpServer(data, supportsRange = true).also { server = it; it.start() }
        val downloaded = download(srv.port)
        assertArrayEquals(data, downloaded)
        assertEquals("小文件应当只发一次请求，实际=${srv.hits.get()}", 1, srv.hits.get())
    }

    /** 够用的极简 HTTP/1.1 服务：只处理 GET /file，支持 Range 与不支持两种模式 */
    private class MiniHttpServer(
        private val data: ByteArray,
        private val supportsRange: Boolean
    ) {
        private val socket = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = socket.localPort
        val hits = AtomicInteger()

        fun start() {
            thread(isDaemon = true, name = "mini-http") {
                while (!socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (e: Exception) {
                        return@thread
                    }
                    thread(isDaemon = true) { handle(client) }
                }
            }
        }

        fun stop() {
            runCatching { socket.close() }
        }

        private fun handle(client: Socket) {
            try {
                client.use { c ->
                    val input = c.getInputStream()
                    val header = readHeaders(input) ?: return
                    hits.incrementAndGet()
                    val rangeLine = header.lineSequence()
                        .firstOrNull { it.startsWith("Range:", ignoreCase = true) }
                    val out: OutputStream = c.getOutputStream()
                    if (supportsRange && rangeLine != null) {
                        val spec = rangeLine.substringAfter("bytes=").trim()
                        val from = spec.substringBefore("-").toInt()
                        val to = spec.substringAfter("-").toIntOrNull() ?: (data.size - 1)
                        val len = to - from + 1
                        writeHead(out, 206, "Partial Content", len, "bytes $from-$to/${data.size}")
                        out.write(data, from, len)
                    } else {
                        writeHead(out, 200, "OK", data.size, null)
                        out.write(data)
                    }
                    out.flush()
                }
            } catch (_: Exception) {
                // 客户端提前断开（例如回退路径取消分段）属正常
            }
        }

        private fun readHeaders(input: java.io.InputStream): String? {
            val sb = StringBuilder()
            var last4 = 0
            while (true) {
                val b = input.read()
                if (b == -1) return if (sb.isEmpty()) null else sb.toString()
                sb.append(b.toChar())
                last4 = (last4 shl 8) or b
                if (last4 == 0x0D0A0D0A) return sb.toString()   // \r\n\r\n
                if (sb.length > 16384) return sb.toString()
            }
        }

        private fun writeHead(out: OutputStream, code: Int, reason: String, length: Int, contentRange: String?) {
            val sb = StringBuilder()
            sb.append("HTTP/1.1 $code $reason\r\n")
            sb.append("Content-Length: $length\r\n")
            sb.append("Accept-Ranges: bytes\r\n")
            sb.append("Connection: close\r\n")
            if (contentRange != null) sb.append("Content-Range: $contentRange\r\n")
            sb.append("\r\n")
            out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        }
    }
}
