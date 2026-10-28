package com.verse.explorer.core.data.feedback

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 反馈提交诊断日志。
 *
 * 反馈页「传不上去」时，用户只能描述现象（例如「参数错误」），
 * 后台看不到请求是怎么发的。这里把每次提交的附件数量/字节数/HTTP 码写进
 * logs/feedback-upload.log，随下一次反馈一并上传（该文件名被
 * [FeedbackRepository.collectLogs] 固定收集）。
 */
object FeedbackLog {

    private const val TAG = "FeedbackUpload"
    private const val FILE_NAME = "feedback-upload.log"
    private const val MAX_BYTES = 512 * 1024L

    @Volatile
    private var logFile: File? = null

    /** 由 [FeedbackRepository.collectLogs] 在拿到 context 时绑定日志文件 */
    fun attach(context: Context) {
        if (logFile != null) return
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(File(base, "VerseExplorerX"), "logs")
        if (!dir.exists()) dir.mkdirs()
        logFile = File(dir, FILE_NAME)
    }

    fun write(message: String) {
        Log.i(TAG, message)
        val file = logFile ?: return
        runCatching {
            // 超过上限就重建，避免日志无限增长
            if (file.length() > MAX_BYTES) file.delete()
            file.appendText("${timestamp()} $message\n")
        }
    }

    private fun timestamp(): String {
        val fmt = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
        return fmt.format(java.util.Date())
    }
}
