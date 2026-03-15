package fr.music.passportslot.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.music.passportslot.data.api.SlotSearchResult
import fr.music.passportslot.data.local.AppDatabase
import fr.music.passportslot.data.model.FoundSlot
import fr.music.passportslot.data.repository.SlotRepository
import fr.music.passportslot.util.Constants
import fr.music.passportslot.util.NotificationHelper
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically checks for available appointment slots.
 * Uses WorkManager for reliable background execution.
 */
@HiltWorker
class SlotCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val slotRepository: SlotRepository,
    private val notificationHelper: NotificationHelper,
    private val database: AppDatabase
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SlotCheckWorker"

        /**
         * Schedule periodic slot checking.
         */
        fun schedule(
            context: Context,
            intervalMinutes: Long = Constants.DEFAULT_CHECK_INTERVAL_MINUTES.toLong()
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SlotCheckWorker>(
                intervalMinutes, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // flex interval
            )
                .setConstraints(constraints)
                .addTag(Constants.SLOT_CHECK_WORK_TAG)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                Constants.SLOT_CHECK_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            Log.d(TAG, "Scheduled periodic slot check every $intervalMinutes minutes")
        }

        /**
         * Cancel periodic slot checking.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(Constants.SLOT_CHECK_WORK_NAME)
            Log.d(TAG, "Cancelled periodic slot check")
        }

        /**
         * Run an immediate one-time check.
         */
        fun runOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SlotCheckWorker>()
                .setConstraints(constraints)
                .addTag(Constants.SLOT_CHECK_WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Enqueued one-time slot check")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting slot check...")

        // Clean up old slots
        slotRepository.cleanupOldSlots()

        // Get all active search configurations
        val configs = database.searchConfigDao().getActiveConfigsList()
        if (configs.isEmpty()) {
            Log.d(TAG, "No active search configs, skipping")
            return Result.success()
        }

        var totalNewSlots = 0
        val allNewSlots = mutableListOf<FoundSlot>()

        for (config in configs) {
            Log.d(TAG, "Checking config ${config.id}: ${config.address}")

            try {
                slotRepository.searchSlots(config)
                    .onEach { result ->
                        when (result) {
                            is SlotSearchResult.SlotsFound -> {
                                val newSlots = result.slots.filter { slot ->
                                    !slotRepository.isSlotAlreadyFound(
                                        meetingPointName = slot.meetingPointName,
                                        date = slot.date,
                                        time = slot.time,
                                        configId = config.id
                                    )
                                }

                                if (newSlots.isNotEmpty()) {
                                    val foundSlots = newSlots.map { slot ->
                                        FoundSlot(
                                            meetingPointName = slot.meetingPointName,
                                            city = slot.city,
                                            zipCode = slot.zipCode,
                                            date = slot.date,
                                            time = slot.time,
                                            distanceKm = slot.distanceKm,
                                            appointmentUrl = slot.appointmentUrl,
                                            searchConfigId = config.id
                                        )
                                    }
                                    slotRepository.saveFoundSlots(foundSlots)
                                    allNewSlots.addAll(foundSlots)
                                    totalNewSlots += newSlots.size
                                    Log.d(TAG, "Found ${newSlots.size} new slots for config ${config.id}")
                                }
                            }
                            is SlotSearchResult.Error -> {
                                Log.e(TAG, "Error for config ${config.id}: ${result.message}")
                            }
                            is SlotSearchResult.CaptchaRequired -> {
                                Log.w(TAG, "Captcha required for config ${config.id} - skipping (needs user interaction)")
                            }
                            else -> { /* ignore other states */ }
                        }
                    }
                    .collect()
            } catch (e: Exception) {
                Log.e(TAG, "Exception checking config ${config.id}", e)
            }
        }

        // Send notification if new slots were found
        if (allNewSlots.isNotEmpty()) {
            Log.d(TAG, "Notifying user of $totalNewSlots new slots")
            notificationHelper.showSlotNotification(allNewSlots)
        } else {
            Log.d(TAG, "No new slots found")
        }

        return Result.success()
    }
}
