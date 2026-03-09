package com.example.xcpro.flightdata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dfcards.FlightModeSelection
import com.example.xcpro.profiles.ProfileIdResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FlightMgmtPreferencesViewModel @Inject constructor(
    private val useCase: FlightMgmtPreferencesUseCase
) : ViewModel() {

    private val profileId = MutableStateFlow(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID)

    val activeTab: StateFlow<String> = useCase.observeLastActiveTab()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = useCase.getLastActiveTab()
        )

    val lastFlightMode: StateFlow<FlightModeSelection> = profileId
        .flatMapLatest { id -> useCase.observeLastFlightMode(id) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = useCase.getLastFlightMode(profileId.value)
        )

    fun setProfileId(id: String) {
        if (profileId.value != id) {
            profileId.value = id
        }
    }

    fun setActiveTab(tab: String) {
        useCase.setLastActiveTab(tab)
    }

    fun setLastFlightMode(mode: FlightModeSelection) {
        useCase.setLastFlightMode(profileId.value, mode)
    }
}
