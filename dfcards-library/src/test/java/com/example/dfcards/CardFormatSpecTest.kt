package com.example.dfcards

import com.example.xcpro.common.units.UnitsPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CardFormatSpecTest {

    private val units = UnitsPreferences()
    private val strings = DefaultCardStrings()

    @Test
    fun thermalAvg_formats_primary_and_secondary() {
        val liveData = RealTimeFlightData(
            thermalAverage = 2.34f,
            currentThermalValid = true,
            currentThermalLiftRate = 1.24,
            thermalAverageValid = true
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.THERMAL_AVG]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("+2.3 m/s", primary)
        assertEquals("+1.2 m/s", secondary)
    }

    @Test
    fun nettoAvg30_formats_value_and_label() {
        val liveData = RealTimeFlightData(
            nettoAverage30s = -0.6
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.NETTO_AVG30]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("-0.6 m/s", primary)
        assertEquals(strings.netto, secondary)
    }

    @Test
    fun localTime_uses_lastUpdateTime_when_present() {
        val liveData = RealTimeFlightData(
            timestamp = 1_111L,
            lastUpdateTime = 2_222L,
            qnh = 1013.25,
            currentPressureHPa = 1013.25
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.LOCAL_TIME]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals(2_222L, formatter.lastEpoch)
        assertEquals("12:34", primary)
        assertEquals("56", secondary)
    }

    @Test
    fun polarLd_formats_live_value() {
        val liveData = RealTimeFlightData(
            polarLdCurrentSpeed = 37f
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.POLAR_LD]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("37:1", primary)
        assertEquals(strings.live, secondary)
    }

    @Test
    fun bestLd_formats_calculated_value() {
        val liveData = RealTimeFlightData(
            polarBestLd = 44f
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.BEST_LD]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("44:1", primary)
        assertEquals(strings.calc, secondary)
    }

    @Test
    fun finalGld_formats_required_glide_ratio() {
        val liveData = RealTimeFlightData(
            glideSolutionValid = true,
            requiredGlideRatio = 34.7
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.FINAL_GLD]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("35:1", primary)
        assertEquals(strings.calc, secondary)
    }

    @Test
    fun arrivalCards_format_signed_altitude_outputs() {
        val liveData = RealTimeFlightData(
            glideSolutionValid = true,
            navAltitude = 1_200.0,
            arrivalHeightM = 120.0,
            requiredAltitudeM = 1_050.0,
            arrivalHeightMc0M = 165.0,
            macCready = 2.0
        )
        val formatter = StubTimeFormatter()

        val arrAlt = CardFormatSpecs.specs[KnownCardId.ARR_ALT]!!
        val reqAlt = CardFormatSpecs.specs[KnownCardId.REQ_ALT]!!
        val arrMc0 = CardFormatSpecs.specs[KnownCardId.ARR_MC0]!!

        assertEquals("+120 m", arrAlt.format(liveData, units, strings, formatter).first)
        assertEquals("1050 m", reqAlt.format(liveData, units, strings, formatter).first)
        assertEquals("+150 m", reqAlt.format(liveData, units, strings, formatter).second)
        assertEquals("+165 m", arrMc0.format(liveData, units, strings, formatter).first)
    }

    @Test
    fun finalGld_uses_invalid_reason_secondary_when_solution_is_invalid() {
        val liveData = RealTimeFlightData(
            glideSolutionValid = false,
            glideInvalidReason = "PRESTART"
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.FINAL_GLD]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("--:1", primary)
        assertEquals(strings.prestart, secondary)
    }

    private class StubTimeFormatter : CardTimeFormatter {
        var lastEpoch: Long? = null

        override fun formatLocalTime(epochMillis: Long): Pair<String, String> {
            lastEpoch = epochMillis
            return "12:34" to "56"
        }
    }
}
