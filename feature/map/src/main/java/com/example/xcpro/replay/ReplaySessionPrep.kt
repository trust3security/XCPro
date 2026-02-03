package com.example.xcpro.replay

import com.example.xcpro.core.common.logging.AppLogger

internal data class PreparedReplaySession(
    val points: List<IgcPoint>,
    val qnhHpa: Double,
    val startMillis: Long,
    val durationMillis: Long
)

internal fun prepareReplaySession(
    log: IgcLog,
    selection: Selection,
    simConfig: ReplaySimConfig,
    sampleEmitter: ReplaySampleEmitter,
    tag: String
): PreparedReplaySession {
    sampleEmitter.reset()
    val densified = when (simConfig.interpolation) {
        ReplayInterpolation.CATMULL_ROM_RUNTIME -> log.points
        ReplayInterpolation.CATMULL_ROM -> {
            val stepMs = when (simConfig.mode) {
                ReplayMode.REALTIME_SIM -> simConfig.baroStepMs
                ReplayMode.REFERENCE -> simConfig.referenceStepMs
            }
            IgcReplayMath.densifyPointsCatmullRom(
                original = log.points,
                stepMs = stepMs
            )
        }
        ReplayInterpolation.LINEAR -> when (simConfig.mode) {
            ReplayMode.REALTIME_SIM -> IgcReplayMath.densifyPoints(
                original = log.points,
                stepMs = simConfig.baroStepMs,
                jitterMs = simConfig.jitterMs,
                random = sampleEmitter.random
            )
            ReplayMode.REFERENCE -> IgcReplayMath.densifyPoints(
                original = log.points,
                stepMs = simConfig.referenceStepMs,
                jitterMs = 0L,
                random = sampleEmitter.random
            )
        }
    }
    if (densified.isEmpty()) throw IllegalArgumentException("IGC file has no B records")
    val qnh = log.metadata.qnhHpa ?: DEFAULT_QNH_HPA
    val start = densified.first().timestampMillis
    val duration = (densified.last().timestampMillis - start).coerceAtLeast(1L)
    logReplaySessionPrep(
        selection = selection,
        pointCount = densified.size,
        startMillis = start,
        endMillis = densified.last().timestampMillis,
        qnh = qnh,
        tag = tag
    )
    AppLogger.i(tag, "REPLAY_SESSION selection=${selection.document.displayName ?: selection.document.uri} durationMs=$duration start=$start")
    return PreparedReplaySession(
        points = densified,
        qnhHpa = qnh,
        startMillis = start,
        durationMillis = duration
    )
}
