package com.verse.explorer.app.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.theme.VerseColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * ???:?? / ?? / ?? ????????????
 * ???????????????(??????????);
 * ???????? / ???????
 */
@Composable
fun TransferScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("verse_transfer", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var activatedKey by remember { mutableStateOf(prefs.getString("activation_key", null)) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text("??", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = VerseColors.PcTextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "?? / ?? / ??,???????????",
            fontSize = 13.sp,
            color = VerseColors.PcTextSecondary
        )
        Spacer(Modifier.height(18.dp))

        if (activatedKey == null) {
            // ===== ???? =====
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = VerseColors.PcGlassCardBg
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("??????", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VerseColors.PcTextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "??? verselauncher.cn ???????????????,????????",
                        fontSize = 13.sp,
                        color = VerseColors.PcTextSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("???") },
                        placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
                        singleLine = true,
                        enabled = !loading
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (input.isNotBlank() && !loading) {
                                loading = true
                                msg = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        verifyKey(context, input.trim())
                                    }
                                    loading = false
                                    if (result.first) {
                                        val key = result.second
                                        prefs.edit().putString("activation_key", key).apply()
                                        activatedKey = key
                                        msg = true to "????"
                                    } else {
                                        msg = false to (result.second)
                                    }
                                }
                            }
                        },
                        enabled = !loading && input.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VerseColors.PcAccent)
                    ) {
                        Text(if (loading) "???." else "??", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    msg?.let { (ok, text) ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text,
                            fontSize = 13.sp,
                            color = if (ok) VerseColors.PcGreen else VerseColors.PcRed,
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
        } else {
            // ===== ???:?? / ?? =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = VerseColors.PcGreen,
                    modifier = Modifier.size(18.dp)
                )
                Text("???", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VerseColors.PcGreen)
            }
            Spacer(Modifier.height(14.dp))

            TransferModeCard(
                icon = Icons.Outlined.UploadFile,
                title = "????",
                desc = "???????? / ?? / ??,???????????"
            )
            Spacer(Modifier.height(12.dp))
            TransferModeCard(
                icon = Icons.Outlined.Download,
                title = "????",
                desc = "????????,???????????"
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "??????????,?????????",
                fontSize = 12.sp,
                color = VerseColors.PcTextMuted
            )
        }
    }
}

@Composable
private fun TransferModeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = VerseColors.PcGlassCardBg
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VerseColors.PcAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = VerseColors.PcAccent, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = VerseColors.PcTextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(desc, fontSize = 12.sp, color = VerseColors.PcTextSecondary)
            }
        }
    }
}

/**
 * ???????????????
 * ?? (????, ????????? / ????????)
 */
private fun verifyKey(context: Context, rawKey: String): Pair<Boolean, String> {
    var conn: HttpURLConnection? = null
    return try {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown-device"
        val url = URL("https://www.verselauncher.cn/api/afdian/key-verify")
        conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        val body = JSONObject().apply {
            put("key", rawKey)
            put("device_id", deviceId)
        }.toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.readText() ?: ""
        val json = JSONObject(text)
        if (code == 200 && json.optBoolean("valid", false)) {
            val normalized = rawKey.replace("-", "").replace(" ", "").uppercase()
            Pair(true, normalized)
        } else {
            Pair(false, json.optString("error", "????(HTTP $code)"))
        }
    } catch (e: Exception) {
        Pair(false, "????:${e.message ?: "????????"}")
    } finally {
        conn?.disconnect()
    }
}
