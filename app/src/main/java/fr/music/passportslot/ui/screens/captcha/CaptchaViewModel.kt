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
    val isLoading: Boolean = true,
    val antibotId: String? = null,
    val requestId: String? = null,
    val error: String? = null,
    val isProcessingToken: Boolean = false,
    val captchaSuccess: Boolean = false
)

@HiltViewModel
class CaptchaViewModel @Inject constructor(
    private val captchaManager: CaptchaManager
) : ViewModel() {

    companion object {
        private const val TAG = "CaptchaViewModel"
    }

    private val _uiState = MutableStateFlow(CaptchaUiState())
    val uiState: StateFlow<CaptchaUiState> = _uiState.asStateFlow()

    init {
        loadAntibotInfo()
    }

    private fun loadAntibotInfo() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val info = captchaManager.requestAntibotInfo()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        antibotId = info.antibotId,
                        requestId = info.requestId
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load antibot info", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Impossible de charger le captcha: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Called when the WebView has successfully obtained the captcha token
     * from the solved captcha widget.
     */
    fun onCaptchaTokenObtained(token: String) {
        Log.d(TAG, "Captcha token obtained, exchanging for JWT...")
        _uiState.update { it.copy(isProcessingToken = true) }

        viewModelScope.launch {
            try {
                // Step 2: Exchange captcha token for JWT
                val jwt = captchaManager.initCaptchaJwt()

                // Step 3: Validate the JWT
                val valid = captchaManager.validateAndStoreJwt(jwt)
                if (valid) {
                    Log.d(TAG, "Captcha JWT validated successfully")
                    _uiState.update {
                        it.copy(
                            isProcessingToken = false,
                            captchaSuccess = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isProcessingToken = false,
                            error = "La validation du captcha a echoue. Veuillez reessayer."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process captcha token", e)
                _uiState.update {
                    it.copy(
                        isProcessingToken = false,
                        error = "Erreur lors de la validation: ${e.message}"
                    )
                }
            }
        }
    }

    fun retry() {
        captchaManager.invalidateJwt()
        _uiState.update {
            CaptchaUiState(isLoading = true)
        }
        loadAntibotInfo()
    }
}
