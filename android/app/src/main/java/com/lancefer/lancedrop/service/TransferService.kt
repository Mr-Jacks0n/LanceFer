package com.lancefer.lancedrop.service

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.lancefer.lancedrop.model.TransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class TransferService : Service() {

    private val binder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private lateinit var notificationManager: NotificationManager

    private var currentFileName: String = "Fichier"
    private var currentProgress: Int = 0
    private var currentSpeed: String = ""
    private var isPaused: Boolean = false

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): TransferService = this@TransferService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRANSFER -> {
                currentFileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "Fichier"
                currentProgress = 0
                currentSpeed = "Calcul..."
                isPaused = false
                val notif = buildNotification(currentFileName, currentProgress, currentSpeed, isPaused)
                startForegroundServiceWithNotification(notif)
            }
            ACTION_UPDATE_PROGRESS -> {
                currentFileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: currentFileName
                currentProgress = intent.getIntExtra(EXTRA_PROGRESS_PERCENT, currentProgress)
                currentSpeed = intent.getStringExtra(EXTRA_SPEED_TEXT) ?: currentSpeed
                isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, isPaused)
                val notif = buildNotification(currentFileName, currentProgress, currentSpeed, isPaused)
                notificationManager.notify(NOTIFICATION_ID, notif)
            }
            ACTION_PAUSE_TRANSFER -> {
                isPaused = true
                _actionEvents.tryEmit(ACTION_PAUSE_TRANSFER)
                val notif = buildNotification(currentFileName, currentProgress, currentSpeed, isPaused)
                notificationManager.notify(NOTIFICATION_ID, notif)
            }
            ACTION_RESUME_TRANSFER -> {
                isPaused = false
                _actionEvents.tryEmit(ACTION_RESUME_TRANSFER)
                val notif = buildNotification(currentFileName, currentProgress, currentSpeed, isPaused)
                notificationManager.notify(NOTIFICATION_ID, notif)
            }
            ACTION_CANCEL_TRANSFER -> {
                _actionEvents.tryEmit(ACTION_CANCEL_TRANSFER)
                stopForegroundService()
            }
            ACTION_STOP_TRANSFER -> {
                stopForegroundService()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Transferts LanceDrop",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Notifications d'état de transfert de fichiers en arrière-plan"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        fileName: String,
        progressPercent: Int,
        speedText: String,
        paused: Boolean
    ): Notification {
        val pauseResumeAction = if (paused) {
            val resumeIntent = Intent(this, TransferService::class.java).apply {
                action = ACTION_RESUME_TRANSFER
            }
            val resumePendingIntent = PendingIntent.getService(
                this,
                101,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                R.drawable.ic_media_play,
                "Reprendre",
                resumePendingIntent
            ).build()
        } else {
            val pauseIntent = Intent(this, TransferService::class.java).apply {
                action = ACTION_PAUSE_TRANSFER
            }
            val pausePendingIntent = PendingIntent.getService(
                this,
                102,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                R.drawable.ic_media_pause,
                "Pause",
                pausePendingIntent
            ).build()
        }

        val cancelIntent = Intent(this, TransferService::class.java).apply {
            action = ACTION_CANCEL_TRANSFER
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            103,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelAction = NotificationCompat.Action.Builder(
            R.drawable.ic_menu_close_clear_cancel,
            "Annuler",
            cancelPendingIntent
        ).build()

        val title = if (paused) "Transfert en pause : $fileName" else "Transfert : $fileName"
        val content = if (paused) "En pause ($progressPercent%)" else "$progressPercent% • $speedText"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.stat_sys_download)
            .setProgress(100, progressPercent.coerceIn(0, 100), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(pauseResumeAction)
            .addAction(cancelAction)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LanceDrop::TransferWakeLock",
        ).apply { acquire() }

        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "LanceDrop::TransferWifiLock",
        ).apply { acquire() }
    }

    private fun releaseLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseLocks()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "lancedrop_transfer_channel"
        const val NOTIFICATION_ID = 4242

        const val ACTION_START_TRANSFER = "com.lancefer.lancedrop.action.START_TRANSFER"
        const val ACTION_UPDATE_PROGRESS = "com.lancefer.lancedrop.action.UPDATE_PROGRESS"
        const val ACTION_PAUSE_TRANSFER = "com.lancefer.lancedrop.action.PAUSE_TRANSFER"
        const val ACTION_RESUME_TRANSFER = "com.lancefer.lancedrop.action.RESUME_TRANSFER"
        const val ACTION_CANCEL_TRANSFER = "com.lancefer.lancedrop.action.CANCEL_TRANSFER"
        const val ACTION_STOP_TRANSFER = "com.lancefer.lancedrop.action.STOP_TRANSFER"

        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_PROGRESS_PERCENT = "extra_progress_percent"
        const val EXTRA_SPEED_TEXT = "extra_speed_text"
        const val EXTRA_IS_PAUSED = "extra_is_paused"

        private val _actionEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
        val actionEvents: SharedFlow<String> = _actionEvents.asSharedFlow()
    }
}
