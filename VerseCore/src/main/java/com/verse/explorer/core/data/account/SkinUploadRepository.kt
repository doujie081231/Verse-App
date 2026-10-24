package com.verse.explorer.core.data.account

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object SkinUploadRepository {
    fun saveSkin(context: Context, uri: Uri, accountId: Int): String? {
        return try {
            val skinsDir = File(context.filesDir, "skins")
            if (!skinsDir.exists()) skinsDir.mkdirs()
            val fileName = "custom_${accountId}_${System.currentTimeMillis()}.png"
            val targetFile = File(skinsDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            fileName
        } catch (_: Exception) {
            null
        }
    }

    fun getSkinFile(context: Context, fileName: String): File {
        return File(context.filesDir, "skins/$fileName")
    }

    fun getSkinFileUrl(context: Context, fileName: String): String {
        val file = getSkinFile(context, fileName)
        return "file://${file.absolutePath}"
    }
}
