package com.verse.explorer.launcher.fcl.launch

import com.verse.explorer.launcher.fcl.util.versioning.GameVersionNumber

/**
 * LWJGL 栈版本选择。
 *
 * 契约：>= 3.4.1 用 3.4.1；其余用 3.3.3；< 3.3.3 额外启用 lwjglx 兼容层。
 */
object LwjglVersionPolicy {

    data class Selection(val version: String, val useLwjglX: Boolean)

    @JvmStatic
    fun select(detectedVersion: String?): Selection {
        if (detectedVersion == null) {
            return Selection("3.3.3", useLwjglX = false)
        }
        return try {
            Selection(
                version = if (GameVersionNumber.compare(detectedVersion, "3.4.1") >= 0) {
                    "3.4.1"
                } else {
                    "3.3.3"
                },
                useLwjglX = GameVersionNumber.compare(detectedVersion, "3.3.3") < 0
            )
        } catch (t: Throwable) {
            Selection("3.3.3", useLwjglX = false)
        }
    }
}
