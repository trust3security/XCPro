package com.example.xcpro.screens.navdrawer

import com.example.xcpro.forecast.DEFAULT_FORECAST_REGION_CODE
import com.example.xcpro.forecast.ForecastCredentialStorageMode
import com.example.xcpro.forecast.FORECAST_OPACITY_DEFAULT
import com.example.xcpro.forecast.FORECAST_WIND_DISPLAY_MODE_DEFAULT
import com.example.xcpro.forecast.FORECAST_WIND_OVERLAY_SCALE_DEFAULT
import com.example.xcpro.forecast.ForecastAuthCheckResult
import com.example.xcpro.forecast.ForecastAuthRepository
import com.example.xcpro.forecast.ForecastCredentialsRepository
import com.example.xcpro.forecast.ForecastPreferencesRepository
import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ForecastSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferencesRepository: ForecastPreferencesRepository = mock()
    private val credentialsRepository: ForecastCredentialsRepository = mock()
    private val authRepository: ForecastAuthRepository = mock()

    @Test
    fun verifyCredentials_success_updatesStateAndResetsChecking() = runTest {
        whenever(credentialsRepository.credentialStorageMode()).thenReturn(
            ForecastCredentialStorageMode.ENCRYPTED
        )
        whenever(credentialsRepository.loadCredentials()).thenReturn(null)
        whenever(authRepository.verifySavedCredentials()).thenReturn(
            ForecastAuthCheckResult.Success(
                code = 200,
                message = "OK"
            )
        )
        val viewModel = ForecastSettingsViewModel(createUseCase())

        viewModel.verifyCredentials()
        runCurrent()

        assertFalse(viewModel.authChecking.value)
        assertEquals("Authentication succeeded", viewModel.authConfirmation.value)
        assertEquals(200, viewModel.authReturnCode.value)
    }

    @Test
    fun verifyCredentials_unexpectedException_setsFailureAndResetsChecking() = runTest {
        whenever(credentialsRepository.credentialStorageMode()).thenReturn(
            ForecastCredentialStorageMode.ENCRYPTED
        )
        whenever(credentialsRepository.loadCredentials()).thenReturn(null)
        whenever(authRepository.verifySavedCredentials()).thenThrow(
            IllegalStateException("boom")
        )
        val viewModel = ForecastSettingsViewModel(createUseCase())

        viewModel.verifyCredentials()
        runCurrent()

        assertFalse(viewModel.authChecking.value)
        assertEquals(
            "Verification failed (IllegalStateException)",
            viewModel.authConfirmation.value
        )
        assertNull(viewModel.authReturnCode.value)
    }

    @Test
    fun init_plaintextFallbackMode_exposesFallbackStorageMode() = runTest {
        whenever(credentialsRepository.credentialStorageMode()).thenReturn(
            ForecastCredentialStorageMode.PLAINTEXT_FALLBACK
        )
        whenever(credentialsRepository.loadCredentials()).thenReturn(null)
        val viewModel = ForecastSettingsViewModel(createUseCase())
        runCurrent()

        assertEquals(
            ForecastCredentialStorageMode.PLAINTEXT_FALLBACK,
            viewModel.credentialStorageMode.value
        )
    }

    private suspend fun createUseCase(): ForecastSettingsUseCase {
        whenever(preferencesRepository.overlayEnabledFlow).thenReturn(MutableStateFlow(false))
        whenever(preferencesRepository.opacityFlow).thenReturn(
            MutableStateFlow(FORECAST_OPACITY_DEFAULT)
        )
        whenever(preferencesRepository.windOverlayScaleFlow).thenReturn(
            MutableStateFlow(FORECAST_WIND_OVERLAY_SCALE_DEFAULT)
        )
        whenever(preferencesRepository.windDisplayModeFlow).thenReturn(
            MutableStateFlow(FORECAST_WIND_DISPLAY_MODE_DEFAULT)
        )
        whenever(preferencesRepository.selectedRegionFlow).thenReturn(
            MutableStateFlow(DEFAULT_FORECAST_REGION_CODE)
        )
        return ForecastSettingsUseCase(
            preferencesRepository = preferencesRepository,
            credentialsRepository = credentialsRepository,
            authRepository = authRepository,
            dispatcher = mainDispatcherRule.dispatcher
        )
    }
}
