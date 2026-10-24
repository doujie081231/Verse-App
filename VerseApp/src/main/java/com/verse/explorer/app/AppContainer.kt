package com.verse.explorer.app

import android.app.Application
import android.content.Context
import com.verse.explorer.core.install.GameInstaller
import com.verse.explorer.core.install.InstallationSnapshot
import com.verse.explorer.core.install.InstallationPaths
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.download.MirrorSourceManager
import com.verse.explorer.core.install.VersionInstallCoordinator
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

    private val appScope get() = (application as VerseApplication).appScope
    val runtimeInstalls by lazy { com.verse.explorer.app.setup.RuntimeInstallCoordinator(application, appScope) }
    val versionInstalls by lazy { VersionInstallCoordinator(appScope, ::installationSnapshot) { GameInstaller(it, loaderInstallBackend) } }

    fun installationSnapshot() = InstallationSnapshot(
        InstallationPaths(GameConfig.getGameDir(application)), MirrorSourceManager.getCurrentSource(application)
    )

    fun newGameInstaller(): GameInstaller = GameInstaller(installationSnapshot(), loaderInstallBackend)
    val modpackImports by lazy { com.verse.explorer.app.install.ModpackImportController(application, modpackTask("local-import")) }

    private val resourceTasks = java.util.concurrent.ConcurrentHashMap<String, com.verse.explorer.app.install.ResourceInstallActions>()
    fun resourceActions(key: String) = resourceTasks.computeIfAbsent(key) {
        com.verse.explorer.app.install.ResourceInstallActions(application, appScope, ::installationSnapshot)
    }

    private val modpackTasks = java.util.concurrent.ConcurrentHashMap<String, com.verse.explorer.app.install.ModpackInstallController>()
    fun modpackTask(key: String) = modpackTasks.computeIfAbsent(key) {
        com.verse.explorer.app.install.ModpackInstallController(appScope, newModpackInstaller())
    }

    fun newModpackInstaller(): ModpackInstaller = ModpackInstaller(modpackInstallBackend)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as VerseApplication).container
