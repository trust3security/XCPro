package com.trust3.xcpro.ogn

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class OgnTrailSelectionUseCase @Inject constructor(
    private val repository: OgnTrailSelectionPreferencesRepository,
    private val trafficRepository: OgnTrafficRepository
) {
    val selectedAircraftKeys: Flow<Set<String>> = repository.selectedAircraftKeysFlow
    val suppressedTargetIds: Flow<Set<String>> = trafficRepository.suppressedTargetIds

    suspend fun setAircraftSelected(aircraftKey: String, selected: Boolean) {
        repository.setAircraftSelected(aircraftKey, selected)
    }

    suspend fun clearSelectedAircraft() {
        repository.clearSelectedAircraft()
    }

    suspend fun removeAircraftKeys(aircraftKeys: Set<String>) {
        repository.removeAircraftKeys(aircraftKeys)
    }
}
