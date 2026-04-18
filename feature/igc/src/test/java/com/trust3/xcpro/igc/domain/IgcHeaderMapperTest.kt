package com.trust3.xcpro.igc.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcHeaderMapperTest {

    private val mapper = IgcHeaderMapper()
    private val formatter = IgcRecordFormatter()

    @Test
    fun map_emitsRequiredHeadersInCanonicalOrder() {
        val context = IgcHeaderContext(
            utcDate = LocalDate.of(2026, 3, 9),
            flightNumberOfDay = 3,
            profileMetadata = IgcProfileMetadata(
                pilotName = "Jane Pilot",
                crew2 = "NIL",
                gliderType = "Sailplane",
                gliderId = "VH-ABC"
            ),
            recorderMetadata = IgcRecorderMetadata(
                firmwareVersion = "1.2.3",
                hardwareVersion = "Pixel / Android 16",
                gpsReceiver = "NKN",
                pressureSensor = "ANDROID_BARO",
                securityStatus = "UNSIGNED",
                gpsAltitudeDatum = IgcGpsAltitudeDatum.GEO,
                pressureAltitudeDatum = IgcPressureAltitudeDatum.ISA
            )
        )

        val headers = mapper.map(context)
        val codes = headers.map { it.code }
        assertEquals(
            listOf(
                "DTE", "PLT", "CM2", "GTY", "GID", "DTM", "RFW",
                "RHW", "FTY", "GPS", "PRS", "FRS", "ALG", "ALP"
            ),
            codes
        )

        val formatted = headers.map(formatter::formatH)
        assertEquals("HFDTEDATE:090326,03", formatted[0])
        assertTrue(formatted.any { it == "HFALPALTPRESSURE:ISA" })
    }

    @Test
    fun map_appliesNilAndNknFallbacks() {
        val context = IgcHeaderContext(
            utcDate = LocalDate.of(2026, 3, 9),
            flightNumberOfDay = 1,
            profileMetadata = IgcProfileMetadata(
                pilotName = null,
                crew2 = null,
                gliderType = null,
                gliderId = null
            ),
            recorderMetadata = IgcRecorderMetadata(
                firmwareVersion = null,
                hardwareVersion = null,
                gpsReceiver = null,
                pressureSensor = null,
                securityStatus = "UNSIGNED",
                gpsAltitudeDatum = IgcGpsAltitudeDatum.NIL,
                pressureAltitudeDatum = IgcPressureAltitudeDatum.NIL
            )
        )

        val formatted = mapper.map(context).map(formatter::formatH)
        assertTrue(formatted.contains("HFPLTPILOTINCHARGE:NKN"))
        assertTrue(formatted.contains("HFCM2CREW2:NIL"))
        assertTrue(formatted.contains("HFGTYGLIDERTYPE:NKN"))
        assertTrue(formatted.contains("HFGIDGLIDERID:NKN"))
        assertTrue(formatted.contains("HFRFWFIRMWAREVERSION:NKN"))
        assertTrue(formatted.contains("HFALGALTGPS:NIL"))
        assertTrue(formatted.contains("HFALPALTPRESSURE:NIL"))
    }
}
