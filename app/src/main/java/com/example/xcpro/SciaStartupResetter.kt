package com.example.xcpro

import com.example.xcpro.ogn.OgnTrailSelectionPreferencesRepository
import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import javax.inject.Inject

class SciaStartupResetter @Inject constructor(
    private val ognTrafficPreferencesRepository: OgnTrafficPreferencesRepository,
    private val ognTrailSelectionPreferencesRepository: OgnTrailSelectionPreferencesRepository
) {
    suspend fun resetForFreshProcessStart() {
        ognTrafficPreferencesRepository.setShowSciaEnabled(false)
        ognTrafficPreferencesRepository.clearTargetSelection()
        ognTrailSelectionPreferencesRepository.clearSelectedAircraft()
    }
}
