package com.cyberos

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

class CyberOSWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val widgetContext = LocalContext.current
            val battery = widgetContext.getSystemService(BatteryManager::class.java)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
            val memory = widgetContext.getSystemService(ActivityManager::class.java).let { manager ->
                ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
            }
            val usedMemoryPercent = if (memory.totalMem > 0) {
                (((memory.totalMem - memory.availMem).toDouble() / memory.totalMem) * 100).toInt().coerceIn(0, 100)
            } else 0
            Column(
                modifier = GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF07100D))).padding(14.dp),
                verticalAlignment = Alignment.Vertical.Top
            ) {
                Text("CYBEROS // HOME NODE", style = widgetText(13.sp, Color(0xFF54F9A2), FontWeight.Bold))
                Spacer(GlanceModifier.height(6.dp))
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text("BATTERY ${bar(battery)} $battery%", style = widgetText(10.sp, Color(0xFFE0F4E7)))
                    Spacer(GlanceModifier.defaultWeight())
                    Text("RAM ${bar(usedMemoryPercent)} $usedMemoryPercent%", style = widgetText(10.sp, Color(0xFF9EB6A6)))
                }
                Spacer(GlanceModifier.defaultWeight())
                Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                    Button("OPEN SHELL", onClick = actionStartActivity(Intent(widgetContext, MainActivity::class.java)))
                    Spacer(GlanceModifier.width(8.dp))
                    Button("QUICK ALERT", onClick = actionRunCallback<CyberWidgetAlertAction>())
                }
            }
        }
    }

    private fun widgetText(size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
        color = ColorProvider(color),
        fontSize = size,
        fontWeight = weight,
        fontFamily = FontFamily.Monospace
    )

    private fun bar(value: Int): String {
        val filled = value / 10
        return "[${"#".repeat(filled)}${"-".repeat(10 - filled)}]"
    }
}

class CyberOSWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CyberOSWidget()
}

class CyberWidgetAlertAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        CyberAlerts.post(context)
    }
}