package com.trust3.xcpro.navdrawer

import com.trust3.xcpro.ConfigurationRepository
import javax.inject.Inject

class NavDrawerConfigUseCase @Inject constructor(
    private val repository: ConfigurationRepository
) {
    suspend fun saveNavDrawerConfig(
        profileExpanded: Boolean,
        mapStyleExpanded: Boolean,
        settingsExpanded: Boolean
    ) {
        repository.saveNavDrawerConfig(
            profileExpanded = profileExpanded,
            mapStyleExpanded = mapStyleExpanded,
            settingsExpanded = settingsExpanded
        )
    }
}
