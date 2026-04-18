package com.trust3.xcpro.navdrawer

import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DrawerMenuSectionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsSection_generalItemUsesCallbackWhenProvided() {
        val navController: NavHostController = mock()
        val drawerState: DrawerState = mock()
        val callbackInvoked = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

        composeTestRule.setContent {
            MaterialTheme {
                SettingsSection(
                    isExpanded = true,
                    onToggle = {},
                    navController = navController,
                    drawerState = drawerState,
                    scope = scope,
                    onOpenGeneralSettings = {
                        callbackInvoked.set(true)
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("General").performClick()
        composeTestRule.waitForIdle()

        assertTrue("General callback should be invoked", callbackInvoked.get())
        runTest {
            verify(drawerState).close()
        }
        verifyNoInteractions(navController)
        scope.cancel()
    }
}
