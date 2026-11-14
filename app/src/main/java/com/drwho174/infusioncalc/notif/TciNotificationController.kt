package com.drwho174.infusioncalc.notif

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Запускает/останавливает foreground-сервис уведомления TCI.
 * Если уведомления выключены пользователем, сервис не стартует.
 */
class TciNotificationController(
    private val context: Context,
) : TciNotifier {
    override fun update(model: TciNotificationModel) {
        TciNotificationBus.model = model
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val intent = Intent(context, TciSessionService::class.java)
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    override fun stop() {
        TciNotificationBus.model = null
        context.stopService(Intent(context, TciSessionService::class.java))
        runCatching {
            NotificationManagerCompat
                .from(context)
                .cancel(TciSessionService.NOTIFICATION_ID)
        }
    }
}
