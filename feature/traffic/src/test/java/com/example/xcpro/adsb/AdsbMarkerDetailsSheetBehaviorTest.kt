package com.example.xcpro.adsb

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.example.xcpro.adsb.metadata.domain.MetadataAvailability
import com.example.xcpro.adsb.metadata.domain.MetadataSyncState
import com.example.xcpro.common.units.UnitsPreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdsbMarkerDetailsSheetBehaviorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun detailsSheet_contentContainerIsScrollable() {
        composeTestRule.setContent {
            MaterialTheme {
                AdsbMarkerDetailsSheet(
                    target = sampleTarget(),
                    unitsPreferences = UnitsPreferences(),
                    onDismiss = {}
                )
            }
        }

        composeTestRule
            .onNodeWithTag(ADSB_DETAILS_SHEET_SCROLL_TAG, useUnmergedTree = true)
            .assert(hasScrollAction())
    }

    @Test
    fun detailsSheet_canScrollToManufacturerSection() {
        composeTestRule.setContent {
            MaterialTheme {
                AdsbMarkerDetailsSheet(
                    target = sampleTarget(),
                    unitsPreferences = UnitsPreferences(),
                    onDismiss = {}
                )
            }
        }

        composeTestRule
            .onNodeWithTag(ADSB_DETAILS_SHEET_SCROLL_TAG, useUnmergedTree = true)
            .performScrollToNode(hasText("Manufacturer"))

        composeTestRule.onNodeWithText("Manufacturer", useUnmergedTree = true)
            .assertExists()
    }

    private fun sampleTarget(): AdsbSelectedTargetDetails {
        val id = Icao24.from("abc123") ?: error("invalid test id")
        return AdsbSelectedTargetDetails(
            id = id,
            callsign = "TEST01",
            lat = -35.0,
            lon = 149.0,
            altitudeM = 1300.0,
            speedMps = 70.0,
            trackDeg = 180.0,
            climbMps = 0.5,
            ageSec = 2,
            isStale = false,
            distanceMeters = 1_500.0,
            bearingDegFromUser = 220.0,
            usesOwnshipReference = true,
            proximityTier = AdsbProximityTier.AMBER,
            proximityReason = AdsbProximityReason.APPROACH_CLOSING,
            isClosing = true,
            closingRateMps = 1.1,
            isEmergencyCollisionRisk = false,
            isEmergencyAudioEligible = false,
            emergencyAudioIneligibilityReason =
                AdsbEmergencyAudioIneligibilityReason.DISTANCE_OUTSIDE_EMERGENCY_RANGE,
            isCirclingEmergencyRedRule = false,
            positionSource = 0,
            category = 3,
            lastContactEpochSec = null,
            registration = "N123AB",
            typecode = "A320",
            model = "A320-214",
            manufacturerName = "Airbus",
            owner = "Owner",
            operator = "Operator",
            operatorCallsign = "OPR",
            icaoAircraftType = "L2J",
            metadataAvailability = MetadataAvailability.Missing,
            metadataSyncState = MetadataSyncState.Idle
        )
    }
}
