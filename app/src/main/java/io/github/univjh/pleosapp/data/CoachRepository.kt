package io.github.univjh.pleosapp.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import io.github.univjh.pleosapp.domain.*
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.UUID

private val Context.settingsStore by preferencesDataStore("coach_settings")
class CoachSettings(private val context: Context) {
    private val consentKey = booleanPreferencesKey("recording_consent")
    private val goalKey = intPreferencesKey("goal_lesson")
    val consent = context.settingsStore.data.map { it[consentKey] ?: false }
    val goal = context.settingsStore.data.map { it[goalKey] ?: 1 }
    suspend fun setConsent(value: Boolean) { context.settingsStore.edit { it[consentKey] = value } }
    suspend fun setGoal(value: Int) { context.settingsStore.edit { it[goalKey] = value } }
}

fun VehicleFrame.payload(): String = JSONObject().apply {
    put("speedSampleTimeMs", speedSampleTimeMs); put("monotonicMs", monotonicMs); put("timeMs", wallTimeMs)
    put("speedMps", speedMps ?: JSONObject.NULL); put("gear", gear.name); put("valid", valid)
    put("beltWarnings", JSONObject(beltWarnings)); put("simulated", simulated)
}.toString()

class CoachRepository(val db: CoachDatabase) {
    val dao = db.dao()
    suspend fun maintenance(now: Long) = db.withTransaction {
        dao.expireSamples(now - 7L * 86_400_000)
        dao.expireTrips(now - 90L * 86_400_000)
    }
    suspend fun recover() { dao.interruptOpenTrips() }
}

/** Single collector owns this engine, so a trip and its events cannot race. */
class RecordingEngine(private val repository: CoachRepository) {
    val recorder = TripRecorder()
    private val detector = DrivingEventDetector()
    var trip: Trip? = null; private set
    private val samples = mutableListOf<RawSample>()
    private val pendingEvidence = mutableListOf<EventEvidence>()
    private val windows = mutableMapOf<String, Long>()
    private var lastFlush = 0L

    suspend fun accept(frame: VehicleFrame): String? {
        if (trip?.let { it.simulated != frame.simulated } == true) finishInterrupted()
        val change = recorder.accept(frame)
        if (change.started) {
            trip = Trip(UUID.randomUUID().toString(), frame.wallTimeMs, simulated = frame.simulated)
            repository.dao.saveTrip(trip!!)
        }
        val found = detector.accept(frame)
        val current = trip ?: return null
        trip = current.copy(lastSeenAt = maxOf(current.lastSeenAt, frame.wallTimeMs),
            distanceMeters = current.distanceMeters + change.distanceMeters,
            validMs = current.validMs + change.validMs, missingMs = current.missingMs + change.missingMs)
        samples += RawSample(tripId = current.id, timeMs = frame.wallTimeMs, payload = frame.payload())
        found.forEach { event ->
            repository.db.withTransaction {
                repository.dao.saveEvent(DrivingEvent(event.id, current.id, event.kind.name, event.timeMs,
                    event.observed, event.seat, event.ruleVersion))
                repository.dao.saveEvidence(event.evidence.map { EventEvidence(event.id, it.monotonicMs, it.payload()) })
            }
            windows[event.id] = frame.monotonicMs + 5_000
        }
        windows.entries.removeAll { it.value < frame.monotonicMs }
        windows.keys.forEach { pendingEvidence += EventEvidence(it, frame.monotonicMs, frame.payload()) }
        if (frame.monotonicMs - lastFlush >= 1_000 || found.isNotEmpty() || change.completed) flush(frame.monotonicMs)
        if (change.completed) {
            repository.dao.saveTrip(trip!!.copy(endedAt = frame.wallTimeMs, status = "COMPLETE"))
            trip = null; detector.reset(); windows.clear()
            return current.id
        }
        return null
    }
    private suspend fun flush(now: Long) {
        repository.db.withTransaction {
            trip?.let { repository.dao.saveTrip(it) }
            if (samples.isNotEmpty()) repository.dao.saveSamples(samples.toList())
            if (pendingEvidence.isNotEmpty()) repository.dao.saveEvidence(pendingEvidence.toList())
        }
        samples.clear(); pendingEvidence.clear(); lastFlush = now
    }
    suspend fun finishInterrupted() {
        flush(lastFlush)
        trip?.let { repository.dao.saveTrip(it.copy(endedAt = it.lastSeenAt, status = "INTERRUPTED")) }
        trip = null; recorder.reset(); detector.reset(); windows.clear()
    }
}
