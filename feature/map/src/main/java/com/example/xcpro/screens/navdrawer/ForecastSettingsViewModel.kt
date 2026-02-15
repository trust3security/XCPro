package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.forecast.ForecastAuthCheckResult
import com.example.xcpro.forecast.FORECAST_OPACITY_DEFAULT
import com.example.xcpro.forecast.DEFAULT_FORECAST_REGION_CODE
import com.example.xcpro.forecast.ForecastRegionOption
import com.example.xcpro.forecast.ForecastProviderCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ForecastSettingsViewModel @Inject constructor(
    private val useCase: ForecastSettingsUseCase
) : ViewModel() {
    private val _authConfirmation = MutableStateFlow("Not verified")
    val authConfirmation: StateFlow<String> = _authConfirmation

    private val _authReturnCode = MutableStateFlow<Int?>(null)
    val authReturnCode: StateFlow<Int?> = _authReturnCode

    private val _authChecking = MutableStateFlow(false)
    val authChecking: StateFlow<Boolean> = _authChecking

    val regionOptions: List<ForecastRegionOption> = useCase.availableRegions

    val overlayEnabled: StateFlow<Boolean> = useCase.overlayEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val opacity: StateFlow<Float> = useCase.opacityFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FORECAST_OPACITY_DEFAULT
        )

    val selectedRegion: StateFlow<String> = useCase.selectedRegionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DEFAULT_FORECAST_REGION_CODE
        )

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setOverlayEnabled(enabled)
        }
    }

    fun setOpacity(opacity: Float) {
        viewModelScope.launch {
            useCase.setOpacity(opacity)
        }
    }

    fun setSelectedRegion(regionCode: String) {
        viewModelScope.launch {
            useCase.setSelectedRegion(regionCode)
        }
    }

    fun loadCredentials(): ForecastProviderCredentials? = useCase.loadCredentials()

    fun saveCredentials(username: String, password: String) {
        useCase.saveCredentials(username = username, password = password)
    }

    fun clearCredentials() {
        useCase.clearCredentials()
        resetAuthStatus()
    }

    fun verifyCredentials() {
        if (_authChecking.value) return
        viewModelScope.launch {
            _authChecking.value = true
            _authConfirmation.value = "Verifying..."

            when (val result = useCase.verifyCredentials()) {
                is ForecastAuthCheckResult.Success -> {
                    _authConfirmation.value = "Authentication succeeded"
                    _authReturnCode.value = result.code
                }
                is ForecastAuthCheckResult.HttpError -> {
                    _authConfirmation.value = "Authentication failed (${result.message})"
                    _authReturnCode.value = result.code
                }
                is ForecastAuthCheckResult.NetworkError -> {
                    _authConfirmation.value = "Network error (${result.message})"
                    _authReturnCode.value = null
                }
                ForecastAuthCheckResult.MissingCredentials -> {
                    _authConfirmation.value = "Credentials not set"
                    _authReturnCode.value = null
                }
                ForecastAuthCheckResult.MissingApiKey -> {
                    _authConfirmation.value = "Missing API key"
                    _authReturnCode.value = null
                }
            }

            _authChecking.value = false
        }
    }

    fun resetAuthStatus() {
        _authConfirmation.value = "Not verified"
        _authReturnCode.value = null
        _authChecking.value = false
    }
}
