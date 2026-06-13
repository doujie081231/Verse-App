package com.verse.explorer.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.ui.theme.VerseColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

private val steveGreetings = listOf(
    "嘿！我是 Steve，你的 Minecraft 助手！有什么需要帮忙的吗？⛏",
    "你好！需要我帮你管理版本、模组，还是想聊聊 Minecraft？",
    "欢迎回来！今天想探索什么？建造、冒险，还是红石机关？",
    "嗨！我是 Steve。想了解新版本、安装模组，还是优化游戏设置？",
    "哟！准备好去冒险了吗？我可以帮你搞定一切 Minecraft 相关的事情！"
)

private val steveHelpResponses = mapOf(
    "版本" to "你可以去「下载」页面查看所有可用的 Minecraft 版本。正式版最稳定，快照版可以提前体验新功能！我推荐使用最新正式版。",
    "模组" to "安装模组可以让游戏更加有趣！去「资源」→「模组」页面浏览 CurseForge 上的模组，选择你喜欢的点击安装即可。记得先确认模组兼容的版本哦！",
    "光影" to "光影可以大幅提升画面效果！去「资源」→「光影」页面，安装 OptiFine 或 Iris 后就可以使用了。建议选择性能友好的光影包。",
    "联机" to "想和朋友一起玩？去「联机」页面创建房间，分享房间码给好友就能加入！支持局域网和远程联机。",
    "设置" to "在「设置」页面可以调整 Java 参数、内存分配、下载源等。自动内存和自动选择 Java 已经帮你优化好了，通常不需要手动修改。",
    "启动" to "在首页选择你要玩的版本，点击「启动游戏」按钮即可。如果启动失败，检查一下「设置」中的 Java 路径和内存设置。",
    "账户" to "在「账户」页面可以添加 Microsoft 正版账户或离线账户。正版账户可以使用皮肤和进入正版服务器。"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteveChatScreen(
    navController: NavHostController
) {
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        delay(500)
        messages = messages + ChatMessage(
            content = steveGreetings.random(),
            isFromUser = false
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = ChatMessage(content = text.trim(), isFromUser = true)
        messages = messages + userMsg
        inputText = ""
        focusManager.clearFocus()

        isTyping = true
        scope.launch {
            delay(800 + (Math.random() * 1200).toLong())
            val response = generateSteveResponse(text.trim())
            messages = messages + ChatMessage(content = response, isFromUser = false)
            isTyping = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.McBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = VerseColors.McDarkBg,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Steve Avatar
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(VerseColors.McGreen, VerseColors.McGreenDark)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "S",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Steve",
                            color = VerseColors.McTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(VerseColors.McGreen)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (isTyping) "正在输入..." else "在线",
                                color = if (isTyping) VerseColors.McTextMuted else VerseColors.McGreen,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { messages = emptyList() }) {
                        Icon(
                            Icons.Outlined.DeleteSweep,
                            "清空对话",
                            Modifier.size(20.dp),
                            VerseColors.McTextMuted
                        )
                    }
                }
            }

            // Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
                }

                if (isTyping) {
                    item {
                        TypingIndicator()
                    }
                }
            }

            // Quick suggestions
            if (messages.size <= 1) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        Text(
                            "试试问我这些：",
                            color = VerseColors.McTextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    val suggestions = listOf(
                        "如何安装模组？" to Icons.Filled.Extension,
                        "怎么和朋友联机？" to Icons.Filled.Wifi,
                        "推荐什么光影？" to Icons.Filled.Flare,
                        "优化游戏设置" to Icons.Filled.Tune
                    )
                    items(suggestions.size) { idx ->
                        val (text, icon) = suggestions[idx]
                        SuggestionChip(
                            onClick = { sendMessage(text) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(icon, null, Modifier.size(14.dp), VerseColors.McTextSecondary)
                                    Spacer(Modifier.width(6.dp))
                                    Text(text, fontSize = 12.sp)
                                }
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = VerseColors.McCardBg,
                                labelColor = VerseColors.McTextSecondary
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                borderColor = VerseColors.McCardBorder,
                                enabled = true
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Input bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = VerseColors.McDarkBg,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(VerseColors.McCardBg)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "问 Steve 任何 Minecraft 问题...",
                                color = VerseColors.McTextDim,
                                fontSize = 13.sp
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = VerseColors.McTextPrimary,
                            fontSize = 13.sp
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = VerseColors.McGreen
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = { sendMessage(inputText) }
                        ),
                        maxLines = 4
                    )
                    Spacer(Modifier.width(4.dp))
                    FilledIconButton(
                        onClick = { sendMessage(inputText) },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (inputText.isNotBlank())
                                VerseColors.McGreen else VerseColors.McCardBorder,
                            contentColor = Color.White
                        ),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Filled.Send,
                            "发送",
                            Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val bubbleColor = if (message.isFromUser)
        VerseColors.McGreen else VerseColors.McCardBg
    val contentColor = if (message.isFromUser)
        Color.White else VerseColors.McTextPrimary
    val alignment = if (message.isFromUser)
        Alignment.End else Alignment.Start
    val shape = if (message.isFromUser)
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    else
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (!message.isFromUser) {
            Row(
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(VerseColors.McGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("S", color = VerseColors.McGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                Text("Steve", color = VerseColors.McTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = shape,
            color = bubbleColor,
            shadowElevation = if (message.isFromUser) 2.dp else 0.dp
        ) {
            Text(
                message.content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = contentColor,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        if (message.isFromUser) {
            Text(
                "你",
                color = VerseColors.McTextDim,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier
            .padding(start = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(VerseColors.McGreen.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text("S", color = VerseColors.McGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(6.dp))
        Text("Steve", color = VerseColors.McTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
    Surface(
        shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
        color = VerseColors.McCardBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { idx ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(VerseColors.McTextMuted)
                )
            }
        }
    }
}

private fun generateSteveResponse(userInput: String): String {
    val input = userInput.lowercase()

    steveHelpResponses.forEach { (keyword, response) ->
        if (input.contains(keyword)) return response
    }

    if (input.contains("你好") || input.contains("嗨") || input.contains("hello") || input.contains("hi")) {
        return "嘿！很高兴见到你！我是 Steve，有什么 Minecraft 相关的问题尽管问我！⛏"
    }

    if (input.contains("谢谢") || input.contains("感谢") || input.contains("thank")) {
        return "不客气！随时找我帮忙。记得带上你的钻石剑去冒险！💎"
    }

    if (input.contains("好玩") || input.contains("有趣") || input.contains("推荐")) {
        return "我推荐试试最新的模组包！或者挑战一下极限模式，非常刺激。如果你喜欢建筑，创造模式也很有趣！"
    }

    if (input.contains("卡") || input.contains("慢") || input.contains("优化") || input.contains("性能")) {
        return "游戏卡顿可以试试：1. 降低渲染距离 2. 安装 OptiFine 或 Sodium 3. 在设置中增加内存分配 4. 关闭不必要的后台程序。需要我帮你调整吗？"
    }

    return "这是个好问题！不过我需要更多信息才能帮你。你可以问我关于版本下载、模组安装、联机设置、Java 配置或者游戏技巧之类的问题！"
}
