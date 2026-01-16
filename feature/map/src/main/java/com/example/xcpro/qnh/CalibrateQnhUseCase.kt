package com.example.xcpro.qnh

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.SensorDataSource
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.sensors.BaroData
import com.example.xcpro.di.LiveSource
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import java.util.concurrent.atomic.AtomicBoolean

class CalibrateQnhUseCase @Inject constructor(
    @LiveSource private val sensorDataSource: SensorDataSource,
    private val terrainProvider: TerrainElevationProvider,
    private val qnhRepository: QnhRepository,
    private val flightDataRepository: FlightDataRepository,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    private val config: QnhCalibrationConfig = QnhCalibrationConfig()
) {

    private val calibrationInProgress = AtomicBoolean(false)

    suspend fun execute(): QnhCalibrationResult = withContext(dispatcher) {
        if (flightDataRepository.activeSource.value == FlightDataRepository.Source.REPLAY) {
            qnhRepository.updateCalibrationState(
                QnhCalibrationState.Failed(QnhCalibrationFailureReason.REPLAY_MODE)
            )
            return@withContext QnhCalibrationResult.Failure(QnhCalibrationFailureReason.REPLAY_MODE)
        }

        if (!calibrationInProgress.compareAndSet(false, true)) {
            return@withContext QnhCalibrationResult.Failure(QnhCalibrationFailureReason.ALREADY_RUNNING)
        }

        try {
            qnhRepository.updateCalibrationState(
                QnhCalibrationState.Collecting(0, config.samplesRequired)
            )

            val samples = mutableListOf<Double>()
            var lastBaroTime = Long.MIN_VALUE
            var terrainElevation: Double? = null
            var terrainAttempted = false
            var usedTerrain = false

            val sampleFlow = combine(
                sensorDataSource.baroFlow.filterNotNull(),
                sensorDataSource.gpsFlow.filterNotNull()
            ) { baro, gps ->
                Pair(baro, gps)
            }.transform { (baro, gps) ->
                val baroTime = baro.timeForCalculationsMillis()
                if (baroTime == lastBaroTime) return@transform
                lastBaroTime = baroTime

                if (!isEligibleSample(baro, gps, config)) return@transform

                if (!terrainAttempted) {
                    terrainAttempted = true
                    terrainElevation = runCatching {
                        terrainProvider.getElevationMeters(
                            gps.position.latitude,
                            gps.position.longitude
                        )
                    }.getOrNull()
                    usedTerrain = terrainElevation != null
                }

                val altitudeMsl = terrainElevation?.plus(config.estimatedAglMeters)
                    ?: gps.altitude.value
                if (!altitudeMsl.isFinite()) return@transform

                val qnhSample = QnhMath.computeQnhFromPressure(
                    pressureHpa = baro.pressureHPa.value,
                    altitudeMeters = altitudeMsl
                )
                if (!qnhSample.isFinite()) return@transform
                if (qnhSample < config.minQnhHpa || qnhSample > config.maxQnhHpa) return@transform

                emit(qnhSample)
            }

            try {
                withTimeout(config.timeoutMs) {
                    sampleFlow.onEach { sample ->
                        samples.add(sample)
                        qnhRepository.updateCalibrationState(
                            QnhCalibrationState.Collecting(
                                samplesCollected = samples.size,
                                samplesRequired = config.samplesRequired
                            )
                        )
                    }.take(config.samplesRequired).collect { /* no-op */ }
                }
            } catch (timeout: TimeoutCancellationException) {
                if (samples.size < config.samplesRequired) {
                    qnhRepository.updateCalibrationState(QnhCalibrationState.TimedOut)
                    return@withContext QnhCalibrationResult.Failure(QnhCalibrationFailureReason.TIMEOUT)
                }
            }

            if (samples.size < config.samplesRequired) {
                qnhRepository.updateCalibrationState(QnhCalibrationState.TimedOut)
                return@withContext QnhCalibrationResult.Failure(QnhCalibrationFailureReason.TIMEOUT)
            }

            val aggregated = aggregateSamples(samples, config.trimFraction)
            if (aggregated < config.minQnhHpa || aggregated > config.maxQnhHpa) {
                qnhRepository.updateCalibrationState(
                    QnhCalibrationState.Failed(QnhCalibrationFailureReason.INVALID_QNH)
                )
                return@withContext QnhCalibrationResult.Failure(QnhCalibrationFailureReason.INVALID_QNH)
            }

            val source = if (usedTerrain) QnhSource.AUTO_TERRAIN else QnhSource.AUTO_GPS
            val confidence = if (usedTerrain) QnhConfidence.HIGH else QnhConfidence.MEDIUM
            val value = QnhValue(
                hpa = aggregated,
                source = source,
                calibratedAtMillis = System.currentTimeMillis(),
                confidence = confidence
            )

            qnhRepository.applyAutoQnh(value)
            qnhRepository.updateCalibrationState(QnhCalibrationState.Succeeded(value))
            QnhCalibrationResult.Success(value)
        } catch (t: Throwable) {
            qnhRepository.updateCalibrationState(QnhCalibrationState.Failed(QnhCalibrationFailureReason.UNKNOWN))
            QnhCalibrationResult.Failure(QnhCalibrationFailureReason.UNKNOWN)
        } finally {
            calibrationInProgress.set(false)
        }
    }

    private fun isEligibleSample(
        baro: BaroData,
        gps: GPSData,
        config: QnhCalibrationConfig
    ): Boolean {
        if (gps.accuracy > config.maxGpsAccuracyMeters) return false
        if (gps.speed.value > config.maxGpsSpeedMs) return false
        val baroTime = baro.timeForCalculationsMillis()
        val gpsTime = gps.timeForCalculationsMillis
        val ageMs = abs(baroTime - gpsTime)
        if (ageMs > config.maxSampleAgeMs) return false
        return true
    }

    private fun aggregateSamples(samples: List<Double>, trimFraction: Double): Double {
        if (samples.isEmpty()) return Double.NaN
        val sorted = samples.sorted()
        val trimCount = (sorted.size * trimFraction).toInt().coerceAtMost(sorted.size / 2)
        val trimmed = if (trimCount > 0 && sorted.size > trimCount * 2) {
            sorted.subList(trimCount, sorted.size - trimCount)
        } else {
            sorted
        }
        return trimmed.average()
    }

    private fun BaroData.timeForCalculationsMillis(): Long =
        if (monotonicTimestampMillis > 0L) monotonicTimestampMillis else timestamp
}

sealed interface QnhCalibrationResult {
    data class Success(val value: QnhValue) : QnhCalibrationResult
    data class Failure(val reason: QnhCalibrationFailureReason) : QnhCalibrationResult
}
