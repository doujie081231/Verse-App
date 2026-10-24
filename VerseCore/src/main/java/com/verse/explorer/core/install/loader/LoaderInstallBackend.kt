package com.verse.explorer.core.install.loader

import java.io.File

/** 加载器安装能力；实现不得向业务层暴露第三方任务或版本类型。 */
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
