package com.verse.explorer.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.mio.controlconverter.CcConverter
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.PcCard
import com.verse.explorer.app.ui.components.pcTextFieldColors
import com.verse.explorer.app.ui.theme.VerseColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 *   删除（Default 出厂布局保护）、SAF 导入；
 *   名称搜索，按选中源（GitHub 直连 / 国内镜像）拉取 version.json 详情并下载
 *   versions/<versionCode>.json 到本地布局目录。
 */


private data class RepoIndexItem(
    val id: String = "",
    val lang: String = "all",
    val name: String = "",
    val introduction: String = "",
    val device: List<Int> = emptyList(),
    val categories: List<Int> = emptyList()
)

private data class RepoCategoryLang(val locale: String = "", val text: String = "")

private data class RepoCategory(val id: Int = 0, val lang: List<RepoCategoryLang> = emptyList()) {
    /** 中文优先显示文本（回退英文/首项） */
    fun displayName(): String =
        lang.firstOrNull { it.locale.startsWith("zh") }?.text
            ?: lang.firstOrNull { it.locale.startsWith("en") }?.text
            ?: lang.firstOrNull()?.text
            ?: id.toString()
}

private data class RepoVersionInfo(val versionCode: Int = 0, val versionName: String = "")

private data class RepoVersion(
    val screenshot: Int = 0,
    val description: String = "",
    val author: String = "",
    val latest: RepoVersionInfo = RepoVersionInfo(),
    val history: List<RepoVersionInfo> = emptyList()
)

private const val REPO_GITHUB = "https://raw.githubusercontent.com/FCL-Team/FCL-Controllers/main/"
private const val REPO_MIRROR = "https://repo.miawa.cn/fcl_controllers/"

private val repoHttp: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

private val repoGson = Gson()

/** 文件名安全化（布局名称可直接用作文件名，特殊字符替换为下划线） */
private fun sanitizeFileName(name: String): String =
    name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "layout" }

private fun httpGetString(url: String): String? = try {
    repoHttp.newCall(Request.Builder().url(url).header("User-Agent", "VerseExplorerX/1.0").build())
        .execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
} catch (e: Exception) {
    com.verse.explorer.app.ui.VerseLogger.warn("请求失败 $url: ${e.javaClass.simpleName}: ${e.message}", "ControlRepo")
    null
}

