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
    private val providerClient: AdsbProviderClient,
    private val tokenRepository: OpenSkyTokenRepository,
    private val clock: Clock,
    @IoDispatcher dispatcher: CoroutineDispatcher,
    private val networkAvailabilityPort: AdsbNetworkAvailabilityPort,
    private val emergencyAudioSettingsPort: AdsbEmergencyAudioSettingsPort =
        DisabledEmergencyAudioSettingsPort(),
    private val emergencyAudioOutputPort: AdsbEmergencyAudioOutputPort =
        NoOpAdsbEmergencyAudioOutputPort,
    private val emergencyAudioFeatureFlags: AdsbEmergencyAudioFeatureFlags =
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

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = AdsbTrafficStore()
    private val emergencyAudioAlertFsm = AdsbEmergencyAudioAlertFsm()

    private val _targets = MutableStateFlow<List<AdsbTrafficUiModel>>(emptyList())
    override val targets: StateFlow<List<AdsbTrafficUiModel>> = _targets.asStateFlow()

    private val _snapshot = MutableStateFlow(
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

    private val _isEnabled = MutableStateFlow(false)
    override val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    @Volatile
    private var center: Center? = null
    private val centerState = MutableStateFlow<Center?>(null)

    @Volatile
    private var ownshipOrigin: Center? = null

    @Volatile
    private var loopJob: Job? = null
    private val loopJobLock = Any()
    private val stateTransitionMutex = Mutex()

    @Volatile
    private var connectionState: AdsbConnectionState = AdsbConnectionState.Disabled

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var lastNetworkFailureKind: AdsbNetworkFailureKind? = null

    @Volatile
    private var lastHttpStatus: Int? = null

    @Volatile
    private var remainingCredits: Int? = null

    @Volatile
    private var authMode: AdsbAuthMode = AdsbAuthMode.Anonymous

    @Volatile
    private var lastPollMonoMs: Long? = null

    @Volatile
    private var lastSuccessMonoMs: Long? = null
    @Volatile
    private var fetchedCount: Int = 0

    @Volatile
    private var withinRadiusCount: Int = 0
    @Volatile
    private var withinVerticalCount: Int = 0
    @Volatile
    private var filteredByVerticalCount: Int = 0
    @Volatile
    private var cappedCount: Int = 0

    @Volatile
    private var ownshipAltitudeMeters: Double? = null
    @Volatile
    private var lastOwnshipAltitudeReselectMonoMs: Long = Long.MIN_VALUE
    @Volatile
    private var lastOwnshipAltitudeReselectMeters: Double? = null

    @Volatile
    private var receiveRadiusKm: Int = ADSB_MAX_DISTANCE_DEFAULT_KM
    @Volatile
    private var verticalFilterAboveMeters: Double = ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
    @Volatile
    private var verticalFilterBelowMeters: Double = ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
    private var consecutiveEmptyPolls: Int = 0
    private val pollingHealthPolicy = AdsbPollingHealthPolicy(
        circuitBreakerFailureThreshold = CIRCUIT_BREAKER_FAILURE_THRESHOLD,
        circuitBreakerOpenWindowMs = CIRCUIT_BREAKER_OPEN_WINDOW_MS
    )
    private var lastPolledCenter: Center? = null
    private val requestTimesMonoMs = ArrayDeque<Long>()
    private var networkOnline: Boolean = true
    private var networkOfflineTransitionCount: Int = 0
    private var networkOnlineTransitionCount: Int = 0
    private var lastNetworkTransitionMonoMs: Long? = null
    @Volatile
    private var emergencyAudioSettings = AdsbEmergencyAudioSettings()

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

    private fun ensureLoopRunning() {
        synchronized(loopJobLock) {
            val existing = loopJob
            if (existing != null && existing.isActive) return
            loopJob = scope.launch {
                runLoop()
            }
        }
    }

    private suspend fun stopLoop(clearTargets: Boolean) {
        val jobToCancel = synchronized(loopJobLock) {
            val existing = loopJob
            loopJob = null
            existing
        }
        jobToCancel?.cancelAndJoin()
        connectionState = AdsbConnectionState.Disabled
        lastError = null
        lastNetworkFailureKind = null
        pollingHealthPolicy.resetForStop()
        if (clearTargets) {
            clearCachedTargets()
        }
        publishSnapshot()
    }

    private fun clearCachedTargets() {
        store.clear()
        _targets.value = emptyList()
        fetchedCount = 0
        withinRadiusCount = 0
        withinVerticalCount = 0
        filteredByVerticalCount = 0
        cappedCount = 0
        consecutiveEmptyPolls = 0
        lastPolledCenter = null
        lastOwnshipAltitudeReselectMonoMs = Long.MIN_VALUE
        lastOwnshipAltitudeReselectMeters = ownshipAltitudeMeters
    }

    private fun observeEmergencyAudioSettings() {
        scope.launch {
            combine(
                emergencyAudioSettingsPort.emergencyAudioEnabledFlow,
                emergencyAudioSettingsPort.emergencyAudioCooldownMsFlow
            ) { enabled, cooldownMs ->
                AdsbEmergencyAudioSettings(
                    enabled = enabled,
                    cooldownMs = cooldownMs
                )
            }.collect { settings ->
                emergencyAudioSettings = settings
                publishSnapshot()
            }
        }
    }

    private suspend fun runLoop() {
        val thisJob = currentCoroutineContext()[Job]
        var backoffMs = RECONNECT_BACKOFF_START_MS
        try {
            while (_isEnabled.value) {
                try {
                    val centerAtPoll = waitForCenter() ?: break
                    val loopMonoMs = clock.nowMonoMs()
                    publishFromStore(centerAtPoll, loopMonoMs)
                    if (!awaitNetworkOnline()) break
                    if (!awaitCircuitBreakerReady()) break
                    lastPollMonoMs = clock.nowMonoMs()

                    val bbox = AdsbGeoMath.computeBbox(
                        centerLat = centerAtPoll.latitude,
                        centerLon = centerAtPoll.longitude,
                        radiusKm = receiveRadiusKm.toDouble()
                    )
                    when (val result = fetchWithAuthRetry(bbox)) {
                        is ProviderResult.Success -> {
                            val nowSuccessMonoMs = clock.nowMonoMs()
                            handleSuccess(result, centerAtPoll, nowSuccessMonoMs)
                            pollingHealthPolicy.resetAfterSuccessfulRequest()
                            connectionState = AdsbConnectionState.Active
                            lastError = null
                            lastNetworkFailureKind = null
                            publishSnapshot()
                            backoffMs = RECONNECT_BACKOFF_START_MS
                            if (!delayForNextAttempt(computeNextPollDelayMs(centerAtPoll, nowSuccessMonoMs))) {
                                break
                            }
                        }

                        is ProviderResult.RateLimited -> {
                            pollingHealthPolicy.resetAfterSuccessfulRequest()
                            lastHttpStatus = 429
                            remainingCredits = result.remainingCredits
                            connectionState = AdsbConnectionState.BackingOff(result.retryAfterSec)
                            lastError = null
                            lastNetworkFailureKind = null
                            publishSnapshot()
                            if (!delayForNextAttempt(result.retryAfterSec.coerceAtLeast(1) * 1_000L)) {
                                break
                            }
                        }

                        is ProviderResult.HttpError -> {
                            val nowFailureMonoMs = clock.nowMonoMs()
                            lastHttpStatus = result.code
                            lastError = "HTTP ${result.code}"
                            lastNetworkFailureKind = null
                            pollingHealthPolicy.markFailureEvent(nowFailureMonoMs)
                            connectionState = AdsbConnectionState.Error(lastError.orEmpty())
                            publishSnapshot()
                            if (pollingHealthPolicy.recordFailureAndMaybeOpenCircuit(nowFailureMonoMs)) {
                                connectionState = AdsbConnectionState.Error(ADSB_ERROR_CIRCUIT_BREAKER_OPEN)
                                lastHttpStatus = null
                                lastError = ADSB_ERROR_CIRCUIT_BREAKER_OPEN
                                publishSnapshot()
                                backoffMs = RECONNECT_BACKOFF_START_MS
                                continue
                            }
                            val waitMs = withJitter(backoffMs)
                            if (!delayForNextAttempt(waitMs)) {
                                break
                            }
                            backoffMs = (backoffMs * 2L).coerceAtMost(RECONNECT_BACKOFF_MAX_MS)
                        }

                        is ProviderResult.NetworkError -> {
                            val nowFailureMonoMs = clock.nowMonoMs()
                            lastHttpStatus = null
                            lastNetworkFailureKind = result.kind
                            lastError = result.toDebugMessage()
                            pollingHealthPolicy.markFailureEvent(nowFailureMonoMs)
                            connectionState = AdsbConnectionState.Error(lastError.orEmpty())
                            publishSnapshot()
                            if (pollingHealthPolicy.recordFailureAndMaybeOpenCircuit(nowFailureMonoMs)) {
                                connectionState = AdsbConnectionState.Error(ADSB_ERROR_CIRCUIT_BREAKER_OPEN)
                                lastHttpStatus = null
                                lastError = ADSB_ERROR_CIRCUIT_BREAKER_OPEN
                                publishSnapshot()
                                backoffMs = RECONNECT_BACKOFF_START_MS
                                continue
                            }
                            val waitMs = maxOf(
                                withJitter(backoffMs),
                                networkFailureRetryFloorMs(result.kind)
                            )
                            if (!delayForNextAttempt(waitMs)) {
                                break
                            }
                            backoffMs = (backoffMs * 2L).coerceAtMost(RECONNECT_BACKOFF_MAX_MS)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "ADS-B loop recovered from unexpected error: ${e::class.java.simpleName}")
                    val nowFailureMonoMs = clock.nowMonoMs()
                    lastHttpStatus = null
                    lastNetworkFailureKind = AdsbNetworkFailureKind.UNKNOWN
                    lastError = "Unexpected ADS-B loop failure"
                    pollingHealthPolicy.markFailureEvent(nowFailureMonoMs)
                    connectionState = AdsbConnectionState.Error(lastError.orEmpty())
                    publishSnapshot()
                    if (pollingHealthPolicy.recordFailureAndMaybeOpenCircuit(nowFailureMonoMs)) {
                        connectionState = AdsbConnectionState.Error(ADSB_ERROR_CIRCUIT_BREAKER_OPEN)
                        lastError = ADSB_ERROR_CIRCUIT_BREAKER_OPEN
                        publishSnapshot()
                        backoffMs = RECONNECT_BACKOFF_START_MS
                        continue
                    }
                    val waitMs = maxOf(
                        withJitter(backoffMs),
                        networkFailureRetryFloorMs(AdsbNetworkFailureKind.UNKNOWN)
                    )
                    if (!delayForNextAttempt(waitMs)) {
                        break
                    }
                    backoffMs = (backoffMs * 2L).coerceAtMost(RECONNECT_BACKOFF_MAX_MS)
                }
            }
            connectionState = AdsbConnectionState.Disabled
            publishSnapshot()
        } finally {
            synchronized(loopJobLock) {
                if (loopJob == thisJob) {
                    loopJob = null
                }
            }
        }
    }

    private suspend fun fetchWithAuthRetry(bbox: BBox): ProviderResult {
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

    private fun handleSuccess(
        result: ProviderResult.Success,
        centerAtPoll: Center,
        nowMonoMs: Long
    ) {
        lastHttpStatus = result.httpCode
        remainingCredits = result.remainingCredits
        fetchedCount = result.response.states.size

        val mappedTargets = buildList {
            for (state in result.response.states) {
                val mapped = state.toTarget(nowMonoMs) ?: continue
                add(mapped)
            }
        }
        store.upsertAll(mappedTargets)
        store.purgeExpired(nowMonoMs = nowMonoMs, expiryAfterSec = EXPIRY_AFTER_SEC)
        val ownshipReference = ownshipReference(centerAtPoll)
        val selection = store.select(
            nowMonoMs = nowMonoMs,
            queryCenterLat = centerAtPoll.latitude,
            queryCenterLon = centerAtPoll.longitude,
            referenceLat = ownshipReference.latitude,
            referenceLon = ownshipReference.longitude,
            ownshipAltitudeMeters = ownshipReference.altitudeMeters,
            usesOwnshipReference = ownshipReference.usesOwnshipReference,
            radiusMeters = receiveRadiusKm * 1_000.0,
            verticalAboveMeters = verticalFilterAboveMeters,
            verticalBelowMeters = verticalFilterBelowMeters,
            maxDisplayed = MAX_DISPLAYED_TARGETS,
            staleAfterSec = STALE_AFTER_SEC
        )
        withinRadiusCount = selection.withinRadiusCount
        withinVerticalCount = selection.withinVerticalCount
        filteredByVerticalCount = selection.filteredByVerticalCount
        cappedCount = selection.cappedCount
        consecutiveEmptyPolls = if (selection.withinRadiusCount == 0) {
            consecutiveEmptyPolls + 1
        } else {
            0
        }
        _targets.value = selection.displayed
        lastSuccessMonoMs = nowMonoMs
        if (selection.displayed.isNotEmpty()) {
            AppLogger.d(TAG, "ADS-B updated targets: ${selection.displayed.size}")
        }
    }

    private fun publishFromStore(centerAtPoll: Center, nowMonoMs: Long = clock.nowMonoMs()) {
        store.purgeExpired(nowMonoMs = nowMonoMs, expiryAfterSec = EXPIRY_AFTER_SEC)
        val ownshipReference = ownshipReference(centerAtPoll)
        val selection = store.select(
            nowMonoMs = nowMonoMs,
            queryCenterLat = centerAtPoll.latitude,
            queryCenterLon = centerAtPoll.longitude,
            referenceLat = ownshipReference.latitude,
            referenceLon = ownshipReference.longitude,
            ownshipAltitudeMeters = ownshipReference.altitudeMeters,
            usesOwnshipReference = ownshipReference.usesOwnshipReference,
            radiusMeters = receiveRadiusKm * 1_000.0,
            verticalAboveMeters = verticalFilterAboveMeters,
            verticalBelowMeters = verticalFilterBelowMeters,
            maxDisplayed = MAX_DISPLAYED_TARGETS,
            staleAfterSec = STALE_AFTER_SEC
        )
        withinRadiusCount = selection.withinRadiusCount
        withinVerticalCount = selection.withinVerticalCount
        filteredByVerticalCount = selection.filteredByVerticalCount
        cappedCount = selection.cappedCount
        _targets.value = selection.displayed
        publishSnapshot()
    }

    private fun shouldReselectForOwnshipAltitude(
        nowMonoMs: Long,
        nextOwnshipAltitudeMeters: Double?
    ): Boolean {
        val lastMonoMs = lastOwnshipAltitudeReselectMonoMs
        if (lastMonoMs == Long.MIN_VALUE) return true

        val deltaMeters = ownshipAltitudeDeltaMeters(
            previousAltitudeMeters = lastOwnshipAltitudeReselectMeters,
            nextAltitudeMeters = nextOwnshipAltitudeMeters
        )
        if (deltaMeters >= OWN_ALTITUDE_RESELECT_FORCE_DELTA_METERS) return true

        val elapsedMs = nowMonoMs - lastMonoMs
        if (elapsedMs < OWN_ALTITUDE_RESELECT_MIN_INTERVAL_MS) return false
        if (deltaMeters >= OWN_ALTITUDE_RESELECT_MIN_DELTA_METERS) return true

        return elapsedMs >= OWN_ALTITUDE_RESELECT_MAX_INTERVAL_MS && deltaMeters > 0.0
    }

    private fun ownshipAltitudeDeltaMeters(
        previousAltitudeMeters: Double?,
        nextAltitudeMeters: Double?
    ): Double {
        if (previousAltitudeMeters == null && nextAltitudeMeters == null) return 0.0
        if (previousAltitudeMeters == null || nextAltitudeMeters == null) return Double.MAX_VALUE
        return abs(nextAltitudeMeters - previousAltitudeMeters)
    }

    private fun ownshipReference(fallbackCenter: Center): ReferencePoint {
        val ownship = ownshipOrigin
        val altitude = ownshipAltitudeMeters?.takeIf { it.isFinite() }
        return if (ownship != null) {
            ReferencePoint(
                latitude = ownship.latitude,
                longitude = ownship.longitude,
                altitudeMeters = altitude,
                usesOwnshipReference = true
            )
        } else {
            ReferencePoint(
                latitude = fallbackCenter.latitude,
                longitude = fallbackCenter.longitude,
                altitudeMeters = altitude,
                usesOwnshipReference = false
            )
        }
    }

    private fun computeNextPollDelayMs(centerAtPoll: Center, nowMonoMs: Long): Long {
        val adaptiveMs = computeAdaptivePollDelayMs(centerAtPoll)
        val budgetFloorMs = computeBudgetFloorDelayMs(nowMonoMs)
        val shouldPrioritizeNearbyTraffic = withinRadiusCount > 0
        val baseDelayMs = if (shouldPrioritizeNearbyTraffic) {
            adaptiveMs
        } else {
            maxOf(adaptiveMs, budgetFloorMs)
        }
        val authFloorMs = when (authMode) {
            AdsbAuthMode.Authenticated -> 0L
            AdsbAuthMode.Anonymous -> ANONYMOUS_POLL_FLOOR_MS
            AdsbAuthMode.AuthFailed -> AUTH_FAILED_POLL_FLOOR_MS
        }
        val delayMs = maxOf(baseDelayMs, authFloorMs)
        lastPolledCenter = centerAtPoll
        return delayMs.coerceIn(POLL_INTERVAL_HOT_MS, POLL_INTERVAL_MAX_MS)
    }

    private fun computeAdaptivePollDelayMs(centerAtPoll: Center): Long {
        if (withinRadiusCount > 0) return POLL_INTERVAL_HOT_MS
        val movementMeters = lastPolledCenter?.let {
            AdsbGeoMath.haversineMeters(
                lat1 = it.latitude,
                lon1 = it.longitude,
                lat2 = centerAtPoll.latitude,
                lon2 = centerAtPoll.longitude
            )
        } ?: 0.0
        if (movementMeters >= MOVEMENT_FAST_POLL_THRESHOLD_METERS) return POLL_INTERVAL_HOT_MS
        return when {
            consecutiveEmptyPolls >= EMPTY_STREAK_QUIET_POLLS -> POLL_INTERVAL_QUIET_MS
            consecutiveEmptyPolls >= EMPTY_STREAK_COLD_POLLS -> POLL_INTERVAL_COLD_MS
            consecutiveEmptyPolls >= EMPTY_STREAK_WARM_POLLS -> POLL_INTERVAL_WARM_MS
            else -> POLL_INTERVAL_HOT_MS
        }
    }

    private fun computeBudgetFloorDelayMs(nowMonoMs: Long): Long {
        val credits = remainingCredits
        if (credits != null) {
            return when {
                credits <= CREDIT_FLOOR_CRITICAL -> BUDGET_FLOOR_CRITICAL_MS
                credits <= CREDIT_FLOOR_LOW -> BUDGET_FLOOR_LOW_MS
                credits <= CREDIT_FLOOR_GUARDED -> BUDGET_FLOOR_GUARDED_MS
                else -> 0L
            }
        }
        pruneRequestHistory(nowMonoMs)
        val requestsInLastHour = requestTimesMonoMs.size
        return when {
            requestsInLastHour >= REQUESTS_PER_HOUR_CRITICAL -> BUDGET_FLOOR_CRITICAL_MS
            requestsInLastHour >= REQUESTS_PER_HOUR_LOW -> BUDGET_FLOOR_LOW_MS
            requestsInLastHour >= REQUESTS_PER_HOUR_GUARDED -> BUDGET_FLOOR_GUARDED_MS
            else -> 0L
        }
    }

    private fun recordRequest(nowMonoMs: Long) {
        requestTimesMonoMs.addLast(nowMonoMs)
        pruneRequestHistory(nowMonoMs)
    }

    private fun pruneRequestHistory(nowMonoMs: Long) {
        val cutoff = nowMonoMs - REQUEST_HISTORY_WINDOW_MS
        while (requestTimesMonoMs.isNotEmpty() && requestTimesMonoMs.first() < cutoff) {
            requestTimesMonoMs.removeFirst()
        }
    }

    private fun publishSnapshot() {
        val activeCenter = center
        val pollingHealthSnapshot = pollingHealthPolicy.snapshotTelemetry()
        val nowMonoMs = clock.nowMonoMs()
        updateNetworkTransitionTelemetry(nowMonoMs)
        val emergencyAudioFeatureGateOn = isEmergencyAudioFeatureGateOn()
        val emergencyTargetId = _targets.value.firstOrNull { target ->
            target.isEmergencyCollisionRisk
        }?.id
        val emergencyAudioDecision = emergencyAudioAlertFsm.evaluate(
            nowMonoMs = nowMonoMs,
            emergencyTargetId = emergencyTargetId,
            hasOwnshipReference = ownshipOrigin != null,
            settings = emergencyAudioSettings,
            featureFlagEnabled = emergencyAudioFeatureGateOn && _isEnabled.value
        )
        maybePlayEmergencyAudioOutput(nowMonoMs, emergencyAudioDecision)
        val emergencyAudioTelemetry = emergencyAudioAlertFsm.snapshotTelemetry(nowMonoMs)
        val offlineDwellMs = if (!networkOnline) {
            val transitionMonoMs = lastNetworkTransitionMonoMs ?: nowMonoMs
            (nowMonoMs - transitionMonoMs).coerceAtLeast(0L)
        } else {
            0L
        }
        _snapshot.value = AdsbTrafficSnapshot(
            targets = _targets.value,
            connectionState = connectionState,
            authMode = authMode,
            centerLat = activeCenter?.latitude,
            centerLon = activeCenter?.longitude,
            usesOwnshipReference = ownshipOrigin != null,
            receiveRadiusKm = receiveRadiusKm,
            fetchedCount = fetchedCount,
            withinRadiusCount = withinRadiusCount,
            withinVerticalCount = withinVerticalCount,
            filteredByVerticalCount = filteredByVerticalCount,
            cappedCount = cappedCount,
            displayedCount = _targets.value.size,
            lastHttpStatus = lastHttpStatus,
            remainingCredits = remainingCredits,
            lastPollMonoMs = lastPollMonoMs,
            lastSuccessMonoMs = lastSuccessMonoMs,
            lastError = lastError,
            lastNetworkFailureKind = lastNetworkFailureKind,
            consecutiveFailureCount = pollingHealthSnapshot.consecutiveFailureCount,
            nextRetryMonoMs = pollingHealthSnapshot.nextRetryMonoMs,
            lastFailureMonoMs = pollingHealthSnapshot.lastFailureMonoMs,
            networkOnline = networkOnline,
            networkOfflineTransitionCount = networkOfflineTransitionCount,
            networkOnlineTransitionCount = networkOnlineTransitionCount,
            lastNetworkTransitionMonoMs = lastNetworkTransitionMonoMs,
            currentOfflineDwellMs = offlineDwellMs,
            emergencyAudioState = emergencyAudioTelemetry.state,
            emergencyAudioEnabledBySetting = emergencyAudioSettings.enabled,
            emergencyAudioFeatureGateOn = emergencyAudioFeatureGateOn,
            emergencyAudioCooldownMs = emergencyAudioSettings.normalizedCooldownMs,
            emergencyAudioAlertTriggerCount = emergencyAudioTelemetry.alertTriggerCount,
            emergencyAudioCooldownBlockEpisodeCount =
                emergencyAudioTelemetry.cooldownBlockEpisodeCount,
            emergencyAudioTransitionEventCount = emergencyAudioTelemetry.transitionEventCount,
            emergencyAudioLastAlertMonoMs = emergencyAudioTelemetry.lastAlertMonoMs,
            emergencyAudioCooldownRemainingMs = emergencyAudioTelemetry.cooldownRemainingMs,
            emergencyAudioActiveTargetId = emergencyAudioTelemetry.activeEmergencyTargetId?.raw
        )
    }

    private fun isEmergencyAudioFeatureGateOn(): Boolean =
        emergencyAudioFeatureFlags.emergencyAudioEnabled ||
            emergencyAudioFeatureFlags.emergencyAudioShadowMode

    private fun isEmergencyAudioMasterOutputEnabled(): Boolean =
        emergencyAudioFeatureFlags.emergencyAudioEnabled

    private fun maybePlayEmergencyAudioOutput(
        nowMonoMs: Long,
        decision: AdsbEmergencyAudioDecision
    ) {
        if (!decision.shouldPlayAlert) return
        if (!isEmergencyAudioMasterOutputEnabled()) return
        runCatching {
            emergencyAudioOutputPort.playEmergencyAlert(
                triggerMonoMs = nowMonoMs,
                emergencyTargetId = decision.activeEmergencyTargetId?.raw
            )
        }.onFailure { throwable ->
            AppLogger.w(
                TAG,
                "ADS-B emergency audio output failed: ${throwable::class.java.simpleName}"
            )
        }
    }

    private fun updateNetworkTransitionTelemetry(nowMonoMs: Long) {
        // Avoid probing the network-availability port while disabled so startup/shutdown
        // snapshot publications do not consume transient adapter faults before loop recovery.
        if (connectionState is AdsbConnectionState.Disabled) return
        val currentOnline = runCatching { networkAvailabilityPort.isOnline.value }.getOrDefault(networkOnline)
        if (currentOnline == networkOnline) return
        networkOnline = currentOnline
        lastNetworkTransitionMonoMs = nowMonoMs
        if (currentOnline) {
            networkOnlineTransitionCount += 1
        } else {
            networkOfflineTransitionCount += 1
        }
    }

    private suspend fun waitForCenter(): Center? {
        center?.let { return it }
        val waitResult = combine(_isEnabled, centerState) { enabled, centerValue ->
            when {
                !enabled -> CenterWaitState.Disabled
                centerValue != null -> CenterWaitState.Ready(centerValue)
                else -> CenterWaitState.Waiting
            }
        }.first { it !is CenterWaitState.Waiting }
        return when (waitResult) {
            is CenterWaitState.Ready -> waitResult.center
            CenterWaitState.Disabled -> null
            CenterWaitState.Waiting -> null
        }
    }

    private suspend fun awaitNetworkOnline(): Boolean {
        if (!_isEnabled.value) return false
        if (networkAvailabilityPort.isOnline.value) return true
        connectionState = AdsbConnectionState.Error(ADSB_ERROR_OFFLINE)
        lastHttpStatus = null
        lastError = ADSB_ERROR_OFFLINE
        lastNetworkFailureKind = AdsbNetworkFailureKind.NO_ROUTE
        pollingHealthPolicy.markFailureEvent(clock.nowMonoMs())
        pollingHealthPolicy.clearNextRetry()
        publishSnapshot()
        while (_isEnabled.value) {
            val waitResult = withTimeoutOrNull(NETWORK_WAIT_HOUSEKEEPING_TICK_MS) {
                combine(_isEnabled, networkAvailabilityPort.isOnline) { enabled, isOnline ->
                    when {
                        !enabled -> NetworkWaitState.Disabled
                        isOnline -> NetworkWaitState.Online
                        else -> NetworkWaitState.Offline
                    }
                }.first { it != NetworkWaitState.Offline }
            }
            when (waitResult) {
                NetworkWaitState.Disabled -> return false
                NetworkWaitState.Online -> return true
                NetworkWaitState.Offline,
                null -> {
                    runHousekeepingTick()
                }
            }
        }
        return false
    }

    private fun runHousekeepingTick(nowMonoMs: Long = clock.nowMonoMs()) {
        val activeCenter = center
        if (activeCenter != null) {
            publishFromStore(centerAtPoll = activeCenter, nowMonoMs = nowMonoMs)
        } else {
            publishSnapshot()
        }
    }

    private suspend fun delayForNextAttempt(waitMs: Long): Boolean {
        if (!_isEnabled.value) return false
        if (!networkAvailabilityPort.isOnline.value) {
            pollingHealthPolicy.clearNextRetry()
            return awaitNetworkOnline()
        }
        val normalizedWaitMs = waitMs.coerceAtLeast(0L)
        pollingHealthPolicy.scheduleNextRetry(
            nowMonoMs = clock.nowMonoMs(),
            waitMs = normalizedWaitMs
        )
        publishSnapshot()
        val interrupted = withTimeoutOrNull(normalizedWaitMs) {
            combine(_isEnabled, networkAvailabilityPort.isOnline) { enabled, isOnline ->
                when {
                    !enabled -> NetworkWaitState.Disabled
                    !isOnline -> NetworkWaitState.Offline
                    else -> NetworkWaitState.Online
                }
            }.first { it != NetworkWaitState.Online }
        }
        pollingHealthPolicy.clearNextRetry()
        publishSnapshot()
        return when (interrupted) {
            null -> _isEnabled.value
            NetworkWaitState.Disabled -> false
            NetworkWaitState.Offline -> awaitNetworkOnline()
            NetworkWaitState.Online -> _isEnabled.value
        }
    }

    private suspend fun awaitCircuitBreakerReady(): Boolean {
        val probeAfterMs = pollingHealthPolicy.circuitOpenProbeDelayMsOrNull() ?: run {
            return _isEnabled.value
        }

        connectionState = AdsbConnectionState.Error(ADSB_ERROR_CIRCUIT_BREAKER_OPEN)
        lastHttpStatus = null
        lastError = ADSB_ERROR_CIRCUIT_BREAKER_OPEN
        publishSnapshot()
        if (!delayForNextAttempt(probeAfterMs)) {
            return false
        }
        pollingHealthPolicy.transitionOpenToHalfOpen()
        connectionState = AdsbConnectionState.Error(ADSB_ERROR_CIRCUIT_BREAKER_PROBE)
        lastError = ADSB_ERROR_CIRCUIT_BREAKER_PROBE
        publishSnapshot()
        return _isEnabled.value
    }

    private fun OpenSkyStateVector.toTarget(receivedMonoMs: Long): AdsbTarget? {
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

    private fun withJitter(backoffMs: Long): Long {
        val random = Random(clock.nowMonoMs())
        val factor = 0.8 + (random.nextDouble() * 0.4)
        return (backoffMs * factor).toLong().coerceAtLeast(1_000L)
    }

    private fun networkFailureRetryFloorMs(kind: AdsbNetworkFailureKind): Long = when (kind) {
        AdsbNetworkFailureKind.DNS,
        AdsbNetworkFailureKind.NO_ROUTE -> RETRY_FLOOR_OFFLINE_MS
        AdsbNetworkFailureKind.TIMEOUT -> RETRY_FLOOR_TIMEOUT_MS
        AdsbNetworkFailureKind.CONNECT -> RETRY_FLOOR_CONNECT_MS
        AdsbNetworkFailureKind.TLS,
        AdsbNetworkFailureKind.MALFORMED_RESPONSE -> RETRY_FLOOR_PROTOCOL_MS
        AdsbNetworkFailureKind.UNKNOWN -> RETRY_FLOOR_UNKNOWN_MS
    }

    private fun ProviderResult.NetworkError.toDebugMessage(): String = when (kind) {
        AdsbNetworkFailureKind.DNS -> "DNS resolution failed"
        AdsbNetworkFailureKind.TIMEOUT -> "Socket timeout"
        AdsbNetworkFailureKind.CONNECT -> "Connect failed"
        AdsbNetworkFailureKind.NO_ROUTE -> "No route to host"
        AdsbNetworkFailureKind.TLS -> "TLS handshake failed"
        AdsbNetworkFailureKind.MALFORMED_RESPONSE -> "Malformed provider response"
        AdsbNetworkFailureKind.UNKNOWN -> message
    }

    private data class Center(
        val latitude: Double,
        val longitude: Double
    )

    private data class ReferencePoint(
        val latitude: Double,
        val longitude: Double,
        val altitudeMeters: Double?,
        val usesOwnshipReference: Boolean
    )

    private sealed interface CenterWaitState {
        data object Waiting : CenterWaitState
        data object Disabled : CenterWaitState
        data class Ready(val center: Center) : CenterWaitState
    }

    private sealed interface NetworkWaitState {
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
        private val AlwaysOnlineNetworkAvailabilityPort = object : AdsbNetworkAvailabilityPort {
            override val isOnline: StateFlow<Boolean> =
                MutableStateFlow(true).asStateFlow()
        }

        private const val TAG = "AdsbTrafficRepository"
        private const val MAX_DISPLAYED_TARGETS = 30
        private const val STALE_AFTER_SEC = 60
        private const val EXPIRY_AFTER_SEC = 120
        private const val POSITION_SOURCE_FLARM = 3
        private const val MIN_AIRBORNE_ALTITUDE_M = 30.48 // 100 ft
        private const val MIN_AIRBORNE_SPEED_MPS = 20.5778 // 40 kt

        private const val POLL_INTERVAL_HOT_MS = 10_000L
        private const val POLL_INTERVAL_WARM_MS = 20_000L
        private const val POLL_INTERVAL_COLD_MS = 30_000L
        private const val POLL_INTERVAL_QUIET_MS = 40_000L
        private const val POLL_INTERVAL_MAX_MS = 60_000L
        private const val MOVEMENT_FAST_POLL_THRESHOLD_METERS = 500.0
        private const val EMPTY_STREAK_WARM_POLLS = 1
        private const val EMPTY_STREAK_COLD_POLLS = 3
        private const val EMPTY_STREAK_QUIET_POLLS = 6
        private const val CREDIT_FLOOR_GUARDED = 500
        private const val CREDIT_FLOOR_LOW = 200
        private const val CREDIT_FLOOR_CRITICAL = 50
        private const val BUDGET_FLOOR_GUARDED_MS = 20_000L
        private const val BUDGET_FLOOR_LOW_MS = 30_000L
        private const val BUDGET_FLOOR_CRITICAL_MS = 60_000L
        private const val ANONYMOUS_POLL_FLOOR_MS = 30_000L
        private const val AUTH_FAILED_POLL_FLOOR_MS = 45_000L
        private const val REQUEST_HISTORY_WINDOW_MS = 60L * 60L * 1_000L
        private const val REQUESTS_PER_HOUR_GUARDED = 120
        private const val REQUESTS_PER_HOUR_LOW = 180
        private const val REQUESTS_PER_HOUR_CRITICAL = 300
        private const val RECONNECT_BACKOFF_START_MS = 2_000L
        private const val RECONNECT_BACKOFF_MAX_MS = 60_000L
        private const val RETRY_FLOOR_UNKNOWN_MS = 2_000L
        private const val RETRY_FLOOR_TIMEOUT_MS = 8_000L
        private const val RETRY_FLOOR_CONNECT_MS = 10_000L
        private const val RETRY_FLOOR_OFFLINE_MS = 15_000L
        private const val RETRY_FLOOR_PROTOCOL_MS = 20_000L
        private const val CIRCUIT_BREAKER_FAILURE_THRESHOLD = 3
        private const val CIRCUIT_BREAKER_OPEN_WINDOW_MS = 30_000L
        private const val NETWORK_WAIT_HOUSEKEEPING_TICK_MS = 1_000L
        private const val OWN_ALTITUDE_RESELECT_MIN_INTERVAL_MS = 1_000L
        private const val OWN_ALTITUDE_RESELECT_MAX_INTERVAL_MS = 10_000L
        private const val OWN_ALTITUDE_RESELECT_MIN_DELTA_METERS = 1.0
        private const val OWN_ALTITUDE_RESELECT_FORCE_DELTA_METERS = 25.0
    }
}
