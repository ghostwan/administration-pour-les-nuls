package fr.music.passportslot.data.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import fr.music.passportslot.data.model.Slot
import fr.music.passportslot.data.model.SlotSearchRequest
import fr.music.passportslot.data.model.SlotStreamResponse
import fr.music.passportslot.data.model.WsMeetingPoint
import fr.music.passportslot.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket client for streaming slot availability from the ANTS API.
 * Primary method for finding available appointment slots.
 *
 * The WebSocket sends three types of messages:
 * 1. JSON object {...} - status/error messages (e.g. captcha required, end_of_search)
 * 2. JSON array [{...}, ...] - batch of meeting points, each may contain available slots
 * 3. Empty JSON array [] - no data for this batch
 */
@Singleton
class SlotWebSocketClient @Inject constructor(
    private val authManager: AuthManager,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "SlotWebSocketClient"
        private const val WS_CLOSE_NORMAL = 1000
        private const val WS_TIMEOUT_SECONDS = 120L
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    /**
     * Search for available slots via WebSocket streaming.
     * Returns a Flow of SlotSearchResult as they are received.
     */
    fun searchSlots(request: SlotSearchRequest): Flow<SlotSearchResult> = callbackFlow {
        val token = try {
            authManager.getToken()
        } catch (e: Exception) {
            trySend(SlotSearchResult.Error("Erreur d'authentification: ${e.message}"))
            close()
            return@callbackFlow
        }

        val wsUrl = "${Constants.ANTS_WSS_BASE_URL}${Constants.WS_SLOTS_ENDPOINT}?token=$token"
        val wsRequest = Request.Builder()
            .url(wsUrl)
            .build()

        Log.d(TAG, "Connecting to WebSocket: $wsUrl")

        val webSocket = client.newWebSocket(wsRequest, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected, sending search request")
                val requestJson = gson.toJson(request)
                Log.d(TAG, "Request: $requestJson")
                webSocket.send(requestJson)
                trySend(SlotSearchResult.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message (${text.length} chars): ${text.take(300)}")
                try {
                    val jsonElement = JsonParser.parseString(text)

                    when {
                        jsonElement.isJsonArray -> {
                            handleArrayMessage(jsonElement.asJsonArray, trySend = { trySend(it) })
                        }
                        jsonElement.isJsonObject -> {
                            handleObjectMessage(jsonElement.asJsonObject, trySend = { trySend(it) })
                        }
                        else -> {
                            Log.w(TAG, "Unexpected JSON type: ${jsonElement.javaClass.simpleName}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse WebSocket message", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(WS_CLOSE_NORMAL, null)
                trySend(SlotSearchResult.Completed)
                close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                trySend(SlotSearchResult.Completed)
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                trySend(SlotSearchResult.Error("Erreur de connexion: ${t.message}"))
                close(t)
            }
        })

        // Auto-close after timeout
        val timeoutJob = launch {
            delay(WS_TIMEOUT_SECONDS * 1000)
            Log.d(TAG, "WebSocket timeout, closing")
            webSocket.close(WS_CLOSE_NORMAL, "Timeout")
        }

        awaitClose {
            timeoutJob.cancel()
            webSocket.close(WS_CLOSE_NORMAL, "Flow cancelled")
        }
    }

    /**
     * Handle a JSON array message from the WebSocket.
     * Each element is a meeting point that may contain available slots.
     */
    private fun handleArrayMessage(
        jsonArray: com.google.gson.JsonArray,
        trySend: (SlotSearchResult) -> Unit
    ) {
        if (jsonArray.size() == 0) {
            // Empty array - no meeting points in this batch
            return
        }

        val meetingPointListType = object : TypeToken<List<WsMeetingPoint>>() {}.type
        val meetingPoints: List<WsMeetingPoint> = gson.fromJson(jsonArray, meetingPointListType)

        for (mp in meetingPoints) {
            val mpName = mp.name ?: "Inconnu"
            val mpCity = mp.cityName ?: ""
            val mpZip = mp.zipCode ?: ""
            val mpId = mp.id ?: ""
            val mpDistance = mp.distanceKm ?: 0.0
            val mpUrl = mp.appointmentUrl ?: mp.website

            if (!mp.availableSlots.isNullOrEmpty()) {
                // This meeting point has available slots
                val slots = mp.availableSlots.mapNotNull { slotData ->
                    val date = slotData.date ?: return@mapNotNull null
                    val time = slotData.time ?: return@mapNotNull null
                    Slot(
                        date = date,
                        time = time,
                        datetime = slotData.datetime ?: "${date}T${time}",
                        meetingPointId = mpId,
                        meetingPointName = mpName,
                        city = mpCity,
                        zipCode = mpZip,
                        distanceKm = mpDistance,
                        appointmentUrl = mpUrl
                    )
                }
                if (slots.isNotEmpty()) {
                    Log.d(TAG, "Found ${slots.size} slot(s) at $mpName ($mpCity)")
                    trySend(SlotSearchResult.SlotsFound(slots))
                } else {
                    trySend(SlotSearchResult.MeetingPointNoSlots(mpName, mpCity, mpDistance))
                }
            } else {
                // Meeting point with no available slots
                trySend(SlotSearchResult.MeetingPointNoSlots(mpName, mpCity, mpDistance))
            }
        }
    }

    /**
     * Handle a JSON object message from the WebSocket.
     * Could be an error, status update, or end_of_search signal.
     */
    private fun handleObjectMessage(
        jsonObject: com.google.gson.JsonObject,
        trySend: (SlotSearchResult) -> Unit
    ) {
        // Check for error messages
        val liStatus = jsonObject.get("li_status")?.asString
        if (liStatus == "error") {
            val message = jsonObject.get("li_message")?.asString ?: "Erreur inconnue"
            Log.w(TAG, "WebSocket error: $message")
            if (message.contains("Token expire", ignoreCase = true)) {
                authManager.invalidateToken()
                trySend(SlotSearchResult.Error("Token expiré, veuillez réessayer"))
            } else if (message.contains("captcha", ignoreCase = true)) {
                trySend(SlotSearchResult.CaptchaRequired)
            } else {
                trySend(SlotSearchResult.Error(message))
            }
            return
        }

        // Check for end_of_search
        val step = jsonObject.get("step")?.asString
        if (step == "end_of_search") {
            val editorsNumber = jsonObject.get("editors_number")?.asInt ?: 0
            val errorCount = jsonObject.get("editor_errors_number")?.asInt ?: 0
            Log.d(TAG, "Search complete: $editorsNumber editors checked, $errorCount errors")
            trySend(SlotSearchResult.Completed)
            return
        }

        // Try parsing as a single meeting point response (legacy format)
        try {
            val response = gson.fromJson(jsonObject, SlotStreamResponse::class.java)
            if (response.meetingPointId != null && !response.slots.isNullOrEmpty()) {
                val slots = response.slots.mapNotNull { detail ->
                    if (detail.date != null && detail.time != null) {
                        Slot(
                            date = detail.date,
                            time = detail.time,
                            datetime = detail.datetime ?: "${detail.date}T${detail.time}",
                            meetingPointId = response.meetingPointId,
                            meetingPointName = response.meetingPointName ?: "Inconnu",
                            city = response.city ?: "",
                            zipCode = response.zipCode ?: "",
                            distanceKm = response.distanceKm ?: 0.0,
                            appointmentUrl = response.appointmentUrl
                        )
                    } else null
                }
                if (slots.isNotEmpty()) {
                    trySend(SlotSearchResult.SlotsFound(slots))
                }
            } else if (response.meetingPointId != null) {
                trySend(
                    SlotSearchResult.MeetingPointNoSlots(
                        name = response.meetingPointName ?: "Inconnu",
                        city = response.city ?: "",
                        distanceKm = response.distanceKm ?: 0.0
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unrecognized object message: ${jsonObject.toString().take(200)}")
        }
    }
}

/**
 * Sealed class representing WebSocket search results.
 */
sealed class SlotSearchResult {
    data object Connected : SlotSearchResult()
    data class SlotsFound(val slots: List<Slot>) : SlotSearchResult()
    data class MeetingPointNoSlots(
        val name: String,
        val city: String,
        val distanceKm: Double
    ) : SlotSearchResult()
    data object Completed : SlotSearchResult()
    data object CaptchaRequired : SlotSearchResult()
    data class Error(val message: String) : SlotSearchResult()
}
