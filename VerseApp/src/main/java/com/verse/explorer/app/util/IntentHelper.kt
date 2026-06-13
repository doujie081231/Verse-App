package com.verse.explorer.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

object IntentHelper {
    
    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
    
    fun shareText(context: Context, text: String, title: String = "分享") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, title)
        context.startActivity(chooser)
    }
    
    fun shareFile(context: Context, file: File, mimeType: String = "*/*") {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } else {
            Uri.fromFile(file)
        }
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(intent, "分享文件")
        context.startActivity(chooser)
    }
    
    fun openFile(context: Context, file: File, mimeType: String = "*/*") {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } else {
            Uri.fromFile(file)
        }
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(intent)
    }
    
    fun sendEmail(context: Context, email: String, subject: String? = null, body: String? = null) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            body?.let { putExtra(Intent.EXTRA_TEXT, it) }
        }
        
        context.startActivity(intent)
    }
    
    fun openAppSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}
