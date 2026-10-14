package com.verse.explorer.app.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.theme.VerseColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.security.MessageDigest

/**
 * 传输:发送 / 接收 一体化的跨设备互传入口
 * 可与 PC(VersePC2) 及其他设备互传文件(需网络连接);
 * 协议与 PC 端完全一致:信令服务器只做识别码协调,
 * 文件在两台设备间 TCP 点对点直传(SHA-256 校验),服务器不中转数据。
 * 免费使用(2026-10-14 起):不再要求激活码;
 * 安全使用:仅传输自己有权分享的内容,勿传播违规文件,建议在可信网络环境下使用。
 */

// ===== 与 PC 端 transfer.rs 一致的协议常量 =====
private const val SIGNAL_BASE = "http://124.223.80.73:8746"
private val MAGIC = byteArrayOf(
    'V'.code.toByte(), 'X'.code.toByte(), 'F'.code.toByte(), 'E'.code.toByte(),
    'R'.code.toByte(), '0'.code.toByte(), '1'.code.toByte(), 0
)
private const val CHUNK_SIZE = 256 * 1024

@Composable
fun TransferScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ===== 发送状态 =====
    var sending by remember { mutableStateOf(false) }
    var sendFileName by remember { mutableStateOf("") }
    var sendCode by remember { mutableStateOf("") }
    var sendStatus by remember { mutableStateOf("") }
    var sendProgress by remember { mutableStateOf(-1f) }
    var sendError by remember { mutableStateOf("") }
    var sendDone by remember { mutableStateOf(false) }

    // ===== 接收状态 =====
    var receiveCode by remember { mutableStateOf("") }
    var treeUri by remember { mutableStateOf<Uri?>(null) }
    var receiving by remember { mutableStateOf(false) }
    var receiveProgress by remember { mutableStateOf(-1f) }
    var receiveMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !sending) {
            sending = true
            sendFileName = ""
            sendCode = ""
            sendStatus = ""
            sendProgress = -1f
            sendError = ""
            sendDone = false
            scope.launch(Dispatchers.IO) {
                runSend(context, uri,
                    onName = { sendFileName = it },
                    onCode = { sendCode = it },
                    onStatus = { sendStatus = it },
                    onProgress = { sendProgress = it },
                    onDone = { sendDone = true; sending = false },
                    onError = { sendError = it; sending = false }
                )
            }
        }
    }
    val pickDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) treeUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text("传输", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = VerseColors.PcTextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "与 PC(VersePC2) 互传文件,点对点直连一步到位",
            fontSize = 13.sp,
            color = VerseColors.PcTextSecondary
        )
        Spacer(Modifier.height(18.dp))

        // ===== 免费使用 + 安全提示 =====
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = VerseColors.PcGlassCardBg
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("免费互传", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VerseColors.PcTextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "互传功能免费使用,无需激活码;文件在两台设备间点对点直连,服务器只做信令不中转数据",
                    fontSize = 13.sp,
                    color = VerseColors.PcTextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "安全提示:请仅传输自己有权分享的内容,勿传播违规文件;建议在可信网络环境下使用",
                    fontSize = 13.sp,
                    color = VerseColors.PcTextSecondary
                )
            }
        }
        Spacer(Modifier.height(14.dp))

            // ----- 发送文件卡片 -----
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = VerseColors.PcGlassCardBg
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("发送文件", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VerseColors.PcTextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "选择文件生成 8 位识别码,对方凭识别码连接后自动直传",
                        fontSize = 13.sp,
                        color = VerseColors.PcTextSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    if (sendCode.isEmpty()) {
                        Button(
                            onClick = { pickFile.launch(arrayOf("*/*")) },
                            enabled = !sending,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VerseColors.PcAccent)
                        ) {
                            Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (sending) "处理中." else "选择文件并生成识别码", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text(
                            sendCode,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = VerseColors.PcAccent,
                            letterSpacing = 6.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "把识别码告诉对方(10 分钟内有效) · 文件:$sendFileName",
                            fontSize = 12.sp,
                            color = VerseColors.PcTextMuted
                        )
                    }
                    if (sendStatus.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(sendStatus, fontSize = 13.sp, color = VerseColors.PcTextSecondary)
                    }
                    if (sendProgress >= 0f) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { sendProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = VerseColors.PcAccent,
                            trackColor = VerseColors.PcTextMuted.copy(alpha = 0.2f)
                        )
                    }
                    if (sendDone) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = VerseColors.PcGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("传输完成,校验通过", fontSize = 13.sp, color = VerseColors.PcGreen)
                        }
                    }
                    if (sendError.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(sendError, fontSize = 13.sp, color = VerseColors.PcRed)
                    }
                    if (sendCode.isNotEmpty() || sendError.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                sending = false
                                sendFileName = ""
                                sendCode = ""
                                sendStatus = ""
                                sendProgress = -1f
                                sendError = ""
                                sendDone = false
                            },
                            enabled = !sending,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("重置", fontSize = 14.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ----- 接收文件卡片 -----
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = VerseColors.PcGlassCardBg
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("接收文件", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VerseColors.PcTextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "输入对方的 8 位识别码,选择保存位置后接收并校验",
                        fontSize = 13.sp,
                        color = VerseColors.PcTextSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = receiveCode,
                        onValueChange = { receiveCode = it.uppercase().take(8) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("识别码") },
                        placeholder = { Text("8 位识别码") },
                        singleLine = true,
                        enabled = !receiving
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { pickDir.launch(null) },
                        enabled = !receiving,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (treeUri == null) "选择保存位置" else "已选择保存位置(点此更改)", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val dir = treeUri
                            if (dir != null && receiveCode.isNotBlank() && !receiving) {
                                receiving = true
                                receiveProgress = -1f
                                receiveMsg = null
                                scope.launch(Dispatchers.IO) {
                                    val result = runReceive(context, dir, receiveCode.trim()) { receiveProgress = it }
                                    receiving = false
                                    receiveMsg = result
                                }
                            }
                        },
                        enabled = !receiving && receiveCode.isNotBlank() && treeUri != null,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VerseColors.PcAccent)
                    ) {
                        Text(if (receiving) "接收中." else "开始接收", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (receiveProgress >= 0f) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { receiveProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = VerseColors.PcAccent,
                            trackColor = VerseColors.PcTextMuted.copy(alpha = 0.2f)
                        )
                    }
                    receiveMsg?.let { (ok, text) ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text,
                            fontSize = 13.sp,
                            color = if (ok) VerseColors.PcGreen else VerseColors.PcRed,
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.SwapHoriz,
                    contentDescription = null,
                    tint = VerseColors.PcTextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "文件在两台设备间点对点直连,服务器只做信令不中转数据,免费使用",
                    fontSize = 12.sp,
                    color = VerseColors.PcTextMuted
                )
            }
    }
}

