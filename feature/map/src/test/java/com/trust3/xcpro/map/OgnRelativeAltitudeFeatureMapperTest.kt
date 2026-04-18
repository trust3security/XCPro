package com.trust3.xcpro.map

import com.trust3.xcpro.common.units.AltitudeUnit
import com.trust3.xcpro.map.OgnAircraftIcon
import org.junit.Assert.assertEquals
import org.junit.Test

class OgnRelativeAltitudeFeatureMapperTest {

    @Test
    fun map_setsAboveIcon_andPlacesDeltaTopSecondaryBottom() {
        val mapping = OgnRelativeAltitudeFeatureMapper.map(
            input(
                icon = OgnAircraftIcon.Glider,
                targetAltitudeMeters = 1040.0,
                ownshipAltitudeMeters = 1000.0,
                distanceMeters = 1_200.0,
                secondaryLabelText = "AB1 1.2 km",
                speedText = "87 km/h"
            )
        )

        assertEquals(OgnRelativeAltitudeBand.ABOVE, mapping.band)
        assertEquals("icon_glider_above", mapping.iconStyleImageId)
        assertEquals("+40 m | 87 km/h", mapping.topLabel)
        assertEquals("AB1 1.2 km", mapping.bottomLabel)
    }

    @Test
    fun map_setsBelowIcon_andPlacesSecondaryTopDeltaBottom() {
        val mapping = OgnRelativeAltitudeFeatureMapper.map(
            input(
                icon = OgnAircraftIcon.Glider,
                targetAltitudeMeters = 960.0,
                ownshipAltitudeMeters = 1000.0,
                distanceMeters = 1_200.0,
                secondaryLabelText = "CD2 0.8 km",
                speedText = "46 kt"
            )
        )

        assertEquals(OgnRelativeAltitudeBand.BELOW, mapping.band)
        assertEquals("icon_glider_below", mapping.iconStyleImageId)
        assertEquals("CD2 0.8 km", mapping.topLabel)
        assertEquals("-40 m | 46 kt", mapping.bottomLabel)
    }

    @Test
    fun map_setsNearIcon_withInclusiveDeadband() {
        val mapping = OgnRelativeAltitudeFeatureMapper.map(
            input(
                icon = OgnAircraftIcon.Glider,
                targetAltitudeMeters = 1020.0,
                ownshipAltitudeMeters = 1000.0,
                distanceMeters = 1_200.0,
                secondaryLabelText = "EF3 0.7 km",
                speedText = "12 m/s"
            )
        )

        assertEquals(OgnRelativeAltitudeBand.NEAR, mapping.band)
        assertEquals("icon_glider_near", mapping.iconStyleImageId)
        assertEquals("+20 m | 12 m/s", mapping.topLabel)
        assertEquals("EF3 0.7 km", mapping.bottomLabel)
    }

    @Test
    fun map_usesUnknownDeltaText_whenAltitudeUnknown() {
        val mapping = OgnRelativeAltitudeFeatureMapper.map(
            input(
                icon = OgnAircraftIcon.Glider,
                targetAltitudeMeters = null,
                ownshipAltitudeMeters = 1000.0,
                distanceMeters = 600.0,
                secondaryLabelText = "GH4 0.6 km",
                speedText = "65 mph"
            )
        )

        assertEquals(OgnRelativeAltitudeBand.UNKNOWN, mapping.band)
        assertEquals("icon_glider_near", mapping.iconStyleImageId)
        assertEquals("${OgnRelativeAltitudeLabelFormatter.UNKNOWN_DELTA_TEXT} | 65 mph", mapping.topLabel)
        assertEquals("GH4 0.6 km", mapping.bottomLabel)
    }

    @Test
    fun map_keepsDefaultIcon_forNonGlider() {
        val mapping = OgnRelativeAltitudeFeatureMapper.map(
            input(
                icon = OgnAircraftIcon.Paraglider,
                targetAltitudeMeters = 1040.0,
                ownshipAltitudeMeters = 1000.0,
                distanceMeters = 600.0,
                secondaryLabelText = "IJ5 1.0 km"
            )
        )

        assertEquals("icon_default", mapping.iconStyleImageId)
    }

    @Test
    fun map_fallsBackToUnknownIdentifier_whenSecondaryLabelMissing() {
        val mapping = OgnRelativeAltitudeFeatureMapper.map(
            input(
                icon = OgnAircraftIcon.Glider,
                targetAltitudeMeters = 1040.0,
                ownshipAltitudeMeters = 1000.0,
                distanceMeters = 1_200.0,
                secondaryLabelText = "   "
            )
        )

        assertEquals(
            OgnIdentifierDistanceLabelMapper.UNKNOWN_IDENTIFIER,
            mapping.bottomLabel
        )
    }

    @Test
    fun map_keepsDeltaOnlyWhenSpeedTextMissing() {
        val mapping = OgnRelativeAltitudeFeatureMapper.map(
            input(
                icon = OgnAircraftIcon.Glider,
                targetAltitudeMeters = 1040.0,
                ownshipAltitudeMeters = 1000.0,
                distanceMeters = 1_200.0,
                secondaryLabelText = "AB1 1.2 km",
                speedText = null
            )
        )

        assertEquals("+40 m", mapping.topLabel)
        assertEquals("AB1 1.2 km", mapping.bottomLabel)
    }

    @Test
    fun map_usesCloseRedIcon_whenWithinOneKilometerAndThreeHundredFeet() {
        val mapping = OgnRelativeAltitudeFeatureMapper.map(
            input(
                icon = OgnAircraftIcon.Glider,
                targetAltitudeMeters = 1040.0,
                ownshipAltitudeMeters = 1000.0,
                distanceMeters = 800.0,
                secondaryLabelText = "AB1 0.8 km"
            )
        )

        assertEquals(OgnRelativeAltitudeBand.ABOVE, mapping.band)
        assertEquals("icon_glider_close_red", mapping.iconStyleImageId)
    }

    @Test
    fun map_keepsBandIcon_whenOutsideCloseRedDistance() {
        val mapping = OgnRelativeAltitudeFeatureMapper.map(
            input(
                icon = OgnAircraftIcon.Glider,
                targetAltitudeMeters = 1040.0,
                ownshipAltitudeMeters = 1000.0,
                distanceMeters = 1_200.0,
                secondaryLabelText = "AB1 1.2 km"
            )
        )

        assertEquals("icon_glider_above", mapping.iconStyleImageId)
    }

    private fun input(
        icon: OgnAircraftIcon,
        targetAltitudeMeters: Double?,
        ownshipAltitudeMeters: Double?,
        distanceMeters: Double?,
        secondaryLabelText: String,
        speedText: String? = null
    ): OgnRelativeAltitudeFeatureMapperInput = OgnRelativeAltitudeFeatureMapperInput(
        targetAltitudeMeters = targetAltitudeMeters,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        distanceMeters = distanceMeters,
        altitudeUnit = AltitudeUnit.METERS,
        icon = icon,
        defaultIconStyleImageId = "icon_default",
        gliderAboveIconStyleImageId = "icon_glider_above",
        gliderBelowIconStyleImageId = "icon_glider_below",
        gliderNearIconStyleImageId = "icon_glider_near",
        gliderCloseRedIconStyleImageId = "icon_glider_close_red",
        secondaryLabelText = secondaryLabelText,
        speedText = speedText
    )
}
