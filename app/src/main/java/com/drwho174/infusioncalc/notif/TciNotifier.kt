package com.drwho174.infusioncalc.notif

/** Мост между ViewModel и foreground-сервисом в пределах одного процесса. */
object TciNotificationBus {
    @Volatile
    var model: TciNotificationModel? = null

    /** Растёт, когда действие из уведомления изменило сессию. */
    @Volatile
    var revision: Int = 0
}

/** Уведомления TCI; no-op используется в тестах и при выключенных уведомлениях. */
interface TciNotifier {
    fun update(model: TciNotificationModel)

    fun stop()
}

object NoopTciNotifier : TciNotifier {
    override fun update(model: TciNotificationModel) = Unit

    override fun stop() = Unit
}
