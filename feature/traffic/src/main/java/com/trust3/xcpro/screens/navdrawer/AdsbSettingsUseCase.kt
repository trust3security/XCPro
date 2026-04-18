package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.adsb.AdsbTrafficRepository
import com.trust3.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.trust3.xcpro.adsb.OpenSkyClientCredentials
import com.trust3.xcpro.adsb.OpenSkyCredentialsRepository
import com.trust3.xcpro.adsb.OpenSkyTokenRepository
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.common.units.UnitsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class AdsbSettingsUseCase @Inject constructor(
    private val repository: AdsbTrafficPreferencesRepository,
    private val credentialsRepository: OpenSkyCredentialsRepository,
    private val tokenRepository: OpenSkyTokenRepository,
    private val adsbTrafficRepository: AdsbTrafficRepository,
    private val unitsRepository: UnitsRepository
) {
    val iconSizePxFlow: Flow<Int> = repository.iconSizePxFlow
    val maxDistanceKmFlow: Flow<Int> = repository.maxDistanceKmFlow
    val verticalAboveMetersFlow: Flow<Double> = repository.verticalAboveMetersFlow
    val verticalBelowMetersFlow: Flow<Double> = repository.verticalBelowMetersFlow
    val emergencyFlashEnabledFlow: Flow<Boolean> = repository.emergencyFlashEnabledFlow
    val emergencyAudioEnabledFlow: Flow<Boolean> = repository.emergencyAudioEnabledFlow
    val emergencyAudioCooldownMsFlow: Flow<Long> = repository.emergencyAudioCooldownMsFlow
    val emergencyAudioMasterEnabledFlow: Flow<Boolean> = repository.emergencyAudioMasterEnabledFlow
    val emergencyAudioShadowModeFlow: Flow<Boolean> = repository.emergencyAudioShadowModeFlow
    val emergencyAudioRollbackLatchedFlow: Flow<Boolean> = repository.emergencyAudioRollbackLatchedFlow
    val emergencyAudioRollbackReasonFlow: Flow<String?> = repository.emergencyAudioRollbackReasonFlow
    val unitsFlow: Flow<UnitsPreferences> = unitsRepository.unitsFlow

    suspend fun setIconSizePx(iconSizePx: Int) {
        repository.setIconSizePx(iconSizePx)
    }

    suspend fun setMaxDistanceKm(maxDistanceKm: Int) {
        repository.setMaxDistanceKm(maxDistanceKm)
        // Radius changes should apply immediately without waiting a full polling interval.
        adsbTrafficRepository.reconnectNow()
    }

    suspend fun setVerticalAboveMeters(aboveMeters: Double) {
        repository.setVerticalAboveMeters(aboveMeters)
    }

    suspend fun setVerticalBelowMeters(belowMeters: Double) {
        repository.setVerticalBelowMeters(belowMeters)
    }

    suspend fun setEmergencyFlashEnabled(enabled: Boolean) {
        repository.setEmergencyFlashEnabled(enabled)
    }

    suspend fun setEmergencyAudioEnabled(enabled: Boolean) {
        repository.setEmergencyAudioEnabled(enabled)
    }

    suspend fun setEmergencyAudioCooldownMs(cooldownMs: Long) {
        repository.setEmergencyAudioCooldownMs(cooldownMs)
    }

    suspend fun setEmergencyAudioMasterEnabled(enabled: Boolean) {
        repository.setEmergencyAudioMasterEnabled(enabled)
    }

    suspend fun setEmergencyAudioShadowMode(enabled: Boolean) {
        repository.setEmergencyAudioShadowMode(enabled)
    }

    suspend fun clearEmergencyAudioRollback() {
        repository.clearEmergencyAudioRollback()
    }

    fun loadOpenSkyCredentials(): OpenSkyClientCredentials? =
        credentialsRepository.loadCredentials()

    fun saveOpenSkyCredentials(clientId: String, clientSecret: String) {
        credentialsRepository.saveCredentials(clientId = clientId, clientSecret = clientSecret)
        tokenRepository.invalidate()
        adsbTrafficRepository.reconnectNow()
    }

    fun clearOpenSkyCredentials() {
        credentialsRepository.clearCredentials()
        tokenRepository.invalidate()
        adsbTrafficRepository.reconnectNow()
    }
}
