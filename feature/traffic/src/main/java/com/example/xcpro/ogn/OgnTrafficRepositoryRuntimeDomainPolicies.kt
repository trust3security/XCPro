package com.example.xcpro.ogn

import com.example.xcpro.core.common.logging.AppLogger
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val TAG = "OgnTrafficRepository"
private const val METERS_PER_KILOMETER = 1_000.0
private const val FILTER_UPDATE_MIN_MOVE_METERS = 20_000.0
private const val TARGET_STALE_AFTER_MS = 120_000L
private const val CENTER_DISTANCE_REFRESH_MIN_MOVE_METERS = 200.0
private const val CENTER_DISTANCE_REFRESH_MIN_INTERVAL_MS = 5_000L
private const val DDB_REFRESH_CHECK_INTERVAL_MS = 60L * 60L * 1000L
private const val DDB_REFRESH_FAILURE_RETRY_START_MS = 2L * 60L * 1000L
private const val DDB_REFRESH_FAILURE_RETRY_MAX_MS = 5L * 60L * 1000L
private const val APP_NAME = "XCPro"
private const val APP_VERSION = "0.1"

internal fun OgnTrafficRepositoryRuntime.resolveDisplayLabel(
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

internal fun OgnTrafficRepositoryRuntime.shouldRefreshTargetDistancesForCenterUpdate(
        previousCenter: OgnTrafficRepositoryRuntime.Center?,
        updatedCenter: OgnTrafficRepositoryRuntime.Center,
        nowMonoMs: Long
    ): Boolean {
        val lastRefreshCenter = lastDistanceRefreshCenter ?: previousCenter
        return shouldRefreshOgnDistanceForCenterUpdate(
            hasTargets = targetsByKey.isNotEmpty(),
            previousRefreshLat = lastRefreshCenter?.latitude,
            previousRefreshLon = lastRefreshCenter?.longitude,
            nextCenterLat = updatedCenter.latitude,
            nextCenterLon = updatedCenter.longitude,
            lastRefreshMonoMs = lastDistanceRefreshMonoMs,
            nowMonoMs = nowMonoMs,
            minMoveMeters = CENTER_DISTANCE_REFRESH_MIN_MOVE_METERS,
            minIntervalMs = CENTER_DISTANCE_REFRESH_MIN_INTERVAL_MS
        )
    }

internal fun OgnTrafficRepositoryRuntime.refreshTargetDistancesForCurrentCenter(nowMonoMs: Long = clock.nowMonoMs()) {
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
        lastDistanceRefreshCenter = requestedCenter ?: subscriptionCenter
        lastDistanceRefreshMonoMs = nowMonoMs
        if (changed) {
            publishTargets()
        } else {
            publishSnapshot()
        }
    }

internal fun OgnTrafficRepositoryRuntime.resolveDistanceMeters(
        targetLat: Double,
        targetLon: Double,
        requestedCenter: OgnTrafficRepositoryRuntime.Center?,
        subscriptionCenter: OgnTrafficRepositoryRuntime.Center?
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

internal fun OgnTrafficRepositoryRuntime.sweepStaleTargets(nowMonoMs: Long) {
        var removed = false
        val iterator = targetsByKey.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.isStale(nowMonoMs, TARGET_STALE_AFTER_MS)) {
                iterator.remove()
                lastTimedSourceSeenMonoByKey.remove(entry.key)
                lastAcceptedTimedSourceTimestampWallByKey.remove(entry.key)
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

internal fun OgnTrafficRepositoryRuntime.publishTargets() {
        _targets.value = targetsByKey.values
            .sortedWith(compareBy({ it.displayLabel }, { it.canonicalKey }))
        publishSuppressedTargetIds()
    }

internal fun OgnTrafficRepositoryRuntime.publishSuppressedTargetIds() {
        val suppressed = suppressedTargetSeenMonoByKey.keys.toSet()
        if (_suppressedTargetIds.value != suppressed) {
            _suppressedTargetIds.value = suppressed
        }
        publishSnapshot()
    }

internal fun OgnTrafficRepositoryRuntime.pruneSuppressedTargets(
        nowMonoMs: Long,
        config: OgnTrafficRepositoryRuntime.OwnshipFilterConfig
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
                lastTimedSourceSeenMonoByKey.remove(entry.key)
                lastAcceptedTimedSourceTimestampWallByKey.remove(entry.key)
                changed = true
            }
        }
        return changed
    }

internal fun OgnTrafficRepositoryRuntime.matchesAnyOwnshipKey(
        canonicalKey: String,
        config: OgnTrafficRepositoryRuntime.OwnshipFilterConfig
    ): Boolean {
        val flarm = config.flarmHex
        if (flarm != null && canonicalKey == "FLARM:$flarm") return true

        val icao = config.icaoHex
        if (icao != null && canonicalKey == "ICAO:$icao") return true

        return false
    }

internal fun OgnTrafficRepositoryRuntime.currentOwnshipFilterConfig(): OgnTrafficRepositoryRuntime.OwnshipFilterConfig =
        OgnTrafficRepositoryRuntime.OwnshipFilterConfig(
            flarmHex = ownFlarmHex,
            icaoHex = ownIcaoHex
        )

internal fun OgnTrafficRepositoryRuntime.applyManualReceiveRadiusKm(radiusKm: Int) {
        manualReceiveRadiusKm = clampOgnReceiveRadiusKm(radiusKm)
        if (autoReceiveRadiusEnabled) return
        applyEffectiveReceiveRadiusKm(manualReceiveRadiusKm)
    }

internal fun OgnTrafficRepositoryRuntime.applyAutoReceiveRadiusEnabled(enabled: Boolean) {
        if (autoReceiveRadiusEnabled == enabled) return
        autoReceiveRadiusEnabled = enabled
        pendingAutoRadiusKm = null
        pendingAutoRadiusSinceMonoMs = 0L
        if (!enabled) {
            applyEffectiveReceiveRadiusKm(manualReceiveRadiusKm)
            return
        }
        evaluateAutoReceiveRadius(
            nowMonoMs = clock.nowMonoMs(),
            forceApply = true
        )
    }

internal fun OgnTrafficRepositoryRuntime.applyAutoReceiveRadiusContext(context: OgnAutoReceiveRadiusContext) {
        latestAutoReceiveRadiusContext = context
        if (!autoReceiveRadiusEnabled) return
        evaluateAutoReceiveRadius(
            nowMonoMs = clock.nowMonoMs(),
            forceApply = false
        )
    }

internal fun OgnTrafficRepositoryRuntime.evaluateAutoReceiveRadius(
        nowMonoMs: Long,
        forceApply: Boolean
    ) {
        val context = latestAutoReceiveRadiusContext ?: return
        val targetRadiusKm = OgnAutoReceiveRadiusPolicy.resolveRadiusKm(context)
        if (targetRadiusKm == receiveRadiusKm) {
            pendingAutoRadiusKm = null
            pendingAutoRadiusSinceMonoMs = 0L
            return
        }
        if (forceApply || lastAutoRadiusApplyMonoMs == Long.MIN_VALUE) {
            pendingAutoRadiusKm = null
            pendingAutoRadiusSinceMonoMs = 0L
            applyEffectiveReceiveRadiusKm(targetRadiusKm)
            lastAutoRadiusApplyMonoMs = nowMonoMs
            return
        }
        if (pendingAutoRadiusKm != targetRadiusKm) {
            pendingAutoRadiusKm = targetRadiusKm
            pendingAutoRadiusSinceMonoMs = nowMonoMs
            return
        }
        val stableForMs = nowMonoMs - pendingAutoRadiusSinceMonoMs
        if (stableForMs < OgnAutoReceiveRadiusPolicy.STABLE_DURATION_MS) return
        val sinceLastApplyMs = nowMonoMs - lastAutoRadiusApplyMonoMs
        if (sinceLastApplyMs < OgnAutoReceiveRadiusPolicy.MIN_APPLY_INTERVAL_MS) return
        pendingAutoRadiusKm = null
        pendingAutoRadiusSinceMonoMs = 0L
        applyEffectiveReceiveRadiusKm(targetRadiusKm)
        lastAutoRadiusApplyMonoMs = nowMonoMs
    }

internal fun OgnTrafficRepositoryRuntime.applyEffectiveReceiveRadiusKm(radiusKm: Int) {
        val clampedRadiusKm = clampOgnReceiveRadiusKm(radiusKm)
        if (receiveRadiusKm == clampedRadiusKm) return
        receiveRadiusKm = clampedRadiusKm
        pruneTargetsOutsideReceiveRadius()
        publishSnapshot()
        if (_isEnabled.value) {
            reconnectRequestedForRadiusChange = true
        }
    }

internal fun OgnTrafficRepositoryRuntime.pruneTargetsOutsideReceiveRadius() {
        if (targetsByKey.isEmpty()) return
        val requestedCenter = center
        val fallbackCenter = activeSubscriptionCenter ?: requestedCenter ?: return
        val radiusMeters = currentReceiveRadiusMeters()
        var changed = false

        val iterator = targetsByKey.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val withinRadius = isWithinReceiveRadiusMeters(
                targetLat = entry.value.latitude,
                targetLon = entry.value.longitude,
                requestedCenterLat = requestedCenter?.latitude,
                requestedCenterLon = requestedCenter?.longitude,
                subscriptionCenterLat = fallbackCenter.latitude,
                subscriptionCenterLon = fallbackCenter.longitude,
                radiusMeters = radiusMeters
            )
            if (!withinRadius) {
                iterator.remove()
                lastTimedSourceSeenMonoByKey.remove(entry.key)
                lastAcceptedTimedSourceTimestampWallByKey.remove(entry.key)
                changed = true
            }
        }

        if (changed) {
            publishTargets()
        }
    }

