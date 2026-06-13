package com.verse.explorer.app.download

object DownloadSourceManager {

    enum class Source(val id: String, val label: String, val description: String) {
        AUTO("auto", "自动选择", "优先官方源，加载缓慢时自动切换镜像"),
        BMCLAPI("bmclapi", "BMCLAPI 镜像", "国内镜像源 (bangbang93.com)"),
        MOJANG("mojang", "Mojang 官方", "Mojang 官方服务器")
    }

    data class DownloadSourceUrls(
        val versionManifest: String,
        val versionJson: (String) -> String,
        val clientJar: (String) -> String,
        val library: (String, String) -> String,
        val assetIndex: (String) -> String,
        val asset: (String) -> String,
        val javaRuntime: (Int) -> String
    )

    private val MOJANG_BASE = "https://launchermeta.mojang.com"
    private val MOJANG_RESOURCES = "https://resources.download.minecraft.net"
    private val MOJANG_LIBRARIES = "https://libraries.minecraft.net"
    private val BMCLAPI_BASE = "https://bmclapi2.bangbang93.com"
    private val BMCLAPI_RESOURCES = "https://bmclapi2.bangbang93.com/assets"
    private val BMCLAPI_LIBRARIES = "https://bmclapi2.bangbang93.com/maven"

    fun getUrls(source: String): DownloadSourceUrls {
        return when (source) {
            "mojang" -> DownloadSourceUrls(
                versionManifest = "$MOJANG_BASE/mc/game/version_manifest.json",
                versionJson = { id -> "$MOJANG_BASE/v1/versions/$id.json" },
                clientJar = { id -> "$MOJANG_BASE/v1/versions/$id/client.jar" },
                library = { group, name ->
                    "$MOJANG_LIBRARIES/${group.replace('.', '/')}/$name"
                },
                assetIndex = { id -> "$MOJANG_BASE/v1/assets/$id" },
                asset = { hash -> "$MOJANG_RESOURCES/${hash.take(2)}/$hash" },
                javaRuntime = { version -> "https://api.adoptium.net/v3/binary/latest/$version/ga/windows/x64/jdk/hotspot/normal/eclipse" }
            )
            "bmclapi" -> DownloadSourceUrls(
                versionManifest = "$BMCLAPI_BASE/mc/game/version_manifest.json",
                versionJson = { id -> "$BMCLAPI_BASE/version/$id/json" },
                clientJar = { id -> "$BMCLAPI_BASE/version/$id/client" },
                library = { group, name ->
                    "$BMCLAPI_LIBRARIES/${group.replace('.', '/')}/$name"
                },
                assetIndex = { id -> "$BMCLAPI_BASE/assets/indexes/$id" },
                asset = { hash -> "$BMCLAPI_RESOURCES/${hash.take(2)}/$hash" },
                javaRuntime = { version ->
                    "https://bmclapi2.bangbang93.com/java-runtime/linux/$version/jre.tar.gz"
                }
            )
            else -> getUrls("bmclapi")
        }
    }

    fun resolveFullUrl(source: String, partialUrl: String): String {
        if (partialUrl.startsWith("http://") || partialUrl.startsWith("https://")) {
            if (source == "mojang") return partialUrl
            if (source == "bmclapi") {
                return partialUrl
                    .replace("https://launchermeta.mojang.com", BMCLAPI_BASE)
                    .replace("https://launcher.mojang.com", BMCLAPI_BASE)
                    .replace("https://resources.download.minecraft.net", BMCLAPI_RESOURCES)
                    .replace("https://libraries.minecraft.net", BMCLAPI_LIBRARIES)
            }
        }
        return partialUrl
    }
}