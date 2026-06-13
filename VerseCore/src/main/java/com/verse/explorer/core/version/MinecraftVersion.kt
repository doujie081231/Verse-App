package com.verse.explorer.core.version

import com.google.gson.annotations.SerializedName

enum class VersionType {
    @SerializedName("release") RELEASE,
    @SerializedName("snapshot") SNAPSHOT,
    @SerializedName("old_alpha") OLD_ALPHA,
    @SerializedName("old_beta") OLD_BETA
}

data class MinecraftVersion(
    val id: String = "",
    val type: VersionType = VersionType.RELEASE,
    val releaseTime: String = "",
    val time: String = "",
    val url: String = "",
    val assetIndex: AssetIndex? = null,
    val libraries: List<Library> = emptyList(),
    val mainClass: String = "",
    val arguments: Arguments? = null,
    val javaVersion: JavaVersion? = null,
    val downloads: VersionDownloads? = null,
    val inheritsFrom: String? = null,
    val minecraftArguments: String? = null,
    val logging: LoggingConfig? = null,
    val minimumLauncherVersion: Int = 0,
    val complianceLevel: Int = 0
)

data class AssetIndex(
    val id: String = "",
    val sha1: String = "",
    val size: Long = 0,
    val totalSize: Long = 0,
    val url: String = "",
    val known: Boolean = true
)

data class Library(
    val name: String = "",
    val downloads: LibraryDownloads? = null,
    val rules: List<Rule>? = null,
    val natives: Map<String, String>? = null,
    val extract: ExtractRule? = null,
    val url: String? = null
)

data class LibraryDownloads(
    val artifact: Artifact? = null,
    val classifiers: Map<String, Artifact>? = null
)

data class Artifact(
    val path: String = "",
    val sha1: String = "",
    val size: Long = 0,
    val url: String = ""
)

data class Rule(
    val action: String = "",
    val os: OsRule? = null,
    val features: Map<String, Boolean>? = null
)

data class OsRule(
    val name: String? = null,
    val arch: String? = null,
    val version: String? = null
)

data class ExtractRule(
    val exclude: List<String> = emptyList()
)

data class Arguments(
    val game: List<Any>? = null,
    val jvm: List<Any>? = null
)

data class JavaVersion(
    val component: String = "java-runtime-gamma",
    val majorVersion: Int = 8
)

data class VersionDownloads(
    val client: Artifact? = null,
    val client_mappings: Artifact? = null,
    val server: Artifact? = null,
    val server_mappings: Artifact? = null
)

data class LoggingConfig(
    val client: LoggingClientConfig? = null
)

data class LoggingClientConfig(
    val argument: String = "",
    val file: Artifact = Artifact(),
    val type: String = ""
)