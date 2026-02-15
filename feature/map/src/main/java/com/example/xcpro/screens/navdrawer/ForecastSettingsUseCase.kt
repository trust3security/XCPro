package com.example.xcpro.screens.navdrawer

import com.example.xcpro.forecast.ForecastAuthCheckResult
import com.example.xcpro.forecast.ForecastAuthRepository
import com.example.xcpro.forecast.ForecastCredentialsRepository
import com.example.xcpro.forecast.ForecastRegionOption
import com.example.xcpro.forecast.ForecastPreferencesRepository
import com.example.xcpro.forecast.FORECAST_REGION_OPTIONS
import com.example.xcpro.forecast.ForecastProviderCredentials
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ForecastSettingsUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository,
    private val credentialsRepository: ForecastCredentialsRepository,
    private val authRepository: ForecastAuthRepository
) {
    val overlayEnabledFlow: Flow<Boolean> = preferencesRepository.overlayEnabledFlow
    val opacityFlow: Flow<Float> = preferencesRepository.opacityFlow
    val selectedRegionFlow: Flow<String> = preferencesRepository.selectedRegionFlow
    val availableRegions: List<ForecastRegionOption> = FORECAST_REGION_OPTIONS

    suspend fun setOverlayEnabled(enabled: Boolean) {
        preferencesRepository.setOverlayEnabled(enabled)
    }

    suspend fun setOpacity(opacity: Float) {
        preferencesRepository.setOpacity(opacity)
    }

    suspend fun setSelectedRegion(regionCode: String) {
        preferencesRepository.setSelectedRegion(regionCode)
    }

    fun loadCredentials(): ForecastProviderCredentials? =
        credentialsRepository.loadCredentials()

    fun saveCredentials(username: String, password: String) {
        credentialsRepository.saveCredentials(username = username, password = password)
    }

    fun clearCredentials() {
        credentialsRepository.clearCredentials()
    }

    suspend fun verifyCredentials(): ForecastAuthCheckResult =
        authRepository.verifySavedCredentials()
}
