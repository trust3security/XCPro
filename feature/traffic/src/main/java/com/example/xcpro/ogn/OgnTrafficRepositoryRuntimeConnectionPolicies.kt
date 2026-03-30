package com.example.xcpro.ogn

import com.example.xcpro.core.common.logging.AppLogger
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.EOFException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private typealias Center = OgnTrafficRepositoryRuntime.Center
private typealias ConnectionExitReason = OgnTrafficRepositoryRuntime.ConnectionExitReason

private const val TAG = "OgnTrafficRepository"
private const val HOST = "aprs.glidernet.org"
private const val FILTER_PORT = 14580
private const val SOCKET_CONNECT_TIMEOUT_MS = 10_000
private const val SOCKET_READ_TIMEOUT_MS = 20_000
private const val KEEPALIVE_INTERVAL_MS = 60_000L
private const val STALL_TIMEOUT_MS = 120_000L
private const val TARGET_STALE_AFTER_MS = 120_000L
private const val STALE_SWEEP_INTERVAL_MS = 10_000L
private const val FILTER_UPDATE_MIN_MOVE_METERS = 20_000.0
private const val UNTIMED_SOURCE_FALLBACK_AFTER_MS = 30_000L
private const val SOURCE_TIME_REWIND_TOLERANCE_MS = 0L
private const val MAX_PLAUSIBLE_SPEED_MPS = 250.0
private const val DDB_ACTIVE_REFRESH_CHECK_INTERVAL_MS = 60_000L
private const val RECONNECT_BACKOFF_START_MS = 1_000L
private const val RECONNECT_BACKOFF_MAX_MS = 60_000L
private const val DDB_REFRESH_FAILURE_RETRY_START_MS = 2L * 60L * 1000L

internal fun OgnTrafficRepositoryRuntime.ensureLoopRunning() {
    synchronized(loopJobLock) {
        val existing = loopJob
        if (existing != null && existing.isActive) return
        loopJob = ioScope.launch {
            runConnectionLoop()
        }
    }
}

internal suspend fun OgnTrafficRepositoryRuntime.stopLoopAndClearTargets() {
    val jobToCancel = synchronized(loopJobLock) {
        val existing = loopJob
        loopJob = null
        existing
    }
    jobToCancel?.let { job ->
        withContext(ioDispatcher) {
            job.cancelAndJoin()
        }
    }
    if (_isEnabled.value) return
    targetsByKey.clear()
    suppressedTargetSeenMonoByKey.clear()
    lastTimedSourceSeenMonoByKey.clear()
    lastAcceptedTimedSourceTimestampWallByKey.clear()
    _targets.value = emptyList()
    _suppressedTargetIds.value = emptySet()
    activeSubscriptionCenter = null
    lastDistanceRefreshCenter = null
    lastDistanceRefreshMonoMs = Long.MIN_VALUE
    droppedOutOfOrderSourceFrames = 0L
    droppedImplausibleMotionFrames = 0L
    ddbRefreshInFlight = false
    ddbRefreshNextFailureRetryMonoMs = Long.MIN_VALUE
    ddbRefreshFailureRetryDelayMs = DDB_REFRESH_FAILURE_RETRY_START_MS
    connectionState = OgnConnectionState.DISCONNECTED
    connectionIssue = null
    lastError = null
    reconnectBackoffMs = null
    lastReconnectWallMs = null
    publishSnapshot()
}

internal suspend fun OgnTrafficRepositoryRuntime.runConnectionLoop() {
    val thisJob = currentCoroutineContext()[Job]
    var backoffMs = RECONNECT_BACKOFF_START_MS
    try {
        while (_isEnabled.value) {
            requestDdbRefreshIfDue(
                nowMonoMs = clock.nowMonoMs(),
                nowWallMs = clock.nowWallMs()
            )
            if (!awaitNetworkOnline()) break
            val centerAtConnect = waitForCenter() ?: break
            val exitReason = try {
                connectAndRead(centerAtConnect)
            } catch (e: CancellationException) {
                throw e
            } catch (throwable: Throwable) {
                runOnWriter {
                    activeSubscriptionCenter = null
                    connectionState = OgnConnectionState.ERROR
                    connectionIssue = deriveConnectionIssue(throwable)
                    lastError = sanitizeError(throwable)
                    publishSnapshot()
                }
                AppLogger.w(TAG, "Traffic stream disconnected: ${throwable.message}")
                null
            }

            runOnWriter {
                sweepStaleTargets(clock.nowMonoMs())
            }
            if (!_isEnabled.value) break

            when (exitReason) {
                ConnectionExitReason.ReconnectRequested -> {
                    backoffMs = RECONNECT_BACKOFF_START_MS
                    runOnWriter {
                        activeSubscriptionCenter = null
                        connectionState = OgnConnectionState.DISCONNECTED
                        connectionIssue = null
                        lastError = null
                        reconnectBackoffMs = null
                        publishSnapshot()
                    }
                }

                ConnectionExitReason.Stopped -> break
                null -> {
                    if (!delayForNextAttempt(backoffMs)) break
                    backoffMs = (backoffMs * 2L).coerceAtMost(RECONNECT_BACKOFF_MAX_MS)
                }
            }
        }
        runOnWriter {
            activeSubscriptionCenter = null
            connectionState = OgnConnectionState.DISCONNECTED
            connectionIssue = null
            reconnectBackoffMs = null
            publishSnapshot()
        }
    } finally {
        synchronized(loopJobLock) {
            if (loopJob == thisJob) {
                loopJob = null
            }
        }
    }
}

