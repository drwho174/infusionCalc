package com.drwho174.infusioncalc.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.drwho174.infusioncalc.InfusionCalcApp
import com.drwho174.infusioncalc.app.AppContainer

@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return (context.applicationContext as InfusionCalcApp).container
}
