package com.verse.explorer.core.download

data class DownloadConfig(
    val maxConcurrentDownloads: Int = 3,
    val maxChunksPerFile: Int = 4,
    val minFileSizeForChunking: Long = 5 * 1024 * 1024,
    val enableMultiThread: Boolean = true,
    val enableResume: Boolean = true,
    val speedLimit: Long = 0
) {
    companion object {
        fun createDefault(
            maxThreads: Int = 64,
            maxChunks: Int = 64,
            minChunkSize: Long = 1024 * 1024,
            multiThread: Boolean = true,
            resume: Boolean = true,
            limit: Long = 0
        ): DownloadConfig {
            return DownloadConfig(
                maxConcurrentDownloads = maxThreads,
                maxChunksPerFile = maxChunks,
                minFileSizeForChunking = minChunkSize,
                enableMultiThread = multiThread,
                enableResume = resume,
                speedLimit = limit
            )
        }
    }
}