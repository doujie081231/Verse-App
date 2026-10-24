package com.verse.explorer.app.terracotta

import com.verse.explorer.app.ui.VerseLogger
import java.net.URI
import java.net.URISyntaxException

/**
 * 获取陶瓦联机公共节点列表
 *
 * 并按地区过滤——保留未标注地区的节点，其余节点仅在
 * 设备地区与节点地区一致时保留（大陆设备选 CN 节点，其它地区设备选非 CN 节点）。
 */
object TerracottaNodeList {
    private const val TAG = "TerracottaNodeList"
    private const val NODE_LIST_URL = "https://terracotta.glavo.site/nodes"

    private data class TerracottaNode(
        val url: String,
        val region: String?
    ) {
        fun validate(): Boolean {
            if (url.isBlank()) return false
            return try {
                URI(url)
                true
            } catch (_: URISyntaxException) {
                false
            }
        }
    }

    private val isChinaMainland: Boolean = run {
        if (java.time.ZoneId.systemDefault().id == "Asia/Shanghai") return@run true
        val offsetSeconds = java.time.ZonedDateTime.now().offset.totalSeconds
        if (offsetSeconds == 8 * 3600) {
            "CN".equals(java.util.Locale.getDefault().country, ignoreCase = true)
        } else {
            false
        }
    }

    @Volatile
    private var list: List<URI>? = null

    fun fetch(): List<URI> {
        list?.let { return it }

        synchronized(this) {
            list?.let { return it }

            var result: List<URI> = emptyList()
            try {
                val request = okhttp3.Request.Builder()
                    .url(NODE_LIST_URL)
                    .header("User-Agent", "VerseExplorerX/Terracotta")
                    .build()
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val nodes = com.google.gson.Gson().fromJson(
                            response.body?.string(),
                            com.google.gson.reflect.TypeToken.getParameterized(
                                List::class.java, TerracottaNode::class.java
                            ).type
                        ) as? List<TerracottaNode>
                        result = nodes
                            ?.filter { node ->
                                if (node == null) return@filter false
                                if (!node.validate()) {
                                    VerseLogger.warn("无效的陶瓦节点: ${node.url}", TAG)
                                    return@filter false
                                }
                                node.region.isNullOrBlank() ||
                                    (isChinaMainland == "CN".equals(node.region, ignoreCase = true))
                            }
                            ?.map { URI.create(it.url) }
                            ?.toList()
                            ?: emptyList()
                    }
                }
                VerseLogger.info("陶瓦节点列表: $result", TAG)
            } catch (e: Exception) {
                VerseLogger.warn("获取陶瓦节点列表失败: ${e.message}", TAG)
                result = emptyList()
            }

            list = result
            return result
        }
    }
}
