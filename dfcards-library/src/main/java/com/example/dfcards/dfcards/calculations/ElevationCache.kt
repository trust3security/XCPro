package com.example.dfcards.dfcards.calculations

import android.util.Log
import java.util.LinkedHashMap
import kotlin.math.roundToInt

/**
 * Elevation Cache - In-memory cache for terrain elevations
 *
 * KISS PRINCIPLE:
 * - Simple map-based caching
 * - 1km grid resolution (0.01 ~= 1.1km at equator)
 * - No persistence (in-memory only for simplicity)
 *
 * PERFORMANCE:
 * - Cache hit: <1ms
 * - Memory: ~100 locations = ~2KB
 * - Coverage: ~90% hit rate during typical flight
 *
 * FUTURE ENHANCEMENT (Optional):
 * - Persist to SharedPreferences or SQLite for app restart survival
 * - LRU eviction for memory management
 */
class ElevationCache {

    companion object {
        private const val TAG = "ElevationCache"
        private const val GRID_RESOLUTION = 0.01 // ~1km at equator
        private const val MAX_CACHE_ENTRIES = 4_096
        private const val LOOKUP_LOG_INTERVAL = 250
        private const val STORE_LOG_INTERVAL = 100
    }

    private inline fun debug(message: () -> String) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, message())
        }
    }

    // In-memory cache with bounded size to avoid unbounded session growth.
    private var evictions = 0
    private val cache = object : LinkedHashMap<String, Double>(MAX_CACHE_ENTRIES + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Double>?): Boolean {
            val shouldEvict = size > MAX_CACHE_ENTRIES
            if (shouldEvict) {
                evictions++
            }
            return shouldEvict
        }
    }

    // Cache statistics
    private var hits = 0
    private var misses = 0

    /**
     * Get cached elevation for location
     *
     * @param lat Latitude
     * @param lon Longitude
     * @return Cached elevation in meters, or null if not cached
     */
    @Synchronized
    fun get(lat: Double, lon: Double): Double? {
        val key = getCacheKey(lat, lon)
        val elevation = cache[key]

        if (elevation != null) {
            hits++
        } else {
            misses++
        }

        val lookups = hits + misses
        if (lookups % LOOKUP_LOG_INTERVAL == 0) {
            debug {
                "Lookup stats: size=${cache.size}, hitRate=${getHitRate()}%, evictions=$evictions"
            }
        }

        return elevation
    }

    /**
     * Store elevation in cache
     *
     * @param lat Latitude
     * @param lon Longitude
     * @param elevation Elevation in meters
     */
    @Synchronized
    fun store(lat: Double, lon: Double, elevation: Double) {
        val key = getCacheKey(lat, lon)
        cache[key] = elevation
        if (cache.size % STORE_LOG_INTERVAL == 0) {
            debug {
                "Store stats: size=${cache.size}, hitRate=${getHitRate()}%, evictions=$evictions"
            }
        }
    }

    /**
     * Generate cache key from coordinates
     *
     * Uses floor() to snap to GRID_RESOLUTION (0.01) grid cells
     * This ensures consistent grid boundaries (no rounding ambiguity at edges)
     *
     * KISS FIX: floor() instead of roundToInt() prevents duplicate API calls
     * at grid boundaries (e.g., 47.525 always maps to 47.52, not 47.52 or 47.53)
     *
     * Examples:
     * - (47.5234, 13.4567) -> "47.52,13.45"
     * - (47.5289, 13.4512) -> "47.52,13.45"
     * - (47.5254, 13.4599) -> "47.52,13.45" (consistent!)
     */
    private fun getCacheKey(lat: Double, lon: Double): String {
        val latRounded = kotlin.math.floor(lat / GRID_RESOLUTION) * GRID_RESOLUTION
        val lonRounded = kotlin.math.floor(lon / GRID_RESOLUTION) * GRID_RESOLUTION
        return "$latRounded,$lonRounded"
    }

    /**
     * Clear all cached elevations
     */
    @Synchronized
    fun clear() {
        val size = cache.size
        cache.clear()
        hits = 0
        misses = 0
        evictions = 0
        debug { "Cache cleared ($size locations removed)" }
    }

    /**
     * Get cache statistics
     */
    @Synchronized
    fun getStats(): CacheStats {
        return CacheStats(
            size = cache.size,
            hits = hits,
            misses = misses,
            hitRate = getHitRate()
        )
    }

    /**
     * Calculate cache hit rate percentage
     */
    @Synchronized
    private fun getHitRate(): Int {
        val total = hits + misses
        return if (total > 0) {
            ((hits.toDouble() / total) * 100).roundToInt()
        } else {
            0
        }
    }

    /**
     * Check if location is in cache
     */
    @Synchronized
    fun contains(lat: Double, lon: Double): Boolean {
        val key = getCacheKey(lat, lon)
        return cache.containsKey(key)
    }

    /**
     * Get cache size (number of unique locations)
     */
    @Synchronized
    fun size(): Int = cache.size
}

/**
 * Cache statistics data class
 */
data class CacheStats(
    val size: Int,        // Number of cached locations
    val hits: Int,        // Number of cache hits
    val misses: Int,      // Number of cache misses
    val hitRate: Int      // Hit rate percentage (0-100)
)
