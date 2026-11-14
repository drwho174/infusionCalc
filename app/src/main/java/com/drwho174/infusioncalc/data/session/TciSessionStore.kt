package com.drwho174.infusioncalc.data.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Хранилище снимка сессии TCI и её настроек (сценарий B: переживаем убийство процесса). */
@Suppress("TooManyFunctions")
interface TciSessionStore {
    suspend fun load(): TciSessionSnapshot?

    suspend fun save(snapshot: TciSessionSnapshot)

    suspend fun clear()

    suspend fun isDisclaimerHidden(): Boolean

    suspend fun setDisclaimerHidden(hidden: Boolean)

    suspend fun loadBolusRateMlPerHour(): java.math.BigDecimal?

    suspend fun saveBolusRateMlPerHour(value: java.math.BigDecimal)

    suspend fun loadTableStepMinutes(): Int?

    suspend fun saveTableStepMinutes(value: Int)

    suspend fun loadWarningLeadMinutes(): Int?

    suspend fun saveWarningLeadMinutes(value: Int)
}

private val Context.tciSessionDataStore by preferencesDataStore(name = "tci_session")

@Suppress("TooManyFunctions")
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

    override suspend fun isDisclaimerHidden(): Boolean =
        runCatching {
            context.tciSessionDataStore.data.first()[Keys.disclaimerHidden] ?: false
        }.getOrDefault(false)

    override suspend fun setDisclaimerHidden(hidden: Boolean) {
        context.tciSessionDataStore.edit { prefs -> prefs[Keys.disclaimerHidden] = hidden }
    }

    override suspend fun loadBolusRateMlPerHour(): java.math.BigDecimal? =
        runCatching {
            context.tciSessionDataStore.data
                .first()[Keys.bolusRate]
                ?.toBigDecimalOrNull()
        }.getOrNull()

    override suspend fun saveBolusRateMlPerHour(value: java.math.BigDecimal) {
        context.tciSessionDataStore.edit { prefs -> prefs[Keys.bolusRate] = value.toPlainString() }
    }

    override suspend fun loadTableStepMinutes(): Int? =
        runCatching {
            context.tciSessionDataStore.data.first()[Keys.tableStepMinutes]
        }.getOrNull()

    override suspend fun saveTableStepMinutes(value: Int) {
        context.tciSessionDataStore.edit { prefs -> prefs[Keys.tableStepMinutes] = value }
    }

    override suspend fun loadWarningLeadMinutes(): Int? =
        runCatching {
            context.tciSessionDataStore.data.first()[Keys.warningLeadMinutes]
        }.getOrNull()

    override suspend fun saveWarningLeadMinutes(value: Int) {
        context.tciSessionDataStore.edit { prefs -> prefs[Keys.warningLeadMinutes] = value }
    }

    private object Keys {
        val snapshot = stringPreferencesKey("snapshot")
        val disclaimerHidden = booleanPreferencesKey("disclaimer_hidden")
        val bolusRate = stringPreferencesKey("bolus_rate_ml_h")
        val tableStepMinutes = intPreferencesKey("table_step_minutes")
        val warningLeadMinutes = intPreferencesKey("warning_lead_minutes")
    }
}
