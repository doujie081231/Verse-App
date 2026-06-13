package com.verse.explorer.core.util

import java.security.MessageDigest

object HashUtils {
    
    fun sha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    fun sha1File(file: java.io.File): String? {
        if (!file.exists()) return null
        
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    fun verifySha1(file: java.io.File, expectedSha1: String): Boolean {
        val actualSha1 = sha1File(file) ?: return false
        return actualSha1.equals(expectedSha1, ignoreCase = true)
    }
}
