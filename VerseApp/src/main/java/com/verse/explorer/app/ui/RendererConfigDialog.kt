package com.verse.explorer.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.PcDropdownRow
import com.verse.explorer.app.ui.components.PcSwitchRow
import com.verse.explorer.app.ui.components.PcTextInputRow
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.launcher.renderer.config.ConfigFieldKind
import com.verse.explorer.launcher.renderer.config.RendererConfigField
import com.verse.explorer.launcher.renderer.config.RendererConfigResolver
import com.verse.explorer.launcher.renderer.config.RendererConfigSpecs
import com.verse.explorer.launcher.renderer.config.RendererConfigStore

/**
 * 渲染器参数配置弹窗。
 *
 * 三类控件对应三种字段（开关 / 枚举 / 整数），改动即时落盘：
 * 关闭弹窗即生效，不额外提供"保存"按钮，避免用户改完忘了点。
 *
 * 作用域：默认写全局；打开"仅本版本"后写版本级存储，该版本启动时优先于全局。
 */
@Composable
fun RendererConfigDialog(
    rendererId: String,
    rendererName: String,
    versionId: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val spec = remember(rendererId) { RendererConfigSpecs.of(rendererId) }
    val store = remember(rendererId) { RendererConfigStore(context) }

    if (spec == null) {
        onDismiss()
        return
    }

    var versionScoped by remember {
        mutableStateOf(versionId != null && store.hasVersion(versionId, spec.rendererId))
    }
    var values by remember {
        mutableStateOf(
            RendererConfigResolver.resolve(
                spec,
                store.loadGlobal(spec.rendererId, spec),
                versionId?.let { store.loadVersion(it, spec.rendererId, spec) } ?: emptyMap()
            )
        )
    }

    fun persist(next: Map<String, Any>, scoped: Boolean) {
        if (scoped && versionId != null) {
            store.saveVersion(versionId, spec.rendererId, spec, next)
        } else {
            store.saveGlobal(spec.rendererId, spec, next)
        }
    }

    fun update(field: RendererConfigField, value: Any) {
        val next = LinkedHashMap(values).apply { put(field.id, value) }
        values = next
        persist(next, versionScoped)
    }

    fun resetCurrentScope() {
        if (versionScoped && versionId != null) {
            store.clearVersion(versionId, spec.rendererId)
        } else {
            store.clearGlobal(spec.rendererId)
            versionId?.let { store.clearVersion(it, spec.rendererId) }
        }
        values = RendererConfigResolver.resolve(
            spec,
            store.loadGlobal(spec.rendererId, spec),
            versionId?.let { store.loadVersion(it, spec.rendererId, spec) } ?: emptyMap()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VerseColors.PcBgCard,
        titleContentColor = VerseColors.PcTextPrimary,
        textContentColor = VerseColors.PcTextSecondary,
        shape = RoundedCornerShape(12.dp),
        title = {
            Column {
                Text(
                    "$rendererName 配置",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerseColors.PcTextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "改完直接生效，下次启动游戏时应用",
                    fontSize = 12.sp,
                    color = VerseColors.PcTextMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (versionId != null) {
                    PcSwitchRow(
                        "仅对当前版本生效",
                        "开启后这套参数只作用于这个版本，其它版本继续用全局设置",
                        versionScoped
                    ) { checked ->
                        versionScoped = checked
                        // 切换作用域时把当前值写到新的落点，避免界面上看到的和存下去的不一致
                        persist(values, checked)
                        if (!checked) store.clearVersion(versionId, spec.rendererId)
                    }
                    Spacer(Modifier.height(4.dp))
                }

                spec.fields.groupBy { it.group.ifBlank { "通用" } }.forEach { (group, fields) ->
                    Text(
                        group,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VerseColors.PcAccent,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                    fields.forEach { field ->
                        when (field.kind) {
                            ConfigFieldKind.SWITCH -> PcSwitchRow(
                                field.title,
                                field.description,
                                values[field.id] as? Boolean ?: false
                            ) { update(field, it) }

                            ConfigFieldKind.CHOICE -> PcDropdownRow(
                                field.title,
                                (values[field.id] as? Int ?: 0).toString(),
                                field.options.map { it.value.toString() to it.label }
                            ) { selected ->
                                selected.toIntOrNull()?.let { update(field, it) }
                            }

                            ConfigFieldKind.NUMBER -> PcTextInputRow(
                                field.title,
                                (values[field.id] as? Int ?: 0).toString(),
                                field.range?.let { "${it.first} ~ ${it.last}" } ?: ""
                            ) { raw ->
                                raw.toIntOrNull()?.let { update(field, it) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            PcButton("完成", onClick = onDismiss, variant = PcButtonVariant.PRIMARY)
        },
        dismissButton = {
            Row {
                PcButton("恢复默认", onClick = { resetCurrentScope() }, variant = PcButtonVariant.GHOST)
                Spacer(Modifier.width(8.dp))
            }
        }
    )
}
