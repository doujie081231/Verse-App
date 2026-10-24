package com.verse.explorer.core.version

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 版本删除语义测试。
 *
 * 真机踩过的坑：直接 deleteRecursively() 失败被静默吞掉 →
 * 用户重下报「版本名存在」，换个名字重下「被删版本又回来了」。
 * 这里锁死三条关键行为：
 *  1. 删除先把目录改名为 `<id>_removed` 腾出版本名（后续删除失败也不占名）；
 *  2. 改名后即使 json/目录删不干净，也不再出现在版本列表、不再占用版本名；
 *  3. 上次删除残留的 `_removed` 目录不参与判重，且再次删除时会被先清掉。
 */
class VersionRemovalTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun manager() = VersionManager(tmp.root)

    private fun versionsDir() = File(tmp.root, "versions")

    /** 造一个带 json 的版本目录（getLocalVersions 判定「有效版本」的最低条件），带 jar 以免被当残缺目录过滤 */
    private fun makeVersion(id: String, hidden: Boolean = false): File {
        val dir = File(versionsDir(), id)
        dir.mkdirs()
        val hiddenField = if (hidden) ""","hidden":true""" else ""
        File(dir, "$id.json").writeText("""{"id":"$id","type":"release"$hiddenField}""")
        // getLocalVersions 要求有 jar（或继承到父 jar）才计入列表
        File(dir, "$id.jar").writeBytes(ByteArray(2048) { it.toByte() })
        return dir
    }

    @Test
    fun removeVersionFromDisk_deletesDirAndFreesName() {
        val dir = makeVersion("26.3-Fabric")
        val vm = manager()

        val ok = vm.removeVersionFromDisk("26.3-Fabric")

        assertTrue(ok)
        assertFalse(dir.exists())
        assertFalse(vm.versionIdConflicts("26.3-Fabric"))
    }

    @Test
    fun removeVersionFromDisk_renameFirst_freesNameEvenWhenDeleteFails() {
        // 用一个不可删除的子目录模拟「文件被游戏进程占用」：
        // 若实现是先 deleteRecursively 再改名（或直接删失败就返回），版本名会被永久占用。
        val dir = makeVersion("busy")
        val locked = File(dir, "locked")
        locked.mkdirs()
        val guard = File(locked, "guard.txt")
        guard.writeText("in-use")
        // Windows/部分文件系统上把目录设为不可写可让 deleteRecursively 失败；
        // 若运行环境允许删除，则跳过本用例（Linux CI 上目录删除不看只读位）。
        locked.setWritable(false)
        val canSimulateLock = !locked.delete()
        if (!canSimulateLock) {
            locked.setWritable(true)
            return // 环境无法模拟占用，由下面的用例覆盖腾名字语义
        }

        val vm = manager()
        val ok = vm.removeVersionFromDisk("busy")

        // 改名这一步是关键：无论后续是否删得干净，版本名必须腾出来
        assertFalse(vm.versionIdConflicts("busy"))
        assertTrue(ok)
        // 残留内容（若有）只允许落在 _removed 目录里
        assertFalse(File(versionsDir(), "busy").exists())

        locked.setWritable(true)
    }

    @Test
    fun removeVersionFromDisk_cleansPreviousRemovedLeftover() {
        makeVersion("dup")
        // 上次删除留下的残留目录，且带 json——不能让它挡住这次改名
        val leftover = File(versionsDir(), "dup_removed")
        leftover.mkdirs()
        File(leftover, "dup_removed.json").writeText("""{"id":"dup_removed"}""")

        val vm = manager()
        val ok = vm.removeVersionFromDisk("dup")

        assertTrue(ok)
        assertFalse(File(versionsDir(), "dup").exists())
        assertFalse(vm.versionIdConflicts("dup"))
    }

    @Test
    fun versionIdConflicts_ignoresRemovedLeftoverAndEmptyDirs() {
        // 删除流程留下的 `_removed` 残留目录（带 json）不占名
        val leftover = File(versionsDir(), "ghost_removed")
        leftover.mkdirs()
        File(leftover, "ghost_removed.json").writeText("""{"id":"ghost_removed"}""")
        File(leftover, "ghost_removed.jar").writeBytes(ByteArray(2048) { it.toByte() })

        // 中断安装留下的空目录（无 json）不占名，否则一次失败下载会让该名永远「重名」
        File(versionsDir(), "halfway").mkdirs()

        val vm = manager()
        assertFalse(vm.versionIdConflicts("ghost"))
        // 关键：残留目录自身的名字也不能被当成「已存在」，否则换名重下仍会撞车
        assertFalse(vm.versionIdConflicts("ghost_removed"))
        assertFalse(vm.versionIdConflicts("halfway"))

        // 真版本占名
        makeVersion("real")
        assertTrue(vm.versionIdConflicts("real"))
    }

    @Test
    fun getLocalVersions_hidesRemovedLeftover() {
        makeVersion("keep")
        val leftover = File(versionsDir(), "keep_removed")
        leftover.mkdirs()
        File(leftover, "keep_removed.json").writeText("""{"id":"keep_removed"}""")
        File(leftover, "keep_removed.jar").writeBytes(ByteArray(2048) { it.toByte() })

        val vm = manager()
        val ids = vm.getLocalVersions().map { it.id }

        assertTrue(ids.contains("keep"))
        assertFalse(ids.contains("keep_removed"))
    }

    @Test
    fun removeVersionFromDisk_missingId_isIdempotent() {
        val vm = manager()
        assertTrue(vm.removeVersionFromDisk("never-installed"))
    }
}
