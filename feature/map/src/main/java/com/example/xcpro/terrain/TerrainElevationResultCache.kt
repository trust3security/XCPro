package com.example.xcpro.terrain

import com.example.xcpro.core.common.logging.AppLogger
import java.util.LinkedHashMap
import kotlin.math.floor
import kotlin.math.roundToInt

internal class TerrainElevationResultCache {

    companion object {
        private const val TAG = "TerrainCache"
        private const val GRID_RESOLUTION = 0.01
        private const val MAX_CACHE_ENTRIES = 4_096
        private const val LOOKUP_LOG_INTERVAL = 250
        private const val STORE_LOG_INTERVAL = 100
    }

    private var evictions = 0
    private var hits = 0
    private var misses = 0
    private val cache = object : LinkedHashMap<String, Double>(MAX_CACHE_ENTRIES + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Double>?): Boolean {
            val shouldEvict = size > MAX_CACHE_ENTRIES
            if (shouldEvict) {
                evictions++
            }
            return shouldEvict
        }
    }

    @Synchronized
    fun get(lat: Double, lon: Double): Double? {
        val elevation = cache[getCacheKey(lat, lon)]
        if (elevation != null) {
            hits++
        } else {
            misses++
        }
        val lookups = hits + misses
        if (lookups % LOOKUP_LOG_INTERVAL == 0) {
            AppLogger.d(
                TAG,
                "Lookup stats: size=${cache.size}, hitRate=${getHitRate()}%, evictions=$evictions"
            )
        }
        return elevation
    }

    @Synchronized
    fun store(lat: Double, lon: Double, elevation: Double) {
        cache[getCacheKey(lat, lon)] = elevation
        if (cache.size % STORE_LOG_INTERVAL == 0) {
            AppLogger.d(
                TAG,
                "Store stats: size=${cache.size}, hitRate=${getHitRate()}%, evictions=$evictions"
            )
        }
    }

    private fun getCacheKey(lat: Double, lon: Double): String {
        val latRounded = floor(lat / GRID_RESOLUTION) * GRID_RESOLUTION
        val lonRounded = floor(lon / GRID_RESOLUTION) * GRID_RESOLUTION
        return "$latRounded,$lonRounded"
    }

    @Synchronized
    private fun getHitRate(): Int {
        val total = hits + misses
        return if (total > 0) {
            ((hits.toDouble() / total) * 100).roundToInt()
        } else {
            0
        }
    }
}
