package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.common.units.UnitsSettingsUseCase
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.hawk.HawkVarioUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HawkVarioSettingsUiState(
    val enableHawkUi: Boolean = false,
    val showHawkCard: Boolean = false,
    val hawkVarioUiState: HawkVarioUiState = HawkVarioUiState(),
    val unitsPreferences: UnitsPreferences = UnitsPreferences(),
    val needleOmegaMinHz: Double = 0.9,
    val needleOmegaMaxHz: Double = 2.0,
    val needleTargetTauSec: Double = 0.8,
    val needleDriftTauMinSec: Double = 3.5,
    val needleDriftTauMaxSec: Double = 8.0
)

@HiltViewModel
class HawkVarioSettingsViewModel @Inject constructor(
    private val useCase: HawkVarioSettingsUseCase,
    private val unitsUseCase: UnitsSettingsUseCase
) : ViewModel() {

    val uiState: StateFlow<HawkVarioSettingsUiState> =
        combine(useCase.configFlow, useCase.hawkVarioUiState, unitsUseCase.unitsFlow) { config, hawkState, units ->
            HawkVarioSettingsUiState(
                enableHawkUi = config.enableHawkUi,
                showHawkCard = config.showHawkCard,
                hawkVarioUiState = hawkState,
                unitsPreferences = units,
                needleOmegaMinHz = config.hawkNeedleOmegaMinHz,
                needleOmegaMaxHz = config.hawkNeedleOmegaMaxHz,
                needleTargetTauSec = config.hawkNeedleTargetTauSec,
                needleDriftTauMinSec = config.hawkNeedleDriftTauMinSec,
                needleDriftTauMaxSec = config.hawkNeedleDriftTauMaxSec
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HawkVarioSettingsUiState()
            )

    fun setProfileId(profileId: String) {
        unitsUseCase.setActiveProfileId(profileId)
    }

    fun setEnableHawkUi(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setEnableHawkUi(enabled)
        }
    }

    fun setShowHawkCard(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setShowHawkCard(enabled)
        }
    }

    fun setNeedleOmegaMinHz(value: Double) {
        val maxHz = uiState.value.needleOmegaMaxHz
        val clamped = value.coerceIn(OMEGA_MIN_HZ_RANGE_START, OMEGA_MIN_HZ_RANGE_END)
            .coerceAtMost(maxHz)
        viewModelScope.launch {
            useCase.setHawkNeedleOmegaMinHz(clamped)
        }
    }

    fun setNeedleOmegaMaxHz(value: Double) {
        val minHz = uiState.value.needleOmegaMinHz
        val clamped = value.coerceIn(OMEGA_MAX_HZ_RANGE_START, OMEGA_MAX_HZ_RANGE_END)
            .coerceAtLeast(minHz)
        viewModelScope.launch {
            useCase.setHawkNeedleOmegaMaxHz(clamped)
        }
    }

    fun setNeedleTargetTauSec(value: Double) {
        val clamped = value.coerceIn(TARGET_TAU_RANGE_START, TARGET_TAU_RANGE_END)
        viewModelScope.launch {
            useCase.setHawkNeedleTargetTauSec(clamped)
        }
    }

    fun setNeedleDriftTauMinSec(value: Double) {
        val maxSec = uiState.value.needleDriftTauMaxSec
        val clamped = value.coerceIn(DRIFT_TAU_MIN_RANGE_START, DRIFT_TAU_MIN_RANGE_END)
            .coerceAtMost(maxSec)
        viewModelScope.launch {
            useCase.setHawkNeedleDriftTauMinSec(clamped)
        }
    }

    fun setNeedleDriftTauMaxSec(value: Double) {
        val minSec = uiState.value.needleDriftTauMinSec
        val clamped = value.coerceIn(DRIFT_TAU_MAX_RANGE_START, DRIFT_TAU_MAX_RANGE_END)
            .coerceAtLeast(minSec)
        viewModelScope.launch {
            useCase.setHawkNeedleDriftTauMaxSec(clamped)
        }
    }

    private companion object {
        private const val OMEGA_MIN_HZ_RANGE_START = 0.5
        private const val OMEGA_MIN_HZ_RANGE_END = 2.5
        private const val OMEGA_MAX_HZ_RANGE_START = 1.0
        private const val OMEGA_MAX_HZ_RANGE_END = 4.0
        private const val TARGET_TAU_RANGE_START = 0.2
        private const val TARGET_TAU_RANGE_END = 2.0
        private const val DRIFT_TAU_MIN_RANGE_START = 1.0
        private const val DRIFT_TAU_MIN_RANGE_END = 8.0
        private const val DRIFT_TAU_MAX_RANGE_START = 2.0
        private const val DRIFT_TAU_MAX_RANGE_END = 15.0
    }
}
