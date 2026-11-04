package com.verse.explorer.app.ui.components.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.vinceglb.confettikit.compose.ConfettiKit
import io.github.vinceglb.confettikit.core.Party
import io.github.vinceglb.confettikit.core.emitter.Emitter
import kotlin.time.Duration.Companion.milliseconds

/**
 * 成功态的彩带庆祝。
 *
 * 只用在"用户完成了一件有成本的事"的节点上（模组/前置下载完成），
 * 不做全局弹窗、不加音效；[play] 为 false 时完全不参与组合，不占布局也不耗帧。
 *
 * 粒子发射器限时 1.2 秒、每秒 90 个，结束后自然落地消失，不会一直占用渲染。
 */
@Composable
fun ConfettiBurst(
    play: Boolean,
    modifier: Modifier = Modifier
) {
    if (!play) return
    val parties = remember {
        listOf(Party(emitter = Emitter(1200.milliseconds).perSecond(90)))
    }
    ConfettiKit(modifier, parties)
}
