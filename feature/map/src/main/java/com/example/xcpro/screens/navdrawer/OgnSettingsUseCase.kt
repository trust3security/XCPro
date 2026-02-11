package com.example.xcpro.screens.navdrawer

import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class OgnSettingsUseCase @Inject constructor(
    private val repository: OgnTrafficPreferencesRepository
) {
    val iconSizePxFlow: Flow<Int> = repository.iconSizePxFlow

    suspend fun setIconSizePx(iconSizePx: Int) {
        repository.setIconSizePx(iconSizePx)
    }
}
