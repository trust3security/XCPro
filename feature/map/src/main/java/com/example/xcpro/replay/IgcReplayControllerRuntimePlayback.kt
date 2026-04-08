package com.example.xcpro.replay

import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.flightdata.FlightDataRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

internal fun IgcReplayControllerRuntime.updateProgress(timestamp: Long) {
    _session.update { state ->
        if (state.selection == null) state else
            state.copy(currentTimestampMillis = timestamp)
    }
    val s = _session.value
    val elapsed = s.currentTimestampMillis - s.startTimestampMillis
    val frac = s.progressFraction
    if (AppLogger.rateLimit(IgcReplayControllerRuntime.TAG, "replay_progress", 1_000L)) {
        AppLogger.i(
            IgcReplayControllerRuntime.TAG,
            "REPLY_PROGRESS elapsed=${elapsed}ms progress=${"%.3f".format(frac)} status=${s.status}"
        )
    }
}

internal suspend fun IgcReplayControllerRuntime.finishReplay() {
    points.lastOrNull()?.let { lastPoint ->
        emitFinishRampIfNeeded(
            lastPoint = lastPoint,
            session = _session.value,
            simConfig = simConfig,
            sampleEmitter = sampleEmitter,
            replayFusionRepository = replayFusionRepository
        )
    }
    silenceReplayAudio("finish")
    flightDataRepository.clear()
    replayFusionRepository?.stop()
    _session.update { it.copy(status = SessionStatus.PAUSED) }
    flightDataRepository.setActiveSource(FlightDataRepository.Source.LIVE)
    flightDataRepository.update(null, FlightDataRepository.Source.LIVE)
    resumeSensors()
    currentIndex = points.size
    if (autoStopAfterFinish) {
        autoStopAfterFinish = false
        resetReplayAfterFinish()
    } else {
        resetReplayModeIfNeeded()
    }
    _events.emit(ReplayEvent.Completed(points.size))
}

internal fun IgcReplayControllerRuntime.resetReplayAfterFinish() {
    replaySensorSource.reset()
    replayAirspeedRepository.reset()
    flightDataRepository.clear()
    flightDataRepository.setActiveSource(FlightDataRepository.Source.LIVE)
    flightDataRepository.update(null, FlightDataRepository.Source.LIVE)
    replayFusionRepository?.resetQnhToStandard()
    points = emptyList()
    currentIndex = 0
    _session.value = SessionState(speedMultiplier = _session.value.speedMultiplier)
    resetReplayModeIfNeeded()
}

internal fun IgcReplayControllerRuntime.cancelReplayJob() {
    replayJob?.cancel()
    replayJob = null
}

internal fun IgcReplayControllerRuntime.suspendSensors() {
    pipeline.suspendSensors()
}

internal fun IgcReplayControllerRuntime.resumeSensors() {
    pipeline.resumeSensors()
}

internal fun IgcReplayControllerRuntime.silenceReplayAudio(reason: String) {
    val repo = replayFusionRepository ?: return
    AppLogger.i(IgcReplayControllerRuntime.TAG, "REPLAY_AUDIO silence reason=$reason")
    repo.stop()
}

internal fun IgcReplayControllerRuntime.resetReplayModeIfNeeded() {
    if (!resetModeAfterSession) return
    resetModeAfterSession = false
    val defaultMode = IgcReplayControllerRuntime.DEFAULT_SIM_CONFIG.mode
    if (simConfig.mode != defaultMode) {
        simConfig = simConfig.copy(mode = defaultMode)
        sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
        AppLogger.i(IgcReplayControllerRuntime.TAG, "Replay mode reset to ${defaultMode.name}")
    }
}

internal fun IgcReplayControllerRuntime.resetReplayEmitterState(reason: String) {
    AppLogger.d(IgcReplayControllerRuntime.TAG, "REPLAY_RESET reason=$reason")
    sampleEmitter.reset()
    replaySensorSource.reset()
    runtimeInterpolator?.reset()
    runtimeTimestampMs = _session.value.startTimestampMillis
}

