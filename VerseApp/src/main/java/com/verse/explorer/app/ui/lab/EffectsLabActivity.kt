package com.verse.explorer.app.ui.lab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Duration.Companion.seconds
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.ui.theme.VerseExplorerTheme
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import io.github.vinceglb.confettikit.compose.ConfettiKit
import io.github.vinceglb.confettikit.core.Party
import io.github.vinceglb.confettikit.core.emitter.Emitter
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 动效实验室。
 *
 * 面板只列出效果名，点哪一项才渲染哪一项：动效在首次组合阶段一旦出问题，
 * 只会崩在那一项上，不会连累整个页面（此前整页一次性渲染，进页面就崩，
 * 反而看不出是哪个库的问题）。每项进入前都写一行日志，便于和崩溃日志对位。
 */
class EffectsLabActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VerseLogger.info("动效实验室：打开页面", "EffectsLab")
        setContentView(ComposeView(this).apply {
            setContent {
                VerseExplorerTheme {
                    EffectsLabScreen(onBack = { finish() })
                }
            }
        })
    }

    companion object {
        fun start(context: android.content.Context) {
            context.startActivity(android.content.Intent(context, EffectsLabActivity::class.java))
        }
    }
}

/** 实验室里的单项效果 */
private enum class LabEffect(val title: String, val desc: String) {
    SHIMMER("骨架屏微光", "加载中的占位块，光带循环扫过"),
    CONFETTI("彩带庆祝", "成功态的小庆祝，点按钮撒一次"),
    REORDER("拖拽排序", "按住右侧手柄上下拖，顺序实时变化"),
    SHARED_ELEMENT("共享元素转场", "点一下圆球，在两个位置之间平滑过渡"),
    LOTTIE("Lottie 动效", "内置一份最小呼吸动画，可换成加载/空态插画")
}

@Composable
private fun EffectsLabScreen(onBack: () -> Unit) {
    var opened by remember { mutableStateOf<LabEffect?>(null) }
    val effect = opened
    if (effect != null) {
        EffectPage(effect = effect, onClose = { opened = null })
        return
    }

    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().background(VerseColors.PcBgPrimary)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "返回", Modifier.size(20.dp), VerseColors.PcTextPrimary)
            }
            Text("动效实验室", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VerseColors.PcTextPrimary)
        }
        Text(
            "点开单项查看效果；某一项闪退只影响该页，返回后可以换下一个",
            fontSize = 11.sp,
            color = VerseColors.PcTextMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LabEffect.entries.toList()) { item ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VerseColors.PcBgCard,
                    modifier = Modifier.fillMaxWidth().clickable {
                        VerseLogger.info("动效实验室：进入 ${item.name}", "EffectsLab")
                        opened = item
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                fontSize = 14.sp,
                                color = VerseColors.PcTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(item.desc, fontSize = 10.sp, color = VerseColors.PcTextMuted)
                        }
                        Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp), VerseColors.PcTextMuted)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EffectPage(effect: LabEffect, onClose: () -> Unit) {
    BackHandler { onClose() }
    // 进入即写日志：万一渲染崩了，崩溃日志里最后一条就是崩在哪个效果上
    LaunchedEffect(effect) {
        VerseLogger.info("动效实验室：开始渲染 ${effect.name}", "EffectsLab")
    }
    Column(Modifier.fillMaxSize().background(VerseColors.PcBgPrimary)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.ArrowBack, "返回", Modifier.size(20.dp), VerseColors.PcTextPrimary)
            }
            Text(effect.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VerseColors.PcTextPrimary)
        }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            when (effect) {
                LabEffect.SHIMMER -> ShimmerDemo()
                LabEffect.CONFETTI -> ConfettiDemo()
                LabEffect.REORDER -> ReorderDemo()
                LabEffect.SHARED_ELEMENT -> SharedElementDemo()
                LabEffect.LOTTIE -> LottieDemo()
            }
        }
    }
}

@Composable
private fun LabCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = VerseColors.PcBgCard, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VerseColors.PcTextPrimary)
            Text(subtitle, fontSize = 10.sp, color = VerseColors.PcTextMuted)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** 骨架屏微光：列表加载占位 */
@Composable
private fun ShimmerDemo() {
    val shimmer = rememberShimmer(shimmerBounds = ShimmerBounds.Window)
    LabCard("骨架屏微光", "加载中的占位块，光带循环扫过") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth().shimmer(shimmer),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(VerseColors.PcBgTertiary))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.fillMaxWidth(if (index == 1) 0.6f else 0.85f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(VerseColors.PcBgTertiary)
                        )
                        Box(
                            Modifier.fillMaxWidth(0.4f).height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(VerseColors.PcBgTertiary)
                        )
                    }
                }
            }
        }
    }
}

