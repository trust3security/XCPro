package com.trust3.xcpro.ui.theme

import com.trust3.xcpro.testing.MainDispatcherRule
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
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_tracksProfileThemeAndCustomColorReads() = runTest {
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

        val viewModel = ThemeViewModel(useCase)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.setProfileId("pilot-theme")
        runCurrent()
        themeFlow("pilot-theme").value = "forest"
        customFlow("pilot-theme", "forest").value =
            """{"primaryColor":"#224400","secondaryColor":"#88AA00"}"""
        runCurrent()

        assertEquals("pilot-theme", viewModel.uiState.value.profileId)
        assertEquals("forest", viewModel.uiState.value.themeId)
        assertEquals(
            """{"primaryColor":"#224400","secondaryColor":"#88AA00"}""",
            viewModel.uiState.value.customColorsJson
        )
        collector.cancel()
    }
}
