package com.verse.explorer.core.launch

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class GameSessionTest {
    private class FakeBackend : GameRuntimeBackend<String> {
        var prepares = 0
        var starts = 0
        var stops = 0
        var releases = 0
        var prepareFailure: Throwable? = null
        var startAction: (() -> Unit)? = null
        override suspend fun prepare(request: LaunchRequest) { prepares++; prepareFailure?.let { throw it } }
        override suspend fun start(surface: String) { starts++; startAction?.invoke() }
        override fun stop() { stops++ }
        override fun release() { releases++ }
    }

    @Test fun duplicateStartCannotInitializeTheJvmTwice() = runBlocking {
        val backend = FakeBackend()
        val session = GameSession(backend, this)
        assertTrue(session.start(LaunchRequest("1.21.1", "/game")))
        assertFalse(session.start(LaunchRequest("1.21.1", "/game")))
        yield()
        assertEquals(SessionPhase.WAITING_FOR_SURFACE, session.state.value.phase)
        assertEquals(0, backend.starts)
        session.onSurfaceCreated("surface")
        yield()
        assertEquals(SessionPhase.RUNNING, session.state.value.phase)
        assertEquals(1, backend.starts)
        session.release()
    }

    @Test fun surfaceBeforeStartIsAcceptedAndDestroyStopsOnce() = runBlocking {
        val backend = FakeBackend()
        val session = GameSession(backend, this)
        session.onSurfaceCreated("surface")
        session.start(LaunchRequest("1.21.1", "/game"))
        yield()
        session.onSurfaceDestroyed()
        session.stop()
        session.release()
        session.release()
        assertEquals(1, backend.starts)
        assertEquals(1, backend.stops)
        assertEquals(1, backend.releases)
        assertEquals(SessionPhase.RELEASED, session.state.value.phase)
    }

    @Test fun prepareFailureNeverStartsJvmAndRemainsAnError() = runBlocking {
        val backend = FakeBackend().apply { prepareFailure = IllegalStateException("JRE missing") }
        val session = GameSession(backend, this)
        session.start(LaunchRequest("1.21.1", "/game"))
        yield()
        assertEquals(SessionPhase.FAILED, session.state.value.phase)
        assertEquals("JRE missing", session.state.value.failure?.message)
        assertEquals(0, backend.starts)
        session.release()
        assertEquals(1, backend.stops)
    }

    @Test fun cancellationWhileWaitingCannotStartAfterALateSurface() = runBlocking {
        val backend = FakeBackend()
        val session = GameSession(backend, this)
        session.start(LaunchRequest("1.21.1", "/game"))
        yield()
        session.stop()
        session.onSurfaceCreated("late")
        yield()
        assertEquals(0, backend.starts)
        assertFalse(session.onExit(0))
        session.release()
    }

    @Test fun synchronousExitDuringStartCannotReturnToRunning() = runBlocking {
        val backend = FakeBackend()
        val session = GameSession(backend, this)
        backend.startAction = { assertTrue(session.onExit(7)) }
        session.onSurfaceCreated("surface")
        session.start(LaunchRequest("1.21.1", "/game"))
        yield()
        assertEquals(SessionPhase.EXITED, session.state.value.phase)
        assertEquals(7, session.state.value.exitCode)
        assertFalse(session.onExit(7))
        assertFalse(session.start(LaunchRequest("1.21.1", "/game")))
        session.release()
    }

    @Test fun startFailureStopsBackendAndIgnoresLateExit() = runBlocking {
        val backend = FakeBackend().apply { startAction = { error("native initialization failed") } }
        val session = GameSession(backend, this)
        session.onSurfaceCreated("surface")
        session.start(LaunchRequest("1.21.1", "/game"))
        yield()
        assertEquals(SessionPhase.FAILED, session.state.value.phase)
        assertEquals(1, backend.stops)
        assertFalse(session.onExit(0))
        session.release()
        session.release()
        assertEquals(1, backend.releases)
    }

    @Test fun normalExitRemainsTerminalUntilExplicitRelease() = runBlocking {
        val backend = FakeBackend()
        val session = GameSession(backend, this)
        session.onSurfaceCreated("surface")
        session.start(LaunchRequest("1.21.1", "/game"))
        yield()
        assertTrue(session.onExit(0))
        session.onSurfaceCreated("late")
        assertFalse(session.onExit(6))
        assertEquals(0, session.state.value.exitCode)
        assertEquals(1, backend.starts)
        session.release()
    }
}
