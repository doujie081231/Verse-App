package com.verse.explorer.core.data.feedback

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 加载验证码 SVG、下发签名密钥、扫描日志附件、multipart 提交 + HMAC 防伪签名。
 */
object FeedbackRepository {

    private const val SITE = "https://verselauncher.cn"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /** 单次反馈附件总量上限：站点侧解析 multipart 有请求体上限，超了会直接 400 invalid form */
    private const val TOTAL_ATTACHMENT_BUDGET = 3 * 1024 * 1024L
    /** 单个日志只保留末尾这么多字节（定位问题看尾部即可） */
    private const val PER_LOG_TAIL_BYTES = 700 * 1024L
    /** 站点每天每 IP 的提交上限（服务端 DAILY_FEEDBACK_LIMIT） */
    private const val DAILY_LIMIT = 10

    /** 站点验证码：GET /api/feedback/captcha → { id, svg } */
    suspend fun fetchCaptcha(): Pair<String, String> {
        val req = Request.Builder().url("$SITE/api/feedback/captcha").build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: throw IllegalStateException("HTTP ${resp.code}")
            return runCatching {
                val json = JSONObject(body)
                json.getString("id") to json.getString("svg")
            }.getOrElse { throw IllegalStateException(body) }
        }
    }

    /** 站点签名密钥：GET /api/feedback/signinfo → { key }（host=verselauncher.cn 放行） */
    suspend fun fetchSignKey(): String {
        val req = Request.Builder().url("$SITE/api/feedback/signinfo").build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: throw IllegalStateException("HTTP ${resp.code}")
            return runCatching { JSONObject(body).getString("key") }
                .getOrElse { throw IllegalStateException(body) }
        }
    }

    /**
     * 扫描启动器日志附件（按分类）：
     * launch  -> logs 下启动相关（launch-debug / launch-fail-* / startup-timing / latest 等）
     * download -> logs 下下载相关（modpack-import / updater-download / loader-install / *.install 等）
     * 目录无法直接列取时回退到已知路径。返回按修改时间倒序，最多 8 个。
     */
    fun collectLogs(context: Context, category: String): List<File> {
        // 反馈上传本身的诊断日志（附件数/字节数/HTTP 码），固定随下一次反馈上传
        FeedbackLog.attach(context)
        val base = context.getExternalFilesDir(null)
        val logsDir = File(File(base, "VerseExplorerX"), "logs")
        val candidates = mutableListOf<File>()

        // 内部 filesDir 也扫一遍（部分日志写内存储）
        val internal = File(context.filesDir, "VerseExplorerX/logs")

        for (dir in listOf(logsDir, internal)) {
            if (!dir.isDirectory) continue
            dir.listFiles()?.forEach { f ->
                if (!f.isFile) return@forEach
                val name = f.name
                // 主日志（latest / launch）识别：无论选哪个分类都随附上传，后台定位问题首要看它
                // 反馈上传日志必须每次带上：否则用户「传不上去」时后台没有任何线索
                val isMainLog = name.startsWith("latest") || name.startsWith("launch") ||
                    name.startsWith("feedback-")
                val keep = isMainLog || when (category) {
                    "launch" ->
                        name.contains("startup-timing") ||
                            name.startsWith("terminal_export") ||
                            name.startsWith("game") || name.startsWith("native_debug") ||
                            name.startsWith("verse_debug") || name.endsWith(".log") ||
                            // 渲染/SDL 诊断链（verse_sdl / verse_glfw / verse_native / verse_jvm_out）：
                            // GL 后端创建失败等渲染问题必须附带
                            name.startsWith("verse_")
                    "download" ->
                        name.contains("modpack-import") || name.contains("updater-download") ||
                            name.contains("loader-install") || name.endsWith(".install") ||
                            name.startsWith("verse_")
                    else -> false
                }
                if (keep && f.exists() && f.length() > 0 && f.length() <= 20 * 1024 * 1024) {
                    candidates.add(f)
                }
            }
        }

        // 每个目录最多留 4 个最近的日志（主日志 latest/launch 优先），总量仍以 8 个封顶
        val grouped = candidates.groupBy { it.parentFile?.absolutePath }
        val picked = grouped.values.flatMap { group ->
            group.sortedWith(
                compareByDescending<File> { f ->
                    // 主日志与渲染诊断链优先保留，避免被其它日志挤出每目录配额
                    val n = f.name
                    when {
                        n.startsWith("latest") || n.startsWith("launch") -> 2
                        n.startsWith("verse_") -> 1
                        else -> 0
                    }
                }.thenByDescending { it.lastModified() }
            ).take(4)
        }
        return picked.sortedByDescending { it.lastModified() }.take(8)
    }

    /**
     * 提交反馈：multipart POST + 验证码 + HMAC 签名 + nonce 防重放。
     * 返回 null 表示成功；否则返回错误信息（含每日 429 提示）。
     */
    suspend fun submitFeedback(
        captchaId: String,
        captchaInput: String,
        category: String,
        categoryLabel: String,
        title: String,
        description: String,
        contact: String,
        signKey: String,
        env: String,
        logFiles: List<File>,
        mediaFiles: List<Pair<String, ByteArray>>
    ): String? {
        val nonce = FeedbackCrypto.randomNonce()
        val ts = System.currentTimeMillis().toString()
        // 站点按 client 区分来源（仅接受「手机端」/「versepc2」），安卓端固定上报手机端
        val clientName = "手机端"
        val desc = description + if (env.isNotBlank()) "\n\n【环境信息】$env" else ""

        val payload = listOf(
            "verse-feedback-v1", captchaId, nonce, ts, clientName, categoryLabel, title, desc
        )
        val sig = FeedbackCrypto.hmacSha256Hex(signKey, payload.joinToString("|"))

        // 表单字段（附件在 post 里按预算追加，重试时可以换更小的附件集）
        val fields = listOf(
            "client" to clientName,
            "title" to title,
            "description" to desc,
            "category" to categoryLabel,
            "email" to contact,
            "captchaId" to captchaId,
            "captchaAnswer" to captchaInput,
            "nonce" to nonce,
            "ts" to ts,
            "sig" to sig
        )

        val attachments = buildAttachments(logFiles, mediaFiles)
        val result = post(fields, attachments)
        if (result.first == null) return null
        // 站点解析 multipart 失败（多为附件总量超过请求体上限）：只带一份主日志重试一次
        if (result.first == "invalid form" && attachments.size > 1) {
            val retry = post(fields, attachments.take(1))
            if (retry.first == null) return null
            return describeError(retry.first, retry.second)
        }
        return describeError(result.first, result.second)
    }

    /**
     * 附件按预算裁剪：单日志只取尾部、总量受 [TOTAL_ATTACHMENT_BUDGET] 约束。
     * 超限的请求会被站点直接 400（invalid form），用户看到的就是「参数错误」。
     */
    private fun buildAttachments(
        logFiles: List<File>,
        mediaFiles: List<Pair<String, ByteArray>>
    ): List<Triple<String, ByteArray, String>> {
        val out = mutableListOf<Triple<String, ByteArray, String>>()
        var budget = TOTAL_ATTACHMENT_BUDGET
        for (f in logFiles) {
            if (out.size >= 10 || budget <= 0) break
            val bytes = readTail(f, minOf(PER_LOG_TAIL_BYTES, budget)) ?: continue
            budget -= bytes.size
            // .log 扩展名会被站点网关静态规则拦截（下载返回 403），统一改挂 .txt
            val uploadName = if (f.name.endsWith(".log")) f.name + ".txt" else f.name
            out.add(Triple(uploadName, bytes, f.name))
        }
        for ((name, bytes) in mediaFiles) {
            if (out.size >= 10 || budget <= 0) break
            val data = if (bytes.size.toLong() > budget) bytes.copyOfRange((bytes.size - budget).toInt(), bytes.size) else bytes
            budget -= data.size
            out.add(Triple(name, data, name))
        }
        return out
    }

    /** 读文件末尾 maxBytes 字节；被截断时在开头加一行说明 */
    private fun readTail(file: File, maxBytes: Long): ByteArray? {
        if (!file.isFile || file.length() == 0L) return null
        val limit = maxBytes.coerceAtLeast(64 * 1024L)
        return runCatching {
            if (file.length() <= limit) {
                file.readBytes()
            } else {
                val start = file.length() - limit
                val buf = ByteArray(limit.toInt())
                java.io.RandomAccessFile(file, "r").use { raf ->
                    raf.seek(start)
                    raf.readFully(buf)
                }
                val note = "[日志较大，仅保留末尾 ${limit / 1024}KB]\n".toByteArray()
                note + buf
            }
        }.getOrNull()
    }

    /** 真正发出请求；返回 (错误码标识, 原始响应体)，错误码标识为 null 表示成功 */
    private fun post(
        fields: List<Pair<String, String>>,
        attachments: List<Triple<String, ByteArray, String>>
    ): Pair<String?, String> {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        fields.forEach { (key, value) -> builder.addFormDataPart(key, value) }
        attachments.forEachIndexed { index, (uploadName, bytes, _) ->
            builder.addFormDataPart(
                "file$index", uploadName,
                bytes.toRequestBody("application/octet-stream".toMediaType())
            )
        }
        val req = Request.Builder().url("$SITE/api/feedback").post(builder.build()).build()
        val totalBytes = attachments.sumOf { it.second.size.toLong() }
        return try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                FeedbackLog.write(
                    "提交附件 ${attachments.size} 个 / ${totalBytes / 1024}KB，HTTP ${resp.code} ${body.take(200)}"
                )
                if (resp.code in 200..299) null to body else body to body
            }
        } catch (e: Exception) {
            FeedbackLog.write("提交异常：${e.javaClass.simpleName}: ${e.message}")
            ("network: ${e.message ?: e.javaClass.simpleName}") to ""
        }
    }

    /** 把服务端错误码翻译成用户能照做的提示 */
    private fun describeError(code: String?, body: String): String {
        if (code == null) return ""
        return when {
            code.startsWith("network:") -> "网络连接失败（${code.removePrefix("network: ")}），请检查网络后重试"
            code.contains("daily limit") ->
                "今天这个网络已提交过反馈（每天上限 $DAILY_LIMIT 条，同一 WiFi / 运营商出口会共用额度）。" +
                    "可以换一个网络（如切换流量/WiFi）或明天再试"
            code.contains("invalid form") ->
                "附件过多导致提交失败（已自动精简重试）。如仍失败，请只勾选「启动日志」后重试"
            code.contains("file too large") -> "单个日志超过 20 MB，请清理日志目录后重试"
            code.contains("too many files") -> "附件数量超过 10 个，请减少后重试"
            code.contains("captcha incorrect") -> "验证码不正确，请点击验证码刷新后重试"
            code.contains("invalid signature") -> "提交校验失败，请刷新验证码后重试"
            code.contains("replay denied") -> "请勿重复提交，请刷新验证码后重试"
            code.contains("title and description required") -> "标题或描述为空，请补充后重试"
            else -> "提交失败（$body），请稍后重试"
        }
    }
}