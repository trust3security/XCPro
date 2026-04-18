package com.trust3.xcpro.adsb

import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

internal suspend fun AdsbTrafficRepositoryRuntime.applySetEnabled(enabled: Boolean) {
    if (_isEnabled.value == enabled) {
        if (enabled) {
            stateTransitionMutex.withLock {
                ensureLoopRunning()
            }
        }
        return
    }
    _isEnabled.value = enabled
    stateTransitionMutex.withLock {
        if (_isEnabled.value) {
            ensureLoopRunning()
        } else {
            stopLoop(clearTargets = false)
        }
    }
}

internal fun AdsbTrafficRepositoryRuntime.applyClearTargets() {
    clearCachedTargets()
    publishSnapshot()
}

internal fun AdsbTrafficRepositoryRuntime.applyUpdateCenter(latitude: Double, longitude: Double) {
    if (!latitude.isFinite() || !longitude.isFinite()) return
    if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return
    val updatedCenter = AdsbTrafficRepositoryRuntime.Center(
        latitude = latitude,
        longitude = longitude
    )
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

internal fun AdsbTrafficRepositoryRuntime.applyUpdateOwnshipOrigin(
    latitude: Double,
    longitude: Double
) {
    if (!latitude.isFinite() || !longitude.isFinite()) return
    if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return
    val updatedOrigin = AdsbTrafficRepositoryRuntime.Center(
        latitude = latitude,
        longitude = longitude
    )
    if (ownshipOrigin == updatedOrigin) {
        ownshipReferenceLastUpdateMonoMs = clock.nowMonoMs()
        if (_isEnabled.value) {
            center?.let { activeCenter -> publishFromStore(activeCenter) } ?: publishSnapshot()
        } else {
            publishSnapshot()
        }
        return
    }
    ownshipOrigin = updatedOrigin
    ownshipReferenceLastUpdateMonoMs = clock.nowMonoMs()
    if (!_isEnabled.value) return
    center?.let { activeCenter -> publishFromStore(activeCenter) }
}

internal fun AdsbTrafficRepositoryRuntime.applyUpdateOwnshipMotion(
    trackDeg: Double?,
    speedMps: Double?
) {
    val normalizedTrackDeg =
        trackDeg?.takeIf { it.isFinite() }?.let { ((it % 360.0) + 360.0) % 360.0 }
    val normalizedSpeedMps = speedMps?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
    val changed = ownshipTrackDeg != normalizedTrackDeg || ownshipSpeedMps != normalizedSpeedMps
    ownshipTrackDeg = normalizedTrackDeg
    ownshipSpeedMps = normalizedSpeedMps
    if (!changed || !_isEnabled.value) return
    center?.let { activeCenter -> publishFromStore(activeCenter) }
}

internal fun AdsbTrafficRepositoryRuntime.applyClearOwnshipOrigin() {
    if (ownshipOrigin == null) return
    ownshipOrigin = null
    ownshipTrackDeg = null
    ownshipSpeedMps = null
    ownshipReferenceLastUpdateMonoMs = null
    if (!_isEnabled.value) return
    center?.let { activeCenter -> publishFromStore(activeCenter) }
}

internal fun AdsbTrafficRepositoryRuntime.applyUpdateOwnshipAltitudeMeters(
    altitudeMeters: Double?
) {
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

internal fun AdsbTrafficRepositoryRuntime.applyUpdateOwnshipCirclingContext(
    isCircling: Boolean,
    circlingFeatureEnabled: Boolean
) {
    val changed =
        ownshipCircling != isCircling ||
            ownshipCirclingFeatureEnabled != circlingFeatureEnabled
    ownshipCircling = isCircling
    ownshipCirclingFeatureEnabled = circlingFeatureEnabled
    if (!changed || !_isEnabled.value) return
    center?.let { activeCenter -> publishFromStore(activeCenter) }
}

internal fun AdsbTrafficRepositoryRuntime.applyUpdateDisplayFilters(
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

internal suspend fun AdsbTrafficRepositoryRuntime.applyReconnectNow() {
    if (!_isEnabled.value) return
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
