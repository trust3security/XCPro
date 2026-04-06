package com.example.xcpro.adsb

import com.example.xcpro.adsb.domain.AdsbNetworkAvailabilityPort
import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow


interface AdsbTrafficRepository {
    val targets: StateFlow<List<AdsbTrafficUiModel>>
    val snapshot: StateFlow<AdsbTrafficSnapshot>
    val isEnabled: StateFlow<Boolean>

    fun setEnabled(enabled: Boolean)
    fun clearTargets()
    fun updateCenter(latitude: Double, longitude: Double)
    fun updateOwnshipOrigin(latitude: Double, longitude: Double)
    fun updateOwnshipMotion(trackDeg: Double?, speedMps: Double?)
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
    emergencyAudioSettingsPort: AdsbEmergencyAudioSettingsPort,
    emergencyAudioRolloutPort: AdsbEmergencyAudioRolloutPort? = null,
    emergencyAudioOutputPort: AdsbEmergencyAudioOutputPort,
    emergencyAudioFeatureFlags: AdsbEmergencyAudioFeatureFlags
) : AdsbTrafficRepository {
    // AI-NOTE: Production ADS-B wiring is always explicit. Disabled/test fallback
    // collaborators belong in test support, not as silent main-source defaults.

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

    override fun updateOwnshipMotion(trackDeg: Double?, speedMps: Double?) =
        runtime.updateOwnshipMotion(trackDeg = trackDeg, speedMps = speedMps)

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
}
