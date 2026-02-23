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

interface OgnTrafficRepository {
    val targets: StateFlow<List<OgnTrafficTarget>>
    val suppressedTargetIds: StateFlow<Set<String>>
    val snapshot: StateFlow<OgnTrafficSnapshot>
    val isEnabled: StateFlow<Boolean>

    fun setEnabled(enabled: Boolean)
    fun updateCenter(latitude: Double, longitude: Double)
    fun start()
    fun stop()
}

@Singleton
class OgnTrafficRepositoryImpl @Inject constructor(
    private val parser: OgnAprsLineParser,
    private val ddbRepository: OgnDdbRepository,
    private val preferencesRepository: OgnTrafficPreferencesRepository,
    private val clock: Clock,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : OgnTrafficRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val targetsByKey = ConcurrentHashMap<String, OgnTrafficTarget>()

    private val _targets = MutableStateFlow<List<OgnTrafficTarget>>(emptyList())
    override val targets: StateFlow<List<OgnTrafficTarget>> = _targets.asStateFlow()
    private val suppressedTargetSeenMonoByKey = ConcurrentHashMap<String, Long>()
    private val _suppressedTargetIds = MutableStateFlow<Set<String>>(emptySet())
    override val suppressedTargetIds: StateFlow<Set<String>> = _suppressedTargetIds.asStateFlow()

    private val _snapshot = MutableStateFlow(
        OgnTrafficSnapshot(
            targets = emptyList(),
            connectionState = OgnConnectionState.DISCONNECTED,
            lastError = null,
            subscriptionCenterLat = null,
            subscriptionCenterLon = null,
            receiveRadiusKm = RECEIVE_RADIUS_KM.toInt(),
            ddbCacheAgeMs = null,
            reconnectBackoffMs = null,
            lastReconnectWallMs = null
        )
    )
    override val snapshot: StateFlow<OgnTrafficSnapshot> = _snapshot.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    override val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    @Volatile
    private var ownFlarmHex: String? = null

    @Volatile
    private var ownIcaoHex: String? = null

    @Volatile
    private var center: Center? = null

    @Volatile
    private var loopJob: Job? = null

    private val loopJobLock = Any()

    @Volatile
    private var connectionState: OgnConnectionState = OgnConnectionState.DISCONNECTED

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var lastDdbRefreshAttemptWallMs: Long = 0L

    @Volatile
    private var reconnectBackoffMs: Long? = null

    @Volatile
    private var lastReconnectWallMs: Long? = null

    @Volatile
    private var activeSubscriptionCenter: Center? = null

    private val stateTransitionMutex = Mutex()

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
        center = Center(latitude = latitude, longitude = longitude)
        refreshTargetDistancesForCurrentCenter()
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

    private fun ensureLoopRunning() {
        synchronized(loopJobLock) {
            val existing = loopJob
            if (existing != null && existing.isActive) return
            loopJob = scope.launch {
                runConnectionLoop()
            }
        }
    }

    private suspend fun stopLoopAndClearTargets() {
        val jobToCancel = synchronized(loopJobLock) {
            val existing = loopJob
            loopJob = null
            existing
        }
        jobToCancel?.cancelAndJoin()
        if (_isEnabled.value) return
        targetsByKey.clear()
        suppressedTargetSeenMonoByKey.clear()
        _targets.value = emptyList()
        _suppressedTargetIds.value = emptySet()
        activeSubscriptionCenter = null
        connectionState = OgnConnectionState.DISCONNECTED
        lastError = null
        publishSnapshot()
    }

    private suspend fun runConnectionLoop() {
        val thisJob = currentCoroutineContext()[Job]
        var backoffMs = RECONNECT_BACKOFF_START_MS
        try {
            while (_isEnabled.value) {
                refreshDdbIfDue()
                val centerAtConnect = waitForCenter() ?: break
                try {
                    connectAndRead(centerAtConnect)
                    connectionState = OgnConnectionState.DISCONNECTED
                    activeSubscriptionCenter = null
                    backoffMs = RECONNECT_BACKOFF_START_MS
                    reconnectBackoffMs = null
                } catch (t: Throwable) {
                    connectionState = OgnConnectionState.ERROR
                    lastError = sanitizeError(t)
                    publishSnapshot()
                    AppLogger.w(TAG, "Traffic stream disconnected: ${t.message}")
                }

                sweepStaleTargets(clock.nowMonoMs())
                if (!_isEnabled.value) break
                reconnectBackoffMs = backoffMs
                lastReconnectWallMs = clock.nowWallMs()
                publishSnapshot()
                delay(backoffMs)
                backoffMs = (backoffMs * 2L).coerceAtMost(RECONNECT_BACKOFF_MAX_MS)
            }
            activeSubscriptionCenter = null
            connectionState = OgnConnectionState.DISCONNECTED
            reconnectBackoffMs = null
            publishSnapshot()
        } finally {
            synchronized(loopJobLock) {
                if (loopJob == thisJob) {
                    loopJob = null
                }
            }
        }
    }

    private suspend fun waitForCenter(): Center? {
        while (_isEnabled.value) {
            center?.let { return it }
            delay(WAIT_FOR_CENTER_MS)
        }
        return null
    }

    private fun connectAndRead(centerAtConnect: Center) {
        val socket = socketFactory()
        var reader: BufferedReader? = null
        var writer: BufferedWriter? = null
        try {
            connectionState = OgnConnectionState.CONNECTING
            lastError = null
            publishSnapshot()

            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = SOCKET_READ_TIMEOUT_MS
            socket.connect(
                InetSocketAddress(HOST, FILTER_PORT),
                SOCKET_CONNECT_TIMEOUT_MS
            )

            reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.ISO_8859_1))

            writer.write(buildLogin(centerAtConnect))
            writer.newLine()
            writer.flush()

            activeSubscriptionCenter = centerAtConnect
            refreshTargetDistancesForCurrentCenter()
            publishSnapshot()
            var connectionEstablished = false

            var lastSweepMonoMs = clock.nowMonoMs()
            var lastKeepaliveMonoMs = lastSweepMonoMs
            var lastReceiveMonoMs = lastSweepMonoMs

            while (_isEnabled.value) {
                val nowMonoMs = clock.nowMonoMs()
                val activeCenter = center
                if (activeCenter != null &&
                    OgnSubscriptionPolicy.shouldReconnectByCenterMoveMeters(
                        previousLat = centerAtConnect.latitude,
                        previousLon = centerAtConnect.longitude,
                        nextLat = activeCenter.latitude,
                        nextLon = activeCenter.longitude,
                        thresholdMeters = FILTER_UPDATE_MIN_MOVE_METERS
                    )
                ) {
                    AppLogger.d(TAG, "Subscription center moved; reconnecting with updated filter")
                    return
                }

                try {
                    val line = reader.readLine() ?: return
                    lastReceiveMonoMs = nowMonoMs
                    when (parseLogrespStatus(line)) {
                        OgnLogrespStatus.VERIFIED -> {
                            if (!connectionEstablished) {
                                connectionEstablished = true
                                connectionState = OgnConnectionState.CONNECTED
                                publishSnapshot()
                                AppLogger.i(
                                    TAG,
                                    "Connected to OGN traffic feed with ${RECEIVE_RADIUS_KM.toInt()}km radius"
                                )
                            }
                        }

                        OgnLogrespStatus.UNVERIFIED -> {
                            throw IllegalStateException("OGN login unverified")
                        }

                        null -> {
                            val sawTraffic = handleIncomingLine(line, nowMonoMs, centerAtConnect)
                            if (sawTraffic && !connectionEstablished) {
                                connectionEstablished = true
                                connectionState = OgnConnectionState.CONNECTED
                                publishSnapshot()
                                AppLogger.i(
                                    TAG,
                                    "Connected to OGN traffic feed with ${RECEIVE_RADIUS_KM.toInt()}km radius"
                                )
                            }
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // Expected timeout so we can evaluate keepalive/reconnect conditions.
                }

                if (nowMonoMs - lastReceiveMonoMs >= STALL_TIMEOUT_MS) {
                    throw IllegalStateException("OGN stream stalled")
                }

                if (nowMonoMs - lastSweepMonoMs >= STALE_SWEEP_INTERVAL_MS) {
                    sweepStaleTargets(nowMonoMs)
                    lastSweepMonoMs = nowMonoMs
                }

                if (nowMonoMs - lastKeepaliveMonoMs >= KEEPALIVE_INTERVAL_MS) {
                    writer.write("#keepalive")
                    writer.newLine()
                    writer.flush()
                    lastKeepaliveMonoMs = nowMonoMs
                }
            }
        } finally {
            activeSubscriptionCenter = null
            runCatching { reader?.close() }
            runCatching { writer?.close() }
            runCatching { socket.close() }
        }
    }

    private fun handleIncomingLine(line: String, nowMonoMs: Long, centerAtConnect: Center): Boolean {
        val parsed = parser.parseTraffic(line, nowMonoMs) ?: return false
        val requestedCenter = center
        if (!isWithinReceiveRadiusMeters(
                targetLat = parsed.latitude,
                targetLon = parsed.longitude,
                requestedCenterLat = requestedCenter?.latitude,
                requestedCenterLon = requestedCenter?.longitude,
                subscriptionCenterLat = centerAtConnect.latitude,
                subscriptionCenterLon = centerAtConnect.longitude,
                radiusMeters = RECEIVE_RADIUS_METERS
            )
        ) {
            return true
        }

        val targetKey = parsed.canonicalKey
        val ddbIdentity = parsed.deviceIdHex?.let { deviceIdHex ->
            ddbRepository.lookup(
                addressType = parsed.addressType,
                deviceIdHex = deviceIdHex
            )
        }
        if (ddbIdentity?.tracked == false) {
            val removedTarget = targetsByKey.remove(targetKey)
            val removedSuppressed = suppressedTargetSeenMonoByKey.remove(targetKey) != null
            if (removedTarget != null) {
                publishTargets()
            } else if (removedSuppressed) {
                publishSuppressedTargetIds()
            }
            return true
        }
        val identity = mergeOgnIdentity(ddbIdentity = ddbIdentity, parsedIdentity = parsed.identity)

        val label = resolveDisplayLabel(parsed, identity)
        val previousTrackDegrees = targetsByKey[targetKey]?.trackDegrees
        val stabilizedTrackDegrees = stabilizeTrackDegrees(
            incomingTrackDegrees = parsed.trackDegrees,
            groundSpeedMps = parsed.groundSpeedMps,
            previousTrackDegrees = previousTrackDegrees
        )
        val enriched = parsed.copy(
            displayLabel = label,
            identity = identity,
            trackDegrees = stabilizedTrackDegrees,
            lastSeenMillis = nowMonoMs,
            distanceMeters = resolveDistanceMeters(
                targetLat = parsed.latitude,
                targetLon = parsed.longitude,
                requestedCenter = requestedCenter,
                subscriptionCenter = centerAtConnect
            )
        )
        if (isOwnshipTarget(enriched, ownFlarmHex, ownIcaoHex)) {
            val removed = targetsByKey.remove(targetKey)
            suppressedTargetSeenMonoByKey[targetKey] = nowMonoMs
            if (removed != null) {
                publishTargets()
            } else {
                publishSuppressedTargetIds()
            }
            return true
        }

        if (suppressedTargetSeenMonoByKey.remove(targetKey) != null) {
            publishSuppressedTargetIds()
        }
        targetsByKey[targetKey] = enriched
        publishTargets()
        return true
    }

    private fun resolveDisplayLabel(
        parsed: OgnTrafficTarget,
        identity: OgnTrafficIdentity?
    ): String {
        val fallback = parsed.deviceIdHex ?: parsed.callsign
        if (identity == null) return fallback
        if (identity.tracked == false) return fallback
        if (identity.identified == false) return fallback
        return identity.competitionNumber?.takeIf { it.isNotBlank() }
            ?: identity.registration?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun refreshTargetDistancesForCurrentCenter() {
        val requestedCenter = center
        val subscriptionCenter = activeSubscriptionCenter
        if (requestedCenter == null && subscriptionCenter == null) return

        var changed = false
        for ((id, target) in targetsByKey.entries) {
            val distanceMeters = resolveDistanceMeters(
                targetLat = target.latitude,
                targetLon = target.longitude,
                requestedCenter = requestedCenter,
                subscriptionCenter = subscriptionCenter
            )
            if (target.distanceMeters != distanceMeters) {
                targetsByKey[id] = target.copy(distanceMeters = distanceMeters)
                changed = true
            }
        }
        if (changed) {
            publishTargets()
        }
    }

    private fun resolveDistanceMeters(
        targetLat: Double,
        targetLon: Double,
        requestedCenter: Center?,
        subscriptionCenter: Center?
    ): Double? {
        val reference = requestedCenter ?: subscriptionCenter ?: return null
        val distanceMeters = OgnSubscriptionPolicy.haversineMeters(
            lat1 = reference.latitude,
            lon1 = reference.longitude,
            lat2 = targetLat,
            lon2 = targetLon
        )
        return distanceMeters.takeIf { it.isFinite() }
    }

    private fun sweepStaleTargets(nowMonoMs: Long) {
        var removed = false
        val iterator = targetsByKey.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.isStale(nowMonoMs, TARGET_STALE_AFTER_MS)) {
                iterator.remove()
                removed = true
            }
        }
        val suppressedChanged = pruneSuppressedTargets(
            nowMonoMs = nowMonoMs,
            config = currentOwnshipFilterConfig()
        )
        if (removed) {
            publishTargets()
        } else if (suppressedChanged) {
            publishSuppressedTargetIds()
        }
    }

    private fun publishTargets() {
        _targets.value = targetsByKey.values
            .sortedWith(compareBy({ it.displayLabel }, { it.canonicalKey }))
        publishSuppressedTargetIds()
    }

    private fun publishSuppressedTargetIds() {
        val suppressed = suppressedTargetSeenMonoByKey.keys.toSet()
        if (_suppressedTargetIds.value != suppressed) {
            _suppressedTargetIds.value = suppressed
        }
        publishSnapshot()
    }

    private fun pruneSuppressedTargets(
        nowMonoMs: Long,
        config: OwnshipFilterConfig
    ): Boolean {
        if (suppressedTargetSeenMonoByKey.isEmpty()) return false
        var changed = false
        val iterator = suppressedTargetSeenMonoByKey.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val stale = nowMonoMs - entry.value > TARGET_STALE_AFTER_MS
            val noLongerMatchesFilter = !matchesAnyOwnshipKey(entry.key, config)
            if (stale || noLongerMatchesFilter) {
                iterator.remove()
                changed = true
            }
        }
        return changed
    }

    private fun matchesAnyOwnshipKey(
        canonicalKey: String,
        config: OwnshipFilterConfig
    ): Boolean {
        val flarm = config.flarmHex
        if (flarm != null && canonicalKey == "FLARM:$flarm") return true

        val icao = config.icaoHex
        if (icao != null && canonicalKey == "ICAO:$icao") return true

        return false
    }

    private fun currentOwnshipFilterConfig(): OwnshipFilterConfig =
        OwnshipFilterConfig(
            flarmHex = ownFlarmHex,
            icaoHex = ownIcaoHex
        )

    private fun publishSnapshot() {
        val activeCenter = center
        val nowWallMs = clock.nowWallMs()
        val ddbUpdatedAt = ddbRepository.lastUpdateWallMs()
        val ddbAge = if (ddbUpdatedAt > 0L && nowWallMs >= ddbUpdatedAt) {
            nowWallMs - ddbUpdatedAt
        } else {
            null
        }
        _snapshot.value = OgnTrafficSnapshot(
            targets = _targets.value,
            suppressedTargetIds = _suppressedTargetIds.value,
            connectionState = connectionState,
            lastError = lastError,
            subscriptionCenterLat = activeCenter?.latitude,
            subscriptionCenterLon = activeCenter?.longitude,
            receiveRadiusKm = RECEIVE_RADIUS_KM.toInt(),
            ddbCacheAgeMs = ddbAge,
            reconnectBackoffMs = reconnectBackoffMs,
            lastReconnectWallMs = lastReconnectWallMs,
            activeSubscriptionCenterLat = activeSubscriptionCenter?.latitude,
            activeSubscriptionCenterLon = activeSubscriptionCenter?.longitude
        )
    }

    private suspend fun refreshDdbIfDue() {
        val nowWallMs = clock.nowWallMs()
        if (nowWallMs - lastDdbRefreshAttemptWallMs < DDB_REFRESH_CHECK_INTERVAL_MS) return
        lastDdbRefreshAttemptWallMs = nowWallMs
        runCatching { ddbRepository.refreshIfNeeded() }
            .onFailure { AppLogger.w(TAG, "DDB refresh failed: ${it.message}") }
    }

    private fun buildLogin(center: Center): String {
        val passcode = generateAprsPasscode(CLIENT_CALLSIGN)
        val filter = "r/${formatCoord(center.latitude)}/${formatCoord(center.longitude)}/${RECEIVE_RADIUS_KM.toInt()}"
        return "user $CLIENT_CALLSIGN pass $passcode vers $APP_NAME $APP_VERSION filter $filter"
    }

    private fun generateAprsPasscode(callsign: String): Int {
        val base = callsign.uppercase(Locale.US).substringBefore("-")
        var hash = 0x73e2
        var i = 0
        while (i < base.length) {
            hash = hash xor (base[i].code shl 8)
            if (i + 1 < base.length) {
                hash = hash xor base[i + 1].code
            }
            i += 2
        }
        return hash and 0x7fff
    }

    private fun formatCoord(value: Double): String = String.format(Locale.US, "%.5f", value)

    private fun sanitizeError(throwable: Throwable): String {
        val type = throwable::class.java.simpleName.ifBlank { "Error" }
        return type.take(80)
    }

    private data class Center(
        val latitude: Double,
        val longitude: Double
    )

    private data class OwnshipFilterConfig(
        val flarmHex: String?,
        val icaoHex: String?
    )

    private companion object {
        private const val TAG = "OgnTrafficRepository"
        private const val HOST = "aprs.glidernet.org"
        private const val FILTER_PORT = 14580
        private const val CLIENT_CALLSIGN = "OGNXC1"
        private const val APP_NAME = "XCPro"
        private const val APP_VERSION = "0.1"

        // Product contract: 300 km diameter around user position -> 150 km radius.
        private const val METERS_PER_KILOMETER = 1_000.0
        private const val RECEIVE_RADIUS_METERS = 150_000.0
        private const val RECEIVE_RADIUS_KM = RECEIVE_RADIUS_METERS / METERS_PER_KILOMETER
        private const val FILTER_UPDATE_MIN_MOVE_METERS = 20_000.0

        private const val SOCKET_CONNECT_TIMEOUT_MS = 10_000
        private const val SOCKET_READ_TIMEOUT_MS = 20_000
        private const val KEEPALIVE_INTERVAL_MS = 60_000L
        private const val STALL_TIMEOUT_MS = 120_000L
        private const val WAIT_FOR_CENTER_MS = 1_000L

        private const val TARGET_STALE_AFTER_MS = 120_000L
        private const val STALE_SWEEP_INTERVAL_MS = 10_000L

        private const val RECONNECT_BACKOFF_START_MS = 1_000L
        private const val RECONNECT_BACKOFF_MAX_MS = 60_000L
        private const val DDB_REFRESH_CHECK_INTERVAL_MS = 60L * 60L * 1000L
    }
}

