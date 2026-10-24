package com.verse.explorer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.verse.explorer.app.R
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.launch.LaunchPhase
import com.verse.explorer.app.launch.LaunchState
import com.verse.explorer.app.ui.backgroundGlass
import com.verse.explorer.app.ui.influencedByBackgroundColor
import com.verse.explorer.app.ui.theme.ActionMenuSide
import com.verse.explorer.app.ui.theme.VerseColors
private val ErrorBg = Color(0xFFFEF2F2)
private val ErrorBorder = Color(0xFFFECACA)
private val RepairBg = Color(0xFFEFF6FF)
private val RepairBorder = Color(0xFFBFDBFE)
private val ProgressGradient = listOf(
    VerseColors.PcAccent,
    VerseColors.PcPurple,
    Color(0xFF06B6D4)
)

@Composable
fun PcLaunchOverlay(
    launchState: LaunchState,
    modifier: Modifier = Modifier,
    /** 游戏已进入创建窗口阶段时允许关闭提示（关闭后启动继续在后台进行） */
    onDismiss: () -> Unit = {}
) {
    val alphaAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1f, animationSpec = tween(250))
    }

    val logoScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
    }

    val transition = rememberInfiniteTransition()
    val shimmerOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val progress = if (launchState.steps.isNotEmpty()) {
        ((launchState.currentStepIndex + 1).toFloat() / launchState.steps.size).coerceIn(0f, 1f)
    } else 0f

    val isFailed = launchState.phase == LaunchPhase.FAILED || launchState.errors.isNotEmpty()
    val showRepair = isFailed
    val showLog = launchState.phase == LaunchPhase.LAUNCHING

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .widthIn(max = 360.dp)
                .alpha(alphaAnim.value),
            shape = RoundedCornerShape(24.dp),
            color = VerseColors.PcBgSecondary,
            contentColor = VerseColors.PcTextPrimary,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 创建窗口阶段：给出可关闭的阶段提示（与「正在启动 Java 虚拟机」同形式的弹窗）
                if (showLog) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "正在创建游戏窗口…",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VerseColors.PcTextPrimary
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭提示",
                                tint = VerseColors.PcTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.size(96.dp)) {
                RoseCurveLoader(
                    modifier = Modifier.fillMaxSize(),
                    color = VerseColors.PcAccent
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(VerseColors.PcBgTertiary)
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .drawWithContent {
                                val brush = Brush.linearGradient(
                                    colors = ProgressGradient,
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f)
                                )
                                drawRect(brush = brush, style = Fill)
                                val shimmerX = shimmerOffset * size.width
                                val shimmerBrush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.5f),
                                        Color.Transparent
                                    ),
                                    start = Offset(shimmerX - size.width * 0.3f, 0f),
                                    end = Offset(shimmerX + size.width * 0.3f, 0f)
                                )
                                drawRect(brush = shimmerBrush, style = Fill)
                                drawContent()
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(6.dp)
                                .blur(6.dp)
                                .background(ProgressGradient.last())
                        )
                    }
                }
            }

            Text(
                text = launchState.message,
                fontSize = 13.sp,
                color = VerseColors.PcTextSecondary,
                textAlign = TextAlign.Center
            )

            if (isFailed) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = ErrorBg,
                    border = BorderStroke(1.dp, ErrorBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = VerseColors.PcRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "启动失败",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VerseColors.PcRed
                            )
                        }
                        val errorMessage = if (launchState.errors.isNotEmpty()) {
                            launchState.errors.joinToString("\n")
                        } else {
                            launchState.message
                        }
                        Text(
                            text = errorMessage,
                            fontSize = 12.sp,
                            color = VerseColors.PcTextSecondary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        PcButton(
                            text = "导出日志",
                            onClick = {},
                            variant = PcButtonVariant.OUTLINE
                        )
                    }
                }
            }

            if (showRepair) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = RepairBg,
                    border = BorderStroke(1.dp, RepairBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = VerseColors.PcPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "尝试自动修复启动问题",
                            fontSize = 13.sp,
                            color = VerseColors.PcTextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        PcButton(
                            text = "修复",
                            onClick = {},
                            variant = PcButtonVariant.PRIMARY
                        )
                    }
                }
            }

            if (showLog) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = VerseColors.PcBgTertiary
                ) {
                    Text(
                        text = launchState.message,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = VerseColors.PcTextMuted,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        }
    }
}
