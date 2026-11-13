package io.github.univjh.pleosapp.domain

import java.time.LocalDate
import java.time.LocalDateTime

enum class TripMode { USUAL, LONG_TRIP }

enum class TempBasis { FORECAST, CAR_SENSOR, SEASON }

/** Where the efficiency (km/kWh) used for the plan came from, best first. */
enum class EfficiencySource {
    /** The car's own record since the last charge. */
    CAR_SINCE_CHARGE,
    /** The car's own record over its whole operating period. */
    CAR_CUMULATIVE,
    /** Remaining range divided by remaining energy, both reported by the car. */
    RANGE_ENERGY,
    /** The car reported nothing usable, so a typical value is assumed. */
    DEFAULT,
}

data class Efficiency(val kmPerKwh: Double, val source: EfficiencySource)

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
    val efficiency: Efficiency,
) {
    val efficiencyAssumed: Boolean get() = efficiency.source == EfficiencySource.DEFAULT
}

object Advisor {
    const val DEFAULT_EFFICIENCY = 5.0
    /** A night is usually colder than the moment the car is parked. */
    const val SENSOR_MARGIN_C = 5.0

    /** Charging after midnight is for the same day's drive; otherwise for tomorrow's. */
    fun drivingDate(now: LocalDateTime): Pair<LocalDate, Boolean> =
        if (now.hour < 4) now.toLocalDate() to true else now.toLocalDate().plusDays(1) to false

    /** Efficiencies outside this range are sensor noise or a record over too short a distance. */
    val PLAUSIBLE_EFFICIENCY = 2.0..10.0

    /**
     * The car's recorded efficiency comes first. When it reports both records the lower one is used,
     * since overestimating efficiency would recommend too little charge.
     */
    fun efficiency(sinceCharge: Double?, cumulative: Double?, rangeKm: Double?, energyKwh: Double?): Efficiency {
        val recorded = listOfNotNull(
            sinceCharge?.takeIf { it in PLAUSIBLE_EFFICIENCY }?.let { Efficiency(it, EfficiencySource.CAR_SINCE_CHARGE) },
            cumulative?.takeIf { it in PLAUSIBLE_EFFICIENCY }?.let { Efficiency(it, EfficiencySource.CAR_CUMULATIVE) },
        ).minByOrNull { it.kmPerKwh }
        if (recorded != null) return recorded
        val measured = if (rangeKm != null && energyKwh != null && rangeKm > 0 && energyKwh > 0) rangeKm / energyKwh else null
        return if (measured != null && measured in PLAUSIBLE_EFFICIENCY) Efficiency(measured, EfficiencySource.RANGE_ENERGY)
        else Efficiency(DEFAULT_EFFICIENCY, EfficiencySource.DEFAULT)
    }

    fun temperature(forecastC: Double?, forecastSource: String?, sensorC: Double?): TempInfo = when {
        forecastC != null -> TempInfo(forecastC, forecastC, TempBasis.FORECAST, forecastSource)
        sensorC != null -> TempInfo(sensorC - SENSOR_MARGIN_C, sensorC, TempBasis.CAR_SENSOR)
        else -> TempInfo(null, null, TempBasis.SEASON)
    }

    fun advise(
        now: LocalDateTime, socPercent: Int, capacityKwh: Double, rangeKm: Double?, energyKwh: Double?,
        sinceChargeEfficiency: Double?, cumulativeEfficiency: Double?,
        records: List<DailyDistance>, usualKm: Int, reservePercent: Int, mode: TripMode,
        forecastC: Double?, forecastSource: String?, sensorC: Double?,
    ): Advice {
        val (date, today) = drivingDate(now)
        val forecast = TripForecaster.forecast(records, date, usualKm.toDouble())
        val efficiency = efficiency(sinceChargeEfficiency, cumulativeEfficiency, rangeKm, energyKwh)
        val temp = temperature(forecastC, forecastSource, sensorC)
        val plan = ChargePlanner.plan(
            PlanInput(socPercent, capacityKwh, efficiency.kmPerKwh, forecast.km, temp.planTempC, reservePercent, mode == TripMode.LONG_TRIP),
            month = date.monthValue,
        )
        return Advice(plan, forecast, date, today, temp, socPercent, efficiency)
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
