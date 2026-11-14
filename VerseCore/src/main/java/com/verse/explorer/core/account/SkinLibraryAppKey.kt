package com.verse.explorer.core.account

/**
 * 皮肤站为「Verse 启动器」签发的应用级 API Key（只读公开皮肤列表）。
 *
 * 为什么是应用级而不是账号级：列表接口按 CurseForge 的模式要求携带 key，
 * 启动器必须自带一把才能浏览；这把 key 没有账号身份（uid=0），
 * 拿到它只能读列表，改不了任何人的角色。
 *
 * 为什么不写明文：直接在 dex 里留 `vk_...` 等于把凭据印在说明书上。
 * 这里存的是每字节异或 0x5A 后的整数，运行时再还原 —— 只提高反编译门槛，
 * 客户端内置凭据本质无法做到真正保密（CurseForge 的 key 也是同样处境）。
 * 真要轮换，改服务端记录 + 换这里的数组即可。
 */
internal object SkinLibraryAppKey {

    private const val MASK = 0x5A

    private val encoded = intArrayOf(
        44, 49, 5, 111, 63, 111, 110, 107,
        111, 59, 63, 60, 110, 106, 111, 59,
        99, 56, 60, 105, 56, 99, 108, 108,
        99, 63, 98, 111, 62, 109, 110, 98,
        111, 63, 111, 98, 60, 110, 56, 59,
        98, 60, 59, 105, 60, 111, 57, 60,
        56, 99, 99,
    )

    fun value(): String = buildString(encoded.size) {
        encoded.forEach { append((it xor MASK).toChar()) }
    }
}
