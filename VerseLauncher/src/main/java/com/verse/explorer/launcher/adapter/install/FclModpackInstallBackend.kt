package com.verse.explorer.launcher.adapter.install

import com.verse.explorer.core.install.modpack.ModpackInfo
import com.verse.explorer.core.install.modpack.ModpackInstallBackend
import com.verse.explorer.core.install.modpack.ModpackInstallOperation
import java.io.File
import java.nio.charset.Charset

class FclModpackInstallBackend : ModpackInstallBackend {
    override fun readModpack(zipFile: File, charset: Charset): ModpackInfo =
        ModpackInfo(FclModpackInstallOperation().readModpack(zipFile, charset).name)

    override fun newOperation(): ModpackInstallOperation = FclModpackInstallOperation()
}
