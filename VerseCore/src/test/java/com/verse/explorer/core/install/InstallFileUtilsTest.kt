package com.verse.explorer.core.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 库文件「是否需要重新下载」的判定。
 *
 * 这里的每一条都对应一次真实事故形态：下载被取消会在目标路径留下半截文件，
 * 若只判断存在性，它会被永久跳过，直到启动时 loader 抛
 * `zip END header not found` / `NoClassDefFoundError`。
 */
class InstallFileUtilsTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** 生成一个可被正常打开的 jar（含一个条目，内容不可压缩以保证体积大于 1KB） */
    private fun makeJar(name: String, content: ByteArray = randomBytes(8192)): File {
        val file = File(temp.root, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("test.txt"))
            zip.write(content)
            zip.closeEntry()
        }
        return file
    }

    private fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        java.util.Random(42L).nextBytes(bytes)
        return bytes
    }

    @Test
    fun missingFile_needsDownload() {
        assertTrue(InstallFileUtils.libraryNeedsDownload(File(temp.root, "absent.jar"), null, -1))
    }

    @Test
    fun tinyFile_needsDownload() {
        val tiny = File(temp.root, "tiny.jar").apply { writeBytes(ByteArray(100)) }
        assertTrue(InstallFileUtils.libraryNeedsDownload(tiny, null, -1))
        // 有 sha1 也一样：过小的文件必然不完整
        assertTrue(InstallFileUtils.libraryNeedsDownload(tiny, "deadbeef", -1))
    }

    @Test
    fun sizeMismatch_needsDownload() {
        val jar = makeJar("size.jar")
        // 清单声明的大小与磁盘不符 → 半截文件，即便 sha1 参数正确也要求重下
        assertTrue(InstallFileUtils.libraryNeedsDownload(jar, null, jar.length() + 1024))
    }

    @Test
    fun sha1Match_noDownload() {
        val jar = makeJar("ok.jar")
        val sha1 = InstallFileUtils.calculateSHA1(jar)
        assertFalse(InstallFileUtils.libraryNeedsDownload(jar, sha1, jar.length()))
        assertFalse(InstallFileUtils.libraryNeedsDownload(jar, sha1.uppercase(), -1))
    }

    @Test
    fun sha1Mismatch_needsDownload() {
        val jar = makeJar("bad.jar")
        // 长度对得上但内容不同（例如被截断后补零）也必须重下
        assertFalse(InstallFileUtils.libraryNeedsDownload(jar, InstallFileUtils.calculateSHA1(jar), -1))
        assertTrue(InstallFileUtils.libraryNeedsDownload(jar, "0000000000000000000000000000000000000000", -1))
    }

    @Test
    fun truncatedJarWithoutSha1_needsDownload() {
        val jar = makeJar("truncated.jar", randomBytes(16384))
        val original = jar.readBytes()
        // 砍掉尾部：zip 中央目录丢失，这正是 netty-common 事故的形态
        jar.writeBytes(original.copyOf(original.size / 2))
        assertTrue(InstallFileUtils.libraryNeedsDownload(jar, null, -1))
    }

    @Test
    fun intactJarWithoutSha1_noDownload() {
        val jar = makeJar("intact.jar")
        assertFalse(InstallFileUtils.libraryNeedsDownload(jar, null, -1))
    }

    @Test
    fun nonJarWithoutSha1_trustedByPresence() {
        // .so 等非 zip 文件没有可用的结构校验，只能信任存在性
        val so = File(temp.root, "libfoo.so").apply { writeBytes(ByteArray(4096)) }
        assertFalse(InstallFileUtils.libraryNeedsDownload(so, null, -1))
    }
}
