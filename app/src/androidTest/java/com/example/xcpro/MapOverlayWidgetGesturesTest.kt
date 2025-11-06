package com.example.xcpro

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapUIWidgetManager
import com.example.xcpro.map.MapUIWidgets
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapOverlayWidgetGesturesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hamburgerTapAndLongPressTriggerCallbacks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mapState = MapScreenState(context, initialMapStyle = "default")
        val widgetManager = MapUIWidgetManager(mapState, mapState.sharedPrefs)

        val tapTriggered = mutableStateOf(false)
        val longPressTriggered = mutableStateOf(false)

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MapUIWidgets.SideHamburgerMenu(
                    widgetManager = widgetManager,
                    hamburgerOffset = Offset(16f, 200f),
                    screenWidthPx = 1080f,
                    screenHeightPx = 1920f,
                    onHamburgerTap = { tapTriggered.value = true },
                    onHamburgerLongPress = { longPressTriggered.value = true },
                    onOffsetChange = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Open navigation drawer")
            .performClick()
        composeRule.waitForIdle()
        assertTrue("Hamburger tap should invoke callback", tapTriggered.value)

        composeRule.onNodeWithContentDescription("Open navigation drawer")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertTrue("Hamburger long press should invoke callback", longPressTriggered.value)
    }
}
