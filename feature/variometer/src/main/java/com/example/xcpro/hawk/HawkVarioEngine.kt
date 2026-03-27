package com.example.xcpro.hawk

import com.example.xcpro.sensors.domain.pressureToAltitudeMeters
import javax.inject.Inject
import kotlin.math.abs

class HawkVarioEngine @Inject constructor() {
    private var baroQc = BaroQc(
        windowSize = 1,
        outlierThresholdM = 1.0,
        maxRateMps = 1.0,
        rejectionWindow = 1
    )
    private var accelTrust = AdaptiveAccelTrust(
        windowSize = 1,
        varianceOkMax = 1.0,
        weightMax = 0.0
    )

    private var lastAcceptedBaroMonoMs: Long? = null
    private var lastAcceptedBaroAltitudeM: Double? = null
    private var lastBaroSampleMonoMs: Long? = null
    private var lastBaroVarioMps: Double? = null

    private var lastAccelMonoMs: Long? = null
    private var lastAccelReliable: Boolean = false

    private var accelIntegralMps: Double = 0.0
    private var velocityMps: Double = 0.0
    private var audioVarioMps: Double = 0.0

    private var lastConfig: HawkConfig? = null

    fun reset() {
        lastAcceptedBaroMonoMs = null
        lastAcceptedBaroAltitudeM = null
        lastBaroSampleMonoMs = null
        lastBaroVarioMps = null
        lastAccelMonoMs = null
        lastAccelReliable = false
        accelIntegralMps = 0.0
        velocityMps = 0.0
        audioVarioMps = 0.0
        baroQc.reset()
        accelTrust.reset()
    }

    fun updateAccel(sample: HawkAccelSample, config: HawkConfig) {
        ensureConfig(config)
        val monoMs = sample.monotonicTimestampMillis
        if (monoMs <= 0L) return

        val previous = lastAccelMonoMs
        if (previous != null) {
            val deltaMs = monoMs - previous
            if (deltaMs > 0L && deltaMs <= config.accelMaxGapMs) {
                if (sample.isReliable) {
                    val dtSeconds = deltaMs.toDouble() / 1000.0
                    accelIntegralMps += sample.verticalAcceleration * dtSeconds
                    accelIntegralMps = accelIntegralMps.coerceIn(
                        -config.accelIntegralClampMps,
                        config.accelIntegralClampMps
                    )
                }
            }
        }

        lastAccelMonoMs = monoMs
        lastAccelReliable = sample.isReliable
        if (sample.isReliable) {
            accelTrust.addSample(sample.verticalAcceleration)
        }
    }

