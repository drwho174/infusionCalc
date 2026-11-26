package com.drwho174.infusioncalc.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Кнопки уведомления: пауза, продолжение и подтверждение уставки. */
class TciActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        if (action !in NOTIFICATION_ACTIONS) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                TciNotificationActions.handle(context.applicationContext, action)
            } finally {
                pending.finish()
            }
        }
    }
}

private val NOTIFICATION_ACTIONS =
    setOf(
        TciNotificationActions.ACTION_PAUSE,
        TciNotificationActions.ACTION_RESUME,
        TciNotificationActions.ACTION_CONFIRM,
        TciNotificationActions.ACTION_STOP,
    )
