package fr.music.passportslot.data.api

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the LiveIdentity captcha JWT lifecycle.
 *
 * Flow:
 * 1. POST /api/send_antibot_info → {antibotId, requestId}
 * 2. User solves captcha in WebView → cookie is set
 * 3. POST /api/initCaptchaJWT → {access_token} (the captcha JWT)
 * 4. POST /api/validateCaptchaJWT?token=<jwt> → 200 OK
 * 5. JWT is sent as antibot_token in WebSocket messages
 */
@Singleton
class CaptchaManager @Inject constructor(
    private val antsApiService: AntsApiService,
    private val authManager: AuthManager
) {
    companion object {
        private const val TAG = "CaptchaManager"
        // Captcha JWT seems to be valid for about 30 minutes
        private const val JWT_TTL_MS = 25 * 60 * 1000L
    }

    private var captchaJwt: String? = null
    private var jwtTimestamp: Long = 0
    private var currentAntibotId: String? = null
    private var currentRequestId: String? = null

    /**
     * Whether we have a valid (non-expired) captcha JWT.
     */
    val hasValidJwt: Boolean
        get() {
            val jwt = captchaJwt ?: return false
            return jwt.isNotEmpty() && (System.currentTimeMillis() - jwtTimestamp) < JWT_TTL_MS
        }

    /**
     * Get the current captcha JWT, or null if not available/expired.
     */
    fun getJwt(): String? {
        return if (hasValidJwt) captchaJwt else null
    }

    /**
     * Step 1: Request antibot info from the ANTS API.
     * Returns the antibotId and requestId needed to initialize the captcha widget.
     */
    suspend fun requestAntibotInfo(): AntibotInfo {
        try {
            val response = antsApiService.sendAntibotInfo(antibotId = currentAntibotId)
            val body = response.responseBody
            currentAntibotId = body.antibotId
            currentRequestId = body.requestId
            Log.d(TAG, "Got antibot info: antibotId=${body.antibotId}, requestId=${body.requestId}")
            return AntibotInfo(body.antibotId, body.requestId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get antibot info", e)
            throw e
        }
    }

    /**
     * Step 2 (after user solves captcha in WebView): Exchange for a JWT.
     * The captcha solution is communicated via cookies set by the LiveIdentity script,
     * so we just call initCaptchaJWT and the server reads the cookie.
     */
    suspend fun initCaptchaJwt(): String {
        try {
            val authHeader = authManager.getAuthHeader()
            val response = antsApiService.initCaptchaJwt(authHeader)
            val jwt = response.accessToken
            Log.d(TAG, "Got captcha JWT: ${jwt.take(20)}...")
            return jwt
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init captcha JWT", e)
            throw e
        }
    }

    /**
     * Step 3: Validate the captcha JWT.
     */
    suspend fun validateAndStoreJwt(jwt: String): Boolean {
        try {
            val authHeader = authManager.getAuthHeader()
            val response = antsApiService.validateCaptchaJwt(authHeader, jwt)
            if (response.isSuccessful) {
                captchaJwt = jwt
                jwtTimestamp = System.currentTimeMillis()
                Log.d(TAG, "Captcha JWT validated and stored")
                return true
            } else {
                Log.w(TAG, "Captcha JWT validation failed: ${response.code()}")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate captcha JWT", e)
            throw e
        }
    }

    /**
     * Store a JWT that was obtained and validated externally (e.g. from WebView flow).
     */
    fun storeJwt(jwt: String) {
        captchaJwt = jwt
        jwtTimestamp = System.currentTimeMillis()
        Log.d(TAG, "Captcha JWT stored externally")
    }

    /**
     * Invalidate the current captcha JWT.
     */
    fun invalidateJwt() {
        captchaJwt = null
        jwtTimestamp = 0
        currentAntibotId = null
        currentRequestId = null
        Log.d(TAG, "Captcha JWT invalidated")
    }
}

data class AntibotInfo(
    val antibotId: String,
    val requestId: String
)
