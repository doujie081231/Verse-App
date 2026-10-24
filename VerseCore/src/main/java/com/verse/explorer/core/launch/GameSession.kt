package com.verse.explorer.core.launch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SessionPhase { IDLE, PREPARING, WAITING_FOR_SURFACE, STARTING, RUNNING, STOPPING, EXITED, FAILED, RELEASED }

data class GameSessionState(
    val phase: SessionPhase = SessionPhase.IDLE,
    val exitCode: Int? = null,
    val failure: Throwable? = null
)

/** 平台实现持有原生资源；S 可以是 Android Surface，也可以是测试中的普通对象。 */
interface GameRuntimeBackend<S : Any> {
    suspend fun prepare(request: LaunchRequest)
    suspend fun start(surface: S)
    fun stop()
    fun release()
}

interface GameRuntimeCallbacks {
    fun onCursorModeChange(mode: Int)
    fun onHitResultTypeChange(type: Int)
    fun onLog(log: String)
    fun onExit(code: Int)
}

/** 单次 JVM 会话；所有状态转换串行化，停止与释放各自最多执行一次。 */
class GameSession<S : Any>(
    private val backend: GameRuntimeBackend<S>,
    parentScope: CoroutineScope
) {
    private val lock = Any()
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val surface = CompletableDeferred<S>()
    private val mutableState = MutableStateFlow(GameSessionState())
    val state: StateFlow<GameSessionState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var stopped = false
    private var released = false

    fun start(request: LaunchRequest): Boolean {
        val launchJob = synchronized(lock) {
            if (released || mutableState.value.phase != SessionPhase.IDLE) return false
            mutableState.value = GameSessionState(SessionPhase.PREPARING)
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    backend.prepare(request)
                    if (!transition(SessionPhase.WAITING_FOR_SURFACE)) return@launch
                    val target = surface.await()
                    if (!transition(SessionPhase.STARTING)) return@launch
                    backend.start(target)
                    transition(SessionPhase.RUNNING)
                } catch (cancelled: CancellationException) {
                    transition(SessionPhase.EXITED)
                    throw cancelled
                } catch (failure: Throwable) {
                    synchronized(lock) {
                        if (!released && !isTerminal()) mutableState.value = GameSessionState(SessionPhase.FAILED, failure = failure)
                    }
                    stopBackendOnce()
                }
            }.also { job = it }
        }
        launchJob.start()
        return true
    }

    fun onSurfaceCreated(target: S) = synchronized(lock) {
        if (!released && !stopped && !isTerminal()) surface.complete(target)
        Unit
    }

    fun onSurfaceDestroyed() = stop()

    /** 返回 false 表示重复或迟到的退出通知，调用方不应再次展示退出界面。 */
    fun onExit(code: Int): Boolean = synchronized(lock) {
        if (released || stopped || mutableState.value.phase !in setOf(SessionPhase.STARTING, SessionPhase.RUNNING)) return false
        mutableState.value = GameSessionState(SessionPhase.EXITED, exitCode = code)
        true
    }

    fun stop() {
        synchronized(lock) {
            if (released || stopped) return
            if (!isTerminal()) mutableState.value = GameSessionState(SessionPhase.STOPPING)
            job?.cancel()
        }
        stopBackendOnce()
        synchronized(lock) {
            if (!released && mutableState.value.phase == SessionPhase.STOPPING) mutableState.value = GameSessionState(SessionPhase.EXITED)
        }
    }

    fun release() {
        synchronized(lock) {
            if (released) return
            released = true
            job?.cancel()
            mutableState.value = GameSessionState(SessionPhase.RELEASED)
        }
        try {
            stopBackendOnce()
        } finally {
            try { backend.release() } finally { scope.cancel() }
        }
    }

    private fun stopBackendOnce() {
        synchronized(lock) {
            if (stopped) return
            stopped = true
        }
        backend.stop()
    }

    private fun transition(phase: SessionPhase): Boolean = synchronized(lock) {
        if (released || stopped || isTerminal()) return false
        mutableState.value = GameSessionState(phase)
        true
    }

    private fun isTerminal(): Boolean = mutableState.value.phase in setOf(SessionPhase.EXITED, SessionPhase.FAILED, SessionPhase.RELEASED)
}
