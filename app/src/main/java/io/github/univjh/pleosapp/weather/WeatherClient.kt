package io.github.univjh.pleosapp.weather

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

data class WeatherReading(val minTempC: Double, val source: String)

/**
 * Fetches the forecast low for one day. Two public sources are tried in order because either can be
 * unreachable (docs/qa/SPIKE.md); neither needs an account or a key.
 */
class WeatherClient {
    suspend fun minTemp(lat: Double, lon: Double, date: LocalDate): WeatherReading? = withContext(Dispatchers.IO) {
        sources.firstNotNullOfOrNull { (name, fetch) ->
            runCatching { fetch(lat, lon, date) }
                .onFailure { Log.w(TAG, "$name failed: $it") }
                .getOrNull()?.let { WeatherReading(it, name) }
        }
    }

    private val sources: List<Pair<String, (Double, Double, LocalDate) -> Double?>> = listOf(
        "Open-Meteo" to ::openMeteo,
        "MET Norway" to ::metNorway,
    )

    private fun openMeteo(lat: Double, lon: Double, date: LocalDate): Double? {
        val json = get("https://api.open-meteo.com/v1/forecast?latitude=${coord(lat)}&longitude=${coord(lon)}" +
            "&daily=temperature_2m_min&timezone=auto&start_date=$date&end_date=$date")
        val daily = JSONObject(json).getJSONObject("daily").getJSONArray("temperature_2m_min")
        return if (daily.length() > 0 && !daily.isNull(0)) daily.getDouble(0) else null
    }

    private fun metNorway(lat: Double, lon: Double, date: LocalDate): Double? {
        val json = get("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=${coord(lat)}&lon=${coord(lon)}")
        val series = JSONObject(json).getJSONObject("properties").getJSONArray("timeseries")
        val zone = ZoneId.systemDefault()
        var min: Double? = null
        for (i in 0 until series.length()) {
            val entry = series.getJSONObject(i)
            if (OffsetDateTime.parse(entry.getString("time")).atZoneSameInstant(zone).toLocalDate() != date) continue
            val temp = entry.getJSONObject("data").getJSONObject("instant").getJSONObject("details").getDouble("air_temperature")
            min = min?.coerceAtMost(temp) ?: temp
        }
        return min
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5_000; c.readTimeout = 5_000
        c.setRequestProperty("User-Agent", "ev-charge-planner-android/0.1")
        try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    /** Two decimals is about 1 km: enough for weather and coarse enough not to pinpoint a home. */
    private fun coord(v: Double) = String.format(Locale.US, "%.2f", v)

    private companion object { const val TAG = "ChargeWeather" }
}
