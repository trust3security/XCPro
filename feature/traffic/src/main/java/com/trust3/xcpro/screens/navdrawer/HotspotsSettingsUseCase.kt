package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.ogn.OgnTrafficPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class HotspotsSettingsUseCase @Inject constructor(
    private val repository: OgnTrafficPreferencesRepository
) {
    val thermalRetentionHoursFlow: Flow<Int> = repository.thermalRetentionHoursFlow
    val hotspotsDisplayPercentFlow: Flow<Int> = repository.hotspotsDisplayPercentFlow

    suspend fun setThermalRetentionHours(hours: Int) {
        repository.setThermalRetentionHours(hours)
    }

    suspend fun setHotspotsDisplayPercent(percent: Int) {
        repository.setHotspotsDisplayPercent(percent)
    }
}
