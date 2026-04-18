package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.testing.MainDispatcherRule
import com.trust3.xcpro.ui.theme.ThemePreferencesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ColorsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_reflectsThemeOwnerUpdates() = runTest {
        val useCase = mock<ThemePreferencesUseCase>()
        val themeFlows = mutableMapOf<String, MutableStateFlow<String>>()
        val customColorFlows = mutableMapOf<Pair<String, String>, MutableStateFlow<String?>>()
        fun themeFlow(profileId: String) = themeFlows.getOrPut(profileId) { MutableStateFlow("default") }
        fun customFlow(profileId: String, themeId: String) = customColorFlows.getOrPut(
            profileId to themeId
        ) { MutableStateFlow(null) }

        whenever(useCase.observeThemeId(any())).thenAnswer { themeFlow(it.getArgument(0)) }
        whenever(useCase.observeCustomColorsJson(any(), any())).thenAnswer {
            customFlow(it.getArgument(0), it.getArgument(1))
        }

        val viewModel = ColorsViewModel(useCase)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.setProfileId("pilot-a")
        runCurrent()
        themeFlow("pilot-a").value = "sunset"
        customFlow("pilot-a", "sunset").value =
            """{"primaryColor":"#AA0000","secondaryColor":"#00AA00"}"""
        runCurrent()

        assertEquals("pilot-a", viewModel.uiState.value.profileId)
        assertEquals("sunset", viewModel.uiState.value.themeId)
        assertEquals(
            """{"primaryColor":"#AA0000","secondaryColor":"#00AA00"}""",
            viewModel.uiState.value.customColorsJson
        )
        collector.cancel()
    }

    @Test
    fun setters_writeThroughToThemePreferencesUseCase() = runTest {
        val useCase = mock<ThemePreferencesUseCase>()
        whenever(useCase.observeThemeId(any())).thenReturn(MutableStateFlow("default"))
        whenever(useCase.observeCustomColorsJson(any(), any())).thenReturn(MutableStateFlow(null))
        val viewModel = ColorsViewModel(useCase)

        viewModel.setProfileId("pilot-b")
        viewModel.setThemeId("ocean")
        viewModel.setCustomColorsJson("ocean", """{"primaryColor":"#001122"}""")
        runCurrent()

        verify(useCase).setThemeId("pilot-b", "ocean")
        verify(useCase).setCustomColorsJson(
            "pilot-b",
            "ocean",
            """{"primaryColor":"#001122"}"""
        )
    }
}
