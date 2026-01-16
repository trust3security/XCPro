// Role: Expose trail settings to Look & Feel UI via a use-case boundary.
// Invariants: UI never accesses preferences directly; updates flow through the use case.
package com.example.xcpro.screens.navdrawer.lookandfeel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.map.trail.MapTrailSettingsUseCase
import com.example.xcpro.map.trail.TrailLength
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.TrailType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for Look & Feel snail trail settings.
 */
@HiltViewModel
class SnailTrailSettingsViewModel @Inject constructor(
    private val trailSettingsUseCase: MapTrailSettingsUseCase
) : ViewModel() {

    val settings: StateFlow<TrailSettings> = trailSettingsUseCase.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = trailSettingsUseCase.getSettings()
        )

    fun setLength(length: TrailLength) {
        trailSettingsUseCase.setTrailLength(length)
    }

    fun setType(type: TrailType) {
        trailSettingsUseCase.setTrailType(type)
    }

    fun setWindDriftEnabled(enabled: Boolean) {
        trailSettingsUseCase.setWindDriftEnabled(enabled)
    }

    fun setScalingEnabled(enabled: Boolean) {
        trailSettingsUseCase.setScalingEnabled(enabled)
    }
}