internal fun OgnTrafficRepositoryRuntime.currentReceiveRadiusMeters(): Double =
        receiveRadiusKm.toDouble() * METERS_PER_KILOMETER

internal fun OgnTrafficRepositoryRuntime.publishSnapshot() {
        val activeCenter = center
        val nowWallMs = clock.nowWallMs()
        val ddbUpdatedAt = ddbRepository.lastUpdateWallMs()
        val ddbAge = if (ddbUpdatedAt > 0L && nowWallMs >= ddbUpdatedAt) {
            nowWallMs - ddbUpdatedAt
        } else {
            null
        }
        val resolvedNetworkOnline = currentNetworkOnlineState()
        networkOnline = resolvedNetworkOnline
        _snapshot.value = OgnTrafficSnapshot(
            targets = _targets.value,
            suppressedTargetIds = _suppressedTargetIds.value,
            connectionState = connectionState,
            connectionIssue = connectionIssue,
            lastError = lastError,
            subscriptionCenterLat = activeCenter?.latitude,
            subscriptionCenterLon = activeCenter?.longitude,
            receiveRadiusKm = receiveRadiusKm,
            ddbCacheAgeMs = ddbAge,
            reconnectBackoffMs = reconnectBackoffMs,
            lastReconnectWallMs = lastReconnectWallMs,
            networkOnline = resolvedNetworkOnline,
            activeSubscriptionCenterLat = activeSubscriptionCenter?.latitude,
            activeSubscriptionCenterLon = activeSubscriptionCenter?.longitude,
            droppedOutOfOrderSourceFrames = droppedOutOfOrderSourceFrames,
            droppedImplausibleMotionFrames = droppedImplausibleMotionFrames
        )
    }

