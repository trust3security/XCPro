package com.trust3.xcpro.replay

import com.trust3.xcpro.sensors.SensorFusionRepository
import kotlinx.coroutines.delay
import kotlin.math.abs

internal suspend fun emitFinishRampIfNeeded(
    lastPoint: IgcPoint,
    session: SessionState,
    simConfig: ReplaySimConfig,
    sampleEmitter: ReplaySampleEmitter,
    replayFusionRepository: SensorFusionRepository?
) {
    val repo = replayFusionRepository ?: return
    val lastDisplay = repo.flightDataFlow.value?.displayVario?.value
    if (lastDisplay == null || !lastDisplay.isFinite()) return
    val lastDisplayKts = lastDisplay * MPS_TO_KTS
    val absDisplayKts = abs(lastDisplayKts)
    if (absDisplayKts < FINISH_RAMP_MIN_START_KTS || absDisplayKts > FINISH_RAMP_MAX_START_KTS) return

    val stepSimMs = simConfig.baroStepMs.coerceAtLeast(1L)
    val samplesPerStep = (FINISH_RAMP_STEP_DURATION_MS / stepSimMs).coerceAtLeast(1L)
    val delayMs = (stepSimMs / session.speedMultiplier).toLong().coerceAtLeast(1L)
    val sign = if (lastDisplayKts >= 0.0) 1.0 else -1.0
    val rampSteps = FINISH_RAMP_STEPS_KTS.dropWhile { it > absDisplayKts + 1e-6 }
    if (rampSteps.isEmpty()) return

    var prev = lastPoint
    var timestamp = lastPoint.timestampMillis
    for (stepKts in rampSteps) {
        val stepMs = stepKts * KTS_TO_MPS * sign
        repeat(samplesPerStep.toInt()) {
            timestamp += stepSimMs
            repo.updateReplayRealVario(stepMs, timestamp)
            val rampPoint = lastPoint.copy(timestampMillis = timestamp)
            sampleEmitter.emitSample(
                current = rampPoint,
                previous = prev,
                qnhHpa = session.qnhHpa,
                startTimestampMillis = session.startTimestampMillis,
                replayFusionRepository = replayFusionRepository
            )
            prev = rampPoint
            delay(delayMs)
        }
    }
    repo.updateReplayRealVario(null, timestamp)
}

private const val FINISH_RAMP_STEP_DURATION_MS = 350L
private const val FINISH_RAMP_MIN_START_KTS = 0.1   // ignore near-zero
private const val FINISH_RAMP_MAX_START_KTS = 2.0   // only taper gentle end values
private val FINISH_RAMP_STEPS_KTS = listOf(0.7, 0.4, 0.3, 0.2, 0.1, 0.0)
private const val MPS_TO_KTS = 1.943844
private const val KTS_TO_MPS = 0.514444
