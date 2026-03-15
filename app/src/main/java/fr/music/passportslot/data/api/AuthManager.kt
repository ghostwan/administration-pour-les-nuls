package fr.music.passportslot.data.api

import android.util.Log
import fr.music.passportslot.util.Constants
import fr.music.passportslot.util.CryptoUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages authentication with the ANTS API.
 * Handles token retrieval and caching.
 */
@Singleton
class AuthManager @Inject constructor(
    private val antsApiService: AntsApiService
) {
    private var cachedToken: String? = null
    private var tokenTimestamp: Long = 0
    private val tokenTtlMs = 30 * 60 * 1000L // 30 minutes

    companion object {
        private const val TAG = "AuthManager"
    }

    /**
     * Get a valid auth token, refreshing if needed.
     */
    suspend fun getToken(): String {
        val now = System.currentTimeMillis()
        val token = cachedToken
        if (token != null && (now - tokenTimestamp) < tokenTtlMs) {
            return token
        }
        return refreshToken()
    }

    /**
     * Get the full authorization header value.
     */
    suspend fun getAuthHeader(): String {
        return "Bearer ${getToken()}"
    }

    /**
     * Force refresh the token.
     */
    suspend fun refreshToken(): String {
        try {
            val encryptedUsername = CryptoUtil.encryptAuthToken()
            val response = antsApiService.authenticate(
                username = encryptedUsername,
                password = Constants.AUTH_PASSWORD
            )
            cachedToken = response.accessToken
            tokenTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Token refreshed successfully")
            return response.accessToken
        } catch (e: Exception) {
            Log.e(TAG, "Failed to authenticate with ANTS API", e)
            throw e
        }
    }

    /**
     * Invalidate the cached token.
     */
    fun invalidateToken() {
        cachedToken = null
        tokenTimestamp = 0
    }
}