internal suspend fun OgnTrafficRepositoryRuntime.connectAndRead(
    centerAtConnect: Center
): ConnectionExitReason {
    val socket = socketFactory()
    var reader: BufferedReader? = null
    var writer: BufferedWriter? = null
    try {
        runOnWriter {
            connectionState = OgnConnectionState.CONNECTING
            connectionIssue = null
            lastError = null
            reconnectBackoffMs = null
            publishSnapshot()
        }

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

        runOnWriter {
            activeSubscriptionCenter = centerAtConnect
            refreshTargetDistancesForCurrentCenter(clock.nowMonoMs())
            publishSnapshot()
        }
        var connectionEstablished = false
        var lastSweepMonoMs = clock.nowMonoMs()
        var lastKeepaliveMonoMs = lastSweepMonoMs
        var lastInboundActivityMonoMs = lastSweepMonoMs
        var lastActiveDdbCheckMonoMs = lastSweepMonoMs

        while (_isEnabled.value) {
            if (reconnectRequestedForRadiusChange) {
                runOnWriter {
                    reconnectRequestedForRadiusChange = false
                }
                AppLogger.d(TAG, "Receive radius changed; reconnecting with updated filter")
                return ConnectionExitReason.ReconnectRequested
            }
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
                return ConnectionExitReason.ReconnectRequested
            }

            try {
                val line = reader.readLine() ?: throw OgnUnexpectedStreamEndException()
                val receivedMonoMs = clock.nowMonoMs()
                val receivedWallMs = clock.nowWallMs()
                lastInboundActivityMonoMs = receivedMonoMs
                when (parseLogrespStatus(line)) {
                    OgnLogrespStatus.VERIFIED -> {
                        if (!connectionEstablished) {
                            connectionEstablished = true
                            runOnWriter {
                                connectionState = OgnConnectionState.CONNECTED
                                connectionIssue = null
                                lastError = null
                                publishSnapshot()
                            }
                            AppLogger.i(
                                TAG,
                                "Connected to OGN traffic feed with ${receiveRadiusKm}km radius"
                            )
                        }
                    }

                    OgnLogrespStatus.UNVERIFIED -> {
                        throw OgnLoginUnverifiedException()
                    }

                    null -> {
                        val sawTraffic = runOnWriter {
                            handleIncomingLine(
                                line = line,
                                nowMonoMs = receivedMonoMs,
                                receivedWallMs = receivedWallMs,
                                centerAtConnect = centerAtConnect
                            )
                        }
                        if (sawTraffic && !connectionEstablished) {
                            connectionEstablished = true
                            runOnWriter {
                                connectionState = OgnConnectionState.CONNECTED
                                connectionIssue = null
                                lastError = null
                                publishSnapshot()
                            }
                            AppLogger.i(
                                TAG,
                                "Connected to OGN traffic feed with ${receiveRadiusKm}km radius"
                            )
                        }
                    }
                }
            } catch (_: SocketTimeoutException) {
                // Expected timeout so we can evaluate keepalive/reconnect conditions.
            }

            val loopNowMonoMs = clock.nowMonoMs()
            if (loopNowMonoMs - lastInboundActivityMonoMs >= STALL_TIMEOUT_MS) {
                throw OgnStreamStalledException()
            }
            if (loopNowMonoMs - lastActiveDdbCheckMonoMs >= DDB_ACTIVE_REFRESH_CHECK_INTERVAL_MS) {
                requestDdbRefreshIfDue(
                    nowMonoMs = loopNowMonoMs,
                    nowWallMs = clock.nowWallMs()
                )
                lastActiveDdbCheckMonoMs = loopNowMonoMs
            }
            if (loopNowMonoMs - lastSweepMonoMs >= STALE_SWEEP_INTERVAL_MS) {
                runOnWriter {
                    sweepStaleTargets(loopNowMonoMs)
                }
                lastSweepMonoMs = loopNowMonoMs
            }
            if (loopNowMonoMs - lastKeepaliveMonoMs >= KEEPALIVE_INTERVAL_MS) {
                writer.write("#keepalive")
                writer.newLine()
                writer.flush()
                lastKeepaliveMonoMs = loopNowMonoMs
            }
        }
        return ConnectionExitReason.Stopped
    } finally {
        runCatching { reader?.close() }
        runCatching { writer?.close() }
        runCatching { socket.close() }
    }
}

