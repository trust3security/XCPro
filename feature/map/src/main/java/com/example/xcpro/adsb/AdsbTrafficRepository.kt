package com.example.xcpro.adsb

import com.example.xcpro.adsb.domain.AdsbNetworkAvailabilityPort
import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.core.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.random.Random


interface AdsbTrafficRepository {
    val targets: StateFlow<List<AdsbTrafficUiModel>>
    val snapshot: StateFlow<AdsbTrafficSnapshot>
    val isEnabled: StateFlow<Boolean>

    fun setEnabled(enabled: Boolean)
    fun clearTargets()
    fun updateCenter(latitude: Double, longitude: Double)
    fun updateOwnshipOrigin(latitude: Double, longitude: Double)
    fun clearOwnshipOrigin()
    fun updateOwnshipAltitudeMeters(altitudeMeters: Double?)
    fun updateOwnshipCirclingContext(
        isCircling: Boolean,
        circlingFeatureEnabled: Boolean
    )
    fun updateDisplayFilters(
        maxDistanceKm: Int,
        verticalAboveMeters: Double,
        verticalBelowMeters: Double
    )
    fun reconnectNow()
    fun start()
    fun stop()
}

@Singleton
class AdsbTrafficRepositoryImpl @Inject constructor(
    providerClient: AdsbProviderClient,
    tokenRepository: OpenSkyTokenRepository,
    clock: Clock,
    @IoDispatcher dispatcher: CoroutineDispatcher,
    networkAvailabilityPort: AdsbNetworkAvailabilityPort,
    emergencyAudioSettingsPort: AdsbEmergencyAudioSettingsPort =
        DisabledEmergencyAudioSettingsPort(),
    emergencyAudioRolloutPort: AdsbEmergencyAudioRolloutPort? = null,
    emergencyAudioOutputPort: AdsbEmergencyAudioOutputPort =
        NoOpAdsbEmergencyAudioOutputPort,
    emergencyAudioFeatureFlags: AdsbEmergencyAudioFeatureFlags =
        AdsbEmergencyAudioFeatureFlags()
) : AdsbTrafficRepository {

    internal constructor(
        providerClient: AdsbProviderClient,
        tokenRepository: OpenSkyTokenRepository,
        clock: Clock,
        dispatcher: CoroutineDispatcher
    ) : this(
        providerClient = providerClient,
        tokenRepository = tokenRepository,
        clock = clock,
        dispatcher = dispatcher,
        networkAvailabilityPort = AlwaysOnlineNetworkAvailabilityPort,
        emergencyAudioSettingsPort = DisabledEmergencyAudioSettingsPort(),
        emergencyAudioRolloutPort = null,
        emergencyAudioOutputPort = NoOpAdsbEmergencyAudioOutputPort,
        emergencyAudioFeatureFlags = AdsbEmergencyAudioFeatureFlags()
    )

    private val runtime = AdsbTrafficRepositoryRuntime(
        providerClient = providerClient,
        tokenRepository = tokenRepository,
        clock = clock,
        dispatcher = dispatcher,
        networkAvailabilityPort = networkAvailabilityPort,
        emergencyAudioSettingsPort = emergencyAudioSettingsPort,
        emergencyAudioRolloutPort = emergencyAudioRolloutPort,
        emergencyAudioOutputPort = emergencyAudioOutputPort,
        emergencyAudioFeatureFlags = emergencyAudioFeatureFlags
    )

    override val targets: StateFlow<List<AdsbTrafficUiModel>>
        get() = runtime.targets
    override val snapshot: StateFlow<AdsbTrafficSnapshot>
        get() = runtime.snapshot
    override val isEnabled: StateFlow<Boolean>
        get() = runtime.isEnabled

    override fun setEnabled(enabled: Boolean) = runtime.setEnabled(enabled)

    override fun clearTargets() = runtime.clearTargets()

    override fun updateCenter(latitude: Double, longitude: Double) =
        runtime.updateCenter(latitude = latitude, longitude = longitude)

    override fun updateOwnshipOrigin(latitude: Double, longitude: Double) =
        runtime.updateOwnshipOrigin(latitude = latitude, longitude = longitude)

    override fun clearOwnshipOrigin() = runtime.clearOwnshipOrigin()

    override fun updateOwnshipAltitudeMeters(altitudeMeters: Double?) =
        runtime.updateOwnshipAltitudeMeters(altitudeMeters)

    override fun updateOwnshipCirclingContext(
        isCircling: Boolean,
        circlingFeatureEnabled: Boolean
    ) = runtime.updateOwnshipCirclingContext(
        isCircling = isCircling,
        circlingFeatureEnabled = circlingFeatureEnabled
    )

    override fun updateDisplayFilters(
        maxDistanceKm: Int,
        verticalAboveMeters: Double,
        verticalBelowMeters: Double
    ) = runtime.updateDisplayFilters(
        maxDistanceKm = maxDistanceKm,
        verticalAboveMeters = verticalAboveMeters,
        verticalBelowMeters = verticalBelowMeters
    )

    override fun reconnectNow() = runtime.reconnectNow()

    override fun start() = runtime.start()

    override fun stop() = runtime.stop()

    private class DisabledEmergencyAudioSettingsPort : AdsbEmergencyAudioSettingsPort {
        override val emergencyAudioEnabledFlow: StateFlow<Boolean> =
            MutableStateFlow(false).asStateFlow()
        override val emergencyAudioCooldownMsFlow: StateFlow<Long> =
            MutableStateFlow(ADSB_EMERGENCY_AUDIO_DEFAULT_COOLDOWN_MS).asStateFlow()
    }

    private companion object {
        private val AlwaysOnlineNetworkAvailabilityPort = object : AdsbNetworkAvailabilityPort {
            override val isOnline: StateFlow<Boolean> =
                MutableStateFlow(true).asStateFlow()
        }
    }
}
