package com.example.xcpro.adsb

import com.example.xcpro.adsb.domain.AdsbNetworkAvailabilityPort
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class FakeTokenRepository(
    private var token: String? = null,
    private val hasCredentials: Boolean = false,
    private val fixedState: OpenSkyTokenAccessState? = null
) : OpenSkyTokenRepository {
    override suspend fun getTokenAccessState(): OpenSkyTokenAccessState {
        val fixed = fixedState
        if (fixed != null) return fixed
        val currentToken = token
        return when {
            !currentToken.isNullOrBlank() -> OpenSkyTokenAccessState.Available(currentToken)
            hasCredentials -> OpenSkyTokenAccessState.CredentialsRejected("test")
            else -> OpenSkyTokenAccessState.NoCredentials
        }
    }

    override suspend fun getValidTokenOrNull(): String? =
        (getTokenAccessState() as? OpenSkyTokenAccessState.Available)?.token

    override fun hasCredentials(): Boolean = hasCredentials || !token.isNullOrBlank()

    override fun invalidate() {
        token = null
    }
}

internal class CapturingBboxProvider : AdsbProviderClient {
    val capturedBboxes = mutableListOf<BBox>()

    override suspend fun fetchStates(bbox: BBox, auth: AdsbAuth?): ProviderResult {
        capturedBboxes += bbox
        return ProviderResult.Success(
            response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
            httpCode = 200,
            remainingCredits = null
        )
    }
}

internal class FakeNetworkAvailabilityPort(
    initialOnline: Boolean = true
) : AdsbNetworkAvailabilityPort {
    private val _isOnline = MutableStateFlow(initialOnline)
    override val isOnline: StateFlow<Boolean> = _isOnline

    fun setOnline(online: Boolean) {
        _isOnline.value = online
    }
}

internal class ThrowingOnceNetworkAvailabilityPort : AdsbNetworkAvailabilityPort {
    private val delegate = MutableStateFlow(true)
    private var shouldThrow = true

    override val isOnline: StateFlow<Boolean>
        get() {
            if (shouldThrow) {
                shouldThrow = false
                throw IllegalStateException("Injected network availability failure")
            }
            return delegate
        }

    override fun currentOnlineState(): Boolean = true
}

internal class DelayedSuccessProvider(
    private val delayMs: Long
) : AdsbProviderClient {
    var callCount: Int = 0
        private set

    override suspend fun fetchStates(bbox: BBox, auth: AdsbAuth?): ProviderResult {
        callCount += 1
        delay(delayMs)
        return ProviderResult.Success(
            response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
            httpCode = 200,
            remainingCredits = null
        )
    }
}

internal class ThrowThenSuccessProvider : AdsbProviderClient {
    var callCount: Int = 0
        private set

    override suspend fun fetchStates(bbox: BBox, auth: AdsbAuth?): ProviderResult {
        callCount += 1
        if (callCount == 1) {
            throw IllegalStateException("Injected failure")
        }
        return ProviderResult.Success(
            response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
            httpCode = 200,
            remainingCredits = null
        )
    }
}

internal class SequenceProvider(
    responses: List<ProviderResult>
) : AdsbProviderClient {
    private val queue = responses.toMutableList()
    var callCount: Int = 0
        private set

    override suspend fun fetchStates(bbox: BBox, auth: AdsbAuth?): ProviderResult {
        callCount += 1
        if (queue.isEmpty()) {
            return ProviderResult.Success(
                response = OpenSkyResponse(timeSec = null, states = emptyList()),
                httpCode = 200,
                remainingCredits = null
            )
        }
        return queue.removeAt(0)
    }
}

internal class FakeEmergencyAudioSettingsPort(
    enabled: Boolean,
    cooldownMs: Long
) : AdsbEmergencyAudioSettingsPort {
    private val _enabled = MutableStateFlow(enabled)
    private val _cooldownMs = MutableStateFlow(cooldownMs)
    override val emergencyAudioEnabledFlow: StateFlow<Boolean> = _enabled
    override val emergencyAudioCooldownMsFlow: StateFlow<Long> = _cooldownMs

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
    }
}

internal class FakeEmergencyAudioRolloutPort(
    masterEnabled: Boolean,
    shadowModeEnabled: Boolean
) : AdsbEmergencyAudioRolloutPort {
    private val _masterEnabled = MutableStateFlow(masterEnabled)
    private val _shadowModeEnabled = MutableStateFlow(shadowModeEnabled)
    private val _rollbackLatched = MutableStateFlow(false)
    private val _rollbackReason = MutableStateFlow<String?>(null)
    override val emergencyAudioMasterEnabledFlow: StateFlow<Boolean> = _masterEnabled
    override val emergencyAudioShadowModeFlow: StateFlow<Boolean> = _shadowModeEnabled
    override val emergencyAudioRollbackLatchedFlow: StateFlow<Boolean> = _rollbackLatched
    override val emergencyAudioRollbackReasonFlow: StateFlow<String?> = _rollbackReason

    fun setMasterEnabled(enabled: Boolean) {
        _masterEnabled.value = enabled
    }

    fun setShadowModeEnabled(enabled: Boolean) {
        _shadowModeEnabled.value = enabled
    }

    override suspend fun latchEmergencyAudioRollback(reason: String) {
        _rollbackLatched.value = true
        _rollbackReason.value = reason
    }

    override suspend fun clearEmergencyAudioRollback() {
        _rollbackLatched.value = false
        _rollbackReason.value = null
    }
}

internal class FakeEmergencyAudioOutputPort(
    private val throwOnPlay: Boolean = false
) : AdsbEmergencyAudioOutputPort {
    val events = mutableListOf<EmergencyOutputEvent>()

    override fun playEmergencyAlert(triggerMonoMs: Long, emergencyTargetId: String?) {
        events += EmergencyOutputEvent(
            triggerMonoMs = triggerMonoMs,
            emergencyTargetId = emergencyTargetId
        )
        if (throwOnPlay) {
            throw IllegalStateException("Injected emergency audio output failure")
        }
    }
}

data class EmergencyOutputEvent(
    val triggerMonoMs: Long,
    val emergencyTargetId: String?
)

internal fun state(
    icao24: String,
    latitude: Double,
    longitude: Double,
    altitudeM: Double?,
    speedMps: Double?,
    positionSource: Int? = 0,
    trueTrackDeg: Double? = 180.0,
    timePositionSec: Long = 1_710_000_000L,
    lastContactSec: Long = 1_710_000_001L
): OpenSkyStateVector = OpenSkyStateVector(
    icao24 = icao24,
    callsign = icao24.uppercase(),
    timePositionSec = timePositionSec,
    lastContactSec = lastContactSec,
    longitude = longitude,
    latitude = latitude,
    baroAltitudeM = altitudeM,
    velocityMps = speedMps,
    trueTrackDeg = trueTrackDeg,
    verticalRateMps = 0.0,
    geoAltitudeM = null,
    positionSource = positionSource,
    category = 2
)
