package fr.music.passportslot.data.api

import android.util.Log
import com.google.gson.Gson
import fr.music.passportslot.data.model.Slot
import fr.music.passportslot.data.model.SlotSearchRequest
import fr.music.passportslot.data.model.SlotStreamResponse
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
 */
@Singleton
class SlotWebSocketClient @Inject constructor(
    private val authManager: AuthManager,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "SlotWebSocketClient"
        private const val WS_CLOSE_NORMAL = 1000
        private const val WS_TIMEOUT_SECONDS = 60L
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    /**
     * Search for available slots via WebSocket streaming.
     * Returns a Flow of Slot objects as they are received.
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
                webSocket.send(requestJson)
                trySend(SlotSearchResult.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received: ${text.take(200)}")
                try {
                    val response = gson.fromJson(text, SlotStreamResponse::class.java)

                    // Check for errors
                    if (response.liStatus == "error") {
                        Log.w(TAG, "WebSocket error: ${response.liMessage}")
                        val message = response.liMessage ?: "Erreur inconnue"
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

                    // Parse meeting point with slots
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
                    }

                    // Check for end signal (meeting point with empty or no slots = just info)
                    if (response.meetingPointId != null && response.slots.isNullOrEmpty()) {
                        trySend(
                            SlotSearchResult.MeetingPointNoSlots(
                                name = response.meetingPointName ?: "Inconnu",
                                city = response.city ?: "",
                                distanceKm = response.distanceKm ?: 0.0
                            )
                        )
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
