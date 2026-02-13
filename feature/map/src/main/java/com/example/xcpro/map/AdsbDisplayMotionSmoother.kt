package com.example.xcpro.map

import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import kotlin.math.abs

/**
 * Visual-only per-aircraft interpolation between provider updates.
 * This does not mutate repository state; it only creates smoother map frames.
 */
internal class AdsbDisplayMotionSmoother {

    private val entries = LinkedHashMap<Icao24, Entry>()

    fun onTargets(targets: List<AdsbTrafficUiModel>, nowMonoMs: Long): Boolean {
        var changed = false
        val incomingIds = HashSet<Icao24>(targets.size)
        for (target in targets) {
            incomingIds.add(target.id)
            val existing = entries[target.id]
            if (existing == null) {
                entries[target.id] = Entry.stationary(target)
                changed = true
                continue
            }

            val from = existing.frameAt(nowMonoMs)
            val to = mergeVisualValues(from = from, update = target)
            if (from == to) {
                existing.replaceWithoutAnimation(to)
                continue
            }

            val durationMs = computeDurationMs(
                previousSampleMonoMs = existing.lastSampleMonoMs,
                nowMonoMs = nowMonoMs
            )
            existing.retarget(
                from = from,
                to = to,
                startMonoMs = nowMonoMs,
                endMonoMs = nowMonoMs + durationMs,
                sampleMonoMs = nowMonoMs
            )
            changed = true
        }

        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!incomingIds.contains(entry.key)) {
                iterator.remove()
                changed = true
            }
        }
        return changed
    }

    fun hasActiveAnimations(nowMonoMs: Long): Boolean =
        entries.values.any { it.isAnimating(nowMonoMs) }

    fun frame(nowMonoMs: Long): List<AdsbTrafficUiModel> =
        entries.values.map { it.frameAt(nowMonoMs) }

    fun clear() {
        entries.clear()
    }

    private fun mergeVisualValues(
        from: AdsbTrafficUiModel,
        update: AdsbTrafficUiModel
    ): AdsbTrafficUiModel = update.copy(
        // Keep last valid render values when provider sends null/non-finite.
        trackDeg = pickFinite(update.trackDeg, from.trackDeg),
        climbMps = pickFinite(update.climbMps, from.climbMps)
    )

    private fun pickFinite(primary: Double?, fallback: Double?): Double? =
        when {
            primary?.isFinite() == true -> primary
            fallback?.isFinite() == true -> fallback
            else -> null
        }

    private fun computeDurationMs(
        previousSampleMonoMs: Long,
        nowMonoMs: Long
    ): Long {
        val delta = (nowMonoMs - previousSampleMonoMs).coerceAtLeast(MIN_DURATION_MS)
        return delta.coerceAtMost(MAX_DURATION_MS)
    }

    private data class Entry(
        var from: AdsbTrafficUiModel,
        var to: AdsbTrafficUiModel,
        var startMonoMs: Long,
        var endMonoMs: Long,
        var lastSampleMonoMs: Long
    ) {
        fun isAnimating(nowMonoMs: Long): Boolean = nowMonoMs < endMonoMs

        fun replaceWithoutAnimation(target: AdsbTrafficUiModel) {
            from = target
            to = target
            startMonoMs = endMonoMs
        }

        fun retarget(
            from: AdsbTrafficUiModel,
            to: AdsbTrafficUiModel,
            startMonoMs: Long,
            endMonoMs: Long,
            sampleMonoMs: Long
        ) {
            this.from = from
            this.to = to
            this.startMonoMs = startMonoMs
            this.endMonoMs = endMonoMs
            this.lastSampleMonoMs = sampleMonoMs
        }

        fun frameAt(nowMonoMs: Long): AdsbTrafficUiModel {
            if (endMonoMs <= startMonoMs || nowMonoMs >= endMonoMs) return to
            if (nowMonoMs <= startMonoMs) return from
            val t = (nowMonoMs - startMonoMs).toDouble() / (endMonoMs - startMonoMs).toDouble()
            return interpolate(from = from, to = to, progress = t.coerceIn(0.0, 1.0))
        }

        companion object {
            fun stationary(target: AdsbTrafficUiModel): Entry = Entry(
                from = target,
                to = target,
                startMonoMs = 0L,
                endMonoMs = 0L,
                lastSampleMonoMs = 0L
            )
        }
    }

    private companion object {
        private const val MIN_DURATION_MS = 250L
        private const val MAX_DURATION_MS = 10_000L
    }
}

private fun interpolate(
    from: AdsbTrafficUiModel,
    to: AdsbTrafficUiModel,
    progress: Double
): AdsbTrafficUiModel = to.copy(
    lat = lerp(from.lat, to.lat, progress),
    lon = lerp(from.lon, to.lon, progress),
    altitudeM = lerpNullable(from.altitudeM, to.altitudeM, progress),
    speedMps = lerpNullable(from.speedMps, to.speedMps, progress),
    trackDeg = lerpAngleNullable(from.trackDeg, to.trackDeg, progress),
    climbMps = lerpNullable(from.climbMps, to.climbMps, progress),
    distanceMeters = lerp(from.distanceMeters, to.distanceMeters, progress),
    bearingDegFromUser = lerpAngle(from.bearingDegFromUser, to.bearingDegFromUser, progress)
)

private fun lerp(start: Double, end: Double, t: Double): Double =
    start + ((end - start) * t)

private fun lerpNullable(start: Double?, end: Double?, t: Double): Double? =
    when {
        start == null && end == null -> null
        start == null -> end
        end == null -> start
        else -> lerp(start, end, t)
    }

private fun lerpAngleNullable(start: Double?, end: Double?, t: Double): Double? =
    when {
        start == null && end == null -> null
        start == null -> normalizeAngle(end ?: 0.0)
        end == null -> normalizeAngle(start)
        else -> lerpAngle(start, end, t)
    }

private fun lerpAngle(start: Double, end: Double, t: Double): Double {
    val normalizedStart = normalizeAngle(start)
    val normalizedEnd = normalizeAngle(end)
    val delta = shortestAngleDelta(normalizedStart, normalizedEnd)
    return normalizeAngle(normalizedStart + (delta * t))
}

private fun normalizeAngle(value: Double): Double {
    val mod = value % 360.0
    return if (mod < 0.0) mod + 360.0 else mod
}

private fun shortestAngleDelta(from: Double, to: Double): Double {
    val direct = to - from
    return when {
        abs(direct) <= 180.0 -> direct
        direct > 0.0 -> direct - 360.0
        else -> direct + 360.0
    }
}
