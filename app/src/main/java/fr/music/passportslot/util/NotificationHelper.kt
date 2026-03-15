package fr.music.passportslot.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.music.passportslot.R
import fr.music.passportslot.data.model.FoundSlot
import fr.music.passportslot.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages notification creation and display for slot alerts.
 */
@Singleton
class NotificationHelper @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val NOTIFICATION_GROUP = "slot_alerts_group"
        private const val SUMMARY_NOTIFICATION_ID = 0
        private const val CAPTCHA_NOTIFICATION_ID = 2000
    }

    /**
     * Create the notification channel (required for Android 8+).
     */
    fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = Constants.NOTIFICATION_CHANNEL_DESCRIPTION
            enableVibration(true)
            enableLights(true)
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Show a notification for newly found slots.
     */
    fun showSlotNotification(slots: List<FoundSlot>) {
        if (slots.isEmpty()) return

        val notificationManager = NotificationManagerCompat.from(context)

        // Intent to open the app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_results", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (slots.size == 1) {
            // Single slot notification
            val slot = slots.first()
            val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Créneau disponible !")
                .setContentText("${slot.meetingPointName} - ${slot.city} le ${formatDate(slot.date)} à ${slot.time}")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(
                            "Créneau disponible à ${slot.meetingPointName}\n" +
                            "${slot.city} (${slot.zipCode})\n" +
                            "Le ${formatDate(slot.date)} à ${slot.time}\n" +
                            "Distance : ${String.format("%.1f", slot.distanceKm)} km"
                        )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(NOTIFICATION_GROUP)
                .build()

            try {
                notificationManager.notify(slot.id.toInt(), notification)
            } catch (e: SecurityException) {
                // Permission not granted
            }
        } else {
            // Multiple slots - show individual + summary
            val groupedByMairie = slots.groupBy { it.meetingPointName }

            for ((index, slot) in slots.withIndex()) {
                val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("${slot.meetingPointName} - ${slot.city}")
                    .setContentText("Le ${formatDate(slot.date)} à ${slot.time}")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setGroup(NOTIFICATION_GROUP)
                    .build()

                try {
                    notificationManager.notify(1000 + index, notification)
                } catch (e: SecurityException) {
                    // Permission not granted
                }
            }

            // Summary notification
            val summaryText = "${slots.size} créneaux disponibles dans ${groupedByMairie.size} mairie(s)"
            val summary = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Créneaux disponibles !")
                .setContentText(summaryText)
                .setStyle(
                    NotificationCompat.InboxStyle().also { style ->
                        style.setBigContentTitle("${slots.size} créneaux trouvés")
                        groupedByMairie.forEach { (mairie, mairieSlots) ->
                            style.addLine("$mairie : ${mairieSlots.size} créneau(x)")
                        }
                        style.setSummaryText("Appuyez pour voir les détails")
                    }
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(NOTIFICATION_GROUP)
                .setGroupSummary(true)
                .build()

            try {
                notificationManager.notify(SUMMARY_NOTIFICATION_ID, summary)
            } catch (e: SecurityException) {
                // Permission not granted
            }
        }
    }

    /**
     * Show a notification when captcha is required.
     * The user needs to open the app to solve the captcha so background monitoring can continue.
     */
    fun showCaptchaRequiredNotification() {
        val notificationManager = NotificationManagerCompat.from(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_captcha", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Captcha requis")
            .setContentText("Ouvrez l'application pour résoudre le captcha et reprendre la surveillance")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Le captcha a expiré. Ouvrez l'application et résolvez le captcha " +
                        "pour que la surveillance en arrière-plan puisse continuer à chercher des créneaux."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(CAPTCHA_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    /**
     * Format a date string from "2026-04-15" to "15/04/2026".
     */
    private fun formatDate(date: String): String {
        return try {
            val parts = date.split("-")
            "${parts[2]}/${parts[1]}/${parts[0]}"
        } catch (e: Exception) {
            date
        }
    }
}
