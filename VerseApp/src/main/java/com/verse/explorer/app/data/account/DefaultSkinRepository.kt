package com.verse.explorer.app.data.account

import android.content.Context
import java.io.File

object DefaultSkinRepository {
    data class DefaultSkin(val id: String, val name: String, val file: String, val model: String = "default")

    val skins = listOf(
        DefaultSkin("steve", "Steve", "steve.png", "default"),
        DefaultSkin("alex", "Alex", "alex.png", "slim")
    )

    fun getSkinUrl(context: Context, skinFile: String): String {
        return if (skinFile.startsWith("custom_")) {
            val file = File(context.filesDir, "skins/$skinFile")
            "file://${file.absolutePath}"
        } else {
            "file:///android_asset/skins/$skinFile"
        }
    }
}
