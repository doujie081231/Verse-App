package com.verse.explorer.app.install

import com.verse.explorer.core.install.modpack.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext

class ModpackInstallControllerTest {
    @get:Rule val temp = TemporaryFolder()
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }
    private class Backend : ModpackInstallBackend {
        var runs = 0
        override fun readModpack(zipFile: File, charset: Charset) = ModpackInfo("pack")
        override fun requiresNewName(gameDir: File, name: String) = false
        override fun newOperation() = object : ModpackInstallOperation {
            override suspend fun install(gameDir: File, zipFile: File, name: String?, onEvent: (ModpackTaskEvent) -> Unit): Result<Unit> {
                runs++
                return Result.success(Unit)
            }
            override fun cancel() {}
        }
    }

    @Test fun cancelBeforeDispatchReleasesTaskAndAllowsRetry() {
        val dispatcher = QueuedDispatcher()
        val appScope = CoroutineScope(SupervisorJob() + dispatcher)
        val backend = Backend()
        val controller = ModpackInstallController(appScope, ModpackInstaller(backend))
        val first = temp.newFile("first.zip")
        assertTrue(controller.start(temp.root, first, "first", "first"))
        controller.cancel()
        dispatcher.drain()
        assertFalse(first.exists())
        assertEquals(0, backend.runs)
        assertEquals(ModpackInstallPhase.CANCELLED, controller.state.value.phase)
        assertTrue(controller.start(temp.root, temp.newFile("retry.zip"), "retry", "retry"))
        dispatcher.drain()
        assertEquals(1, backend.runs)
        assertEquals(ModpackInstallPhase.DONE, controller.state.value.phase)
        appScope.cancel()
    }

    @Test fun progressCountsAreMonotonicAndLateEventsCannotChangeTerminalState() {
        val start = ModpackInstallUiState(visible = true).withFixedStageRows()
        val declared = start.applyTaskEvent(ModpackTaskEvent("stage:fcl.install.assets", "assets", kind = ModpackTaskKind.RUNNING, countTotal = 10))
        val first = declared.applyTaskEvent(ModpackTaskEvent("stage:fcl.install.assets", "assets", kind = ModpackTaskKind.RUNNING, countDone = 6))
        val later = first.applyTaskEvent(ModpackTaskEvent("stage:fcl.install.assets", "assets", kind = ModpackTaskKind.RUNNING, countDone = 2))
        assertTrue(later.overall >= first.overall)
        val done = later.copy(phase = ModpackInstallPhase.DONE)
        assertEquals(done, done.applyTaskEvent(ModpackTaskEvent("stage:fcl.install.assets", "assets", kind = ModpackTaskKind.FAILED)))
    }
}
