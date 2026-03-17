package com.example.dfcards.dfcards.calculations

/**
 * Shared terrain read seam for runtime consumers that need terrain elevation
 * without owning Android/network/cache concerns directly.
 */
interface TerrainElevationReadPort {
    suspend fun getElevationMeters(lat: Double, lon: Double): Double?
}