internal fun IgcReplayControllerRuntime.prepareSession(log: IgcLog, selection: Selection) {
    val prepared = prepareReplaySession(
        log = log,
        selection = selection,
        simConfig = simConfig,
        sampleEmitter = sampleEmitter,
        tag = IgcReplayControllerRuntime.TAG
    )
    cancelReplayJob()
    seekJob?.cancel()
    seekJob = null
    points = prepared.points
    currentIndex = 0
    runtimeInterpolator = if (simConfig.interpolation == ReplayInterpolation.CATMULL_ROM_RUNTIME) {
        ReplayRuntimeInterpolator(points)
    } else {
        null
    }
    uiRuntimeInterpolator = if (simConfig.interpolation == ReplayInterpolation.CATMULL_ROM_RUNTIME) {
        ReplayRuntimeInterpolator(points)
    } else {
        null
    }
    runtimeTimestampMs = prepared.startMillis
    suspendSensors()
    replaySensorSource.reset()

    flightDataRepository.setActiveSource(FlightDataRepository.Source.REPLAY)
    val repo = checkNotNull(replayFusionRepository) { "Replay fusion pipeline not initialized" }
    repo.stop()
    repo.setManualQnh(prepared.qnhHpa)
    _session.value = SessionState(
        selection = selection,
        status = SessionStatus.PAUSED,
        speedMultiplier = _session.value.speedMultiplier,
        startTimestampMillis = prepared.startMillis,
        currentTimestampMillis = prepared.startMillis,
        durationMillis = prepared.durationMillis,
        qnhHpa = prepared.qnhHpa
    )
    if (simConfig.interpolation == ReplayInterpolation.CATMULL_ROM_RUNTIME) {
        val interpolated = runtimeInterpolator?.interpolate(prepared.startMillis)
        val initialPoint = interpolated?.point ?: points.first()
        sampleEmitter.emitSample(
            initialPoint,
            null,
            prepared.qnhHpa,
            prepared.startMillis,
            replayFusionRepository,
            interpolated?.movement
        )
    } else {
        sampleEmitter.emitSample(
            points.first(),
            null,
            prepared.qnhHpa,
            prepared.startMillis,
            replayFusionRepository
        )
    }
}

internal suspend fun IgcReplayControllerRuntime.playRuntimeInterpolation() {
    val interpolator = runtimeInterpolator ?: return
    val stepMs = simConfig.gpsStepMs.coerceAtLeast(1L)
    val sessionStart = _session.value.startTimestampMillis
    val sessionEnd = sessionStart + _session.value.durationMillis
    var previousPoint: IgcPoint? = null
    while (runtimeTimestampMs <= sessionEnd && coroutineContext.isActive) {
        val fix = interpolator.interpolate(runtimeTimestampMs) ?: break
        sampleEmitter.emitSample(
            fix.point,
            previousPoint,
            _session.value.qnhHpa,
            _session.value.startTimestampMillis,
            replayFusionRepository,
            fix.movement
        )
        previousPoint = fix.point
        updateProgress(runtimeTimestampMs)
        if (AppLogger.rateLimit(IgcReplayControllerRuntime.TAG, "replay_frame", 1_000L)) {
            AppLogger.d(
                IgcReplayControllerRuntime.TAG,
                "REPLY_FRAME runtime ts=${runtimeTimestampMs} " +
                    "alt=${fix.point.pressureAltitude ?: fix.point.gpsAltitude} speed=${_session.value.speedMultiplier}"
            )
        }
        if (runtimeTimestampMs >= sessionEnd) {
            finishReplay()
            break
        }
        val speed = _session.value.speedMultiplier
        val delayMillis = (stepMs / speed).toLong().coerceAtLeast(1L)
        runtimeTimestampMs += stepMs
        delay(delayMillis)
    }
}
