package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.DistanceUnit
import com.example.xcpro.common.units.SpeedUnit
import com.example.xcpro.common.units.UnitsPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OgnOwnshipTargetBadgeRenderModelTest {

    @Test
    fun build_returnsNull_whenDisabled() {
        val model = OgnOwnshipTargetBadgeRenderModelBuilder.build(
            request(
                enabled = false,
                target = target(distanceMeters = 2_500.0, altitudeMeters = 1_500.0)
            )
        )

        assertNull(model)
    }

    @Test
    fun build_returnsModel_whenTargetWouldPreviouslyBeOnScreen() {
        val model = OgnOwnshipTargetBadgeRenderModelBuilder.build(
            request(
                enabled = true,
                target = target(distanceMeters = 2_500.0, altitudeMeters = 1_500.0)
            )
        )

        assertEquals("2.5 km\n+500 m | 126 km/h", model?.labelText)
        assertEquals(OgnOwnshipTargetBadgeRenderModelBuilder.ABOVE_OR_LEVEL_TEXT_COLOR_HEX, model?.textColorHex)
    }

    @Test
    fun build_formatsDistanceRelativeAltitudeAndSpeed_withNavyForAboveOrLevel() {
        val model = OgnOwnshipTargetBadgeRenderModelBuilder.build(
            request(
                enabled = true,
                target = target(distanceMeters = 2_500.0, altitudeMeters = 1_500.0),
                ownshipAltitudeMeters = 1_400.0,
                altitudeUnit = AltitudeUnit.FEET,
                unitsPreferences = UnitsPreferences(
                    distance = DistanceUnit.KILOMETERS,
                    speed = SpeedUnit.KNOTS
                )
            )
        )

        assertEquals("2.5 km\n+328 ft | 68 kt", model?.labelText)
        assertEquals(OgnOwnshipTargetBadgeRenderModelBuilder.ABOVE_OR_LEVEL_TEXT_COLOR_HEX, model?.textColorHex)
    }

    @Test
    fun build_usesRedWhenTargetIsBelowOwnship() {
        val model = OgnOwnshipTargetBadgeRenderModelBuilder.build(
            request(
                enabled = true,
                target = target(distanceMeters = 1_200.0, altitudeMeters = 900.0),
                ownshipAltitudeMeters = 1_000.0,
                altitudeUnit = AltitudeUnit.METERS
            )
        )

        assertEquals("1.2 km\n-100 m | 126 km/h", model?.labelText)
        assertEquals(OgnOwnshipTargetBadgeRenderModelBuilder.BELOW_TEXT_COLOR_HEX, model?.textColorHex)
    }

    @Test
    fun build_usesFallbackMarkers_whenDistanceAltitudeOrSpeedAreUnknown() {
        val model = OgnOwnshipTargetBadgeRenderModelBuilder.build(
            request(
                enabled = true,
                target = target(distanceMeters = null, altitudeMeters = null, groundSpeedMps = null),
                ownshipAltitudeMeters = 1_000.0,
                altitudeUnit = AltitudeUnit.METERS
            )
        )

        assertEquals("--\n-- | --", model?.labelText)
        assertEquals(OgnOwnshipTargetBadgeRenderModelBuilder.ABOVE_OR_LEVEL_TEXT_COLOR_HEX, model?.textColorHex)
    }

    private fun request(
        enabled: Boolean,
        target: OgnTrafficTarget?,
        ownshipAltitudeMeters: Double? = 1_000.0,
        altitudeUnit: AltitudeUnit = AltitudeUnit.METERS,
        unitsPreferences: UnitsPreferences = UnitsPreferences(distance = DistanceUnit.KILOMETERS)
    ): OgnOwnshipTargetBadgeRenderRequest = OgnOwnshipTargetBadgeRenderRequest(
        enabled = enabled,
        target = target,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        altitudeUnit = altitudeUnit,
        unitsPreferences = unitsPreferences
    )

    private fun target(
        distanceMeters: Double?,
        altitudeMeters: Double?,
        groundSpeedMps: Double? = 35.0
    ): OgnTrafficTarget = OgnTrafficTarget(
        id = "T1",
        callsign = "CALL",
        destination = "APRS",
        latitude = -35.0,
        longitude = 149.0,
        altitudeMeters = altitudeMeters,
        trackDegrees = 90.0,
        groundSpeedMps = groundSpeedMps,
        verticalSpeedMps = 0.0,
        deviceIdHex = "ABC123",
        signalDb = -12.0,
        displayLabel = "T1",
        identity = null,
        rawComment = null,
        rawLine = "raw",
        timestampMillis = 1_000L,
        lastSeenMillis = 1_000L,
        distanceMeters = distanceMeters
    )
}