// ==================== 发送 ====================

/**
 * 发送主流程:选中的文档 → SHA-256 → 监听端口 → 信令注册(设备标识) → 等待对端连接 → 直传 → 回执校验
 */
private suspend fun runSend(
    context: Context,
    uri: Uri,
    onName: (String) -> Unit,
    onCode: (String) -> Unit,
    onStatus: (String) -> Unit,
    onProgress: (Float) -> Unit,
    onDone: () -> Unit,
    onError: (String) -> Unit
) {
    var server: ServerSocket? = null
    var socket: Socket? = null
    try {
        val resolver = context.contentResolver
        val name = queryDisplayName(resolver, uri)
        onName(name)

        // 1. 流式 SHA-256 + 大小
        val md = MessageDigest.getInstance("SHA-256")
        var size = 0L
        resolver.openInputStream(uri)?.use { ins ->
            val buf = ByteArray(CHUNK_SIZE)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
                size += n
            }
        } ?: throw Exception("无法读取所选文件")
        if (size <= 0L) throw Exception("所选文件为空")
        val digestBytes = md.digest()
        val hash = digestBytes.joinToString("") { "%02x".format(it) }

        // 2. 监听随机端口(等待对方连接最长 10 分钟,与识别码有效期一致)
        onStatus("准备中.")
        server = ServerSocket(0)
        server.soTimeout = 600_000
        val port = server.localPort

        // 3. 信令注册(设备标识,免费使用,信令直接发识别码)
        val deviceId = androidId(context)
        val code = registerSignal(name, size, hash, port, deviceId)
        onCode(code)
        onStatus("等待对方连接(识别码 10 分钟内有效)")

        // 4. 等待对端连接
        socket = server.accept()
        socket.tcpNoDelay = true
        onStatus("对方已连接,传输中")

        // 5. 直传:协议头(魔数+文件名+大小+SHA-256) + 分块流
        val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), CHUNK_SIZE))
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        out.write(MAGIC)
        out.writeInt(nameBytes.size)
        out.write(nameBytes)
        out.writeLong(size)
        out.write(digestBytes)
        resolver.openInputStream(uri)?.use { ins ->
            val buf = ByteArray(CHUNK_SIZE)
            var sent = 0L
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
                sent += n
                onProgress(sent.toFloat() / size)
            }
        }
        out.flush()

        // 6. 回执(1 字节:0x01 对端校验通过)
        socket.soTimeout = 30_000
        val ack = socket.getInputStream().read()
        if (ack == 0x01) {
            onProgress(1f)
            onDone()
        } else {
            onError("对端校验未通过,文件可能已损坏")
        }
    } catch (e: Exception) {
        onError(e.message ?: "发送失败")
    } finally {
        try { socket?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
    }
}

// ==================== 接收 ====================

/**
 * 接收主流程:识别码 → 信令换取发送方端点(接收方免费) → 连接 →
 * 按协议接收(魔数+文件名+大小+SHA-256+分块流) → 校验 → 回执
 * 返回 (是否成功, 成功时含文件名 / 失败时为错误信息)
 */
