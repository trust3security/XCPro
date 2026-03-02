package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class OgnRelativeAltitudeLabelFormatterTest {

    @Test
    fun formatDelta_returnsUnknownMarker_whenDeltaMissingOrInvalid() {
        assertEquals(
            OgnRelativeAltitudeLabelFormatter.UNKNOWN_DELTA_TEXT,
            OgnRelativeAltitudeLabelFormatter.formatDelta(
                deltaMeters = null,
                altitudeUnit = AltitudeUnit.METERS
            )
        )
        assertEquals(
            OgnRelativeAltitudeLabelFormatter.UNKNOWN_DELTA_TEXT,
            OgnRelativeAltitudeLabelFormatter.formatDelta(
                deltaMeters = Double.POSITIVE_INFINITY,
                altitudeUnit = AltitudeUnit.FEET
            )
        )
    }

    @Test
    fun formatDelta_formatsSignedMeters() {
        assertEquals(
            "+41 m",
            OgnRelativeAltitudeLabelFormatter.formatDelta(
                deltaMeters = 40.6,
                altitudeUnit = AltitudeUnit.METERS
            )
        )
        assertEquals(
            "-41 m",
            OgnRelativeAltitudeLabelFormatter.formatDelta(
                deltaMeters = -40.6,
                altitudeUnit = AltitudeUnit.METERS
            )
        )
        assertEquals(
            "0 m",
            OgnRelativeAltitudeLabelFormatter.formatDelta(
                deltaMeters = 0.0,
                altitudeUnit = AltitudeUnit.METERS
            )
        )
    }

    @Test
    fun formatDelta_usesRawSign_beforeRounding() {
        assertEquals(
            "+0 ft",
            OgnRelativeAltitudeLabelFormatter.formatDelta(
                deltaMeters = 0.03,
                altitudeUnit = AltitudeUnit.FEET
            )
        )
        assertEquals(
            "-0 ft",
            OgnRelativeAltitudeLabelFormatter.formatDelta(
                deltaMeters = -0.03,
                altitudeUnit = AltitudeUnit.FEET
            )
        )
    }
}

