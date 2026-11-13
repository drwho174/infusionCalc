package com.drwho174.infusioncalc.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Хранилище снимка сессии TCI (сценарий B: переживаем убийство процесса). */
interface TciSessionStore {
    suspend fun load(): TciSessionSnapshot?

    suspend fun save(snapshot: TciSessionSnapshot)

    suspend fun clear()
}

private val Context.tciSessionDataStore by preferencesDataStore(name = "tci_session")

class DataStoreTciSessionStore(
    private val context: Context,
) : TciSessionStore {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override suspend fun load(): TciSessionSnapshot? =
        runCatching {
            val raw = context.tciSessionDataStore.data.first()[Keys.snapshot] ?: return null
            json
                .decodeFromString<TciSessionSnapshot>(raw)
                .takeIf { it.version == TciSessionSnapshot.CURRENT_VERSION }
        }.getOrNull()

    override suspend fun save(snapshot: TciSessionSnapshot) {
        context.tciSessionDataStore.edit { prefs ->
            prefs[Keys.snapshot] = json.encodeToString(snapshot)
        }
    }

    override suspend fun clear() {
        context.tciSessionDataStore.edit { prefs -> prefs.remove(Keys.snapshot) }
    }

    private object Keys {
        val snapshot = stringPreferencesKey("snapshot")
    }
}
