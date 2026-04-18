package com.trust3.xcpro.screens.navdrawer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.trust3.xcpro.testing.MainDispatcherRule
import com.trust3.xcpro.thermalling.ThermallingModePreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThermallingSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_reflectsRepositoryUpdates() = runTest {
        val repository = ThermallingModePreferencesRepository(FakePreferencesDataStore())
        val viewModel = ThermallingSettingsViewModel(ThermallingSettingsUseCase(repository))
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        repository.setEnabled(true)
        repository.setSwitchToThermalMode(false)
        repository.setZoomOnlyFallbackWhenThermalHidden(false)
        repository.setEnterDelaySeconds(2)
        repository.setExitDelaySeconds(9)
        repository.setApplyZoomOnEnter(false)
        repository.setThermalZoomLevel(15.5f)
        repository.setRememberManualThermalZoomInSession(false)
        repository.setRestorePreviousModeOnExit(false)
        repository.setRestorePreviousZoomOnExit(false)
        runCurrent()

        val ui = viewModel.uiState.value
        assertTrue(ui.enabled)
        assertFalse(ui.switchToThermalMode)
        assertFalse(ui.zoomOnlyFallbackWhenThermalHidden)
        assertEquals(2, ui.enterDelaySeconds)
        assertEquals(9, ui.exitDelaySeconds)
        assertFalse(ui.applyZoomOnEnter)
        assertEquals(15.5f, ui.thermalZoomLevel)
        assertFalse(ui.rememberManualThermalZoomInSession)
        assertFalse(ui.restorePreviousModeOnExit)
        assertFalse(ui.restorePreviousZoomOnExit)
        collector.cancel()
    }

    @Test
    fun setters_writeThroughUseCaseIntoRepository() = runTest {
        val repository = ThermallingModePreferencesRepository(FakePreferencesDataStore())
        val viewModel = ThermallingSettingsViewModel(ThermallingSettingsUseCase(repository))

        viewModel.setEnabled(true)
        viewModel.setSwitchToThermalMode(false)
        viewModel.setZoomOnlyFallbackWhenThermalHidden(false)
        viewModel.setEnterDelaySeconds(4)
        viewModel.setExitDelaySeconds(7)
        viewModel.setApplyZoomOnEnter(false)
        viewModel.setThermalZoomLevel(12.8f)
        viewModel.setRememberManualThermalZoomInSession(false)
        viewModel.setRestorePreviousModeOnExit(false)
        viewModel.setRestorePreviousZoomOnExit(false)
        runCurrent()

        val settings = repository.settingsFlow.first()
        assertTrue(settings.enabled)
        assertFalse(settings.switchToThermalMode)
        assertFalse(settings.zoomOnlyFallbackWhenThermalHidden)
        assertEquals(4, settings.enterDelaySeconds)
        assertEquals(7, settings.exitDelaySeconds)
        assertFalse(settings.applyZoomOnEnter)
        assertEquals(12.8f, settings.thermalZoomLevel)
        assertFalse(settings.rememberManualThermalZoomInSession)
        assertFalse(settings.restorePreviousModeOnExit)
        assertFalse(settings.restorePreviousZoomOnExit)
    }

    private class FakePreferencesDataStore(
        initialPreferences: Preferences = emptyPreferences()
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initialPreferences)

        override val data: Flow<Preferences> = flow {
            emitAll(state)
        }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
