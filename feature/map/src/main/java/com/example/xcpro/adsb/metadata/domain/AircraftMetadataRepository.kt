package com.example.xcpro.adsb.metadata.domain

interface AircraftMetadataRepository {
    suspend fun getMetadataFor(icao24s: List<String>): Map<String, AircraftMetadata>
}