private suspend fun runReceive(
    context: Context,
    tree: Uri,
    rawCode: String,
    onProgress: (Float) -> Unit
): Pair<Boolean, String> {
    var socket: Socket? = null
    try {
        val code = rawCode.replace(" ", "").uppercase()
        if (code.isEmpty()) return false to "请输入识别码"

        // 1. 信令 lookup
        val (ip, port) = lookupSignal(code)

        // 2. 连接发送方
        socket = Socket()
        socket.connect(java.net.InetSocketAddress(ip, port), 8_000)
        socket.tcpNoDelay = true
        socket.soTimeout = 30_000

        // 3. 协议头
        val din = DataInputStream(BufferedInputStream(socket.getInputStream(), CHUNK_SIZE))
        val magic = ByteArray(MAGIC.size)
        din.readFully(magic)
        if (!magic.contentEquals(MAGIC)) return false to "协议不匹配,对端不是 Verse 互传"
        val nameLen = din.readInt()
        if (nameLen <= 0 || nameLen > 1024) return false to "文件名长度异常"
        val nameBytes = ByteArray(nameLen)
        din.readFully(nameBytes)
        val fileName = String(nameBytes, Charsets.UTF_8)
        val fileSize = din.readLong()
        val expectHash = ByteArray(32)
        din.readFully(expectHash)

        // 防路径穿越:仅取文件名部分
        val safeName = fileName.replace('\\', '/').substringAfterLast('/').ifEmpty { "file" }

        // 4. 在所选目录创建目标文档
        val resolver = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(tree)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(tree, treeDocId)
        val docUri = DocumentsContract.createDocument(
            resolver, parentDocUri, "application/octet-stream", safeName
        ) ?: return false to "无法创建保存文件"

        // 5. 接收字节流 + 同步计算 SHA-256
        val md = MessageDigest.getInstance("SHA-256")
        var received = 0L
        var ok = true
        val out = resolver.openOutputStream(docUri)
        if (out == null) {
            return false to "无法写入保存文件"
        }
        val buf = ByteArray(CHUNK_SIZE)
        try {
            while (received < fileSize) {
                val want = minOf(buf.size.toLong(), fileSize - received).toInt()
                din.readFully(buf, 0, want)
                out.write(buf, 0, want)
                md.update(buf, 0, want)
                received += want
                if (fileSize > 0) onProgress(received.toFloat() / fileSize)
            }
            out.flush()
        } catch (e: Exception) {
            ok = false
        } finally {
            try { out.close() } catch (_: Exception) {}
        }

        // 6. 校验 + 回执
        val actualHash = md.digest()
        val verified = ok && received == fileSize && actualHash.contentEquals(expectHash)
        try {
            socket.getOutputStream().write(if (verified) 0x01 else 0x00)
            socket.getOutputStream().flush()
        } catch (_: Exception) {}
        onProgress(if (verified) 1f else -1f)
        return if (verified) {
            true to "接收完成:$safeName,校验通过"
        } else {
            false to if (ok) "接收完成但校验未通过,文件可能已损坏" else "接收中断,数据不完整"
        }
    } catch (e: Exception) {
        return false to (e.message ?: "接收失败")
    } finally {
        try { socket?.close() } catch (_: Exception) {}
    }
}

// ==================== 信令与工具 ====================

/** 信令注册:返回 8 位识别码(免费使用,不再携带激活码);失败时抛异常 */
private fun registerSignal(
    fileName: String,
    fileSize: Long,
    fileHash: String,
    port: Int,
    deviceId: String
): String {
    val body = JSONObject().apply {
        put("fileName", fileName)
        put("fileSize", fileSize)
        put("fileHash", fileHash)
        put("port", port)
        put("activationKey", "")
        put("deviceId", deviceId)
    }.toString()
    val json = httpPostJson("$SIGNAL_BASE/api/transfer/register", body)
    val code = json.optString("code", "")
    if (code.isNotEmpty()) return code
    throw Exception(json.optString("error", "生成识别码失败"))
}

/** 信令 lookup:识别码换取发送方端点(接收方无需激活) */
private fun lookupSignal(code: String): Pair<String, Int> {
    val conn = (URL("$SIGNAL_BASE/api/transfer/lookup?code=$code").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 10_000
    }
    try {
        val status = conn.responseCode
        val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        val json = JSONObject(text)
        val ip = json.optString("ip", "")
        val port = json.optInt("port", 0)
        if (status == 200 && ip.isNotEmpty() && port > 0) return ip to port
        throw Exception(json.optString("error", "识别码不存在或已过期"))
    } finally {
        conn.disconnect()
    }
}

/** 通用 POST JSON(信令用,非加密 HTTP) */
private fun httpPostJson(urlStr: String, body: String): JSONObject {
    var conn: HttpURLConnection? = null
    try {
        conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val status = conn.responseCode
        val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        return JSONObject(text)
    } finally {
        conn?.disconnect()
    }
}

/** 设备标识:ANDROID_ID */
private fun androidId(context: Context): String =
    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"

/** 查询 SAF 文档显示名(查询失败时回退 "file") */
private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String {
    return try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) {
                c.getString(idx)?.takeIf { it.isNotBlank() } ?: "file"
            } else "file"
        } ?: "file"
    } catch (_: Exception) {
        "file"
    }
}
