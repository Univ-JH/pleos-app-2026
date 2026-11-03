package io.github.univjh.pleosapp.domain

import kotlin.math.abs
import java.util.UUID

/** All speed values are m/s; monotonicMs is elapsedRealtime, never wall time. */
data class VehicleFrame(
    val monotonicMs: Long,
    val wallTimeMs: Long,
    val speedMps: Double?,
    val gear: Gear,
    val beltWarnings: Map<String, Boolean> = emptyMap(),
    val valid: Boolean = true,
    val simulated: Boolean = false,
    val speedSampleTimeMs: Long = monotonicMs,
)
fun VehicleFrame.hasValidMotion(maxAgeMs: Long = 250) = valid && speedMps?.isFinite() == true &&
    gear != Gear.UNKNOWN && monotonicMs - speedSampleTimeMs in 0..maxAgeMs

enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN }
enum class EventKind(val title: String, val lessonId: Int) {
    RAPID_START("급출발 후보", 1), HARD_BRAKING("급감속 후보", 3), SEAT_BELT("안전벨트 경고", 5)
}
data class RuleConfig(
    val version: String = "experimental-1",
    val acceleration: Double = 3.0,
    val accelerationHoldMs: Long = 1_000,
    val stoppedHoldMs: Long = 2_000,
    val launchWindowMs: Long = 5_000,
    val beltHoldMs: Long = 3_000,
    val parkHoldMs: Long = 30_000,
    val maxSampleGapMs: Long = 250,
)
data class DetectedEvent(
    val id: String = UUID.randomUUID().toString(),
    val kind: EventKind,
    val timeMs: Long,
    val monotonicMs: Long,
    val observed: Double,
    val seat: String? = null,
    val ruleVersion: String,
    val evidence: List<VehicleFrame>,
)

/** A latch emits once per continuous condition; missing data breaks continuity. */
private class Latch {
    private var since: Long? = null
    private var emitted = false
    fun update(condition: Boolean, now: Long, hold: Long): Boolean {
        if (!condition) { reset(); return false }
        if (since == null) since = now
        if (!emitted && now - since!! >= hold) { emitted = true; return true }
        return false
    }
    fun reset() { since = null; emitted = false }
}

class DrivingEventDetector(private val rules: RuleConfig = RuleConfig()) {
    private val history = ArrayDeque<VehicleFrame>()
    private var last: VehicleFrame? = null
    private var stoppedSince: Long? = null
    private var stoppedReady = false
    private var launchAt: Long? = null
    private var launchEmitted = false
    private var brakeEmitted = false
    private var brakeRecoveredSince: Long? = null
    private var brakeStartSpeed = 0.0
    private val startLatch = Latch()
    private val brakeLatch = Latch()
    private val beltLatches = mutableMapOf<String, Latch>()

    fun reset() {
        history.clear(); last = null; stoppedSince = null; stoppedReady = false; launchAt = null
        startLatch.reset(); brakeLatch.reset(); beltLatches.clear(); brakeStartSpeed = 0.0
        launchEmitted = false; brakeEmitted = false; brakeRecoveredSince = null
    }

