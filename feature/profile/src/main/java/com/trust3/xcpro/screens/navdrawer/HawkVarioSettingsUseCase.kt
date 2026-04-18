package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.hawk.HawkVarioPreviewReadPort
import com.trust3.xcpro.vario.LevoVarioPreferencesRepository
import javax.inject.Inject

class HawkVarioSettingsUseCase @Inject constructor(
    private val preferencesRepository: LevoVarioPreferencesRepository,
    private val hawkVarioPreviewReadPort: HawkVarioPreviewReadPort
) {
    val configFlow = preferencesRepository.config
    val hawkVarioUiState = hawkVarioPreviewReadPort.hawkVarioUiState

    suspend fun setShowHawkCard(enabled: Boolean) {
        preferencesRepository.setShowHawkCard(enabled)
    }

    suspend fun setEnableHawkUi(enabled: Boolean) {
        preferencesRepository.setEnableHawkUi(enabled)
    }

    suspend fun setHawkNeedleOmegaMinHz(value: Double) {
        preferencesRepository.setHawkNeedleOmegaMinHz(value)
    }

    suspend fun setHawkNeedleOmegaMaxHz(value: Double) {
        preferencesRepository.setHawkNeedleOmegaMaxHz(value)
    }

    suspend fun setHawkNeedleTargetTauSec(value: Double) {
        preferencesRepository.setHawkNeedleTargetTauSec(value)
    }

    suspend fun setHawkNeedleDriftTauMinSec(value: Double) {
        preferencesRepository.setHawkNeedleDriftTauMinSec(value)
    }

    suspend fun setHawkNeedleDriftTauMaxSec(value: Double) {
        preferencesRepository.setHawkNeedleDriftTauMaxSec(value)
    }
}
