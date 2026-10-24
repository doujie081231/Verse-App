package com.verse.explorer.app.data

import android.content.Context
import com.google.gson.Gson
import com.verse.explorer.controller.data.ControlLayout
import java.io.File

class KeyMappingRepository(context: Context) {

    private val mappingsDir = File(context.filesDir, "key_mappings")
    private val gson = Gson()

    init {
        if (!mappingsDir.exists()) {
            mappingsDir.mkdirs()
        }
    }

    fun saveMapping(layout: ControlLayout) {
        val file = File(mappingsDir, "${layout.name}.json")
        file.writeText(gson.toJson(layout))
    }

    fun loadMapping(name: String): ControlLayout? {
        val file = File(mappingsDir, "$name.json")
        if (!file.exists()) return null

        return try {
            val json = file.readText()
            gson.fromJson(json, ControlLayout::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getAllMappings(): List<ControlLayout> {
        return mappingsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                loadMapping(file.nameWithoutExtension)
            } ?: emptyList()
    }

    fun deleteMapping(name: String) {
        val file = File(mappingsDir, "$name.json")
        if (file.exists()) {
            file.delete()
        }
    }

    fun exportMapping(layout: ControlLayout, targetFile: File) {
        targetFile.writeText(gson.toJson(layout))
    }

    fun importMapping(sourceFile: File): ControlLayout? {
        return try {
            val json = sourceFile.readText()
            val layout = gson.fromJson(json, ControlLayout::class.java)
            saveMapping(layout)
            layout
        } catch (e: Exception) {
            null
        }
    }
}
