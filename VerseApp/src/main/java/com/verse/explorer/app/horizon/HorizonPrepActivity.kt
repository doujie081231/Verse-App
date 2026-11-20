package com.verse.explorer.app.horizon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.theme.VerseColors
import kotlinx.coroutines.launch

/**
 * Rust 版前置准备页。
 *
 * 首次启动（或派生物缺失时）走到这里，四步走完自动进游戏：
 *   ① 生成派生数据（server.jar + 官方数据生成器，Java 25）
 *   ② 交给引擎下载约 700MB 游戏文件（进度来自 nativeStatus 轮询）
 * 全程在启动器内，不需要电脑、不需要正版账号。
 */
class HorizonPrepActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HorizonPrepScreen(onFinished = { finish() }) }
    }
}

private data class StepState(val title: String, val detail: String, val done: Boolean, val active: Boolean)

@Composable
private fun HorizonPrepScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stage by remember { mutableStateOf(HorizonPrep.Stage.CHECK) }
    var detail by remember { mutableStateOf("") }
    var fraction by remember { mutableStateOf(0f) }
    var logTail by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    fun run() {
        if (running) return
        running = true
        error = null
        scope.launch {
            val derived = HorizonPrep.ensureDerivedPack(context) { p ->
                stage = p.stage
                detail = p.text
                if (p.fraction >= 0f) fraction = p.fraction
                logTail = HorizonPrepLog.tail(6)
            }
            if (derived.isFailure) {
                val ex = derived.exceptionOrNull()
                HorizonPrepLog.append(context, "失败: ${ex?.javaClass?.simpleName}: ${ex?.message}")
                logTail = HorizonPrepLog.tail(6)
                error = ex?.message ?: "生成派生数据失败"
                running = false
                return@launch
            }
            val engine = HorizonPrep.runEngineSetup(context) { p ->
                stage = p.stage
                detail = p.text
                if (p.fraction >= 0f) fraction = p.fraction
                logTail = HorizonPrepLog.tail(6)
            }
            if (engine.isFailure) {
                val ex = engine.exceptionOrNull()
                HorizonPrepLog.append(context, "失败: ${ex?.javaClass?.simpleName}: ${ex?.message}")
                logTail = HorizonPrepLog.tail(6)
                error = ex?.message ?: "准备游戏文件失败"
                running = false
                return@launch
            }
            running = false
            HorizonPrep.markInstalled(context, true)
            runCatching { HorizonLauncher.startGame(context) }
            onFinished()
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { run() }

    val steps = listOf(
        StepState(
            "生成 Rust 版派生数据",
            "下载 26.3 服务端并运行官方数据生成器（Java 25，约 2-5 分钟）",
            done = stage.ordinal > HorizonPrep.Stage.DATAGEN.ordinal,
            active = stage == HorizonPrep.Stage.VERSION_JSON ||
                stage == HorizonPrep.Stage.SERVER_JAR ||
                stage == HorizonPrep.Stage.DATAGEN ||
                stage == HorizonPrep.Stage.PACK
        ),
        StepState(
            "准备游戏文件",
            "约 700MB，走 Mojang 官方端点，可断点续传",
            done = stage == HorizonPrep.Stage.READY,
            active = stage == HorizonPrep.Stage.ENGINE
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.PcBgPrimary)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Rust 版 · 首次准备",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = VerseColors.PcTextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "这一步会在启动器内自动完成，不需要电脑，也不需要正版账号。",
            fontSize = 11.sp,
            color = VerseColors.PcTextMuted
        )
        Spacer(Modifier.height(20.dp))

        steps.forEach { step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(19.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            when {
                                step.done -> VerseColors.PcTextPrimary
                                step.active -> VerseColors.PcTextSecondary
                                else -> VerseColors.PcBgTertiary
                            }
                        )
                )
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        step.title,
                        fontSize = 13.sp,
                        color = if (step.done || step.active) VerseColors.PcTextPrimary else VerseColors.PcTextMuted
                    )
                    Text(step.detail, fontSize = 10.sp, color = VerseColors.PcTextMuted)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 当前进度
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(VerseColors.PcBgTertiary)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(5.dp)
                    .background(VerseColors.PcTextPrimary)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            detail.ifBlank { if (running) "准备中…" else "等待开始" },
            fontSize = 11.sp,
            color = VerseColors.PcTextSecondary
        )

        if (logTail.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(VerseColors.PcBgCard)
                    .border(1.dp, VerseColors.PcBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("执行日志", fontSize = 10.sp, color = VerseColors.PcTextMuted)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (copied) "已复制" else "复制日志",
                        fontSize = 10.sp,
                        color = VerseColors.PcAccent,
                        modifier = Modifier.clickable {
                            runCatching {
                                val clipboard = context.getSystemService(
                                    android.content.Context.CLIPBOARD_SERVICE
                                ) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        "horizon-prep-log", HorizonPrepLog.all()
                                    )
                                )
                                copied = true
                            }
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = logTail,
                    fontSize = 9.sp,
                    color = VerseColors.PcTextSecondary,
                    maxLines = 8
                )
            }
        }

        error?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(
                "失败：$message",
                fontSize = 11.sp,
                color = VerseColors.PcRed,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(VerseColors.PcRed.copy(alpha = 0.1f))
                    .padding(10.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "重试",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerseColors.PcBgPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(VerseColors.PcTextPrimary)
                    .clickable { run() }
                    .padding(vertical = 11.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "派生物由你自己设备上的 26.3 服务端生成，仅保存在本机（files/horizon/）。",
            fontSize = 9.sp,
            color = VerseColors.PcTextMuted
        )
    }
}
