package com.example.xcpro.map.ui

import com.example.xcpro.adsb.AdsbAuthMode
import com.example.xcpro.adsb.AdsbConnectionState
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.DistanceUnit
import com.example.xcpro.common.units.SpeedUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.AdsbTrafficUiModel
import com.example.xcpro.map.Icao24
import com.example.xcpro.map.OgnTrafficTarget
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.ogn.OgnConnectionState
import com.example.xcpro.ogn.OgnDisplayUpdateMode
import com.example.xcpro.ogn.OgnTrafficSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapTrafficOverlayUiAdaptersTest {

    @Test
    fun buildTrafficOverlayRenderState_hidesOwnshipInputsInSpectatorMode() {
        val renderState = buildTrafficOverlayRenderState(
            traffic = sampleTrafficBinding(),
            locationForUi = sampleLocation(),
            unitsPreferences = sampleUnitsPreferences(),
            renderLocalOwnship = false
        )

        assertNull(renderState.ownshipCoordinate)
        assertNull(renderState.ownshipAltitudeMeters)
        assertEquals(1, renderState.ognTargets.size)
        assertEquals(1, renderState.adsbTargets.size)
    }

    @Test
    fun buildTrafficOverlayRenderState_keepsOwnshipInputsWhileFlying() {
        val renderState = buildTrafficOverlayRenderState(
            traffic = sampleTrafficBinding(),
            locationForUi = sampleLocation(),
            unitsPreferences = sampleUnitsPreferences(),
            renderLocalOwnship = true
        )

        val ownshipCoordinate = requireNotNull(renderState.ownshipCoordinate)
        assertEquals(-33.9, ownshipCoordinate.latitude, 0.0)
        assertEquals(151.2, ownshipCoordinate.longitude, 0.0)
        assertEquals(1_234.0, requireNotNull(renderState.ownshipAltitudeMeters), 0.0)
    }

    private fun sampleTrafficBinding(): MapTrafficUiBinding {
        return MapTrafficUiBinding(
            ognTargets = listOf(
                OgnTrafficTarget(
                    id = "OGN123",
                    callsign = "WATCH123",
                    destination = "",
                    latitude = -33.9,
                    longitude = 151.2,
                    altitudeMeters = 1_500.0,
                    trackDegrees = 180.0,
                    groundSpeedMps = 20.0,
                    verticalSpeedMps = 1.5,
                    deviceIdHex = "ABC123",
                    signalDb = null,
                    displayLabel = "WATCH123",
                    identity = null,
                    rawComment = null,
                    rawLine = "raw",
                    timestampMillis = 1_000L,
                    lastSeenMillis = 1_000L
                )
            ),
            ognSnapshot = OgnTrafficSnapshot(
                targets = emptyList(),
                connectionState = OgnConnectionState.DISCONNECTED,
                lastError = null,
                subscriptionCenterLat = null,
                subscriptionCenterLon = null,
                receiveRadiusKm = 0,
                ddbCacheAgeMs = null,
                reconnectBackoffMs = null,
                lastReconnectWallMs = null
            ),
            ognOverlayEnabled = true,
            ognIconSizePx = 48,
            ognDisplayUpdateMode = OgnDisplayUpdateMode.DEFAULT,
            ognThermalHotspots = emptyList(),
            showOgnSciaEnabled = false,
            ognTargetEnabled = false,
            ognTargetAircraftKey = null,
            ognResolvedTarget = null,
            showOgnThermalsEnabled = false,
            ognGliderTrailSegments = emptyList(),
            ownshipAltitudeMeters = 1_234.0,
            ognAltitudeUnit = AltitudeUnit.METERS,
            adsbTargets = listOf(
                AdsbTrafficUiModel(
                    id = Icao24("abcdef"),
                    callsign = "ADSB1",
                    lat = -33.8,
                    lon = 151.3,
                    altitudeM = 1_600.0,
                    speedMps = 25.0,
                    trackDeg = 190.0,
                    climbMps = 0.5,
                    ageSec = 2,
                    isStale = false,
                    distanceMeters = 500.0,
                    bearingDegFromUser = 45.0,
                    positionSource = null,
                    category = null,
                    lastContactEpochSec = null
                )
            ),
            adsbSnapshot = AdsbTrafficSnapshot(
                targets = emptyList(),
                connectionState = AdsbConnectionState.Disabled,
                authMode = AdsbAuthMode.Anonymous,
                centerLat = null,
                centerLon = null,
                receiveRadiusKm = 0,
                fetchedCount = 0,
                withinRadiusCount = 0,
                displayedCount = 0,
                lastHttpStatus = null,
                remainingCredits = null,
                lastPollMonoMs = null,
                lastSuccessMonoMs = null,
                lastError = null
            ),
            adsbOverlayEnabled = true,
            adsbIconSizePx = 48,
            adsbEmergencyFlashEnabled = false,
            adsbDefaultMediumUnknownIconEnabled = false,
            selectedOgnTarget = null,
            selectedOgnThermal = null,
            selectedAdsbTarget = null
        )
    }

    private fun sampleLocation(): MapLocationUiModel {
        return MapLocationUiModel(
            latitude = -33.9,
            longitude = 151.2,
            speedMs = 18.0,
            bearingDeg = 180.0,
            accuracyMeters = 4.0,
            timestampMs = 1_000L
        )
    }

    private fun sampleUnitsPreferences(): UnitsPreferences {
        return UnitsPreferences(
            altitude = AltitudeUnit.METERS,
            speed = SpeedUnit.METERS_PER_SECOND,
            distance = DistanceUnit.KILOMETERS
        )
    }
}
