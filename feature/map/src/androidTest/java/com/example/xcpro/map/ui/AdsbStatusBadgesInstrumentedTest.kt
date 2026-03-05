package com.example.xcpro.map.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.xcpro.adsb.AdsbAuthMode
import com.example.xcpro.adsb.AdsbConnectionState
import com.example.xcpro.adsb.AdsbNetworkFailureKind
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdsbStatusBadgesInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MapComposeTestActivity>()

    @Test
    fun persistentStatusBadge_rendersOfflineReason() {
        composeTestRule.setContent {
            AdsbPersistentStatusBadge(
                visible = true,
                snapshot = snapshot(
                    connectionState = AdsbConnectionState.Error("Socket timeout"),
                    authMode = AdsbAuthMode.Authenticated,
                    lastNetworkFailureKind = AdsbNetworkFailureKind.TIMEOUT
                )
            )
        }

        composeTestRule.onNodeWithTag(ADSB_PERSISTENT_STATUS_BADGE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("ADS-B Offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Network: Socket timeout").assertIsDisplayed()
    }

    @Test
    fun issueFlashBadge_rendersForBackingOff() {
        composeTestRule.setContent {
            AdsbIssueFlashBadge(
                visible = true,
                snapshot = snapshot(
                    connectionState = AdsbConnectionState.BackingOff(retryAfterSec = 5),
                    authMode = AdsbAuthMode.Authenticated
                )
            )
        }

        composeTestRule.onNodeWithTag(ADSB_ISSUE_FLASH_BADGE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("ADS-B ISSUE").assertIsDisplayed()
    }

    @Test
    fun persistentStatusBadge_rendersActiveWhenVisibleAndHealthy() {
        composeTestRule.setContent {
            AdsbPersistentStatusBadge(
                visible = true,
                snapshot = snapshot(
                    connectionState = AdsbConnectionState.Active,
                    authMode = AdsbAuthMode.Authenticated
                )
            )
        }

        composeTestRule.onNodeWithTag(ADSB_PERSISTENT_STATUS_BADGE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("ADS-B Active").assertIsDisplayed()
    }

    @Test
    fun offlineAtStart_showsPersistentOfflineAndIssueFlash() {
        val offlineSnapshot = snapshot(
            connectionState = AdsbConnectionState.Error("Network unavailable"),
            authMode = AdsbAuthMode.Authenticated,
            lastError = "Network unavailable"
        )
        setContentWithStatusBadges(currentSnapshotProvider = { offlineSnapshot })

        composeTestRule.onNodeWithTag(ADSB_PERSISTENT_STATUS_BADGE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ADSB_ISSUE_FLASH_BADGE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("ADS-B Offline").assertIsDisplayed()
    }

    @Test
    fun backoffAtStart_showsBackoffStatusAndIssueFlash() {
        val backoffSnapshot = snapshot(
            connectionState = AdsbConnectionState.BackingOff(retryAfterSec = 5),
            authMode = AdsbAuthMode.Authenticated
        )
        setContentWithStatusBadges(currentSnapshotProvider = { backoffSnapshot })

        composeTestRule.onNodeWithTag(ADSB_PERSISTENT_STATUS_BADGE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ADSB_ISSUE_FLASH_BADGE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("ADS-B Backoff").assertIsDisplayed()
    }

    @Test
    fun persistentStatusBadge_autoDismissesAfterRecoveryDwell() {
        composeTestRule.mainClock.autoAdvance = false
        var currentSnapshot by mutableStateOf(
            snapshot(
                connectionState = AdsbConnectionState.Error("Network unavailable"),
                authMode = AdsbAuthMode.Authenticated,
                lastError = "Network unavailable"
            )
        )

        setContentWithStatusBadges(currentSnapshotProvider = { currentSnapshot })

        composeTestRule.onNodeWithTag(ADSB_PERSISTENT_STATUS_BADGE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ADSB_ISSUE_FLASH_BADGE_TAG).assertIsDisplayed()

        composeTestRule.runOnIdle {
            currentSnapshot = snapshot(
                connectionState = AdsbConnectionState.Active,
                authMode = AdsbAuthMode.Authenticated
            )
        }
        composeTestRule.mainClock.advanceTimeBy(2_100L)
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag(ADSB_PERSISTENT_STATUS_BADGE_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(ADSB_ISSUE_FLASH_BADGE_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("ADS-B Active").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("ADS-B Offline").assertCountEquals(0)
    }

    @Test
    fun issueReturnDuringRecoveryDwell_keepsPersistentVisibleUntilHealthyDwellCompletes() {
        composeTestRule.mainClock.autoAdvance = false
        var currentSnapshot by mutableStateOf(
            snapshot(
                connectionState = AdsbConnectionState.Error("Network unavailable"),
                authMode = AdsbAuthMode.Authenticated,
                lastError = "Network unavailable"
            )
        )

        setContentWithStatusBadges(currentSnapshotProvider = { currentSnapshot })
        composeTestRule.onNodeWithTag(ADSB_PERSISTENT_STATUS_BADGE_TAG).assertIsDisplayed()

        composeTestRule.runOnIdle {
            currentSnapshot = snapshot(
                connectionState = AdsbConnectionState.Active,
                authMode = AdsbAuthMode.Authenticated
            )
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)

        composeTestRule.runOnIdle {
            currentSnapshot = snapshot(
                connectionState = AdsbConnectionState.Error("Network unavailable"),
                authMode = AdsbAuthMode.Authenticated,
                lastError = "Network unavailable"
            )
        }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ADSB_PERSISTENT_STATUS_BADGE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ADSB_ISSUE_FLASH_BADGE_TAG).assertIsDisplayed()

        composeTestRule.runOnIdle {
            currentSnapshot = snapshot(
                connectionState = AdsbConnectionState.Active,
                authMode = AdsbAuthMode.Authenticated
            )
        }
        composeTestRule.mainClock.advanceTimeBy(2_100L)
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag(ADSB_PERSISTENT_STATUS_BADGE_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(ADSB_ISSUE_FLASH_BADGE_TAG).assertCountEquals(0)
    }

    private fun setContentWithStatusBadges(
        currentSnapshotProvider: () -> AdsbTrafficSnapshot
    ) {
        composeTestRule.setContent {
            val currentSnapshot = currentSnapshotProvider()
            val showPersistentStatus = rememberPersistentIssueVisibility(
                enabled = true,
                issueActive = shouldSurfacePersistentAdsbStatus(currentSnapshot),
                healthy = isAdsbReadyForAutoDismiss(currentSnapshot),
                recoveryDwellMs = 2_000L
            )
            val showIssueFlash = rememberTimedVisibility(
                enabled = shouldFlashAdsbIssue(currentSnapshot),
                readyForAutoDismiss = true,
                autoDismissDelayMs = 2_000L
            )
            AdsbPersistentStatusBadge(
                visible = showPersistentStatus,
                snapshot = currentSnapshot
            )
            AdsbIssueFlashBadge(
                visible = showIssueFlash,
                snapshot = currentSnapshot
            )
        }
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
