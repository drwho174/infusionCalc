package com.drwho174.infusioncalc.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object DisclaimerKey : NavKey

@Serializable
data object WeightKey : NavKey

@Serializable
data object CatalogKey : NavKey

@Serializable
data class CalculatorKey(
    val drugId: String,
) : NavKey

@Serializable
data class EditorKey(
    val drugId: String? = null,
) : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object AboutKey : NavKey

@Serializable
data object FolderSettingsKey : NavKey

@Serializable
data object TciKey : NavKey

@Serializable
data object TciModelsKey : NavKey
