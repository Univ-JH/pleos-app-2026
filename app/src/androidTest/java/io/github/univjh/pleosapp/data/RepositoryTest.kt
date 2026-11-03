package io.github.univjh.pleosapp.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.univjh.pleosapp.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RepositoryTest {
    private lateinit var db: CoachDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, CoachDatabase::class.java).build() }
    @After fun teardown() { db.close() }
    @Test fun engineRecordsOnceAndSeparatesReplayFromRealTrips() = runBlocking {
        val engine = RecordingEngine(CoachRepository(db))
        (0L..60).forEach { t -> engine.accept(VehicleFrame(t*100, 1_000_000+t*100, 3.0, Gear.DRIVE, mapOf("seat" to true), simulated=true)) }
        (61L..400).forEach { t -> engine.accept(VehicleFrame(t*100, 1_000_000+t*100, 0.0, Gear.PARK, simulated=true)) }
        val trip = db.dao().trips().first().single()
        assertEquals("COMPLETE",trip.status); assertTrue(trip.simulated)
        val e = db.dao().events().first().single()
        assertEquals("SEAT_BELT", e.kind)
        assertTrue(db.dao().evidence(e.id).size > 30)
        db.dao().saveFeedback(EventFeedback(e.id, "긴급 회피", false))
        assertEquals(e, db.dao().events().first().single())
        assertFalse(db.dao().feedback().first().single().includeInStats)
        db.dao().deleteTrip(trip.id)
        assertTrue(db.dao().events().first().isEmpty()); assertTrue(db.dao().feedback().first().isEmpty())
        assertEquals(0, db.dao().sampleCount()); assertEquals(0, db.dao().evidenceCount())
    }
    @Test fun recoveryAndRetentionPreserveEventEvidenceUntilNinetyDays() = runBlocking {
        val dao = db.dao(); val now = 100L*86_400_000
        dao.saveTrip(Trip("old", 0, status="COMPLETE"))
        dao.saveTrip(Trip("recent", now-8L*86_400_000, status="COMPLETE"))
        dao.saveTrip(Trip("open", now))
        dao.saveEvent(DrivingEvent("event", "recent", "HARD_BRAKING", now, -3.0, null, "experimental-1"))
        dao.saveSamples(listOf(RawSample(tripId="recent", timeMs=now-8L*86_400_000, payload="{}")))
        dao.saveEvidence(listOf(EventEvidence("event", 100, "{}")))
        val repo = CoachRepository(db); repo.recover(); repo.recover(); repo.maintenance(now)
        assertEquals(2,dao.trips().first().size)
        assertEquals("INTERRUPTED",dao.trips().first().first { it.id=="open" }.status)
        assertEquals(0,dao.sampleCount()); assertEquals(1,dao.evidenceCount())
        dao.deleteFinishedTrips()
        assertTrue(dao.trips().first().isEmpty()); assertEquals(0,dao.evidenceCount())
    }
    @Test fun switchingBetweenReplayAndSdkCannotMixTripProvenance() = runBlocking {
        val engine = RecordingEngine(CoachRepository(db))
        engine.accept(VehicleFrame(0,1000,0.0,Gear.DRIVE,simulated=true))
        engine.accept(VehicleFrame(100,1100,0.0,Gear.DRIVE,simulated=false))
        engine.finishInterrupted()
        val trips=db.dao().trips().first()
        assertEquals(2,trips.size)
        assertEquals(setOf(true,false),trips.map { it.simulated }.toSet())
    }
    @Test fun stopPersistsLastCheckpointAndPreventsActiveDeletion() = runBlocking {
        val engine = RecordingEngine(CoachRepository(db))
        engine.accept(VehicleFrame(0,1000,0.0,Gear.DRIVE))
        engine.accept(VehicleFrame(100,1100,1.0,Gear.DRIVE))
        val id = engine.trip!!.id
        db.dao().deleteTrip(id); assertEquals(1,db.dao().trips().first().size)
        engine.finishInterrupted()
        val t=db.dao().trips().first().single()
        assertEquals("INTERRUPTED",t.status); assertEquals(1100L,t.lastSeenAt)
        assertEquals(2,db.dao().sampleCount())
    }
}
