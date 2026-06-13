package com.verse.explorer.core.context

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object Contexts {
    private var _appContext: Context? = null
    val appContext: Context get() = _appContext!!

    private val _gameDir = MutableStateFlow(File(""))
    val gameDir: StateFlow<File> = _gameDir.asStateFlow()

    private val _javaHome = MutableStateFlow<File?>(null)
    val javaHome: StateFlow<File?> = _javaHome.asStateFlow()

    private val _selectedAccountId = MutableStateFlow<Int?>(null)
    val selectedAccountId: StateFlow<Int?> = _selectedAccountId.asStateFlow()

    fun initialize(context: Context) {
        _appContext = context.applicationContext
        _gameDir.value = File(context.getExternalFilesDir(null), "VerseExplorerX").also { it.mkdirs() }
    }

    fun setGameDir(dir: File) { _gameDir.value = dir }
    fun setJavaHome(home: File?) { _javaHome.value = home }
    fun setSelectedAccountId(id: Int?) { _selectedAccountId.value = id }

    fun getNativesDir(): File = File(gameDir.value, "natives").also { it.mkdirs() }
    fun getVersionsDir(): File = File(gameDir.value, "versions").also { it.mkdirs() }
    fun getLibrariesDir(): File = File(gameDir.value, "libraries").also { it.mkdirs() }
    fun getAssetsDir(): File = File(gameDir.value, "assets").also { it.mkdirs() }
    fun getModsDir(): File = File(gameDir.value, "mods").also { it.mkdirs() }
    fun getLogDir(): File = File(gameDir.value, "logs").also { it.mkdirs() }
    fun getJreDir(): File = File(gameDir.value, "jre").also { it.mkdirs() }
}
