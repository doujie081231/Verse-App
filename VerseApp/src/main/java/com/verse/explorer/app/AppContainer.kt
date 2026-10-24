package com.verse.explorer.app

import android.app.Application
import android.content.Context
import com.verse.explorer.core.install.GameInstaller
import com.verse.explorer.core.install.loader.LoaderInstallBackend
import com.verse.explorer.core.install.modpack.ModpackInstaller
import com.verse.explorer.launcher.adapter.install.FclLoaderInstallBackend
import com.verse.explorer.launcher.adapter.install.FclModpackInstallBackend
import com.verse.explorer.app.permissions.PermissionRequests

/** 应用组合根：第三方实现只在这里选择，业务层接收自身定义的能力接口。 */
class AppContainer(private val application: Application) {
    val permissions = PermissionRequests()
    val loaderInstallBackend: LoaderInstallBackend = FclLoaderInstallBackend()
    private val modpackInstallBackend = FclModpackInstallBackend()

    fun newGameInstaller(): GameInstaller = GameInstaller(application, loaderInstallBackend)
    fun newModpackInstaller(): ModpackInstaller = ModpackInstaller(modpackInstallBackend)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as VerseApplication).container
