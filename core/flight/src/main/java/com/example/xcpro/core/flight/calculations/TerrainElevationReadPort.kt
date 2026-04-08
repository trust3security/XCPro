package com.example.xcpro.core.flight.calculations

interface TerrainElevationReadPort {
    suspend fun getElevationMeters(lat: Double, lon: Double): Double?
}
