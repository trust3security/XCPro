package com.example.dfcards.dfcards.calculations

import android.content.Context
import android.util.Log
import kotlin.math.*

/**
 * Simple AGL (Above Ground Level) Calculator - KISS + SSOT
 *
 * APPROACH: Open-Meteo Elevation API (Option A - Simplest)
 *
 * ADVANTAGES:
 * - ✅ Free, no API key
 * - ✅ Global coverage (SRTM30, 90m resolution)
 * - ✅ Simple HTTP API
 * - ✅ Cache-first strategy (fast repeat lookups)
 * - ✅ 200m throttling (prevents excessive API calls)
 *
 * FORMULA:
 * ```
 * AGL = GPS_Altitude - Ground_Elevation
 * ```
 *
 * DATA FLOW:
 * ```
 * GPS Update
 *   ↓
 * calculateAgl(gpsAlt, lat, lon)
 *   ↓
 * Check if moved >200m from last fetch
 *   ├─ No → Use cached value (skip API)
 *   └─ Yes → Check cache
 *            ├─ Hit → Return cached elevation (< 1ms)
 *            └─ Miss → API call → Cache → Return (100-500ms)
 * ```
 */
class SimpleAglCalculator(context: Context) {

    companion object {
        private const val TAG = "SimpleAGL"
        private const val FETCH_THROTTLE_DISTANCE_METERS = 200.0 // Only fetch if moved >200m
    }

    // SSOT: Open-Meteo Elevation API (with permission + network checks)
    private val api = OpenMeteoElevationApi(context)

    // Cache layer for performance
    private val cache = ElevationCache()

    // Throttling: Track last fetched location to prevent excessive API calls
    private var lastFetchLocation: Pair<Double, Double>? = null

    /**
     * Calculate AGL from barometric altitude and terrain elevation
     *
     * @param altitude Current altitude (meters ASL) - use barometric for stability
     * @param lat Current latitude
     * @param lon Current longitude
     * @param speed Current ground speed (m/s), used for ground detection
     * @return AGL in meters, or null if terrain data unavailable
     */
    suspend fun calculateAgl(altitude: Double, lat: Double, lon: Double, speed: Double? = null): Double? {
        // ✅ THROTTLING: Only fetch if moved >200m from last fetch location
        val shouldFetch = lastFetchLocation?.let { (lastLat, lastLon) ->
            val distance = haversineDistance(lastLat, lastLon, lat, lon)
            distance > FETCH_THROTTLE_DISTANCE_METERS
        } ?: true  // First fetch - no previous location

        // Get terrain elevation (cache first)
        var groundElevation = cache.get(lat, lon)

        if (groundElevation == null && shouldFetch) {
            // Not in cache AND moved >200m - fetch from API
            groundElevation = api.fetchElevation(lat, lon)

            if (groundElevation != null) {
                cache.store(lat, lon, groundElevation)
                lastFetchLocation = Pair(lat, lon)  // Update last fetch location
                Log.d(TAG, "📡 Fetched ${groundElevation.toInt()}m elevation, cached")
            } else {
                Log.w(TAG, "⚠️ No terrain data available (network issue)")
                return null
            }
        } else if (groundElevation == null && !shouldFetch) {
            // Not in cache but haven't moved >200m yet - wait for throttle
            Log.d(TAG, "⏳ Throttled: Moved <200m, waiting for cache or movement")
            return null
        }

        // At this point, groundElevation must not be null (either from cache or fetch)
        // Add explicit check for compiler
        if (groundElevation == null) {
            Log.e(TAG, "❌ Unexpected: groundElevation is null after fetch logic")
            return null
        }

        // Calculate AGL
        val agl = altitude - groundElevation

        // ✅ IMPROVED: Handle negative AGL properly
        if (agl < -50.0) {
            // Large negative AGL indicates barometric altitude error (wrong QNH)
            Log.w(TAG, "⚠️ AGL very negative (${agl.toInt()}m) - possible QNH calibration issue")
            return null  // Return null to show "NO DATA" instead of confusing value
        }

        // Small negative values OK (rounding/GPS error) - coerce to 0
        val finalAgl = agl.coerceAtLeast(0.0)

        Log.d(TAG, "✅ AGL: ${finalAgl.toInt()}m (Alt: ${altitude.toInt()}m, Ground: ${groundElevation.toInt()}m, Speed: ${speed?.toInt() ?: "?"}m/s)")

        return finalAgl
    }

