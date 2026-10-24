package com.verse.explorer.core.install

import com.verse.explorer.core.version.MinecraftVersion
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InstallationOwnershipTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun loaderSharesBaseWhileVanillaUsesNamedDirectory() {
        assertEquals(InstallationPlan("custom", "1.21.1"), InstallationPlan.create("1.21.1", "custom", "fabric", "0.16"))
        assertEquals(InstallationPlan("custom", "custom"), InstallationPlan.create("1.21.1", "custom", null, null))
        assertEquals("1.21.1", InstallationPlan.create("1.21.1", "", null, null).installId)
    }

    @Test fun differentEntrypointsCannotWriteTheSameVersionAndReleaseIsIdempotent() {
        val first = InstallationPaths(temp.newFolder("a")).version("same")
        val second = InstallationPaths(temp.newFolder("b")).version("same")
        val lease = InstallationLease.acquire(listOf(first))!!
        assertNull(InstallationLease.acquire(listOf(first)))
        InstallationLease.acquire(listOf(second))!!.close()
        lease.close(); lease.close()
        InstallationLease.acquire(listOf(first))!!.close()
    }

    @Test fun cleanupPreservesExistingBaseAndOtherTaskDirectories() {
        val paths = InstallationPaths(temp.newFolder("first"))
        val other = InstallationPaths(temp.newFolder("other"))
        paths.version("1.21.1").mkdirs()
        val cleanup = InstallationCleanup(paths, InstallationPlan("custom", "1.21.1"))
        paths.version("custom").mkdirs()
        other.version("custom").mkdirs()
        cleanup.afterFailure()
        assertTrue(paths.version("1.21.1").exists())
        assertFalse(paths.version("custom").exists())
        assertTrue(other.version("custom").exists())
    }

    @Test(expected = IllegalArgumentException::class) fun versionCannotEscapeTaskDirectory() {
        InstallationPaths(temp.root).version("../outside")
    }

    @Test fun retryProgressDoesNotDoubleCountBytesAndTasksAreIndependent() {
        var now = 0L
        val first = DownloadAggregator { now }
        val second = DownloadAggregator { now }
        first.report("file", 100)
        first.report("file", 20)
        now = 1000
        first.report("file", 150)
        second.report("file", 10)
        assertEquals(150L, first.downloaded)
        assertEquals(150L, first.speed)
        assertEquals(10L, second.downloaded)
    }

    @Test fun cancellationWaitsForWriterBeforeCleanupAndNextStart() = runBlocking {
        val paths = InstallationPaths(temp.newFolder("task"))
        val entered = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = VersionInstallCoordinator(scope, { InstallationSnapshot(paths, "official") }) {
            object : VersionInstallOperation {
                override fun cancel() {}
                override suspend fun installVersion(version: MinecraftVersion, installName: String, loaderType: String?, loaderVersion: String?, progressCallback: (InstallProgress) -> Unit): Boolean {
                    paths.version(installName).mkdirs()
                    entered.complete(Unit)
                    try { awaitCancellation() } finally { withContext(NonCancellable) { finished.await() } }
                }
            }
        }
        assertTrue(coordinator.start(MinecraftVersion(id = "1.21.1"), "custom", null, null))
        entered.await()
        coordinator.cancel()
        assertTrue(paths.version("custom").exists())
        assertFalse(coordinator.start(MinecraftVersion(id = "1.21.1"), "custom", null, null))
        finished.complete(Unit)
        withTimeout(5000) { while (coordinator.downloading.value.isNotEmpty()) yield() }
        assertFalse(paths.version("custom").exists())
        scope.cancel()
    }
}
