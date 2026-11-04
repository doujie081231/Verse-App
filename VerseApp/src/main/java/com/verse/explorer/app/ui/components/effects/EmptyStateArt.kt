package com.verse.explorer.app.ui.components.effects

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.verse.explorer.app.ui.theme.VerseColors
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

/**
 * 空态 / 错误态插图：一圈缓慢呼吸的光环 + 中间的图标。
 *
 * 之前空态是一个静止的灰色图标，观感上像"页面没加载出来"；这里加一层很轻的
 * Lottie 呼吸环，让页面看起来是"确实没有内容"而不是"卡住了"。
 *
 * 动画用内联 JSON（几百字节）随包走，不引入外部资源文件，也不走网络；
 * 万一某台设备上解析失败（composition 为 null），退化为纯图标，不影响可用性。
 */
@Composable
fun EmptyStateArt(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    iconTint: Color = VerseColors.PcTextMuted
) {
    val composition = rememberLottieComposition(LottieCompositionSpec.JsonString(BREATH_RING_JSON))
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        val composed = composition.value
        if (composed != null) {
            Image(
                painter = rememberLottiePainter(composed),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(size * 0.42f)
        )
    }
}

/**
 * 呼吸光环：半径 88 的描边圆，90 帧（3 秒）内缩放 88% ↔ 104%、透明度 45% ↔ 85%。
 * 尺寸与时间都刻意做得克制——空态是长时间停留的界面，动得太明显会干扰阅读。
 */
private const val BREATH_RING_JSON = """
{"v":"5.5.7","fr":30,"ip":0,"op":90,"w":120,"h":120,"nm":"ring","ddd":0,"assets":[],"layers":[
{"ddd":0,"ind":1,"ty":4,"nm":"ring","sr":1,
"ks":{"o":{"a":1,"k":[{"t":0,"s":[40],"e":[80],"i":{"x":[0.5],"y":[1]},"o":{"x":[0.5],"y":[0]}},
{"t":45,"s":[80],"e":[40],"i":{"x":[0.5],"y":[1]},"o":{"x":[0.5],"y":[0]}},
{"t":90,"s":[40]}]},
"r":{"a":0,"k":0},
"p":{"a":0,"k":[60,60,0]},
"a":{"a":0,"k":[0,0,0]},
"s":{"a":1,"k":[{"t":0,"s":[88,88,100],"e":[104,104,100],"i":{"x":[0.5],"y":[1]},"o":{"x":[0.5],"y":[0]}},
{"t":45,"s":[104,104,100],"e":[88,88,100],"i":{"x":[0.5],"y":[1]},"o":{"x":[0.5],"y":[0]}},
{"t":90,"s":[88,88,100]}]}},
"ao":0,
"shapes":[
{"ty":"el","p":{"a":0,"k":[0,0]},"s":{"a":0,"k":[88,88]},"nm":"e"},
{"ty":"st","c":{"a":0,"k":[0.55,0.58,0.64,1]},"o":{"a":0,"k":100},"w":{"a":0,"k":3},"lc":2,"lj":2,"nm":"s"}
],
"ip":0,"op":90,"st":0,"bm":0}]}
"""
