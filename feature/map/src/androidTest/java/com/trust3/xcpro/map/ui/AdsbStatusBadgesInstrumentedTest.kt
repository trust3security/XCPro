package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trust3.xcpro.map.AdsbAuthMode
import com.trust3.xcpro.map.AdsbNetworkFailureKind
import com.trust3.xcpro.map.AdsbTrafficSnapshot
import com.trust3.xcpro.map.adsbConnectionStateActive
import com.trust3.xcpro.map.adsbConnectionStateBackingOff
import com.trust3.xcpro.map.adsbConnectionStateError
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import com.trust3.xcpro.map.AdsbConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Ignore("Compose hierarchy is unavailable in current module-level connected test harness; behavior is covered by Robolectric tests.")
@RunWith(AndroidJUnit4::class)
class AdsbStatusBadgesInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun persistentStatusBadge_rendersOfflineReason() {
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
                    connectionState = adsbConnectionStateBackingOff(retryAfterSec = 5),
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
                    connectionState = adsbConnectionStateActive(),
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
            connectionState = adsbConnectionStateError("Network unavailable"),
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
            connectionState = adsbConnectionStateBackingOff(retryAfterSec = 5),
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
                connectionState = adsbConnectionStateError("Network unavailable"),
                authMode = AdsbAuthMode.Authenticated,
                lastError = "Network unavailable"
            )
        )

        setContentWithStatusBadges(currentSnapshotProvider = { currentSnapshot })

        composeTestRule.onNodeWithTag(ADSB_PERSISTENT_STATUS_BADGE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ADSB_ISSUE_FLASH_BADGE_TAG).assertIsDisplayed()

        composeTestRule.runOnIdle {
            currentSnapshot = snapshot(
                connectionState = adsbConnectionStateActive(),
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
                connectionState = adsbConnectionStateError("Network unavailable"),
                authMode = AdsbAuthMode.Authenticated,
                lastError = "Network unavailable"
            )
        )

        setContentWithStatusBadges(currentSnapshotProvider = { currentSnapshot })
        composeTestRule.onNodeWithTag(ADSB_PERSISTENT_STATUS_BADGE_TAG).assertIsDisplayed()

        composeTestRule.runOnIdle {
            currentSnapshot = snapshot(
                connectionState = adsbConnectionStateActive(),
                authMode = AdsbAuthMode.Authenticated
            )
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)

        composeTestRule.runOnIdle {
            currentSnapshot = snapshot(
                connectionState = adsbConnectionStateError("Network unavailable"),
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
                connectionState = adsbConnectionStateActive(),
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
            val showPersistentStatus = rememberPersistentIssueVisibilityForTest(
                enabled = true,
                issueActive = shouldSurfacePersistentAdsbStatus(currentSnapshot),
                healthy = isAdsbReadyForAutoDismiss(currentSnapshot),
                recoveryDwellMs = 2_000L
            )
            val showIssueFlash = rememberTimedVisibilityForTest(
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

@Composable
private fun rememberTimedVisibilityForTest(
    enabled: Boolean,
    readyForAutoDismiss: Boolean,
    autoDismissDelayMs: Long
): Boolean {
    var visible by remember(enabled) { mutableStateOf(enabled) }
    LaunchedEffect(enabled, readyForAutoDismiss, autoDismissDelayMs) {
        if (!enabled) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        if (!readyForAutoDismiss) return@LaunchedEffect
        delay(autoDismissDelayMs)
        if (isActive) {
            visible = false
        }
    }
    return visible
}

@Composable
private fun rememberPersistentIssueVisibilityForTest(
    enabled: Boolean,
    issueActive: Boolean,
    healthy: Boolean,
    recoveryDwellMs: Long
): Boolean {
    var state by remember(enabled) {
        mutableStateOf(
            PersistentIssueVisibilityState(
                visible = enabled && issueActive,
                healthySinceMonoMs = null
            )
        )
    }
    LaunchedEffect(enabled, issueActive, healthy, recoveryDwellMs) {
        fun updateState(nowMonoMs: Long) {
            state = reducePersistentIssueVisibility(
                previous = state,
                enabled = enabled,
                issueActive = issueActive,
                healthy = healthy,
                recoveryDwellMs = recoveryDwellMs,
                nowMonoMs = nowMonoMs
            )
        }

        updateState(withFrameNanos { it / 1_000_000L })
        if (!state.visible || issueActive || !healthy) {
            return@LaunchedEffect
        }

        while (isActive && state.visible && !issueActive && healthy) {
            updateState(withFrameNanos { it / 1_000_000L })
        }
    }
    return state.visible
}

