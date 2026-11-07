package com.drwho174.infusioncalc.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val disclaimerAccepted: Boolean = false,
    val theme: String = THEME_SYSTEM,
    val language: String = LANGUAGE_SYSTEM,
) {
    companion object {
        const val THEME_SYSTEM = "SYSTEM"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_DARK = "DARK"
        const val LANGUAGE_SYSTEM = "SYSTEM"
    }
}

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val disclaimer = booleanPreferencesKey("disclaimer_accepted")
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            disclaimerAccepted = prefs[Keys.disclaimer] ?: false,
            theme = prefs[Keys.theme] ?: AppSettings.THEME_SYSTEM,
            language = prefs[Keys.language] ?: AppSettings.LANGUAGE_SYSTEM,
        )
    }

    suspend fun setDisclaimerAccepted() {
        context.settingsDataStore.edit { it[Keys.disclaimer] = true }
    }

    suspend fun setTheme(value: String) {
        context.settingsDataStore.edit { it[Keys.theme] = value }
    }

    suspend fun setLanguage(value: String) {
        context.settingsDataStore.edit { it[Keys.language] = value }
    }
}
