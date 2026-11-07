package com.verse.explorer.core.launch

/** 游戏进程内一次启动的生命周期。停止后，迟到的 Surface 不能再启动 JVM。 */
class GameSessionLifecycle {
    enum class Stop { ALREADY_STOPPED, BEFORE_JVM, AFTER_JVM }

    private var launchClaimed = false
    private var stopped = false
    private var released = false
    private var jvmStarted = false

    @Synchronized
    fun beginLaunch(): Boolean {
        if (launchClaimed || stopped || released) return false
        launchClaimed = true
        return true
    }

    @Synchronized
    fun startJvm(start: () -> Unit): Boolean {
        if (!launchClaimed || stopped || released || jvmStarted) return false
        jvmStarted = true
        start()
        return true
    }

    @Synchronized
    fun stop(): Stop {
        if (stopped) return Stop.ALREADY_STOPPED
        stopped = true
        return if (jvmStarted) Stop.AFTER_JVM else Stop.BEFORE_JVM
    }

    @Synchronized
    fun beginRelease(): Boolean {
        if (released) return false
        released = true
        return true
    }

    @Synchronized
    fun hasStartedJvm(): Boolean = jvmStarted
}
