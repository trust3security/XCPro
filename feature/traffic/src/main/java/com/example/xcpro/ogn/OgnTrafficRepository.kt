package com.example.xcpro.ogn

import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.core.time.Clock
import com.example.xcpro.ogn.domain.OgnNetworkAvailabilityPort
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


interface OgnTrafficRepository {
    val targets: StateFlow<List<OgnTrafficTarget>>
    val suppressedTargetIds: StateFlow<Set<String>>
    val snapshot: StateFlow<OgnTrafficSnapshot>
    val isEnabled: StateFlow<Boolean>

    fun setEnabled(enabled: Boolean)
    fun updateCenter(latitude: Double, longitude: Double)
    fun updateAutoReceiveRadiusContext(
        zoomLevel: Float,
        groundSpeedMs: Double,
        isFlying: Boolean
    ) = Unit
    fun start()
    fun stop()
}

@Singleton
class OgnTrafficRepositoryImpl @Inject constructor(
    parser: OgnAprsLineParser,
    ddbRepository: OgnDdbRepository,
    preferencesRepository: OgnTrafficPreferencesRepository,
    clock: Clock,
    @IoDispatcher dispatcher: CoroutineDispatcher,
    networkAvailabilityPort: OgnNetworkAvailabilityPort
) : OgnTrafficRepository {

    private val runtime = OgnTrafficRepositoryRuntime(
        parser = parser,
        ddbRepository = ddbRepository,
        preferencesRepository = preferencesRepository,
        clock = clock,
        ioDispatcher = dispatcher,
        networkAvailabilityPort = networkAvailabilityPort
    )

    internal var socketFactory: () -> Socket
        get() = runtime.socketFactory
        set(value) {
            runtime.socketFactory = value
        }

    override val targets: StateFlow<List<OgnTrafficTarget>>
        get() = runtime.targets
    override val suppressedTargetIds: StateFlow<Set<String>>
        get() = runtime.suppressedTargetIds
    override val snapshot: StateFlow<OgnTrafficSnapshot>
        get() = runtime.snapshot
    override val isEnabled: StateFlow<Boolean>
        get() = runtime.isEnabled

    override fun setEnabled(enabled: Boolean) = runtime.setEnabled(enabled)

    override fun updateCenter(latitude: Double, longitude: Double) =
        runtime.updateCenter(latitude = latitude, longitude = longitude)

    override fun updateAutoReceiveRadiusContext(
        zoomLevel: Float,
        groundSpeedMs: Double,
        isFlying: Boolean
    ) = runtime.updateAutoReceiveRadiusContext(
        zoomLevel = zoomLevel,
        groundSpeedMs = groundSpeedMs,
        isFlying = isFlying
    )

    override fun start() = runtime.start()

    override fun stop() = runtime.stop()
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

internal fun shouldRefreshOgnDistanceForCenterUpdate(
    hasTargets: Boolean,
    previousRefreshLat: Double?,
    previousRefreshLon: Double?,
    nextCenterLat: Double,
    nextCenterLon: Double,
    lastRefreshMonoMs: Long,
    nowMonoMs: Long,
    minMoveMeters: Double,
    minIntervalMs: Long
): Boolean {
    if (!hasTargets) return false
    if (previousRefreshLat == null || previousRefreshLon == null) return true
    if (lastRefreshMonoMs == Long.MIN_VALUE) return true

    val movementMeters = OgnSubscriptionPolicy.haversineMeters(
        lat1 = previousRefreshLat,
        lon1 = previousRefreshLon,
        lat2 = nextCenterLat,
        lon2 = nextCenterLon
    )
    if (movementMeters >= minMoveMeters) return true

    val elapsedMs = nowMonoMs - lastRefreshMonoMs
    return elapsedMs >= minIntervalMs
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

internal fun shouldAcceptOgnSourceTimestamp(
    previousSourceTimestampWallMs: Long?,
    incomingSourceTimestampWallMs: Long?,
    rewindToleranceMs: Long
): Boolean {
    if (previousSourceTimestampWallMs == null || incomingSourceTimestampWallMs == null) {
        return true
    }
    return incomingSourceTimestampWallMs + rewindToleranceMs >= previousSourceTimestampWallMs
}

internal fun shouldAcceptUntimedAfterTimedSourceLock(
    lastTimedSourceSeenMonoMs: Long?,
    incomingSourceTimestampWallMs: Long?,
    nowMonoMs: Long,
    fallbackAfterMs: Long
): Boolean {
    if (incomingSourceTimestampWallMs != null) return true
    if (lastTimedSourceSeenMonoMs == null) return true
    if (fallbackAfterMs <= 0L) return false
    return nowMonoMs - lastTimedSourceSeenMonoMs >= fallbackAfterMs
}

internal fun isPlausibleOgnMotion(
    previousLatitude: Double?,
    previousLongitude: Double?,
    previousSourceTimestampWallMs: Long?,
    previousSeenMonoMs: Long?,
    incomingLatitude: Double,
    incomingLongitude: Double,
    incomingSourceTimestampWallMs: Long?,
    incomingSeenMonoMs: Long?,
    maxPlausibleSpeedMps: Double
): Boolean {
    if (previousLatitude == null || previousLongitude == null) return true
    val dtMs = when {
        previousSourceTimestampWallMs != null && incomingSourceTimestampWallMs != null ->
            incomingSourceTimestampWallMs - previousSourceTimestampWallMs
        previousSeenMonoMs != null && incomingSeenMonoMs != null ->
            incomingSeenMonoMs - previousSeenMonoMs
        else -> return true
    }
    if (dtMs <= 0L) return true
    if (maxPlausibleSpeedMps <= 0.0 || !maxPlausibleSpeedMps.isFinite()) return true

    val distanceMeters = OgnSubscriptionPolicy.haversineMeters(
        lat1 = previousLatitude,
        lon1 = previousLongitude,
        lat2 = incomingLatitude,
        lon2 = incomingLongitude
    )
    if (!distanceMeters.isFinite()) return false
    val speedMps = distanceMeters / (dtMs / 1000.0)
    return speedMps <= maxPlausibleSpeedMps
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
