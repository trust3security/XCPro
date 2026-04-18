package com.trust3.xcpro.map

import com.trust3.xcpro.map.AdsbTrafficUiModel
import com.trust3.xcpro.map.Icao24
import com.trust3.xcpro.map.AdsbAircraftIcon
import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbStickyIconProjectionCacheTest {

    @Test
    fun unknownAfterStrongFixedWing_withinTtl_keepsPriorStrongIcon() {
        val cache = AdsbStickyIconProjectionCache(holdTtlMs = 1_000L)
        val id = "abc123"
        cache.projectStyleImageIds(
            targets = listOf(target(id, category = 0, metadataTypecode = "B738")),
            nowMonoMs = 1_000L
        )

        val projected = cache.projectStyleImageIds(
            targets = listOf(target(id, category = 0, metadataTypecode = null)),
            nowMonoMs = 1_600L
        )

        assertEquals(
            AdsbAircraftIcon.PlaneTwinJet.styleImageId,
            projected[id]
        )
    }

    @Test
    fun unknownAfterStrongFixedWing_afterTtl_expiresToUnknownIcon() {
        val cache = AdsbStickyIconProjectionCache(holdTtlMs = 500L)
        val id = "abc123"
        cache.projectStyleImageIds(
            targets = listOf(target(id, category = 0, metadataTypecode = "B738")),
            nowMonoMs = 1_000L
        )

        val projected = cache.projectStyleImageIds(
            targets = listOf(target(id, category = 0, metadataTypecode = null)),
            nowMonoMs = 1_700L
        )

        assertEquals(
            AdsbAircraftIcon.Unknown.styleImageId,
            projected[id]
        )
    }

    @Test
    fun authoritativeNonFixedWingOutcome_isNeverOverriddenByStickyState() {
        val cache = AdsbStickyIconProjectionCache(holdTtlMs = 2_000L)
        val id = "abc123"
        cache.projectStyleImageIds(
            targets = listOf(target(id, category = 0, metadataTypecode = "B738")),
            nowMonoMs = 1_000L
        )

        val projected = cache.projectStyleImageIds(
            targets = listOf(target(id, category = 9, metadataTypecode = null)),
            nowMonoMs = 1_200L
        )

        assertEquals(
            AdsbAircraftIcon.Glider.styleImageId,
            projected[id]
        )
    }

    @Test
    fun unknownAfterStrongFixedWing_rolloutDisabled_usesLegacyUnknownIcon() {
        val cache = AdsbStickyIconProjectionCache(holdTtlMs = 500L)
        val id = "abc123"
        cache.projectStyleImageIds(
            targets = listOf(target(id, category = 0, metadataTypecode = "B738")),
            nowMonoMs = 1_000L,
            defaultMediumUnknownIconEnabled = false
        )

        val projected = cache.projectStyleImageIds(
            targets = listOf(target(id, category = 0, metadataTypecode = null)),
            nowMonoMs = 1_700L,
            defaultMediumUnknownIconEnabled = false
        )

        assertEquals(
            ADSB_ICON_STYLE_UNKNOWN_LEGACY,
            projected[id]
        )
    }

    private fun target(
        rawIcao24: String,
        category: Int?,
        metadataTypecode: String?
    ): AdsbTrafficUiModel {
        return AdsbTrafficUiModel(
            id = Icao24.from(rawIcao24) ?: error("invalid ICAO24"),
            callsign = rawIcao24.uppercase(),
            lat = -33.86,
            lon = 151.20,
            altitudeM = 1000.0,
            speedMps = 70.0,
            trackDeg = 180.0,
            climbMps = 0.5,
            ageSec = 2,
            isStale = false,
            distanceMeters = 1200.0,
            bearingDegFromUser = 90.0,
            positionSource = 0,
            category = category,
            lastContactEpochSec = 1_710_000_000L,
            metadataTypecode = metadataTypecode,
            metadataIcaoAircraftType = null
        )
    }
}