    fun accept(frame: VehicleFrame): List<DetectedEvent> {
        val previous = last
        if (!frame.valid || frame.speedMps == null || !frame.speedMps.isFinite() || frame.gear == Gear.UNKNOWN ||
            frame.monotonicMs - frame.speedSampleTimeMs !in 0..rules.maxSampleGapMs) {
            reset(); return emptyList()
        }
        if (previous != null && frame.monotonicMs <= previous.monotonicMs) {
            reset(); return emptyList()
        }
        if (previous != null && (frame.monotonicMs - previous.monotonicMs > rules.maxSampleGapMs ||
                    frame.simulated != previous.simulated)) reset()
        val prior = last
        last = frame
        history.addLast(frame)
        while (history.size > 1 && frame.monotonicMs - history.first().monotonicMs > 10_000) history.removeFirst()
        val speed = abs(frame.speedMps)
        if (speed <= 1.0 / 3.6) {
            if (stoppedSince == null) stoppedSince = frame.monotonicMs
            if (frame.monotonicMs - stoppedSince!! >= rules.stoppedHoldMs) stoppedReady = true
        } else {
            if (stoppedReady && frame.gear == Gear.DRIVE) { launchAt = frame.monotonicMs; launchEmitted = false }
            stoppedSince = null; stoppedReady = false
        }
        val distinctSamples = history.filter { frame.speedSampleTimeMs - it.speedSampleTimeMs in 0..1_000 }
            .distinctBy { it.speedSampleTimeMs }
        val base = distinctSamples.firstOrNull()
        val windowMs = base?.let { frame.speedSampleTimeMs - it.speedSampleTimeMs } ?: 0
        val sampleCount = distinctSamples.size
        val acceleration = if (windowMs >= 800 && (sampleCount - 1) * 1000.0 / windowMs >= 5.0 && base?.speedMps != null) {
            (frame.speedMps - base.speedMps) / (windowMs / 1000.0)
        } else null
        val events = mutableListOf<DetectedEvent>()
        fun emit(kind: EventKind, value: Double, seat: String? = null) {
            events += DetectedEvent(kind = kind, timeMs = frame.wallTimeMs, monotonicMs = frame.monotonicMs,
                observed = value, seat = seat, ruleVersion = rules.version, evidence = history.toList())
        }
        val drive = frame.gear == Gear.DRIVE
        if (!drive) launchAt = null
        if (startLatch.update(drive && !launchEmitted && acceleration != null && acceleration >= rules.acceleration - 1e-6 &&
                launchAt?.let { frame.monotonicMs - it <= rules.launchWindowMs } == true,
                frame.monotonicMs, rules.accelerationHoldMs)) {
            emit(EventKind.RAPID_START, acceleration!!); launchEmitted = true
        }
        val braking = drive && acceleration != null && acceleration <= -rules.acceleration + 1e-6
        if (!drive) { brakeEmitted = false; brakeRecoveredSince = null }
        if (acceleration != null && acceleration > -rules.acceleration) {
            if (brakeRecoveredSince == null) brakeRecoveredSince = frame.monotonicMs
            if (frame.monotonicMs - brakeRecoveredSince!! >= rules.accelerationHoldMs) brakeEmitted = false
        } else brakeRecoveredSince = null
        if (!braking) brakeStartSpeed = 0.0
        else if (brakeStartSpeed == 0.0) brakeStartSpeed = maxOf(speed, abs(prior?.speedMps ?: 0.0), abs(base?.speedMps ?: 0.0))
        if (brakeLatch.update(braking && !brakeEmitted && brakeStartSpeed >= 10.0 / 3.6, frame.monotonicMs,
                rules.accelerationHoldMs)) {
            emit(EventKind.HARD_BRAKING, acceleration!!); brakeEmitted = true
        }
        (beltLatches.keys - frame.beltWarnings.keys).forEach { beltLatches.remove(it) }
        frame.beltWarnings.forEach { (seat, warning) ->
            if (beltLatches.getOrPut(seat) { Latch() }.update(warning && speed >= 5.0 / 3.6,
                    frame.monotonicMs, rules.beltHoldMs)) emit(EventKind.SEAT_BELT, speed * 3.6, seat)
        }
        return events
    }
}

enum class RecorderState { WAITING, RECORDING, PARK_CONFIRMING }
data class TripChange(val started: Boolean = false, val completed: Boolean = false, val distanceMeters: Double = 0.0,
    val validMs: Long = 0, val missingMs: Long = 0)
class TripRecorder(private val rules: RuleConfig = RuleConfig()) {
    var state = RecorderState.WAITING; private set
    private var parkSince: Long? = null
    private var previous: VehicleFrame? = null
    fun reset() { state = RecorderState.WAITING; parkSince = null; previous = null }
    fun accept(frame: VehicleFrame): TripChange {
        val before = previous
        if (before != null && frame.monotonicMs <= before.monotonicMs) { previous = null; parkSince = null; return TripChange() }
        previous = frame
        val usable = frame.hasValidMotion(rules.maxSampleGapMs)
        val dt = before?.let { frame.monotonicMs - it.monotonicMs } ?: 0
        if (state == RecorderState.WAITING) {
            if (usable && frame.gear in setOf(Gear.DRIVE, Gear.REVERSE)) {
                state = RecorderState.RECORDING
                return TripChange(started = true)
            }
            return TripChange()
        }
        val continuous = usable && before?.hasValidMotion(rules.maxSampleGapMs) == true && before.speedMps != null && dt in 1..rules.maxSampleGapMs
        val parked = usable && frame.gear == Gear.PARK && abs(frame.speedMps!!) < 1.0 / 3.6
        if (!parked || !continuous) parkSince = null
        if (parked) {
            if (parkSince == null) parkSince = frame.monotonicMs
            state = RecorderState.PARK_CONFIRMING
        } else state = RecorderState.RECORDING
        val completed = parkSince?.let { frame.monotonicMs - it >= rules.parkHoldMs } == true
        if (completed) { state = RecorderState.WAITING; parkSince = null }
        return TripChange(completed = completed,
            distanceMeters = if (continuous) (abs(before!!.speedMps!!) + abs(frame.speedMps!!)) / 2 * dt / 1000.0 else 0.0,
            validMs = if (continuous) dt else 0,
            missingMs = if (!continuous) dt.coerceAtLeast(0) else 0)
    }
}

fun canReview(frame: VehicleFrame?, uxAllowed: Boolean): Boolean = uxAllowed && frame?.hasValidMotion() == true &&
    frame.gear == Gear.PARK && frame.speedMps?.let { abs(it) < 1.0 / 3.6 } == true

/** Park access needs continuous fresh observations, independently of trip recording. */
class ParkedGate(private val holdMs: Long = 30_000) {
    private var since: Long? = null
    private var previous: Long? = null
    fun reset() { since = null; previous = null }
    fun accept(frame: VehicleFrame): Boolean {
        if (!canReview(frame, true) || previous?.let { frame.monotonicMs - it !in 1..250 } == true) since = null
        previous = frame.monotonicMs
        if (!canReview(frame, true)) return false
        if (since == null) since = frame.monotonicMs
        return frame.monotonicMs - since!! >= holdMs
    }
}
