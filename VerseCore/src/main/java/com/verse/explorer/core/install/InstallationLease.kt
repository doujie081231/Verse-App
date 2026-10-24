package com.verse.explorer.core.install

import java.io.Closeable
import java.io.File

/** 同一进程中所有安装器共用目录占用表，避免不同入口同时写入同名版本。 */
class InstallationLease private constructor(private val paths: Set<String>) : Closeable {
    private var closed = false
    override fun close() = synchronized(occupied) {
        if (!closed) { occupied.removeAll(paths); closed = true }
    }

    companion object {
        private val occupied = mutableSetOf<String>()
        fun acquire(directories: Collection<File>): InstallationLease? {
            val paths = directories.map { it.canonicalPath }.toSet()
            return synchronized(occupied) {
                if (paths.any { it in occupied }) null
                else InstallationLease(paths).also { occupied.addAll(paths) }
            }
        }
    }
}
