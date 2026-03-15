package fr.music.passportslot.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.music.passportslot.data.local.AppDatabase
import fr.music.passportslot.data.model.SearchConfig
import fr.music.passportslot.data.repository.SlotRepository
import fr.music.passportslot.worker.SlotCheckWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val activeConfigs: List<SearchConfig> = emptyList(),
    val checkIntervalMinutes: Int = 15,
    val isLoading: Boolean = true,
    val showSavedMessage: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val slotRepository: SlotRepository,
    private val database: AppDatabase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            slotRepository.getActiveSearchConfigs().collect { configs ->
                _uiState.update {
                    it.copy(
                        activeConfigs = configs,
                        isLoading = false,
                        checkIntervalMinutes = configs.firstOrNull()?.checkIntervalMinutes ?: 15
                    )
                }
            }
        }
    }

    fun saveCheckInterval(minutes: Int) {
        _uiState.update { it.copy(checkIntervalMinutes = minutes) }
        viewModelScope.launch {
            val configs = database.searchConfigDao().getActiveConfigsList()
            configs.forEach { config ->
                database.searchConfigDao().update(config.copy(checkIntervalMinutes = minutes))
            }
            if (configs.isNotEmpty()) {
                SlotCheckWorker.schedule(getApplication(), minutes.toLong())
            }
            _uiState.update { it.copy(showSavedMessage = true) }
        }
    }

    fun dismissSavedMessage() {
        _uiState.update { it.copy(showSavedMessage = false) }
    }

    fun deleteConfig(config: SearchConfig) {
        viewModelScope.launch {
            slotRepository.deleteSearchConfig(config)
            val remaining = database.searchConfigDao().getActiveConfigsList()
            if (remaining.isEmpty()) {
                SlotCheckWorker.cancel(getApplication())
            }
        }
    }

    fun toggleConfig(config: SearchConfig) {
        viewModelScope.launch {
            slotRepository.updateSearchConfig(config.copy(isActive = !config.isActive))
            val activeConfigs = database.searchConfigDao().getActiveConfigsList()
            if (activeConfigs.isNotEmpty()) {
                SlotCheckWorker.schedule(getApplication())
            } else {
                SlotCheckWorker.cancel(getApplication())
            }
        }
    }

    fun runCheckNow() {
        SlotCheckWorker.runOnce(getApplication())
    }
}
