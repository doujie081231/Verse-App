package com.verse.explorer.app.download

import com.verse.explorer.core.download.MirrorSourceManager

/**
 * 下载源管理（UI 层壳）：转发到 VerseCore 的 MirrorSourceManager
 */
object DownloadSourceManager {

    enum class Source(val id: String, val label: String, val description: String) {
        AUTO("auto", "智能混合", "并行测速选最快源，自动换源，镜像熔断保护"),
        BMCLAPI("bmclapi", "BMCLAPI 镜像", "国内镜像源 (bangbang93.com)"),
        OFFICIAL("official", "官方源", "Mojang 官方服务器"),
        MOJANG("mojang", "Mojang", "Mojang 官方（不经过任何镜像）")
    }

    fun getSourceLabel(source: String): String = MirrorSourceManager.getSourceLabel(source)

    fun getCurrentSourceLabel(): String {
        val source = com.verse.explorer.app.data.SettingsRepository.getDownloadSource()
        return MirrorSourceManager.getSourceLabel(source)
    }
}
