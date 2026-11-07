package com.verse.explorer.core.install.loader

import java.io.File

interface LoaderInstallBackend {
    suspend fun install(
        gameDir: File,
        versionId: String,
        loaderType: String,
        loaderVersion: String,
        baseVersionId: String = versionId,
        newVersionId: String = "$versionId-$loaderType-$loaderVersion",
        progress: (String, Float) -> Unit
    ): Result<Unit>
}
