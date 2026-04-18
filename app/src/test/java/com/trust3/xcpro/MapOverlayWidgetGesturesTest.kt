package com.trust3.xcpro

import android.content.Context
import android.content.ComponentName
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.trust3.xcpro.map.MapScreenState
import com.trust3.xcpro.map.ui.widgets.MapUIWidgetManager
import com.trust3.xcpro.map.ui.widgets.MapUIWidgets
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapOverlayWidgetGesturesTest {

    private val composeRule = createComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(ActivityRegistrationRule())
        .around(composeRule)

    @Test
    fun hamburgerTapAndLongPressTriggerCallbacks() {
        val mapState = MapScreenState()
        val widgetManager = MapUIWidgetManager(mapState)

        val tapTriggered = mutableStateOf(false)
        val longPressTriggered = mutableStateOf(false)

        composeRule.setContent {
            MapUIWidgets.SideHamburgerMenu(
                widgetManager = widgetManager,
                hamburgerOffset = Offset(16f, 200f),
                screenWidthPx = 1080f,
                screenHeightPx = 1920f,
                onHamburgerTap = { tapTriggered.value = true },
                onHamburgerLongPress = { longPressTriggered.value = true },
                onOffsetChange = {},
                isEditMode = false,
                modifier = Modifier.testTag("side_hamburger")
            )
        }

        composeRule.onNodeWithTag("side_hamburger")
            .performClick()
        assertTrue("Hamburger tap should invoke callback", tapTriggered.value)

        composeRule.onNodeWithTag("side_hamburger")
            .performTouchInput { longClick() }
        assertTrue("Hamburger long press should invoke callback", longPressTriggered.value)
    }

    @Test
    fun settingsShortcutTapTriggersCallback() {
        val mapState = MapScreenState()
        val widgetManager = MapUIWidgetManager(mapState)
        val tapTriggered = mutableStateOf(false)

        composeRule.setContent {
            MapUIWidgets.SettingsShortcut(
                widgetManager = widgetManager,
                settingsOffset = Offset(16f, 260f),
                screenWidthPx = 1080f,
                screenHeightPx = 1920f,
                onSettingsTap = { tapTriggered.value = true },
                onOffsetChange = {},
                isEditMode = false,
                modifier = Modifier.testTag("settings_shortcut")
            )
        }

        composeRule.onNodeWithTag("settings_shortcut")
            .performClick()

        assertTrue("Settings shortcut tap should invoke callback", tapTriggered.value)
    }

    @Test
    fun hamburgerCornerDragInEditModeTriggersResize() {
        val mapState = MapScreenState()
        val widgetManager = MapUIWidgetManager(mapState)
        val defaultSize = 90f
        val resizedSize = mutableStateOf(defaultSize)

        composeRule.setContent {
            MapUIWidgets.SideHamburgerMenu(
                widgetManager = widgetManager,
                hamburgerOffset = Offset(16f, 200f),
                screenWidthPx = 1080f,
                screenHeightPx = 1920f,
                sizePx = defaultSize,
                onHamburgerTap = {},
                onHamburgerLongPress = {},
                onOffsetChange = {},
                onSizeChange = { resizedSize.value = it },
                isEditMode = true,
                modifier = Modifier.testTag("side_hamburger_resize")
            )
        }

        composeRule.onNodeWithContentDescription("Hamburger resize handle")
            .performTouchInput {
                down(center)
                advanceEventTime(120L)
                moveBy(Offset(52f, 52f))
                advanceEventTime(120L)
                up()
            }

        assertTrue(
            "Hamburger corner drag should increase size in edit mode",
            resizedSize.value > defaultSize
        )
    }

    @Test
    fun settingsCornerDragInEditModeTriggersResize() {
        val mapState = MapScreenState()
        val widgetManager = MapUIWidgetManager(mapState)
        val defaultSize = 56f
        val resizedSize = mutableStateOf(defaultSize)

        composeRule.setContent {
            MapUIWidgets.SettingsShortcut(
                widgetManager = widgetManager,
                settingsOffset = Offset(16f, 260f),
                screenWidthPx = 1080f,
                screenHeightPx = 1920f,
                sizePx = defaultSize,
                onSettingsTap = {},
                onOffsetChange = {},
                onSizeChange = { resizedSize.value = it },
                isEditMode = true,
                modifier = Modifier.testTag("settings_shortcut_resize")
            )
        }

        composeRule.onNodeWithContentDescription("Settings resize handle")
            .performTouchInput {
                down(center)
                advanceEventTime(120L)
                moveBy(Offset(44f, 44f))
                advanceEventTime(120L)
                up()
            }

        assertTrue(
            "Settings corner drag should increase size in edit mode",
            resizedSize.value > defaultSize
        )
    }
}

private class ActivityRegistrationRule : ExternalResource() {
    override fun before() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowPackageManager = Shadows.shadowOf(context.packageManager)
        val component = ComponentName(context, androidx.activity.ComponentActivity::class.java)
        val activityInfo = shadowPackageManager.addActivityIfNotPresent(component)
        activityInfo.exported = true
        activityInfo.name = androidx.activity.ComponentActivity::class.java.name
        activityInfo.packageName = context.packageName
        shadowPackageManager.addOrUpdateActivity(activityInfo)
    }
}
