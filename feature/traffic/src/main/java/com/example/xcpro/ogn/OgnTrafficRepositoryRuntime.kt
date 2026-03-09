package com.example.xcpro.ogn

import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.core.time.Clock
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs


internal class OgnTrafficRepositoryRuntime(
    internal val parser: OgnAprsLineParser,
    internal val ddbRepository: OgnDdbRepository,
    internal val preferencesRepository: OgnTrafficPreferencesRepository,
    internal val clock: Clock,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : OgnTrafficRepository {

    internal val scope = CoroutineScope(SupervisorJob() + dispatcher)
    internal val targetsByKey = ConcurrentHashMap<String, OgnTrafficTarget>()

    internal val _targets = MutableStateFlow<List<OgnTrafficTarget>>(emptyList())
    override val targets: StateFlow<List<OgnTrafficTarget>> = _targets.asStateFlow()
    internal val suppressedTargetSeenMonoByKey = ConcurrentHashMap<String, Long>()
    internal val lastTimedSourceSeenMonoByKey = ConcurrentHashMap<String, Long>()
    internal val lastAcceptedTimedSourceTimestampWallByKey = ConcurrentHashMap<String, Long>()
    internal val _suppressedTargetIds = MutableStateFlow<Set<String>>(emptySet())
    override val suppressedTargetIds: StateFlow<Set<String>> = _suppressedTargetIds.asStateFlow()

    internal val _snapshot = MutableStateFlow(
        OgnTrafficSnapshot(
            targets = emptyList(),
            connectionState = OgnConnectionState.DISCONNECTED,
            lastError = null,
            subscriptionCenterLat = null,
            subscriptionCenterLon = null,
            receiveRadiusKm = OGN_RECEIVE_RADIUS_DEFAULT_KM,
            ddbCacheAgeMs = null,
            reconnectBackoffMs = null,
            lastReconnectWallMs = null
        )
    )
    override val snapshot: StateFlow<OgnTrafficSnapshot> = _snapshot.asStateFlow()

    internal val _isEnabled = MutableStateFlow(false)
    override val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    @Volatile
    internal var ownFlarmHex: String? = null

    @Volatile
    internal var ownIcaoHex: String? = null

    @Volatile
    internal var clientCallsign: String = generateOgnClientCallsign()

    @Volatile
    internal var center: Center? = null

    @Volatile
    internal var loopJob: Job? = null

    internal val loopJobLock = Any()

    @Volatile
    internal var connectionState: OgnConnectionState = OgnConnectionState.DISCONNECTED

    @Volatile
    internal var lastError: String? = null

    @Volatile
    internal var lastDdbRefreshSuccessWallMs: Long = Long.MIN_VALUE

    @Volatile
    internal var ddbRefreshNextFailureRetryMonoMs: Long = Long.MIN_VALUE

    @Volatile
    internal var ddbRefreshFailureRetryDelayMs: Long = DDB_REFRESH_FAILURE_RETRY_START_MS

    @Volatile
    internal var ddbRefreshInFlight: Boolean = false

    @Volatile
    internal var reconnectBackoffMs: Long? = null

    @Volatile
    internal var lastReconnectWallMs: Long? = null

    @Volatile
    internal var receiveRadiusKm: Int = OGN_RECEIVE_RADIUS_DEFAULT_KM

    @Volatile
    internal var manualReceiveRadiusKm: Int = OGN_RECEIVE_RADIUS_DEFAULT_KM

    @Volatile
    internal var autoReceiveRadiusEnabled: Boolean = false

    @Volatile
    internal var latestAutoReceiveRadiusContext: OgnAutoReceiveRadiusContext? = null

    @Volatile
    internal var pendingAutoRadiusKm: Int? = null

    @Volatile
    internal var pendingAutoRadiusSinceMonoMs: Long = 0L

    @Volatile
    internal var lastAutoRadiusApplyMonoMs: Long = Long.MIN_VALUE

    @Volatile
    internal var activeSubscriptionCenter: Center? = null

    @Volatile
    internal var reconnectRequestedForRadiusChange: Boolean = false
    @Volatile
    internal var lastDistanceRefreshCenter: Center? = null
    @Volatile
    internal var lastDistanceRefreshMonoMs: Long = Long.MIN_VALUE
    @Volatile
    internal var droppedOutOfOrderSourceFrames: Long = 0L
    @Volatile
    internal var droppedImplausibleMotionFrames: Long = 0L

    internal val stateTransitionMutex = Mutex()
    internal val ddbRefreshLock = Any()

    internal var socketFactory: () -> Socket = { Socket() }

    init {
        scope.launch {
            combine(
                preferencesRepository.ownFlarmHexFlow,
                preferencesRepository.ownIcaoHexFlow
            ) { flarmHex, icaoHex ->
                OwnshipFilterConfig(
                    flarmHex = normalizeOgnHex6OrNull(flarmHex),
                    icaoHex = normalizeOgnHex6OrNull(icaoHex)
                )
            }.collect { config ->
                applyOwnshipFilterConfig(config)
            }
        }

        scope.launch {
            preferencesRepository.receiveRadiusKmFlow.collect { radiusKm ->
                applyManualReceiveRadiusKm(radiusKm)
            }
        }

        scope.launch {
            preferencesRepository.autoReceiveRadiusEnabledFlow.collect { enabled ->
                applyAutoReceiveRadiusEnabled(enabled)
            }
        }

        scope.launch {
            preferencesRepository.clientCallsignFlow.collect { persistedCallsign ->
                val resolvedCallsign = persistedCallsign ?: clientCallsign
                if (persistedCallsign == null) {
                    preferencesRepository.setClientCallsign(resolvedCallsign)
                }
                clientCallsign = resolvedCallsign
            }
        }
    }

    override fun start() {
        setEnabled(true)
    }

    override fun stop() {
        setEnabled(false)
    }

    override fun setEnabled(enabled: Boolean) {
        if (_isEnabled.value == enabled) return
        _isEnabled.value = enabled
        scope.launch {
            stateTransitionMutex.withLock {
                if (_isEnabled.value) {
                    ensureLoopRunning()
                } else {
                    stopLoopAndClearTargets()
                }
            }
        }
    }

    override fun updateCenter(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return
        val updatedCenter = Center(latitude = latitude, longitude = longitude)
        val previousCenter = center
        if (previousCenter == updatedCenter) return
        center = updatedCenter

        val nowMonoMs = clock.nowMonoMs()
        if (shouldRefreshTargetDistancesForCenterUpdate(
                previousCenter = previousCenter,
                updatedCenter = updatedCenter,
                nowMonoMs = nowMonoMs
            )
        ) {
            refreshTargetDistancesForCurrentCenter(nowMonoMs)
        } else {
            publishSnapshot()
        }
        if (_isEnabled.value) {
            ensureLoopRunning()
        }
    }

    override fun updateAutoReceiveRadiusContext(
        zoomLevel: Float,
        groundSpeedMs: Double,
        isFlying: Boolean
    ) {
        if (!zoomLevel.isFinite() || !groundSpeedMs.isFinite()) return
        scope.launch {
            applyAutoReceiveRadiusContext(
                OgnAutoReceiveRadiusContext(
                    zoomLevel = zoomLevel,
                    groundSpeedMs = groundSpeedMs.coerceAtLeast(0.0),
                    isFlying = isFlying
                )
            )
        }
    }

    private fun applyOwnshipFilterConfig(config: OwnshipFilterConfig) {
        ownFlarmHex = config.flarmHex
        ownIcaoHex = config.icaoHex

        val nowMonoMs = clock.nowMonoMs()
        var targetsChanged = false
        val iterator = targetsByKey.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!isOwnshipTarget(entry.value, config.flarmHex, config.icaoHex)) continue
            iterator.remove()
            suppressedTargetSeenMonoByKey[entry.key] = nowMonoMs
            targetsChanged = true
        }
        pruneSuppressedTargets(nowMonoMs = nowMonoMs, config = config)

        if (targetsChanged) {
            publishTargets()
        } else {
            publishSuppressedTargetIds()
        }
    }


    internal data class Center(
        val latitude: Double,
        val longitude: Double
    )

    internal data class OwnshipFilterConfig(
        val flarmHex: String?,
        val icaoHex: String?
    )

    internal enum class ConnectionExitReason {
        StreamEnded,
        ReconnectRequested
    }

    private companion object {
        private const val TAG = "OgnTrafficRepository"
        private const val HOST = "aprs.glidernet.org"
        private const val FILTER_PORT = 14580
        private const val APP_NAME = "XCPro"
        private const val APP_VERSION = "0.1"

        private const val METERS_PER_KILOMETER = 1_000.0
        private const val FILTER_UPDATE_MIN_MOVE_METERS = 20_000.0

        private const val SOCKET_CONNECT_TIMEOUT_MS = 10_000
        private const val SOCKET_READ_TIMEOUT_MS = 20_000
        private const val KEEPALIVE_INTERVAL_MS = 60_000L
        private const val STALL_TIMEOUT_MS = 120_000L
        private const val WAIT_FOR_CENTER_MS = 1_000L

        private const val TARGET_STALE_AFTER_MS = 120_000L
        private const val STALE_SWEEP_INTERVAL_MS = 10_000L
        private const val CENTER_DISTANCE_REFRESH_MIN_MOVE_METERS = 200.0
        private const val CENTER_DISTANCE_REFRESH_MIN_INTERVAL_MS = 5_000L
        private const val UNTIMED_SOURCE_FALLBACK_AFTER_MS = 30_000L
        private const val SOURCE_TIME_REWIND_TOLERANCE_MS = 0L
        private const val MAX_PLAUSIBLE_SPEED_MPS = 250.0
        private const val DDB_ACTIVE_REFRESH_CHECK_INTERVAL_MS = 60_000L

        private const val RECONNECT_BACKOFF_START_MS = 1_000L
        private const val RECONNECT_BACKOFF_MAX_MS = 60_000L
        private const val DDB_REFRESH_CHECK_INTERVAL_MS = 60L * 60L * 1000L
        private const val DDB_REFRESH_FAILURE_RETRY_START_MS = 2L * 60L * 1000L
        private const val DDB_REFRESH_FAILURE_RETRY_MAX_MS = 5L * 60L * 1000L
    }
}
