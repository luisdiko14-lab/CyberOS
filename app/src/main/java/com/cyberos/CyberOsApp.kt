package com.cyberos

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

internal enum class CyberTheme(val title: String, val accent: Color) {
    Matrix("MATRIX GREEN", Color(0xFF54F9A2)),
    Amber("AMBER TRACE", Color(0xFFFFB84A)),
    Pink("PINK NOISE", Color(0xFFFF5F9E)),
    Cobalt("COBALT LINK", Color(0xFF53A9FF));

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: Matrix
    }
}

private enum class ShellTab(val title: String) {
    Desktop("DESK"), Terminal("TERM"), Store("STORE"), Files("FILES"), System("SYSTEM")
}

private data class MediaEntry(val uri: Uri, val name: String, val isVideo: Boolean)

@Composable
fun CyberOsApp() {
    val context = LocalContext.current
    val activity = context as Activity
    val preferences = remember { context.getSharedPreferences("cyberos_shell", Context.MODE_PRIVATE) }
    var isBooted by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(ShellTab.Desktop.name) }
    var themeName by rememberSaveable { mutableStateOf(preferences.getString("theme", CyberTheme.Matrix.name)) }
    var wallpaperUri by rememberSaveable { mutableStateOf(preferences.getString("wallpaper", null)) }
        var biometricGateEnabled by rememberSaveable { mutableStateOf(preferences.getBoolean("biometric_shell_gate", false)) }
        var shellBiometricVerified by remember { mutableStateOf(!preferences.getBoolean("biometric_shell_gate", false)) }
    var mediaRefresh by remember { mutableIntStateOf(0) }
    var mediaEntries by remember { mutableStateOf(emptyList<MediaEntry>()) }
    var audioEnabled by rememberSaveable { mutableStateOf(false) }
    var overlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var installAllowed by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }
    var firefoxInstalled by remember { mutableStateOf(FirefoxInstaller.isInstalled(context)) }
    var webFallback by rememberSaveable { mutableStateOf(false) }
    var clearance by rememberSaveable { mutableStateOf(false) }
    var coreStaged by rememberSaveable { mutableStateOf(false) }
    val logs = remember { mutableStateListOf("CYBEROS SHELL // build 1.0.0", "Type help to inspect available commands.") }
    val theme = CyberTheme.fromName(themeName)

    fun appendLog(line: String) {
        logs.add(line)
        while (logs.size > 80) logs.removeAt(0)
    }

    fun applyTheme(nextTheme: CyberTheme) {
        themeName = nextTheme.name
        preferences.edit().putString("theme", nextTheme.name).apply()
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        audioEnabled = result[Manifest.permission.RECORD_AUDIO] == true || hasPermission(context, Manifest.permission.RECORD_AUDIO)
        mediaRefresh += 1
        appendLog(if (result.values.any { it }) "Permission state updated." else "No new permissions granted.")
    }
    val overlaySettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        overlayEnabled = Settings.canDrawOverlays(context)
        if (overlayEnabled) {
            FloatingTerminalService.start(context)
            appendLog("Floating terminal overlay started.")
        }
    }
    val installSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        installAllowed = context.packageManager.canRequestPackageInstalls()
        if (installAllowed) appendLog("Package installer authorization enabled by user.")
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            wallpaperUri = uri.toString()
            preferences.edit().putString("wallpaper", uri.toString()).apply()
            appendLog("Shell background updated from selected media.")
        }
    }
    fun requestCapabilities(includeAudio: Boolean = false, includeMedia: Boolean = false, includeNotifications: Boolean = false) {
        val permissions = buildList {
            if (includeAudio) add(Manifest.permission.RECORD_AUDIO)
            if (includeMedia) {
                if (Build.VERSION.SDK_INT >= 33) {
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                    add(Manifest.permission.READ_MEDIA_VIDEO)
                    if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                } else {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            if (includeNotifications && Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { !hasPermission(context, it) }
        if (permissions.isEmpty()) {
            if (includeAudio) audioEnabled = hasPermission(context, Manifest.permission.RECORD_AUDIO)
            if (includeMedia) mediaRefresh += 1
            appendLog("Requested capability is already available.")
        } else permissionLauncher.launch(permissions.toTypedArray())
    }

    fun requestOverlay() {
        if (Settings.canDrawOverlays(context)) {
            overlayEnabled = true
            FloatingTerminalService.start(context)
            appendLog("Floating terminal overlay started.")
        } else {
            overlaySettingsLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            )
        }
    }

    fun requestInstaller() {
        if (context.packageManager.canRequestPackageInstalls()) {
            installAllowed = true
            installFirefox(context, ::appendLog)
        } else {
            installSettingsLauncher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            )
        }
    }

    fun executeCommand(rawCommand: String) {
        val command = rawCommand.trim()
        if (command.isEmpty()) return
        appendLog("> $command")
        val tokens = command.lowercase().split(Regex("\\s+"))
        val response = when {
            tokens == listOf("help") -> "help | status | theme <matrix|amber|pink|cobalt> | unlock 3344 | sideload core | launch firefox | overlay | audio | media | notify"
            tokens == listOf("status") -> "SHELL ONLINE // overlay=${if (overlayEnabled) "ready" else "locked"} // firefox=${if (firefoxInstalled) "present" else "absent"}"
                tokens == listOf("diagnostics") -> "QUICK DIAGNOSTICS // shell online // media=${mediaEntries.size} indexed // theme=${theme.title}"
            tokens == listOf("clear") -> {
                logs.clear()
                "Terminal history cleared."
            }
            tokens.size == 2 && tokens[0] == "theme" -> {
                val nextTheme = when (tokens[1]) {
                    "matrix", "green" -> CyberTheme.Matrix
                    "amber" -> CyberTheme.Amber
                    "pink" -> CyberTheme.Pink
                    "cobalt", "blue" -> CyberTheme.Cobalt
                    else -> null
                }
                if (nextTheme != null) {
                    applyTheme(nextTheme)
                    "Visual profile changed to ${nextTheme.title}."
                } else "Unknown theme profile."
            }
            tokens == listOf("unlock", "3344") -> {
                clearance = true
                "CLEARANCE GRANTED // restricted module map revealed."
            }
            tokens == listOf("sideload", "core") -> {
                if (clearance) {
                    coreStaged = true
                    "CORE IMAGE STAGED // simulator only; no package was installed."
                } else "ACCESS DENIED // unlock clearance first."
            }
            tokens == listOf("launch", "firefox") -> {
                firefoxInstalled = FirefoxInstaller.isInstalled(context)
                if (firefoxInstalled && FirefoxInstaller.launch(context)) "Firefox launched through its native app intent."
                else "Firefox is not installed. Open STORE to install or use the web fallback."
            }
            tokens == listOf("overlay") -> {
                requestOverlay()
                "Overlay permission flow opened."
            }
            tokens == listOf("audio") -> {
                requestCapabilities(includeAudio = true)
                "Microphone permission flow opened."
            }
            tokens == listOf("media") -> {
                requestCapabilities(includeMedia = true)
                "Media access permission flow opened."
            }
            tokens == listOf("notify") -> {
                if (Build.VERSION.SDK_INT < 33 || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)) {
                    CyberAlerts.trigger(context)
                    "System alert dispatched."
                } else {
                    requestCapabilities(includeNotifications = true)
                    "Notification permission flow opened."
                }
            }
            else -> "Unknown command. Type help."
        }
        if (response.startsWith("Unknown command")) CyberSoundEngine.error()
        appendLog(response)
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                val command = normalizeVoiceCommand(spoken)
                appendLog("VOICE // $spoken")
                executeCommand(command)
            }
        }
    }

    fun launchVoiceCommand() {
        if (!hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
            requestCapabilities(includeAudio = true)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "CyberOS command")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        runCatching { voiceLauncher.launch(intent) }
            .onFailure { appendLog("Voice recognizer unavailable: ${it.message ?: "error"}") }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayEnabled = Settings.canDrawOverlays(context)
                installAllowed = context.packageManager.canRequestPackageInstalls()
                firefoxInstalled = FirefoxInstaller.isInstalled(context)
                audioEnabled = hasPermission(context, Manifest.permission.RECORD_AUDIO) && audioEnabled
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        listOf("CYBEROS BIOS 1.0.0", "MEMORY CHECK .......... OK", "SECURE CHANNEL ......... READY", "LOADING SHELL .......... OK").forEach {
            delay(320)
            appendLog("[BOOT] $it")
        }
        CyberSoundEngine.boot()
        delay(180)
        isBooted = true
    }

    LaunchedEffect(mediaRefresh) {
        if (mediaRefresh == 0) return@LaunchedEffect
        mediaEntries = withContext(Dispatchers.IO) { queryMedia(context) }
        appendLog("Media index refreshed: ${mediaEntries.size} items visible.")
    }

    if (!isBooted) {
        BootScreen(logs, theme)
        return
    }

        if (biometricGateEnabled && !shellBiometricVerified) {
            BiometricGate(theme) {
                val host = context as? androidx.fragment.app.FragmentActivity
                if (host == null) appendLog("Biometric host activity unavailable.")
                else CyberVaultCrypto.authenticateShell(
                    host,
                    onSuccess = { shellBiometricVerified = true },
                    onFailure = { appendLog("Biometric authentication failed: $it") }
                )
            }
            return
        }

    val tab = ShellTab.entries.firstOrNull { it.name == selectedTab } ?: ShellTab.Terminal
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF07100D)).cyberGestureInput(
            onFirefoxSwipe = {
                if (!FirefoxInstaller.launch(context)) selectedTab = ShellTab.Store.name
            },
            onQuickDiagnostics = {
                selectedTab = ShellTab.Terminal.name
                executeCommand("diagnostics")
            }
        )
    ) {
        WallpaperLayer(wallpaperUri)
        CyberDigitalRain(theme, Modifier.fillMaxSize())
        ShellBackdrop(theme)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            ShellHeader(theme, tab, clearance)
            Spacer(Modifier.height(15.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    ShellTab.Desktop -> WindowManagerScreen(
                        theme = theme,
                        onCommand = ::executeCommand,
                        onLaunchFirefox = {
                            if (!FirefoxInstaller.launch(context)) webFallback = true
                        },
                        onOpenVault = { selectedTab = ShellTab.Files.name },
                        onOpenScripts = { selectedTab = ShellTab.Terminal.name }
                    )
                    ShellTab.Terminal -> TerminalScreen(
                        theme, logs, audioEnabled,
                        onCommand = ::executeCommand,
                        onVoice = ::launchVoiceCommand,
                        onRequestAudio = { requestCapabilities(includeAudio = true) },
                        onClear = { logs.clear() }
                    )
                    ShellTab.Store -> StoreScreen(
                        theme, firefoxInstalled, installAllowed, webFallback,
                        clearance = clearance,
                        coreStaged = coreStaged,
                        onStageCore = { executeCommand("sideload core") },
                        onRefresh = { firefoxInstalled = FirefoxInstaller.isInstalled(context) },
                        onLaunchFirefox = {
                            if (!FirefoxInstaller.launch(context)) webFallback = true
                        },
                        onDownloadFirefox = { startFirefoxDownload(context, ::appendLog) },
                        onInstallDownloaded = { requestInstaller() },
                        onPlayStore = {
                            if (!FirefoxInstaller.openPlayStore(context)) appendLog("No Play Store or browser handler is available for Firefox.")
                        },
                        onWebFallback = { webFallback = true },
                        onCloseWeb = { webFallback = false }
                    )
                    ShellTab.Files -> FilesScreen(
                        theme, mediaEntries, wallpaperUri,
                        onPick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onScan = { requestCapabilities(includeMedia = true) },
                        onSetWallpaper = { entry ->
                            if (!entry.isVideo) {
                                wallpaperUri = entry.uri.toString()
                                preferences.edit().putString("wallpaper", wallpaperUri).apply()
                                appendLog("Shell and lock background set to ${entry.name}.")
                            }
                        }
                    )
                    ShellTab.System -> SystemScreen(
                        theme,
                        biometricGateEnabled = biometricGateEnabled,
                        overlayEnabled = overlayEnabled,
                        installAllowed = installAllowed,
                        audioAllowed = hasPermission(context, Manifest.permission.RECORD_AUDIO),
                        notificationAllowed = Build.VERSION.SDK_INT < 33 || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS),
                        onOverlay = ::requestOverlay,
                        onInstallPermission = {
                            installSettingsLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                            )
                        },
                        onAudio = { requestCapabilities(includeAudio = true) },
                        onNotifications = { requestCapabilities(includeNotifications = true) },
                        onSendAlert = {
                            if (Build.VERSION.SDK_INT >= 33 && !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)) {
                                requestCapabilities(includeNotifications = true)
                            } else CyberAlerts.trigger(context)
                        },
                        onTheme = ::applyTheme,
                        onBiometricGate = {
                            if (biometricGateEnabled) {
                                biometricGateEnabled = false
                                shellBiometricVerified = true
                                preferences.edit().putBoolean("biometric_shell_gate", false).apply()
                            } else {
                                val host = context as? androidx.fragment.app.FragmentActivity
                                if (host == null || !CyberVaultCrypto.isBiometricAvailable(context)) appendLog("Enroll a strong biometric before enabling the shell gate.")
                                else CyberVaultCrypto.authenticateShell(
                                    host,
                                    onSuccess = {
                                        biometricGateEnabled = true
                                        shellBiometricVerified = true
                                        preferences.edit().putBoolean("biometric_shell_gate", true).apply()
                                    },
                                    onFailure = { appendLog("Biometric setup not completed: $it") }
                                )
                            }
                        },
                        onStopOverlay = { FloatingTerminalService.stop(context) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ShellNavigation(tab, theme) { selectedTab = it.name }
        }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun startFirefoxDownload(context: Context, appendLog: (String) -> Unit) {
    runCatching { FirefoxInstaller.enqueue(context) }
        .onSuccess { appendLog("Official Firefox APK download queued (job $it).") }
        .onFailure { appendLog("Firefox download could not start: ${it.message ?: "unknown error"}") }
}

private fun installFirefox(context: Context, appendLog: (String) -> Unit) {
    val downloadId = context.getSharedPreferences(FirefoxInstaller.PREFERENCES, Context.MODE_PRIVATE)
        .getLong(FirefoxInstaller.DOWNLOAD_ID, -1L)
    if (downloadId < 0) {
        appendLog("No Firefox download is ready. Start the download from Store first.")
        return
    }
    runCatching { FirefoxInstaller.install(context, downloadId) }
        .onSuccess { if (it) appendLog("Handing Firefox package to Android Package Installer.") else appendLog("Firefox download is not ready yet.") }
        .onFailure { appendLog("Package installer could not open: ${it.message ?: "unknown error"}") }
}

private fun normalizeVoiceCommand(transcript: String): String {
    val phrase = transcript.trim().lowercase().removePrefix("cyberos ").trim()
    return when {
        phrase.startsWith("launch firefox") || phrase == "open firefox" -> "launch firefox"
        phrase.startsWith("set theme ") -> "theme ${phrase.removePrefix("set theme ").trim()}"
        phrase == "scan system" || phrase == "run diagnostics" -> "diagnostics"
        phrase == "send alert" || phrase == "system alert" -> "notify"
        phrase.startsWith("change theme ") -> "theme ${phrase.removePrefix("change theme ").trim()}"
        else -> phrase
    }
}

@Composable
private fun BootScreen(logs: List<String>, theme: CyberTheme) {
    Box(Modifier.fillMaxSize().background(Color(0xFF07100D))) {
        CyberDigitalRain(theme, Modifier.fillMaxSize())
        ShellBackdrop(theme)
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("CYBEROS", color = theme.accent, fontSize = 34.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text("BIOS / SECURE BOOT", color = Color(0xFFA8C5B3), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            logs.takeLast(4).forEach { Text(it, color = theme.accent.copy(alpha = 0.88f), fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            Spacer(Modifier.height(8.dp))
            Text("INITIALIZING", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ShellHeader(theme: CyberTheme, tab: ShellTab, clearance: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("CYBEROS", color = theme.accent, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text("${tab.title} // 21:07:44 UTC", color = Color(0xFFA8C5B3), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (clearance) "CLEARANCE 04" else "SECURE LINK", color = theme.accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(if (clearance) "CORE MAP OPEN" else "NODE 07 / ONLINE", color = Color(0xFFA8C5B3), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
    Spacer(Modifier.height(10.dp))
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(theme.accent.copy(alpha = 0.55f), start = androidx.compose.ui.geometry.Offset.Zero, end = androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
    }
}

@Composable
private fun ShellNavigation(selected: ShellTab, theme: CyberTheme, onSelect: (ShellTab) -> Unit) {
    val items = listOf(
        ShellTab.Desktop to Icons.Outlined.Home,
        ShellTab.Terminal to Icons.Outlined.Terminal,
        ShellTab.Store to Icons.Outlined.Apps,
        ShellTab.Files to Icons.Outlined.FolderOpen,
        ShellTab.System to Icons.Outlined.Settings
    )
    Row(
        modifier = Modifier.fillMaxWidth().border(1.dp, theme.accent.copy(alpha = 0.38f), RoundedCornerShape(5.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { (tab, icon) ->
            val active = tab == selected
            Column(
                modifier = Modifier.weight(1f).clickable { onSelect(tab) }.padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(icon, contentDescription = tab.title, tint = if (active) theme.accent else Color(0xFF81998B), modifier = Modifier.size(18.dp))
                Text(tab.title, color = if (active) theme.accent else Color(0xFF81998B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun TerminalScreen(
    theme: CyberTheme,
    logs: List<String>,
    audioEnabled: Boolean,
    onCommand: (String) -> Unit,
    onVoice: () -> Unit,
    onRequestAudio: () -> Unit,
    onClear: () -> Unit
) {
    val view = androidx.compose.ui.platform.LocalView.current
    var command by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("INTERACTIVE TERMINAL", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            TextButtonLike("CLEAR", theme, onClick = onClear)
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .border(1.dp, theme.accent.copy(alpha = 0.36f), RoundedCornerShape(5.dp))
                .background(Color(0xB9081711), RoundedCornerShape(5.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            logs.forEach { line ->
                Text(line, color = if (line.startsWith(">")) Color.White else theme.accent.copy(alpha = 0.9f), fontSize = 11.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace)
            }
            if (audioEnabled) {
                Spacer(Modifier.height(10.dp))
                AudioVisualizer(theme)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("$", color = theme.accent, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
            OutlinedTextField(
                value = command,
                onValueChange = { command = it; performCyberHaptic(view) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("enter command", color = Color(0xFF71877A), fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onCommand(command); command = "" }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = theme.accent.copy(alpha = 0.32f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = theme.accent
                )
            )
            CyberButton("RUN", theme, Modifier.width(64.dp)) { onCommand(command); command = "" }
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("MIC ${if (audioEnabled) "LIVE" else "STANDBY"}", color = if (audioEnabled) theme.accent else Color(0xFF829688), fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            TextButtonLike(if (audioEnabled) "AUDIO ACTIVE" else "ENABLE AUDIO", theme, onClick = onRequestAudio)
                    TextButtonLike("VOICE", theme, onClick = onVoice)
        }
    }
}

@Composable
private fun AudioVisualizer(theme: CyberTheme) {
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val minimumBuffer = AudioRecord.getMinBufferSize(22050, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimumBuffer <= 0) return@LaunchedEffect
        val recorder = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, 22050, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimumBuffer, 4096))
        }.getOrNull() ?: return@LaunchedEffect
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@LaunchedEffect
        }
        val sampleBuffer = ShortArray(512)
        try {
            recorder.startRecording()
            while (currentCoroutineContext().isActive) {
                val count = withContext(Dispatchers.IO) { recorder.read(sampleBuffer, 0, sampleBuffer.size) }
                if (count > 0) {
                    var sum = 0.0
                    for (index in 0 until count) {
                        val sample = sampleBuffer[index].toDouble() / Short.MAX_VALUE
                        sum += sample * sample
                    }
                    level = (sqrt(sum / count).toFloat() * 4.2f).coerceIn(0.02f, 1f)
                }
            }
        } catch (_: SecurityException) {
            level = 0f
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("AMBIENT INPUT // RMS ${(level * 100).toInt()}%", color = theme.accent, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Canvas(Modifier.fillMaxWidth().height(46.dp)) {
            val mid = size.height / 2f
            val path = Path()
            val points = 48
            for (index in 0..points) {
                val x = size.width * index / points
                val phase = index * 0.72f
                val envelope = (0.12f + level * (0.45f + 0.45f * kotlin.math.abs(kotlin.math.sin(phase * 0.22f))))
                val y = mid + kotlin.math.sin(phase) * size.height * envelope * 0.42f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, theme.accent, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
private fun StoreScreen(
    theme: CyberTheme,
    firefoxInstalled: Boolean,
    installAllowed: Boolean,
    webFallback: Boolean,
    clearance: Boolean,
    coreStaged: Boolean,
    onStageCore: () -> Unit,
    onRefresh: () -> Unit,
    onLaunchFirefox: () -> Unit,
    onDownloadFirefox: () -> Unit,
    onInstallDownloaded: () -> Unit,
    onPlayStore: () -> Unit,
    onWebFallback: () -> Unit,
    onCloseWeb: () -> Unit
) {
    if (webFallback) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("FIREFOX // WEB ACCESS", color = theme.accent, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButtonLike("CLOSE", theme, onClick = onCloseWeb)
            }
            Spacer(Modifier.height(8.dp))
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, theme.accent.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        loadUrl("https://www.mozilla.org/firefox/android/")
                    }
                }
            )
        }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("SOFTWARE EXCHANGE", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("SIGNED CHANNEL // PUBLIC REPOSITORY", color = Color(0xFFA8C5B3), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
            TextButtonLike("RESCAN", theme, onClick = onRefresh)
        }
        StorePackage("FIREFOX", "org.mozilla.firefox", "Mozilla browser // native launch or verified vendor download", theme, "${if (firefoxInstalled) "INSTALLED" else "NOT INSTALLED"}") {
            if (firefoxInstalled) onLaunchFirefox() else onDownloadFirefox()
        }
        if (!firefoxInstalled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CyberButton("INSTALL READY APK", theme, Modifier.weight(1f), onClick = onInstallDownloaded)
                CyberButton("PLAY STORE", theme, Modifier.weight(1f), onClick = onPlayStore)
            }
            TextButtonLike("OPEN MOZILLA WEB FALLBACK", theme, onClick = onWebFallback)
            Text(
                if (installAllowed) "Installer authorization is enabled. Android still requires confirmation before installing the package."
                else "APK installation requires the per-app Android setting and a final Package Installer confirmation.",
                color = Color(0xFFA8C5B3), fontSize = 10.sp, lineHeight = 15.sp
            )
        }
        StorePackage("SIGNAL SCOPE", "com.cyberos.signalscope", "Audio-reactive system monitor // local module", theme, "AVAILABLE") { }
        StorePackage("PACKET ATLAS", "com.cyberos.packetatlas", "Offline network map // demo package listing", theme, "CATALOG") { }
        StorePackage("CORE UTILITIES", "com.cyberos.coretools", "Diagnostics, storage index, and theme engine", theme, "CATALOG") { }
        if (clearance) {
            StorePackage(
                "BLACKBOX CORE",
                "com.cyberos.blackbox",
                if (coreStaged) "Clearance verified // core image staged in local simulator" else "Restricted diagnostics // authorization required",
                theme,
                if (coreStaged) "STAGED" else "CLEARANCE 04",
                onStageCore
            )
        }
    }
}

@Composable
private fun StorePackage(name: String, packageId: String, description: String, theme: CyberTheme, status: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, theme.accent.copy(alpha = 0.36f), RoundedCornerShape(5.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(packageId, color = Color(0xFF829688), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
            Text(status, color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(6.dp))
        Text(description, color = Color(0xFFB5C9BC), fontSize = 10.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(8.dp))
        TextButtonLike(if (name == "FIREFOX" && status == "INSTALLED") "LAUNCH" else if (name == "FIREFOX") "DOWNLOAD OFFICIAL APK" else "OPEN MODULE", theme, onClick)
    }
}

@Composable
private fun FilesScreen(
    theme: CyberTheme,
    entries: List<MediaEntry>,
    wallpaperUri: String?,
    onPick: () -> Unit,
    onScan: () -> Unit,
    onSetWallpaper: (MediaEntry) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("LOCAL MEDIA INDEX", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("${entries.size} VISIBLE // READ-ONLY SOURCE", color = Color(0xFFA8C5B3), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
            TextButtonLike("SCAN", theme, onClick = onScan)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CyberButton("PICK WALLPAPER", theme, Modifier.weight(1f), onClick = onPick)
            Text("${if (wallpaperUri == null) "NO WALLPAPER" else "WALLPAPER SET"}", color = theme.accent, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.CenterVertically))
        }
        Spacer(Modifier.height(8.dp))
        if (entries.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = theme.accent.copy(alpha = 0.8f), modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(8.dp))
                Text("MEDIA ACCESS REQUIRED", color = theme.accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("Grant photo/video access or use the system picker.", color = Color(0xFFA8C5B3), fontSize = 10.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                items(entries, key = { it.uri.toString() }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSetWallpaper(entry) }.padding(vertical = 11.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (entry.isVideo) "VID" else "IMG", color = theme.accent, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(38.dp))
                        Text(entry.name, color = Color(0xFFD8E9DF), fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(if (entry.isVideo) "VIDEO" else "SET BG", color = Color(0xFF829688), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                        drawLine(theme.accent.copy(alpha = 0.16f), androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx())
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemScreen(
    theme: CyberTheme,
    biometricGateEnabled: Boolean,
    overlayEnabled: Boolean,
    installAllowed: Boolean,
    audioAllowed: Boolean,
    notificationAllowed: Boolean,
    onOverlay: () -> Unit,
    onInstallPermission: () -> Unit,
    onAudio: () -> Unit,
    onNotifications: () -> Unit,
    onSendAlert: () -> Unit,
    onTheme: (CyberTheme) -> Unit,
    onBiometricGate: () -> Unit,
    onStopOverlay: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("SYSTEM CONTROL", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        PermissionRow("FLOATING TERMINAL", if (overlayEnabled) "AUTHORIZED" else "SETTINGS REQUIRED", theme, onOverlay)
        if (overlayEnabled) PermissionRow("STOP OVERLAY SERVICE", "USER CONTROL", theme, onStopOverlay)
        PermissionRow("UNKNOWN APP INSTALLS", if (installAllowed) "AUTHORIZED" else "SETTINGS REQUIRED", theme, onInstallPermission)
        PermissionRow("MICROPHONE / VISUALIZER", if (audioAllowed) "AUTHORIZED" else "NOT GRANTED", theme, onAudio)
        PermissionRow("NOTIFICATION CHANNEL", if (notificationAllowed) "AUTHORIZED" else "NOT GRANTED", theme, onNotifications)
        PermissionRow("BIOMETRIC SHELL GATE", if (biometricGateEnabled) "ENABLED // NO APP PIN" else "OPTIONAL / DISABLED", theme, onBiometricGate)
        Column {
            Text("DISPLAY PROFILE", color = theme.accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                CyberTheme.entries.forEach { option ->
                    val selected = option == theme
                    Column(
                        modifier = Modifier.weight(1f).border(1.dp, option.accent.copy(alpha = if (selected) 1f else 0.45f), RoundedCornerShape(4.dp))
                            .clickable { onTheme(option) }.padding(vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Canvas(Modifier.size(16.dp)) { drawCircle(option.accent) }
                        Text(option.title.substringBefore(' '), color = option.accent, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        Column {
            Text("ALERT CHANNEL", color = theme.accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(5.dp))
            Text("RemoteViews notification with interactive open and acknowledge actions.", color = Color(0xFFA8C5B3), fontSize = 10.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(7.dp))
            CyberButton("SEND TEST ALERT", theme, Modifier.fillMaxWidth(), onClick = onSendAlert)
        }
        Text("ANDROID HOST CONTROLS PERMISSIONS AND PACKAGE INSTALL CONFIRMATION.", color = Color(0xFF829688), fontSize = 9.sp, lineHeight = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PermissionRow(title: String, status: String, theme: CyberTheme, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().border(1.dp, theme.accent.copy(alpha = 0.28f), RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(status, color = theme.accent, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        Text("OPEN", color = theme.accent, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun CyberButton(label: String, theme: CyberTheme, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    Button(
        onClick = { performCyberHaptic(view); onClick() },
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = Color(0xFF07100D)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

@Composable
private fun TextButtonLike(label: String, theme: CyberTheme, onClick: () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    Text(label, modifier = Modifier.clickable { performCyberHaptic(view); onClick() }.padding(horizontal = 5.dp, vertical = 8.dp), color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
}

@Composable
private fun WallpaperLayer(uriString: String?, alpha: Float = 0.18f) {
    if (uriString == null) return
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = uriString) {
        value = withContext(Dispatchers.IO) { readWallpaper(context, Uri.parse(uriString)) }
    }
    bitmap?.let {
        Image(it, contentDescription = null, modifier = Modifier.fillMaxSize().alpha(alpha), contentScale = ContentScale.Crop)
    }
}

private fun readWallpaper(context: Context, uri: Uri): ImageBitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 1440 || bounds.outHeight / sampleSize > 2200) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
}.getOrNull()

@Composable
private fun ShellBackdrop(theme: CyberTheme) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(theme.accent.copy(alpha = 0.012f))
        val spacing = 5.dp.toPx()
        var lineY = 0f
        while (lineY < size.height) {
            drawLine(Color(0x0DFF3158), androidx.compose.ui.geometry.Offset(0f, lineY - 0.5f), androidx.compose.ui.geometry.Offset(size.width, lineY - 0.5f), 0.5.dp.toPx())
            drawLine(Color(0x0D258DFF), androidx.compose.ui.geometry.Offset(0f, lineY + 0.5f), androidx.compose.ui.geometry.Offset(size.width, lineY + 0.5f), 0.5.dp.toPx())
            drawLine(Color.Black.copy(alpha = 0.12f), androidx.compose.ui.geometry.Offset(0f, lineY), androidx.compose.ui.geometry.Offset(size.width, lineY), 1.dp.toPx())
            lineY += spacing
        }
        val grid = 32.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(theme.accent.copy(alpha = 0.025f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
            x += grid
        }
    }
}

private fun queryMedia(context: Context): List<MediaEntry> {
    val results = mutableListOf<MediaEntry>()
    val imageCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val videoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
    listOf(imageCollection to false, videoCollection to true).forEach { (collection, isVideo) ->
        runCatching {
            context.contentResolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                while (cursor.moveToNext() && results.size < 160) {
                    results += MediaEntry(
                        Uri.withAppendedPath(collection, cursor.getLong(idColumn).toString()),
                        cursor.getString(nameColumn) ?: "untitled",
                        isVideo
                    )
                }
            }
        }
    }
    return results.sortedByDescending { it.name.lowercase() }
}