package com.trust3.xcpro.adsb

import com.trust3.xcpro.common.units.UnitsFormatter
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.common.units.VerticalSpeedMs
import com.trust3.xcpro.common.units.VerticalSpeedUnit

internal object AdsbDetailsFormatter {
    private const val FPM_LABEL = "ft/min"

    fun formatVerticalRate(climbMps: Double?, unitsPreferences: UnitsPreferences): String {
        val climb = climbMps ?: return "--"
        val isFeetPerMinute = unitsPreferences.verticalSpeed == VerticalSpeedUnit.FEET_PER_MINUTE
        val decimals = if (isFeetPerMinute) 0 else 1
        val formatted = UnitsFormatter.verticalSpeed(
            verticalSpeed = VerticalSpeedMs(climb),
            preferences = unitsPreferences,
            decimals = decimals,
            showSign = true
        )
        if (!isFeetPerMinute) return formatted.text
        val signedValue = formatted.value.toInt()
        val sign = if (signedValue >= 0) "+" else ""
        return "$sign$signedValue $FPM_LABEL"
    }
}
