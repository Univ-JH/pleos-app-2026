package io.github.univjh.pleosapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargePlannerTest {
    private fun input(
        km: Double = 50.0, temp: Double? = 22.0, soc: Int = 30, reserve: Int = 20,
        longTrip: Boolean = false, efficiency: Double = 5.0, capacity: Double = 77.4,
    ) = PlanInput(soc, capacity, efficiency, km, temp, reserve, longTrip)

    @Test fun `mild day short commute stays at the 50 percent floor`() {
        val plan = ChargePlanner.plan(input(km = 50.0, temp = 22.0))
        // 50 km / 5 km/kWh = 10 kWh = 12.9% -> 13%, plus 20% reserve = 33% -> floor 50.
        assertEquals(13, plan.needPercent)
        assertEquals(50, plan.targetPercent)
        assertEquals(PlanKind.EVERYDAY, plan.kind)
    }

    @Test fun `cold morning raises the need by the range curve`() {
        val warm = ChargePlanner.plan(input(km = 120.0, temp = 22.0))
        val cold = ChargePlanner.plan(input(km = 120.0, temp = -7.0))
        // 120 km = 24 kWh = 31.01% -> rounded up to 32%; at -7C only 59% of range remains -> 31.01 / 0.59 = 52.6% -> 53%.
        assertEquals(32, warm.needPercent)
        assertEquals(53, cold.needPercent)
        assertEquals(55, warm.targetPercent)
        assertEquals(75, cold.targetPercent)
    }

    @Test fun `temperature curve interpolates and clamps`() {
        assertEquals(1.0, ChargePlanner.rangeRetained(22.0), 1e-9)
        assertEquals(0.59, ChargePlanner.rangeRetained(-7.0), 1e-9)
        assertEquals(0.46, ChargePlanner.rangeRetained(-30.0), 1e-9)
        assertEquals(0.86, ChargePlanner.rangeRetained(40.0), 1e-9)
        assertEquals((0.59 + 1.0) / 2, ChargePlanner.rangeRetained(7.5), 1e-9)
    }

    @Test fun `need above the everyday cap goes to full charge`() {
        val plan = ChargePlanner.plan(input(km = 260.0, temp = 22.0))
        // 260 km = 52 kWh = 67.2% -> 68% + 20% = 88% > 80.
        assertEquals(100, plan.targetPercent)
        assertEquals(PlanKind.NEEDS_FULL, plan.kind)
    }

    @Test fun `long trip tab always recommends full charge`() {
        val plan = ChargePlanner.plan(input(km = 30.0, longTrip = true))
        assertEquals(100, plan.targetPercent)
        assertEquals(PlanKind.LONG_TRIP, plan.kind)
    }

    @Test fun `target is a supported 5 percent step between 50 and 100`() {
        for (km in 0..600 step 7) for (temp in listOf(-20.0, -7.0, 5.0, 22.0, 35.0, null)) {
            val plan = ChargePlanner.plan(input(km = km.toDouble(), temp = temp))
            assertTrue("$km km $temp", plan.targetPercent in 50..100 && plan.targetPercent % 5 == 0)
        }
    }

    @Test fun `never recommends less than need plus reserve`() {
        for (km in 0..600 step 7) for (temp in listOf(-20.0, -7.0, 5.0, 22.0, 35.0, null)) for (reserve in listOf(10, 20, 30)) {
            val plan = ChargePlanner.plan(input(km = km.toDouble(), temp = temp, reserve = reserve))
            assertTrue("$km km $temp reserve $reserve -> ${plan.targetPercent}",
                plan.targetPercent >= minOf(100, plan.needPercent + reserve))
        }
    }

    @Test fun `flags a trip that even a full battery cannot cover with the reserve`() {
        val plan = ChargePlanner.plan(input(km = 380.0, temp = -7.0))
        assertEquals(100, plan.targetPercent)
        assertTrue(plan.exceedsFullCharge)
        assertFalse(ChargePlanner.plan(input(km = 260.0)).exceedsFullCharge)
    }

    @Test fun `missing forecast uses a conservative seasonal factor`() {
        val unknownWinter = ChargePlanner.plan(input(km = 120.0, temp = null), month = 1)
        val unknownSummer = ChargePlanner.plan(input(km = 120.0, temp = null), month = 7)
        assertEquals(53, unknownWinter.needPercent)
        assertEquals(37, unknownSummer.needPercent)
        assertTrue(unknownWinter.temperatureAssumed)
        assertFalse(ChargePlanner.plan(input(temp = 3.0)).temperatureAssumed)
    }

    @Test fun `already charged enough is reported without lowering the target`() {
        val plan = ChargePlanner.plan(input(km = 50.0, soc = 72))
        assertEquals(50, plan.targetPercent)
        assertTrue(plan.alreadyEnough)
        assertFalse(ChargePlanner.plan(input(km = 50.0, soc = 30)).alreadyEnough)
    }

    @Test fun `bad vehicle values fall back to the safe side`() {
        val plan = ChargePlanner.plan(input(efficiency = 0.0, capacity = 0.0))
        assertEquals(80, plan.targetPercent)
        assertEquals(PlanKind.UNKNOWN_VEHICLE, plan.kind)
    }
}
