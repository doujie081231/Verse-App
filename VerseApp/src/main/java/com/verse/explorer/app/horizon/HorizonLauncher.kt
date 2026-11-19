package com.verse.explorer.app.horizon

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import java.io.File

/**
 * Rust 版（Horizon 安卓移植，引擎已内置 APK）的启动门面。
 *
 * 引擎 so 已随 APK 收编（jniLibs/arm64-v8a/libhorizon.so），壳层 Java 保持原包名
 * `dev.horizonlab.horizon`（native 层 JNI 反调只认这个包名）。启动入口统一走壳层的
 * SetupActivity：资源未就绪时它自己会引导下载（约 500MB，Mojang 官方端点），就绪后
 * 由用户点 Play 进入游戏。
 */
object HorizonLauncher {

    /** 主页版本选择器里的固定条目 id；启动链路据此分流 */
    const val VERSION_ID = "horizon-rust-26.3"
    const val VERSION_TITLE = "Rust 版 26.3"

    data class HorizonMeta(
        val repo: String = "",
        val tag: String = "",
        val version: String = "",
        val publishedAt: String = "",
        val notesUrl: String = "",
        val engineLib: String = "libhorizon.so",
        val abi: String = "arm64-v8a",
        val shellPackage: String = "dev.horizonlab.horizon",
        val apkSize: Long = 0
    )

    fun readMeta(context: Context): HorizonMeta? = runCatching {
        context.assets.open("horizon_meta.json").bufferedReader().use {
            Gson().fromJson(it, HorizonMeta::class.java)
        }
    }.getOrNull()

    /** 引擎 so 是否随包收编成功 */
    fun isEnginePackaged(context: Context): Boolean =
        File(context.applicationInfo.nativeLibraryDir, "libhorizon.so").exists()

    /** 启动 Rust 版：统一入口（FLAG_ACTIVITY_NEW_TASK 使任意 Context 可用） */
    fun start(context: Context) {
        val intent = Intent(context, dev.horizonlab.horizon.SetupActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 启动链路分流标记 */
    fun isHorizonVersion(versionId: String?): Boolean = versionId == VERSION_ID

    /** 主页版本选择器的合成条目：只有 id 有意义，其余字段留默认 */
    fun syntheticVersion(): com.verse.explorer.core.version.MinecraftVersion =
        com.verse.explorer.core.version.MinecraftVersion(id = VERSION_ID)
}
