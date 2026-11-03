package io.github.univjh.pleosapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val lastSeenAt: Long = startedAt,
    val status: String = "RECORDING",
    val distanceMeters: Double = 0.0,
    val validMs: Long = 0,
    val missingMs: Long = 0,
    val simulated: Boolean = false,
)
@Entity(tableName = "events", foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"],
    childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)], indices = [Index("tripId")])
data class DrivingEvent(
    @PrimaryKey val id: String,
    val tripId: String,
    val kind: String,
    val timeMs: Long,
    val observed: Double,
    val seat: String?,
    val ruleVersion: String,
    val quality: String = "유효한 연속 신호",
)
@Entity(tableName = "feedback", foreignKeys = [ForeignKey(entity = DrivingEvent::class, parentColumns = ["id"],
    childColumns = ["eventId"], onDelete = ForeignKey.CASCADE)])
data class EventFeedback(@PrimaryKey val eventId: String, val reason: String, val includeInStats: Boolean)
@Entity(tableName = "samples", foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"],
    childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)], indices = [Index("tripId"), Index("timeMs")])
data class RawSample(@PrimaryKey(autoGenerate = true) val id: Long = 0, val tripId: String, val timeMs: Long, val payload: String)
@Entity(tableName = "evidence", primaryKeys = ["eventId", "monotonicMs"], foreignKeys = [ForeignKey(
    entity = DrivingEvent::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE)])
data class EventEvidence(val eventId: String, val monotonicMs: Long, val payload: String)

@Dao
interface CoachDao {
    @Query("SELECT * FROM trips ORDER BY startedAt DESC") fun trips(): Flow<List<Trip>>
    @Query("SELECT * FROM events ORDER BY timeMs DESC") fun events(): Flow<List<DrivingEvent>>
    @Query("SELECT * FROM feedback") fun feedback(): Flow<List<EventFeedback>>
    @Query("SELECT * FROM evidence WHERE eventId = :id ORDER BY monotonicMs") suspend fun evidence(id: String): List<EventEvidence>
    @Upsert suspend fun saveTrip(trip: Trip)
    @Insert suspend fun saveEvent(event: DrivingEvent)
    @Upsert suspend fun saveFeedback(feedback: EventFeedback)
    @Insert suspend fun saveSamples(samples: List<RawSample>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun saveEvidence(evidence: List<EventEvidence>)
    @Query("UPDATE trips SET status = 'INTERRUPTED', endedAt = lastSeenAt WHERE status = 'RECORDING'") suspend fun interruptOpenTrips()
    @Query("DELETE FROM trips WHERE id = :id AND status != 'RECORDING'") suspend fun deleteTrip(id: String)
    @Query("DELETE FROM trips WHERE status != 'RECORDING'") suspend fun deleteFinishedTrips()
    @Query("DELETE FROM trips WHERE lastSeenAt < :cutoff AND status != 'RECORDING'") suspend fun expireTrips(cutoff: Long)
    @Query("DELETE FROM samples WHERE timeMs < :cutoff") suspend fun expireSamples(cutoff: Long)
    @Query("SELECT COUNT(*) FROM samples") suspend fun sampleCount(): Int
    @Query("SELECT COUNT(*) FROM evidence") suspend fun evidenceCount(): Int
}
@Database(entities = [Trip::class, DrivingEvent::class, EventFeedback::class, RawSample::class, EventEvidence::class], version = 1, exportSchema = true)
abstract class CoachDatabase : RoomDatabase() { abstract fun dao(): CoachDao }
