package com.example.xcpro.flightdata

import com.example.dfcards.FlightModeSelection
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class FlightMgmtPreferencesUseCase @Inject constructor(
    private val repository: FlightMgmtPreferencesRepository
) {
    fun getLastActiveTab(): String = repository.getLastActiveTab()

    fun observeLastActiveTab(): Flow<String> = repository.observeLastActiveTab()

    fun setLastActiveTab(tab: String) {
        repository.setLastActiveTab(tab)
    }

    fun getLastFlightMode(profileId: String): FlightModeSelection =
        repository.getLastFlightMode(profileId)

    fun observeLastFlightMode(profileId: String): Flow<FlightModeSelection> =
        repository.observeLastFlightMode(profileId)

    fun setLastFlightMode(profileId: String, mode: FlightModeSelection) {
        repository.setLastFlightMode(profileId, mode)
    }
}