internal enum class OgnLogrespStatus {
    VERIFIED,
    UNVERIFIED
}

internal fun parseLogrespStatus(line: String): OgnLogrespStatus? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || !trimmed.startsWith("#")) return null
    val normalized = trimmed.lowercase(Locale.US)
    if (!normalized.contains("logresp")) return null
    return when {
        normalized.contains("unverified") -> OgnLogrespStatus.UNVERIFIED
        normalized.contains("verified") -> OgnLogrespStatus.VERIFIED
        else -> null
    }
}

internal fun isWithinReceiveRadiusMeters(
    targetLat: Double,
    targetLon: Double,
    requestedCenterLat: Double?,
    requestedCenterLon: Double?,
    subscriptionCenterLat: Double,
    subscriptionCenterLon: Double,
    radiusMeters: Double
): Boolean {
    val hasRequestedCenter = requestedCenterLat != null && requestedCenterLon != null
    val centerLat = if (hasRequestedCenter) requestedCenterLat!! else subscriptionCenterLat
    val centerLon = if (hasRequestedCenter) requestedCenterLon!! else subscriptionCenterLon
    val distanceMeters = OgnSubscriptionPolicy.haversineMeters(
        lat1 = centerLat,
        lon1 = centerLon,
        lat2 = targetLat,
        lon2 = targetLon
    )
    return distanceMeters <= radiusMeters
}

