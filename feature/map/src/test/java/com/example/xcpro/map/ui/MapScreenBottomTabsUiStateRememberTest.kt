package com.example.xcpro.map.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.MapTasksUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapScreenBottomTabsUiStateRememberTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun generalSettingsOpenThenClose_restoresBottomTabsSheet() {
        val taskScreenManager = createManager()
        var hasTrafficDetailsOpen by mutableStateOf(false)
        var isGeneralSettingsVisible by mutableStateOf(false)
        lateinit var uiState: MapScreenBottomTabsUiState

        composeTestRule.setContent {
            uiState = rememberMapScreenBottomTabsUiState(
                taskScreenManager = taskScreenManager,
                hasTrafficDetailsOpen = hasTrafficDetailsOpen,
                isGeneralSettingsVisible = isGeneralSettingsVisible
            )
        }

        composeTestRule.runOnIdle {
            uiState.setBottomTabsSheetVisible(true)
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertTrue(uiState.isBottomTabsSheetVisible)
        }

        composeTestRule.runOnIdle {
            isGeneralSettingsVisible = true
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertFalse(uiState.isBottomTabsSheetVisible)
        }

        composeTestRule.runOnIdle {
            isGeneralSettingsVisible = false
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertTrue(uiState.isBottomTabsSheetVisible)
        }

        composeTestRule.runOnIdle {
            hasTrafficDetailsOpen = false
        }
    }

    @Test
    fun generalSettingsClose_doesNotRestoreWhileTaskPanelBlocksSheet() {
        val taskScreenManager = createManager()
        var isGeneralSettingsVisible by mutableStateOf(false)
        lateinit var uiState: MapScreenBottomTabsUiState

        composeTestRule.setContent {
            uiState = rememberMapScreenBottomTabsUiState(
                taskScreenManager = taskScreenManager,
                hasTrafficDetailsOpen = false,
                isGeneralSettingsVisible = isGeneralSettingsVisible
            )
        }

        composeTestRule.runOnIdle {
            uiState.setBottomTabsSheetVisible(true)
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            isGeneralSettingsVisible = true
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            taskScreenManager.showTaskPanel(MapTaskScreenManager.TaskPanelState.EXPANDED_PARTIAL)
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            isGeneralSettingsVisible = false
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertFalse(uiState.isBottomTabsSheetVisible)
        }
    }

    private fun createManager(): MapTaskScreenManager {
        val tasksUseCase: MapTasksUseCase = mock()
        whenever(tasksUseCase.currentWaypointCount()).thenReturn(1)

        return MapTaskScreenManager(
            mapState = MapScreenState(),
            tasksUseCase = tasksUseCase,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }
}
