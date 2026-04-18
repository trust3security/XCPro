package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.forecast.ForecastAuthCheckResult
import com.trust3.xcpro.forecast.ForecastAuthRepository
import com.trust3.xcpro.forecast.ForecastCredentialStorageMode
import com.trust3.xcpro.forecast.ForecastCredentialsRepository
import com.trust3.xcpro.forecast.ForecastRegionOption
import com.trust3.xcpro.forecast.ForecastWindDisplayMode
import com.trust3.xcpro.forecast.ForecastPreferencesRepository
import com.trust3.xcpro.forecast.FORECAST_REGION_OPTIONS
import com.trust3.xcpro.forecast.ForecastProviderCredentials
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ForecastSettingsUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository,
    private val credentialsRepository: ForecastCredentialsRepository,
    private val authRepository: ForecastAuthRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    val overlayEnabledFlow: Flow<Boolean> = preferencesRepository.overlayEnabledFlow
    val opacityFlow: Flow<Float> = preferencesRepository.opacityFlow
    val windOverlayScaleFlow: Flow<Float> = preferencesRepository.windOverlayScaleFlow
    val windDisplayModeFlow: Flow<ForecastWindDisplayMode> = preferencesRepository.windDisplayModeFlow
    val selectedRegionFlow: Flow<String> = preferencesRepository.selectedRegionFlow
    val availableRegions: List<ForecastRegionOption> = FORECAST_REGION_OPTIONS
    val windDisplayModes: List<ForecastWindDisplayMode> = ForecastWindDisplayMode.entries

    suspend fun setOverlayEnabled(enabled: Boolean) {
        preferencesRepository.setOverlayEnabled(enabled)
    }

    suspend fun setOpacity(opacity: Float) {
        preferencesRepository.setOpacity(opacity)
    }

    suspend fun setWindOverlayScale(scale: Float) {
        preferencesRepository.setWindOverlayScale(scale)
    }

    suspend fun setWindDisplayMode(mode: ForecastWindDisplayMode) {
        preferencesRepository.setWindDisplayMode(mode)
    }

    suspend fun setSelectedRegion(regionCode: String) {
        preferencesRepository.setSelectedRegion(regionCode)
    }

    suspend fun loadCredentials(): ForecastProviderCredentials? = withContext(dispatcher) {
        credentialsRepository.loadCredentials()
    }

    suspend fun saveCredentials(username: String, password: String) = withContext(dispatcher) {
        credentialsRepository.saveCredentials(username = username, password = password)
    }

    suspend fun clearCredentials() = withContext(dispatcher) {
        credentialsRepository.clearCredentials()
    }

    suspend fun credentialStorageMode(): ForecastCredentialStorageMode = withContext(dispatcher) {
        credentialsRepository.credentialStorageMode()
    }

    suspend fun volatileFallbackAllowed(): Boolean = withContext(dispatcher) {
        credentialsRepository.volatileFallbackAllowed()
    }

    suspend fun setVolatileFallbackAllowed(allowed: Boolean) = withContext(dispatcher) {
        credentialsRepository.setVolatileFallbackAllowed(allowed)
    }

    suspend fun verifyCredentials(): ForecastAuthCheckResult =
        authRepository.verifySavedCredentials()
}