/** 彩带庆祝：每次点按换一个 key 重新组合，粒子系统因此从头播放 */
@Composable
private fun ConfettiDemo() {
    var replay by remember { mutableIntStateOf(0) }
    LabCard("彩带庆祝", "点一次撒一次，用于安装完成等成功态") {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            if (replay > 0) {
                key(replay) {
                    ConfettiKit(Modifier.fillMaxSize(), listOf(Party(emitter = Emitter(2.seconds).perSecond(60))))
                }
            }
            PcButton(
                text = "撒一次",
                onClick = { replay++ },
                variant = PcButtonVariant.PRIMARY,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/** 拖拽排序 */
@Composable
private fun ReorderDemo() {
    val listState = rememberLazyListState()
    val data = remember { mutableStateListOf("第一项", "第二项", "第三项", "第四项", "第五项") }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        data.add(to.index, data.removeAt(from.index))
    }
    LabCard("拖拽排序", "按住右侧手柄上下拖，顺序实时变化") {
        LazyColumn(state = listState, modifier = Modifier.height(300.dp)) {
            items(data, key = { it }) { item ->
                ReorderableItem(state = reorderState, key = item) { _ ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VerseColors.PcBgTertiary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item, fontSize = 13.sp, color = VerseColors.PcTextPrimary, modifier = Modifier.weight(1f))
                            Icon(
                                Icons.Filled.DragHandle, "拖动",
                                Modifier.size(18.dp).draggableHandle(),
                                VerseColors.PcTextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 共享元素转场：用 Compose 自带的 SharedTransitionLayout。
 *
 * 同一把 key 的 sharedElement 在两处出现时，系统会自动做尺寸/位置的补间，
 * 效果与第三方封装库一致，但不需要额外依赖。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedElementDemo() {
    var moved by remember { mutableStateOf(false) }
    LabCard("共享元素转场", "点一下圆球，在两个位置之间平滑过渡") {
        SharedTransitionLayout(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VerseColors.PcBgTertiary)
                .clickable { moved = !moved }
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .size(52.dp)
                        .sharedElementWithCallerManagedVisibility(
                            rememberSharedContentState("ball"),
                            visible = !moved
                        )
                        .background(VerseColors.PcAccent, CircleShape)
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .size(52.dp)
                        .sharedElementWithCallerManagedVisibility(
                            rememberSharedContentState("ball"),
                            visible = moved
                        )
                        .background(VerseColors.PcAccent, CircleShape)
                )
            }
        }
    }
}

/** Lottie 动效（纯 Kotlin 渲染，内置一份最小动画） */
@Composable
private fun LottieDemo() {
    val composition = rememberLottieComposition(LottieCompositionSpec.JsonString(LOTTIE_BREATH_JSON))
    LabCard("Lottie 动效", "内置一份最小呼吸动画，实际可放加载/空态插画") {
        Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            val comp = composition.value
            if (comp != null) {
                Image(
                    painter = rememberLottiePainter(comp),
                    contentDescription = null,
                    modifier = Modifier.size(140.dp)
                )
            } else {
                Text("加载动画中…", fontSize = 11.sp, color = VerseColors.PcTextMuted)
            }
        }
    }
}

/** 最小 Lottie：一个圆在 2 秒内呼吸缩放 */
private const val LOTTIE_BREATH_JSON = """
{"v":"5.5.7","fr":30,"ip":0,"op":60,"w":100,"h":100,"nm":"breath","ddd":0,"assets":[],"layers":[
{"ddd":0,"ind":1,"ty":4,"nm":"dot","sr":1,
"ks":{"o":{"a":0,"k":100},"r":{"a":0,"k":0},"p":{"a":0,"k":[50,50,0]},"a":{"a":0,"k":[0,0,0]},
"s":{"a":1,"k":[{"t":0,"s":[90,90,100],"e":[150,150,100],"i":{"x":[0.5],"y":[1]},"o":{"x":[0.5],"y":[0]}},
{"t":30,"s":[150,150,100],"e":[90,90,100],"i":{"x":[0.5],"y":[1]},"o":{"x":[0.5],"y":[0]}},
{"t":60,"s":[90,90,100]}]}},
"ao":0,"shapes":[{"ty":"el","p":{"a":0,"k":[0,0]},"s":{"a":0,"k":[48,48]},"nm":"e"},
{"ty":"fl","c":{"a":0,"k":[0.34,0.45,0.98,1]},"o":{"a":0,"k":100},"r":1,"nm":"f"}],
"ip":0,"op":60,"st":0,"bm":0}]}
"""
