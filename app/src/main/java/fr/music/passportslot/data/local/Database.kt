package fr.music.passportslot.data.local

import androidx.room.*
import fr.music.passportslot.data.model.AppointmentReason
import fr.music.passportslot.data.model.FoundSlot
import fr.music.passportslot.data.model.SearchConfig
import kotlinx.coroutines.flow.Flow

/**
 * Room database for persisting search configs and found slots.
 */
@Database(
    entities = [SearchConfig::class, FoundSlot::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchConfigDao(): SearchConfigDao
    abstract fun foundSlotDao(): FoundSlotDao
}

/**
 * Type converters for Room.
 */
class Converters {
    @TypeConverter
    fun fromAppointmentReason(value: AppointmentReason): String = value.name

    @TypeConverter
    fun toAppointmentReason(value: String): AppointmentReason = AppointmentReason.valueOf(value)
}

/**
 * DAO for SearchConfig entities.
 */
@Dao
interface SearchConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: SearchConfig): Long

    @Update
    suspend fun update(config: SearchConfig)

    @Delete
    suspend fun delete(config: SearchConfig)

    @Query("SELECT * FROM search_configs WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getActiveConfigs(): Flow<List<SearchConfig>>

    @Query("SELECT * FROM search_configs WHERE id = :id")
    suspend fun getById(id: Long): SearchConfig?

    @Query("SELECT * FROM search_configs ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SearchConfig>>

    @Query("SELECT * FROM search_configs WHERE isActive = 1")
    suspend fun getActiveConfigsList(): List<SearchConfig>
}

/**
 * DAO for FoundSlot entities.
 */
@Dao
interface FoundSlotDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(slots: List<FoundSlot>)

    @Query("SELECT * FROM found_slots WHERE notified = 0 AND dismissed = 0")
    suspend fun getUnnotified(): List<FoundSlot>

    @Query("UPDATE found_slots SET notified = 1 WHERE id IN (:ids)")
    suspend fun markNotified(ids: List<Long>)

    @Query("SELECT * FROM found_slots WHERE searchConfigId = :configId AND dismissed = 0 ORDER BY foundAt DESC")
    fun getForConfig(configId: Long): Flow<List<FoundSlot>>

    @Query("UPDATE found_slots SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("DELETE FROM found_slots WHERE foundAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM found_slots WHERE meetingPointName = :meetingPointName AND date = :date AND time = :time AND searchConfigId = :configId)")
    suspend fun exists(meetingPointName: String, date: String, time: String, configId: Long): Boolean

    @Query("SELECT * FROM found_slots WHERE dismissed = 0 ORDER BY foundAt DESC")
    fun getAllActive(): Flow<List<FoundSlot>>
}
