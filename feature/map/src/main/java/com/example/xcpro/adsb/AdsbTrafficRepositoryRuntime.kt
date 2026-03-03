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


internal class AdsbTrafficRepositoryRuntime(
    internal val providerClient: AdsbProviderClient,
    internal val tokenRepository: OpenSkyTokenRepository,
    internal val clock: Clock,
    @IoDispatcher dispatcher: CoroutineDispatcher,
    internal val networkAvailabilityPort: AdsbNetworkAvailabilityPort,
    internal val emergencyAudioSettingsPort: AdsbEmergencyAudioSettingsPort =
        DisabledEmergencyAudioSettingsPort(),
    internal val emergencyAudioOutputPort: AdsbEmergencyAudioOutputPort =
        NoOpAdsbEmergencyAudioOutputPort,
    internal val emergencyAudioFeatureFlags: AdsbEmergencyAudioFeatureFlags =
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
        emergencyAudioOutputPort = NoOpAdsbEmergencyAudioOutputPort,
        emergencyAudioFeatureFlags = AdsbEmergencyAudioFeatureFlags()
    )

    internal val scope = CoroutineScope(SupervisorJob() + dispatcher)
    internal val store = AdsbTrafficStore()
    internal val emergencyAudioAlertFsm = AdsbEmergencyAudioAlertFsm()

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

    init {
        observeEmergencyAudioSettings()
    }

    override fun start() {
        setEnabled(true)
    }

    override fun stop() {
        setEnabled(false)
    }

    override fun setEnabled(enabled: Boolean) {
        if (_isEnabled.value == enabled) {
            if (enabled) {
                scope.launch {
                    stateTransitionMutex.withLock {
                        ensureLoopRunning()
                    }
                }
            }
            return
        }
        _isEnabled.value = enabled
        scope.launch {
            stateTransitionMutex.withLock {
                if (_isEnabled.value) {
                    ensureLoopRunning()
                } else {
                    stopLoop(clearTargets = false)
                }
            }
        }
    }

    override fun clearTargets() {
        scope.launch {
            clearCachedTargets()
            publishSnapshot()
        }
    }

    override fun updateCenter(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return
        val updatedCenter = Center(latitude = latitude, longitude = longitude)
        if (center == updatedCenter) return
        center = updatedCenter
        centerState.value = updatedCenter
        if (_isEnabled.value) {
            publishFromStore(updatedCenter)
        } else {
            publishSnapshot()
        }
        if (_isEnabled.value) ensureLoopRunning()
    }

    override fun updateOwnshipOrigin(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return
        val updatedOrigin = Center(latitude = latitude, longitude = longitude)
        if (ownshipOrigin == updatedOrigin) return
        ownshipOrigin = updatedOrigin
        if (!_isEnabled.value) return
        center?.let { activeCenter -> publishFromStore(activeCenter) }
    }

    override fun clearOwnshipOrigin() {
        if (ownshipOrigin == null) return
        ownshipOrigin = null
        if (!_isEnabled.value) return
        center?.let { activeCenter -> publishFromStore(activeCenter) }
    }

    override fun updateOwnshipAltitudeMeters(altitudeMeters: Double?) {
        val normalizedAltitude = altitudeMeters?.takeIf { it.isFinite() }
        if (ownshipAltitudeMeters == normalizedAltitude) return
        ownshipAltitudeMeters = normalizedAltitude
        if (!_isEnabled.value) return
        val activeCenter = center ?: return
        val nowMonoMs = clock.nowMonoMs()
        if (!shouldReselectForOwnshipAltitude(
                nowMonoMs = nowMonoMs,
                nextOwnshipAltitudeMeters = normalizedAltitude
            )
        ) {
            return
        }
        publishFromStore(activeCenter, nowMonoMs)
        lastOwnshipAltitudeReselectMonoMs = nowMonoMs
        lastOwnshipAltitudeReselectMeters = normalizedAltitude
    }

    override fun updateDisplayFilters(
        maxDistanceKm: Int,
        verticalAboveMeters: Double,
        verticalBelowMeters: Double
    ) {
        val clampedDistanceKm = clampAdsbMaxDistanceKm(maxDistanceKm)
        val clampedAboveMeters = clampAdsbVerticalFilterMeters(verticalAboveMeters)
        val clampedBelowMeters = clampAdsbVerticalFilterMeters(verticalBelowMeters)
        val changed =
            receiveRadiusKm != clampedDistanceKm ||
                this.verticalFilterAboveMeters != clampedAboveMeters ||
                this.verticalFilterBelowMeters != clampedBelowMeters
        if (!changed) return
        receiveRadiusKm = clampedDistanceKm
        this.verticalFilterAboveMeters = clampedAboveMeters
        this.verticalFilterBelowMeters = clampedBelowMeters
        center?.let { activeCenter ->
            if (_isEnabled.value) {
                publishFromStore(activeCenter)
            } else {
                publishSnapshot()
            }
        } ?: publishSnapshot()
    }

    override fun reconnectNow() {
        if (!_isEnabled.value) return
        scope.launch {
            stateTransitionMutex.withLock {
                stopLoop(clearTargets = false)
                if (_isEnabled.value) {
                    ensureLoopRunning()
                } else {
                    connectionState = AdsbConnectionState.Disabled
                    publishSnapshot()
                }
            }
        }
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

    internal fun OpenSkyStateVector.toTarget(receivedMonoMs: Long): AdsbTarget? {
        val id = Icao24.from(icao24) ?: return null
        val latitude = latitude ?: return null
        val longitude = longitude ?: return null
        if (!AdsbGeoMath.isValidCoordinate(latitude, longitude)) return null
        if (positionSource == POSITION_SOURCE_FLARM) return null
        val altitudeMeters = altitudeM?.takeIf { it.isFinite() } ?: return null
        val speedMetersPerSecond = velocityMps?.takeIf { it.isFinite() } ?: return null
        if (altitudeMeters <= MIN_AIRBORNE_ALTITUDE_M) return null
        if (speedMetersPerSecond <= MIN_AIRBORNE_SPEED_MPS) return null
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
            lastContactEpochSec = lastContactSec,
            receivedMonoMs = receivedMonoMs
        )
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
        val usesOwnshipReference: Boolean
    )

    internal sealed interface CenterWaitState {
        data object Waiting : CenterWaitState
        data object Disabled : CenterWaitState
        data class Ready(val center: Center) : CenterWaitState
    }

    internal sealed interface NetworkWaitState {
        data object Offline : NetworkWaitState
        data object Disabled : NetworkWaitState
        data object Online : NetworkWaitState
    }

    private class DisabledEmergencyAudioSettingsPort : AdsbEmergencyAudioSettingsPort {
        override val emergencyAudioEnabledFlow: StateFlow<Boolean> =
            MutableStateFlow(false).asStateFlow()
        override val emergencyAudioCooldownMsFlow: StateFlow<Long> =
            MutableStateFlow(ADSB_EMERGENCY_AUDIO_DEFAULT_COOLDOWN_MS).asStateFlow()
    }

    private companion object {
        internal val AlwaysOnlineNetworkAvailabilityPort = object : AdsbNetworkAvailabilityPort {
            override val isOnline: StateFlow<Boolean> =
                MutableStateFlow(true).asStateFlow()
        }

        internal const val TAG = "AdsbTrafficRepository"
        internal const val MAX_DISPLAYED_TARGETS = 30
        internal const val STALE_AFTER_SEC = 60
        internal const val EXPIRY_AFTER_SEC = 120
        internal const val POSITION_SOURCE_FLARM = 3
        internal const val MIN_AIRBORNE_ALTITUDE_M = 30.48 // 100 ft
        internal const val MIN_AIRBORNE_SPEED_MPS = 20.5778 // 40 kt

        internal const val POLL_INTERVAL_HOT_MS = 10_000L
        internal const val POLL_INTERVAL_WARM_MS = 20_000L
        internal const val POLL_INTERVAL_COLD_MS = 30_000L
        internal const val POLL_INTERVAL_QUIET_MS = 40_000L
        internal const val POLL_INTERVAL_MAX_MS = 60_000L
        internal const val MOVEMENT_FAST_POLL_THRESHOLD_METERS = 500.0
        internal const val EMPTY_STREAK_WARM_POLLS = 1
        internal const val EMPTY_STREAK_COLD_POLLS = 3
        internal const val EMPTY_STREAK_QUIET_POLLS = 6
        internal const val CREDIT_FLOOR_GUARDED = 500
        internal const val CREDIT_FLOOR_LOW = 200
        internal const val CREDIT_FLOOR_CRITICAL = 50
        internal const val BUDGET_FLOOR_GUARDED_MS = 20_000L
        internal const val BUDGET_FLOOR_LOW_MS = 30_000L
        internal const val BUDGET_FLOOR_CRITICAL_MS = 60_000L
        internal const val ANONYMOUS_POLL_FLOOR_MS = 30_000L
        internal const val AUTH_FAILED_POLL_FLOOR_MS = 45_000L
        internal const val REQUEST_HISTORY_WINDOW_MS = 60L * 60L * 1_000L
        internal const val REQUESTS_PER_HOUR_GUARDED = 120
        internal const val REQUESTS_PER_HOUR_LOW = 180
        internal const val REQUESTS_PER_HOUR_CRITICAL = 300
        internal const val RECONNECT_BACKOFF_START_MS = 2_000L
        internal const val RECONNECT_BACKOFF_MAX_MS = 60_000L
        internal const val RETRY_FLOOR_UNKNOWN_MS = 2_000L
        internal const val RETRY_FLOOR_TIMEOUT_MS = 8_000L
        internal const val RETRY_FLOOR_CONNECT_MS = 10_000L
        internal const val RETRY_FLOOR_OFFLINE_MS = 15_000L
        internal const val RETRY_FLOOR_PROTOCOL_MS = 20_000L
        internal const val CIRCUIT_BREAKER_FAILURE_THRESHOLD = 3
        internal const val CIRCUIT_BREAKER_OPEN_WINDOW_MS = 30_000L
        internal const val NETWORK_WAIT_HOUSEKEEPING_TICK_MS = 1_000L
        internal const val OWN_ALTITUDE_RESELECT_MIN_INTERVAL_MS = 1_000L
        internal const val OWN_ALTITUDE_RESELECT_MAX_INTERVAL_MS = 10_000L
        internal const val OWN_ALTITUDE_RESELECT_MIN_DELTA_METERS = 1.0
        internal const val OWN_ALTITUDE_RESELECT_FORCE_DELTA_METERS = 25.0
    }
}
