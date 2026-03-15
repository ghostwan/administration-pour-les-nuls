package fr.music.passportslot.ui.screens.captcha

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.music.passportslot.data.api.CaptchaManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CaptchaUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isProcessingToken: Boolean = false,
    val captchaSuccess: Boolean = false,
    val captchaNotRequired: Boolean = false,
    val captchaDetected: Boolean = false,
    val pageLoaded: Boolean = false
)

/**
 * ViewModel for the CaptchaScreen.
 *
 * Handles three scenarios:
 * 1. Captcha JWT captured from the real ANTS site's initCaptchaJWT call → validate, store, navigate back
 * 2. WebSocket search detected (captcha not required) → auto-navigate back for retry
 * 3. Captcha widget visible → update UI to inform user
 */
@HiltViewModel
class CaptchaViewModel @Inject constructor(
    private val captchaManager: CaptchaManager
) : ViewModel() {

    companion object {
        private const val TAG = "CaptchaViewModel"
    }

    private val _uiState = MutableStateFlow(CaptchaUiState())
    val uiState: StateFlow<CaptchaUiState> = _uiState.asStateFlow()

    /**
     * Called when the JS interceptor captures the JWT from the real ANTS site's
     * initCaptchaJWT API call. The ANTS Angular app already obtained and will
     * validate this JWT - we just need to store it for our own WebSocket calls.
     */
    fun onCaptchaJwtCaptured(jwt: String) {
        Log.d(TAG, "Captcha JWT captured from ANTS site, validating...")
        _uiState.update { it.copy(isProcessingToken = true) }

        viewModelScope.launch {
            try {
                // Validate the JWT via the ANTS API
                val valid = captchaManager.validateAndStoreJwt(jwt)
                if (valid) {
                    Log.d(TAG, "Captcha JWT validated and stored successfully")
                    _uiState.update {
                        it.copy(
                            isProcessingToken = false,
                            captchaSuccess = true
                        )
                    }
                } else {
                    // Validation failed, but the JWT was obtained from the real site.
                    // Store it anyway - it might still work for WebSocket calls
                    // since the ANTS site itself validated it.
                    Log.w(TAG, "JWT validation returned non-200, storing anyway")
                    captchaManager.storeJwt(jwt)
                    _uiState.update {
                        it.copy(
                            isProcessingToken = false,
                            captchaSuccess = true
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate captcha JWT", e)
                // Even if our validation call fails, the JWT was obtained from
                // the real ANTS site flow. Store it and try using it.
                captchaManager.storeJwt(jwt)
                Log.d(TAG, "Stored JWT despite validation error, proceeding")
                _uiState.update {
                    it.copy(
                        isProcessingToken = false,
                        captchaSuccess = true
                    )
                }
            }
        }
    }

    /**
     * Called when the JS interceptor detects the ANTS site creating a search
     * WebSocket. This means captcha was not required — navigate back so the
     * HomeScreen can retry the search without captcha.
     */
    fun onWebSocketDetected() {
        Log.d(TAG, "WebSocket detected - captcha not required, navigating back")
        _uiState.update { it.copy(captchaNotRequired = true) }
    }

    /**
     * Called when the JS interceptor detects the LiveIdentity captcha widget
     * is visible in the DOM. Updates the UI to inform the user.
     */
    fun onCaptchaWidgetDetected() {
        Log.d(TAG, "Captcha widget detected in DOM")
        _uiState.update { it.copy(captchaDetected = true) }
    }

    /**
     * Called when the ANTS page finishes loading.
     */
    fun onPageLoaded() {
        _uiState.update { it.copy(pageLoaded = true) }
    }

    fun retry() {
        captchaManager.invalidateJwt()
        _uiState.update { CaptchaUiState() }
    }
}
