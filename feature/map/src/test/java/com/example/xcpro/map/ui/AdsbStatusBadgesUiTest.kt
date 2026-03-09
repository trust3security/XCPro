package com.example.xcpro.map.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.xcpro.map.AdsbAuthMode
import com.example.xcpro.map.AdsbNetworkFailureKind
import com.example.xcpro.map.AdsbTrafficSnapshot
import com.example.xcpro.map.adsbConnectionStateActive
import com.example.xcpro.map.adsbConnectionStateBackingOff
import com.example.xcpro.map.adsbConnectionStateError
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import com.example.xcpro.map.AdsbConnectionState

@RunWith(RobolectricTestRunner::class)
class AdsbStatusBadgesUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun persistentStatusBadge_rendersForOfflineError() {
        composeTestRule.setContent {
            AdsbPersistentStatusBadge(
                visible = true,
                snapshot = snapshot(
                    connectionState = adsbConnectionStateError("Socket timeout"),
                    authMode = AdsbAuthMode.Authenticated,
                    lastNetworkFailureKind = AdsbNetworkFailureKind.TIMEOUT
                )
            )
        }

        composeTestRule.onNodeWithText("ADS-B Offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Network: Socket timeout").assertIsDisplayed()
    }

    @Test
    fun persistentStatusBadge_hiddenWhenNotVisible_only() {
        var badgeVisible by mutableStateOf(false)
        var badgeSnapshot by mutableStateOf(
            snapshot(
                connectionState = adsbConnectionStateError("Network unavailable"),
                authMode = AdsbAuthMode.Authenticated
            )
        )
        composeTestRule.setContent {
            AdsbPersistentStatusBadge(
                visible = badgeVisible,
                snapshot = badgeSnapshot
            )
        }

        composeTestRule.onAllNodesWithText("ADS-B Offline").assertCountEquals(0)

        composeTestRule.runOnIdle {
            badgeVisible = true
            badgeSnapshot = snapshot(
                connectionState = adsbConnectionStateActive(),
                authMode = AdsbAuthMode.Authenticated
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("ADS-B Active").assertIsDisplayed()
    }

    @Test
    fun issueFlashBadge_rendersForBackingOffState() {
        composeTestRule.setContent {
            AdsbIssueFlashBadge(
                visible = true,
                snapshot = snapshot(
                    connectionState = adsbConnectionStateBackingOff(retryAfterSec = 5),
                    authMode = AdsbAuthMode.Authenticated
                )
            )
        }

        composeTestRule.onNodeWithText("ADS-B ISSUE").assertIsDisplayed()
    }

    private fun snapshot(
        connectionState: AdsbConnectionState,
        authMode: AdsbAuthMode,
        lastError: String? = null,
        lastHttpStatus: Int? = null,
        lastNetworkFailureKind: AdsbNetworkFailureKind? = null
    ): AdsbTrafficSnapshot =
        AdsbTrafficSnapshot(
            targets = emptyList(),
            connectionState = connectionState,
            authMode = authMode,
            centerLat = null,
            centerLon = null,
            receiveRadiusKm = 10,
            fetchedCount = 0,
            withinRadiusCount = 0,
            withinVerticalCount = 0,
            filteredByVerticalCount = 0,
            cappedCount = 0,
            displayedCount = 0,
            lastHttpStatus = lastHttpStatus,
            remainingCredits = null,
            lastPollMonoMs = null,
            lastSuccessMonoMs = null,
            lastError = lastError,
            lastNetworkFailureKind = lastNetworkFailureKind
        )
}

