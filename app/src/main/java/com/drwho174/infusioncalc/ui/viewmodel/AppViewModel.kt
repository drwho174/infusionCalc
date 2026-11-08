package com.drwho174.infusioncalc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drwho174.infusioncalc.data.settings.AppSettings
import com.drwho174.infusioncalc.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Настройки приложения: тема, язык, принятие дисклеймера. */
class AppViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> =
        settingsRepository.settings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun acceptDisclaimer() {
        viewModelScope.launch { settingsRepository.setDisclaimerAccepted() }
    }

    fun setTheme(value: String) {
        viewModelScope.launch { settingsRepository.setTheme(value) }
    }

    fun setLanguage(value: String) {
        viewModelScope.launch { settingsRepository.setLanguage(value) }
    }
}
