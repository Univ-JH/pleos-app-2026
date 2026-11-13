package io.github.univjh.pleosapp.domain

import java.time.LocalDate

/** Odometer readings seen on one calendar day. */
data class OdometerDay(val date: LocalDate, val firstKm: Double, val lastKm: Double)

data class DailyDistance(val date: LocalDate, val km: Double)

enum class ForecastBasis { WEEKDAY_AVERAGE, RECENT_AVERAGE, DRIVER_ANSWER }

data class Forecast(val km: Double, val basis: ForecastBasis, val samples: Int)

/**
 * Guesses how far the car will drive on a given day from what it actually drove before.
 * Until enough real days are recorded it uses the one answer the driver gave at first launch.
 */
object TripForecaster {
    private const val WEEKDAY_WEEKS = 8L
    private const val WEEKDAY_MIN_SAMPLES = 3
    private const val RECENT_DAYS = 14L
    private const val RECENT_MIN_SAMPLES = 2

    fun dailyDistances(days: List<OdometerDay>): List<DailyDistance> {
        val sorted = days.sortedBy { it.date }
        return sorted.mapIndexed { i, day ->
            // The car may have moved between the last reading of the previous day and today's first.
            val start = if (i > 0) minOf(sorted[i - 1].lastKm, day.firstKm) else day.firstKm
            DailyDistance(day.date, (day.lastKm - start).coerceAtLeast(0.0))
        }
    }

    fun forecast(records: List<DailyDistance>, forDate: LocalDate, usualKm: Double): Forecast {
        val past = records.filter { it.date < forDate }
        val sameWeekday = past.filter {
            it.date.dayOfWeek == forDate.dayOfWeek && it.date >= forDate.minusWeeks(WEEKDAY_WEEKS)
        }
        if (sameWeekday.size >= WEEKDAY_MIN_SAMPLES) {
            return Forecast(floor(sameWeekday.map { it.km }.average(), usualKm), ForecastBasis.WEEKDAY_AVERAGE, sameWeekday.size)
        }
        val recent = past.filter { it.date >= forDate.minusDays(RECENT_DAYS) }
        if (recent.size >= RECENT_MIN_SAMPLES) {
            return Forecast(floor(recent.map { it.km }.average(), usualKm), ForecastBasis.RECENT_AVERAGE, recent.size)
        }
        return Forecast(usualKm, ForecastBasis.DRIVER_ANSWER, 0)
    }

    /** A handful of short days must not talk the planner into an unsafe low charge. */
    private fun floor(learnedKm: Double, usualKm: Double) = maxOf(learnedKm, usualKm / 2)
}
