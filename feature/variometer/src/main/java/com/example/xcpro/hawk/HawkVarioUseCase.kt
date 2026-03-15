package com.example.xcpro.hawk

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.core.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalCoroutinesApi::class)
class HawkVarioUseCase @Inject constructor(
    private val repository: HawkVarioRepository,
    private val configRepository: HawkConfigRepository,
    private val clock: Clock,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) : HawkVarioPreviewReadPort {
    override val hawkVarioUiState: Flow<HawkVarioUiState> =
        configRepository.config.flatMapLatest { config ->
            val ticker = tickerFlow(config.staleCheckIntervalMs)
            combine(repository.output, ticker) { output, nowMono ->
                mapOutput(output, config, nowMono)
            }
        }

    private fun tickerFlow(intervalMs: Long): Flow<Long> = flow {
        val safeInterval = intervalMs.coerceAtLeast(50L)
        while (true) {
            emit(clock.nowMonoMs())
            delay(safeInterval)
        }
    }.flowOn(dispatcher)

    private fun mapOutput(
        output: HawkOutput?,
        config: HawkConfig,
        nowMonoMs: Long
    ): HawkVarioUiState {
        if (output == null) {
            return HawkVarioUiState()
        }

        val accelFresh = output.lastAccelSampleMonoMs?.let { nowMonoMs - it <= config.accelStaleMs } ?: false
        val accelVarianceOk = output.accelVariance?.let { it <= config.accelVarianceOkMax } ?: false
        val accelOk = output.accelReliable && accelFresh && accelVarianceOk

        val baroFresh = output.lastBaroSampleMonoMs?.let { nowMonoMs - it <= config.baroStaleMs } ?: false
        val baroRateStable = output.lastBaroVarioMps?.let { abs(it) <= config.maxBaroRateMps } ?: false
        val baroRejectionOk = output.baroRejectionRate <= config.baroRejectionMaxFraction
        val baroOk = baroFresh && baroRateStable && baroRejectionOk && output.baroSampleAccepted

        val confidenceScore = computeConfidenceScore(output, config, nowMonoMs)
        val confidence = if (output.lastUpdateMonoMs == null) {
            HawkConfidence.UNKNOWN
        } else {
            mapScoreToConfidence(confidenceScore)
        }

        return HawkVarioUiState(
            varioSmoothedMps = output.vAudioMps?.toFloat(),
            varioRawMps = output.vRawMps?.toFloat(),
            accelOk = accelOk,
            baroOk = baroOk,
            confidence = confidence,
            confidenceScore = confidenceScore.toFloat(),
            accelVariance = output.accelVariance?.toFloat(),
            baroInnovationMps = output.baroInnovationMps?.toFloat(),
            baroHz = output.baroHz?.toFloat(),
            lastUpdateElapsedRealtimeMs = output.lastUpdateMonoMs
        )
    }

    private fun computeConfidenceScore(
        output: HawkOutput,
        config: HawkConfig,
        nowMonoMs: Long
    ): Double {
        val baroFreshScore = freshnessScore(output.lastBaroSampleMonoMs, config.baroStaleMs, nowMonoMs)
        val baroRateScore = normalizedInverse(abs(output.lastBaroVarioMps ?: Double.NaN), config.maxBaroRateMps)
        val baroRejectScore = normalizedInverse(output.baroRejectionRate, config.baroRejectionMaxFraction)
        val baroAcceptScore = if (output.baroSampleAccepted) 1.0 else 0.0
        val baroQuality = baroFreshScore * baroRateScore * baroRejectScore * baroAcceptScore

        val accelFreshScore = freshnessScore(output.lastAccelSampleMonoMs, config.accelStaleMs, nowMonoMs)
        val accelVarianceScore = normalizedInverse(output.accelVariance ?: Double.NaN, config.accelVarianceOkMax)
        val accelReliableScore = if (output.accelReliable) 1.0 else 0.0
        val accelQuality = accelFreshScore * accelVarianceScore * accelReliableScore

        val score = baroQuality * (0.4 + 0.6 * accelQuality)
        return score.coerceIn(0.0, 1.0)
    }

    private fun freshnessScore(lastSampleMonoMs: Long?, staleMs: Long, nowMonoMs: Long): Double {
        if (lastSampleMonoMs == null) return 0.0
        val age = max(0L, nowMonoMs - lastSampleMonoMs).toDouble()
        if (staleMs <= 0L) return 0.0
        val score = 1.0 - (age / staleMs.toDouble())
        return score.coerceIn(0.0, 1.0)
    }

    private fun normalizedInverse(value: Double, maxValue: Double): Double {
        if (!value.isFinite() || !maxValue.isFinite() || maxValue <= 0.0) return 0.0
        val score = 1.0 - (value / maxValue)
        return score.coerceIn(0.0, 1.0)
    }

    private fun mapScoreToConfidence(score: Double): HawkConfidence {
        return when {
            score < 0.15 -> HawkConfidence.LEVEL1
            score < 0.30 -> HawkConfidence.LEVEL2
            score < 0.45 -> HawkConfidence.LEVEL3
            score < 0.60 -> HawkConfidence.LEVEL4
            score < 0.75 -> HawkConfidence.LEVEL5
            else -> HawkConfidence.LEVEL6
        }
    }
}
