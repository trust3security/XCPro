package com.example.xcpro.navdrawer

import com.example.xcpro.ConfigurationRepository
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
