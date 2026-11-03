package com.cyberos

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private data class NearbyBleNode(val name: String, val rssi: Int)

@Composable
internal fun ScannerHud(theme: CyberTheme) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION
    var hasPermission by remember { mutableStateOf(hasScanPermission(context, permission)) }
    var scanning by remember { mutableStateOf(false) }
    var sweepAngle by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("PASSIVE SCAN STANDBY") }
    val nodes = remember { mutableStateListOf<NearbyBleNode>() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) {
            nodes.clear()
            scanning = true
            status = "LISTENING FOR BLE ADVERTISEMENTS"
        } else status = "BLUETOOTH SCAN PERMISSION DENIED"
    }

    DisposableEffect(scanning, hasPermission) {
        if (!scanning || !hasPermission) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val scanner = manager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            status = "BLUETOOTH RADIO UNAVAILABLE"
            scanning = false
            return@DisposableEffect onDispose { }
        }
        val mainHandler = Handler(Looper.getMainLooper())
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val label = result.scanRecord?.deviceName?.takeIf { it.isNotBlank() } ?: "UNNAMED BLE NODE"
                mainHandler.post {
                    val oldIndex = nodes.indexOfFirst { it.name == label }
                    if (oldIndex >= 0) nodes[oldIndex] = nodes[oldIndex].copy(rssi = maxOf(nodes[oldIndex].rssi, result.rssi))
                    else if (nodes.size < 30) nodes.add(NearbyBleNode(label, result.rssi))
                }
            }

            override fun onScanFailed(errorCode: Int) {
                mainHandler.post {
                    status = "SCAN ERROR $errorCode"
                    scanning = false
                }
            }
        }
        runCatching { scanner.startScan(callback) }
            .onSuccess { status = "RADAR ACTIVE // NO CONNECTIONS" }
            .onFailure { status = "SCAN BLOCKED // CHECK RADIO SETTINGS"; scanning = false }
        onDispose { runCatching { scanner.stopScan(callback) } }
    }

    LaunchedEffect(scanning) {
        if (!scanning) return@LaunchedEffect
        repeat(30) {
            sweepAngle = (sweepAngle + 12f) % 360f
            delay(100)
        }
        scanning = false
        status = "SCAN COMPLETE // ${nodes.size} ADVERTISEMENTS"
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF08130E)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("PASSIVE SCANNER HUD", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(status, color = Color(0xFFA8C5B3), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }
            WindowTool(if (scanning) "STOP" else if (hasPermission) "SCAN 3S" else "AUTHORIZE", theme) {
                if (scanning) {
                    scanning = false
                    status = "SCAN STOPPED"
                } else if (hasPermission) {
                    nodes.clear()
                    scanning = true
                } else permissionLauncher.launch(permission)
            }
        }
        Box(Modifier.fillMaxWidth().height(146.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.43f
                listOf(0.33f, 0.66f, 1f).forEach { scale ->
                    drawCircle(theme.accent.copy(alpha = 0.24f), radius * scale, center, style = Stroke(1.dp.toPx()))
                }
                drawLine(theme.accent.copy(alpha = 0.18f), androidx.compose.ui.geometry.Offset(center.x - radius, center.y), androidx.compose.ui.geometry.Offset(center.x + radius, center.y), 1.dp.toPx())
                drawLine(theme.accent.copy(alpha = 0.18f), androidx.compose.ui.geometry.Offset(center.x, center.y - radius), androidx.compose.ui.geometry.Offset(center.x, center.y + radius), 1.dp.toPx())
                if (scanning) {
                    rotate(sweepAngle, center) {
                        drawLine(theme.accent.copy(alpha = 0.76f), center, androidx.compose.ui.geometry.Offset(center.x, center.y - radius), 2.dp.toPx())
                    }
                }
                nodes.take(18).forEachIndexed { index, node ->
                    val angle = Math.toRadians(((index * 137 + node.name.hashCode().mod(29)) % 360).toDouble())
                    val distance = radius * (1f - ((node.rssi + 100).coerceIn(0, 85) / 100f))
                    val x = center.x + kotlin.math.cos(angle).toFloat() * distance
                    val y = center.y + kotlin.math.sin(angle).toFloat() * distance
                    drawCircle(theme.accent.copy(alpha = 0.82f), 3.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
                }
            }
        }
        Text("BLE ADVERTISEMENTS // ${nodes.size}", color = theme.accent, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        Column(Modifier.weight(1f).fillMaxWidth().border(1.dp, theme.accent.copy(alpha = 0.24f), RoundedCornerShape(3.dp)).padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            nodes.sortedByDescending { it.rssi }.take(8).forEach { node ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(node.name.take(19), color = Color(0xFFD8E9DF), fontSize = 8.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${node.rssi} dBm", color = theme.accent, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
            if (nodes.isEmpty()) Text(if (scanning) "WAITING FOR ADVERTISEMENTS..." else "NO DEVICES INDEXED", color = Color(0xFF81998B), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        }
        val localNetwork = remember { readLocalNetwork(context) }
        Text("LOCAL LINK // ${localNetwork.transport}  ${localNetwork.address}", color = Color(0xFFA8C5B3), fontSize = 8.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

private data class LocalNetworkSummary(val transport: String, val address: String)

private fun readLocalNetwork(context: Context): LocalNetworkSummary {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val network = connectivity.activeNetwork ?: return LocalNetworkSummary("OFFLINE", "NO ADDRESS")
    val capabilities = connectivity.getNetworkCapabilities(network)
    val transport = when {
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELL"
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
        else -> "ONLINE"
    }
    val address = connectivity.getLinkProperties(network)?.linkAddresses
        ?.firstOrNull { it.address is java.net.Inet4Address }
        ?.address?.hostAddress ?: "ADDRESS HIDDEN"
    return LocalNetworkSummary(transport, address)
}

private fun hasScanPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED