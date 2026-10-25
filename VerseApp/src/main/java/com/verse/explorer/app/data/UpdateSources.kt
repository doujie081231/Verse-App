package com.verse.explorer.app.data

/**
 * 更新包下载源（官网 API 只负责检测更新，下载走以下镜像）。
 *
 * 优先级：Gitee 发行版附件（国内主源）→ GitHub Release（镜像备源）。
 * Gitee 免费版单文件 100MB 上限，安装包按 ~95MB 分卷上传；
 * Gitee 仓库 raw 路径对大文件强制登录，故走发行版附件下载地址（匿名可下）。
 * URL 约定：分卷第一卷以 `.part1` 结尾，后续卷把 `.part1` 替换为 `.partN`。
 */
object UpdateSources {

    private const val GITEE_OWNER = "doujie081231"
    private const val GITEE_REPO = "verse-apk"
    private const val GITHUB_OWNER = "doujie081231"
    private const val GITHUB_REPO = "Verse-App"

    /**
     * 按版本名生成分卷第一卷的候选 URL，顺序即优先级。
     * 例：1.0.1 →
     *  - https://gitee.com/doujie081231/verse-apk/releases/download/v1.0.1/Verse-1.0.1-arm64-v8a.apk.part1
     *  - https://github.com/doujie081231/Verse-App/releases/download/v1.0.1/Verse-1.0.1-arm64-v8a.apk.part1
     */
    fun part1Urls(versionName: String): List<String> {
        val name = "Verse-$versionName-arm64-v8a.apk.part1"
        return listOf(
            "https://gitee.com/$GITEE_OWNER/$GITEE_REPO/releases/download/v$versionName/$name",
            "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/v$versionName/$name"
        )
    }
}
