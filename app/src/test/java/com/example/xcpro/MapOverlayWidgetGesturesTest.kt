package com.example.xcpro

import android.content.Context
import android.content.ComponentName
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.ui.widgets.MapUIWidgets
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mapState = MapScreenState()
        val widgetPrefs = context.getSharedPreferences("MapPrefs", Context.MODE_PRIVATE)
        val widgetManager = MapUIWidgetManager(mapState, widgetPrefs)

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
