package com.example.xcpro.ogn

import com.example.xcpro.core.common.logging.AppLogger
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private typealias Center = OgnTrafficRepositoryRuntime.Center
private typealias OwnshipFilterConfig = OgnTrafficRepositoryRuntime.OwnshipFilterConfig
private typealias ConnectionExitReason = OgnTrafficRepositoryRuntime.ConnectionExitReason

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

internal fun OgnTrafficRepositoryRuntime.ensureLoopRunning() {
        synchronized(loopJobLock) {
            val existing = loopJob
            if (existing != null && existing.isActive) return
            loopJob = scope.launch {
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
        jobToCancel?.cancelAndJoin()
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
        ddbRefreshNextFailureRetryMonoMs = Long.MIN_VALUE
        ddbRefreshFailureRetryDelayMs = DDB_REFRESH_FAILURE_RETRY_START_MS
        connectionState = OgnConnectionState.DISCONNECTED
        lastError = null
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
                val centerAtConnect = waitForCenter() ?: break
                var reconnectRequestedByPolicy = false
                try {
                    reconnectRequestedByPolicy =
                        connectAndRead(centerAtConnect) == ConnectionExitReason.ReconnectRequested
                    connectionState = OgnConnectionState.DISCONNECTED
                    activeSubscriptionCenter = null
                    backoffMs = RECONNECT_BACKOFF_START_MS
                    reconnectBackoffMs = null
                    publishSnapshot()
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    connectionState = OgnConnectionState.ERROR
                    lastError = sanitizeError(t)
                    publishSnapshot()
                    AppLogger.w(TAG, "Traffic stream disconnected: ${t.message}")
                }

                sweepStaleTargets(clock.nowMonoMs())
                if (!_isEnabled.value) break
                if (reconnectRequestedByPolicy) {
                    continue
                }
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

internal suspend fun OgnTrafficRepositoryRuntime.waitForCenter(): Center? {
        while (_isEnabled.value) {
            center?.let { return it }
            delay(WAIT_FOR_CENTER_MS)
        }
        return null
    }

internal suspend fun OgnTrafficRepositoryRuntime.connectAndRead(centerAtConnect: Center): ConnectionExitReason {
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
            refreshTargetDistancesForCurrentCenter(clock.nowMonoMs())
            publishSnapshot()
            var connectionEstablished = false

            var lastSweepMonoMs = clock.nowMonoMs()
            var lastKeepaliveMonoMs = lastSweepMonoMs
            var lastInboundActivityMonoMs = lastSweepMonoMs
            var lastActiveDdbCheckMonoMs = lastSweepMonoMs

            while (_isEnabled.value) {
                val activeCenter = center
                if (reconnectRequestedForRadiusChange) {
                    reconnectRequestedForRadiusChange = false
                    AppLogger.d(TAG, "Receive radius changed; reconnecting with updated filter")
                    return ConnectionExitReason.ReconnectRequested
                }
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
                    val line = reader.readLine() ?: return ConnectionExitReason.StreamEnded
                    val receivedMonoMs = clock.nowMonoMs()
                    lastInboundActivityMonoMs = receivedMonoMs
                    when (parseLogrespStatus(line)) {
                        OgnLogrespStatus.VERIFIED -> {
                            if (!connectionEstablished) {
                                connectionEstablished = true
                                connectionState = OgnConnectionState.CONNECTED
                                publishSnapshot()
                                AppLogger.i(
                                    TAG,
                                    "Connected to OGN traffic feed with ${receiveRadiusKm}km radius"
                                )
                            }
                        }

                        OgnLogrespStatus.UNVERIFIED -> {
                            throw IllegalStateException("OGN login unverified")
                        }

                        null -> {
                            val sawTraffic = handleIncomingLine(
                                line = line,
                                nowMonoMs = receivedMonoMs,
                                centerAtConnect = centerAtConnect
                            )
                            if (sawTraffic && !connectionEstablished) {
                                connectionEstablished = true
                                connectionState = OgnConnectionState.CONNECTED
                                publishSnapshot()
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
                    throw IllegalStateException("OGN stream stalled")
                }
                if (loopNowMonoMs - lastActiveDdbCheckMonoMs >= DDB_ACTIVE_REFRESH_CHECK_INTERVAL_MS) {
                    requestDdbRefreshIfDue(
                        nowMonoMs = loopNowMonoMs,
                        nowWallMs = clock.nowWallMs()
                    )
                    lastActiveDdbCheckMonoMs = loopNowMonoMs
                }

                if (loopNowMonoMs - lastSweepMonoMs >= STALE_SWEEP_INTERVAL_MS) {
                    sweepStaleTargets(loopNowMonoMs)
                    lastSweepMonoMs = loopNowMonoMs
                }

                if (loopNowMonoMs - lastKeepaliveMonoMs >= KEEPALIVE_INTERVAL_MS) {
                    writer.write("#keepalive")
                    writer.newLine()
                    writer.flush()
                    lastKeepaliveMonoMs = loopNowMonoMs
                }
            }
            return ConnectionExitReason.StreamEnded
        } finally {
            activeSubscriptionCenter = null
            runCatching { reader?.close() }
            runCatching { writer?.close() }
            runCatching { socket.close() }
        }
    }

internal fun OgnTrafficRepositoryRuntime.handleIncomingLine(line: String, nowMonoMs: Long, centerAtConnect: Center): Boolean {
        val parsed = parser.parseTraffic(
            line = line,
            receivedAtMillis = nowMonoMs,
            receivedAtWallMillis = clock.nowWallMs()
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

