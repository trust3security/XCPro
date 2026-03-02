package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.ogn.OgnAircraftIcon
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
                secondaryLabelText = "AB1 1.2 km"
            )
        )

        assertEquals(OgnRelativeAltitudeBand.ABOVE, mapping.band)
        assertEquals("icon_glider_above", mapping.iconStyleImageId)
        assertEquals("+40 m", mapping.topLabel)
        assertEquals("AB1 1.2 km", mapping.bottomLabel)
    }

    @Test
    fun map_setsBelowIcon_andPlacesSecondaryTopDeltaBottom() {
        val mapping = OgnRelativeAltitudeFeatureMapper.map(
            input(
                icon = OgnAircraftIcon.Glider,
                targetAltitudeMeters = 960.0,
                ownshipAltitudeMeters = 1000.0,
                secondaryLabelText = "CD2 0.8 km"
            )
        )

        assertEquals(OgnRelativeAltitudeBand.BELOW, mapping.band)
        assertEquals("icon_glider_below", mapping.iconStyleImageId)
        assertEquals("CD2 0.8 km", mapping.topLabel)
        assertEquals("-40 m", mapping.bottomLabel)
    }

    @Test
    fun map_setsNearIcon_withInclusiveDeadband() {
        val mapping = OgnRelativeAltitudeFeatureMapper.map(
            input(
                icon = OgnAircraftIcon.Glider,
                targetAltitudeMeters = 1020.0,
                ownshipAltitudeMeters = 1000.0,
                secondaryLabelText = "EF3 0.7 km"
            )
        )

        assertEquals(OgnRelativeAltitudeBand.NEAR, mapping.band)
        assertEquals("icon_glider_near", mapping.iconStyleImageId)
        assertEquals("+20 m", mapping.topLabel)
        assertEquals("EF3 0.7 km", mapping.bottomLabel)
    }

    @Test
    fun map_usesUnknownDeltaText_whenAltitudeUnknown() {
        val mapping = OgnRelativeAltitudeFeatureMapper.map(
            input(
                icon = OgnAircraftIcon.Glider,
                targetAltitudeMeters = null,
                ownshipAltitudeMeters = 1000.0,
                secondaryLabelText = "GH4 0.6 km"
            )
        )

        assertEquals(OgnRelativeAltitudeBand.UNKNOWN, mapping.band)
        assertEquals("icon_glider_near", mapping.iconStyleImageId)
        assertEquals(OgnRelativeAltitudeLabelFormatter.UNKNOWN_DELTA_TEXT, mapping.topLabel)
        assertEquals("GH4 0.6 km", mapping.bottomLabel)
    }

    @Test
    fun map_keepsDefaultIcon_forNonGlider() {
        val mapping = OgnRelativeAltitudeFeatureMapper.map(
            input(
                icon = OgnAircraftIcon.Paraglider,
                targetAltitudeMeters = 1040.0,
                ownshipAltitudeMeters = 1000.0,
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
                secondaryLabelText = "   "
            )
        )

        assertEquals(
            OgnIdentifierDistanceLabelMapper.UNKNOWN_IDENTIFIER,
            mapping.bottomLabel
        )
    }

    private fun input(
        icon: OgnAircraftIcon,
        targetAltitudeMeters: Double?,
        ownshipAltitudeMeters: Double?,
        secondaryLabelText: String
    ): OgnRelativeAltitudeFeatureMapperInput = OgnRelativeAltitudeFeatureMapperInput(
        targetAltitudeMeters = targetAltitudeMeters,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        altitudeUnit = AltitudeUnit.METERS,
        icon = icon,
        defaultIconStyleImageId = "icon_default",
        gliderAboveIconStyleImageId = "icon_glider_above",
        gliderBelowIconStyleImageId = "icon_glider_below",
        gliderNearIconStyleImageId = "icon_glider_near",
        secondaryLabelText = secondaryLabelText
    )
}
