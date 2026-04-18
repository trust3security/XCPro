package com.trust3.xcpro.weglide.domain

interface WeGlideAircraftMappingReadRepository {
    suspend fun getMapping(profileId: String): WeGlideAircraftMapping?
    suspend fun getAircraftById(aircraftId: Long): WeGlideAircraft?
}
