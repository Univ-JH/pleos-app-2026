package io.github.univjh.pleosapp.domain

import org.junit.Assert.*
import org.junit.Test

class DrivingTest {
    private fun frame(t: Long, speed: Double = 0.0, gear: Gear = Gear.DRIVE, warning: Boolean = false, valid: Boolean = true) =
        VehicleFrame(t, 1_000_000 + t, speed, gear, mapOf("앞좌석 왼쪽" to warning), valid)
    private fun launch(acceleration: Double): List<DetectedEvent> {
        val d = DrivingEventDetector()
        return (0L..70L).flatMap { i -> d.accept(frame(i*100, if (i <= 25) 0.0 else (i-25)*.1*acceleration)) }
    }
    @Test fun normalAndBoundaryLaunch() {
        assertTrue(launch(2.9).isEmpty())
        assertEquals(1, launch(3.0).count { it.kind == EventKind.RAPID_START })
        assertEquals(1, launch(3.1).count { it.kind == EventKind.RAPID_START })
        assertEquals(1, launch(4.0).count { it.kind == EventKind.RAPID_START })
    }
    @Test fun launchHasOnlyOneEventDespiteThresholdFluctuation() {
        val d=DrivingEventDetector(RuleConfig(launchWindowMs=10_000))
        var speed=0.0
        val events=(0L..110L).flatMap { i ->
            if(i>25) speed += if(i in 51..63) .1 else .4
            d.accept(frame(i*100,speed))
        }
        assertEquals(1,events.count { it.kind==EventKind.RAPID_START })
    }
    @Test fun brakeEpisodeDoesNotRepeatAfterBriefThresholdNoise() {
        val d=DrivingEventDetector(); var speed=60.0
        val events=(0L..100L).flatMap { i ->
            if(i>10) speed -= if(i in 41..46) .1 else .4
            d.accept(frame(i*100,speed))
        }
        assertEquals(1,events.count { it.kind==EventKind.HARD_BRAKING })
    }
    @Test fun movementWithoutEstablishedStopIsNotLaunch() {
        val d = DrivingEventDetector()
        assertTrue((0L..40L).flatMap { d.accept(frame(it*100, it*.4)) }.isEmpty())
    }
    @Test fun singleLongBrakingEventKeepsEvidenceAndVersion() {
        val d = DrivingEventDetector()
        val events = (0L..100L).flatMap { i -> d.accept(frame(i*100, if(i<20) 35.0 else (35.0-(i-20)*.4).coerceAtLeast(0.0))) }
        val brake = events.single { it.kind == EventKind.HARD_BRAKING }
        assertEquals("experimental-1", brake.ruleVersion)
        assertTrue(brake.observed <= -3)
        assertTrue(brake.evidence.size >= 10)
    }
    @Test fun noReverseAccelerationEvents() {
        val d = DrivingEventDetector()
        assertTrue((0L..100L).flatMap { d.accept(frame(it*100, (30-it*.4).coerceAtLeast(0.0), Gear.REVERSE)) }.isEmpty())
    }
    @Test fun beltRequiresThreeContinuousSecondsAndSpeed() {
        val d = DrivingEventDetector()
        assertTrue((0L..29L).flatMap { d.accept(frame(it*100, 2.0, warning=true)) }.isEmpty())
        assertEquals(EventKind.SEAT_BELT, d.accept(frame(3000, 2.0, warning=true)).single().kind)
        assertTrue((31L..80L).flatMap { d.accept(frame(it*100, 2.0, warning=true)) }.isEmpty())
        val stationary = DrivingEventDetector()
        assertTrue((0L..50L).flatMap { stationary.accept(frame(it*100, 1.0, warning=true)) }.isEmpty())
    }
    @Test fun gapInvalidAndReversedTimeBreakWarningContinuity() {
        listOf(frame(2900, valid=false), frame(0), frame(4000)).forEach { interruption ->
            val d = DrivingEventDetector()
            (0L..28L).forEach { d.accept(frame(it*100, 2.0, warning=true)) }
            assertTrue(d.accept(interruption).isEmpty())
            assertTrue((41L..60L).flatMap { d.accept(frame(it*100, 2.0, warning=true)) }.isEmpty())
        }
    }
    @Test fun lowSamplingRateDoesNotMakeAccelerationEvents() {
        val d = DrivingEventDetector()
        assertTrue((0L..80L).flatMap { d.accept(frame(it*500, (40-it).coerceAtLeast(0).toDouble())) }.isEmpty())
    }
    @Test fun unavailableSeatDoesNotMeanUnbuckled() {
        val d = DrivingEventDetector()
        assertTrue((0L..60L).flatMap { d.accept(frame(it*100, 3.0).copy(beltWarnings=emptyMap())) }.isEmpty())
    }
    @Test fun redLightAndBriefParkDoNotEndTrip() {
        val r = TripRecorder()
        assertTrue(r.accept(frame(0)).started)
        (1L..400L).forEach { assertFalse(r.accept(frame(it*100)).completed) }
        (401L..450L).forEach { assertFalse(r.accept(frame(it*100, gear=Gear.PARK)).completed) }
        assertFalse(r.accept(frame(45100)).completed)
        assertEquals(RecorderState.RECORDING, r.state)
    }
    @Test fun thirtySecondParkCompletesOnceAndNextDriveStartsAgain() {
        val r = TripRecorder(); r.accept(frame(0))
        val changes = (1L..350L).map { r.accept(frame(it*100, gear=Gear.PARK)) }
        assertEquals(1, changes.count { it.completed })
        assertTrue(r.accept(frame(35100, gear=Gear.REVERSE)).started)
    }
    @Test fun gapDuringParkRestartsConfirmationAndAccountsMissingDistance() {
        val r = TripRecorder(); r.accept(frame(0, 10.0))
        val good = r.accept(frame(100, 10.0)); assertEquals(1.0, good.distanceMeters, .001)
        val gap = r.accept(frame(3000, 10.0)); assertEquals(2900L, gap.missingMs); assertEquals(0.0, gap.distanceMeters, .0)
        (31L..300L).forEach { r.accept(frame(it*100, gear=Gear.PARK)) }
        assertFalse(r.accept(frame(40000, gear=Gear.PARK)).completed)
    }
    @Test fun reviewFailsClosedOnMissingSignalsAndUxRestrictions() {
        assertTrue(canReview(frame(0, gear=Gear.PARK), true))
        assertFalse(canReview(frame(0, gear=Gear.PARK), false))
        assertFalse(canReview(null, true))
        assertFalse(canReview(frame(0, gear=Gear.PARK,valid=false), true))
        assertFalse(canReview(frame(0), true))
    }
    @Test fun staleSpeedNeverGeneratesAnEvent() {
        val d = DrivingEventDetector()
        assertTrue((0L..60L).flatMap { d.accept(frame(it*100, 2.0, warning=true).copy(speedSampleTimeMs=0)) }.isEmpty())
    }
    @Test fun parkAccessNeedsThirtySecondsAndResetsOnUxOrSignalLoss() {
        val gate = ParkedGate()
        (0L..299L).forEach { assertFalse(gate.accept(frame(it*100, gear=Gear.PARK))) }
        assertTrue(gate.accept(frame(30000, gear=Gear.PARK)))
        assertFalse(gate.accept(frame(30100, gear=Gear.PARK,valid=false)))
        assertFalse(gate.accept(frame(30200, gear=Gear.PARK)))
    }
    @Test fun repeatedSpeedSamplesDoNotSatisfyFiveHertz() {
        val d=DrivingEventDetector()
        assertTrue((0L..60L).flatMap {
            val sampled = it / 3 * 300
            d.accept(frame(it*100, 30-sampled*.004).copy(speedSampleTimeMs=sampled))
        }.isEmpty())
    }
    @Test fun trendsRequireComparableCoverageAndDistance() {
        assertNull(calculateTrend(emptyList(), 0).ratePer100Km)
        val trips = (1..3).map { TrendTrip(it.toString(), 4000.0, 10000, 0) }
        assertTrue(calculateTrend(trips, 3).enough)
        assertEquals(25.0, calculateTrend(trips, 3).ratePer100Km!!, .01)
        assertEquals(0.0, calculateTrend(trips, 0).ratePer100Km!!, .01)
        assertFalse(calculateTrend(trips.map { it.copy(missingMs = 2000) }, 3).enough)
        assertFalse(calculateTrend(trips.take(1), 1).enough)
    }
}
