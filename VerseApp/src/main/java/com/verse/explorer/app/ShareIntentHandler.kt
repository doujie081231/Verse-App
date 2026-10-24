package com.verse.explorer.app

import android.content.Intent

/**
 * 解析外部分享/打开进来的整合包 Uri：
 *  ACTION_SEND（微信等长按文件「分享」→ EXTRA_STREAM）；
 *  ACTION_VIEW（文件管理器点击 zip/mrpack「打开方式」→ data，走 ModpackImportAlias 入口）。
 *
 * @param onUri 命中待导入 Uri 时的回调（由调用方写入自身状态）
 */
internal fun handleShareIntent(intent: Intent?, onUri: (android.net.Uri) -> Unit) {
    when (intent?.action) {
        Intent.ACTION_SEND -> {
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } ?: return
            onUri(uri)
        }
        Intent.ACTION_VIEW -> {
            val uri = intent.data ?: return
            onUri(uri)
        }
    }
}
