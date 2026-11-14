package com.drwho174.infusioncalc.notif

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.drwho174.infusioncalc.MainActivity
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.tci.TargetMode
import com.drwho174.infusioncalc.tci.TciEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground-сервис живой сессии TCI: раз в секунду обновляет уведомление
 * с концентрацией по выбранному режиму, обратным отсчётом до коррекции
 * и звуковым/вибрационным сигналом в момент коррекции.
 */
private const val TICK_INTERVAL_MS = 1_000L
private const val SDK_CHANNEL = 26
private const val SDK_SPECIAL_USE = 34
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

@Suppress("TooManyFunctions")
class TciSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private var lastAlertedOffset: Int? = null
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val model = TciNotificationBus.model
        if (model == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val content = evaluateTciNotification(model, System.currentTimeMillis())
        if (!foregroundStarted) {
            startForegroundCompat(build(model, content, alert = false))
        }
        if (ticker == null) {
            ticker =
                scope.launch {
                    while (isActive) {
                        delay(TICK_INTERVAL_MS)
                        val current = TciNotificationBus.model ?: break
                        render(current)
                    }
                    stopSelf()
                }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun render(model: TciNotificationModel) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val content = evaluateTciNotification(model, System.currentTimeMillis())
        val due = content.dueOffsetSeconds
        val alert = due != null && due != lastAlertedOffset
        if (due != null) lastAlertedOffset = due
        val notification = build(model, content, alert)
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("LongMethod")
    private fun build(
        model: TciNotificationModel,
        content: TciNotificationContent,
        alert: Boolean,
    ): Notification {
        val concentration =
            content.concentrationMcgPerMl
                ?.let { TciEngine.formatConcentrationMcgMl(it).toPlainString() }
                ?: "—"
        val title =
            if (model.mode == TargetMode.PLASMA) {
                getString(R.string.tci_notif_title_cp, concentration)
            } else {
                getString(R.string.tci_notif_title_ce, concentration)
            }
        val text =
            when {
                content.paused -> getString(R.string.tci_notif_paused)
                content.dueOffsetSeconds != null ->
                    getString(R.string.tci_notif_due, formatRate(content.currentRateMlPerHour))
                content.nextInSeconds != null ->
                    getString(
                        R.string.tci_notif_next,
                        formatCountdown(content.nextInSeconds),
                        formatRate(content.nextRateMlPerHour ?: content.currentRateMlPerHour),
                    )
                else -> getString(R.string.tci_notif_steady, formatRate(content.currentRateMlPerHour))
            }
        val openApp =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pending =
            PendingIntent.getActivity(
                this,
                0,
                openApp,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val builder =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tci_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(!alert)
                .setSilent(!alert)
                .setContentIntent(pending)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (!content.paused) {
            builder.addAction(
                R.drawable.ic_tci_notification,
                getString(R.string.tci_notif_action_pause),
                actionPending(TciNotificationActions.ACTION_PAUSE, 1),
            )
            if (content.dueOffsetSeconds != null) {
                builder.addAction(
                    R.drawable.ic_tci_notification,
                    getString(R.string.tci_notif_action_confirm),
                    actionPending(TciNotificationActions.ACTION_CONFIRM, 2),
                )
            }
        }
        return builder.build()
    }

    private fun actionPending(
        action: String,
        requestCode: Int,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            requestCode,
            Intent(this, TciActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= SDK_SPECIAL_USE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < SDK_CHANNEL) return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tci_notif_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.tci_notif_channel_desc)
                enableVibration(true)
                setShowBadge(false)
            }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun formatRate(value: java.math.BigDecimal): String = TciEngine.formatRateMlPerHour(value).toPlainString()

    private fun formatCountdown(seconds: Int): String {
        val hours = seconds / SECONDS_PER_HOUR
        val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val rest = seconds % SECONDS_PER_MINUTE
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, rest)
        } else {
            "%02d:%02d".format(minutes, rest)
        }
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "tci_session"
        const val NOTIFICATION_ID = 7101
    }
}
