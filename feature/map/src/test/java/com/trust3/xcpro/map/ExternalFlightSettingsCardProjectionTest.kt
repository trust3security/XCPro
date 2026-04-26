package com.trust3.xcpro.map

import com.trust3.xcpro.external.ExternalFlightSettingsSnapshot
import com.trust3.xcpro.qnh.QnhConfidence
import com.trust3.xcpro.qnh.QnhSource
import com.trust3.xcpro.qnh.QnhValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalFlightSettingsCardProjectionTest {

    @Test
    fun creates_card_projection_from_external_settings_and_effective_qnh() {
        val projection = createExternalFlightSettingsCardProjection(
            snapshot = ExternalFlightSettingsSnapshot(
                macCreadyMps = 1.2,
                bugsPercent = 73,
                ballastOverloadFactor = 1.3,
                outsideAirTemperatureC = 18.5
            ),
            qnhValue = qnhValue(QnhSource.EXTERNAL)
        )

        assertEquals(true, projection.externalMacCreadyActive)
        assertEquals(true, projection.externalQnhActive)
        assertEquals(50, projection.bugsPercent)
        assertEquals(true, projection.bugsValid)
        assertEquals(1.3, projection.ballastOverloadFactor, 1e-6)
        assertEquals(true, projection.ballastFactorValid)
        assertEquals(18.5, projection.outsideAirTemperatureC, 1e-6)
        assertEquals(true, projection.outsideAirTemperatureValid)
    }

    @Test
    fun ignores_invalid_optional_external_settings() {
        val projection = createExternalFlightSettingsCardProjection(
            snapshot = ExternalFlightSettingsSnapshot(
                macCreadyMps = Double.NaN,
                ballastOverloadFactor = 0.9,
                outsideAirTemperatureC = Double.NaN
            ),
            qnhValue = qnhValue(QnhSource.MANUAL)
        )

        assertEquals(false, projection.externalMacCreadyActive)
        assertEquals(false, projection.externalQnhActive)
        assertEquals(false, projection.bugsValid)
        assertEquals(false, projection.ballastFactorValid)
        assertEquals(false, projection.outsideAirTemperatureValid)
    }

    private fun qnhValue(source: QnhSource): QnhValue =
        QnhValue(
            hpa = 1013.25,
            source = source,
            calibratedAtMillis = 1_000L,
            confidence = QnhConfidence.HIGH
        )
}
