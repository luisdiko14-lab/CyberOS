package com.cyberos

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class FloatingTerminalService : Service() {
    private var windowManager: WindowManager? = null
    private var overlay: LinearLayout? = null
    private var outputView: TextView? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        createOverlayChannel()
        val notification = NotificationCompat.Builder(this, OVERLAY_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("CyberOS terminal active")
            .setContentText("Tap to return to the CyberOS shell")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(OVERLAY_NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(OVERLAY_NOTIFICATION, notification)
        }
        if (overlay == null) showTerminal()
        return START_STICKY
    }

    private fun showTerminal() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(7, 16, 13))
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        header.addView(TextView(this).apply {
            text = "CYBEROS // LIVE SHELL"
            textSize = 12f
            setTextColor(Color.rgb(84, 249, 162))
        }, LinearLayout.LayoutParams(0, dp(36), 1f))
        header.addView(Button(this).apply {
            text = "X"
            setTextColor(Color.rgb(84, 249, 162))
            setOnClickListener { stopSelf() }
        }, LinearLayout.LayoutParams(dp(48), dp(36)))
        root.addView(header)

        val output = TextView(this).apply {
            text = "Overlay session ready.\nType help for commands."
            textSize = 12f
            setTextColor(Color.rgb(193, 230, 207))
            setPadding(0, dp(5), 0, dp(9))
        }
        outputView = output
        root.addView(output, LinearLayout.LayoutParams(-1, dp(72)))

        val input = EditText(this).apply {
            hint = "enter command"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEND
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.rgb(18, 35, 27))
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                runOverlayCommand(input.text.toString())
                input.text.clear()
                true
            } else false
        }
        root.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
        overlay = root

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            dp(320),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(96)
        }
        windowManager?.addView(root, params)
    }

    private fun runOverlayCommand(command: String) {
        val result = when (command.trim().lowercase()) {
            "help" -> "help  status  unlock 3344  sideload core"
            "status" -> "CYBEROS ONLINE // overlay daemon active"
            "unlock 3344" -> "CLEARANCE GRANTED // core access unlocked"
            "sideload core" -> "CORE PACKAGE STAGED // authorization required"
            else -> "command not found: ${command.take(48)}"
        }
        outputView?.text = "> $command\n$result"
    }

    private fun createOverlayChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(OVERLAY_CHANNEL, "Terminal overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        overlay?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlay = null
        outputView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val OVERLAY_CHANNEL = "cyberos_overlay"
        private const val OVERLAY_NOTIFICATION = 4201

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FloatingTerminalService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingTerminalService::class.java))
        }
    }
}

class CyberAlertService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CyberAlerts.post(this)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

object CyberAlerts {
    private const val CHANNEL_ID = "cyberos_alerts"
    private const val NOTIFICATION_ID = 4210

    fun trigger(context: Context) {
        context.startService(Intent(context, CyberAlertService::class.java))
    }

    fun post(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val openIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ackIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, CyberNotificationReceiver::class.java).setAction(CyberNotificationReceiver.ACTION_ACK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openAction = PendingIntent.getBroadcast(
            context,
            3,
            Intent(context, CyberNotificationReceiver::class.java).setAction(CyberNotificationReceiver.ACTION_OPEN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val compact = android.widget.RemoteViews(context.packageName, R.layout.notification_cyberos).apply {
            setTextViewText(R.id.notificationTitle, "SYSTEM EVENT // 21:07")
            setTextViewText(R.id.notificationBody, "Secure channel initialized. Terminal is standing by.")
            setOnClickPendingIntent(R.id.notificationAck, ackIntent)
        }
        val expanded = android.widget.RemoteViews(context.packageName, R.layout.notification_cyberos).apply {
            setTextViewText(R.id.notificationTitle, "CYBEROS SECURITY ALERT")
            setTextViewText(R.id.notificationBody, "Secure channel initialized. Open the shell to inspect active modules.")
            setOnClickPendingIntent(R.id.notificationAck, ackIntent)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("CYBEROS SECURITY ALERT")
            .setContentText("Secure channel initialized")
            .setTicker("CYBEROS // secure channel initialized")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .setCustomContentView(compact)
            .setCustomBigContentView(expanded)
            .addAction(android.R.drawable.ic_menu_view, "OPEN SHELL", openAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "ACK", ackIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "CyberOS alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Security events and terminal alerts from CyberOS"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

class CyberNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ACK -> NotificationManagerCompat.from(context).cancel(4210)
            ACTION_OPEN -> {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
        }
    }

    companion object {
        const val ACTION_ACK = "com.cyberos.action.ACK_ALERT"
        const val ACTION_OPEN = "com.cyberos.action.OPEN_SHELL"
    }
}

class FirefoxDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val preferences = context.getSharedPreferences(FirefoxInstaller.PREFERENCES, Context.MODE_PRIVATE)
        if (completedId != preferences.getLong(FirefoxInstaller.DOWNLOAD_ID, -2L)) return

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val openIntent = PendingIntent.getActivity(
            context,
            40,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(FirefoxInstaller.CHANNEL_ID, "CyberOS downloads", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        manager.notify(
            4211,
            NotificationCompat.Builder(context, FirefoxInstaller.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Firefox download ready")
                .setContentText("Open CyberOS Store to authorize installation")
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .build()
        )
    }
}

object FirefoxInstaller {
    const val PREFERENCES = "cyberos_firefox"
    const val DOWNLOAD_ID = "download_id"
    const val CHANNEL_ID = "cyberos_downloads"
    private const val FIREFOX_PACKAGE = "org.mozilla.firefox"
    private const val FIREFOX_APK_URL = "https://download.mozilla.org/?product=fenix-latest&os=android&lang=en-US"

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(FIREFOX_PACKAGE, 0)
    }.isSuccess

    fun launch(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(FIREFOX_PACKAGE) ?: return false
        context.startActivity(launchIntent)
        return true
    }

    fun enqueue(context: Context): Long {
        val request = DownloadManager.Request(Uri.parse(FIREFOX_APK_URL))
            .setTitle("Firefox for Android")
            .setDescription("Downloading the official Firefox installer")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "firefox-android.apk")
        val id = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putLong(DOWNLOAD_ID, id).apply()
        return id
    }

    fun install(context: Context, downloadId: Long): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) return false
        val uri = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).getUriForDownloadedFile(downloadId)
            ?: return false
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return true
    }
}