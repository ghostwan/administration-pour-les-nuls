package fr.music.passportslot.data.repository

import android.util.Log
import fr.music.passportslot.data.api.AuthManager
import fr.music.passportslot.data.api.SlotSearchResult
import fr.music.passportslot.data.api.SlotWebSocketClient
import fr.music.passportslot.data.local.AppDatabase
import fr.music.passportslot.data.model.*
import fr.music.passportslot.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing slot searches and results.
 * Coordinates between the WebSocket API, local database, and UI.
 */
@Singleton
class SlotRepository @Inject constructor(
    private val webSocketClient: SlotWebSocketClient,
    private val authManager: AuthManager,
    private val database: AppDatabase
) {
    companion object {
        private const val TAG = "SlotRepository"
    }

    /**
     * Search for available slots based on the given configuration.
     */
    fun searchSlots(config: SearchConfig): Flow<SlotSearchResult> {
        val today = LocalDate.now()
        val endDate = today.plusMonths(Constants.DEFAULT_SEARCH_MONTHS_AHEAD.toLong())
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val request = SlotSearchRequest(
            startDate = today.format(formatter),
            endDate = endDate.format(formatter),
            latitude = config.latitude,
            longitude = config.longitude,
            radiusKm = config.radiusKm,
            reason = config.reason.apiValue,
            documentsNumber = config.documentsNumber,
            address = config.address
        )

        Log.d(TAG, "Searching slots: lat=${config.latitude}, lon=${config.longitude}, radius=${config.radiusKm}km")
        return webSocketClient.searchSlots(request)
    }

    /**
     * Save a search configuration to the database.
     */
    suspend fun saveSearchConfig(config: SearchConfig): Long {
        return database.searchConfigDao().insert(config)
    }

    /**
     * Get all active search configurations.
     */
    fun getActiveSearchConfigs(): Flow<List<SearchConfig>> {
        return database.searchConfigDao().getActiveConfigs()
    }

    /**
     * Get a specific search config by ID.
     */
    suspend fun getSearchConfig(id: Long): SearchConfig? {
        return database.searchConfigDao().getById(id)
    }

    /**
     * Update a search configuration.
     */
    suspend fun updateSearchConfig(config: SearchConfig) {
        database.searchConfigDao().update(config)
    }

    /**
     * Delete a search configuration.
     */
    suspend fun deleteSearchConfig(config: SearchConfig) {
        database.searchConfigDao().delete(config)
    }

    /**
     * Save found slots to the database.
     */
    suspend fun saveFoundSlots(slots: List<FoundSlot>) {
        database.foundSlotDao().insertAll(slots)
    }

    /**
     * Get unnotified found slots.
     */
    suspend fun getUnnotifiedSlots(): List<FoundSlot> {
        return database.foundSlotDao().getUnnotified()
    }

    /**
     * Mark slots as notified.
     */
    suspend fun markSlotsNotified(ids: List<Long>) {
        database.foundSlotDao().markNotified(ids)
    }

    /**
     * Get all found slots for a search config.
     */
    fun getFoundSlotsForConfig(configId: Long): Flow<List<FoundSlot>> {
        return database.foundSlotDao().getForConfig(configId)
    }

    /**
     * Dismiss a found slot.
     */
    suspend fun dismissSlot(id: Long) {
        database.foundSlotDao().dismiss(id)
    }

    /**
     * Clean up old found slots (older than 24h).
     */
    suspend fun cleanupOldSlots() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        database.foundSlotDao().deleteOlderThan(cutoff)
    }

    /**
     * Check if a slot was already found (to avoid duplicate notifications).
     */
    suspend fun isSlotAlreadyFound(
        meetingPointName: String,
        date: String,
        time: String,
        configId: Long
    ): Boolean {
        return database.foundSlotDao().exists(meetingPointName, date, time, configId)
    }
}
