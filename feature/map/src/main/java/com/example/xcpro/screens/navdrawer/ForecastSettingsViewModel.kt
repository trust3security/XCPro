package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.forecast.ForecastAuthCheckResult
import com.example.xcpro.forecast.ForecastCredentialStorageMode
import com.example.xcpro.forecast.FORECAST_OPACITY_DEFAULT
import com.example.xcpro.forecast.FORECAST_WIND_OVERLAY_SCALE_DEFAULT
import com.example.xcpro.forecast.FORECAST_WIND_DISPLAY_MODE_DEFAULT
import com.example.xcpro.forecast.DEFAULT_FORECAST_REGION_CODE
import com.example.xcpro.forecast.ForecastRegionOption
import com.example.xcpro.forecast.ForecastProviderCredentials
import com.example.xcpro.forecast.ForecastWindDisplayMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
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

    private val _savedCredentials = MutableStateFlow<ForecastProviderCredentials?>(null)
    val savedCredentials: StateFlow<ForecastProviderCredentials?> = _savedCredentials

    private val _credentialsStatus = MutableStateFlow("Loading credentials...")
    val credentialsStatus: StateFlow<String> = _credentialsStatus

    private val _credentialStorageMode = MutableStateFlow(ForecastCredentialStorageMode.ENCRYPTED)
    val credentialStorageMode: StateFlow<ForecastCredentialStorageMode> = _credentialStorageMode

    val regionOptions: List<ForecastRegionOption> = useCase.availableRegions
    val windDisplayModes: List<ForecastWindDisplayMode> = useCase.windDisplayModes

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

    val windOverlayScale: StateFlow<Float> = useCase.windOverlayScaleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FORECAST_WIND_OVERLAY_SCALE_DEFAULT
        )

    val windDisplayMode: StateFlow<ForecastWindDisplayMode> = useCase.windDisplayModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FORECAST_WIND_DISPLAY_MODE_DEFAULT
        )

    val selectedRegion: StateFlow<String> = useCase.selectedRegionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DEFAULT_FORECAST_REGION_CODE
        )

    init {
        refreshCredentials()
    }

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

    fun setWindOverlayScale(scale: Float) {
        viewModelScope.launch {
            useCase.setWindOverlayScale(scale)
        }
    }

    fun setWindDisplayMode(mode: ForecastWindDisplayMode) {
        viewModelScope.launch {
            useCase.setWindDisplayMode(mode)
        }
    }

    fun setSelectedRegion(regionCode: String) {
        viewModelScope.launch {
            useCase.setSelectedRegion(regionCode)
        }
    }

    fun saveCredentials(username: String, password: String) {
        viewModelScope.launch {
            useCase.saveCredentials(username = username, password = password)
            refreshCredentialsSnapshot()
            verifyCredentials()
        }
    }

    fun clearCredentials() {
        viewModelScope.launch {
            useCase.clearCredentials()
            refreshCredentialsSnapshot()
            resetAuthStatus()
        }
    }

    fun verifyCredentials() {
        if (_authChecking.value) return
        viewModelScope.launch {
            _authChecking.value = true
            _authConfirmation.value = "Verifying..."
            try {
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
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _authConfirmation.value = "Verification failed (${t::class.java.simpleName})"
                _authReturnCode.value = null
            } finally {
                _authChecking.value = false
            }
        }
    }

    fun resetAuthStatus() {
        _authConfirmation.value = "Not verified"
        _authReturnCode.value = null
        _authChecking.value = false
    }

    fun refreshCredentials() {
        viewModelScope.launch {
            refreshCredentialsSnapshot()
        }
    }

    private suspend fun refreshCredentialsSnapshot() {
        _credentialStorageMode.value = useCase.credentialStorageMode()
        val credentials = useCase.loadCredentials()
        _savedCredentials.value = credentials
        _credentialsStatus.value = if (credentials == null) {
            "Credentials not set"
        } else {
            "Credentials saved"
        }
    }
}