internal fun isOwnshipTarget(
    target: OgnTrafficTarget,
    ownFlarmHex: String?,
    ownIcaoHex: String?
): Boolean {
    val addressHex = normalizeOgnHex6OrNull(target.addressHex) ?: return false
    return when (target.addressType) {
        OgnAddressType.FLARM -> ownFlarmHex != null && addressHex == ownFlarmHex
        OgnAddressType.ICAO -> ownIcaoHex != null && addressHex == ownIcaoHex
        OgnAddressType.UNKNOWN -> false
    }
}

internal fun mergeOgnIdentity(
    ddbIdentity: OgnTrafficIdentity?,
    parsedIdentity: OgnTrafficIdentity?
): OgnTrafficIdentity? {
    if (ddbIdentity == null) return parsedIdentity

    val parsedAircraftTypeCode = parsedIdentity?.aircraftTypeCode
    if (ddbIdentity.aircraftTypeCode != null || parsedAircraftTypeCode == null) return ddbIdentity

    return ddbIdentity.copy(aircraftTypeCode = parsedAircraftTypeCode)
}

internal fun stabilizeTrackDegrees(
    incomingTrackDegrees: Double?,
    groundSpeedMps: Double?,
    previousTrackDegrees: Double?
): Double? {
    val previous = previousTrackDegrees
        ?.takeIf { it.isFinite() }
        ?.let(::normalizeHeading360)
    val incoming = incomingTrackDegrees
        ?.takeIf { it.isFinite() }
        ?.let(::normalizeHeading360)

    if (incoming == null) return previous

    val speed = groundSpeedMps
    if (speed == null || !speed.isFinite() || speed < TRACK_UPDATE_MIN_SPEED_MPS) {
        return previous ?: incoming
    }

    if (previous == null) return incoming

    val delta = shortestAngularDeltaDegrees(previous, incoming)
    return if (abs(delta) < TRACK_UPDATE_MIN_DELTA_DEG) previous else incoming
}

internal fun shortestAngularDeltaDegrees(fromDeg: Double, toDeg: Double): Double {
    var delta = (toDeg - fromDeg) % 360.0
    if (delta > 180.0) delta -= 360.0
    if (delta <= -180.0) delta += 360.0
    return delta
}

private fun normalizeHeading360(value: Double): Double {
    var normalized = value % 360.0
    if (normalized < 0.0) normalized += 360.0
    return normalized
}

private const val TRACK_UPDATE_MIN_SPEED_MPS = 5.0
private const val TRACK_UPDATE_MIN_DELTA_DEG = 4.0
