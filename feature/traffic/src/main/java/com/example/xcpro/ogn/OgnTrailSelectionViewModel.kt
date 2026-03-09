package com.example.xcpro.ogn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OgnTrailSelectionViewModel @Inject constructor(
    private val useCase: OgnTrailSelectionUseCase
) : ViewModel() {
    val selectedTrailAircraftKeys: StateFlow<Set<String>> = useCase.selectedAircraftKeys
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )

    init {
        viewModelScope.launch {
            useCase.suppressedTargetIds.collect { suppressedKeys ->
                if (suppressedKeys.isEmpty()) return@collect
                useCase.removeAircraftKeys(suppressedKeys)
            }
        }
    }

    fun setTrailAircraftSelected(aircraftKey: String, selected: Boolean) {
        viewModelScope.launch {
            useCase.setAircraftSelected(aircraftKey, selected)
        }
    }

    fun clearTrailAircraftSelection() {
        viewModelScope.launch {
            useCase.clearSelectedAircraft()
        }
    }
}
