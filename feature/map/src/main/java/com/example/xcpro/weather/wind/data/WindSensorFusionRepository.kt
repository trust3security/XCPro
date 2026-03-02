package com.example.xcpro.weather.wind.data

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.di.LiveSource
import com.example.xcpro.di.ReplaySource
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.CirclingDetector
import com.example.xcpro.core.time.Clock
import com.example.xcpro.weather.wind.domain.CirclingWind
import com.example.xcpro.weather.wind.domain.CirclingWindSample
import com.example.xcpro.weather.wind.domain.WindCandidate
import com.example.xcpro.weather.wind.domain.WindSelectionUseCase
import com.example.xcpro.weather.wind.domain.WindStore
import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.GLoadSample
import com.example.xcpro.weather.wind.model.GpsSample
import com.example.xcpro.weather.wind.model.HeadingSample
import com.example.xcpro.weather.wind.model.PressureSample
import com.example.xcpro.weather.wind.model.WindOverride
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.weather.wind.model.WindVector
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Singleton
class WindSensorFusionRepository @Inject constructor(
    @LiveSource private val liveInputs: WindSensorInputs,
    @ReplaySource private val replayInputs: WindSensorInputs,
    private val flightDataRepository: FlightDataRepository,
    private val flightStateSource: FlightStateSource,
    private val windOverrideSource: WindOverrideSource,
    private val windSelectionUseCase: WindSelectionUseCase,
    private val clock: Clock,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) {

    private data class WindFusionInput(
        val gps: GpsSample?,
        val pressure: PressureSample?,
        val airspeed: AirspeedSample?,
        val heading: HeadingSample?,
        val gLoad: GLoadSample?,
        val manualWind: WindOverride?,
        val externalWind: WindOverride?
    )

    private data class OverrideRevision(
        val manual: WindOverride?,
        val external: WindOverride?
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val circlingDetector = CirclingDetector()
    private val circlingWind = CirclingWind()
    private val windStore = WindStore()

    private val _windState = MutableStateFlow(WindState())
    val windState: StateFlow<WindState> = _windState.asStateFlow()

    private var lastCirclingClockMillis = Long.MIN_VALUE
    private var lastGpsClockMillis = Long.MIN_VALUE
    private var lastUpdatedClockMillis = Long.MIN_VALUE
    private var lastProcessedOverrideRevision = OverrideRevision(null, null)
    private var lastActiveSource: FlightDataRepository.Source? = null
    private var pendingResetJob: Job? = null
    private var windHoldUntilElapsedMs: Long = 0L

    init {
        scope.launch {
            flightDataRepository.activeSource
                .onEach { source -> handleSourceSwitch(source) }
                .flatMapLatest { source ->
                    val inputs = if (source == FlightDataRepository.Source.REPLAY) {
                        replayInputs
                    } else {
                        liveInputs
                    }
                    val sensorInputs = combine(
                        inputs.gps,
                        inputs.pressure,
                        inputs.airspeed,
                        inputs.heading,
                        inputs.gLoad
                    ) { gps, pressure, airspeed, heading, gLoad ->
                        WindFusionInput(
                            gps = gps,
                            pressure = pressure,
                            airspeed = airspeed,
                            heading = heading,
                            gLoad = gLoad,
                            manualWind = null,
                            externalWind = null
                        )
                    }
                    combine(
                        sensorInputs,
                        windOverrideSource.manualWind,
                        windOverrideSource.externalWind
                    ) { input, manual, external ->
                        input.copy(
                            manualWind = manual,
                            externalWind = external
                        )
                    }
                }
                .collect { input ->
                    if (input.gps == null) {
                        handleNoData()
                    } else {
                        processSample(input)
                    }
                }
        }
    }

    private fun resetForSourceSwitch() {
        circlingDetector.reset()
        circlingWind.reset()
        windStore.reset()
        lastCirclingClockMillis = Long.MIN_VALUE
        lastGpsClockMillis = Long.MIN_VALUE
        lastUpdatedClockMillis = Long.MIN_VALUE
        lastProcessedOverrideRevision = OverrideRevision(null, null)
        _windState.value = WindState()
        windHoldUntilElapsedMs = 0L
        pendingResetJob?.cancel()
        pendingResetJob = null
    }

    private fun handleNoData() {
        if (isHoldActive()) return
        resetForSourceSwitch()
    }

    private fun handleSourceSwitch(source: FlightDataRepository.Source) {
        pendingResetJob?.cancel()
        pendingResetJob = null
        val previous = lastActiveSource
        lastActiveSource = source
        if (previous == FlightDataRepository.Source.REPLAY &&
            source == FlightDataRepository.Source.LIVE
        ) {
            val holdMs = REPLAY_WIND_HOLD_MS
            windHoldUntilElapsedMs = clock.nowMonoMs() + holdMs
            pendingResetJob = scope.launch {
                delay(holdMs)
                if (!isHoldActive()) {
                    resetForSourceSwitch()
                }
            }
        } else {
            resetForSourceSwitch()
        }
    }

    private fun isHoldActive(): Boolean =
        windHoldUntilElapsedMs > 0L && clock.nowMonoMs() < windHoldUntilElapsedMs

    private fun processSample(input: WindFusionInput) {
        val gps = input.gps ?: return
        val gpsClockMillis = gps.clockMillis
        val gpsWallMillis = gps.timestampMillis
        val headingDeg = headingFromSample(gps, input.heading)
        if (!gps.trackRad.isFinite() || !gps.groundSpeedMs.isFinite()) {
            refreshExistingWindState(
                gpsClockMillis = gpsClockMillis,
                gpsWallMillis = gpsWallMillis,
                headingDeg = headingDeg
            )
            return
        }
        val overrideRevision = OverrideRevision(input.manualWind, input.externalWind)
        val gpsAdvanced = gpsClockMillis > lastGpsClockMillis
        val overrideChanged = overrideRevision != lastProcessedOverrideRevision
        if (!gpsAdvanced && !overrideChanged) {
            return
        }
        if (gpsAdvanced) {
            lastGpsClockMillis = gpsClockMillis
        }
        lastProcessedOverrideRevision = overrideRevision
        val trackRad = gps.trackRad
        val isFlying = flightStateSource.flightState.value.isFlying
        val circlingDecision = circlingDetector.update(
            trackDegrees = Math.toDegrees(trackRad),
            timestampMillis = gpsClockMillis,
            isFlying = isFlying
        )
        val isCircling = circlingDecision.isCircling
        val result = circlingWind.addSample(
            CirclingWindSample(
                clockMillis = gpsClockMillis,
                trackRad = trackRad,
                groundSpeed = gps.groundSpeedMs,
                isCircling = isCircling
            )
        )

        val altitudeMeters = altitudeFromSample(gps, input.pressure)

        if (result != null) {
            lastCirclingClockMillis = result.clockMillis
            windStore.slotMeasurement(
                clockMillis = result.clockMillis,
                timestampMillis = gpsWallMillis,
                altitudeMeters = altitudeMeters,
                vector = result.windVector,
                quality = result.quality,
                source = WindSource.CIRCLING
            )
        }

        val evaluated = windStore.evaluate(gpsClockMillis, altitudeMeters)
        val storeCandidate = evaluated?.let {
            WindCandidate(
                vector = it.vector,
                source = it.source,
                quality = it.quality,
                timestampMillis = it.timestampMillis
            )
        }
        val autoCandidate = storeCandidate
        val autoClockMillis = evaluated?.clockMillis

        val manualCandidate = input.manualWind?.let { it.toCandidate() }
        val externalCandidate = input.externalWind?.let { it.toCandidate() }

        val autoFresh = autoCandidate != null &&
            autoClockMillis != null &&
            gpsClockMillis >= autoClockMillis &&
            gpsClockMillis - autoClockMillis <= STALE_MS

        val selected = windSelectionUseCase.select(
            auto = autoCandidate?.takeIf { autoFresh },
            manual = manualCandidate,
            external = externalCandidate
        )

        if (selected != null) {
            lastUpdatedClockMillis = if (isAutoSource(selected.source)) {
                autoClockMillis ?: Long.MIN_VALUE
            } else {
                Long.MIN_VALUE
            }
            publishWindState(
                vector = selected.vector,
                source = selected.source,
                quality = selected.quality,
                headingDeg = headingDeg,
                timestamp = selected.timestampMillis,
                clockMillis = gpsClockMillis,
                stale = false
            )
            return
        }

        refreshExistingWindState(
            gpsClockMillis = gpsClockMillis,
            gpsWallMillis = gpsWallMillis,
            headingDeg = headingDeg
        )
    }

    private fun altitudeFromSample(gps: GpsSample, pressure: PressureSample?): Double {
        return when {
            pressure != null && pressure.altitudeMeters.isFinite() -> pressure.altitudeMeters
            gps.altitudeMeters.isFinite() -> gps.altitudeMeters
            else -> 0.0
        }
    }

    private fun headingFromSample(gps: GpsSample, heading: HeadingSample?): Double {
        return heading?.headingDeg
            ?: Math.toDegrees(gps.trackRad).takeIf { it.isFinite() }
            ?: 0.0
    }

    private fun refreshExistingWindState(
        gpsClockMillis: Long,
        gpsWallMillis: Long,
        headingDeg: Double
    ) {
        val existing = _windState.value
        val age = windAgeMillis(existing, gpsClockMillis, gpsWallMillis)
        if (age > STALE_MS) {
            _windState.value = WindState(stale = true)
            return
        }

        val vector = existing.vector ?: return
        val (head, cross) = computeHeadAndCross(vector, headingDeg)
        val normalizedQuality = normalizePublishedQuality(existing.quality)
        val confidence = computeWindConfidence(
            quality = normalizedQuality,
            source = existing.source,
            nowMonoMs = gpsClockMillis
        )
        _windState.value = existing.copy(
            quality = normalizedQuality,
            headwind = head,
            crosswind = cross,
            confidence = confidence,
            stale = false
        )
    }

    private fun windAgeMillis(
        existing: WindState,
        gpsClockMillis: Long,
        gpsWallMillis: Long
    ): Long {
        if (existing.vector == null) return Long.MAX_VALUE
        return if (isAutoSource(existing.source)) {
            if (lastUpdatedClockMillis != Long.MIN_VALUE && gpsClockMillis >= lastUpdatedClockMillis) {
                gpsClockMillis - lastUpdatedClockMillis
            } else {
                Long.MAX_VALUE
            }
        } else if (existing.lastUpdatedMillis > 0) {
            (gpsWallMillis - existing.lastUpdatedMillis).coerceAtLeast(0L)
        } else {
            Long.MAX_VALUE
        }
    }

    private fun computeHeadAndCross(wind: WindVector, headingDeg: Double): Pair<Double, Double> {
        val headingRad = Math.toRadians(headingDeg)
        val sinH = sin(headingRad)
        val cosH = cos(headingRad)

        val dot = wind.east * sinH + wind.north * cosH
        val headwind = -dot

        val crosswind = wind.east * cosH - wind.north * sinH

        return headwind to crosswind
    }

    private fun publishWindState(
        vector: WindVector,
        source: WindSource,
        quality: Int,
        headingDeg: Double,
        timestamp: Long,
        clockMillis: Long,
        stale: Boolean
    ) {
        val normalizedQuality = normalizePublishedQuality(quality)
        val (head, cross) = computeHeadAndCross(vector, headingDeg)
        val confidence = computeWindConfidence(
            quality = normalizedQuality,
            source = source,
            nowMonoMs = clockMillis
        )
        _windState.value = WindState(
            vector = vector,
            source = source,
            quality = normalizedQuality,
            headwind = head,
            crosswind = cross,
            lastUpdatedMillis = timestamp,
            stale = stale,
            confidence = confidence,
            lastCirclingClockMillis = lastCirclingClockMillis
        )
        pendingResetJob?.cancel()
        pendingResetJob = null
        windHoldUntilElapsedMs = 0L
    }

    private fun WindOverride.toCandidate(): WindCandidate = WindCandidate(
        vector = vector,
        source = source,
        quality = quality,
        timestampMillis = timestampMillis
    )

    private fun isAutoSource(source: WindSource): Boolean =
        source == WindSource.CIRCLING || source == WindSource.EKF

    private fun computeWindConfidence(quality: Int, source: WindSource, nowMonoMs: Long): Double {
        if (quality <= 0) return 0.0
        val baseQuality = (quality.coerceIn(0, MAX_MEASUREMENT_QUALITY)).toDouble() / MAX_MEASUREMENT_QUALITY.toDouble()
        if (source == WindSource.MANUAL || source == WindSource.EXTERNAL) {
            return baseQuality
        }
        if (!isAutoSource(source)) return 0.0
        if (lastCirclingClockMillis <= 0L || nowMonoMs <= 0L) return 0.0
        val ageMs = (nowMonoMs - lastCirclingClockMillis).coerceAtLeast(0L)
        val decay = 0.5.pow(ageMs.toDouble() / CONF_HALF_LIFE_MS.toDouble())
        return (baseQuality * decay).coerceIn(0.0, 1.0)
    }

    private fun normalizePublishedQuality(quality: Int): Int =
        quality.coerceIn(0, MAX_MEASUREMENT_QUALITY)

    companion object {
        private const val MAX_MEASUREMENT_QUALITY = 5
        private const val STALE_MS = 3_600_000L // 1 hour (soaring-scale glides between wind updates)
        private const val REPLAY_WIND_HOLD_MS = 10_000L
        private const val CONF_HALF_LIFE_MS = 7 * 60 * 1000L
    }
}
