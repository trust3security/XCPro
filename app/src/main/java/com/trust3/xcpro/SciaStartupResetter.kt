package com.trust3.xcpro

import com.trust3.xcpro.ogn.OgnTrailSelectionPreferencesRepository
import com.trust3.xcpro.ogn.OgnTrafficPreferencesRepository
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
