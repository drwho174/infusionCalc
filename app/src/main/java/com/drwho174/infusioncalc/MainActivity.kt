package com.drwho174.infusioncalc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drwho174.infusioncalc.data.settings.AppSettings
import com.drwho174.infusioncalc.ui.AppNav
import com.drwho174.infusioncalc.ui.theme.InfusionCalcTheme
import com.drwho174.infusioncalc.ui.viewmodel.AppViewModel
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var openTci by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Locale.setDefault(resources.configuration.locales[0])
        openTci = intent?.getBooleanExtra(EXTRA_OPEN_TCI, false) == true
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = koinViewModel()
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent
            val darkTheme =
                when (current.theme) {
                    AppSettings.THEME_LIGHT -> false
                    AppSettings.THEME_DARK -> true
                    else -> isSystemInDarkTheme()
                }
            InfusionCalcTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNav(
                        disclaimerAccepted = current.disclaimerAccepted,
                        onAcceptDisclaimer = appViewModel::acceptDisclaimer,
                        openTci = openTci,
                        onOpenTciConsumed = { openTci = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_TCI, false)) {
            openTci = true
        }
    }

    companion object {
        const val EXTRA_OPEN_TCI = "open_tci"
    }
}
