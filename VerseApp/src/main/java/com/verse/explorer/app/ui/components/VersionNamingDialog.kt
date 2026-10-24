package com.verse.explorer.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.verse.explorer.app.ui.theme.VerseColors

/**
 *
 *    （translatable=false，中英文均为 Fabric / Forge / NeoForge / OptiFine / Quilt）。
 *    本弹窗打开时加载器已定死，故仅以自动命名初始化一次，用户可自由手改。）
 *    - 空输入 → "请先填写必要的输入框。"
 *    - 与已安装版本冲突 → "当前版本已存在，请换一个名称。"
 *    弹 Toast 提示，不关闭弹窗。
 */
@Composable
fun VersionNamingDialog(
    gameVersion: String,
    selectedLoaderId: String?,
    conflictsWith: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    iconUrl: String? = null
) {
    val context = LocalContext.current

    // refreshVersionName 刷新；本弹窗的加载器在选择打开前已确定（弹窗内无刷新源），
    // 故不引入该标志，只在打开瞬间用自动命名初始化一次。
    var name by remember { mutableStateOf(generateVersionName(gameVersion, selectedLoaderId)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VerseColors.PcBgCard,
        titleContentColor = VerseColors.PcTextPrimary,
        textContentColor = VerseColors.PcTextSecondary,
        shape = RoundedCornerShape(12.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text("安装新游戏版本", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                PcTextInput(
                    value = name,
                    onValueChange = { newName -> name = newName },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "默认命名：${generateVersionName(gameVersion, selectedLoaderId)}",
                    fontSize = 11.sp,
                    color = VerseColors.PcTextMuted
                )
            }
        },
        confirmButton = {
            Text(
                "确认下载",
                color = VerseColors.PcAccent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable {
                        val newName = name
                        when {
                            newName.isBlank() ->
                                Toast.makeText(context, "请先填写必要的输入框。", Toast.LENGTH_SHORT).show()
                            conflictsWith(newName) ->
                                Toast.makeText(context, "当前版本已存在，请换一个名称。", Toast.LENGTH_SHORT).show()
                            !isValidVersionId(newName) ->
                                Toast.makeText(context, "无效的名称", Toast.LENGTH_SHORT).show()
                            else -> onConfirm(newName)
                        }
                    }
            )
        },
        dismissButton = {
            Text(
                "取消",
                color = VerseColors.PcTextSecondary,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable(onClick = onDismiss)
            )
        }
    )
}

/**
 * gameVersion 起，按 LibraryAnalyzer.LibraryType.values() 顺序取已选加载器拼接 "-加载器名"。
 * getLoaderName 对未知 LibraryType 返回 null 并被过滤（不拼接）；
 */
private val FCL_LOADER_NAMES = mapOf(
    "fabric" to "Fabric",
    "forge" to "Forge",
    "neoforge" to "NeoForge",
    "optifine" to "OptiFine",
    "quilt" to "Quilt"
)

private fun generateVersionName(gameVersion: String, selectedLoaderId: String?): String {
    if (selectedLoaderId.isNullOrBlank()) return gameVersion
    val loaderName = FCL_LOADER_NAMES[selectedLoaderId] ?: return gameVersion
    return "$gameVersion-$loaderName"
}

/**
 * - 禁用的版本 id（与 modpack 配置文件命名冲突）：modpack / minecraftinstance / manifest
 * - 不允许空、"."、"/"、"\0"
 * - FAT32/exFAT 非法字符：\ / : * ? " < > |
 */
private val FORBIDDEN_VERSION_IDS = setOf("modpack", "minecraftinstance", "manifest")
private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|]")

private fun isValidVersionId(id: String): Boolean {
    if (FORBIDDEN_VERSION_IDS.contains(id)) return false
    if (id.isEmpty()) return false
    if (id == ".") return false
    if (id.contains('/') || id.contains('\u0000')) return false
    return !ILLEGAL_CHARS.containsMatchIn(id)
}