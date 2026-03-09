package com.example.xcpro.ogn

import com.example.xcpro.core.time.FakeClock
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlinx.coroutines.flow.MutableStateFlow

internal fun emitClimbSamples(
    trafficRepository: FakeOgnTrafficRepository,
    clock: FakeClock,
    baseTimestampMs: Long = 0L,
    advance: () -> Unit
) {
    val samples = listOf(
        sampleTarget(climbMps = 1.2, altitudeMeters = 1000.0, timestampMs = baseTimestampMs + 0L, trackDegrees = 0.0),
        sampleTarget(climbMps = 1.6, altitudeMeters = 1020.0, timestampMs = baseTimestampMs + 10_000L, trackDegrees = 180.0),
        sampleTarget(climbMps = 1.9, altitudeMeters = 1040.0, timestampMs = baseTimestampMs + 20_000L, trackDegrees = 0.0),
        sampleTarget(climbMps = 1.8, altitudeMeters = 1060.0, timestampMs = baseTimestampMs + 30_000L, trackDegrees = 180.0),
        sampleTarget(climbMps = 1.7, altitudeMeters = 1080.0, timestampMs = baseTimestampMs + 40_000L, trackDegrees = 0.0),
        sampleTarget(climbMps = 1.6, altitudeMeters = 1100.0, timestampMs = baseTimestampMs + 50_000L, trackDegrees = 180.0)
    )

    for (target in samples) {
        clock.setMonoMs(target.lastSeenMillis)
        clock.setWallMs(target.lastSeenMillis)
        trafficRepository.targets.value = listOf(target)
        advance()
    }
}

internal fun emitClimbSamplesForTarget(
    trafficRepository: FakeOgnTrafficRepository,
    clock: FakeClock,
    id: String,
    baseTimestampMs: Long,
    latitude: Double,
    longitude: Double,
    climbRates: List<Double>,
    advance: () -> Unit
) {
    for (index in climbRates.indices) {
        val timestampMs = baseTimestampMs + index * 10_000L
        val trackDegrees = if (index % 2 == 0) 0.0 else 180.0
        val altitudeMeters = 1000.0 + index * 20.0
        clock.setMonoMs(timestampMs)
        clock.setWallMs(timestampMs)
        trafficRepository.targets.value = listOf(
            sampleTarget(
                id = id,
                climbMps = climbRates[index],
                altitudeMeters = altitudeMeters,
                timestampMs = timestampMs,
                latitude = latitude,
                longitude = longitude,
                trackDegrees = trackDegrees
            )
        )
        advance()
    }
}

internal fun emitLowTurnClimbSamples(
    trafficRepository: FakeOgnTrafficRepository,
    clock: FakeClock,
    advance: () -> Unit
) {
    val samples = listOf(
        sampleTarget(climbMps = 1.2, altitudeMeters = 1000.0, timestampMs = 0L, trackDegrees = 0.0),
        sampleTarget(climbMps = 1.6, altitudeMeters = 1020.0, timestampMs = 10_000L, trackDegrees = 180.0),
        sampleTarget(climbMps = 1.9, altitudeMeters = 1040.0, timestampMs = 20_000L, trackDegrees = 0.0),
        sampleTarget(climbMps = 1.8, altitudeMeters = 1060.0, timestampMs = 30_000L, trackDegrees = 180.0),
        sampleTarget(climbMps = 1.7, altitudeMeters = 1080.0, timestampMs = 40_000L, trackDegrees = 0.0)
    )
    for (target in samples) {
        clock.setMonoMs(target.lastSeenMillis)
        clock.setWallMs(target.lastSeenMillis)
        trafficRepository.targets.value = listOf(target)
        advance()
    }
}

internal fun clearConfirmedTrackerHotspotIds(repository: OgnThermalRepositoryImpl) {
    val trackerMapField = OgnThermalRepositoryImpl::class.java
        .getDeclaredField("trackerByTargetId")
    trackerMapField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val trackerMap = trackerMapField.get(repository) as MutableMap<String, Any>
    for (tracker in trackerMap.values) {
        val confirmedField = tracker.javaClass.getDeclaredField("confirmed")
        confirmedField.isAccessible = true
        if (!confirmedField.getBoolean(tracker)) continue
        val hotspotIdField = tracker.javaClass.getDeclaredField("hotspotId")
        hotspotIdField.isAccessible = true
        hotspotIdField.set(tracker, null)
    }
}

internal fun sampleTarget(
    id: String = "ABCD01",
    climbMps: Double,
    altitudeMeters: Double,
    timestampMs: Long,
    latitude: Double = -35.1,
    longitude: Double = 149.1,
    trackDegrees: Double = 120.0
): OgnTrafficTarget = OgnTrafficTarget(
    id = id,
    callsign = id,
    destination = "APRS",
    latitude = latitude,
    longitude = longitude,
    altitudeMeters = altitudeMeters,
    trackDegrees = trackDegrees,
    groundSpeedMps = 26.0,
    verticalSpeedMps = climbMps,
    deviceIdHex = normalizeOgnHex6OrNull(id),
    signalDb = 12.0,
    displayLabel = id,
    identity = null,
    rawComment = "",
    rawLine = "",
    timestampMillis = timestampMs,
    lastSeenMillis = timestampMs
)

internal fun shutdownRepository(trafficRepository: FakeOgnTrafficRepository) {
    trafficRepository.setEnabled(false)
}

internal fun adjacentLegacyGridCellLongitudes(
    baseLatitude: Double,
    nearLongitude: Double
): Pair<Double, Double> {
    val metersPerDegreeLongitude =
        111_320.0 * abs(cos(Math.toRadians(baseLatitude))).coerceAtLeast(1.0e-6)
    val nearLongitudeMeters = nearLongitude * metersPerDegreeLongitude
    val nextBoundaryMeters = (floor(nearLongitudeMeters / 700.0) + 1.0) * 700.0
    val boundaryLongitude = nextBoundaryMeters / metersPerDegreeLongitude
    return (boundaryLongitude - 0.0001) to (boundaryLongitude + 0.0001)
}

internal class FakeOgnTrafficRepository : OgnTrafficRepository {
    override val targets = MutableStateFlow<List<OgnTrafficTarget>>(emptyList())
    override val suppressedTargetIds = MutableStateFlow<Set<String>>(emptySet())
    override val snapshot = MutableStateFlow(
        OgnTrafficSnapshot(
            targets = emptyList(),
            connectionState = OgnConnectionState.DISCONNECTED,
            lastError = null,
            subscriptionCenterLat = null,
            subscriptionCenterLon = null,
            receiveRadiusKm = 150,
            ddbCacheAgeMs = null,
            reconnectBackoffMs = null,
            lastReconnectWallMs = null
        )
    )
    override val isEnabled = MutableStateFlow(true)

    override fun setEnabled(enabled: Boolean) {
        isEnabled.value = enabled
    }

    override fun updateCenter(latitude: Double, longitude: Double) = Unit

    override fun start() {
        setEnabled(true)
    }

    override fun stop() {
        setEnabled(false)
    }
}
