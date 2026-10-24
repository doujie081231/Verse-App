package com.verse.explorer.core.install

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.ServerSocket
import java.net.InetAddress
import java.net.SocketException
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicInteger

class InstallDownloadExecutorTest {
    @get:Rule val temp = TemporaryFolder()

    private fun withServer(action: (String, AtomicInteger) -> Unit) {
        val requests = AtomicInteger()
        val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true) {
            try {
                while (!server.isClosed) server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val request = reader.readLine().orEmpty()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val number = requests.incrementAndGet()
                    val code = if (request.contains("/retry ") && number == 1) "503 Unavailable" else "200 OK"
                    val body = "verified-body"
                    socket.getOutputStream().write("HTTP/1.1 $code\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray())
                }
            } catch (closed: SocketException) {
                if (!server.isClosed) throw closed
            }
        }
        try { action("http://127.0.0.1:${server.localPort}", requests) } finally { server.close(); worker.join(2000) }
    }

    @Test fun checksumFailureKeepsExistingTargetAndRemovesTemporaryFile() = withServer { url, _ ->
        val target = temp.newFile("file.jar").apply { writeText("existing") }
        val http = OkHttpClient()
        val transfer = InstallDownloadExecutor(http, http) {}
        val failure = runCatching { runBlocking { transfer.downloadFileWithProgress(url, target, "bad-hash") { _, _ -> } } }.exceptionOrNull()
        assertTrue(failure?.message?.contains("SHA1") == true)
        assertEquals("existing", target.readText())
        assertFalse(File(temp.root, "file.jar.tmp").exists())
    }

    @Test fun retryRecoversTransientFailureWithoutChangingTargetDirectory() = withServer { url, requests ->
        val target = File(temp.root, "nested/result.jar")
        val http = OkHttpClient()
        runBlocking { InstallDownloadExecutor(http, http) {}.downloadFileWithRetry("$url/retry", target, maxRetries = 2) }
        assertEquals(2, requests.get())
        assertEquals("verified-body", target.readText())
        assertFalse(File(target.parentFile, "result.jar.tmp").exists())
    }

    @Test fun cancellationDoesNotRetryOrLeaveTemporaryFiles() = withServer { url, requests ->
        val http = OkHttpClient()
        val target = File(temp.root, "cancel.jar")
        val transfer = InstallDownloadExecutor(http, http) { throw GameInstaller.CancelledInstallException() }
        val failure = runCatching { runBlocking { transfer.downloadFileWithRetry(url, target) } }.exceptionOrNull()
        assertTrue(failure is GameInstaller.CancelledInstallException)
        assertEquals(0, requests.get())
        assertFalse(target.exists())
        assertFalse(File(temp.root, "cancel.jar.tmp").exists())
    }
}
