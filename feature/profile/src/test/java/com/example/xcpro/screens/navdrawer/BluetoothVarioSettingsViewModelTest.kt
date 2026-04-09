package com.example.xcpro.screens.navdrawer

import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BluetoothVarioSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_refreshes_and_exposes_ui_state() = runTest {
        val useCase = mock<BluetoothVarioSettingsUseCase>()
        whenever(useCase.uiState).thenReturn(
            MutableStateFlow(
                BluetoothVarioSettingsUiState(
                    statusText = "Connected",
                    connectEnabled = false,
                    disconnectEnabled = true
                )
            )
        )

        val viewModel = BluetoothVarioSettingsViewModel(useCase)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        verify(useCase).refresh()
        assertEquals("Connected", viewModel.uiState.value.statusText)
        assertEquals(false, viewModel.uiState.value.connectEnabled)
        assertEquals(true, viewModel.uiState.value.disconnectEnabled)
        collector.cancel()
    }

    @Test
    fun forwards_permission_refresh_selection_and_button_actions() = runTest {
        val useCase = mock<BluetoothVarioSettingsUseCase>()
        whenever(useCase.uiState).thenReturn(MutableStateFlow(BluetoothVarioSettingsUiState()))

        val viewModel = BluetoothVarioSettingsViewModel(useCase)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onPermissionResult(true)
        viewModel.refresh()
        viewModel.selectDevice("AA:BB")
        viewModel.connect()
        viewModel.disconnect()
        runCurrent()

        verify(useCase).onPermissionResult(true)
        verify(useCase, times(2)).refresh()
        verify(useCase).selectDevice("AA:BB")
        verify(useCase).connect()
        verify(useCase).disconnect()
        collector.cancel()
    }
}
