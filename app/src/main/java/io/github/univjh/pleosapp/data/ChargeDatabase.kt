package io.github.univjh.pleosapp.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** First and last odometer reading seen on one calendar day (ISO date). */
@Entity(tableName = "odometer_day")
data class OdometerDayEntity(@PrimaryKey val date: String, val firstKm: Double, val lastKm: Double)

/** One charge limit the app sent to the car. */
@Entity(tableName = "charge_decision")
data class ChargeDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMs: Long,
    val targetPercent: Int,
    val socPercent: Int,
    val expectedKm: Double,
    val minTempC: Double?,
    /** True when the driver pressed the button or changed the value; false for the countdown. */
    val manual: Boolean,
    val odometerKm: Double?,
    /** The car reported the limit back. */
    val verified: Boolean,
)

@Dao
interface ChargeDao {
    @Query("SELECT * FROM odometer_day ORDER BY date")
    fun odometerDays(): Flow<List<OdometerDayEntity>>

    @Query("INSERT INTO odometer_day(date, firstKm, lastKm) VALUES(:date, :km, :km) " +
        "ON CONFLICT(date) DO UPDATE SET lastKm = :km")
    suspend fun recordOdometer(date: String, km: Double)

    @Insert suspend fun insert(decision: ChargeDecisionEntity)

    @Query("SELECT * FROM charge_decision ORDER BY timeMs DESC LIMIT 1")
    fun latestDecision(): Flow<ChargeDecisionEntity?>

    @Query("SELECT * FROM charge_decision WHERE manual = 1 ORDER BY timeMs DESC LIMIT 1")
    suspend fun latestManual(): ChargeDecisionEntity?
}

@Database(entities = [OdometerDayEntity::class, ChargeDecisionEntity::class], version = 1, exportSchema = false)
abstract class ChargeDatabase : RoomDatabase() {
    abstract fun dao(): ChargeDao
}
