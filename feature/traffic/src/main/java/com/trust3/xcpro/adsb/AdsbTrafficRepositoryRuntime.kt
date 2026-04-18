package com.trust3.xcpro.adsb

import com.trust3.xcpro.adsb.domain.AdsbNetworkAvailabilityPort
import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.core.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.random.Random


@OptIn(ExperimentalCoroutinesApi::class)
internal class AdsbTrafficRepositoryRuntime(
    internal val providerClient: AdsbProviderClient,
    internal val tokenRepository: OpenSkyTokenRepository,
    internal val clock: Clock,
    @IoDispatcher dispatcher: CoroutineDispatcher,
    internal val networkAvailabilityPort: AdsbNetworkAvailabilityPort,
    internal val emergencyAudioSettingsPort: AdsbEmergencyAudioSettingsPort,
    internal val emergencyAudioRolloutPort: AdsbEmergencyAudioRolloutPort? = null,
    internal val emergencyAudioOutputPort: AdsbEmergencyAudioOutputPort,
    emergencyAudioFeatureFlags: AdsbEmergencyAudioFeatureFlags
) : AdsbTrafficRepository {

    internal val singleWriterDispatcher: CoroutineDispatcher = dispatcher.limitedParallelism(1)
    internal val scope = CoroutineScope(SupervisorJob() + singleWriterDispatcher)
    internal val store = AdsbTrafficStore()
    internal val emergencyAudioAlertFsm = AdsbEmergencyAudioAlertFsm()
    internal val emergencyAudioKpiAccumulator = AdsbEmergencyAudioKpiAccumulator()

    internal val _targets = MutableStateFlow<List<AdsbTrafficUiModel>>(emptyList())
    override val targets: StateFlow<List<AdsbTrafficUiModel>> = _targets.asStateFlow()

    internal val _snapshot = MutableStateFlow(
        AdsbTrafficSnapshot(
            targets = emptyList(),
            connectionState = AdsbConnectionState.Disabled,
            authMode = AdsbAuthMode.Anonymous,
            centerLat = null,
            centerLon = null,
            usesOwnshipReference = false,
            receiveRadiusKm = ADSB_MAX_DISTANCE_DEFAULT_KM,
            fetchedCount = 0,
            withinRadiusCount = 0,
            withinVerticalCount = 0,
            filteredByVerticalCount = 0,
            cappedCount = 0,
            displayedCount = 0,
            lastHttpStatus = null,
            remainingCredits = null,
            lastPollMonoMs = null,
            lastSuccessMonoMs = null,
            lastError = null,
            lastNetworkFailureKind = null,
            consecutiveFailureCount = 0,
            nextRetryMonoMs = null,
            lastFailureMonoMs = null
        )
    )
    override val snapshot: StateFlow<AdsbTrafficSnapshot> = _snapshot.asStateFlow()

    internal val _isEnabled = MutableStateFlow(false)
    override val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    @Volatile
    internal var center: Center? = null
    internal val centerState = MutableStateFlow<Center?>(null)

    @Volatile
    internal var ownshipOrigin: Center? = null

    @Volatile
    internal var loopJob: Job? = null
    internal val loopJobLock = Any()
    internal val stateTransitionMutex = Mutex()

    @Volatile
    internal var connectionState: AdsbConnectionState = AdsbConnectionState.Disabled

    @Volatile
    internal var lastError: String? = null

    @Volatile
    internal var lastNetworkFailureKind: AdsbNetworkFailureKind? = null

    @Volatile
    internal var lastHttpStatus: Int? = null

    @Volatile
    internal var remainingCredits: Int? = null

    @Volatile
    internal var authMode: AdsbAuthMode = AdsbAuthMode.Anonymous

    @Volatile
    internal var lastPollMonoMs: Long? = null

    @Volatile
    internal var lastSuccessMonoMs: Long? = null
    @Volatile
    internal var fetchedCount: Int = 0

    @Volatile
    internal var withinRadiusCount: Int = 0
    @Volatile
    internal var withinVerticalCount: Int = 0
    @Volatile
    internal var filteredByVerticalCount: Int = 0
    @Volatile
    internal var cappedCount: Int = 0

    @Volatile
    internal var ownshipAltitudeMeters: Double? = null
    @Volatile
    internal var ownshipTrackDeg: Double? = null
    @Volatile
    internal var ownshipSpeedMps: Double? = null
    @Volatile
    internal var ownshipCircling: Boolean = false
    @Volatile
    internal var ownshipCirclingFeatureEnabled: Boolean = false
    @Volatile
    internal var lastOwnshipAltitudeReselectMonoMs: Long = Long.MIN_VALUE
    @Volatile
    internal var lastOwnshipAltitudeReselectMeters: Double? = null
    @Volatile
    internal var receiveRadiusKm: Int = ADSB_MAX_DISTANCE_DEFAULT_KM
    @Volatile
    internal var verticalFilterAboveMeters: Double = ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
    @Volatile
    internal var verticalFilterBelowMeters: Double = ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
    internal var consecutiveEmptyPolls: Int = 0
    internal val pollingHealthPolicy = AdsbPollingHealthPolicy(
        circuitBreakerFailureThreshold = CIRCUIT_BREAKER_FAILURE_THRESHOLD,
        circuitBreakerOpenWindowMs = CIRCUIT_BREAKER_OPEN_WINDOW_MS
    )
    internal var lastPolledCenter: Center? = null
    internal val requestTimesMonoMs = ArrayDeque<Long>()
    internal var networkOnline: Boolean = true
    internal var networkOfflineTransitionCount: Int = 0
    internal var networkOnlineTransitionCount: Int = 0
    internal var lastNetworkTransitionMonoMs: Long? = null
    @Volatile
    internal var emergencyAudioSettings = AdsbEmergencyAudioSettings()
    @Volatile
    internal var emergencyAudioMasterConfigured: Boolean = emergencyAudioFeatureFlags.emergencyAudioEnabled
    @Volatile
    internal var emergencyAudioShadowModeConfigured: Boolean = emergencyAudioFeatureFlags.emergencyAudioShadowMode
    @Volatile
    internal var emergencyAudioRollbackLatched: Boolean = false
    @Volatile
    internal var emergencyAudioRollbackReason: String? = null
    @Volatile
    internal var ownshipReferenceLastUpdateMonoMs: Long? = null
    @Volatile
    internal var emergencyAudioCandidateId: Icao24? = null

    init {
        observeEmergencyAudioSettings()
        observeEmergencyAudioRollout()
    }

    override fun start() { setEnabled(true) }

    override fun stop() { setEnabled(false) }

    override fun setEnabled(enabled: Boolean) {
        scope.launch { applySetEnabled(enabled) }
    }

    override fun clearTargets() {
        scope.launch { applyClearTargets() }
    }

    override fun updateCenter(latitude: Double, longitude: Double) {
        scope.launch { applyUpdateCenter(latitude = latitude, longitude = longitude) }
    }

    override fun updateOwnshipOrigin(latitude: Double, longitude: Double) {
        scope.launch { applyUpdateOwnshipOrigin(latitude = latitude, longitude = longitude) }
    }

    override fun updateOwnshipMotion(trackDeg: Double?, speedMps: Double?) {
        scope.launch { applyUpdateOwnshipMotion(trackDeg = trackDeg, speedMps = speedMps) }
    }

    override fun clearOwnshipOrigin() {
        scope.launch { applyClearOwnshipOrigin() }
    }

    override fun updateOwnshipAltitudeMeters(altitudeMeters: Double?) {
        scope.launch { applyUpdateOwnshipAltitudeMeters(altitudeMeters = altitudeMeters) }
    }

    override fun updateOwnshipCirclingContext(
        isCircling: Boolean,
        circlingFeatureEnabled: Boolean
    ) {
        scope.launch {
            applyUpdateOwnshipCirclingContext(
                isCircling = isCircling,
                circlingFeatureEnabled = circlingFeatureEnabled
            )
        }
    }

    override fun updateDisplayFilters(
        maxDistanceKm: Int,
        verticalAboveMeters: Double,
        verticalBelowMeters: Double
    ) {
        scope.launch {
            applyUpdateDisplayFilters(
                maxDistanceKm = maxDistanceKm,
                verticalAboveMeters = verticalAboveMeters,
                verticalBelowMeters = verticalBelowMeters
            )
        }
    }

    override fun reconnectNow() {
        scope.launch { applyReconnectNow() }
    }

    internal suspend fun fetchWithAuthRetry(bbox: BBox): ProviderResult {
        return try {
            recordRequest(clock.nowMonoMs())
            val tokenState = tokenRepository.getTokenAccessState()
            val token = (tokenState as? OpenSkyTokenAccessState.Available)?.token
            authMode = tokenState.toAuthMode()
            val first = providerClient.fetchStates(bbox, token?.let(::AdsbAuth))
            if (first is ProviderResult.HttpError && first.code == 401 && !token.isNullOrBlank()) {
                tokenRepository.invalidate()
                val refreshedTokenState = tokenRepository.getTokenAccessState()
                val refreshedToken = (refreshedTokenState as? OpenSkyTokenAccessState.Available)?.token
                authMode = refreshedTokenState.toAuthMode()
                recordRequest(clock.nowMonoMs())
                return providerClient.fetchStates(bbox, refreshedToken?.let(::AdsbAuth))
            }
            first
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProviderResult.NetworkError(
                kind = AdsbNetworkFailureKind.UNKNOWN,
                message = e::class.java.simpleName.ifBlank { "ProviderFailure" }
            )
        }
    }

    private fun OpenSkyTokenAccessState.toAuthMode(): AdsbAuthMode = when (this) {
        is OpenSkyTokenAccessState.Available -> AdsbAuthMode.Authenticated
        OpenSkyTokenAccessState.NoCredentials -> AdsbAuthMode.Anonymous
        is OpenSkyTokenAccessState.CredentialsRejected -> AdsbAuthMode.AuthFailed
        is OpenSkyTokenAccessState.TransientFailure -> AdsbAuthMode.Anonymous
    }

    internal fun OpenSkyStateVector.toTarget(
        responseTimeEpochSec: Long?,
        receivedMonoMs: Long
    ): AdsbTarget? {
        val id = Icao24.from(icao24) ?: return null
        val latitude = latitude ?: return null
        val longitude = longitude ?: return null
        if (!AdsbGeoMath.isValidCoordinate(latitude, longitude)) return null
        if (positionSource == POSITION_SOURCE_FLARM) return null
        val altitudeMeters = altitudeM?.takeIf { it.isFinite() } ?: return null
        val speedMetersPerSecond = velocityMps?.takeIf { it.isFinite() } ?: return null
        if (altitudeMeters <= MIN_AIRBORNE_ALTITUDE_M) return null
        if (speedMetersPerSecond <= MIN_AIRBORNE_SPEED_MPS) return null
        val sanitizedResponseTimeEpochSec = sanitizeProviderEpochSec(responseTimeEpochSec)
        val sanitizedPositionTimeEpochSec = sanitizeProviderEpochSec(timePositionSec)
        val sanitizedLastContactEpochSec = sanitizeProviderEpochSec(lastContactSec)
        val effectivePositionEpochSec = sanitizedPositionTimeEpochSec ?: sanitizedResponseTimeEpochSec
        val positionFreshnessSource = when {
            sanitizedPositionTimeEpochSec != null -> AdsbPositionFreshnessSource.POSITION_TIME
            sanitizedResponseTimeEpochSec != null -> AdsbPositionFreshnessSource.RESPONSE_TIME
            else -> AdsbPositionFreshnessSource.RECEIVED_MONO_FALLBACK
        }
        return AdsbTarget(
            id = id,
            callsign = callsign?.takeIf { it.isNotBlank() },
            lat = latitude,
            lon = longitude,
            altitudeM = altitudeMeters,
            speedMps = speedMetersPerSecond,
            trackDeg = trueTrackDeg,
            climbMps = verticalRateMps,
            positionSource = positionSource,
            category = category,
            lastContactEpochSec = sanitizedLastContactEpochSec,
            receivedMonoMs = receivedMonoMs,
            contactReceivedMonoMs = receivedMonoMs,
            positionTimestampEpochSec = sanitizedPositionTimeEpochSec,
            responseTimestampEpochSec = sanitizedResponseTimeEpochSec,
            effectivePositionEpochSec = effectivePositionEpochSec,
            positionAgeAtReceiptSec = providerAgeAtReceiptSec(
                responseTimeEpochSec = sanitizedResponseTimeEpochSec,
                sampleTimeEpochSec = effectivePositionEpochSec
            ) ?: 0,
            contactAgeAtReceiptSec = providerAgeAtReceiptSec(
                responseTimeEpochSec = sanitizedResponseTimeEpochSec,
                sampleTimeEpochSec = sanitizedLastContactEpochSec
            ),
            positionFreshnessSource = positionFreshnessSource
        )
    }

    private fun sanitizeProviderEpochSec(value: Long?): Long? =
        value?.takeIf { it > 0L }

    private fun providerAgeAtReceiptSec(
        responseTimeEpochSec: Long?,
        sampleTimeEpochSec: Long?
    ): Int? {
        if (responseTimeEpochSec == null || sampleTimeEpochSec == null) return null
        if (responseTimeEpochSec < sampleTimeEpochSec) return null
        return (responseTimeEpochSec - sampleTimeEpochSec).toInt().coerceAtLeast(0)
    }

    internal fun withJitter(backoffMs: Long): Long {
        val random = Random(clock.nowMonoMs())
        val factor = 0.8 + (random.nextDouble() * 0.4)
        return (backoffMs * factor).toLong().coerceAtLeast(1_000L)
    }

    internal fun networkFailureRetryFloorMs(kind: AdsbNetworkFailureKind): Long = when (kind) {
        AdsbNetworkFailureKind.DNS,
        AdsbNetworkFailureKind.NO_ROUTE -> RETRY_FLOOR_OFFLINE_MS
        AdsbNetworkFailureKind.TIMEOUT -> RETRY_FLOOR_TIMEOUT_MS
        AdsbNetworkFailureKind.CONNECT -> RETRY_FLOOR_CONNECT_MS
        AdsbNetworkFailureKind.TLS,
        AdsbNetworkFailureKind.MALFORMED_RESPONSE -> RETRY_FLOOR_PROTOCOL_MS
        AdsbNetworkFailureKind.UNKNOWN -> RETRY_FLOOR_UNKNOWN_MS
    }

    internal fun ProviderResult.NetworkError.toDebugMessage(): String = when (kind) {
        AdsbNetworkFailureKind.DNS -> "DNS resolution failed"
        AdsbNetworkFailureKind.TIMEOUT -> "Socket timeout"
        AdsbNetworkFailureKind.CONNECT -> "Connect failed"
        AdsbNetworkFailureKind.NO_ROUTE -> "No route to host"
        AdsbNetworkFailureKind.TLS -> "TLS handshake failed"
        AdsbNetworkFailureKind.MALFORMED_RESPONSE -> "Malformed provider response"
        AdsbNetworkFailureKind.UNKNOWN -> message
    }

    internal data class Center(
        val latitude: Double,
        val longitude: Double
    )

    internal data class ReferencePoint(
        val latitude: Double,
        val longitude: Double,
        val altitudeMeters: Double?,
        val usesOwnshipReference: Boolean,
        val referenceSampleMonoMs: Long?,
        val ownshipTrackDeg: Double?,
        val ownshipSpeedMps: Double?
    )

    private companion object {
        internal const val TAG = "AdsbTrafficRepository"
        internal const val POSITION_SOURCE_FLARM = 3
        internal const val MIN_AIRBORNE_ALTITUDE_M = 30.48 // 100 ft
        internal const val MIN_AIRBORNE_SPEED_MPS = 20.5778 // 40 kt
        internal const val RETRY_FLOOR_UNKNOWN_MS = 2_000L
        internal const val RETRY_FLOOR_TIMEOUT_MS = 8_000L
        internal const val RETRY_FLOOR_CONNECT_MS = 10_000L
        internal const val RETRY_FLOOR_OFFLINE_MS = 15_000L
        internal const val RETRY_FLOOR_PROTOCOL_MS = 20_000L
        internal const val CIRCUIT_BREAKER_FAILURE_THRESHOLD = 3
        internal const val CIRCUIT_BREAKER_OPEN_WINDOW_MS = 30_000L
    }
}
