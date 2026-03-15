package fr.music.passportslot.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.music.passportslot.data.api.SlotSearchResult
import fr.music.passportslot.data.model.*
import fr.music.passportslot.data.repository.GeocodingRepository
import fr.music.passportslot.data.repository.SlotRepository
import fr.music.passportslot.worker.SlotCheckWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val addressQuery: String = "",
    val suggestions: List<GeoFeature> = emptyList(),
    val selectedAddress: GeoFeature? = null,
    val radiusKm: Int = 10,
    val reason: AppointmentReason = AppointmentReason.PASSPORT,
    val documentsNumber: Int = 1,
    val isSearching: Boolean = false,
    val searchProgress: String = "",
    val foundSlots: List<Slot> = emptyList(),
    val meetingPointsChecked: Int = 0,
    val errorMessage: String? = null,
    val isMonitoringActive: Boolean = false,
    val showSuggestions: Boolean = false,
    val captchaRequired: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val slotRepository: SlotRepository,
    private val geocodingRepository: GeocodingRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null

    init {
        // Check if monitoring is already active
        viewModelScope.launch {
            slotRepository.getActiveSearchConfigs().collect { configs ->
                _uiState.update { it.copy(isMonitoringActive = configs.isNotEmpty()) }
            }
        }
    }

    fun onAddressQueryChanged(query: String) {
        _uiState.update { it.copy(addressQuery = query, showSuggestions = true) }

        suggestionJob?.cancel()
        if (query.length >= 3) {
            suggestionJob = viewModelScope.launch {
                val results = geocodingRepository.searchMunicipality(query)
                _uiState.update { it.copy(suggestions = results) }
            }
        } else {
            _uiState.update { it.copy(suggestions = emptyList()) }
        }
    }

    fun onSuggestionSelected(feature: GeoFeature) {
        val label = feature.properties.label ?: feature.properties.city ?: ""
        _uiState.update {
            it.copy(
                addressQuery = label,
                selectedAddress = feature,
                suggestions = emptyList(),
                showSuggestions = false
            )
        }
    }

    fun onRadiusChanged(radius: Int) {
        _uiState.update { it.copy(radiusKm = radius) }
    }

    fun onReasonChanged(reason: AppointmentReason) {
        _uiState.update { it.copy(reason = reason) }
    }

    fun onDocumentsNumberChanged(number: Int) {
        _uiState.update { it.copy(documentsNumber = number) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun hideSuggestions() {
        _uiState.update { it.copy(showSuggestions = false) }
    }

    /**
     * Start a manual slot search.
     */
    fun searchSlots() {
        val state = _uiState.value
        val selectedAddress = state.selectedAddress

        if (selectedAddress == null) {
            _uiState.update { it.copy(errorMessage = "Veuillez sélectionner une adresse") }
            return
        }

        val coords = selectedAddress.geometry.coordinates
        if (coords.size < 2) {
            _uiState.update { it.copy(errorMessage = "Coordonnées invalides") }
            return
        }

        val longitude = coords[0]
        val latitude = coords[1]

        searchJob?.cancel()
        _uiState.update {
            it.copy(
                isSearching = true,
                foundSlots = emptyList(),
                meetingPointsChecked = 0,
                searchProgress = "Connexion...",
                errorMessage = null
            )
        }

        val config = SearchConfig(
            latitude = latitude,
            longitude = longitude,
            address = state.addressQuery,
            radiusKm = state.radiusKm,
            reason = state.reason,
            documentsNumber = state.documentsNumber
        )

        searchJob = viewModelScope.launch {
            slotRepository.searchSlots(config)
                .onEach { result ->
                    when (result) {
                        is SlotSearchResult.Connected -> {
                            _uiState.update { it.copy(searchProgress = "Recherche en cours...") }
                        }
                        is SlotSearchResult.SlotsFound -> {
                            _uiState.update { current ->
                                current.copy(
                                    foundSlots = current.foundSlots + result.slots,
                                    searchProgress = "${current.foundSlots.size + result.slots.size} créneau(x) trouvé(s)"
                                )
                            }
                        }
                        is SlotSearchResult.MeetingPointNoSlots -> {
                            _uiState.update { current ->
                                current.copy(
                                    meetingPointsChecked = current.meetingPointsChecked + 1,
                                    searchProgress = "${current.meetingPointsChecked + 1} mairie(s) vérifiée(s) - ${result.name}"
                                )
                            }
                        }
                        is SlotSearchResult.Completed -> {
                            val currentState = _uiState.value
                            _uiState.update {
                                it.copy(
                                    isSearching = false,
                                    searchProgress = if (currentState.foundSlots.isEmpty())
                                        "Aucun créneau disponible (${currentState.meetingPointsChecked} mairies vérifiées)"
                                    else
                                        "${currentState.foundSlots.size} créneau(x) trouvé(s) dans ${currentState.meetingPointsChecked} mairies"
                                )
                            }
                        }
                        is SlotSearchResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    isSearching = false,
                                    errorMessage = result.message
                                )
                            }
                        }
                        is SlotSearchResult.CaptchaRequired -> {
                            _uiState.update {
                                it.copy(
                                    isSearching = false,
                                    captchaRequired = true,
                                    searchProgress = "Captcha requis - veuillez le resoudre"
                                )
                            }
                        }
                    }
                }
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            errorMessage = "Erreur: ${e.message}"
                        )
                    }
                }
                .collect()
        }
    }

    /**
     * Start background monitoring for available slots.
     */
    fun startMonitoring() {
        val state = _uiState.value
        val selectedAddress = state.selectedAddress

        if (selectedAddress == null) {
            _uiState.update { it.copy(errorMessage = "Veuillez sélectionner une adresse") }
            return
        }

        val coords = selectedAddress.geometry.coordinates
        val longitude = coords[0]
        val latitude = coords[1]

        viewModelScope.launch {
            val config = SearchConfig(
                latitude = latitude,
                longitude = longitude,
                address = state.addressQuery,
                radiusKm = state.radiusKm,
                reason = state.reason,
                documentsNumber = state.documentsNumber,
                isActive = true
            )

            slotRepository.saveSearchConfig(config)
            SlotCheckWorker.schedule(getApplication())
            _uiState.update { it.copy(isMonitoringActive = true) }
        }
    }

    /**
     * Stop background monitoring.
     */
    fun stopMonitoring() {
        viewModelScope.launch {
            slotRepository.getActiveSearchConfigs().first().forEach { config ->
                slotRepository.updateSearchConfig(config.copy(isActive = false))
            }
            SlotCheckWorker.cancel(getApplication())
            _uiState.update { it.copy(isMonitoringActive = false) }
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                isSearching = false,
                searchProgress = "Recherche annulée"
            )
        }
    }

    fun onCaptchaCompleted() {
        _uiState.update { it.copy(captchaRequired = false) }
        // Auto-retry the search after captcha is solved
        searchSlots()
    }

    fun dismissCaptchaRequired() {
        _uiState.update { it.copy(captchaRequired = false) }
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
        suggestionJob?.cancel()
    }
}
