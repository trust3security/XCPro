package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.bluetooth.NmeaLine
import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.common.units.UnitsConverter
import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.external.ExternalInstrumentFlightSnapshot
import com.trust3.xcpro.external.ExternalInstrumentReadPort
import com.trust3.xcpro.external.TimedExternalValue
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.weather.wind.data.ExternalWindWritePort
import com.trust3.xcpro.weather.wind.model.AirspeedSample
import com.trust3.xcpro.weather.wind.model.WindVector
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class CondorLiveSampleRepository @Inject constructor(
    private val parser: CondorSentenceParser,
    private val clock: Clock,
    private val externalWindWritePort: ExternalWindWritePort
) : ExternalInstrumentReadPort {

    private val mutableGpsFlow = MutableStateFlow<GPSData?>(null)
    private val mutableAirspeedFlow = MutableStateFlow<AirspeedSample?>(null)
    private val mutableExternalFlightSnapshot = MutableStateFlow(ExternalInstrumentFlightSnapshot())

    private var lastGga: CondorSentence.Gga? = null
    private var lastRmc: CondorSentence.Rmc? = null

    val gpsFlow: StateFlow<GPSData?> = mutableGpsFlow.asStateFlow()
    val airspeedFlow: StateFlow<AirspeedSample?> = mutableAirspeedFlow.asStateFlow()
    override val externalFlightSnapshot: StateFlow<ExternalInstrumentFlightSnapshot> =
        mutableExternalFlightSnapshot.asStateFlow()

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
        mutableExternalFlightSnapshot.value = ExternalInstrumentFlightSnapshot()
        externalWindWritePort.clearExternalWind()
    }

    fun onStreamStale() {
        externalWindWritePort.clearExternalWind()
    }

    private fun applySentence(sentence: CondorSentence) {
        when (sentence) {
            is CondorSentence.Gga -> {
                lastGga = sentence
                publishGps()
            }

            is CondorSentence.Rmc -> {
                lastRmc = sentence
            }

            is CondorSentence.LxWp0 -> {
                publishInstrumentSamples(sentence)
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

    private fun publishInstrumentSamples(sentence: CondorSentence.LxWp0) {
        val wallTime = clock.nowWallMs()
        publishAirspeed(sentence, wallTime)
        publishExternalInstrumentSnapshot(sentence)
        publishExternalWind(sentence, wallTime)
    }

    private fun publishAirspeed(sentence: CondorSentence.LxWp0, wallTime: Long) {
        val trueMs = UnitsConverter.kmhToMs(sentence.airspeedKph)
        if (!trueMs.isFinite() || trueMs <= 0.0) return
        mutableAirspeedFlow.value = AirspeedSample.tasOnly(
            trueMs = trueMs,
            clockMillis = sentence.receivedMonoMs,
            timestampMillis = wallTime
        )
    }

    private fun publishExternalInstrumentSnapshot(sentence: CondorSentence.LxWp0) {
        val current = mutableExternalFlightSnapshot.value
        mutableExternalFlightSnapshot.value = current.copy(
            pressureAltitudeM = sentence.pressureAltitudeM?.let {
                TimedExternalValue(value = it, receivedMonoMs = sentence.receivedMonoMs)
            } ?: current.pressureAltitudeM,
            totalEnergyVarioMps = sentence.totalEnergyVarioMps?.let {
                TimedExternalValue(value = it, receivedMonoMs = sentence.receivedMonoMs)
            } ?: current.totalEnergyVarioMps
        )
    }

    private fun publishExternalWind(sentence: CondorSentence.LxWp0, wallTime: Long) {
        val windDirectionFromDeg = sentence.windDirectionFromDeg ?: return
        val windSpeedMs = sentence.windSpeedMs ?: return
        externalWindWritePort.updateExternalWindVector(
            vector = WindVector.fromSpeedAndBearing(
                speed = windSpeedMs,
                directionFromRad = Math.toRadians(windDirectionFromDeg)
            ),
            timestampMillis = wallTime
        )
    }

    private companion object {
        private const val MAX_RMC_AGE_MS = 5_000L
    }
}
