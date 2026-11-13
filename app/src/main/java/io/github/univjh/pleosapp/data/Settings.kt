package io.github.univjh.pleosapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

data class UserSettings(
    /** The driver's answer to "how far on a usual day"; null until first launch is finished. */
    val usualKm: Int? = null,
    /** Battery share to keep on arrival. */
    val reservePercent: Int = 20,
    val autoApply: Boolean = true,
    val autoDelaySec: Int = 15,
)

/** Last forecast received, kept so a short network gap does not drop the weather reason. */
data class CachedWeather(val minTempC: Double, val forDate: String, val source: String, val fetchedMs: Long)

class SettingsStore(private val context: Context) {
    private object Keys {
        val usualKm = intPreferencesKey("usual_km")
        val reserve = intPreferencesKey("reserve_percent")
        val autoApply = booleanPreferencesKey("auto_apply")
        val autoDelay = intPreferencesKey("auto_delay_sec")
        val weatherTemp = doublePreferencesKey("weather_min_temp")
        val weatherDate = stringPreferencesKey("weather_date")
        val weatherSource = stringPreferencesKey("weather_source")
        val weatherTime = longPreferencesKey("weather_time")
    }

    val settings: Flow<UserSettings> = context.store.data.map {
        UserSettings(it[Keys.usualKm], it[Keys.reserve] ?: 20, it[Keys.autoApply] ?: true, it[Keys.autoDelay] ?: 15)
    }
    val weather: Flow<CachedWeather?> = context.store.data.map {
        val temp = it[Keys.weatherTemp]; val date = it[Keys.weatherDate]
        if (temp == null || date == null) null
        else CachedWeather(temp, date, it[Keys.weatherSource] ?: "", it[Keys.weatherTime] ?: 0)
    }

    suspend fun setUsualKm(km: Int) { context.store.edit { it[Keys.usualKm] = km } }
    suspend fun setReserve(percent: Int) { context.store.edit { it[Keys.reserve] = percent } }
    suspend fun setAutoApply(on: Boolean) { context.store.edit { it[Keys.autoApply] = on } }
    suspend fun saveWeather(w: CachedWeather) {
        context.store.edit {
            it[Keys.weatherTemp] = w.minTempC; it[Keys.weatherDate] = w.forDate
            it[Keys.weatherSource] = w.source; it[Keys.weatherTime] = w.fetchedMs
        }
    }
}
