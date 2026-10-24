package com.verse.explorer.core.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 存档管理核心逻辑测试。
 *
 * 重点覆盖三类会直接造成**数据丢失**的路径：
 * 备份名解析（世界名含下划线）、导入重名（静默覆盖）、损坏 zip（解出空目录）。
 */
class WorldManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun manager(): WorldManager {
        val saves = File(tmp.root, "saves")
        val backups = File(tmp.root, "backups")
        return WorldManager(saves, backups)
    }

    private fun makeWorld(manager: WorldManager, name: String, content: String = "level"): File {
        val dir = File(tmp.root, "saves/$name")
        dir.mkdirs()
        File(dir, "level.dat").writeText(content)
        return dir
    }

    @Test
    fun getWorlds_ignoresFoldersWithoutLevelDat_andSortsByLastPlayed() {
        val manager = manager()
        val older = makeWorld(manager, "Older")
        File(tmp.root, "saves/NotAWorld").mkdirs()

        Thread.sleep(10)
        val newer = makeWorld(manager, "Newer")
        older.listFiles()?.forEach { it.setLastModified(1_600_000_000_000L) }
        newer.listFiles()?.forEach { it.setLastModified(1_700_000_000_000L) }

        val worlds = manager.getWorlds()

        assertEquals(listOf("Newer", "Older"), worlds.map { it.name })
    }

    @Test
    fun backupIdWithUnderscoreWorldName_parsedByTrailingTimestamp() {
        // 世界名本身含下划线：不能按最后一个下划线切分
        assertEquals("New_World", WorldNames.worldNameOfBackup("New_World_1730000000000"))
        assertEquals("我的_世界", WorldNames.worldNameOfBackup("我的_世界_1730000000000"))
    }

    @Test
    fun createBackup_listsWithCorrectWorldName() {
        val manager = manager()
        makeWorld(manager, "New_World")

        val backup = manager.createBackup("New_World")
        assertNotNull(backup)

        val backups = manager.getBackups()
        assertEquals(1, backups.size)
        assertEquals("New_World", backups[0].worldName)
        assertTrue(backups[0].size > 0)
    }

    @Test
    fun restoreBackup_bringsBackDeletedFile() {
        val manager = manager()
        val worldDir = makeWorld(manager, "WorldA", "v1")
        val backup = manager.createBackup("WorldA")!!

        File(worldDir, "level.dat").writeText("v2-broken")

        assertTrue(manager.restoreBackup(backup.id))
        assertEquals("v1", File(worldDir, "level.dat").readText())
    }

    @Test
    fun importWorld_whenNameExists_createsUniqueNameAndKeepsOriginal() {
        val manager = manager()
        val original = makeWorld(manager, "Shared", "original")
        val source = File(tmp.root, "Shared.zip")
        assertTrue(manager.exportWorld("Shared", source))

        val imported = manager.importWorld(source)

        // 原存档不被覆盖，新存档自动改名为 Shared (2)
        assertEquals("original", File(original, "level.dat").readText())
        assertEquals("Shared (2)", imported)
        assertEquals("original", File(tmp.root, "saves/Shared (2)/level.dat").readText())
    }

    @Test
    fun importWorld_overwriteTrue_replacesContent() {
        val manager = manager()
        makeWorld(manager, "Shared", "original")
        val other = makeWorld(manager, "Temp", "replacement")
        val source = File(tmp.root, "Shared.zip")
        assertTrue(manager.exportWorld("Temp", source))
        other.deleteRecursively()

        assertEquals("Shared", manager.importWorld(source, overwrite = true))
        assertEquals("replacement", File(tmp.root, "saves/Shared/level.dat").readText())
    }

    @Test
    fun importWorld_zipWithoutLevelDat_isRejected() {
        val manager = manager()
        val badZip = File(tmp.root, "Broken.zip")
        ZipOutputStream(badZip.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("readme.txt"))
            zip.write("hello".toByteArray())
            zip.closeEntry()
        }

        assertNull(manager.importWorld(badZip))
        // 解压失败的目录必须清理干净，不能留半成品
        assertFalse(File(tmp.root, "saves/Broken").exists())
    }

    @Test
    fun renameWorld_conflictReturnsFalse() {
        val manager = manager()
        makeWorld(manager, "A")
        makeWorld(manager, "B")

        assertFalse(manager.renameWorld("A", "B"))
        assertTrue(manager.renameWorld("A", "C"))
        assertTrue(File(tmp.root, "saves/C/level.dat").exists())
    }

    @Test
    fun deleteWorld_removesDirectory() {
        val manager = manager()
        makeWorld(manager, "Gone")

        assertTrue(manager.deleteWorld("Gone"))
        assertFalse(File(tmp.root, "saves/Gone").exists())
    }

    @Test
    fun deleteBackup_removesOnlyThatBackup() {
        val manager = manager()
        makeWorld(manager, "W")
        val first = manager.createBackup("W")!!
        val second = manager.createBackup("W")!!

        assertTrue(manager.deleteBackup(first.id))
        assertEquals(listOf(second.id), manager.getBackups().map { it.id })
    }
}
