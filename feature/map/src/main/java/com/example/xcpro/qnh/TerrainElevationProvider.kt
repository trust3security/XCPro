package com.example.xcpro.qnh

interface TerrainElevationProvider {
    suspend fun getElevationMeters(lat: Double, lon: Double): Double?
}

