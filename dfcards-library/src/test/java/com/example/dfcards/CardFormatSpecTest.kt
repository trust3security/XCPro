package com.example.dfcards

import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.DistanceM
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.common.units.UnitsFormatter
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.core.flight.RealTimeFlightData
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
            nettoAverage30s = -0.6,
            nettoAverage30sValid = true
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.NETTO_AVG30]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("-0.6 m/s", primary)
        assertEquals(strings.netto, secondary)
    }

    @Test
    fun nettoAvg30_requires_explicit_valid_flag() {
        val liveData = RealTimeFlightData(
            nettoAverage30s = -0.6,
            nettoAverage30sValid = false
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.NETTO_AVG30]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("-- m/s", primary)
        assertEquals(strings.noData, secondary)
    }

    @Test
    fun netto_uses_no_data_secondary_in_s100_mode_when_value_is_invalid() {
        val liveData = RealTimeFlightData(
            nettoValid = false,
            airspeedSource = "SENSOR",
            varioSource = "EXTERNAL"
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.NETTO]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("+0.0 m/s", primary)
        assertEquals(strings.noData, secondary)
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
            polarLdCurrentSpeed = 37f,
            polarLdCurrentSpeedValid = true
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
            polarBestLd = 44f,
            polarBestLdValid = true
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.BEST_LD]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("44:1", primary)
        assertEquals(strings.calc, secondary)
    }

    @Test
    fun mc_formats_live_label_when_external_override_is_active() {
        val liveData = RealTimeFlightData(
            macCready = 1.5,
            externalMacCreadyActive = true
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.MC]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("+1.5 m/s", primary)
        assertEquals(strings.live, secondary)
    }

    @Test
    fun bugs_formats_live_percent_when_valid() {
        val liveData = RealTimeFlightData(
            bugsPercent = 12,
            bugsValid = true
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.BUGS]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("12%", primary)
        assertEquals(strings.live, secondary)
    }

    @Test
    fun ballastFactor_formats_live_value_when_valid() {
        val liveData = RealTimeFlightData(
            ballastOverloadFactor = 1.2,
            ballastFactorValid = true
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.BALLAST_FACTOR]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("1.20x", primary)
        assertEquals(strings.live, secondary)
    }

    @Test
    fun oat_formats_live_temperature_when_valid() {
        val liveData = RealTimeFlightData(
            outsideAirTemperatureC = 23.1,
            outsideAirTemperatureValid = true
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.OAT]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("+23 C", primary)
        assertEquals(strings.live, secondary)
    }

    @Test
    fun ldCurr_requires_explicit_valid_flag() {
        val liveData = RealTimeFlightData(
            pilotCurrentLD = 35f,
            pilotCurrentLDValid = false
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.LD_CURR]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("--:1", primary)
        assertEquals(strings.noData, secondary)
    }

    @Test
    fun ldCurr_shows_thermal_when_held_during_circling() {
        val liveData = RealTimeFlightData(
            pilotCurrentLD = 35f,
            pilotCurrentLDValid = true,
            pilotCurrentLDSource = "HELD",
            isCircling = true
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.LD_CURR]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("35:1", primary)
        assertEquals(strings.thermal, secondary)
    }

    @Test
    fun ldCurr_shows_thermal_when_held_during_turning() {
        val liveData = RealTimeFlightData(
            pilotCurrentLD = 34f,
            pilotCurrentLDValid = true,
            pilotCurrentLDSource = "HELD",
            isTurning = true
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.LD_CURR]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("34:1", primary)
        assertEquals(strings.thermal, secondary)
    }

    @Test
    fun ldCurr_shows_thermal_when_invalid_during_thermal_timeout() {
        val liveData = RealTimeFlightData(
            pilotCurrentLDValid = false,
            isTurning = true
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.LD_CURR]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("--:1", primary)
        assertEquals(strings.thermal, secondary)
    }

    @Test
    fun ldCurr_formats_fused_pilot_metric_without_using_raw_fields() {
        val liveData = RealTimeFlightData(
            currentLD = 18f,
            currentLDValid = true,
            pilotCurrentLD = 35f,
            pilotCurrentLDValid = true,
            currentLDAir = 13f,
            currentLDAirValid = false
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.LD_CURR]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("35:1", primary)
        assertEquals(strings.live, secondary)
    }

    @Test
    fun ldVario_formats_live_value() {
        val liveData = RealTimeFlightData(
            currentLDAir = 13f,
            currentLDAirValid = true
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.LD_VARIO]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("13:1", primary)
        assertEquals(strings.live, secondary)
    }

    @Test
    fun ldVario_requires_explicit_valid_flag() {
        val liveData = RealTimeFlightData(
            currentLDAir = 13f,
            currentLDAirValid = false
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.LD_VARIO]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("--:1", primary)
        assertEquals(strings.noData, secondary)
    }

    @Test
    fun wptDist_formats_authoritative_distance_when_waypoint_is_valid() {
        val liveData = RealTimeFlightData(
            waypointDistanceMeters = 12_345.0,
            waypointValid = true
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.WPT_DIST]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals(UnitsFormatter.distance(DistanceM(12_345.0), units).text, primary)
        assertEquals(strings.live, secondary)
    }

    @Test
    fun wptBrg_uses_explicit_waypoint_validity() {
        val validLiveData = RealTimeFlightData(
            waypointBearingTrueDegrees = 87.4,
            waypointValid = true
        )
        val invalidLiveData = RealTimeFlightData(
            waypointBearingTrueDegrees = 87.4,
            waypointValid = false,
            waypointInvalidReason = "PRESTART"
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.WPT_BRG]
        assertNotNull(spec)

        assertEquals("87deg", spec!!.format(validLiveData, units, strings, formatter).first)
        assertEquals(strings.live, spec.format(validLiveData, units, strings, formatter).second)
        assertEquals("---deg", spec.format(invalidLiveData, units, strings, formatter).first)
        assertEquals(strings.prestart, spec.format(invalidLiveData, units, strings, formatter).second)
    }

    @Test
    fun wptEta_formats_explicit_eta_epoch_and_source() {
        val liveData = RealTimeFlightData(
            waypointEtaEpochMillis = 9_999L,
            waypointEtaValid = true,
            waypointEtaSource = "GROUND_SPEED"
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.WPT_ETA]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals(9_999L, formatter.lastEpoch)
        assertEquals("12:34", primary)
        assertEquals(strings.gps, secondary)
    }

    @Test
    fun wptEta_uses_explicit_static_invalid_reason() {
        val liveData = RealTimeFlightData(
            waypointEtaValid = false,
            waypointEtaInvalidReason = "STATIC"
        )
        val formatter = StubTimeFormatter()

        val spec = CardFormatSpecs.specs[KnownCardId.WPT_ETA]
        assertNotNull(spec)

        val (primary, secondary) = spec!!.format(liveData, units, strings, formatter)

        assertEquals("--:--", primary)
        assertEquals(strings.static, secondary)
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
    fun finalGld_distinguishes_degraded_valid_and_invalid_states() {
        val formatter = StubTimeFormatter()
        val spec = CardFormatSpecs.specs[KnownCardId.FINAL_GLD]
        assertNotNull(spec)

        val valid = spec!!.format(
            RealTimeFlightData(
                glideSolutionValid = true,
                requiredGlideRatio = 34.7
            ),
            units,
            strings,
            formatter
        )
        val degraded = spec.format(
            RealTimeFlightData(
                glideSolutionValid = true,
                glideDegraded = true,
                glideDegradedReason = "STILL_AIR_ASSUMED",
                requiredGlideRatio = 34.7
            ),
            units,
            strings,
            formatter
        )
        val invalid = spec.format(
            RealTimeFlightData(
                glideSolutionValid = false,
                glideInvalidReason = "PRESTART"
            ),
            units,
            strings,
            formatter
        )

        assertEquals("35:1", valid.first)
        assertEquals(strings.calc, valid.second)
        assertEquals("35:1", degraded.first)
        assertEquals(strings.noWind, degraded.second)
        assertEquals("--:1", invalid.first)
        assertEquals(strings.prestart, invalid.second)
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
    fun arrivalCards_append_degraded_reason_without_looking_invalid() {
        val liveData = RealTimeFlightData(
            glideSolutionValid = true,
            glideDegraded = true,
            glideDegradedReason = "STILL_AIR_ASSUMED",
            navAltitude = 1_200.0,
            arrivalHeightM = 120.0,
            requiredAltitudeM = 1_050.0,
            arrivalHeightMc0M = 165.0,
            macCready = 2.0
        )
        val formatter = StubTimeFormatter()

        val arrAlt = CardFormatSpecs.specs[KnownCardId.ARR_ALT]!!.format(liveData, units, strings, formatter)
        val reqAlt = CardFormatSpecs.specs[KnownCardId.REQ_ALT]!!.format(liveData, units, strings, formatter)
        val arrMc0 = CardFormatSpecs.specs[KnownCardId.ARR_MC0]!!.format(liveData, units, strings, formatter)

        assertEquals("+120 m", arrAlt.first)
        assertEquals("MC 2 NO WIND", arrAlt.second)
        assertEquals("1050 m", reqAlt.first)
        assertEquals("+150 m NO WIND", reqAlt.second)
        assertEquals("+165 m", arrMc0.first)
        assertEquals("MC0 NO WIND", arrMc0.second)
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

    @Test
    fun taskMetrics_format_authoritative_values_when_runtime_owner_marks_them_valid() {
        val liveData = RealTimeFlightData(
            taskSpeedMs = 25.0,
            taskSpeedValid = true,
            taskDistanceMeters = 12_345.0,
            taskDistanceValid = true,
            taskRemainingDistanceMeters = 23_456.0,
            taskRemainingDistanceValid = true,
            taskRemainingTimeMillis = 5_400_000L,
            taskRemainingTimeValid = true,
            taskRemainingTimeBasis = "ACHIEVED_TASK_SPEED",
            startAltitudeMeters = 1_234.0,
            startAltitudeValid = true
        )
        val formatter = StubTimeFormatter()

        val taskSpeed = CardFormatSpecs.specs[KnownCardId.TASK_SPD]!!.format(liveData, units, strings, formatter)
        val taskDistance = CardFormatSpecs.specs[KnownCardId.TASK_DIST]!!.format(liveData, units, strings, formatter)
        val taskRemainingDistance =
            CardFormatSpecs.specs[KnownCardId.TASK_REMAIN_DIST]!!.format(liveData, units, strings, formatter)
        val taskRemainingTime =
            CardFormatSpecs.specs[KnownCardId.TASK_REMAIN_TIME]!!.format(liveData, units, strings, formatter)
        val startAlt = CardFormatSpecs.specs[KnownCardId.START_ALT]!!.format(liveData, units, strings, formatter)

        assertEquals(UnitsFormatter.speed(SpeedMs(25.0), units).text, taskSpeed.first)
        assertEquals(strings.calc, taskSpeed.second)
        assertEquals(UnitsFormatter.distance(DistanceM(12_345.0), units).text, taskDistance.first)
        assertEquals(strings.live, taskDistance.second)
        assertEquals(UnitsFormatter.distance(DistanceM(23_456.0), units).text, taskRemainingDistance.first)
        assertEquals(strings.live, taskRemainingDistance.second)
        assertEquals("1:30", taskRemainingTime.first)
        assertEquals(strings.calc, taskRemainingTime.second)
        assertEquals(UnitsFormatter.altitude(AltitudeM(1_234.0), units).text, startAlt.first)
        assertEquals(strings.calc, startAlt.second)
    }

    @Test
    fun taskMetrics_use_upstream_invalid_reasons_without_local_heuristics() {
        val liveData = RealTimeFlightData(
            taskSpeedValid = false,
            taskSpeedInvalidReason = "PRESTART",
            taskRemainingTimeValid = false,
            taskRemainingTimeInvalidReason = "STATIC",
            startAltitudeValid = false,
            startAltitudeInvalidReason = "NO_START"
        )
        val formatter = StubTimeFormatter()

        val taskSpeed = CardFormatSpecs.specs[KnownCardId.TASK_SPD]!!.format(liveData, units, strings, formatter)
        val taskRemainingTime =
            CardFormatSpecs.specs[KnownCardId.TASK_REMAIN_TIME]!!.format(liveData, units, strings, formatter)
        val startAlt = CardFormatSpecs.specs[KnownCardId.START_ALT]!!.format(liveData, units, strings, formatter)

        assertEquals(placeholderFor(KnownCardId.TASK_SPD, units, strings), taskSpeed.first)
        assertEquals(strings.prestart, taskSpeed.second)
        assertEquals("--:--", taskRemainingTime.first)
        assertEquals(strings.static, taskRemainingTime.second)
        assertEquals(placeholderFor(KnownCardId.START_ALT, units, strings), startAlt.first)
        assertEquals(strings.noStart, startAlt.second)
    }

    private class StubTimeFormatter : CardTimeFormatter {
        var lastEpoch: Long? = null

        override fun formatLocalTime(epochMillis: Long): Pair<String, String> {
            lastEpoch = epochMillis
            return "12:34" to "56"
        }
    }
}

