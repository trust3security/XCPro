package com.trust3.xcpro.map

import com.trust3.xcpro.external.ExternalFlightSettingsSnapshot
import com.trust3.xcpro.qnh.QnhSource
import com.trust3.xcpro.qnh.QnhValue

internal data class ExternalFlightSettingsCardProjection(
    val externalMacCreadyActive: Boolean = false,
    val externalQnhActive: Boolean = false,
    val bugsPercent: Int = 0,
    val bugsValid: Boolean = false,
    val ballastOverloadFactor: Double = Double.NaN,
    val ballastFactorValid: Boolean = false,
    val outsideAirTemperatureC: Double = Double.NaN,
    val outsideAirTemperatureValid: Boolean = false
)

internal fun createExternalFlightSettingsCardProjection(
    snapshot: ExternalFlightSettingsSnapshot,
    qnhValue: QnhValue
): ExternalFlightSettingsCardProjection {
    val bugsPercent = snapshot.bugsPercent?.coerceIn(0, 50)
    val ballastOverloadFactor = snapshot.ballastOverloadFactor
        ?.takeIf { it.isFinite() && it >= 1.0 }
    val outsideAirTemperatureC = snapshot.outsideAirTemperatureC
        ?.takeIf { it.isFinite() }

    return ExternalFlightSettingsCardProjection(
        externalMacCreadyActive = snapshot.macCreadyMps?.isFinite() == true,
        externalQnhActive = qnhValue.source == QnhSource.EXTERNAL,
        bugsPercent = bugsPercent ?: 0,
        bugsValid = bugsPercent != null,
        ballastOverloadFactor = ballastOverloadFactor ?: Double.NaN,
        ballastFactorValid = ballastOverloadFactor != null,
        outsideAirTemperatureC = outsideAirTemperatureC ?: Double.NaN,
        outsideAirTemperatureValid = outsideAirTemperatureC != null
    )
}
