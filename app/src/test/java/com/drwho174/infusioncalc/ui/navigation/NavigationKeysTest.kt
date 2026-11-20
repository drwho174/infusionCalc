package com.drwho174.infusioncalc.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationKeysTest {
    private val json = Json { serializersModule = navSerializersModule }
    private val serializer = PolymorphicSerializer(NavKey::class)

    @Test
    fun `navigation keys survive a polymorphic round trip`() {
        val keys =
            listOf<NavKey>(
                DisclaimerKey,
                WeightKey,
                CatalogKey,
                CalculatorKey("norepinephrine"),
                EditorKey(null),
                EditorKey("norepinephrine"),
                SettingsKey,
                AboutKey,
                FolderSettingsKey,
                TciKey,
                TciModelsKey,
            )

        keys.forEach { key ->
            val encoded = json.encodeToString(serializer, key)
            val decoded = json.decodeFromString(serializer, encoded)
            assertEquals(key, decoded)
        }
    }
}
