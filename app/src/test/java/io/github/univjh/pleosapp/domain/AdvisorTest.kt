package io.github.univjh.pleosapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AdvisorTest {
    private val evening = LocalDateTime.of(2026, 11, 12, 19, 42)

    @Test fun `evening charge is for tomorrow and after midnight for the same day`() {
        assertEquals(evening.toLocalDate().plusDays(1) to false, Advisor.drivingDate(evening))
        val late = LocalDateTime.of(2026, 11, 13, 1, 30)
        assertEquals(late.toLocalDate() to true, Advisor.drivingDate(late))
    }

    @Test fun `efficiency comes from the car when range and energy are sane`() {
        assertEquals(118.0 / 24.0 to false, Advisor.efficiency(118.0, 24.0))
        assertEquals(Advisor.DEFAULT_EFFICIENCY to true, Advisor.efficiency(0.0, 24.0))
        assertEquals(Advisor.DEFAULT_EFFICIENCY to true, Advisor.efficiency(null, null))
        assertEquals(Advisor.DEFAULT_EFFICIENCY to true, Advisor.efficiency(900.0, 10.0))
    }

    @Test fun `temperature prefers forecast then the car sensor with a night margin`() {
        assertEquals(TempInfo(-6.0, -6.0, TempBasis.FORECAST, "x"), Advisor.temperature(-6.0, "x", 12.0))
        assertEquals(TempInfo(7.0, 12.0, TempBasis.CAR_SENSOR), Advisor.temperature(null, null, 12.0))
        assertEquals(TempBasis.SEASON, Advisor.temperature(null, null, null).basis)
    }

    @Test fun `advice ties the real values together`() {
        val a = Advisor.advise(evening, 28, 77.4, 118.0, 24.0, emptyList(), 50, 20, TripMode.USUAL, -6.0, "x", 25.0)
        assertEquals(50.0, a.forecast.km, 1e-9)
        assertEquals(ForecastBasis.DRIVER_ANSWER, a.forecast.basis)
        assertFalse(a.efficiencyAssumed)
        // 50 km / 4.92 km/kWh / 0.604 retained / 77.4 kWh = 21.8% -> 22% + 20% -> floor 50.
        assertEquals(22, a.plan.needPercent)
        assertEquals(50, a.plan.targetPercent)
        assertEquals(100, Advisor.advise(evening, 28, 77.4, 118.0, 24.0, emptyList(), 50, 20, TripMode.LONG_TRIP, -6.0, "x", 25.0).plan.targetPercent)
    }

    @Test fun `a hand-set higher limit is kept until the car moves`() {
        val t = 1_000_000_000L
        assertEquals(100, Advisor.manualToKeep(100, 1200.0, t, t + 3_600_000, 1200.2, target = 60))
        assertNull(Advisor.manualToKeep(100, 1200.0, t, t + 3_600_000, 1240.0, target = 60))
        assertNull(Advisor.manualToKeep(100, 1200.0, t, t + 25 * 3_600_000L, 1200.0, target = 60))
        assertNull(Advisor.manualToKeep(55, 1200.0, t, t + 1000, 1200.0, target = 60))
        assertNull(Advisor.manualToKeep(null, null, null, t, 1200.0, target = 60))
        // Without odometer readings the app cannot tell the car moved, so it stays on the safe side.
        assertEquals(100, Advisor.manualToKeep(100, null, t, t + 1000, null, target = 60))
    }
}
