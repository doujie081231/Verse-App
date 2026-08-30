package com.verse.explorer.app.domain.model.version

data class DetailLoaderOption(
    val id: String,
    val name: String,
    val desc: String,
    val iconAssetPath: String
)

data class LoaderVersion(
    val version: String,
    val type: String = ""
)

data class FabricLoaderMeta(val loader: FabricLoaderMetaInner)
data class FabricLoaderMetaInner(val version: String)
data class ForgeRaw(val version: String, val type: String)
data class NeoForgeEntry(val version: String)
data class OptiFineEntry(
    val fileName: String = "",
    val type: String = "",
    val patch: String = ""
)
