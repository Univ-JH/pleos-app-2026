package io.github.univjh.pleosapp.domain

import kotlin.math.ceil

/** What the car and the driver tell us at the moment the charger is about to be plugged in. */
data class PlanInput(
    val socPercent: Int,
    val capacityKwh: Double,
    val efficiencyKmPerKwh: Double,
    val expectedKm: Double,
    /** Forecast low for the next driving day, or null when no weather is available. */
    val minTempC: Double?,
    val reservePercent: Int = 20,
    val longTrip: Boolean = false,
)

enum class PlanKind { EVERYDAY, NEEDS_FULL, LONG_TRIP, UNKNOWN_VEHICLE }

data class ChargePlan(
    val targetPercent: Int,
    /** Battery share the expected drive uses, temperature included, before the reserve. */
    val needPercent: Int,
    val reservePercent: Int,
    val rangeRetained: Double,
    val temperatureAssumed: Boolean,
    val kind: PlanKind,
    /** The current charge already covers the target. */
    val alreadyEnough: Boolean,
    /** Even 100% cannot cover the drive plus the reserve; a charging stop is needed on the way. */
    val exceedsFullCharge: Boolean,
) {
    val neededWithReserve get() = minOf(100, needPercent + reservePercent)
}

/**
 * Picks the charge limit for tonight.
 *
 * Everyday driving stays between 50% and 80% (docs/research/battery-care.html: makers and fleet
 * data agree that long stays at a high state of charge age the battery). It goes to 100% only when
 * the next drive needs it. The result is never below what the drive needs plus the reserve.
 */
object ChargePlanner {
    const val EVERYDAY_FLOOR = 50
    const val EVERYDAY_CAP = 80
    private const val STEP = 5

    /** Share of mild-weather range that remains at a temperature (AAA cold and hot weather tests). */
    private val curve = listOf(-18.0 to 0.46, -7.0 to 0.59, 22.0 to 1.0, 35.0 to 0.86)

    fun rangeRetained(tempC: Double): Double {
        if (tempC <= curve.first().first) return curve.first().second
        if (tempC >= curve.last().first) return curve.last().second
        val (lo, hi) = curve.zipWithNext().first { tempC <= it.second.first }
        return lo.second + (hi.second - lo.second) * (tempC - lo.first) / (hi.first - lo.first)
    }

    /** Used only when there is no forecast: assume the cold end of the season. */
    fun seasonalRetained(month: Int): Double = when (month) {
        12, 1, 2 -> 0.59
        3, 11 -> 0.75
        6, 7, 8 -> 0.86
        else -> 0.9
    }

    fun plan(input: PlanInput, month: Int = java.time.LocalDate.now().monthValue): ChargePlan {
        val retained = input.minTempC?.let(::rangeRetained) ?: seasonalRetained(month)
        val assumed = input.minTempC == null
        if (input.capacityKwh <= 0 || input.efficiencyKmPerKwh <= 0) {
            return ChargePlan(EVERYDAY_CAP, 0, input.reservePercent, retained, assumed, PlanKind.UNKNOWN_VEHICLE,
                input.socPercent >= EVERYDAY_CAP, false)
        }
        val needExact = input.expectedKm.coerceAtLeast(0.0) / input.efficiencyKmPerKwh / retained / input.capacityKwh * 100
        val need = ceil(needExact - 1e-9).toInt()
        val withReserve = need + input.reservePercent
        val (target, kind) = when {
            input.longTrip -> 100 to PlanKind.LONG_TRIP
            withReserve > EVERYDAY_CAP -> 100 to PlanKind.NEEDS_FULL
            else -> roundUp(maxOf(EVERYDAY_FLOOR, withReserve)) to PlanKind.EVERYDAY
        }
        return ChargePlan(target, minOf(need, 100), input.reservePercent, retained, assumed, kind,
            alreadyEnough = input.socPercent >= target, exceedsFullCharge = withReserve > 100)
    }

    /** Rounds a driver-chosen value to a limit the car accepts. */
    fun snap(percent: Int): Int = roundUp(percent.coerceIn(EVERYDAY_FLOOR, 100))

    private fun roundUp(percent: Int) = ((percent + STEP - 1) / STEP * STEP).coerceAtMost(100)
}
