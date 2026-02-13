package com.example.xcpro.adsb

import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.core.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    fun start()
    fun stop()
}

@Singleton
class AdsbTrafficRepositoryImpl @Inject constructor(
    private val providerClient: AdsbProviderClient,
    private val tokenRepository: OpenSkyTokenRepository,
    private val clock: Clock,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : AdsbTrafficRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = AdsbTrafficStore()

    private val _targets = MutableStateFlow<List<AdsbTrafficUiModel>>(emptyList())
    override val targets: StateFlow<List<AdsbTrafficUiModel>> = _targets.asStateFlow()

    private val _snapshot = MutableStateFlow(
        AdsbTrafficSnapshot(
            targets = emptyList(),
            connectionState = AdsbConnectionState.Disabled,
            centerLat = null,
            centerLon = null,
            receiveRadiusKm = RECEIVE_RADIUS_KM,
            fetchedCount = 0,
            withinRadiusCount = 0,
            displayedCount = 0,
            lastHttpStatus = null,
            remainingCredits = null,
            lastPollMonoMs = null,
            lastSuccessMonoMs = null,
            lastError = null
        )
    )
    override val snapshot: StateFlow<AdsbTrafficSnapshot> = _snapshot.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    override val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    @Volatile
    private var center: Center? = null

    @Volatile
    private var ownshipOrigin: Center? = null

    @Volatile
    private var loopJob: Job? = null

    @Volatile
    private var connectionState: AdsbConnectionState = AdsbConnectionState.Disabled

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var lastHttpStatus: Int? = null

    @Volatile
    private var remainingCredits: Int? = null

    @Volatile
    private var lastPollMonoMs: Long? = null

    @Volatile
    private var lastSuccessMonoMs: Long? = null

    @Volatile
    private var fetchedCount: Int = 0

    @Volatile
    private var withinRadiusCount: Int = 0
    private var consecutiveEmptyPolls: Int = 0
    private var lastPolledCenter: Center? = null
    private val requestTimesMonoMs = ArrayDeque<Long>()

    override fun start() {
        setEnabled(true)
    }

    override fun stop() {
        setEnabled(false)
    }

    override fun setEnabled(enabled: Boolean) {
        if (_isEnabled.value == enabled) return
        _isEnabled.value = enabled
        if (enabled) {
            ensureLoopRunning()
        } else {
            scope.launch {
                stopLoop(clearTargets = false)
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
        center = updatedCenter
        publishFromStore(updatedCenter)
        if (_isEnabled.value) ensureLoopRunning()
    }

    override fun updateOwnshipOrigin(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return
        ownshipOrigin = Center(latitude = latitude, longitude = longitude)
        center?.let { activeCenter ->
            publishFromStore(activeCenter)
        }
    }

    private fun ensureLoopRunning() {
        val existing = loopJob
        if (existing != null && existing.isActive) return
        loopJob = scope.launch {
            runLoop()
        }
    }

    private suspend fun stopLoop(clearTargets: Boolean) {
        loopJob?.cancelAndJoin()
        loopJob = null
        connectionState = AdsbConnectionState.Disabled
        lastError = null
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
        consecutiveEmptyPolls = 0
        lastPolledCenter = null
    }

    private suspend fun runLoop() {
        var backoffMs = RECONNECT_BACKOFF_START_MS
        while (_isEnabled.value) {
            val centerAtPoll = waitForCenter() ?: break
            val nowMonoMs = clock.nowMonoMs()
            lastPollMonoMs = nowMonoMs
            store.purgeExpired(nowMonoMs = nowMonoMs, expiryAfterSec = EXPIRY_AFTER_SEC)
            publishFromStore(centerAtPoll)

            val bbox = AdsbGeoMath.computeBbox(
                centerLat = centerAtPoll.latitude,
                centerLon = centerAtPoll.longitude,
                radiusKm = RECEIVE_RADIUS_KM.toDouble()
            )
            when (val result = fetchWithAuthRetry(bbox)) {
                is ProviderResult.Success -> {
                    handleSuccess(result, centerAtPoll, nowMonoMs)
                    connectionState = AdsbConnectionState.Active
                    lastError = null
                    publishSnapshot()
                    backoffMs = RECONNECT_BACKOFF_START_MS
                    delay(computeNextPollDelayMs(centerAtPoll, nowMonoMs))
                }

                is ProviderResult.RateLimited -> {
                    lastHttpStatus = 429
                    remainingCredits = result.remainingCredits
                    connectionState = AdsbConnectionState.BackingOff(result.retryAfterSec)
                    lastError = null
                    publishSnapshot()
                    delay(result.retryAfterSec.coerceAtLeast(1) * 1_000L)
                }

                is ProviderResult.HttpError -> {
                    lastHttpStatus = result.code
                    lastError = "HTTP ${result.code}"
                    connectionState = AdsbConnectionState.Error(lastError.orEmpty())
                    publishSnapshot()
                    val waitMs = withJitter(backoffMs)
                    delay(waitMs)
                    backoffMs = (backoffMs * 2L).coerceAtMost(RECONNECT_BACKOFF_MAX_MS)
                }

                is ProviderResult.NetworkError -> {
                    lastHttpStatus = null
                    lastError = result.message
                    connectionState = AdsbConnectionState.Error(result.message)
                    publishSnapshot()
                    val waitMs = withJitter(backoffMs)
                    delay(waitMs)
                    backoffMs = (backoffMs * 2L).coerceAtMost(RECONNECT_BACKOFF_MAX_MS)
                }
            }
        }

        connectionState = AdsbConnectionState.Disabled
        publishSnapshot()
    }

    private suspend fun fetchWithAuthRetry(bbox: BBox): ProviderResult {
        recordRequest(clock.nowMonoMs())
        val token = tokenRepository.getValidTokenOrNull()
        val first = providerClient.fetchStates(bbox, token?.let(::AdsbAuth))
        if (first is ProviderResult.HttpError && first.code == 401 && !token.isNullOrBlank()) {
            tokenRepository.invalidate()
            val refreshedToken = tokenRepository.getValidTokenOrNull()
            recordRequest(clock.nowMonoMs())
            return providerClient.fetchStates(bbox, refreshedToken?.let(::AdsbAuth))
        }
        return first
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
            radiusMeters = RECEIVE_RADIUS_KM * 1_000.0,
            maxDisplayed = MAX_DISPLAYED_TARGETS,
            staleAfterSec = STALE_AFTER_SEC
        )
        withinRadiusCount = selection.withinRadiusCount
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

    private fun publishFromStore(centerAtPoll: Center) {
        val ownshipReference = ownshipReference(centerAtPoll)
        val selection = store.select(
            nowMonoMs = clock.nowMonoMs(),
            queryCenterLat = centerAtPoll.latitude,
            queryCenterLon = centerAtPoll.longitude,
            referenceLat = ownshipReference.latitude,
            referenceLon = ownshipReference.longitude,
            radiusMeters = RECEIVE_RADIUS_KM * 1_000.0,
            maxDisplayed = MAX_DISPLAYED_TARGETS,
            staleAfterSec = STALE_AFTER_SEC
        )
        withinRadiusCount = selection.withinRadiusCount
        _targets.value = selection.displayed
        publishSnapshot()
    }

    private fun ownshipReference(fallbackCenter: Center): Center =
        ownshipOrigin ?: fallbackCenter

    private fun computeNextPollDelayMs(centerAtPoll: Center, nowMonoMs: Long): Long {
        val adaptiveMs = computeAdaptivePollDelayMs(centerAtPoll)
        val budgetFloorMs = computeBudgetFloorDelayMs(nowMonoMs)
        val shouldPrioritizeNearbyTraffic = withinRadiusCount > 0
        val delayMs = if (shouldPrioritizeNearbyTraffic) {
            adaptiveMs
        } else {
            maxOf(adaptiveMs, budgetFloorMs)
        }
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
        _snapshot.value = AdsbTrafficSnapshot(
            targets = _targets.value,
            connectionState = connectionState,
            centerLat = activeCenter?.latitude,
            centerLon = activeCenter?.longitude,
            receiveRadiusKm = RECEIVE_RADIUS_KM,
            fetchedCount = fetchedCount,
            withinRadiusCount = withinRadiusCount,
            displayedCount = _targets.value.size,
            lastHttpStatus = lastHttpStatus,
            remainingCredits = remainingCredits,
            lastPollMonoMs = lastPollMonoMs,
            lastSuccessMonoMs = lastSuccessMonoMs,
            lastError = lastError
        )
    }

    private suspend fun waitForCenter(): Center? {
        while (_isEnabled.value) {
            center?.let { return it }
            delay(WAIT_FOR_CENTER_MS)
        }
        return null
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

    private data class Center(
        val latitude: Double,
        val longitude: Double
    )

    private companion object {
        private const val TAG = "AdsbTrafficRepository"
        private const val RECEIVE_RADIUS_KM = 20
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
        private const val REQUEST_HISTORY_WINDOW_MS = 60L * 60L * 1_000L
        private const val REQUESTS_PER_HOUR_GUARDED = 120
        private const val REQUESTS_PER_HOUR_LOW = 180
        private const val REQUESTS_PER_HOUR_CRITICAL = 300
        private const val WAIT_FOR_CENTER_MS = 100L
        private const val RECONNECT_BACKOFF_START_MS = 2_000L
        private const val RECONNECT_BACKOFF_MAX_MS = 60_000L
    }
}
