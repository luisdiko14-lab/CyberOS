package com.cyberos

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.TrafficStats
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.math.max
import kotlin.math.sin

private data class HardwareSample(
    val temperatureC: Float,
    val voltageV: Float,
    val health: String,
    val ramPercent: Int,
    val rxPerSecond: Long,
    val txPerSecond: Long
)

@Composable
internal fun TelemetryPanel(theme: CyberTheme) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val samples = remember { mutableStateListOf<HardwareSample>() }
    var current by remember { mutableStateOf<HardwareSample?>(null) }
    LaunchedEffect(Unit) {
        var previousRx = TrafficStats.getTotalRxBytes()
        var previousTx = TrafficStats.getTotalTxBytes()
        var previousTime = System.currentTimeMillis()
        while (isActive) {
            val now = System.currentTimeMillis()
            val elapsed = (now - previousTime).coerceAtLeast(1L)
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val memory = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
            val health = when (battery?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
                BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
                BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER-VOLTAGE"
                else -> "UNKNOWN"
            }
            val sample = HardwareSample(
                temperatureC = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
                voltageV = (battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f,
                health = health,
                ramPercent = if (memory.totalMem > 0) (((memory.totalMem - memory.availMem) * 100) / memory.totalMem).toInt().coerceIn(0, 100) else 0,
                rxPerSecond = if (previousRx < 0 || rx < previousRx) -1 else ((rx - previousRx) * 1000L / elapsed),
                txPerSecond = if (previousTx < 0 || tx < previousTx) -1 else ((tx - previousTx) * 1000L / elapsed)
            )
            previousRx = rx
            previousTx = tx
            previousTime = now
            current = sample
            samples.add(sample)
            while (samples.size > 48) samples.removeAt(0)
            delay(1000)
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF08130E)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("LIVE HARDWARE DIAGNOSTICS", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        current?.let { sample ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TelemetryMetric("BATTERY", "${sample.temperatureC} C", "${sample.voltageV} V", theme)
                TelemetryMetric("HEALTH", sample.health, "", theme)
                TelemetryMetric("RAM", "${sample.ramPercent}%", asciiBar(sample.ramPercent), theme)
            }
            Text("RX ${formatRate(sample.rxPerSecond)}  TX ${formatRate(sample.txPerSecond)}", color = Color(0xFFD8E9DF), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            TelemetryGraph(samples, theme)
        } ?: Text("POLLING SYSTEM TELEMETRY...", color = Color(0xFFA8C5B3), fontSize = 8.sp)
        Text("NETWORK TOTALS ARE DEVICE-WIDE TRAFFIC COUNTERS.", color = Color(0xFF81998B), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun TelemetryMetric(title: String, value: String, secondary: String, theme: CyberTheme) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color(0xFF81998B), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        if (secondary.isNotBlank()) Text(secondary, color = Color(0xFFA8C5B3), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun TelemetryGraph(samples: List<HardwareSample>, theme: CyberTheme) {
    Canvas(Modifier.fillMaxWidth().height(64.dp).background(Color(0xFF07100D), RoundedCornerShape(3.dp))) {
        val valid = samples.filter { it.rxPerSecond >= 0 && it.txPerSecond >= 0 }
        if (valid.size < 2) return@Canvas
        val maxRate = max(1L, valid.maxOf { max(it.rxPerSecond, it.txPerSecond) }).toFloat()
        val stepX = size.width / (valid.size - 1)
        for (index in 1 until valid.size) {
            val previous = valid[index - 1]
            val next = valid[index]
            drawLine(theme.accent,
                androidx.compose.ui.geometry.Offset((index - 1) * stepX, size.height * (1f - previous.rxPerSecond / maxRate)),
                androidx.compose.ui.geometry.Offset(index * stepX, size.height * (1f - next.rxPerSecond / maxRate)), 1.5.dp.toPx())
            drawLine(Color(0xFFFFB84A),
                androidx.compose.ui.geometry.Offset((index - 1) * stepX, size.height * (1f - previous.txPerSecond / maxRate)),
                androidx.compose.ui.geometry.Offset(index * stepX, size.height * (1f - next.txPerSecond / maxRate)), 1.dp.toPx())
        }
    }
}

internal fun asciiBar(value: Int, width: Int = 12): String {
    val count = value.coerceIn(0, 100) * width / 100
    return "[${"#".repeat(count)}${".".repeat(width - count)}]"
}

private fun formatRate(value: Long): String = when {
    value < 0 -> "N/A"
    value >= 1_000_000 -> "${value / 1_000_000} MB/s"
    value >= 1_000 -> "${value / 1_000} KB/s"
    else -> "$value B/s"
}

@Composable
internal fun CyberDigitalRain(theme: CyberTheme, modifier: Modifier = Modifier) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            frame++
            delay(85)
        }
    }
    Canvas(modifier) {
        val textSize = 10.dp.toPx()
        val columnWidth = 13.dp.toPx()
        val columns = (size.width / columnWidth).toInt().coerceAtLeast(1)
        val rows = (size.height / textSize).toInt().coerceAtLeast(1)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = false
            typeface = android.graphics.Typeface.MONOSPACE
            this.textSize = textSize
        }
        val glyphs = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val save = nativeCanvas.save()
        for (column in 0 until columns) {
            val head = ((frame / (1 + column % 4) + column * 11) % (rows + 8)) - 4
            for (tail in 0 until 8) {
                val row = head - tail
                if (row !in 0 until rows) continue
                val charIndex = (column * 31 + row * 17 + frame / 3) % glyphs.length
                paint.color = if (tail == 0) android.graphics.Color.WHITE else android.graphics.Color.argb((205 - tail * 23).coerceAtLeast(24), 55, 249, 162)
                nativeCanvas.drawText(glyphs[charIndex].toString(), column * columnWidth, row * textSize, paint)
            }
        }
        nativeCanvas.restoreToCount(save)
        drawRect(theme.accent.copy(alpha = 0.006f))
    }
}

internal object CyberSoundEngine {
    private var pool: SoundPool? = null
    private val samples = mutableMapOf<String, Int>()
    private val ready = mutableSetOf<Int>()

    @Synchronized
    fun initialize(context: Context) {
        if (pool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val soundPool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
        soundPool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready += id }
        pool = soundPool
        listOf("click" to 760.0, "boot" to 460.0, "error" to 190.0).forEach { (name, frequency) ->
            val file = java.io.File(context.cacheDir, "cyberos-$name.wav")
            if (!file.exists()) file.writeBytes(toneWav(frequency))
            samples[name] = soundPool.load(file.absolutePath, 1)
        }
    }

    fun click() = play("click")
    fun boot() = play("boot")
    fun error() = play("error")

    private fun play(name: String) {
        val id = samples[name] ?: return
        if (id in ready) pool?.play(id, 0.24f, 0.24f, 1, 0, 1f)
    }

    @Synchronized
    fun release() {
        pool?.release()
        pool = null
        samples.clear()
        ready.clear()
    }

    private fun toneWav(frequency: Double): ByteArray {
        val sampleRate = 22050
        val count = (sampleRate * 0.075).toInt()
        val pcm = ByteArray(count) { index ->
            val envelope = 1.0 - index.toDouble() / count
            val pitch = frequency * (1.0 + index.toDouble() / count * 0.28)
            (128 + sin(2 * Math.PI * pitch * index / sampleRate) * 86 * envelope).toInt().coerceIn(0, 255).toByte()
        }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeBytes("RIFF"); writeLe(output, 36 + pcm.size)
            output.writeBytes("WAVEfmt "); writeLe(output, 16)
            writeLe(output, 1, 2); writeLe(output, 1, 2)
            writeLe(output, sampleRate); writeLe(output, sampleRate)
            writeLe(output, 1, 2); writeLe(output, 8, 2)
            output.writeBytes("data"); writeLe(output, pcm.size); output.write(pcm)
        }
        return bytes.toByteArray()
    }

    private fun writeLe(output: DataOutputStream, value: Int, byteCount: Int = 4) {
        repeat(byteCount) { output.writeByte(value ushr (8 * it)) }
    }
}