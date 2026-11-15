package com.drwho174.infusioncalc.ui

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import com.drwho174.infusioncalc.data.settings.AppSettings

/**
 * Переключение языка приложения. На Android 13+ используется системный
 * per-app language API; на более старых версиях выбор языка недоступен.
 */
fun applyAppLanguage(
    context: Context,
    language: String,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val locales =
        when (language) {
            AppSettings.LANGUAGE_RU -> LocaleList.forLanguageTags("ru")
            AppSettings.LANGUAGE_EN -> LocaleList.forLanguageTags("en")
            else -> LocaleList.getEmptyLocaleList()
        }
    context.getSystemService(LocaleManager::class.java)?.applicationLocales = locales
}
