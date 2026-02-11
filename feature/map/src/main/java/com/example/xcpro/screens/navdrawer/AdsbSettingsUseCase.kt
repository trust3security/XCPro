package com.example.xcpro.screens.navdrawer

import com.example.xcpro.adsb.AdsbTrafficPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class AdsbSettingsUseCase @Inject constructor(
    private val repository: AdsbTrafficPreferencesRepository
) {
    val iconSizePxFlow: Flow<Int> = repository.iconSizePxFlow

    suspend fun setIconSizePx(iconSizePx: Int) {
        repository.setIconSizePx(iconSizePx)
    }
}
