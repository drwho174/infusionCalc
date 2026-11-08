package com.drwho174.infusioncalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drwho174.infusioncalc.data.settings.AppSettings
import com.drwho174.infusioncalc.ui.AppNav
import com.drwho174.infusioncalc.ui.theme.InfusionCalcTheme
import com.drwho174.infusioncalc.ui.viewmodel.AppViewModel
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    )
                }
            }
        }
    }
}
