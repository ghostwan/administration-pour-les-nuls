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
    val hasCaptchaJwt: Boolean = false
)

/**
 * ViewModel for the CaptchaScreen.
 *
 * With the new approach (loading the real ANTS website), the ViewModel's
 * role is simpler: it receives the captcha JWT captured by the JavaScript
 * interceptor from the real ANTS site's initCaptchaJWT call, validates it,
 * and stores it in CaptchaManager.
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
                            captchaSuccess = true,
                            hasCaptchaJwt = true
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
                            captchaSuccess = true,
                            hasCaptchaJwt = true
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
                        captchaSuccess = true,
                        hasCaptchaJwt = true
                    )
                }
            }
        }
    }

    /**
     * Called when the JS interceptor detects that the ANTS site started a
     * WebSocket search (SlotsFromPositionStreaming), meaning the captcha was
     * either solved silently or was not required at all.
     *
     * We don't have a captcha JWT in this case, but we know the ANTS site
     * let the search through. Navigate back so the user can retry with our
     * own WebSocket — it may also work without captcha now.
     */
    @Suppress("UNUSED_PARAMETER")
    fun onSearchStartedWithoutCaptcha(authToken: String) {
        Log.d(TAG, "ANTS site started search without captcha, navigating back")
        _uiState.update {
            it.copy(
                isProcessingToken = false,
                captchaSuccess = true
            )
        }
    }

    fun retry() {
        captchaManager.invalidateJwt()
        _uiState.update { CaptchaUiState() }
    }
}
