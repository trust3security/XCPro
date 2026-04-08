package com.example.dfcards

import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.core.flight.RealTimeFlightData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CardDataFormatterTest {

    @Test
    fun gpsAlt_usesBaroWhenQnhCalibrated() {
        withLocale(Locale.US) {
            val data = RealTimeFlightData(
                isQNHCalibrated = true,
                currentPressureHPa = 1000.0,
                baroAltitude = 123.0,
                qnh = 1015.6
            )
            val result = CardDataFormatter.mapLiveDataToCard("gps_alt", data, UnitsPreferences())
            assertEquals("123 m", result.first)
            assertEquals("QNH 1016", result.second)
        }
    }

    @Test
    fun vario_invalidShowsStale() {
        withLocale(Locale.US) {
            val data = RealTimeFlightData(
                displayVario = 1.0,
                verticalSpeed = 0.0,
                varioValid = false
            )
            val result = CardDataFormatter.mapLiveDataToCard("vario", data, UnitsPreferences())
            assertEquals("+1.0 m/s", result.first)
            assertEquals("STALE", result.second)
        }
    }

    @Test
    fun thermalAvg_formatsPrimaryAndSecondary() {
        withLocale(Locale.US) {
            val data = RealTimeFlightData(
                thermalAverage = 1.2f,
                currentThermalValid = true,
                currentThermalLiftRate = 0.8
            )
            val result = CardDataFormatter.mapLiveDataToCard("thermal_avg", data, UnitsPreferences())
            assertEquals("+1.2 m/s", result.first)
            assertEquals("+0.8 m/s", result.second)
        }
    }

    @Test
    fun qnh_noBaroShowsNoBaro() {
        val data = RealTimeFlightData(
            currentPressureHPa = 0.0,
            qnh = 1013.25
        )
        val result = CardDataFormatter.mapLiveDataToCard("qnh", data, UnitsPreferences())
        assertEquals("-- hPa", result.first)
        assertEquals("NO BARO", result.second)
    }

    @Test
    fun track_staticWhenSlow() {
        val data = RealTimeFlightData(
            groundSpeed = 0.0
        )
        val result = CardDataFormatter.mapLiveDataToCard("track", data, UnitsPreferences())
        assertEquals("--deg", result.first)
        assertEquals("STATIC", result.second)
    }

    @Test
    fun localTime_placeholderWhenTimestampMissing() {
        val data = RealTimeFlightData(
            timestamp = 0L,
            lastUpdateTime = 0L
        )
        val result = CardDataFormatter.mapLiveDataToCard("local_time", data, UnitsPreferences())
        assertEquals("--:--", result.first)
        assertEquals("--", result.second)
    }

    @Test
    fun localTime_usesInjectedTimeFormatter() {
        val data = RealTimeFlightData(
            timestamp = 1234L,
            lastUpdateTime = 0L
        )
        val formatter = TestCardTimeFormatter(time = "12:34", seconds = "56")
        val result = CardDataFormatter.mapLiveDataToCard(
            cardId = "local_time",
            liveData = data,
            units = UnitsPreferences(),
            timeFormatter = formatter
        )
        assertEquals("12:34", result.first)
        assertEquals("56", result.second)
    }

    @Test
    fun nullLiveData_usesStringsNoDataAndPlaceholder() {
        val strings = TestCardStrings(noData = "NOPE", degUnit = "degx")
        val result = CardDataFormatter.mapLiveDataToCard(
            cardId = "wind_dir",
            liveData = null,
            units = UnitsPreferences(),
            strings = strings
        )
        assertEquals("-- degx", result.first)
        assertEquals("NOPE", result.second)
    }

    @Test
    fun unknownCard_usesStringsUnknown() {
        val strings = TestCardStrings(unknown = "???")
        val result = CardDataFormatter.mapLiveDataToCard(
            cardId = "not_a_card",
            liveData = RealTimeFlightData(),
            units = UnitsPreferences(),
            strings = strings
        )
        assertEquals("--", result.first)
        assertEquals("???", result.second)
    }

    @Test
    fun vario_blankSource_usesStringsTe() {
        val strings = TestCardStrings(te = "TEX")
        val data = RealTimeFlightData(
            displayVario = 1.0,
            varioValid = true,
            varioSource = ""
        )
        val result = CardDataFormatter.mapLiveDataToCard(
            cardId = "vario",
            liveData = data,
            units = UnitsPreferences(),
            strings = strings
        )
        assertEquals("+1.0 m/s", result.first)
        assertEquals("TEX", result.second)
    }

    @Test
    fun airspeed_labels_use_airspeed_source_instead_of_tas_valid() {
        val strings = TestCardStrings(est = "ESTX", gps = "GPSX")
        val windResult = CardDataFormatter.mapLiveDataToCard(
            cardId = "ias",
            liveData = RealTimeFlightData(indicatedAirspeed = 10.0, airspeedSource = "WIND", tasValid = false),
            units = UnitsPreferences(),
            strings = strings
        )
        assertEquals("ESTX", windResult.second)

        val gpsResult = CardDataFormatter.mapLiveDataToCard(
            cardId = "ias",
            liveData = RealTimeFlightData(indicatedAirspeed = 10.0, airspeedSource = "GPS", tasValid = true),
            units = UnitsPreferences(),
            strings = strings
        )
        assertEquals("GPSX", gpsResult.second)

        val sensorTasResult = CardDataFormatter.mapLiveDataToCard(
            cardId = "tas",
            liveData = RealTimeFlightData(trueAirspeed = 10.0, airspeedSource = "SENSOR", tasValid = false),
            units = UnitsPreferences(),
            strings = strings
        )
        assertEquals("ESTX", sensorTasResult.second)
    }

    @Test
    fun windSpeed_noWind_usesStringsNoWind() {
        val strings = TestCardStrings(noWind = "NOWIND")
        val result = CardDataFormatter.mapLiveDataToCard(
            cardId = "wind_spd",
            liveData = RealTimeFlightData(windQuality = 0, windSpeed = 0f),
            units = UnitsPreferences(),
            strings = strings
        )
        assertEquals("-- km/h", result.first)
        assertEquals("NOWIND", result.second)
    }

    @Test
    fun windDirection_usesStringsDegUnit() {
        val strings = TestCardStrings(degUnit = "degx")
        val result = CardDataFormatter.mapLiveDataToCard(
            cardId = "wind_dir",
            liveData = RealTimeFlightData(
                windQuality = 1,
                windSpeed = 2f,
                windDirection = 90f,
                windValid = true
            ),
            units = UnitsPreferences(),
            strings = strings
        )
        assertEquals("90 degx", result.first)
    }

    @Test
    fun windSpeed_invalidWindFlag_showsNoWindEvenWhenQualityAndSpeedPresent() {
        val strings = TestCardStrings(noWind = "NOWIND")
        val result = CardDataFormatter.mapLiveDataToCard(
            cardId = "wind_spd",
            liveData = RealTimeFlightData(
                windQuality = 5,
                windSpeed = 8f,
                windConfidence = 0.9,
                windValid = false
            ),
            units = UnitsPreferences(),
            strings = strings
        )
        assertEquals("NOWIND", result.second)
    }

    private inline fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    private class TestCardStrings(
        private val base: CardStrings = DefaultCardStrings(),
        override val noData: String = base.noData,
        override val unknown: String = base.unknown,
        override val noWind: String = base.noWind,
        override val degUnit: String = base.degUnit,
        override val est: String = base.est,
        override val gps: String = base.gps,
        override val te: String = base.te
    ) : CardStrings by base

    private class TestCardTimeFormatter(
        private val time: String,
        private val seconds: String
    ) : CardTimeFormatter {
        override fun formatLocalTime(epochMillis: Long): Pair<String, String> = time to seconds
    }
}