internal fun OgnTrafficRepositoryRuntime.handleIncomingLine(
    line: String,
    nowMonoMs: Long,
    receivedWallMs: Long,
    centerAtConnect: Center
): Boolean {
    val parsed = parser.parseTraffic(
        line = line,
        receivedAtMillis = nowMonoMs,
        receivedAtWallMillis = receivedWallMs
    ) ?: return false
    val requestedCenter = center
    if (!isWithinReceiveRadiusMeters(
            targetLat = parsed.latitude,
            targetLon = parsed.longitude,
            requestedCenterLat = requestedCenter?.latitude,
            requestedCenterLon = requestedCenter?.longitude,
            subscriptionCenterLat = centerAtConnect.latitude,
            subscriptionCenterLon = centerAtConnect.longitude,
            radiusMeters = currentReceiveRadiusMeters()
        )
    ) {
        return true
    }

    val targetKey = parsed.canonicalKey
    val previousCommittedTarget = targetsByKey[targetKey]
    val lastAcceptedTimedSourceTimestampWallMs =
        lastAcceptedTimedSourceTimestampWallByKey[targetKey]
    val lastTimedSourceSeenMonoMs = lastTimedSourceSeenMonoByKey[targetKey]
    if (!shouldAcceptUntimedAfterTimedSourceLock(
            lastTimedSourceSeenMonoMs = lastTimedSourceSeenMonoMs,
            incomingSourceTimestampWallMs = parsed.sourceTimestampWallMs,
            nowMonoMs = nowMonoMs,
            fallbackAfterMs = UNTIMED_SOURCE_FALLBACK_AFTER_MS
        )
    ) {
        droppedOutOfOrderSourceFrames += 1L
        publishSnapshot()
        return true
    }
    if (!shouldAcceptOgnSourceTimestamp(
            previousSourceTimestampWallMs =
            lastAcceptedTimedSourceTimestampWallMs ?: previousCommittedTarget?.sourceTimestampWallMs,
            incomingSourceTimestampWallMs = parsed.sourceTimestampWallMs,
            rewindToleranceMs = SOURCE_TIME_REWIND_TOLERANCE_MS
        )
    ) {
        droppedOutOfOrderSourceFrames += 1L
        publishSnapshot()
        return true
    }
    if (!isPlausibleOgnMotion(
            previousLatitude = previousCommittedTarget?.latitude,
            previousLongitude = previousCommittedTarget?.longitude,
            previousSourceTimestampWallMs = previousCommittedTarget?.sourceTimestampWallMs,
            previousSeenMonoMs = previousCommittedTarget?.lastSeenMillis,
            incomingLatitude = parsed.latitude,
            incomingLongitude = parsed.longitude,
            incomingSourceTimestampWallMs = parsed.sourceTimestampWallMs,
            incomingSeenMonoMs = nowMonoMs,
            maxPlausibleSpeedMps = MAX_PLAUSIBLE_SPEED_MPS
        )
    ) {
        droppedImplausibleMotionFrames += 1L
        publishSnapshot()
        return true
    }
    val ddbIdentity = parsed.deviceIdHex?.let { deviceIdHex ->
        ddbRepository.lookup(
            addressType = parsed.addressType,
            deviceIdHex = deviceIdHex
        )
    }
    if (ddbIdentity?.tracked == false) {
        val removedTarget = targetsByKey.remove(targetKey)
        val removedSuppressed = suppressedTargetSeenMonoByKey.remove(targetKey) != null
        lastTimedSourceSeenMonoByKey.remove(targetKey)
        lastAcceptedTimedSourceTimestampWallByKey.remove(targetKey)
        if (removedTarget != null) {
            publishTargets()
        } else if (removedSuppressed) {
            publishSuppressedTargetIds()
        }
        return true
    }
    val identity = mergeOgnIdentity(ddbIdentity = ddbIdentity, parsedIdentity = parsed.identity)
    val label = resolveDisplayLabel(parsed, identity)
    val previousTrackDegrees = previousCommittedTarget?.trackDegrees
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
        lastTimedSourceSeenMonoByKey.remove(targetKey)
        lastAcceptedTimedSourceTimestampWallByKey.remove(targetKey)
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
    if (parsed.sourceTimestampWallMs != null) {
        lastTimedSourceSeenMonoByKey[targetKey] = nowMonoMs
        lastAcceptedTimedSourceTimestampWallByKey[targetKey] = parsed.sourceTimestampWallMs
    }
    targetsByKey[targetKey] = enriched
    publishTargets()
    return true
}

internal class OgnUnexpectedStreamEndException :
    EOFException("OGN stream ended unexpectedly")

internal class OgnLoginUnverifiedException :
    IllegalStateException("OGN login unverified")

internal class OgnStreamStalledException :
    IllegalStateException("OGN stream stalled")
