package com.verse.explorer.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.theme.VerseColors

@Composable
fun VerseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    singleLine: Boolean = true,
    maxLines: Int = 1,
    minLines: Int = 1,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    onFocusChange: ((Boolean) -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> VerseColors.McRedstone
            isFocused -> VerseColors.McGreen
            else -> VerseColors.McCardBorder
        },
        animationSpec = tween(200),
        label = "borderColor"
    )

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) VerseColors.McRedstone else VerseColors.McTextSecondary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .border(1.dp, borderColor, RoundedCornerShape(3.dp)),
            shape = RoundedCornerShape(3.dp),
            color = if (enabled) VerseColors.McCardBg else VerseColors.McCardBgLight
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = if (isFocused) VerseColors.McGreen else VerseColors.McTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
                            onFocusChange?.invoke(focusState.isFocused)
                        },
                    enabled = enabled,
                    readOnly = readOnly,
                    textStyle = textStyle.copy(color = if (enabled) VerseColors.McTextPrimary else VerseColors.McTextDim),
                    singleLine = singleLine,
                    maxLines = maxLines,
                    minLines = minLines,
                    cursorBrush = SolidColor(VerseColors.McGreen),
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onImeAction() },
                        onNext = { onImeAction() },
                        onSearch = { onImeAction() }
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = hint ?: placeholder ?: "",
                                    style = textStyle,
                                    color = VerseColors.McTextMuted
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                if (trailingIcon != null) {
                    Spacer(Modifier.width(8.dp))
                    trailingIcon()
                }
            }
        }

        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = VerseColors.McRedstone,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}

@Composable
fun VerseSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索...",
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) VerseColors.McGreen else VerseColors.McCardBorder,
        animationSpec = tween(200),
        label = "borderColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        color = if (enabled) VerseColors.McCardBg else VerseColors.McCardBgLight
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = if (isFocused) VerseColors.McGreen else VerseColors.McTextMuted,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(8.dp))

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .onFocusChanged { isFocused = it.isFocused },
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = VerseColors.McTextPrimary),
                singleLine = true,
                cursorBrush = SolidColor(VerseColors.McGreen),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = VerseColors.McTextMuted
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (value.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "清除",
                    tint = VerseColors.McTextMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable {
                            onValueChange("")
                        }
                )
            }
        }
    }
}

@Composable
fun VersePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {}
) {
    var passwordVisible by remember { mutableStateOf(false) }

    VerseTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        hint = hint,
        enabled = enabled,
        isError = isError,
        errorMessage = errorMessage,
        keyboardType = KeyboardType.Password,
        imeAction = imeAction,
        onImeAction = onImeAction,
        trailingIcon = {
            Icon(
                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                tint = VerseColors.McTextMuted,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { passwordVisible = !passwordVisible }
            )
        },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
    )
}

@Composable
fun VerseChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    dismissible: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> VerseColors.McCardBgLight
            selected -> VerseColors.McGreen
            else -> VerseColors.McCardBg
        },
        animationSpec = tween(150),
        label = "backgroundColor"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            !enabled -> VerseColors.McTextDim
            selected -> Color.White
            else -> VerseColors.McTextPrimary
        },
        animationSpec = tween(150),
        label = "textColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> VerseColors.McCardBorder
            selected -> VerseColors.McGreen
            else -> VerseColors.McCardBorder
        },
        animationSpec = tween(150),
        label = "borderColor"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier
            ),
        shape = RoundedCornerShape(2.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
            }

            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Medium
            )

            if (dismissible && onDismiss != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "移除",
                    tint = textColor,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onDismiss() }
                )
            }
        }
    }
}

@Composable
fun VerseChipGroup(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            VerseChip(
                text = option,
                selected = option == selectedOption,
                onClick = { onOptionSelected(option) },
                enabled = enabled
            )
        }
    }
}

@Composable
fun VerseSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(3.dp),
        color = VerseColors.McCardBgLight
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) VerseColors.McCardBg else Color.Transparent,
                    animationSpec = tween(150),
                    label = "backgroundColor"
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(2.dp))
                        .clickable { onOptionSelected(index) },
                    shape = RoundedCornerShape(2.dp),
                    color = backgroundColor,
                    shadowElevation = if (isSelected) 1.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) VerseColors.McTextPrimary else VerseColors.McTextSecondary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
