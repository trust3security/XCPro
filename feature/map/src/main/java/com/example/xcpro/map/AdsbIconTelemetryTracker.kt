package com.example.xcpro.map

import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.ui.aircraftIcon

internal data class AdsbIconTelemetrySnapshot(
    val unknownRenderCount: Long = 0L,
    val legacyUnknownRenderCount: Long = 0L,
    val resolveLatencySampleCount: Long = 0L,
    val resolveLatencyTotalMs: Long = 0L,
    val resolveLatencyLastMs: Long? = null,
    val resolveLatencyMaxMs: Long? = null
) {
    val resolveLatencyAverageMs: Long?
        get() = if (resolveLatencySampleCount <= 0L) null else resolveLatencyTotalMs / resolveLatencySampleCount
}

/**
 * Runtime-only ADS-B icon telemetry.
 *
 * Tracks unknown icon render frequency and first-seen -> first-resolved icon latency per ICAO24.
 */
internal class AdsbIconTelemetryTracker {
    private var unknownRenderCount = 0L
    private var legacyUnknownRenderCount = 0L
    private var resolveLatencySampleCount = 0L
    private var resolveLatencyTotalMs = 0L
    private var resolveLatencyLastMs: Long? = null
    private var resolveLatencyMaxMs: Long? = null

    private val firstSeenMonoByIcao24 = HashMap<String, Long>()
    private val resolvedOnceByIcao24 = HashSet<String>()

    fun onRenderedTargets(
        targets: List<AdsbTrafficUiModel>,
        nowMonoMs: Long,
        iconStyleIdOverrides: Map<String, String> = emptyMap()
    ) {
        val activeIcao24 = HashSet<String>(targets.size)
        for (target in targets) {
            val icao24 = target.id.raw
            activeIcao24 += icao24
            firstSeenMonoByIcao24.putIfAbsent(icao24, nowMonoMs)

            val resolvedStyleId = iconStyleIdOverrides[icao24] ?: target.aircraftIcon().styleImageId
            if (isUnknownAdsbIconStyleId(resolvedStyleId)) {
                unknownRenderCount += 1L
                if (resolvedStyleId == ADSB_ICON_STYLE_UNKNOWN_LEGACY) {
                    legacyUnknownRenderCount += 1L
                }
                continue
            }

            if (!resolvedOnceByIcao24.add(icao24)) {
                continue
            }

            val firstSeenMonoMs = firstSeenMonoByIcao24[icao24] ?: continue
            if (nowMonoMs < firstSeenMonoMs) {
                continue
            }
            recordResolveLatency(nowMonoMs - firstSeenMonoMs)
        }

        firstSeenMonoByIcao24.keys.retainAll(activeIcao24)
        resolvedOnceByIcao24.retainAll(activeIcao24)
    }

    fun onMapDetached() {
        firstSeenMonoByIcao24.clear()
        resolvedOnceByIcao24.clear()
    }

    fun snapshot(): AdsbIconTelemetrySnapshot = AdsbIconTelemetrySnapshot(
        unknownRenderCount = unknownRenderCount,
        legacyUnknownRenderCount = legacyUnknownRenderCount,
        resolveLatencySampleCount = resolveLatencySampleCount,
        resolveLatencyTotalMs = resolveLatencyTotalMs,
        resolveLatencyLastMs = resolveLatencyLastMs,
        resolveLatencyMaxMs = resolveLatencyMaxMs
    )

    private fun recordResolveLatency(latencyMs: Long) {
        val safeLatencyMs = latencyMs.coerceAtLeast(0L)
        resolveLatencySampleCount += 1L
        resolveLatencyTotalMs += safeLatencyMs
        resolveLatencyLastMs = safeLatencyMs
        resolveLatencyMaxMs = maxOf(resolveLatencyMaxMs ?: safeLatencyMs, safeLatencyMs)
    }
}
