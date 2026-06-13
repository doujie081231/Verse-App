package com.verse.explorer.app.ui.game

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verse.explorer.controller.data.*
import com.verse.explorer.controller.event.EventHandler
import com.verse.explorer.controller.layout.ControlLayerView
import com.verse.explorer.launcher.renderer.RendererManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    rendererManager: RendererManager,
    controlLayout: ControlLayout,
    eventHandler: EventHandler?,
    onExit: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showVirtualKeys by remember { mutableStateOf(true) }
    var keyOpacity by remember { mutableStateOf(0.7f) }
    var isGrabbing by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        controlLayout.layers.forEachIndexed { index, layer ->
            if (showVirtualKeys) {
                ControlLayerView(
                    layer = layer,
                    isGrabbing = isGrabbing,
                    eventHandler = eventHandler,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SmallActionButton(
                icon = Icons.Default.Mouse,
                onClick = { isGrabbing = !isGrabbing },
                active = isGrabbing
            )
            SmallActionButton(
                icon = Icons.Default.Keyboard,
                onClick = { showVirtualKeys = !showVirtualKeys },
                active = showVirtualKeys
            )
            SmallActionButton(
                icon = Icons.Default.Menu,
                onClick = { showMenu = true },
                active = false
            )
        }

        if (showMenu) {
            GameMenuDialog(
                onDismiss = { showMenu = false },
                onExit = onExit,
                keyOpacity = keyOpacity,
                onKeyOpacityChange = { keyOpacity = it }
            )
        }
    }
}

@Composable
fun SmallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    active: Boolean
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary 
                             else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.onPrimary 
                   else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameMenuDialog(
    onDismiss: () -> Unit,
    onExit: () -> Unit,
    keyOpacity: Float,
    onKeyOpacityChange: (Float) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("游戏菜单") },
        text = {
            Column {
                Text(
                    text = "虚拟按键透明度",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = keyOpacity,
                    onValueChange = onKeyOpacityChange,
                    valueRange = 0.3f..1f
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {  }) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("设置")
                    }
                    OutlinedButton(onClick = {  }) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("按键映射")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("退出游戏")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("继续游戏")
            }
        }
    )
}


