package com.verse.explorer.launcher.util

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object SystemInfo {
    
    fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }
    
    fun getAndroidVersion(): String {
        return "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }
    
    fun getArchitecture(): String {
        return when {
            Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "aarch64"
            Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "arm"
            Build.SUPPORTED_ABIS.contains("x86_64") -> "x86_64"
            Build.SUPPORTED_ABIS.contains("x86") -> "x86"
            else -> Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        }
    }
    
    fun getCPUInfo(): Map<String, String> {
        val info = mutableMapOf<String, String>()
        
        try {
            val cpuInfo = File("/proc/cpuinfo").readText()
            cpuInfo.split("\n").forEach { line ->
                val parts = line.split(":")
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    info[key] = value
                }
            }
        } catch (e: Exception) {
        }
        
        return info
    }
    
    fun getCPUCoreCount(): Int {
        return Runtime.getRuntime().availableProcessors()
    }
    
    fun getTotalMemory(): Long {
        try {
            val memInfo = File("/proc/meminfo").readText()
            val totalRegex = "MemTotal:\\s+(\\d+)".toRegex()
            val match = totalRegex.find(memInfo)
            return match?.groupValues?.get(1)?.toLongOrNull()?.times(1024) ?: 0L
        } catch (e: Exception) {
            return 0L
        }
    }
    
    fun getAvailableMemory(): Long {
        try {
            val memInfo = File("/proc/meminfo").readText()
            val availableRegex = "MemAvailable:\\s+(\\d+)".toRegex()
            val match = availableRegex.find(memInfo)
            return match?.groupValues?.get(1)?.toLongOrNull()?.times(1024) ?: 0L
        } catch (e: Exception) {
            return 0L
        }
    }
    
    fun getStorageInfo(): Pair<Long, Long> {
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val available = stat.availableBlocksLong * stat.blockSizeLong
        return Pair(total, available)
    }
    
    fun getGPUInfo(): String {
        return try {
            val process = Runtime.getRuntime().exec("cat /sys/class/kgsl/kgsl-3d0/devinfo")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            process.waitFor()
            
            val lines = output.split("\n")
            lines.find { it.contains("gpu_model") }?.substringAfter(":")?.trim() ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    fun hasVulkanSupport(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("pm list features")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            process.waitFor()
            
            output.contains("android.hardware.vulkan")
        } catch (e: Exception) {
            false
        }
    }
    
    fun getOpenGLVersion(): String {
        return try {
            val process = Runtime.getRuntime().exec("dumpsys SurfaceFlinger")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            process.waitFor()
            
            val regex = "OpenGL ES (\\d+\\.\\d+)".toRegex()
            regex.find(output)?.groupValues?.get(1) ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    fun getProcessId(): Int = Process.myPid()
    
    fun getPackageName(context: Context): String = context.packageName
    
    fun getVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    fun getVersionCode(context: Context): Long {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    fun isLowRamDevice(context: Context): Boolean {
        return context.getSystemService(Context.ACTIVITY_SERVICE)
            .let { it as? android.app.ActivityManager }
            ?.isLowRamDevice ?: false
    }
    
    fun getMemoryClass(context: Context): Int {
        return context.getSystemService(Context.ACTIVITY_SERVICE)
            .let { it as? android.app.ActivityManager }
            ?.memoryClass ?: 128
    }
    
    fun getLargeMemoryClass(context: Context): Int {
        return context.getSystemService(Context.ACTIVITY_SERVICE)
            .let { it as? android.app.ActivityManager }
            ?.largeMemoryClass ?: 256
    }
}