    fun updateBaro(sample: HawkBaroSample, config: HawkConfig): HawkOutput? {
        ensureConfig(config)
        val monoMs = sample.monotonicTimestampMillis
        if (monoMs <= 0L) return null

        val previousSampleMs = lastBaroSampleMonoMs
        lastBaroSampleMonoMs = monoMs
        val baroHz = if (previousSampleMs != null) {
            val dtMs = (monoMs - previousSampleMs).coerceAtLeast(1L)
            1000.0 / dtMs.toDouble()
        } else {
            null
        }

        val altitudeM = pressureToAltitudeMeters(sample.pressureHpa)
        if (!altitudeM.isFinite()) {
            baroQc.record(accepted = false, altitudeM = altitudeM)
            return buildOutput(
                vRawMps = velocityMps,
                vAudioMps = audioVarioMps,
                accelVariance = accelTrust.variance(),
                baroInnovationMps = null,
                baroHz = baroHz,
                lastUpdateMonoMs = monoMs,
                baroSampleAccepted = false,
                baroRejectionRate = baroQc.rejectionRate(),
                lastBaroVarioMps = lastBaroVarioMps
            )
        }

        val lastAcceptedMs = lastAcceptedBaroMonoMs
        val lastAcceptedAlt = lastAcceptedBaroAltitudeM
        if (lastAcceptedMs == null || lastAcceptedAlt == null) {
            seedBaseline(altitudeM, monoMs)
            baroQc.record(accepted = true, altitudeM = altitudeM)
            lastBaroVarioMps = 0.0
            return buildOutput(
                vRawMps = velocityMps,
                vAudioMps = audioVarioMps,
                accelVariance = accelTrust.variance(),
                baroInnovationMps = null,
                baroHz = baroHz,
                lastUpdateMonoMs = monoMs,
                baroSampleAccepted = true,
                baroRejectionRate = baroQc.rejectionRate(),
                lastBaroVarioMps = lastBaroVarioMps
            )
        }

        val deltaMs = monoMs - lastAcceptedMs
        if (deltaMs <= 0L || deltaMs > config.maxBaroGapMs) {
            seedBaseline(altitudeM, monoMs)
            baroQc.record(accepted = true, altitudeM = altitudeM)
            lastBaroVarioMps = 0.0
            return buildOutput(
                vRawMps = velocityMps,
                vAudioMps = audioVarioMps,
                accelVariance = accelTrust.variance(),
                baroInnovationMps = null,
                baroHz = baroHz,
                lastUpdateMonoMs = monoMs,
                baroSampleAccepted = true,
                baroRejectionRate = baroQc.rejectionRate(),
                lastBaroVarioMps = lastBaroVarioMps
            )
        }

        val dtSeconds = deltaMs.toDouble() / 1000.0
        val baroVario = (altitudeM - lastAcceptedAlt) / dtSeconds
        val accelVariance = accelTrust.variance()
        val accelFresh = lastAccelMonoMs != null && (monoMs - lastAccelMonoMs!!) <= config.accelStaleMs
        val accelReliable = lastAccelReliable && accelFresh
        val accelWeight = if (accelReliable) accelTrust.weight(accelVariance) else 0.0
        val predicted = (velocityMps + accelIntegralMps).coerceIn(
            -config.accelIntegralClampMps,
            config.accelIntegralClampMps
        )
        val innovation = baroVario - predicted

        val qcResult = baroQc.evaluate(altitudeM, baroVario)
        var accepted = qcResult.accepted
        if (accelReliable && abs(innovation) > config.baroInnovationRejectMps) {
            accepted = false
        }
        baroQc.record(accepted = accepted, altitudeM = altitudeM)

        if (!accepted) {
            return buildOutput(
                vRawMps = velocityMps,
                vAudioMps = audioVarioMps,
                accelVariance = accelVariance,
                baroInnovationMps = innovation,
                baroHz = baroHz,
                lastUpdateMonoMs = monoMs,
                baroSampleAccepted = false,
                baroRejectionRate = baroQc.rejectionRate(),
                lastBaroVarioMps = lastBaroVarioMps
            )
        }

        // AI-NOTE: Baro-gated fusion; accel cannot create lift unless baro supports it.
        val supportAccel = abs(baroVario) >= config.baroSupportMinMps
        val fused = if (supportAccel && accelWeight > 0.0) {
            (1.0 - accelWeight) * baroVario + accelWeight * predicted
        } else {
            baroVario
        }

        velocityMps = fused.coerceIn(-config.audioClampMps, config.audioClampMps)
        audioVarioMps = smoothAudio(audioVarioMps, velocityMps, dtSeconds, config)
        lastAcceptedBaroMonoMs = monoMs
        lastAcceptedBaroAltitudeM = altitudeM
        lastBaroVarioMps = baroVario
        accelIntegralMps = 0.0

        return buildOutput(
            vRawMps = velocityMps,
            vAudioMps = audioVarioMps,
            accelVariance = accelVariance,
            baroInnovationMps = innovation,
            baroHz = baroHz,
            lastUpdateMonoMs = monoMs,
            baroSampleAccepted = true,
            baroRejectionRate = baroQc.rejectionRate(),
            lastBaroVarioMps = baroVario
        )
    }

    private fun ensureConfig(config: HawkConfig) {
        if (config == lastConfig) return
        baroQc.updateConfig(
            windowSize = config.baroMedianWindow,
            outlierThresholdM = config.baroOutlierThresholdM,
            maxRateMps = config.maxBaroRateMps,
            rejectionWindow = config.baroRejectionWindow
        )
        accelTrust.updateConfig(
            windowSize = config.accelVarianceWindow,
            varianceOkMax = config.accelVarianceOkMax,
            weightMax = config.accelWeightMax
        )
        lastConfig = config
    }

    private fun seedBaseline(altitudeM: Double, monoMs: Long) {
        lastAcceptedBaroMonoMs = monoMs
        lastAcceptedBaroAltitudeM = altitudeM
        velocityMps = 0.0
        audioVarioMps = 0.0
        accelIntegralMps = 0.0
        baroQc.reset()
    }

    private fun smoothAudio(current: Double, target: Double, dtSeconds: Double, config: HawkConfig): Double {
        val tau = config.audioTimeConstantMs.toDouble() / 1000.0
        val alpha = (dtSeconds / (tau + dtSeconds)).coerceIn(0.0, 1.0)
        var updated = current + alpha * (target - current)
        if (abs(updated) < config.audioDeadbandMps) {
            updated = 0.0
        }
        return updated.coerceIn(-config.audioClampMps, config.audioClampMps)
    }

    private fun buildOutput(
        vRawMps: Double?,
        vAudioMps: Double?,
        accelVariance: Double?,
        baroInnovationMps: Double?,
        baroHz: Double?,
        lastUpdateMonoMs: Long,
        baroSampleAccepted: Boolean,
        baroRejectionRate: Double,
        lastBaroVarioMps: Double?
    ): HawkOutput = HawkOutput(
        vRawMps = vRawMps,
        vAudioMps = vAudioMps,
        accelVariance = accelVariance,
        baroInnovationMps = baroInnovationMps,
        baroHz = baroHz,
        lastUpdateMonoMs = lastUpdateMonoMs,
        lastBaroSampleMonoMs = lastBaroSampleMonoMs,
        lastAccelSampleMonoMs = lastAccelMonoMs,
        accelReliable = lastAccelReliable,
        baroSampleAccepted = baroSampleAccepted,
        baroRejectionRate = baroRejectionRate,
        lastBaroVarioMps = lastBaroVarioMps
    )
}
