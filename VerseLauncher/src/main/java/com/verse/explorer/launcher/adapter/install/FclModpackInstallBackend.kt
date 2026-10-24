package com.verse.explorer.launcher.adapter.install

import com.verse.explorer.core.install.modpack.ModpackInfo
import com.verse.explorer.core.install.modpack.ModpackInstallBackend
import com.verse.explorer.core.install.modpack.ModpackInstallOperation
import java.io.File
import java.nio.charset.Charset
import com.verse.explorer.core.install.InstallationPaths

class FclModpackInstallBackend : ModpackInstallBackend {
    override fun readModpack(zipFile: File, charset: Charset): ModpackInfo =
        ModpackInfo(FclModpackInstallOperation().readModpack(zipFile, charset).name)

    override fun newOperation(): ModpackInstallOperation = FclModpackInstallOperation()

    override fun requiresNewName(gameDir: File, name: String): Boolean =
        InstallationPaths(gameDir).version(name).exists()
}
