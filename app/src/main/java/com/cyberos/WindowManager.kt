package com.cyberos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private enum class CyberWindowKind(val title: String, val label: String) {
    Terminal("TERMINAL", "TERM"),
    Store("APP STORE", "STORE"),
    Files("FILE MANAGER", "FILES"),
    Scanner("SIGNAL HUD", "SCAN"),
    Vault("CYBER VAULT", "VAULT"),
    Scripts("CYBERSCRIPT", "SCRIPT"),
    Telemetry("SYSTEM MONITOR", "SYS")
}

private data class CyberWindowState(
    val kind: CyberWindowKind,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val minimized: Boolean = false,
    val maximized: Boolean = false,
    val z: Int = 0
)

@Composable
internal fun WindowManagerScreen(
    theme: CyberTheme,
    onCommand: (String) -> Unit,
    onLaunchFirefox: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenScripts: () -> Unit
) {
    val windows = remember {
        mutableStateListOf(
            CyberWindowState(CyberWindowKind.Terminal, 8f, 8f, 278f, 210f, z = 3),
            CyberWindowState(CyberWindowKind.Store, 46f, 110f, 278f, 208f, z = 2),
            CyberWindowState(CyberWindowKind.Telemetry, 18f, 336f, 278f, 152f, z = 1)
        )
    }
    var topZ by remember { mutableIntStateOf(4) }
    val minimized = windows.filter { it.minimized }

    Column(Modifier.fillMaxSize()) {
        Text("DESKTOP // WINDOW MANAGER", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CyberWindowKind.entries.forEach { kind ->
                WindowTool(kind.label, theme) {
                    val existing = windows.indexOfFirst { it.kind == kind }
                    if (existing >= 0) {
                        windows[existing] = windows[existing].copy(minimized = false, z = topZ++)
                    } else {
                        windows += CyberWindowState(kind, 18f + (windows.size % 3) * 22f, 18f + (windows.size % 3) * 26f, 278f, 204f, z = topZ++)
                    }
                }
            }
            WindowTool("TILE", theme) { tileWindows(windows) }
        }
        Spacer(Modifier.height(7.dp))
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .border(1.dp, theme.accent.copy(alpha = 0.38f), RoundedCornerShape(5.dp))
                .background(Color(0xB907120D))
        ) {
            val density = LocalDensity.current
            val availableWidth = maxWidth.value
            val availableHeight = maxHeight.value
            windows.filterNot { it.minimized }.sortedBy { it.z }.forEach { state ->
                val left = if (state.maximized) 0f else state.x.coerceIn(0f, (availableWidth - state.width).coerceAtLeast(0f))
                val top = if (state.maximized) 0f else state.y.coerceIn(0f, (availableHeight - state.height).coerceAtLeast(0f))
                val width = if (state.maximized) availableWidth else state.width.coerceAtMost(availableWidth)
                val height = if (state.maximized) availableHeight else state.height.coerceAtMost(availableHeight)
                val index = windows.indexOfFirst { it.kind == state.kind }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(with(density) { left.dp.roundToPx() }, with(density) { top.dp.roundToPx() }) }
                        .width(width.dp)
                        .height(height.dp)
                        .border(1.dp, theme.accent.copy(alpha = if (state.z == topZ - 1) 0.9f else 0.5f), RoundedCornerShape(4.dp))
                        .background(Color(0xFF0A1711), RoundedCornerShape(4.dp))
                        .clickable {
                            if (index >= 0) windows[index] = windows[index].copy(z = topZ++)
                        }
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(32.dp)
                                .background(theme.accent.copy(alpha = 0.13f))
                                .pointerInput(state.kind) {
                                    detectDragGestures(onDragStart = {
                                        if (index >= 0) windows[index] = windows[index].copy(z = topZ++)
                                    }) { change, dragAmount ->
                                        change.consume()
                                        if (index >= 0 && !windows[index].maximized) {
                                            val xDelta = with(density) { dragAmount.x.toDp().value }
                                            val yDelta = with(density) { dragAmount.y.toDp().value }
                                            windows[index] = windows[index].copy(x = (windows[index].x + xDelta).coerceIn(0f, availableWidth - windows[index].width), y = (windows[index].y + yDelta).coerceIn(0f, availableHeight - windows[index].height))
                                        }
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("  ${state.kind.title}", color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            WindowAction("_", theme) { windows[index] = windows[index].copy(minimized = true) }
                            WindowAction(if (state.maximized) "[]" else "□", theme) {
                                windows[index] = windows[index].copy(maximized = !state.maximized, x = 0f, y = 0f)
                            }
                            WindowAction("×", theme) { windows.removeAll { it.kind == state.kind } }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                            WindowContent(state.kind, theme, onCommand, onLaunchFirefox, onOpenVault, onOpenScripts)
                            if (!state.maximized) {
                                Box(
                                    Modifier.align(Alignment.BottomEnd).size(20.dp)
                                        .pointerInput(state.kind) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                if (index >= 0) {
                                                    val dw = with(density) { dragAmount.x.toDp().value }
                                                    val dh = with(density) { dragAmount.y.toDp().value }
                                                    val current = windows[index]
                                                    windows[index] = current.copy(
                                                        width = (current.width + dw).coerceIn(210f, availableWidth - current.x),
                                                        height = (current.height + dh).coerceIn(120f, availableHeight - current.y)
                                                    )
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    Text("◢", color = theme.accent, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (minimized.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                minimized.forEach { state -> WindowTool("RESTORE ${state.kind.label}", theme) {
                    val index = windows.indexOfFirst { it.kind == state.kind }
                    if (index >= 0) windows[index] = windows[index].copy(minimized = false, z = topZ++)
                } }
            }
        }
    }
}

@Composable
private fun WindowContent(
    kind: CyberWindowKind,
    theme: CyberTheme,
    onCommand: (String) -> Unit,
    onLaunchFirefox: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenScripts: () -> Unit
) {
    val context = LocalContext.current
    val firefoxInstalled = FirefoxInstaller.isInstalled(context)
    when (kind) {
        CyberWindowKind.Terminal -> {
            val view = LocalView.current
            var command by remember { mutableStateOf("") }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("CYBEROS SHELL // READY", color = theme.accent, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                BasicTextField(
                    value = command,
                    onValueChange = { command = it; performCyberHaptic(view) },
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF10241A), RoundedCornerShape(3.dp)).padding(8.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    decorationBox = { inner -> if (command.isEmpty()) Text("enter command", color = Color.Gray, fontSize = 11.sp); inner() }
                )
                WindowTool("RUN COMMAND", theme) { onCommand(command); command = "" }
            }
        }
        CyberWindowKind.Store -> WindowModule("FIREFOX // ${if (firefoxInstalled) "INSTALLED" else "READY TO INSTALL"}", "Mozilla browser / native app intent", theme) {
            if (firefoxInstalled) onLaunchFirefox()
            else onCommand("launch firefox")
        }
        CyberWindowKind.Files -> WindowModule("LOCAL MEDIA INDEX", "Images and videos remain on this device.", theme, onOpenVault)
        CyberWindowKind.Scanner -> ScannerHud(theme)
        CyberWindowKind.Vault -> CyberVaultPanel(theme)
        CyberWindowKind.Scripts -> CyberScriptPanel(theme, onCommand)
        CyberWindowKind.Telemetry -> TelemetryPanel(theme)
    }
}

@Composable
private fun WindowModule(title: String, description: String, theme: CyberTheme, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(description, color = Color(0xFFB5C9BC), fontSize = 9.sp, lineHeight = 13.sp)
        WindowTool("OPEN MODULE", theme, onClick)
    }
}

@Composable
internal fun WindowTool(label: String, theme: CyberTheme, onClick: () -> Unit) {
    val view = LocalView.current
    Text(
        label,
        modifier = Modifier.border(1.dp, theme.accent.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .clickable { performCyberHaptic(view); onClick() }.padding(horizontal = 8.dp, vertical = 6.dp),
        color = theme.accent,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        maxLines = 1
    )
}

@Composable
private fun WindowAction(label: String, theme: CyberTheme, onClick: () -> Unit) {
    Text(label, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp), color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

private fun tileWindows(windows: MutableList<CyberWindowState>) {
    val active = windows.filterNot { it.minimized }.take(4)
    if (active.isEmpty()) return
    val columns = if (active.size == 1) 1 else 2
    val tileWidth = 160f
    val tileHeight = if (active.size <= 2) 220f else 180f
    active.forEachIndexed { index, window ->
        val targetIndex = windows.indexOfFirst { it.kind == window.kind }
        if (targetIndex >= 0) {
            windows[targetIndex] = window.copy(
                x = (index % columns) * tileWidth,
                y = (index / columns) * tileHeight,
                width = tileWidth,
                height = tileHeight,
                maximized = false,
                z = index
            )
        }
    }
}