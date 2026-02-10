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
    fun updateCenter(latitude: Double, longitude: Double)
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
                stopLoopAndClearTargets()
            }
        }
    }

    override fun updateCenter(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return
        center = Center(latitude = latitude, longitude = longitude)
        publishSnapshot()
        if (_isEnabled.value) ensureLoopRunning()
    }

    private fun ensureLoopRunning() {
        val existing = loopJob
        if (existing != null && existing.isActive) return
        loopJob = scope.launch {
            runLoop()
        }
    }

    private suspend fun stopLoopAndClearTargets() {
        loopJob?.cancelAndJoin()
        loopJob = null
        store.clear()
        _targets.value = emptyList()
        connectionState = AdsbConnectionState.Disabled
        lastError = null
        fetchedCount = 0
        withinRadiusCount = 0
        publishSnapshot()
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
                    delay(POLL_INTERVAL_MS)
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
        val token = tokenRepository.getValidTokenOrNull()
        val first = providerClient.fetchStates(bbox, token?.let(::AdsbAuth))
        if (first is ProviderResult.HttpError && first.code == 401 && !token.isNullOrBlank()) {
            tokenRepository.invalidate()
            val refreshedToken = tokenRepository.getValidTokenOrNull()
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
        val selection = store.select(
            nowMonoMs = nowMonoMs,
            centerLat = centerAtPoll.latitude,
            centerLon = centerAtPoll.longitude,
            radiusMeters = RECEIVE_RADIUS_KM * 1_000.0,
            maxDisplayed = MAX_DISPLAYED_TARGETS,
            staleAfterSec = STALE_AFTER_SEC
        )
        withinRadiusCount = selection.withinRadiusCount
        _targets.value = selection.displayed
        lastSuccessMonoMs = nowMonoMs
        if (selection.displayed.isNotEmpty()) {
            AppLogger.d(TAG, "ADS-B updated targets: ${selection.displayed.size}")
        }
    }

    private fun publishFromStore(centerAtPoll: Center) {
        val selection = store.select(
            nowMonoMs = clock.nowMonoMs(),
            centerLat = centerAtPoll.latitude,
            centerLon = centerAtPoll.longitude,
            radiusMeters = RECEIVE_RADIUS_KM * 1_000.0,
            maxDisplayed = MAX_DISPLAYED_TARGETS,
            staleAfterSec = STALE_AFTER_SEC
        )
        withinRadiusCount = selection.withinRadiusCount
        _targets.value = selection.displayed
        publishSnapshot()
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
        private const val RECEIVE_RADIUS_KM = 15
        private const val MAX_DISPLAYED_TARGETS = 30
        private const val STALE_AFTER_SEC = 60
        private const val EXPIRY_AFTER_SEC = 120
        private const val POSITION_SOURCE_FLARM = 3
        private const val MIN_AIRBORNE_ALTITUDE_M = 30.48 // 100 ft
        private const val MIN_AIRBORNE_SPEED_MPS = 20.5778 // 40 kt

        private const val POLL_INTERVAL_MS = 10_000L
        private const val WAIT_FOR_CENTER_MS = 1_000L
        private const val RECONNECT_BACKOFF_START_MS = 2_000L
        private const val RECONNECT_BACKOFF_MAX_MS = 60_000L
    }
}
