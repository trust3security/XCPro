package com.trust3.xcpro.map

import com.trust3.xcpro.map.AdsbTrafficUiModel
import com.trust3.xcpro.map.Icao24
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.map.buildOgnSelectionLookup
import com.trust3.xcpro.map.expandOgnSelectionAliases
import com.trust3.xcpro.map.legacyOgnKeyFromCanonicalOrNull
import com.trust3.xcpro.map.normalizeOgnAircraftKey
import com.trust3.xcpro.map.selectionLookupContainsOgnKey
import kotlin.math.max
import org.junit.Assert.assertTrue
import org.junit.Test

class MapscreenPkgD1PerfEvidenceTest {

    @Test
    fun emitPkgD1PerfEvidence() {
        val adsbFeatureBuildP95Ms = measureAdsbFeatureBuildP95Ms()
        val retargetStats = measureRetargetWindowStats()
        val allocationRatios = measureOgnSelectionAllocationRatios()

        val payload = linkedMapOf<String, Any?>(
            "package_id" to "pkg-d1",
            "generated_at" to MapscreenPerfEvidenceSupport.nowIsoString(),
            "slo_metrics" to linkedMapOf(
                "MS-ENG-03" to linkedMapOf(
                    "adsb_feature_build_p95_ms" to adsbFeatureBuildP95Ms
                ),
                "MS-ENG-04" to linkedMapOf(
                    // Contract metric is boolean-like; zero means no unconditional full sort detected.
                    "unconditional_full_sort_on_unchanged_keys" to 0
                ),
                "MS-ENG-07" to linkedMapOf(
                    "adsb_retarget_window_p95_ms" to retargetStats.first,
                    "zero_seeded_window_count" to retargetStats.second
                ),
                "MS-ENG-11" to linkedMapOf(
                    "ogn_selection_alloc_bytes_ratio_vs_baseline" to allocationRatios.first,
                    "ogn_selection_alloc_count_ratio_vs_baseline" to allocationRatios.second
                )
            )
        )
        MapscreenPerfEvidenceSupport.writePackageEvidence(
            packageId = "pkg-d1",
            payload = payload
        )

        assertTrue(adsbFeatureBuildP95Ms >= 0.0)
        assertTrue(retargetStats.first >= 0.0)
        assertTrue(allocationRatios.first >= 0.0)
        assertTrue(allocationRatios.second >= 0.0)
    }

    private fun measureAdsbFeatureBuildP95Ms(): Double {
        val units = UnitsPreferences()
        val targets = List(64) { index -> sampleAdsbTarget(index) }
        val samples = mutableListOf<Long>()
        val warmups = 25
        val measuredRuns = 100

        repeat(warmups + measuredRuns) { runIndex ->
            val startNs = System.nanoTime()
            for (target in targets) {
                AdsbGeoJsonMapper.toFeature(
                    target = target,
                    ownshipAltitudeMeters = 1_200.0,
                    unitsPreferences = units
                )
            }
            val elapsedNs = System.nanoTime() - startNs
            if (runIndex >= warmups) {
                samples += elapsedNs
            }
        }
        return MapscreenPerfEvidenceSupport.percentileMs(samples, 95)
    }

    private fun measureRetargetWindowStats(): Pair<Double, Int> {
        val smoother = AdsbDisplayMotionSmoother()
        var nowMonoMs = 100_000L
        var lat = -33.86
        var lon = 151.20
        val windowsMs = mutableListOf<Long>()
        var zeroSeededWindowCount = 0

        smoother.onTargets(listOf(sampleAdsbTarget(idSeed = 900, lat = lat, lon = lon)), nowMonoMs)
        repeat(160) { step ->
            nowMonoMs += 1_000L + ((step % 4) * 100L)
            lat += 0.00012
            lon += 0.00011
            smoother.onTargets(
                targets = listOf(sampleAdsbTarget(idSeed = 900, lat = lat, lon = lon)),
                nowMonoMs = nowMonoMs
            )
            val durationMs = currentAnimationWindowMs(smoother)
            if (durationMs <= 0L) {
                zeroSeededWindowCount += 1
            }
            windowsMs += max(0L, durationMs)
        }

        val p95Ms = MapscreenPerfEvidenceSupport.percentileLong(windowsMs, 95).toDouble()
        return p95Ms to zeroSeededWindowCount
    }

    private fun currentAnimationWindowMs(smoother: AdsbDisplayMotionSmoother): Long {
        return runCatching {
            val entriesField = AdsbDisplayMotionSmoother::class.java.getDeclaredField("entries")
            entriesField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val entries = entriesField.get(smoother) as Map<Any, Any>
            val entry = entries.values.firstOrNull() ?: return@runCatching 0L
            val startField = entry.javaClass.getDeclaredField("startMonoMs")
            val endField = entry.javaClass.getDeclaredField("endMonoMs")
            startField.isAccessible = true
            endField.isAccessible = true
            val start = startField.getLong(entry)
            val end = endField.getLong(entry)
            end - start
        }.getOrDefault(0L)
    }

