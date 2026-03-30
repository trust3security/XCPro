package com.example.xcpro.ogn

import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.time.Clock
import com.example.xcpro.ogn.domain.OgnNetworkAvailabilityPort
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
internal class OgnTrafficRepositoryRuntime(
    internal val parser: OgnAprsLineParser,
    internal val ddbRepository: OgnDdbRepository,
    internal val preferencesRepository: OgnTrafficPreferencesRepository,
    internal val clock: Clock,
    @IoDispatcher internal val ioDispatcher: CoroutineDispatcher,
    internal val networkAvailabilityPort: OgnNetworkAvailabilityPort
) : OgnTrafficRepository {

    internal val writerDispatcher: CoroutineDispatcher = ioDispatcher.limitedParallelism(1)
    private val runtimeJob = SupervisorJob()
    internal val scope = CoroutineScope(runtimeJob + writerDispatcher)
    internal val ioScope = CoroutineScope(runtimeJob + ioDispatcher)
    internal val targetsByKey = ConcurrentHashMap<String, OgnTrafficTarget>()

    internal val _targets = MutableStateFlow<List<OgnTrafficTarget>>(emptyList())
    override val targets: StateFlow<List<OgnTrafficTarget>> = _targets.asStateFlow()

    internal val suppressedTargetSeenMonoByKey = ConcurrentHashMap<String, Long>()
    internal val lastTimedSourceSeenMonoByKey = ConcurrentHashMap<String, Long>()
    internal val lastAcceptedTimedSourceTimestampWallByKey = ConcurrentHashMap<String, Long>()
    internal val _suppressedTargetIds = MutableStateFlow<Set<String>>(emptySet())
    override val suppressedTargetIds: StateFlow<Set<String>> = _suppressedTargetIds.asStateFlow()

    internal val centerState = MutableStateFlow<Center?>(null)
    internal val _snapshot = MutableStateFlow(
        OgnTrafficSnapshot(
            targets = emptyList(),
            connectionState = OgnConnectionState.DISCONNECTED,
            connectionIssue = null,
            lastError = null,
            subscriptionCenterLat = null,
            subscriptionCenterLon = null,
            receiveRadiusKm = OGN_RECEIVE_RADIUS_DEFAULT_KM,
            ddbCacheAgeMs = null,
            reconnectBackoffMs = null,
            lastReconnectWallMs = null,
            networkOnline = true
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
    internal var connectionIssue: OgnConnectionIssue? = null

    @Volatile
    internal var lastError: String? = null

    @Volatile
    internal var networkOnline: Boolean = true

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

        scope.launch {
            networkAvailabilityPort.isOnline.collect { isOnline ->
                applyNetworkOnline(isOnline)
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
        scope.launch {
            applySetEnabled(enabled)
        }
    }

    override fun updateCenter(latitude: Double, longitude: Double) {
        scope.launch {
            applyUpdateCenter(latitude = latitude, longitude = longitude)
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

    internal suspend fun applySetEnabled(enabled: Boolean) {
        if (_isEnabled.value == enabled) return
        _isEnabled.value = enabled
        if (enabled) {
            ensureLoopRunning()
        } else {
            stopLoopAndClearTargets()
        }
    }

    internal fun applyUpdateCenter(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return
        val updatedCenter = Center(latitude = latitude, longitude = longitude)
        val previousCenter = center
        if (previousCenter == updatedCenter) return
        center = updatedCenter
        centerState.value = updatedCenter

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

    private fun applyNetworkOnline(isOnline: Boolean) {
        if (networkOnline == isOnline) return
        networkOnline = isOnline
        publishSnapshot()
    }

    internal suspend fun <T> runOnWriter(
        block: OgnTrafficRepositoryRuntime.() -> T
    ): T = kotlinx.coroutines.withContext(writerDispatcher) {
        block()
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
        ReconnectRequested,
        Stopped
    }

    private companion object {
        private const val DDB_REFRESH_FAILURE_RETRY_START_MS = 2L * 60L * 1000L
    }
}
