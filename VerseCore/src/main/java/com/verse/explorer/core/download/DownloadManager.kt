package com.verse.explorer.core.download

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.PriorityBlockingQueue

data class DownloadTask(
    val id: String,
    val url: String,
    val targetFile: File,
    val sha1: String? = null,
    val size: Long = 0,
    val priority: Int = 0,
    val supportResume: Boolean = true
) : Comparable<DownloadTask> {
    override fun compareTo(other: DownloadTask): Int = other.priority.compareTo(priority)
}

data class DownloadProgress(
    val taskId: String,
    val fileName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speed: Long,
    val progress: Float,
    val status: DownloadStatus,
    val error: String? = null
)

enum class DownloadStatus {
    PENDING,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
    VERIFYING
}

data class DownloadQueueState(
    val pendingTasks: Int = 0,
    val activeTasks: Int = 0,
    val completedTasks: Int = 0,
    val failedTasks: Int = 0,
    val totalSpeed: Long = 0
)

class DownloadManager(
    private val maxConcurrentDownloads: Int = 32,
    private val maxChunksPerFile: Int = 8,
    private val minFileSizeForChunking: Long = 5 * 1024 * 1024
) {
    
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .connectionPool(SmartDownloadClient.sharedConnectionPool)
        .dns(SmartDownloadClient.ipv4Only())
        .retryOnConnectionFailure(true)
        .build()
    
    private val activeDownloads = ConcurrentHashMap<String, Boolean>()
    private val pausedDownloads = ConcurrentHashMap<String, Boolean>()
    private val downloadProgress = ConcurrentHashMap<String, DownloadProgress>()
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val taskQueue = PriorityBlockingQueue<DownloadTask>()
    private val downloadChunks = ConcurrentHashMap<String, ConcurrentHashMap<Int, Long>>()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _progressFlow = MutableSharedFlow<DownloadProgress>()
    val progressFlow: SharedFlow<DownloadProgress> = _progressFlow.asSharedFlow()
    
    private val _queueState = MutableStateFlow(DownloadQueueState())
    val queueState: StateFlow<DownloadQueueState> = _queueState.asStateFlow()
    
    private var queueProcessorJob: Job? = null
    
    init {
        startQueueProcessor()
    }
    
    private fun startQueueProcessor() {
        queueProcessorJob = scope.launch {
            while (isActive) {
                val activeCount = activeDownloads.size
                if (activeCount < maxConcurrentDownloads) {
                    val task = taskQueue.poll()
                    if (task != null && !activeDownloads.containsKey(task.id)) {
                        startDownload(task)
                    }
                }
                delay(100)
            }
        }
    }
    
    fun addToQueue(task: DownloadTask) {
        taskQueue.offer(task)
        updateQueueState()
    }
    
    fun addMultipleToQueue(tasks: List<DownloadTask>) {
        tasks.forEach { task ->
            taskQueue.offer(task)
        }
        updateQueueState()
    }
    
    fun removeFromQueue(taskId: String) {
        taskQueue.removeIf { it.id == taskId }
        updateQueueState()
    }
    
    fun clearQueue() {
        taskQueue.clear()
        updateQueueState()
    }
    
    fun getQueue(): List<DownloadTask> = taskQueue.toList()
    
    private fun startDownload(task: DownloadTask) {
        val job = scope.launch {
            download(task).collect { progress ->
                _progressFlow.emit(progress)
                downloadProgress[task.id] = progress
                updateQueueState()
            }
        }
        downloadJobs[task.id] = job
    }
    
    fun download(task: DownloadTask): Flow<DownloadProgress> = channelFlow {
        val initialProgress = DownloadProgress(
            taskId = task.id,
            fileName = task.targetFile.name,
            bytesDownloaded = 0,
            totalBytes = task.size,
            speed = 0,
            progress = 0f,
            status = DownloadStatus.PENDING
        )
        send(initialProgress)
        
        activeDownloads[task.id] = true
        pausedDownloads[task.id] = false
        
        try {
            task.targetFile.parentFile?.mkdirs()
            
            val serverFileSize = getServerFileSize(task.url)
            val totalSize = if (task.size > 0) task.size else serverFileSize
            
            if (serverFileSize > 0 && serverFileSize >= minFileSizeForChunking && task.supportResume) {
                downloadWithChunks(task, serverFileSize).collect { progress ->
                    send(progress)
                }
            } else {
                downloadSingleThread(task).collect { progress ->
                    send(progress)
                }
            }
            
        } catch (e: Exception) {
            val errorProgress = DownloadProgress(
                taskId = task.id,
                fileName = task.targetFile.name,
                bytesDownloaded = 0,
                totalBytes = task.size,
                speed = 0,
                progress = 0f,
                status = DownloadStatus.FAILED,
                error = e.message
            )
            downloadProgress[task.id] = errorProgress
            send(errorProgress)
        } finally {
            activeDownloads.remove(task.id)
            pausedDownloads.remove(task.id)
            downloadChunks.remove(task.id)
            updateQueueState()
        }
    }.flowOn(Dispatchers.IO)
    
    private fun getServerFileSize(url: String): Long {
        return try {
            val request = Request.Builder().url(url)
                .header("User-Agent", "VerseExplorerX/1.0")
                .build()
            val response = httpClient.newCall(request).execute()
            val size = response.header("Content-Length")?.toLongOrNull() ?: 0L
            response.close()
            size
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun checkResumeSupport(url: String): Boolean {
        return try {
            val request = Request.Builder().url(url)
                .header("User-Agent", "VerseExplorerX/1.0")
                .header("Range", "bytes=0-0")
                .build()
            val response = httpClient.newCall(request).execute()
            val support = response.code == 206 || 
                          response.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true ||
                          response.header("Accept-Ranges") == "bytes"
            response.close()
            support
        } catch (e: Exception) {
            false
        }
    }
    
    private fun downloadWithChunks(task: DownloadTask, totalSize: Long): Flow<DownloadProgress> = channelFlow<DownloadProgress> {
        val supportsResume = checkResumeSupport(task.url)
        val chunks = mutableListOf<Pair<Long, Long>>()
        val chunkSize = totalSize / maxChunksPerFile
        
        for (i in 0 until maxChunksPerFile) {
            val start = i * chunkSize
            val end = if (i == maxChunksPerFile - 1) totalSize - 1 else (i + 1) * chunkSize - 1
            chunks.add(Pair(start, end))
        }
        
        val chunkProgress = ConcurrentHashMap<Int, Long>()
        chunks.forEachIndexed { index, _ -> chunkProgress[index] = 0L }
        downloadChunks[task.id] = chunkProgress
        
        val tempDir = File(task.targetFile.parentFile, ".download_chunks")
        tempDir.mkdirs()
        val chunkFiles = chunks.mapIndexed { index, _ ->
            File(tempDir, "${task.targetFile.name}.chunk_$index")
        }
        
        chunkFiles.forEach { it.delete() }
        
        val completedChunks = ConcurrentHashMap<Int, Boolean>()
        
        val downloadJobs = chunks.mapIndexed { index, pair ->
            val (start, end) = pair
            scope.launch {
                try {
                    downloadChunk(task.url, chunkFiles[index], start, end, task.id, index) { 
                        chunkProgress[index] = it 
                    }
                    completedChunks[index] = true
                } catch (e: Exception) {
                    completedChunks[index] = false
                }
            }
        }
        
        var lastReportTime = System.currentTimeMillis()
        var lastTotalBytes = 0L
        
        while (completedChunks.size < chunks.size) {
            if (activeDownloads[task.id] == false) {
                downloadJobs.forEach { it.cancel() }
                send(DownloadProgress(
                    taskId = task.id,
                    fileName = task.targetFile.name,
                    bytesDownloaded = 0,
                    totalBytes = totalSize,
                    speed = 0,
                    progress = 0f,
                    status = DownloadStatus.CANCELLED
                ))
                chunkFiles.forEach { it.delete() }
                return@channelFlow
            }
            
            while (pausedDownloads[task.id] == true) {
                delay(100)
                send(DownloadProgress(
                    taskId = task.id,
                    fileName = task.targetFile.name,
                    bytesDownloaded = chunkProgress.values.sum(),
                    totalBytes = totalSize,
                    speed = 0,
                    progress = if (totalSize > 0) (chunkProgress.values.sum().toFloat() / totalSize * 100) else 0f,
                    status = DownloadStatus.PAUSED
                ))
            }
            
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastReportTime >= 300) {
                val totalDownloaded = chunkProgress.values.sum()
                val speed = if (currentTime - lastReportTime > 0) {
                    ((totalDownloaded - lastTotalBytes) * 1000 / (currentTime - lastReportTime))
                } else 0L
                
                send(DownloadProgress(
                    taskId = task.id,
                    fileName = task.targetFile.name,
                    bytesDownloaded = totalDownloaded,
                    totalBytes = totalSize,
                    speed = speed,
                    progress = if (totalSize > 0) (totalDownloaded.toFloat() / totalSize * 100) else 0f,
                    status = DownloadStatus.DOWNLOADING
                ))
                
                lastReportTime = currentTime
                lastTotalBytes = totalDownloaded
            }
            
            delay(100)
        }
        
        downloadJobs.forEach { it.cancel() }
        
        val allCompleted = completedChunks.values.all { it }
        if (!allCompleted) {
            chunkFiles.forEach { it.delete() }
            send(DownloadProgress(
                taskId = task.id,
                fileName = task.targetFile.name,
                bytesDownloaded = 0,
                totalBytes = totalSize,
                speed = 0,
                progress = 0f,
                status = DownloadStatus.FAILED,
                error = "Some chunks failed to download"
            ))
            return@channelFlow
        }
        
        mergeChunks(chunkFiles, task.targetFile)
        chunkFiles.forEach { it.delete() }
        tempDir.delete()
        
        if (task.sha1 != null) {
            send(DownloadProgress(
                taskId = task.id,
                fileName = task.targetFile.name,
                bytesDownloaded = totalSize,
                totalBytes = totalSize,
                speed = 0,
                progress = 100f,
                status = DownloadStatus.VERIFYING
            ))
            
            val actualSha1 = calculateSHA1(task.targetFile)
            if (!actualSha1.equals(task.sha1, ignoreCase = true)) {
                task.targetFile.delete()
                send(DownloadProgress(
                    taskId = task.id,
                    fileName = task.targetFile.name,
                    bytesDownloaded = 0,
                    totalBytes = totalSize,
                    speed = 0,
                    progress = 0f,
                    status = DownloadStatus.FAILED,
                    error = "SHA1 verification failed"
                ))
                return@channelFlow
            }
        }
        
        send(DownloadProgress(
            taskId = task.id,
            fileName = task.targetFile.name,
            bytesDownloaded = totalSize,
            totalBytes = totalSize,
            speed = 0,
            progress = 100f,
            status = DownloadStatus.COMPLETED
        ))
    }.flowOn(Dispatchers.IO)
    
    private suspend fun downloadChunk(
        url: String,
        chunkFile: File,
        start: Long,
        end: Long,
        taskId: String,
        chunkIndex: Int,
        onProgress: (Long) -> Unit
    ) {
        var downloadedBytes = 0L
        
        if (chunkFile.exists() && chunkFile.length() > 0) {
            downloadedBytes = chunkFile.length()
            if (downloadedBytes >= (end - start + 1)) {
                onProgress(downloadedBytes)
                return
            }
        }
        
        val requestBuilder = Request.Builder().url(url)
            .header("User-Agent", "VerseExplorerX/1.0")
            .header("Range", "bytes=${start + downloadedBytes}-$end")
        
        val request = requestBuilder.build()
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful && response.code != 206) {
            throw Exception("HTTP ${response.code} for chunk $chunkIndex")
        }
        
        val body = response.body ?: throw Exception("Empty response body for chunk $chunkIndex")
        
        body.byteStream().use { input ->
            val output = FileOutputStream(chunkFile, downloadedBytes > 0)
            output.use { out ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (activeDownloads[taskId] == false) {
                        output.flush()
                        throw Exception("Download cancelled")
                    }
                    out.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    onProgress(downloadedBytes)
                }
            }
        }
        
        response.close()
    }
    
    private fun mergeChunks(chunkFiles: List<File>, targetFile: File) {
        RandomAccessFile(targetFile, "rw").use { raf ->
            chunkFiles.forEach { chunkFile ->
                if (chunkFile.exists()) {
                    chunkFile.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            raf.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }
        }
    }
    
    private fun downloadSingleThread(task: DownloadTask): Flow<DownloadProgress> = channelFlow {
        var downloadedBytes = 0L
        val supportsResume = task.supportResume && task.targetFile.exists() && task.targetFile.length() > 0
        
        val requestBuilder = Request.Builder().url(task.url)
            .header("User-Agent", "VerseExplorerX/1.0")
        
        if (supportsResume) {
            downloadedBytes = task.targetFile.length()
            requestBuilder.header("Range", "bytes=$downloadedBytes-")
        }
        
        val request = requestBuilder.build()
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful && response.code != 206) {
            send(DownloadProgress(
                taskId = task.id,
                fileName = task.targetFile.name,
                bytesDownloaded = downloadedBytes,
                totalBytes = task.size,
                speed = 0,
                progress = 0f,
                status = DownloadStatus.FAILED,
                error = "HTTP ${response.code}"
            ))
            return@channelFlow
        }
        
        if (response.code == 206) {
            downloadedBytes = 0
            task.targetFile.delete()
        }
        
        val body = response.body ?: run {
            send(DownloadProgress(
                taskId = task.id,
                fileName = task.targetFile.name,
                bytesDownloaded = downloadedBytes,
                totalBytes = task.size,
                speed = 0,
                progress = 0f,
                status = DownloadStatus.FAILED,
                error = "Empty response body"
            ))
            return@channelFlow
        }
        
        val totalBytes = if (task.size > 0) task.size else body.contentLength()
        var lastTime = System.currentTimeMillis()
        var lastBytes = downloadedBytes
        var currentSpeed = 0L
        
        val mode = if (supportsResume && downloadedBytes > 0) "rw" else "rws"
        
        if (supportsResume && downloadedBytes > 0) {
            RandomAccessFile(task.targetFile, mode).use { raf ->
                raf.seek(downloadedBytes)
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (activeDownloads[task.id] == false) {
                            send(DownloadProgress(
                                taskId = task.id,
                                fileName = task.targetFile.name,
                                bytesDownloaded = downloadedBytes,
                                totalBytes = totalBytes,
                                speed = 0,
                                progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes * 100) else 0f,
                                status = DownloadStatus.CANCELLED
                            ))
                            return@channelFlow
                        }
                        
                        while (pausedDownloads[task.id] == true) {
                            delay(100)
                            send(DownloadProgress(
                                taskId = task.id,
                                fileName = task.targetFile.name,
                                bytesDownloaded = downloadedBytes,
                                totalBytes = totalBytes,
                                speed = 0,
                                progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes * 100) else 0f,
                                status = DownloadStatus.PAUSED
                            ))
                        }
                        
                        raf.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        val currentTime = System.currentTimeMillis()
                        val timeDiff = currentTime - lastTime
                        
                        if (timeDiff >= 300) {
                            currentSpeed = ((downloadedBytes - lastBytes) * 1000 / timeDiff)
                            lastTime = currentTime
                            lastBytes = downloadedBytes
                        }
                        
                        send(DownloadProgress(
                            taskId = task.id,
                            fileName = task.targetFile.name,
                            bytesDownloaded = downloadedBytes,
                            totalBytes = totalBytes,
                            speed = currentSpeed,
                            progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes * 100) else 0f,
                            status = DownloadStatus.DOWNLOADING
                        ))
                    }
                }
            }
        } else {
            FileOutputStream(task.targetFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (activeDownloads[task.id] == false) {
                            send(DownloadProgress(
                                taskId = task.id,
                                fileName = task.targetFile.name,
                                bytesDownloaded = downloadedBytes,
                                totalBytes = totalBytes,
                                speed = 0,
                                progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes * 100) else 0f,
                                status = DownloadStatus.CANCELLED
                            ))
                            return@channelFlow
                        }
                        
                        while (pausedDownloads[task.id] == true) {
                            delay(100)
                            send(DownloadProgress(
                                taskId = task.id,
                                fileName = task.targetFile.name,
                                bytesDownloaded = downloadedBytes,
                                totalBytes = totalBytes,
                                speed = 0,
                                progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes * 100) else 0f,
                                status = DownloadStatus.PAUSED
                            ))
                        }
                        
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        val currentTime = System.currentTimeMillis()
                        val timeDiff = currentTime - lastTime
                        
                        if (timeDiff >= 300) {
                            currentSpeed = ((downloadedBytes - lastBytes) * 1000 / timeDiff)
                            lastTime = currentTime
                            lastBytes = downloadedBytes
                        }
                        
                        send(DownloadProgress(
                            taskId = task.id,
                            fileName = task.targetFile.name,
                            bytesDownloaded = downloadedBytes,
                            totalBytes = totalBytes,
                            speed = currentSpeed,
                            progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes * 100) else 0f,
                            status = DownloadStatus.DOWNLOADING
                        ))
                    }
                }
            }
        }
        
        response.close()
        
        if (task.sha1 != null) {
            send(DownloadProgress(
                taskId = task.id,
                fileName = task.targetFile.name,
                bytesDownloaded = downloadedBytes,
                totalBytes = totalBytes,
                speed = 0,
                progress = 100f,
                status = DownloadStatus.VERIFYING
            ))
            
            val actualSha1 = calculateSHA1(task.targetFile)
            if (!actualSha1.equals(task.sha1, ignoreCase = true)) {
                task.targetFile.delete()
                send(DownloadProgress(
                    taskId = task.id,
                    fileName = task.targetFile.name,
                    bytesDownloaded = 0,
                    totalBytes = totalBytes,
                    speed = 0,
                    progress = 0f,
                    status = DownloadStatus.FAILED,
                    error = "SHA1 verification failed"
                ))
                return@channelFlow
            }
        }
        
        send(DownloadProgress(
            taskId = task.id,
            fileName = task.targetFile.name,
            bytesDownloaded = downloadedBytes,
            totalBytes = totalBytes,
            speed = 0,
            progress = 100f,
            status = DownloadStatus.COMPLETED
        ))
    }.flowOn(Dispatchers.IO)
    
    fun pause(taskId: String) {
        pausedDownloads[taskId] = true
    }
    
    fun resume(taskId: String) {
        pausedDownloads[taskId] = false
    }
    
    fun cancel(taskId: String) {
        activeDownloads[taskId] = false
        pausedDownloads.remove(taskId)
    }
    
    fun getProgress(taskId: String): DownloadProgress? {
        return downloadProgress[taskId]
    }
    
    fun getAllProgress(): Map<String, DownloadProgress> {
        return downloadProgress.toMap()
    }
    
    fun isDownloading(taskId: String): Boolean {
        return activeDownloads.containsKey(taskId)
    }
    
    private fun calculateSHA1(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    
    private fun updateQueueState() {
        val progressMap = downloadProgress.toMap()
        val totalSpeed = progressMap.values
            .filter { it.status == DownloadStatus.DOWNLOADING }
            .sumOf { it.speed }
        
        _queueState.value = DownloadQueueState(
            pendingTasks = taskQueue.size,
            activeTasks = activeDownloads.size,
            completedTasks = progressMap.values.count { it.status == DownloadStatus.COMPLETED },
            failedTasks = progressMap.values.count { it.status == DownloadStatus.FAILED },
            totalSpeed = totalSpeed
        )
    }
    
    fun pauseAll() {
        activeDownloads.keys.toList().forEach { taskId ->
            pausedDownloads[taskId] = true
        }
    }
    
    fun resumeAll() {
        pausedDownloads.keys.toList().forEach { taskId ->
            pausedDownloads[taskId] = false
        }
    }
    
    fun cancelAll() {
        activeDownloads.keys.toList().forEach { taskId ->
            cancel(taskId)
        }
        taskQueue.clear()
        updateQueueState()
    }
    
    fun retryFailed() {
        val failedTasks = downloadProgress.entries
            .filter { it.value.status == DownloadStatus.FAILED }
            .map { it.key }
        
        failedTasks.forEach { taskId ->
            downloadProgress.remove(taskId)
        }
    }
    
    fun getDownloadStats(): DownloadStats {
        val progressMap = downloadProgress.toMap()
        return DownloadStats(
            totalDownloads = progressMap.size,
            completed = progressMap.values.count { it.status == DownloadStatus.COMPLETED },
            failed = progressMap.values.count { it.status == DownloadStatus.FAILED },
            downloading = progressMap.values.count { it.status == DownloadStatus.DOWNLOADING },
            paused = progressMap.values.count { it.status == DownloadStatus.PAUSED },
            totalBytesDownloaded = progressMap.values
                .filter { it.status == DownloadStatus.COMPLETED }
                .sumOf { it.bytesDownloaded }
        )
    }
    
    fun shutdown() {
        queueProcessorJob?.cancel()
        downloadJobs.values.forEach { it.cancel() }
        activeDownloads.clear()
        pausedDownloads.clear()
        taskQueue.clear()
    }
}

data class DownloadStats(
    val totalDownloads: Int,
    val completed: Int,
    val failed: Int,
    val downloading: Int,
    val paused: Int,
    val totalBytesDownloaded: Long
)
