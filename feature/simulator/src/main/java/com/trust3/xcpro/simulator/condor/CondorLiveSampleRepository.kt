package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.bluetooth.NmeaLine
import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.common.units.UnitsConverter
import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.weather.wind.model.AirspeedSample
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class CondorLiveSampleRepository @Inject constructor(
    private val parser: CondorSentenceParser,
    private val clock: Clock
) {

    private val mutableGpsFlow = MutableStateFlow<GPSData?>(null)
    private val mutableAirspeedFlow = MutableStateFlow<AirspeedSample?>(null)

    private var lastGga: CondorSentence.Gga? = null
    private var lastRmc: CondorSentence.Rmc? = null

    val gpsFlow: StateFlow<GPSData?> = mutableGpsFlow.asStateFlow()
    val airspeedFlow: StateFlow<AirspeedSample?> = mutableAirspeedFlow.asStateFlow()

    fun onLines(lines: List<NmeaLine>) {
        lines.asSequence()
            .mapNotNull(parser::parse)
            .forEach(::applySentence)
    }

    fun clear() {
        lastGga = null
        lastRmc = null
        mutableGpsFlow.value = null
        mutableAirspeedFlow.value = null
    }

    private fun applySentence(sentence: CondorSentence) {
        when (sentence) {
            is CondorSentence.Gga -> {
                lastGga = sentence
                publishGps()
            }

            is CondorSentence.Rmc -> {
                lastRmc = sentence
                publishGps()
            }

            is CondorSentence.LxWp0 -> {
                val trueMs = UnitsConverter.kmhToMs(sentence.airspeedKph)
                if (!trueMs.isFinite() || trueMs <= 0.0) return
                mutableAirspeedFlow.value = AirspeedSample.tasOnly(
                    trueMs = trueMs,
                    clockMillis = sentence.receivedMonoMs,
                    timestampMillis = clock.nowWallMs()
                )
            }
        }
    }

    private fun publishGps() {
        val gga = lastGga ?: return
        val rmc = lastRmc?.takeIf { candidate ->
            val ageMs = (gga.receivedMonoMs - candidate.receivedMonoMs).let { delta ->
                if (delta < 0L) -delta else delta
            }
            ageMs <= MAX_RMC_AGE_MS
        }
        mutableGpsFlow.value = GPSData(
            position = GeoPoint(latitude = gga.latitude, longitude = gga.longitude),
            altitude = AltitudeM(gga.altitudeMeters),
            speed = SpeedMs(rmc?.speedKnots?.let(UnitsConverter::knotsToMs) ?: 0.0),
            bearing = rmc?.bearingDeg ?: 0.0,
            accuracy = 0f,
            timestamp = clock.nowWallMs(),
            monotonicTimestampMillis = maxOf(gga.receivedMonoMs, rmc?.receivedMonoMs ?: 0L)
        )
    }

    private companion object {
        private const val MAX_RMC_AGE_MS = 5_000L
    }
}
