package com.example.xcpro.adsb.metadata.domain

import kotlinx.coroutines.flow.StateFlow

interface AircraftMetadataRepository {
    val metadataRevision: StateFlow<Long>

    suspend fun getMetadataFor(icao24s: List<String>): Map<String, AircraftMetadata>
}

