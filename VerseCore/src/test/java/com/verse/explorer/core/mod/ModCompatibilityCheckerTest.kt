package com.verse.explorer.core.mod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 启动前模组兼容性预检测试。
 *
 * 用户反馈「模组玩不了」暴露的问题：不兼容的模组只在游戏内静默失败。
 * 这里锁死两类关键行为：
 *  1. 加载器/MC 版本/Java 版本不匹配必须被识别为具体条目；
 *  2. **拿不准必须放行**——快照号、无法解析的约束写法一律不拦（保守原则）。
 */
class ModCompatibilityCheckerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun makeFabricJar(
        dir: File,
        name: String,
        dependsJson: String,
        withFabricMeta: Boolean = true
    ): File {
        val jar = File(dir, name)
        ZipOutputStream(jar.outputStream()).use { zip ->
            if (withFabricMeta) {
                zip.putNextEntry(ZipEntry("fabric.mod.json"))
                zip.write(
                    """{"id":"$name","version":"1.0","name":"模组$name","depends":$dependsJson}"""
                        .toByteArray()
                )
                zip.closeEntry()
            }
        }
        return jar
    }

    private fun makeForgeJar(dir: File, name: String, toml: String): File {
        val jar = File(dir, name)
        ZipOutputStream(jar.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/mods.toml"))
            zip.write(toml.toByteArray())
            zip.closeEntry()
        }
        return jar
    }

    private fun reportOf(vararg jars: File, mc: String, loader: String?, java: Int) =
        ModCompatibilityChecker.check(jars.first().parentFile, mc, loader, java)

    // ---------- 加载器匹配 ----------

    @Test
    fun fabricMod_onForgeVersion_flagged() {
        val jar = makeFabricJar(tmp.root, "a.jar", """{"java":">=8"}""")
        val report = reportOf(jar, mc = "1.20.1", loader = "forge", java = 17)
        assertTrue(report.hasIssues)
        assertTrue(report.issues[0].reason.contains("Fabric"))
    }

    @Test
    fun fabricMod_onQuiltVersion_allowed() {
        val jar = makeFabricJar(tmp.root, "a.jar", """{"java":">=8"}""")
        val report = reportOf(jar, mc = "1.20.1", loader = "quilt", java = 17)
        assertFalse(report.hasIssues)
    }

    @Test
    fun anyMod_onVanillaVersion_flagged() {
        val jar = makeFabricJar(tmp.root, "a.jar", """{}""")
        val report = reportOf(jar, mc = "1.20.1", loader = null, java = 17)
        assertTrue(report.hasIssues)
        assertTrue(report.issues[0].reason.contains("加载器"))
    }

    @Test
    fun forgeMod_onFabricVersion_flagged() {
        val jar = makeForgeJar(
            tmp.root, "b.jar",
            """[[mods]]
modId="b"
version="1.0"

[[dependencies.b]]
modId="minecraft"
versionRange="[1.20,1.21)"
mandatory=true
"""
        )
        val report = reportOf(jar, mc = "1.20.1", loader = "fabric", java = 17)
        assertTrue(report.hasIssues)
        assertTrue(report.issues[0].reason.contains("Forge"))
    }

    // ---------- MC 版本约束 ----------

    @Test
    fun mcRange_geLt_matchingVersion_passes() {
        val jar = makeFabricJar(tmp.root, "a.jar", """{"minecraft":">=1.20 <1.21"}""")
        val report = reportOf(jar, mc = "1.20.1", loader = "fabric", java = 17)
        assertFalse(report.hasIssues)
    }

    @Test
    fun mcRange_geLt_outsideVersion_flagged() {
        val jar = makeFabricJar(tmp.root, "a.jar", """{"minecraft":">=1.20 <1.21"}""")
        val report = reportOf(jar, mc = "1.21.1", loader = "fabric", java = 17)
        assertTrue(report.hasIssues)
        assertTrue(report.issues[0].reason.contains("1.21.1"))
    }

    @Test
    fun mcExact_mismatch_flagged() {
        val jar = makeFabricJar(tmp.root, "a.jar", """{"minecraft":"1.20.1"}""")
        val report = reportOf(jar, mc = "1.19.4", loader = "fabric", java = 17)
        assertTrue(report.hasIssues)
    }

    @Test
    fun mcRange_snapshotActual_undecidable_passes() {
        // 快照号无法与 1.x 比较：保守放行
        val jar = makeFabricJar(tmp.root, "a.jar", """{"minecraft":">=1.20"}""")
        val report = reportOf(jar, mc = "26w14a", loader = "fabric", java = 21)
        assertFalse(report.hasIssues)
    }

    @Test
    fun mcRange_tilde_sameMinor() {
        val jar = makeFabricJar(tmp.root, "a.jar", """{"minecraft":"~1.20.1"}""")
        assertFalse(reportOf(jar, mc = "1.20.4", loader = "fabric", java = 17).hasIssues)
        assertTrue(reportOf(jar, mc = "1.21", loader = "fabric", java = 17).hasIssues)
    }

    @Test
    fun mcRange_caret_sameMajor() {
        val jar = makeFabricJar(tmp.root, "a.jar", """{"minecraft":"^1.20.1"}""")
        assertFalse(reportOf(jar, mc = "1.20.6", loader = "fabric", java = 17).hasIssues)
        assertTrue(reportOf(jar, mc = "1.21", loader = "fabric", java = 17).hasIssues)
    }

    @Test
    fun mcRange_mavenInterval() {
        val jar = makeForgeJar(
            tmp.root, "c.jar",
            """[[mods]]
modId="c"

[[dependencies.c]]
modId="minecraft"
versionRange="[1.20,1.21)"
"""
        )
        assertFalse(reportOf(jar, mc = "1.20.6", loader = "forge", java = 17).hasIssues)
        assertTrue(reportOf(jar, mc = "1.21.1", loader = "forge", java = 17).hasIssues)
    }

    @Test
    fun mcRange_arrayConstraints_allChecked() {
        val jar = makeFabricJar(tmp.root, "a.jar", """{"minecraft":[">=1.20","<1.21"]}""")
        assertFalse(reportOf(jar, mc = "1.20.4", loader = "fabric", java = 17).hasIssues)
        assertTrue(reportOf(jar, mc = "1.21.1", loader = "fabric", java = 17).hasIssues)
    }

    // ---------- Java 版本约束 ----------

    @Test
    fun javaRequirement_tooLow_flagged() {
        val jar = makeFabricJar(tmp.root, "a.jar", """{"java":">=17"}""")
        val report = reportOf(jar, mc = "1.20.1", loader = "fabric", java = 8)
        assertTrue(report.hasIssues)
        assertTrue(report.issues[0].reason.contains("Java"))
    }

    @Test
    fun javaRequirement_met_passes() {
        val jar = makeFabricJar(tmp.root, "a.jar", """{"java":">=17"}""")
        assertFalse(reportOf(jar, mc = "1.20.1", loader = "fabric", java = 21).hasIssues)
    }

    @Test
    fun javaRequirement_unknownJavaMajor_skipped() {
        val jar = makeFabricJar(tmp.root, "a.jar", """{"java":">=17"}""")
        assertFalse(reportOf(jar, mc = "1.20.1", loader = "fabric", java = 0).hasIssues)
    }

    // ---------- 边界 ----------

    @Test
    fun disabledAndNonJarFiles_ignored() {
        makeFabricJar(tmp.root, "a.jar", """{}""")
        // 禁用（.disabled 后缀）与非 jar 文件不参与检查
        File(tmp.root, "a.jar.disabled").writeBytes(ByteArray(0))
        File(tmp.root, "readme.txt").writeText("not a jar")
        val report = ModCompatibilityChecker.check(tmp.root, "1.20.1", "fabric", 17)
        assertFalse(report.hasIssues)
        assertEquals(1, report.modCount)
    }

    @Test
    fun unreadableJar_skippedByChecker() {
        // 无元数据 jar：由冲突扫描负责（UNREADABLE），兼容性检查不拦
        File(tmp.root, "plain.jar").writeBytes(ByteArray(10))
        val report = ModCompatibilityChecker.check(tmp.root, "1.20.1", "fabric", 17)
        assertFalse(report.hasIssues)
        assertEquals(0, report.modCount)
    }

    @Test
    fun emptyOrMissingModsDir_emptyReport() {
        assertTrue(ModCompatibilityChecker.check(null, "1.20.1", "fabric", 17).modCount == 0)
        assertTrue(ModCompatibilityChecker.check(File(tmp.root, "nope"), "1.20.1", "fabric", 17).modCount == 0)
    }

    // ---------- 版本比较器 ----------

    @Test
    fun compareMc_numericSemantics() {
        assertEquals(1, McVersionRange.compareMc("1.21", "1.20.9"))
        assertEquals(0, McVersionRange.compareMc("1.20", "1.20.0"))
        assertEquals(-1, McVersionRange.compareMc("1.7.10", "1.8"))
        assertNull(McVersionRange.compareMc("26w14a", "1.20"))
        assertEquals(0, McVersionRange.compareMc("26w14a", "26w14a"))
    }
}
