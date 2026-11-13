package io.github.univjh.pleosapp.domain

import java.time.LocalDate
import java.time.LocalDateTime

enum class TripMode { USUAL, LONG_TRIP }

enum class TempBasis { FORECAST, CAR_SENSOR, SEASON }

/** Temperature the plan was made with, and where it came from. */
data class TempInfo(val planTempC: Double?, val shownTempC: Double?, val basis: TempBasis, val source: String? = null)

/** Everything the recommendation card shows, derived from live car values. */
data class Advice(
    val plan: ChargePlan,
    val forecast: Forecast,
    val forDate: LocalDate,
    val forToday: Boolean,
    val temp: TempInfo,
    val socPercent: Int,
    val efficiencyKmPerKwh: Double,
    /** No usable range/energy pair came from the car, so a typical efficiency is assumed. */
    val efficiencyAssumed: Boolean,
)

object Advisor {
    const val DEFAULT_EFFICIENCY = 5.0
    /** A night is usually colder than the moment the car is parked. */
    const val SENSOR_MARGIN_C = 5.0

    /** Charging after midnight is for the same day's drive; otherwise for tomorrow's. */
    fun drivingDate(now: LocalDateTime): Pair<LocalDate, Boolean> =
        if (now.hour < 4) now.toLocalDate() to true else now.toLocalDate().plusDays(1) to false

    fun efficiency(rangeKm: Double?, energyKwh: Double?): Pair<Double, Boolean> {
        val measured = if (rangeKm != null && energyKwh != null && rangeKm > 0 && energyKwh > 0) rangeKm / energyKwh else null
        return if (measured != null && measured in 2.0..10.0) measured to false else DEFAULT_EFFICIENCY to true
    }

    fun temperature(forecastC: Double?, forecastSource: String?, sensorC: Double?): TempInfo = when {
        forecastC != null -> TempInfo(forecastC, forecastC, TempBasis.FORECAST, forecastSource)
        sensorC != null -> TempInfo(sensorC - SENSOR_MARGIN_C, sensorC, TempBasis.CAR_SENSOR)
        else -> TempInfo(null, null, TempBasis.SEASON)
    }

    fun advise(
        now: LocalDateTime, socPercent: Int, capacityKwh: Double, rangeKm: Double?, energyKwh: Double?,
        records: List<DailyDistance>, usualKm: Int, reservePercent: Int, mode: TripMode,
        forecastC: Double?, forecastSource: String?, sensorC: Double?,
    ): Advice {
        val (date, today) = drivingDate(now)
        val forecast = TripForecaster.forecast(records, date, usualKm.toDouble())
        val (efficiency, assumed) = efficiency(rangeKm, energyKwh)
        val temp = temperature(forecastC, forecastSource, sensorC)
        val plan = ChargePlanner.plan(
            PlanInput(socPercent, capacityKwh, efficiency, forecast.km, temp.planTempC, reservePercent, mode == TripMode.LONG_TRIP),
            month = date.monthValue,
        )
        return Advice(plan, forecast, date, today, temp, socPercent, efficiency, assumed)
    }

    /**
     * The limit the driver set by hand still stands while the car has not moved since.
     * Returns that limit when the countdown must not lower it, else null.
     */
    fun manualToKeep(manualPercent: Int?, manualOdometerKm: Double?, manualTimeMs: Long?, nowMs: Long, odometerKm: Double?, target: Int): Int? {
        if (manualPercent == null || manualTimeMs == null || manualPercent <= target) return null
        if (nowMs - manualTimeMs > 24 * 3_600_000L) return null
        val moved = manualOdometerKm != null && odometerKm != null && kotlin.math.abs(odometerKm - manualOdometerKm) >= 0.5
        return if (moved) null else manualPercent
    }
}
