package com.example.xcpro.map

import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.roundToInt

/**
 * Throttle a hot flow to at most one emission per [frameMs]. Uses elapsedRealtime to
 * avoid wall-clock jumps. Intended for UI-facing telemetry (vario, altitude, charts).
 */
fun <T> Flow<T>.throttleFrame(frameMs: Long, clock: () -> Long = { SystemClock.elapsedRealtime() }): Flow<T> =
    callbackFlow {
        var last = 0L
        collect { value ->
            val now = clock()
            if (now - last >= frameMs) {
                last = now
                trySend(value)
            }
        }
        awaitClose { }
    }

fun Float.bucket(step: Float): Float {
    if (!this.isFinite()) return this
    if (step == 0f) return this
    return ((this / step).roundToInt() * step)
}

fun Double.bucket(step: Double): Double {
    if (!this.isFinite()) return this
    if (step == 0.0) return this
    return ((this / step).roundToInt() * step)
}
