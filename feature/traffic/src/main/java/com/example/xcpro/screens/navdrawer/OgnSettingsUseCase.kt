package com.example.xcpro.screens.navdrawer

import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import com.example.xcpro.ogn.OgnDisplayUpdateMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class OgnSettingsUseCase @Inject constructor(
    private val repository: OgnTrafficPreferencesRepository
) {
    val overlayEnabledFlow: Flow<Boolean> = repository.enabledFlow
    val showSciaEnabledFlow: Flow<Boolean> = repository.showSciaEnabledFlow
    val iconSizePxFlow: Flow<Int> = repository.iconSizePxFlow
    val receiveRadiusKmFlow: Flow<Int> = repository.receiveRadiusKmFlow
    val autoReceiveRadiusEnabledFlow: Flow<Boolean> = repository.autoReceiveRadiusEnabledFlow
    val displayUpdateModeFlow: Flow<OgnDisplayUpdateMode> = repository.displayUpdateModeFlow
    val ownFlarmHexFlow: Flow<String?> = repository.ownFlarmHexFlow
    val ownIcaoHexFlow: Flow<String?> = repository.ownIcaoHexFlow

    suspend fun setIconSizePx(iconSizePx: Int) {
        repository.setIconSizePx(iconSizePx)
    }

    suspend fun setReceiveRadiusKm(radiusKm: Int) {
        repository.setReceiveRadiusKm(radiusKm)
    }

    suspend fun setAutoReceiveRadiusEnabled(enabled: Boolean) {
        repository.setAutoReceiveRadiusEnabled(enabled)
    }

    suspend fun setDisplayUpdateMode(mode: OgnDisplayUpdateMode) {
        repository.setDisplayUpdateMode(mode)
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        repository.setEnabled(enabled)
    }

    suspend fun setShowSciaEnabled(enabled: Boolean) {
        repository.setShowSciaEnabled(enabled)
    }

    suspend fun setOverlayAndShowSciaEnabled(
        overlayEnabled: Boolean,
        showSciaEnabled: Boolean
    ) {
        repository.setOverlayAndSciaEnabled(
            overlayEnabled = overlayEnabled,
            showSciaEnabled = showSciaEnabled
        )
    }

    suspend fun setOwnFlarmHex(value: String?) {
        repository.setOwnFlarmHex(value)
    }

    suspend fun setOwnIcaoHex(value: String?) {
        repository.setOwnIcaoHex(value)
    }
}
