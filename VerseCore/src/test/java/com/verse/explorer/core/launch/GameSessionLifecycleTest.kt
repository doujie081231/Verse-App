package com.verse.explorer.core.launch

import org.junit.Assert.*
import org.junit.Test

class GameSessionLifecycleTest {
    @Test fun stopBeforeSurfacePreventsLateJvmStart() {
        val session = GameSessionLifecycle()
        assertTrue(session.beginLaunch())
        assertEquals(GameSessionLifecycle.Stop.BEFORE_JVM, session.stop())
        assertFalse(session.startJvm { error("迟到的 Surface 不能启动 JVM") })
        assertFalse(session.beginLaunch())
        assertFalse(session.hasStartedJvm())
    }

    @Test fun failedNativeStartStillOwnsCleanupExactlyOnce() {
        val session = GameSessionLifecycle()
        assertTrue(session.beginLaunch())
        assertFalse(session.beginLaunch())
        assertTrue(runCatching { session.startJvm { error("native startup failure") } }.isFailure)
        assertTrue(session.hasStartedJvm())
        assertFalse(session.startJvm { error("不能重复启动") })
        assertEquals(GameSessionLifecycle.Stop.AFTER_JVM, session.stop())
        assertEquals(GameSessionLifecycle.Stop.ALREADY_STOPPED, session.stop())
        assertTrue(session.beginRelease())
        assertFalse(session.beginRelease())
    }
}
