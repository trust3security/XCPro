package com.example.xcpro.livefollow.data.ownship

import com.example.xcpro.core.flight.calculations.ConfidenceLevel
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentity
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentityType
import com.example.xcpro.livefollow.model.LiveFollowConfidence
import com.example.xcpro.livefollow.model.LiveFollowValueQuality
import com.example.xcpro.livefollow.model.LiveFollowValueState
import com.example.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.example.xcpro.livefollow.model.LiveOwnshipSourceLabel
import com.example.xcpro.livefollow.state.LiveFollowRuntimeMode
import com.example.xcpro.sensors.CompleteFlightData
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class FlightDataLiveOwnshipSnapshotSource(
    scope: CoroutineScope,
    flightDataRepository: FlightDataRepository,
    ownFlarmHexFlow: Flow<String?>,
    ownIcaoHexFlow: Flow<String?>
) : LiveOwnshipSnapshotSource {
    private val mutableRuntimeMode = MutableStateFlow(
        toRuntimeMode(flightDataRepository.activeSource.value)
    )
    private val mutableSnapshot = MutableStateFlow<LiveOwnshipSnapshot?>(null)

    override val runtimeMode: StateFlow<LiveFollowRuntimeMode> = mutableRuntimeMode.asStateFlow()
    override val snapshot: StateFlow<LiveOwnshipSnapshot?> = mutableSnapshot.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flightDataRepository.activeSource
                .map(::toRuntimeMode)
                .collect { mode ->
                    mutableRuntimeMode.value = mode
                }
        }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(
                flightDataRepository.flightData,
                runtimeMode,
                ownFlarmHexFlow,
                ownIcaoHexFlow
            ) { flightData, mode, ownFlarmHex, ownIcaoHex ->
                flightData?.toLiveOwnshipSnapshot(
                    runtimeMode = mode,
                    ownFlarmHex = ownFlarmHex,
                    ownIcaoHex = ownIcaoHex
                )
            }.collect { ownshipSnapshot ->
                mutableSnapshot.value = ownshipSnapshot
            }
        }
    }
}

private fun toRuntimeMode(source: FlightDataRepository.Source): LiveFollowRuntimeMode {
    return when (source) {
        FlightDataRepository.Source.LIVE -> LiveFollowRuntimeMode.LIVE
        FlightDataRepository.Source.REPLAY -> LiveFollowRuntimeMode.REPLAY
    }
}

private fun CompleteFlightData.toLiveOwnshipSnapshot(
    runtimeMode: LiveFollowRuntimeMode,
    ownFlarmHex: String?,
    ownIcaoHex: String?
): LiveOwnshipSnapshot? {
    val gpsData = gps ?: return null
    if (!gpsData.position.isValid) return null

    val fixMonoMs = gpsData.monotonicTimestampMillis
    if (fixMonoMs <= 0L) return null

    return LiveOwnshipSnapshot(
        latitudeDeg = gpsData.latitude,
        longitudeDeg = gpsData.longitude,
        gpsAltitudeMslMeters = gpsData.altitude.value.takeFiniteOrNull(),
        pressureAltitudeMslMeters = baroAltitude.value.takeFiniteOrNull(),
        aglMeters = agl.value.takeFiniteOrNull()
            ?.takeIf { aglTimestampMonoMs > 0L },
        groundSpeedMs = gpsData.speed.value.takeFiniteOrNull(),
        trackDeg = gpsData.bearing.takeFiniteOrNull(),
        verticalSpeedMs = verticalSpeed.value.takeFiniteOrNull().takeIf { varioValid },
        fixMonoMs = fixMonoMs,
        fixWallMs = gpsData.timestamp.takeIf { it > 0L },
        positionQuality = positionQualityForAccuracy(
            latitudeDeg = gpsData.latitude,
            longitudeDeg = gpsData.longitude,
            accuracyMeters = gpsData.accuracy
        ),
        verticalQuality = verticalQuality(
            varioValid = varioValid,
            confidenceLevel = baroConfidence
        ),
        canonicalIdentity = ownshipCanonicalIdentity(
            ownFlarmHex = ownFlarmHex,
            ownIcaoHex = ownIcaoHex
        ),
        sourceLabel = runtimeMode.toOwnshipSourceLabel()
    )
}

private fun positionQualityForAccuracy(
    latitudeDeg: Double,
    longitudeDeg: Double,
    accuracyMeters: Float
): LiveFollowValueQuality {
    if (!latitudeDeg.isFinite() || !longitudeDeg.isFinite()) {
        return LiveFollowValueQuality(
            state = LiveFollowValueState.INVALID,
            confidence = LiveFollowConfidence.UNKNOWN
        )
    }

    return when {
        accuracyMeters <= 10f -> LiveFollowValueQuality(
            state = LiveFollowValueState.VALID,
            confidence = LiveFollowConfidence.HIGH
        )

        accuracyMeters <= 25f -> LiveFollowValueQuality(
            state = LiveFollowValueState.DEGRADED,
            confidence = LiveFollowConfidence.MEDIUM
        )

        accuracyMeters > 25f -> LiveFollowValueQuality(
            state = LiveFollowValueState.DEGRADED,
            confidence = LiveFollowConfidence.LOW
        )

        else -> LiveFollowValueQuality(
            state = LiveFollowValueState.INVALID,
            confidence = LiveFollowConfidence.UNKNOWN
        )
    }
}

private fun verticalQuality(
    varioValid: Boolean,
    confidenceLevel: ConfidenceLevel
): LiveFollowValueQuality {
    if (!varioValid) {
        return LiveFollowValueQuality(
            state = LiveFollowValueState.UNAVAILABLE,
            confidence = LiveFollowConfidence.UNKNOWN
        )
    }

    return when (confidenceLevel) {
        ConfidenceLevel.HIGH -> LiveFollowValueQuality(
            state = LiveFollowValueState.VALID,
            confidence = LiveFollowConfidence.HIGH
        )

        ConfidenceLevel.MEDIUM -> LiveFollowValueQuality(
            state = LiveFollowValueState.DEGRADED,
            confidence = LiveFollowConfidence.MEDIUM
        )

        ConfidenceLevel.LOW -> LiveFollowValueQuality(
            state = LiveFollowValueState.DEGRADED,
            confidence = LiveFollowConfidence.LOW
        )
    }
}

private fun ownshipCanonicalIdentity(
    ownFlarmHex: String?,
    ownIcaoHex: String?
): LiveFollowAircraftIdentity? {
    return LiveFollowAircraftIdentity.create(
        type = LiveFollowAircraftIdentityType.FLARM,
        rawValue = ownFlarmHex.orEmpty(),
        verified = true
    ) ?: LiveFollowAircraftIdentity.create(
        type = LiveFollowAircraftIdentityType.ICAO,
        rawValue = ownIcaoHex.orEmpty(),
        verified = true
    )
}

private fun LiveFollowRuntimeMode.toOwnshipSourceLabel(): LiveOwnshipSourceLabel {
    return when (this) {
        LiveFollowRuntimeMode.LIVE -> LiveOwnshipSourceLabel.LIVE_FLIGHT_RUNTIME
        LiveFollowRuntimeMode.REPLAY -> LiveOwnshipSourceLabel.REPLAY_RUNTIME
    }
}

private fun Double.takeFiniteOrNull(): Double? = takeIf { it.isFinite() }
