package com.example.xcpro.map

/**
 * Session-level sticky icon projection cache.
 *
 * Keeps last strong fixed-wing icon for a target when current classification is unknown,
 * bounded by a monotonic TTL.
 */
class AdsbStickyIconProjectionCache(
    private val holdTtlMs: Long = DEFAULT_HOLD_TTL_MS
) {
    private val stickyByIcao24 = LinkedHashMap<String, StickyEntry>()

    fun projectStyleImageIds(
        targets: List<AdsbTrafficUiModel>,
        nowMonoMs: Long,
        defaultMediumUnknownIconEnabled: Boolean = true
    ): Map<String, String> {
        if (targets.isEmpty()) {
            pruneExpired(nowMonoMs)
            return emptyMap()
        }

        val unknownStyleId = if (defaultMediumUnknownIconEnabled) {
            AdsbAircraftIcon.Unknown.styleImageId
        } else {
            ADSB_ICON_STYLE_UNKNOWN_LEGACY
        }
        pruneExpired(nowMonoMs)
        val styleByIcao24 = HashMap<String, String>(targets.size)
        for (target in targets) {
            val icao24 = target.id.raw
            val currentIcon = target.aircraftIcon()
            when {
                currentIcon.isAuthoritativeNonFixedWing() -> {
                    stickyByIcao24.remove(icao24)
                    styleByIcao24[icao24] = currentIcon.styleImageId
                }

                currentIcon.isStrongFixedWing() -> {
                    stickyByIcao24[icao24] = StickyEntry(
                        icon = currentIcon,
                        seenAtMonoMs = nowMonoMs
                    )
                    styleByIcao24[icao24] = currentIcon.styleImageId
                }

                else -> {
                    val sticky = stickyByIcao24[icao24]
                    if (sticky != null && nowMonoMs - sticky.seenAtMonoMs <= holdTtlMs) {
                        styleByIcao24[icao24] = sticky.icon.styleImageId
                    } else {
                        stickyByIcao24.remove(icao24)
                        styleByIcao24[icao24] = unknownStyleId
                    }
                }
            }
        }
        pruneOverflow()
        return styleByIcao24
    }

    fun clear() {
        stickyByIcao24.clear()
    }

    private fun pruneExpired(nowMonoMs: Long) {
        stickyByIcao24.entries.removeIf { (_, entry) ->
            nowMonoMs - entry.seenAtMonoMs > holdTtlMs
        }
    }

    private fun pruneOverflow() {
        while (stickyByIcao24.size > MAX_ENTRIES) {
            val oldest = stickyByIcao24.entries.firstOrNull()?.key ?: return
            stickyByIcao24.remove(oldest)
        }
    }

    private fun AdsbAircraftIcon.isStrongFixedWing(): Boolean = when (this) {
        AdsbAircraftIcon.PlaneLight,
        AdsbAircraftIcon.PlaneMedium,
        AdsbAircraftIcon.PlaneLarge,
        AdsbAircraftIcon.PlaneHeavy,
        AdsbAircraftIcon.PlaneTwinJet,
        AdsbAircraftIcon.PlaneTwinProp,
        AdsbAircraftIcon.PlaneLargeIcaoOverride -> true

        else -> false
    }

    private fun AdsbAircraftIcon.isAuthoritativeNonFixedWing(): Boolean = when (this) {
        AdsbAircraftIcon.Helicopter,
        AdsbAircraftIcon.Glider,
        AdsbAircraftIcon.Balloon,
        AdsbAircraftIcon.Parachutist,
        AdsbAircraftIcon.Hangglider,
        AdsbAircraftIcon.Drone -> true

        else -> false
    }

    private data class StickyEntry(
        val icon: AdsbAircraftIcon,
        val seenAtMonoMs: Long
    )

    private companion object {
        private const val DEFAULT_HOLD_TTL_MS = 10_000L
        private const val MAX_ENTRIES = 2_048
    }
}