internal suspend fun OgnTrafficRepositoryRuntime.requestDdbRefreshIfDue(
        nowMonoMs: Long,
        nowWallMs: Long
    ) {
        val shouldLaunch = runOnWriter {
            if (ddbRefreshInFlight) return@runOnWriter false
            val hasPendingFailure = ddbRefreshNextFailureRetryMonoMs != Long.MIN_VALUE
            val due = if (hasPendingFailure) {
                nowMonoMs >= ddbRefreshNextFailureRetryMonoMs
            } else {
                lastDdbRefreshSuccessWallMs == Long.MIN_VALUE ||
                    nowWallMs - lastDdbRefreshSuccessWallMs >= DDB_REFRESH_CHECK_INTERVAL_MS
            }
            if (!due) return@runOnWriter false
            ddbRefreshInFlight = true
            true
        }
        if (!shouldLaunch) return

        ioScope.launch {
            try {
                when (ddbRepository.refreshIfNeeded()) {
                    OgnDdbRefreshResult.Updated -> runOnWriter {
                        lastDdbRefreshSuccessWallMs = clock.nowWallMs()
                        ddbRefreshNextFailureRetryMonoMs = Long.MIN_VALUE
                        ddbRefreshFailureRetryDelayMs = DDB_REFRESH_FAILURE_RETRY_START_MS
                    }

                    OgnDdbRefreshResult.NotDue -> runOnWriter {
                        // Keep repository-side cadence aligned with DDB's internal "not due" decision
                        // so we do not relaunch refresh attempts every active-session check tick.
                        val refreshedWallMs = clock.nowWallMs()
                        if (refreshedWallMs > lastDdbRefreshSuccessWallMs) {
                            lastDdbRefreshSuccessWallMs = refreshedWallMs
                        }
                        ddbRefreshNextFailureRetryMonoMs = Long.MIN_VALUE
                        ddbRefreshFailureRetryDelayMs = DDB_REFRESH_FAILURE_RETRY_START_MS
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (throwable: Throwable) {
                AppLogger.w(TAG, "DDB refresh failed: ${throwable.message}")
                runOnWriter {
                    val failureNowMonoMs = clock.nowMonoMs()
                    val retryDelayMs = ddbRefreshFailureRetryDelayMs
                    ddbRefreshNextFailureRetryMonoMs = failureNowMonoMs + retryDelayMs
                    ddbRefreshFailureRetryDelayMs = (retryDelayMs * 2L)
                        .coerceAtMost(DDB_REFRESH_FAILURE_RETRY_MAX_MS)
                }
            } finally {
                runOnWriter {
                    ddbRefreshInFlight = false
                }
            }
        }
    }

internal fun OgnTrafficRepositoryRuntime.buildLogin(center: OgnTrafficRepositoryRuntime.Center): String {
        val loginCallsign = clientCallsign
        val passcode = generateAprsPasscode(loginCallsign)
        val filter = "r/${formatCoord(center.latitude)}/${formatCoord(center.longitude)}/${receiveRadiusKm}"
        return "user $loginCallsign pass $passcode vers $APP_NAME $APP_VERSION filter $filter"
    }

internal fun OgnTrafficRepositoryRuntime.generateAprsPasscode(callsign: String): Int {
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

internal fun OgnTrafficRepositoryRuntime.formatCoord(value: Double): String = String.format(Locale.US, "%.5f", value)

internal fun OgnTrafficRepositoryRuntime.deriveConnectionIssue(
        throwable: Throwable
    ): OgnConnectionIssue = when (throwable) {
        is OgnUnexpectedStreamEndException -> OgnConnectionIssue.UNEXPECTED_STREAM_END
        is OgnLoginUnverifiedException -> OgnConnectionIssue.LOGIN_UNVERIFIED
        is OgnStreamStalledException -> OgnConnectionIssue.STALL_TIMEOUT
        else -> OgnConnectionIssue.TRANSPORT_ERROR
    }

internal fun OgnTrafficRepositoryRuntime.sanitizeError(throwable: Throwable): String {
        return when (throwable) {
            is OgnUnexpectedStreamEndException -> "UnexpectedStreamEnd"
            is OgnLoginUnverifiedException -> "LoginUnverified"
            is OgnStreamStalledException -> "StreamStalled"
            else -> throwable::class.java.simpleName.ifBlank { "Error" }.take(80)
        }
    }

