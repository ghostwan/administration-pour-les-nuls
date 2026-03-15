package fr.music.passportslot.ui.screens.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.music.passportslot.data.local.AppDatabase
import fr.music.passportslot.data.model.FoundSlot
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultsUiState(
    val slots: List<FoundSlot> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val database: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            database.foundSlotDao().getAllActive()
                .collect { slots ->
                    _uiState.update {
                        it.copy(
                            slots = slots,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun dismissSlot(id: Long) {
        viewModelScope.launch {
            database.foundSlotDao().dismiss(id)
        }
    }

    fun dismissAll() {
        viewModelScope.launch {
            database.foundSlotDao().dismissAll()
        }
    }
}
