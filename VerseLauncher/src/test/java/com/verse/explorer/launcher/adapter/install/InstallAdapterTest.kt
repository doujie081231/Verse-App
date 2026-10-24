package com.verse.explorer.launcher.adapter.install

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InstallAdapterTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun retryNeverRemovesExistingVanillaOrModpackDirectories() {
        val backend = FclModpackInstallBackend()
        val vanilla = File(temp.root, "versions/vanilla").apply { mkdirs() }
        val config = File(vanilla, "options.txt").apply { writeText("user-data") }
        val pack = File(temp.root, "versions/pack").apply { mkdirs() }
        File(pack, "modpack.json").writeText("{}")
        assertTrue(backend.requiresNewName(temp.root, "vanilla"))
        assertTrue(backend.requiresNewName(temp.root, "pack"))
        assertFalse(backend.requiresNewName(temp.root, "new"))
        assertEquals("user-data", config.readText())
        assertTrue(pack.exists())
    }

    @Test fun assetCandidatesKeepOfficialBeforeConfiguredMirror() {
        val urls = VerseDownloadProvider("https://mirror.example").getAssetObjectCandidates("ab/hash").map { it.toString() }
        assertEquals(listOf("https://resources.download.minecraft.net/ab/hash", "https://mirror.example/assets/ab/hash"), urls)
    }
}
