package io.github.univjh.pleosapp

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.app.NotificationCompat
import androidx.room.Room
import io.github.univjh.pleosapp.data.CachedWeather
import io.github.univjh.pleosapp.data.ChargeDatabase
import io.github.univjh.pleosapp.data.SettingsStore
import io.github.univjh.pleosapp.domain.Advisor
import io.github.univjh.pleosapp.vehicle.EnergySource
import io.github.univjh.pleosapp.voice.Speaker
import io.github.univjh.pleosapp.weather.WeatherClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class ChargeApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val energy by lazy { EnergySource(this) }
    val settings by lazy { SettingsStore(this) }
    val database by lazy { Room.databaseBuilder(this, ChargeDatabase::class.java, "charge.db").build() }
    val speaker by lazy { Speaker(this) }
    private val weatherClient = WeatherClient()

    /** Fires when the charge port opens while parked: the moment just before plugging in. */
    val plugMoments = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** True while the activity is on screen; decides between showing the card and notifying. */
    @Volatile var visible = false

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH))
        scope.launch {
            var wasOpen: Boolean? = null
            energy.state.map { it.portOpen to (it.parked && !it.driving) }.distinctUntilChanged().collect { (open, parked) ->
                // The first value is the state at connection, not a change the driver just made.
                if (wasOpen == false && open && parked) {
                    if (visible) plugMoments.tryEmit(Unit) else notifyPlugMoment()
                }
                wasOpen = open
            }
        }
        scope.launch {
            energy.state.map { it.odometerKm }.distinctUntilChanged().collect { km ->
                if (km != null && km > 0) database.dao().recordOdometer(LocalDate.now().toString(), km)
            }
        }
    }

    /** Fetches the forecast for the next driving day unless a fresh one is already stored. */
    fun refreshWeather(force: Boolean = false) = scope.launch {
        val (date, _) = Advisor.drivingDate(LocalDateTime.now())
        val cached = settings.weather.first()
        val fresh = cached != null && cached.forDate == date.toString() && System.currentTimeMillis() - cached.fetchedMs < 3 * 3_600_000L
        if (fresh && !force) return@launch
        val here = lastLocation() ?: return@launch
        val reading = weatherClient.minTemp(here.first, here.second, date) ?: return@launch
        settings.saveWeather(CachedWeather(reading.minTempC, date.toString(), reading.source, System.currentTimeMillis()))
    }

    private fun lastLocation(): Pair<Double, Double>? {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val manager = getSystemService(LocationManager::class.java)
        return manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }?.let { it.latitude to it.longitude }
    }

    private fun notifyPlugMoment() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).putExtra(EXTRA_SHOW_CARD, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.notify_title))
            .setContentText(getString(R.string.notify_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(open).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(1, notification)
    }

    companion object {
        const val CHANNEL = "charge_advice"
        const val EXTRA_SHOW_CARD = "show_card"
    }
}