@Composable
fun ControlLayoutsContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ===== 本地布局状态 =====
    var localLayouts by remember { mutableStateOf<List<File>>(emptyList()) }
    var defaultCtrlPath by remember { mutableStateOf(LauncherPreferences.PREF_DEFAULTCTRL_PATH ?: "") }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var detailTarget by remember { mutableStateOf<File?>(null) }

    fun refreshLocal() {
        // CTRLMAP_PATH 与 LauncherPreferences 静态字段都由各自 init 初始化；直接进入本页时
        // （未经启动设置/游戏/编辑器）两者均未初始化——路径为空串导致列表为空，
        // DEFAULT_PREF 为 null 导致"设为默认"写入时 NPE 崩溃
        net.kdt.pojavlaunch.Tools.init(context)
        LauncherPreferences.init(context)
        localLayouts = try {
            File(Tools.CTRLMAP_PATH).listFiles { f -> f.isFile && f.extension == "json" }
                ?.sortedBy { it.nameWithoutExtension.lowercase(Locale.ROOT) }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        defaultCtrlPath = LauncherPreferences.PREF_DEFAULTCTRL_PATH ?: ""
    }

    LaunchedEffect(Unit) { refreshLocal() }

    // SAF 导入布局 json
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val display = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                }
                val base = (display ?: "imported").removeSuffix(".json")
                val dest = File(Tools.CTRLMAP_PATH, sanitizeFileName(base) + ".json")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: error("无法读取所选文件")
                // 导入后校验是合法布局 json，避免坏文件进列表
                val raw = dest.readText()
                repoGson.fromJson(raw, Map::class.java) ?: error("不是有效的布局文件")
                // ZL2 布局（layers + editorVersion）：导入时就转成 FCL 仓库格式落盘，
                // 而不是等游戏启动时惰性转换——列表里看到的就是可用格式，
                // 转换失败则删掉文件，避免留下一个「点开就报错」的布局。
                val converted = runCatching {
                    if (!CcConverter.isZl2Layout(raw)) {
                        false
                    } else {
                        val fcl = CcConverter.convertZlToFcl(
                            JsonParser.parseString(raw).asJsonObject,
                            stripMeta = true
                        )
                        dest.writeText(fcl.toString())
                        true
                    }
                }.getOrElse { error ->
                    dest.delete()
                    throw IllegalStateException("ZL2 布局转换失败：${error.message ?: "格式不受支持"}")
                }
                dest to converted
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { (file, converted) ->
                    ToastCompat.show(
                        context,
                        if (converted) "已从 ZL2 格式转换并导入 ${file.nameWithoutExtension}"
                        else "已导入 ${file.nameWithoutExtension}"
                    )
                }.onFailure { ToastCompat.show(context, "导入失败：${it.message}") }
                refreshLocal()
            }
        }
    }

    // ===== 布局仓库状态 =====
    // 默认走国内镜像（repo.miawa.cn，实测速度远快于 GitHub raw）；GitHub 源作为备选
    var sourceMirror by remember { mutableStateOf(true) }
    var indexes by remember { mutableStateOf<List<RepoIndexItem>>(emptyList()) }
    var categories by remember { mutableStateOf<List<RepoCategory>>(emptyList()) }
    var selectedCategory by remember { mutableIntStateOf(0) }
    var searchText by remember { mutableStateOf("") }
    var repoLoading by remember { mutableStateOf(false) }
    var repoError by remember { mutableStateOf<String?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var versionCache by remember { mutableStateOf<Map<String, RepoVersion>>(emptyMap()) }
    var versionLoadingId by remember { mutableStateOf<String?>(null) }
    var versionLoadKey by remember { mutableIntStateOf(0) }
    var downloadingId by remember { mutableStateOf<String?>(null) }

    fun repoHead(): String = if (sourceMirror) REPO_MIRROR else REPO_GITHUB

    fun loadRepo(force: Boolean, autoFallback: Boolean = true) {
        if (repoLoading) return
        if (!force && indexes.isNotEmpty()) return
        repoLoading = true
        repoError = null
        scope.launch(Dispatchers.IO) {
            val head = repoHead()
            val indexResult = httpGetString(head + "index.json")
            val categoryResult = httpGetString(head + "category.json")
            val newIndex = indexResult?.let {
                runCatching {
                    repoGson.fromJson<List<RepoIndexItem>>(
                        it,
                        object : TypeToken<List<RepoIndexItem>>() {}.type
                    )
                }.getOrNull()
            }
            val newCategories = categoryResult?.let {
                runCatching {
                    repoGson.fromJson<List<RepoCategory>>(
                        it,
                        object : TypeToken<List<RepoCategory>>() {}.type
                    )
                }.getOrNull()
            }
            withContext(Dispatchers.Main) {
                repoLoading = false
                if (newIndex == null) {
                    if (autoFallback) {
                        // 当前源加载失败：自动切到另一个源重试一次
                        sourceMirror = !sourceMirror
                        loadRepo(force = true, autoFallback = false)
                    } else {
                        repoError = "布局仓库加载失败（两个源均不可达），请检查网络后重试"
                    }
                } else {
                    indexes = newIndex
                    categories = newCategories ?: emptyList()
                }
            }
        }
    }

    // 下载布局 json（版本号与文件均双源尝试：当前源失败自动切另一源，临时文件落盘后原子替换）
    fun downloadLayout(item: RepoIndexItem) {
        if (downloadingId != null) return
        downloadingId = item.id
        scope.launch(Dispatchers.IO) {
            val heads = listOf(repoHead(), if (sourceMirror) REPO_GITHUB else REPO_MIRROR)
            val result = runCatching {
                var code: Int? = null
                var body: String? = null
                for (head in heads) {
                    val v = httpGetString(head + "repo_json/" + item.id + "/version.json")
                        ?.let { runCatching { repoGson.fromJson(it, RepoVersion::class.java) }.getOrNull() }
                    code = v?.latest?.versionCode
                    if (code != null) {
                        body = httpGetString(head + "repo_json/" + item.id + "/versions/" + code + ".json")
                        if (body != null) break
                    }
                }
                if (code == null) error("获取版本信息失败")
                body ?: error("下载布局文件失败")
                // 校验为合法布局 json 后落盘
                repoGson.fromJson(body, Map::class.java) ?: error("布局文件内容无效")
                val dest = File(Tools.CTRLMAP_PATH, sanitizeFileName(item.name) + ".json")
                val tmp = File(Tools.CTRLMAP_PATH, dest.name + ".tmp")
                tmp.writeText(body)
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) error("写入布局文件失败")
                dest
            }
            withContext(Dispatchers.Main) {
                downloadingId = null
                result.onSuccess { ToastCompat.show(context, "已下载「${item.name}」") }
                    .onFailure { ToastCompat.show(context, "下载失败：${it.message}") }
                refreshLocal()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshLocal()
        loadRepo(force = false)
    }

    val filtered = remember(indexes, searchText, selectedCategory) {
        val q = searchText.trim().lowercase(Locale.ROOT)
        indexes.filter { item ->
            (selectedCategory == 0 || item.categories.contains(selectedCategory)) &&
                (q.isEmpty() || item.name.lowercase(Locale.ROOT).contains(q) ||
                    item.introduction.lowercase(Locale.ROOT).contains(q))
        }
    }

    // 外层是 SettingsScreen 的 LazyColumn（滚动容器）：此处不能再套 verticalScroll，
    // 否则 LazyColumn 的无限高度约束会让嵌套滚动容器测量崩溃
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===== 本地布局 =====
        PcCard(title = "本地布局") {
            if (localLayouts.isEmpty()) {
                Text(
                    "暂无布局文件，可从下方布局仓库下载或导入",
                    fontSize = 12.sp,
                    color = VerseColors.PcTextMuted
                )
            } else {
                localLayouts.forEach { file ->
                    val isDefault = file.absolutePath == defaultCtrlPath ||
                        file.name == "Default.json" && defaultCtrlPath.isBlank()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                file.nameWithoutExtension,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = VerseColors.PcTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (isDefault) "默认布局" else "${file.length() / 1024} KB",
                                fontSize = 11.sp,
                                color = if (isDefault) VerseColors.PcAccent else VerseColors.PcTextMuted
                            )
                        }
                        Text(
                            "编辑",
                            fontSize = 12.sp,
                            color = VerseColors.PcAccent,
                            modifier = Modifier
                                .clickable {
                                    com.verse.explorer.app.ui.game.LayoutEditorActivity.start(context, file.absolutePath)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        Text(
                            "详情",
                            fontSize = 12.sp,
                            color = VerseColors.PcTextSecondary,
                            modifier = Modifier
                                .clickable { detailTarget = file }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        Text(
                            "设为默认",
                            fontSize = 12.sp,
                            color = if (isDefault) VerseColors.PcTextMuted else VerseColors.PcAccent,
                            modifier = Modifier
                                .clickable(enabled = !isDefault) {
                                    LauncherPreferences.DEFAULT_PREF.edit()
                                        .putString("defaultCtrl", file.absolutePath).apply()
                                    LauncherPreferences.PREF_DEFAULTCTRL_PATH = file.absolutePath
                                    refreshLocal()
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        if (file.name != "Default.json") {
                            Text(
                                "删除",
                                fontSize = 12.sp,
                                color = VerseColors.PcRed,
                                modifier = Modifier
                                    .clickable { deleteTarget = file }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = VerseColors.PcBorder, thickness = 1.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            PcButton(
                text = "导入布局文件",
                onClick = { importLauncher.launch("application/json") },
                variant = PcButtonVariant.OUTLINE
            )
        }

        // ===== 布局仓库（下载） =====
        PcCard(title = "布局仓库") {
            // 源切换（GitHub 直连 / 镜像加速）
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(false to "GitHub 源", true to "镜像加速").forEach { (mirror, label) ->
                    val active = sourceMirror == mirror
                    SourceChip(label, active) {
                        if (sourceMirror != mirror) {
                            sourceMirror = mirror
                            indexes = emptyList()
                            categories = emptyList()
                            expandedId = null
                            loadRepo(force = true)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("搜索布局名称或简介", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = pcTextFieldColors()
            )
            // 分类 chips（category.json 的中文文本，0 = 全部）
            if (categories.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SourceChip("全部", selectedCategory == 0) { selectedCategory = 0 }
                    categories.chunked(4).forEach { rowCats ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowCats.forEach { cat ->
                                SourceChip(cat.displayName(), selectedCategory == cat.id) {
                                    selectedCategory = if (selectedCategory == cat.id) 0 else cat.id
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            when {
                repoLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoseLoader(
                            modifier = Modifier.size(16.dp),
                            size = 16.dp,
                            color = VerseColors.PcAccent
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("正在加载布局仓库…", fontSize = 12.sp, color = VerseColors.PcTextSecondary)
                    }
                }
                repoError != null -> {
                    Column {
                        Text(repoError ?: "", fontSize = 12.sp, color = VerseColors.PcRed)
                        Spacer(Modifier.height(6.dp))
                        PcButton(
                            text = "重试",
                            onClick = { loadRepo(force = true) },
                            variant = PcButtonVariant.OUTLINE
                        )
                    }
                }
                indexes.isEmpty() -> {
                    Text(
                        "进入页面自动加载，或选择源后手动刷新",
                        fontSize = 12.sp,
                        color = VerseColors.PcTextMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    PcButton(
                        text = "加载布局列表",
                        onClick = { loadRepo(force = true) },
                        variant = PcButtonVariant.OUTLINE
                    )
                }
                else -> {
                    if (filtered.isEmpty()) {
                        Text("没有匹配的布局", fontSize = 12.sp, color = VerseColors.PcTextMuted)
                    }
                    filtered.forEach { item ->
                        val expanded = expandedId == item.id
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedId = if (expanded) null else item.id }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = VerseColors.PcTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.introduction,
                                        fontSize = 11.sp,
                                        color = VerseColors.PcTextMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 15.sp
                                    )
                                }
                                Icon(
                                    if (expanded) Icons.Filled.KeyboardArrowDown
                                    else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = VerseColors.PcTextMuted
                                )
                            }
                            // 详情加载必须放在条目常驻组合内：若放进分支内部，
                            // 加载态切换分支时协程会被取消，导致永远停在"加载详情"
                            if (expanded) {
                                LaunchedEffect(item.id, sourceMirror, versionLoadKey) {
                                    if (versionCache[item.id] == null) {
                                        versionLoadingId = item.id
                                        val v = withContext(Dispatchers.IO) {
                                            // 当前源失败自动切另一源（与列表加载的双源互备一致）
                                            val heads = listOf(repoHead(), if (sourceMirror) REPO_GITHUB else REPO_MIRROR)
                                            var result: RepoVersion? = null
                                            for (head in heads) {
                                                result = httpGetString(head + "repo_json/" + item.id + "/version.json")
                                                    ?.let {
                                                        runCatching {
                                                            repoGson.fromJson(it, RepoVersion::class.java)
                                                        }.getOrNull()
                                                    }
                                                if (result != null) break
                                            }
                                            result
                                        }
                                        if (v != null) versionCache = versionCache + (item.id to v)
                                        versionLoadingId = null
                                    }
                                }
                                val cached = versionCache[item.id]
                                when {
                                    versionLoadingId == item.id -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RoseLoader(
                                                modifier = Modifier.size(14.dp),
                                                size = 14.dp,
                                                color = VerseColors.PcAccent
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("加载详情…", fontSize = 11.sp, color = VerseColors.PcTextMuted)
                                        }
                                    }
                                    cached != null -> {
                                        DetailLine("作者", cached.author)
                                        DetailLine("最新版本", cached.latest.versionName)
                                        DetailLine("适用设备", item.device.joinToString("、") { d ->
                                            when (d) {
                                                0 -> "手机"; 1 -> "平板"; else -> "其他"
                                            }
                                        })
                                        if (cached.description.isNotBlank()) {
                                            DetailLine("描述", cached.description)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        PcButton(
                                            text = if (downloadingId == item.id) "下载中…" else "下载最新版",
                                            onClick = { downloadLayout(item) },
                                            variant = PcButtonVariant.PRIMARY
                                        )
                                    }
                                    else -> {
                                        Text(
                                            "详情加载失败，点击重试",
                                            fontSize = 11.sp,
                                            color = VerseColors.PcRed,
                                            modifier = Modifier.clickable { versionLoadKey++ }
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = VerseColors.PcBorder, thickness = 1.dp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    // 布局详情（名称/大小/控件数/是否默认）
    detailTarget?.let { target ->
        val detail = remember(target.absolutePath) {
            runCatching {
                val json = repoGson.fromJson(target.readText(), Map::class.java)
                val buttons = (json?.get("mControlDataList") as? List<*>)?.size ?: 0
                val drawers = (json?.get("mDrawerDataList") as? List<*>)?.size ?: 0
                val joysticks = (json?.get("mJoystickDataList") as? List<*>)?.size ?: 0
                Triple(buttons, drawers, joysticks)
            }.getOrElse { Triple(0, 0, 0) }
        }
        AlertDialog(
            onDismissRequest = { detailTarget = null },
            title = {
                Text(
                    target.nameWithoutExtension,
                    color = VerseColors.PcTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    DetailLine("控件数量", "${detail.first}")
                    DetailLine("抽屉控件", "${detail.second}")
                    DetailLine("方向控制键", "${detail.third}")
                    DetailLine("文件大小", "${target.length() / 1024} KB")
                    DetailLine(
                        "默认布局",
                        if (target.absolutePath == defaultCtrlPath || (target.name == "Default.json" && defaultCtrlPath.isBlank()))
                            "是" else "否"
                    )
                    DetailLine("文件路径", target.absolutePath)
                }
            },
            confirmButton = {
                TextButton(onClick = { detailTarget = null }) {
                    Text("关闭", color = VerseColors.PcAccent)
                }
            },
            containerColor = VerseColors.PcBgSecondary
        )
    }

    // 删除确认
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除布局", color = VerseColors.PcTextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "确定删除布局「${target.nameWithoutExtension}」？删除后无法恢复。",
                    fontSize = 12.sp,
                    color = VerseColors.PcTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    target.delete()
                    deleteTarget = null
                    refreshLocal()
                }) { Text("删除", color = VerseColors.PcRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消", color = VerseColors.PcTextSecondary)
                }
            },
            containerColor = VerseColors.PcBgSecondary
        )
    }
}

@Composable
private fun SourceChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (active) VerseColors.PcAccent.copy(alpha = 0.12f) else VerseColors.PcBgTertiary,
        border = if (active) BorderStroke(1.dp, VerseColors.PcAccent) else null,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = if (active) VerseColors.PcAccent else VerseColors.PcTextSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun DetailLine(key: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            key,
            fontSize = 11.sp,
            color = VerseColors.PcTextMuted,
            modifier = Modifier.width(56.dp)
        )
        Text(
            value,
            fontSize = 11.sp,
            color = VerseColors.PcTextSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Compose 内简单 Toast 助手（避免在回调里重复写 main looper 判断） */
private object ToastCompat {
    fun show(context: android.content.Context, msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
