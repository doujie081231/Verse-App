package com.verse.explorer.app.launch

import android.content.Context
import android.content.Intent
import com.verse.explorer.app.ui.game.GameActivity
import com.verse.explorer.core.launch.LaunchRequest
import com.verse.explorer.core.launch.LaunchRequestCodec

/** Android 启动边界；旧 Intent 键和默认值由 Core 中的协议统一维护。 */
object LaunchIntentCodec {
    fun create(context: Context, request: LaunchRequest): Intent =
        write(Intent(context, GameActivity::class.java), request)

    fun write(intent: Intent, request: LaunchRequest): Intent = intent.apply {
        LaunchRequestCodec.encode(request).forEach { (key, value) ->
            when (value) {
                is String -> putExtra(key, value)
                is Int -> putExtra(key, value)
                is Boolean -> putExtra(key, value)
                null -> putExtra(key, null as String?)
                else -> error("Unsupported launch value: $key")
            }
        }
    }

    @Suppress("DEPRECATION")
    fun read(intent: Intent): Result<LaunchRequest> {
        val extras = intent.extras
        return LaunchRequestCodec.decode(extras?.keySet()?.associateWith { extras.get(it) } ?: emptyMap())
    }
}