    /**
     * Get terrain elevation at a specific location (for QNH calibration)
     *
     * This method is used by the barometric altitude calculator to get stable
     * terrain elevation data for QNH calibration, avoiding noisy GPS vertical readings.
     *
     * @param lat Latitude
     * @param lon Longitude
     * @return Terrain elevation in meters MSL, or null if unavailable
     */
    suspend fun getTerrainElevation(lat: Double, lon: Double): Double? {
        // Check cache first (instant if available)
        var elevation = cache.get(lat, lon)

        if (elevation == null) {
            // Not in cache - fetch from API
            elevation = api.fetchElevation(lat, lon)

            if (elevation != null) {
                // Cache for future use
                cache.store(lat, lon, elevation)
                Log.d(TAG, "📡 QNH Calibration: Fetched ${elevation.toInt()}m elevation at ($lat, $lon)")
            } else {
                Log.w(TAG, "⚠️ QNH Calibration: No terrain data available (network issue)")
            }
        } else {
            Log.d(TAG, "✅ QNH Calibration: Using cached ${elevation.toInt()}m elevation")
        }

        return elevation
    }

    /**
     * Format AGL for display (simple version - deprecated)
     *
     * @param agl AGL value or null
     * @return Formatted string: "0m" or "250m" or "---"
     */
    @Deprecated("Use formatAglWithStatus for better ground detection")
    fun formatAgl(agl: Double?): String {
        return when {
            agl == null -> "---"
            agl < 5.0 -> "0" // On ground (within GPS error ~5m)
            else -> "${agl.toInt()}"
        }
    }

    /**
     * Format AGL for display with speed-based ground detection
     *
     * ✅ IMPROVED: Uses speed to distinguish "on ground" from "flying low"
     *
     * @param agl AGL value or null
     * @param speed Ground speed in m/s (null if unavailable)
     * @return Pair of (value, status) for display
     *
     * Examples:
     * - AGL=3m, speed=0m/s → ("0", "ON GROUND") ✅ Landed
     * - AGL=3m, speed=25m/s → ("3", "LOW!") ⚠️ Flying dangerously low!
     * - AGL=500m, speed=30m/s → ("500", "AGL") ✅ Normal flight
     * - AGL=null, speed=? → ("---", "NO DATA") ❌ No terrain data
     */
    fun formatAglWithStatus(agl: Double?, speed: Double?): Pair<String, String> {
        return when {
            // No AGL data available
            agl == null -> Pair("---", "NO DATA")

            // Very low altitude - check speed to distinguish ground vs flying
            agl < 5.0 -> {
                if (speed != null && speed < 2.0) {
                    // Low altitude AND slow speed = on ground (landed/taxiing)
                    Pair("0", "ON GROUND")
                } else {
                    // Low altitude BUT moving fast = DANGER! Flying very low
                    Pair("${agl.toInt()}", "LOW!")
                }
            }

            // Normal flight altitude
            else -> Pair("${agl.toInt()}", "AGL")
        }
    }

    /**
     * Get cache statistics (for debugging/monitoring)
     */
    fun getCacheStats(): CacheStats {
        return cache.getStats()
    }

    /**
     * Clear terrain cache (for testing)
     */
    fun clearCache() {
        cache.clear()
        Log.d(TAG, "Cache cleared")
    }

    /**
     * Pre-fetch elevations along route (optional optimization)
     *
     * Call this before flight to pre-cache terrain along planned route
     */
    suspend fun prefetchRoute(waypoints: List<Pair<Double, Double>>) {
        Log.d(TAG, "📥 Pre-fetching ${waypoints.size} waypoints...")

        waypoints.forEach { (lat, lon) ->
            if (!cache.contains(lat, lon)) {
                val elevation = api.fetchElevation(lat, lon)
                if (elevation != null) {
                    cache.store(lat, lon, elevation)
                }
            }
        }

        val stats = cache.getStats()
        Log.d(TAG, "✅ Pre-fetch complete: ${stats.size} locations cached")
    }

    /**
     * Calculate distance between two coordinates using Haversine formula
     *
     * @param lat1 First latitude
     * @param lon1 First longitude
     * @param lat2 Second latitude
     * @param lon2 Second longitude
     * @return Distance in meters
     */
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}
