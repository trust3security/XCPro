package com.example.ui1

import com.trust3.xcpro.common.units.UnitsFormatter
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.common.units.VerticalSpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class VarioDialConfigSupportTest {

    @Test
    fun stripUnit_removesUnitSuffixAndTrimsWhitespace() {
        val formatted = UnitsFormatter.FormattedValue(
            value = 1.2,
            unitLabel = "m/s",
            text = "+1.2 m/s"
        )

        assertEquals("+1.2", stripUnit(formatted))
    }

    @Test
    fun buildVarioDialConfig_usesOneMeterPerSecondLabelsForMetric() {
        val config = buildVarioDialConfig(
            UnitsPreferences(verticalSpeed = VerticalSpeedUnit.METERS_PER_SECOND)
        )

        assertEquals(5f, config.maxValueSi, 0f)
        assertEquals(listOf("-5", "-4", "-3", "-2", "-1", "0", "1", "2", "3", "4", "5"), config.labelValues.map { it.text })
    }

    @Test
    fun buildVarioDialConfig_roundsKnotsLabelsToTwoKtSteps() {
        val config = buildVarioDialConfig(
            UnitsPreferences(verticalSpeed = VerticalSpeedUnit.KNOTS)
        )

        assertEquals(listOf("-10", "-8", "-6", "-4", "-2", "0", "2", "4", "6", "8", "10"), config.labelValues.map { it.text })
        assertEquals(-5f, config.labelValues.first().valueSi, 0f)
        assertEquals(5f, config.labelValues.last().valueSi, 0f)
    }
}
