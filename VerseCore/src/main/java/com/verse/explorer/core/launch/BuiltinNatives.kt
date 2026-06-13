package com.verse.explorer.core.launch

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object BuiltinNatives {
    
    private const val NATIVES_ASSET_PATH = "natives"
    
    private val REQUIRED_LIBS = listOf(
        "liblwjgl.so",
        "liblwjgl_glfw.so",
        "liblwjgl_jemalloc.so",
        "libopenal.so",
        "liblwjgl_stb.so",
        "liblwjgl_opengl.so",
        "liblwjgl_tinyfd.so",
        "liblwjgl_assimp.so"
    )
    
    fun extractBuiltinNatives(context: Context, targetDir: File): Int {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        
        var extractedCount = 0
        
        try {
            val assetManager = context.assets
            val nativesFiles = try {
                assetManager.list(NATIVES_ASSET_PATH)?.toList() ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.w("BuiltinNatives", "No builtin natives found in assets", e)
                return 0
            }
            
            android.util.Log.d("BuiltinNatives", "Found ${nativesFiles.size} files in assets/$NATIVES_ASSET_PATH")
            
            for (fileName in nativesFiles) {
                if (fileName.endsWith(".so")) {
                    try {
                        val targetFile = File(targetDir, fileName)
                        
                        if (!targetFile.exists() || targetFile.length() == 0L) {
                            assetManager.open("$NATIVES_ASSET_PATH/$fileName").use { input ->
                                FileOutputStream(targetFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            android.util.Log.d("BuiltinNatives", "Extracted: $fileName (${targetFile.length()} bytes)")
                            extractedCount++
                        } else {
                            android.util.Log.d("BuiltinNatives", "Skipped (exists): $fileName")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BuiltinNatives", "Failed to extract $fileName", e)
                    }
                }
            }
            
            val soFiles = targetDir.listFiles()?.filter { it.name.endsWith(".so") && it.isFile } ?: emptyList()
            android.util.Log.d("BuiltinNatives", "Extraction complete: $extractedCount new files, ${soFiles.size} total .so files")
            
        } catch (e: Exception) {
            android.util.Log.e("BuiltinNatives", "Error extracting builtin natives", e)
        }
        
        return extractedCount
    }
    
    fun hasAllRequiredNatives(nativesDir: File): Boolean {
        if (!nativesDir.exists()) return false
        
        val existingFiles = nativesDir.listFiles()
            ?.filter { it.name.endsWith(".so") && it.isFile && it.length() > 1024 }
            ?.map { it.name }
            ?.toSet() ?: emptySet()
        
        val hasRequired = REQUIRED_LIBS.all { lib -> existingFiles.contains(lib) }
        
        if (!hasRequired) {
            val missing = REQUIRED_LIBS.filter { !existingFiles.contains(it) }
            android.util.Log.d("BuiltinNatives", "Missing required libs: $missing")
        }
        
        return hasRequired
    }
    
    fun getMissingRequiredNatives(nativesDir: File): List<String> {
        if (!nativesDir.exists()) return REQUIRED_LIBS
        
        val existingFiles = nativesDir.listFiles()
            ?.filter { it.name.endsWith(".so") && it.isFile && it.length() > 1024 }
            ?.map { it.name }
            ?.toSet() ?: emptySet()
        
        return REQUIRED_LIBS.filter { !existingFiles.contains(it) }
    }
    
    fun ensureNativesExist(context: Context, gameDir: File): Boolean {
        val nativesDir = File(gameDir, "natives")
        
        if (hasAllRequiredNatives(nativesDir)) {
            android.util.Log.d("BuiltinNatives", "All required natives already exist")
            return true
        }
        
        android.util.Log.d("BuiltinNatives", "Extracting builtin natives...")
        val extracted = extractBuiltinNatives(context, nativesDir)
        
        return hasAllRequiredNatives(nativesDir)
    }
}
