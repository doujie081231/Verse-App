package com.verse.explorer.core.install

/** 每次任务独占；重试从零计数不会重复累计之前已收到的字节。 */
internal class DownloadAggregator(private val clock: () -> Long = System::currentTimeMillis) {
    private val lastByKey = mutableMapOf<String, Long>()
    private var lastTime = clock()
    private var lastBytes = 0L
    @Volatile var speed = 0L; private set
    @Volatile var downloaded = 0L; private set

    @Synchronized fun reset() {
        lastByKey.clear()
        downloaded = 0
        speed = 0
        lastBytes = 0
        lastTime = clock()
    }

    @Synchronized fun report(key: String, fileDownloaded: Long) {
        val previous = lastByKey[key] ?: 0L
        if (fileDownloaded <= previous) return
        lastByKey[key] = fileDownloaded
        downloaded += fileDownloaded - previous
        val now = clock()
        val elapsed = now - lastTime
        if (elapsed >= 500) {
            speed = (downloaded - lastBytes) * 1000 / elapsed
            lastBytes = downloaded
            lastTime = now
        }
    }
}
