package com.lakh.racing.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

enum class OfflineState { NONE, DOWNLOADING, READY, FAILED }

@Entity(tableName = "races", primaryKeys = ["id"])
data class RaceEntity(
    val id: String,
    val name: String,
    val description: String,
    /** The full [com.lakh.racing.data.model.Race] as JSON. */
    val json: String,
    val importedAt: Long,
    val offlineState: OfflineState = OfflineState.NONE,
    val offlineBytes: Long = 0,
)

@Entity(tableName = "check_ins", primaryKeys = ["raceId", "checkpointId"])
data class CheckInEntity(
    val raceId: String,
    val checkpointId: String,
    val timeMs: Long,
)

@Dao
interface RaceDao {
    @Query("SELECT * FROM races ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<RaceEntity>>

    @Query("SELECT * FROM races WHERE id = :id")
    fun observe(id: String): Flow<RaceEntity?>

    @Query("SELECT * FROM races WHERE id = :id")
    suspend fun get(id: String): RaceEntity?

    @Upsert
    suspend fun upsert(race: RaceEntity)

    @Query("UPDATE races SET offlineState = :state, offlineBytes = :bytes WHERE id = :id")
    suspend fun setOffline(id: String, state: OfflineState, bytes: Long)

    @Query("DELETE FROM races WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM check_ins WHERE raceId = :raceId ORDER BY timeMs")
    fun observeCheckIns(raceId: String): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins WHERE raceId = :raceId")
    suspend fun checkIns(raceId: String): List<CheckInEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCheckIn(checkIn: CheckInEntity)

    @Query("DELETE FROM check_ins WHERE raceId = :raceId")
    suspend fun clearCheckIns(raceId: String)
}

@Database(entities = [RaceEntity::class, CheckInEntity::class], version = 1, exportSchema = false)
abstract class RacingDatabase : RoomDatabase() {
    abstract fun raceDao(): RaceDao

    companion object {
        fun create(context: Context) =
            Room.databaseBuilder(context, RacingDatabase::class.java, "racing.db").build()
    }
}
