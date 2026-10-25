package com.verse.explorer.core.install

import com.verse.explorer.core.install.modpack.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.charset.Charset

class ModpackInstallerTest {
    private class Operation : ModpackInstallOperation {
        val finish = CompletableDeferred<Unit>()
        var cancellations = 0
        override suspend fun install(gameDir: File, zipFile: File, name: String?, onEvent: (ModpackTaskEvent) -> Unit): Result<Unit> {
            finish.await()
            return Result.success(Unit)
        }
        override fun cancel() { cancellations++ }
    }
    private class Backend : ModpackInstallBackend {
        val operations = mutableListOf<Operation>()
        override fun readModpack(zipFile: File, charset: Charset) = ModpackInfo("test")
        override fun newOperation() = Operation().also { operations += it }
        override fun requiresNewName(gameDir: File, name: String) = false
    }
    @Test fun pagesHaveIndependentCancellationAndRetryWaitsForOldWriter() = runBlocking {
        val backend = Backend()
        val first = ModpackInstaller(backend)
        val second = ModpackInstaller(backend)
        val a = async(start = CoroutineStart.UNDISPATCHED) { first.installModpack(File("a"), File("a.zip")) {} }
        val b = async(start = CoroutineStart.UNDISPATCHED) { second.installModpack(File("b"), File("b.zip")) {} }
        first.cancel()
        assertEquals(1, backend.operations[0].cancellations)
        assertEquals(0, backend.operations[1].cancellations)
        assertTrue(first.installModpack(File("a"), File("a.zip")) {}.isFailure)
        backend.operations[0].finish.complete(Unit)
        a.await()
        val retry = async(start = CoroutineStart.UNDISPATCHED) { first.installModpack(File("a"), File("a.zip")) {} }
        first.cancel()
        assertEquals(1, backend.operations.last().cancellations)
        backend.operations[1].finish.complete(Unit)
        backend.operations.last().finish.complete(Unit)
        b.await(); retry.await()
        Unit
    }
}
