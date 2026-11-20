package com.verse.explorer.app.horizon

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Horizon 派生包（horizon-derived.zip）的生成逻辑。
 *
 * 上游的 prepare 工具在手机上跑不了（Rust 桌面二进制），但它真正做的事只是：
 * `java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports --output out`，
 * 然后把产物里的四样东西挑出来打包。启动器自己就有 JRE 和版本下载能力，所以这一段可以在
 * 手机上原样复刻——不需要 PC，也不需要正版账号（server.jar 是 Mojang 公开下载）。
 *
 * 四样产物（与 crates/horizon-prepare/src/lib.rs 的 install_datagen_reports 对齐）：
 *   registries.json、commands.json、items.json（由 components/item 下的每个 json 合并）、biome-parameters/
 */
object HorizonDataPack {

    data class ServerJar(
        val id: String,
        val url: String,
        val size: Long,
        val sha1: String
    )

    /** 从 26.3 的版本 JSON 里取 server.jar 的下载信息（缺失返回 null） */
    fun parseServerJar(versionJson: JsonObject): ServerJar? {
        val id = versionJson.get("id")?.asString ?: return null
        val server = versionJson.getAsJsonObject("downloads")?.getAsJsonObject("server") ?: return null
        val url = server.get("url")?.asString ?: return null
        return ServerJar(
            id = id,
            url = url,
            size = server.get("size")?.asLong ?: 0L,
            sha1 = server.get("sha1")?.asString ?: ""
        )
    }

    /**
     * 合并 `reports/minecraft/components/item/<id>.json` 的 `components` 字段。
     *
     * 输入顺序即输出顺序（上游用 Python dict，保持插入顺序），避免不同设备产出不同 zip。
     */
    fun buildItemsJson(components: List<Pair<String, JsonObject>>): String {
        val root = JsonObject()
        for ((id, report) in components) {
            val componentsObject = report.getAsJsonObject("components") ?: continue
            root.add("minecraft:$id", componentsObject)
        }
        return root.toString()
    }

    /**
     * 把 datagen 的 reports 目录打包成 horizon-derived.zip。
     *
     * @return 写进 zip 的条目数；0 表示报告目录不完整（没有生成文件）
     */
    /**
     * 定位真正的 reports 目录：官方生成器会在 --output 下自建 reports/，
     * 上游工具也可能直接把产物摊在根目录，所以两种都要认。
     */
    fun resolveReportsDir(dir: File): File =
        if (File(dir, "registries.json").isFile) dir
        else listOf(File(dir, "reports"), File(dir, "report"))
            .firstOrNull { File(it, "registries.json").isFile } ?: dir

    fun buildDerivedZip(reportsDir: File, dest: File): Int {
        val root = resolveReportsDir(reportsDir)
        val registries = File(root, "registries.json")
        val commands = File(reportsDir, "commands.json")
        val itemDir = File(root, "minecraft/components/item")
        if (!registries.isFile || !itemDir.isDirectory) return 0

        val componentEntries = itemDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedBy { it.name }
            ?.mapNotNull { file ->
                val parsed = runCatching {
                    JsonParser.parseString(file.readText()).asJsonObject
                }.getOrNull() ?: return@mapNotNull null
                file.nameWithoutExtension to parsed
            }
            .orEmpty()
        if (componentEntries.isEmpty()) return 0

        val itemsJson = buildItemsJson(componentEntries)
        val biomeDir = File(root, "minecraft/biome_parameters")
        val biomeFiles = biomeDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedBy { it.name }
            .orEmpty()

        dest.parentFile?.mkdirs()
        var count = 0
        ZipOutputStream(dest.outputStream().buffered()).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
                count++
            }
            put("registries.json", registries.readBytes())
            if (commands.isFile) put("commands.json", commands.readBytes())
            put("items.json", itemsJson.toByteArray(Charsets.UTF_8))
            for (file in biomeFiles) {
                put("biome-parameters/${file.name}", file.readBytes())
            }
        }
        return count
    }
}
