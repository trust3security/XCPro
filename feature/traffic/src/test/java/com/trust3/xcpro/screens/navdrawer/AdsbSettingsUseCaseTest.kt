package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.trust3.xcpro.adsb.AdsbTrafficRepository
import com.trust3.xcpro.adsb.OpenSkyCredentialsRepository
import com.trust3.xcpro.adsb.OpenSkyTokenRepository
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.common.units.UnitsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AdsbSettingsUseCaseTest {

    private val repository: AdsbTrafficPreferencesRepository = mock()
    private val credentialsRepository: OpenSkyCredentialsRepository = mock()
    private val tokenRepository: OpenSkyTokenRepository = mock()
    private val adsbTrafficRepository: AdsbTrafficRepository = mock()
    private val unitsRepository: UnitsRepository = mock()

    @Test
    fun flowProjection_includesEmergencyAudioSettings() = runTest {
        whenever(repository.iconSizePxFlow).thenReturn(flowOf(140))
        whenever(repository.maxDistanceKmFlow).thenReturn(flowOf(22))
        whenever(repository.verticalAboveMetersFlow).thenReturn(flowOf(1200.0))
        whenever(repository.verticalBelowMetersFlow).thenReturn(flowOf(900.0))
        whenever(repository.emergencyFlashEnabledFlow).thenReturn(flowOf(true))
        whenever(repository.emergencyAudioEnabledFlow).thenReturn(flowOf(true))
        whenever(repository.emergencyAudioCooldownMsFlow).thenReturn(flowOf(60_000L))
        whenever(repository.emergencyAudioMasterEnabledFlow).thenReturn(flowOf(true))
        whenever(repository.emergencyAudioShadowModeFlow).thenReturn(flowOf(false))
        whenever(repository.emergencyAudioRollbackLatchedFlow).thenReturn(flowOf(false))
        whenever(repository.emergencyAudioRollbackReasonFlow).thenReturn(flowOf(null))
        whenever(unitsRepository.unitsFlow).thenReturn(flowOf(UnitsPreferences()))

        val useCase = AdsbSettingsUseCase(
            repository = repository,
            credentialsRepository = credentialsRepository,
            tokenRepository = tokenRepository,
            adsbTrafficRepository = adsbTrafficRepository,
            unitsRepository = unitsRepository
        )

        assertEquals(140, useCase.iconSizePxFlow.first())
        assertEquals(22, useCase.maxDistanceKmFlow.first())
        assertEquals(1200.0, useCase.verticalAboveMetersFlow.first(), 0.0)
        assertEquals(900.0, useCase.verticalBelowMetersFlow.first(), 0.0)
        assertTrue(useCase.emergencyFlashEnabledFlow.first())
        assertTrue(useCase.emergencyAudioEnabledFlow.first())
        assertEquals(60_000L, useCase.emergencyAudioCooldownMsFlow.first())
        assertTrue(useCase.emergencyAudioMasterEnabledFlow.first())
        assertEquals(false, useCase.emergencyAudioShadowModeFlow.first())
        assertEquals(false, useCase.emergencyAudioRollbackLatchedFlow.first())
        assertEquals(null, useCase.emergencyAudioRollbackReasonFlow.first())
    }

    @Test
    fun emergencyAudioSetters_delegateToPreferencesRepository() = runTest {
        whenever(repository.iconSizePxFlow).thenReturn(flowOf(124))
        whenever(repository.maxDistanceKmFlow).thenReturn(flowOf(10))
        whenever(repository.verticalAboveMetersFlow).thenReturn(flowOf(2000.0))
        whenever(repository.verticalBelowMetersFlow).thenReturn(flowOf(1000.0))
        whenever(repository.emergencyFlashEnabledFlow).thenReturn(flowOf(true))
        whenever(repository.emergencyAudioEnabledFlow).thenReturn(flowOf(false))
        whenever(repository.emergencyAudioCooldownMsFlow).thenReturn(flowOf(45_000L))
        whenever(repository.emergencyAudioMasterEnabledFlow).thenReturn(flowOf(true))
        whenever(repository.emergencyAudioShadowModeFlow).thenReturn(flowOf(false))
        whenever(repository.emergencyAudioRollbackLatchedFlow).thenReturn(flowOf(false))
        whenever(repository.emergencyAudioRollbackReasonFlow).thenReturn(flowOf(null))
        whenever(unitsRepository.unitsFlow).thenReturn(flowOf(UnitsPreferences()))
        val useCase = AdsbSettingsUseCase(
            repository = repository,
            credentialsRepository = credentialsRepository,
            tokenRepository = tokenRepository,
            adsbTrafficRepository = adsbTrafficRepository,
            unitsRepository = unitsRepository
        )

        useCase.setEmergencyFlashEnabled(false)
        useCase.setEmergencyAudioEnabled(true)
        useCase.setEmergencyAudioCooldownMs(90_000L)
        useCase.setEmergencyAudioMasterEnabled(false)
        useCase.setEmergencyAudioShadowMode(true)
        useCase.clearEmergencyAudioRollback()

        verify(repository).setEmergencyFlashEnabled(false)
        verify(repository).setEmergencyAudioEnabled(true)
        verify(repository).setEmergencyAudioCooldownMs(90_000L)
        verify(repository).setEmergencyAudioMasterEnabled(false)
        verify(repository).setEmergencyAudioShadowMode(true)
        verify(repository).clearEmergencyAudioRollback()
    }
}
