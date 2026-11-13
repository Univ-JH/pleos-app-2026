package io.github.univjh.pleosapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TripForecasterTest {
    private val thursday = LocalDate.of(2026, 11, 12)
    private fun day(daysAgo: Long, km: Double) = DailyDistance(thursday.minusDays(daysAgo), km)

    @Test fun `no records uses the driver's own answer`() {
        val f = TripForecaster.forecast(emptyList(), forDate = thursday.plusDays(1), usualKm = 50.0)
        assertEquals(50.0, f.km, 1e-9)
        assertEquals(ForecastBasis.DRIVER_ANSWER, f.basis)
    }

    @Test fun `three same weekdays use the weekday average`() {
        // Forecast for Friday 11/13; previous Fridays are 6, 13, 20 days before Thursday 11/12.
        val records = listOf(day(6, 40.0), day(13, 60.0), day(20, 50.0), day(1, 300.0))
        val f = TripForecaster.forecast(records, forDate = thursday.plusDays(1), usualKm = 30.0)
        assertEquals(50.0, f.km, 1e-9)
        assertEquals(ForecastBasis.WEEKDAY_AVERAGE, f.basis)
        assertEquals(3, f.samples)
    }

    @Test fun `weekday average only looks back eight weeks`() {
        val records = listOf(day(6, 40.0), day(13, 60.0), day(6 + 7 * 9, 500.0))
        val f = TripForecaster.forecast(records, forDate = thursday.plusDays(1), usualKm = 30.0)
        assertEquals(ForecastBasis.RECENT_AVERAGE, f.basis)
    }

    @Test fun `two recent days use the recent average`() {
        val records = listOf(day(1, 30.0), day(2, 50.0), day(40, 400.0))
        val f = TripForecaster.forecast(records, forDate = thursday.plusDays(1), usualKm = 80.0)
        assertEquals(40.0, f.km, 1e-9)
        assertEquals(ForecastBasis.RECENT_AVERAGE, f.basis)
        assertEquals(2, f.samples)
    }

    @Test fun `one recent day is not enough`() {
        val f = TripForecaster.forecast(listOf(day(1, 30.0)), forDate = thursday.plusDays(1), usualKm = 80.0)
        assertEquals(ForecastBasis.DRIVER_ANSWER, f.basis)
    }

    @Test fun `learned averages never go below the driver's answer by more than half`() {
        // A few short days must not produce an unsafe low forecast.
        val records = listOf(day(1, 2.0), day(2, 3.0))
        val f = TripForecaster.forecast(records, forDate = thursday.plusDays(1), usualKm = 50.0)
        assertEquals(25.0, f.km, 1e-9)
    }

    @Test fun `odometer snapshots become daily distances`() {
        val snaps = listOf(
            OdometerDay(thursday.minusDays(2), 1000.0, 1040.0),
            OdometerDay(thursday.minusDays(1), 1040.0, 1040.0),
            OdometerDay(thursday, 1045.0, 1100.0),
        )
        val days = TripForecaster.dailyDistances(snaps)
        // Distance driven before the first reading of a day counts for that day.
        assertEquals(listOf(40.0, 0.0, 60.0), days.map { it.km })
    }
}