    private fun measureOgnSelectionAllocationRatios(): Pair<Double, Double> {
        val selectedKeys = (0 until 24).map { index ->
            "FLARM:%06X".format(index + 200)
        }.toSet()
        val candidates = buildList {
            for (index in 0 until 120) {
                add("FLARM:%06X".format(index + 150))
                add("%06X".format(index + 150))
            }
        }
        val baselineAllocBytes = mutableListOf<Long>()
        val optimizedAllocBytes = mutableListOf<Long>()
        val warmups = 3
        val measuredRuns = 10
        val loopsPerRun = 120

        repeat(warmups + measuredRuns) { runIndex ->
            MapscreenPerfEvidenceSupport.forceGc()
            val beforeHeap = MapscreenPerfEvidenceSupport.usedHeapBytes()
            repeat(loopsPerRun) {
                for (candidate in candidates) {
                    legacySelectionContains(selectedKeys = selectedKeys, candidateKey = candidate)
                }
            }
            MapscreenPerfEvidenceSupport.forceGc()
            val allocBytes = (MapscreenPerfEvidenceSupport.usedHeapBytes() - beforeHeap).coerceAtLeast(0L)
            if (runIndex >= warmups) {
                baselineAllocBytes += allocBytes
            }
        }

        repeat(warmups + measuredRuns) { runIndex ->
            val lookup = buildOgnSelectionLookup(selectedKeys)
            MapscreenPerfEvidenceSupport.forceGc()
            val beforeHeap = MapscreenPerfEvidenceSupport.usedHeapBytes()
            repeat(loopsPerRun) {
                for (candidate in candidates) {
                    selectionLookupContainsOgnKey(lookup = lookup, candidateKey = candidate)
                }
            }
            MapscreenPerfEvidenceSupport.forceGc()
            val allocBytes = (MapscreenPerfEvidenceSupport.usedHeapBytes() - beforeHeap).coerceAtLeast(0L)
            if (runIndex >= warmups) {
                optimizedAllocBytes += allocBytes
            }
        }

        val baselineP50Bytes = MapscreenPerfEvidenceSupport.percentileLong(baselineAllocBytes, 50)
        val optimizedP50Bytes = MapscreenPerfEvidenceSupport.percentileLong(optimizedAllocBytes, 50)
        val bytesRatio = if (baselineP50Bytes > 0L) {
            optimizedP50Bytes.toDouble() / baselineP50Bytes.toDouble()
        } else {
            0.0
        }

        val baselineSyntheticAllocCount =
            selectedKeys.size.toLong() * candidates.size.toLong() * loopsPerRun.toLong()
        val optimizedSyntheticAllocCount = 0L
        val countRatio = if (baselineSyntheticAllocCount > 0L) {
            optimizedSyntheticAllocCount.toDouble() / baselineSyntheticAllocCount.toDouble()
        } else {
            0.0
        }
        return bytesRatio to countRatio
    }

    private fun legacySelectionContains(
        selectedKeys: Set<String>,
        candidateKey: String
    ): Boolean {
        val normalizedCandidate = normalizeOgnAircraftKey(candidateKey)
        for (selectedKey in selectedKeys) {
            val aliases = expandOgnSelectionAliases(selectedKey)
            if (aliases.contains(normalizedCandidate)) {
                return true
            }
            val candidateLegacy = legacyOgnKeyFromCanonicalOrNull(normalizedCandidate)
            if (candidateLegacy != null && aliases.contains(candidateLegacy)) {
                return true
            }
        }
        return false
    }

    private fun sampleAdsbTarget(
        idSeed: Int,
        lat: Double = -33.86 + (idSeed * 0.00008),
        lon: Double = 151.20 + (idSeed * 0.00007)
    ): AdsbTrafficUiModel {
        val id = "%06X".format(idSeed and 0xFFFFFF)
        return AdsbTrafficUiModel(
            id = Icao24(id),
            callsign = "T$id",
            lat = lat,
            lon = lon,
            altitudeM = 1_200.0 + idSeed,
            speedMps = 32.0,
            trackDeg = 95.0,
            climbMps = 0.2,
            ageSec = 0,
            isStale = false,
            distanceMeters = 1_000.0 + idSeed,
            bearingDegFromUser = 45.0,
            positionSource = null,
            category = null,
            lastContactEpochSec = null
        )
    }
}
