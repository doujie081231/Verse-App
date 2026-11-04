package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.data.SettingsRepository

/**
 * 协议版本。
 *
 * 改动协议文本时同步改这里：老用户已同意的是旧版本，版本号不一致会重新弹出确认，
 * 这样"覆盖更新"的用户也能看到新协议（而不是只有全新安装才弹）。
 */
const val AGREEMENT_VERSION = "1.1.0-2026-11"

private data class AgreementDoc(val title: String, val assetPath: String)

private val AGREEMENT_DOCS = listOf(
    AgreementDoc("《Verse App 用户协议》", "legal/zh/user_agreement.md"),
    AgreementDoc("《Verse App 隐私政策》", "legal/zh/privacy_policy.md")
)

/** 当前是否已同意最新版本协议（未同意时不弹赞助等打扰性弹窗） */
fun isAgreementAccepted(): Boolean =
    SettingsRepository.getAgreedAgreementVersion() == AGREEMENT_VERSION

/**
 * 首次使用 / 协议更新后的强制同意门。
 *
 * 未同意时以不可取消的全屏弹窗覆盖整个界面：必须勾选同意才能继续使用。
 * 同意状态按 [AGREEMENT_VERSION] 记录，协议文本变更后自动再次弹出。
 *
 * 布局分三段：中间正文可滚动、底部按钮固定。
 * 这样小屏 / 横屏 / 系统大字号下按钮也不会被内容挤出屏幕。
 */
@Composable
fun AgreementGate(onAgreed: () -> Unit = {}) {
    // SettingsRepository 是全局单例（应用启动时已 init），直接读当前已同意的版本
    var agreedVersion by remember { mutableStateOf(SettingsRepository.getAgreedAgreementVersion()) }
    var checked by remember { mutableStateOf(false) }
    var openedDoc by remember { mutableStateOf<AgreementDoc?>(null) }

    if (agreedVersion == AGREEMENT_VERSION) return

    Dialog(
        onDismissRequest = { /* 协议未同意不允许关闭 */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(VerseColors.PcBgPrimary)
                .systemBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ---------- 中间：正文可滚动，内容不足一屏时垂直居中 ----------
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val viewportHeight = maxHeight
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = viewportHeight)
                                .padding(top = 20.dp, bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // 大字标题：首屏最重要的信息
                            Text(
                                text = "欢迎首次使用",
                                fontSize = 22.sp,
                                color = VerseColors.PcTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Verse App",
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color = VerseColors.PcAccent,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(18.dp))
                            Text(
                                text = "在使用之前，请你先同意两部分协议",
                                fontSize = 15.sp,
                                color = VerseColors.PcTextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(18.dp))

                            // 两份协议：点击查看全文
                            AGREEMENT_DOCS.forEach { doc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(VerseColors.PcBgCard)
                                        .clickable { openedDoc = doc }
                                        .padding(horizontal = 14.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = doc.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = VerseColors.PcTextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "查看",
                                        fontSize = 12.sp,
                                        color = VerseColors.PcAccent
                                    )
                                    Icon(
                                        Icons.Filled.ChevronRight,
                                        null,
                                        Modifier.size(16.dp),
                                        VerseColors.PcTextMuted
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }

                            Spacer(Modifier.height(10.dp))

                            // 勾选同意
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { checked = !checked }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { checked = it },
                                    colors = CheckboxDefaults.colors(checkedColor = VerseColors.PcAccent)
                                )
                                Text(
                                    text = "我已阅读并同意上述两部分协议",
                                    fontSize = 12.sp,
                                    color = VerseColors.PcTextSecondary
                                )
                            }
                        }
                    }
                }

                // ---------- 底部：按钮固定，永远在屏幕内 ----------
                Spacer(Modifier.height(10.dp))
                PcButton(
                    text = if (checked) "同意并继续" else "请先勾选同意",
                    onClick = {
                        if (!checked) return@PcButton
                        SettingsRepository.setAgreedAgreementVersion(AGREEMENT_VERSION)
                        agreedVersion = AGREEMENT_VERSION
                        VerseLogger.info("协议已同意: $AGREEMENT_VERSION", "Agreement")
                        onAgreed()
                    },
                    variant = if (checked) PcButtonVariant.PRIMARY else PcButtonVariant.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "若你不同意上述协议，请卸载本应用",
                    fontSize = 10.sp,
                    color = VerseColors.PcTextMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    openedDoc?.let { doc ->
        AgreementDocumentDialog(doc = doc, onClose = { openedDoc = null })
    }
}

/** 协议全文阅读弹窗：读取 assets 内的文本，按「# 标题 / 正文」简单排版 */
@Composable
private fun AgreementDocumentDialog(doc: AgreementDoc, onClose: () -> Unit) {
    val context = LocalContext.current
    val content = remember(doc.assetPath) {
        runCatching {
            context.assets.open(doc.assetPath).bufferedReader().use { it.readText() }
        }.getOrDefault("协议文本读取失败，请重新安装应用后再试。")
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(VerseColors.PcBgPrimary)
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = doc.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerseColors.PcTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                PcButton(text = "返回", onClick = onClose, variant = PcButtonVariant.OUTLINE)
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VerseColors.PcBgCard)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                content.lineSequence().forEach { raw ->
                    val line = raw.trim()
                    when {
                        line.isEmpty() -> Spacer(Modifier.height(4.dp))
                        line.startsWith("### ") -> Text(
                            line.removePrefix("### "),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VerseColors.PcTextSecondary
                        )
                        line.startsWith("## ") -> Text(
                            line.removePrefix("## "),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VerseColors.PcTextPrimary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        line.startsWith("# ") -> Text(
                            line.removePrefix("# "),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = VerseColors.PcTextPrimary
                        )
                        else -> Text(
                            line,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            color = VerseColors.PcTextSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PcButton(text = "关闭", onClick = onClose, variant = PcButtonVariant.PRIMARY)
            }
        }
    }
}
