package com.example.xcpro.replay

import android.net.Uri
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.core.common.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

internal suspend fun IgcReplayControllerRuntime.loadDocumentRuntime(document: DocumentRef) {
    val uri = Uri.parse(document.uri)
    loadFileRuntime(uri, document.displayName)
}

internal suspend fun IgcReplayControllerRuntime.loadFileRuntime(uri: Uri, displayName: String?) {
    ensureReplayPipelineActive()
    var failure: Throwable? = null
    val document = DocumentRef(uri = uri.toString(), displayName = displayName)
    withContext(scope.coroutineContext) {
        try {
            val log = appContext.loadIgcLog(uri, igcParser)
            AppLogger.i(
                IgcReplayControllerRuntime.TAG,
                "REPLY_LOAD Loaded IGC file ${document.displayName ?: document.uri} " +
                    "with ${log.points.size} raw points (qnh=${log.metadata.qnhHpa})"
            )
            prepareSession(
                log = log,
                selection = Selection(document)
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            failure = t
        }
    }
    failure?.let { t ->
        AppLogger.e(IgcReplayControllerRuntime.TAG, "Failed to load IGC file ${document.displayName ?: document.uri}", t)
        _events.tryEmit(ReplayEvent.Failed(t))
        throw t
    }
}

internal suspend fun IgcReplayControllerRuntime.loadAssetRuntime(assetPath: String, displayName: String? = null) {
    ensureReplayPipelineActive()
    var failure: Throwable? = null
    withContext(scope.coroutineContext) {
        try {
            val log = appContext.loadIgcAssetLog(assetPath, igcParser)
            val name = displayName ?: assetPath.substringAfterLast('/')
            val uri = Uri.parse("${IgcReplayControllerRuntime.ASSET_URI_PREFIX}$assetPath")
            val document = DocumentRef(uri = uri.toString(), displayName = name)
            AppLogger.i(IgcReplayControllerRuntime.TAG, "REPLY_LOAD Loaded IGC asset $assetPath with ${log.points.size} raw points (qnh=${log.metadata.qnhHpa})")
            prepareSession(
                log = log,
                selection = Selection(document)
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            failure = t
        }
    }
    failure?.let { t ->
        AppLogger.e(IgcReplayControllerRuntime.TAG, "Failed to load IGC asset $assetPath", t)
        _events.tryEmit(ReplayEvent.Failed(t))
        throw t
    }
}

internal suspend fun IgcReplayControllerRuntime.loadLogRuntime(log: IgcLog, displayName: String? = null) {
    ensureReplayPipelineActive()
    var failure: Throwable? = null
    withContext(scope.coroutineContext) {
        try {
            val name = displayName ?: "Replay log"
            val uri = Uri.parse("memory://replay/${name.replace(' ', '_')}")
            val document = DocumentRef(uri = uri.toString(), displayName = name)
            AppLogger.i(IgcReplayControllerRuntime.TAG, "REPLY_LOAD Loaded synthetic IGC log with ${log.points.size} raw points (qnh=${log.metadata.qnhHpa})")
            prepareSession(
                log = log,
                selection = Selection(document)
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            failure = t
        }
    }
    failure?.let { t ->
        AppLogger.e(IgcReplayControllerRuntime.TAG, "Failed to load synthetic IGC log", t)
        _events.tryEmit(ReplayEvent.Failed(t))
        throw t
    }
}

internal fun IgcReplayControllerRuntime.setReplayModeRuntime(mode: ReplayMode, resetAfterSession: Boolean = false) {
    if (_session.value.status == SessionStatus.PLAYING) {
        AppLogger.w(IgcReplayControllerRuntime.TAG, "Replay mode change ignored while playing")
        return
    }
    if (simConfig.mode == mode) {
        resetModeAfterSession = resetAfterSession
        return
    }
    simConfig = simConfig.copy(mode = mode)
    sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
    resetModeAfterSession = resetAfterSession
    AppLogger.i(IgcReplayControllerRuntime.TAG, "Replay mode set to ${mode.name} (resetAfterSession=$resetAfterSession)")
}

internal fun IgcReplayControllerRuntime.getReplayModeRuntime(): ReplayMode = simConfig.mode

internal fun IgcReplayControllerRuntime.getReplayCadenceRuntime(): ReplayCadenceProfile = ReplayCadenceProfile(
    referenceStepMs = simConfig.referenceStepMs,
    gpsStepMs = simConfig.gpsStepMs
)

internal fun IgcReplayControllerRuntime.getReplayBaroStepMsRuntime(): Long = simConfig.baroStepMs

internal fun IgcReplayControllerRuntime.getReplayNoiseProfileRuntime(): ReplayNoiseProfile = ReplayNoiseProfile(
    pressureNoiseSigmaHpa = simConfig.pressureNoiseSigmaHpa,
    gpsAltitudeNoiseSigmaM = simConfig.gpsAltitudeNoiseSigmaM,
    jitterMs = simConfig.jitterMs
)

internal fun IgcReplayControllerRuntime.getReplayGpsAccuracyMetersRuntime(): Float = simConfig.gpsAccuracyMeters

internal fun IgcReplayControllerRuntime.getReplayInterpolationRuntime(): ReplayInterpolation = simConfig.interpolation

internal fun IgcReplayControllerRuntime.setReplayCadenceRuntime(profile: ReplayCadenceProfile) {
    if (_session.value.status == SessionStatus.PLAYING) {
        AppLogger.w(IgcReplayControllerRuntime.TAG, "Replay cadence change ignored while playing")
        return
    }
    val referenceStepMs = profile.referenceStepMs.coerceAtLeast(1L)
    val gpsStepMs = profile.gpsStepMs.coerceAtLeast(0L)
    if (simConfig.referenceStepMs == referenceStepMs && simConfig.gpsStepMs == gpsStepMs) return
    simConfig = simConfig.copy(
        referenceStepMs = referenceStepMs,
        gpsStepMs = gpsStepMs
    )
    sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
    AppLogger.i(IgcReplayControllerRuntime.TAG, "Replay cadence set referenceStepMs=$referenceStepMs gpsStepMs=$gpsStepMs")
}

internal fun IgcReplayControllerRuntime.setReplayBaroStepMsRuntime(stepMs: Long) {
    if (_session.value.status == SessionStatus.PLAYING) {
        AppLogger.w(IgcReplayControllerRuntime.TAG, "Replay baro step change ignored while playing")
        return
    }
    val clamped = stepMs.coerceAtLeast(1L)
    if (simConfig.baroStepMs == clamped) return
    simConfig = simConfig.copy(baroStepMs = clamped)
    sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
    AppLogger.i(IgcReplayControllerRuntime.TAG, "Replay baro step set baroStepMs=$clamped")
}

internal fun IgcReplayControllerRuntime.setReplayNoiseProfileRuntime(profile: ReplayNoiseProfile) {
    if (_session.value.status == SessionStatus.PLAYING) {
        AppLogger.w(IgcReplayControllerRuntime.TAG, "Replay noise profile change ignored while playing")
        return
    }
    val jitterMs = profile.jitterMs.coerceAtLeast(0L)
    val pressureSigma = profile.pressureNoiseSigmaHpa.coerceAtLeast(0.0)
    val gpsSigma = profile.gpsAltitudeNoiseSigmaM.coerceAtLeast(0.0)
    if (simConfig.pressureNoiseSigmaHpa == pressureSigma &&
        simConfig.gpsAltitudeNoiseSigmaM == gpsSigma &&
        simConfig.jitterMs == jitterMs
    ) return
    simConfig = simConfig.copy(
        pressureNoiseSigmaHpa = pressureSigma,
        gpsAltitudeNoiseSigmaM = gpsSigma,
        jitterMs = jitterMs
    )
    sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
    AppLogger.i(
        IgcReplayControllerRuntime.TAG,
        "Replay noise profile set pressureSigma=$pressureSigma gpsSigma=$gpsSigma jitterMs=$jitterMs"
    )
}

internal fun IgcReplayControllerRuntime.setReplayGpsAccuracyMetersRuntime(accuracyMeters: Float) {
    if (_session.value.status == SessionStatus.PLAYING) {
        AppLogger.w(IgcReplayControllerRuntime.TAG, "Replay GPS accuracy change ignored while playing")
        return
    }
    val clamped = accuracyMeters.coerceIn(
        IgcReplayControllerRuntime.MIN_GPS_ACCURACY_M,
        IgcReplayControllerRuntime.MAX_GPS_ACCURACY_M
    )
    if (simConfig.gpsAccuracyMeters == clamped) return
    simConfig = simConfig.copy(gpsAccuracyMeters = clamped)
    sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
    AppLogger.i(IgcReplayControllerRuntime.TAG, "Replay GPS accuracy set accuracyMeters=$clamped")
}

internal fun IgcReplayControllerRuntime.setReplayInterpolationRuntime(interpolation: ReplayInterpolation) {
    if (_session.value.status == SessionStatus.PLAYING) {
        AppLogger.w(IgcReplayControllerRuntime.TAG, "Replay interpolation change ignored while playing")
        return
    }
    if (simConfig.interpolation == interpolation) return
    simConfig = simConfig.copy(interpolation = interpolation)
    sampleEmitter = ReplaySampleEmitter(replaySensorSource, replayAirspeedRepository, simConfig)
    AppLogger.i(IgcReplayControllerRuntime.TAG, "Replay interpolation set to ${interpolation.name}")
}

internal fun IgcReplayControllerRuntime.setAutoStopAfterFinishRuntime(enabled: Boolean) {
    autoStopAfterFinish = enabled
}

internal fun IgcReplayControllerRuntime.isAutoStopAfterFinishEnabledRuntime(): Boolean = autoStopAfterFinish

internal fun IgcReplayControllerRuntime.getInterpolatedReplayHeadingDegRuntime(timestampMs: Long): Double? {
    if (simConfig.interpolation != ReplayInterpolation.CATMULL_ROM_RUNTIME) return null
    val session = _session.value
    if (session.selection == null) return null
    val start = session.startTimestampMillis
    val end = start + session.durationMillis
    if (end <= start) return null
    val clamped = timestampMs.coerceIn(start, end)
    val interpolator = uiRuntimeInterpolator ?: return null
    val fix = synchronized(uiInterpolatorLock) {
        interpolator.interpolate(clamped)
    } ?: return null
    return fix.movement.bearingDeg.toDouble()
}

internal fun IgcReplayControllerRuntime.getInterpolatedReplayPoseRuntime(timestampMs: Long): ReplayDisplayPose? {
    if (simConfig.interpolation != ReplayInterpolation.CATMULL_ROM_RUNTIME) return null
    val session = _session.value
    if (session.selection == null) return null
    val start = session.startTimestampMillis
    val end = start + session.durationMillis
    if (end <= start) return null
    val clamped = timestampMs.coerceIn(start, end)
    val interpolator = uiRuntimeInterpolator ?: return null
    val fix = synchronized(uiInterpolatorLock) {
        interpolator.interpolate(clamped)
    } ?: return null
    return ReplayDisplayPose(
        latitude = fix.point.latitude,
        longitude = fix.point.longitude,
        timestampMillis = fix.point.timestampMillis,
        bearingDeg = fix.movement.bearingDeg.toDouble(),
        speedMs = fix.movement.speedMs
    )
}
