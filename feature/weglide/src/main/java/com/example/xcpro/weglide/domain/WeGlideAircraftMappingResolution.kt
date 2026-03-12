package com.example.xcpro.weglide.domain

data class WeGlideAircraftMappingResolution(
    val profileId: String,
    val status: Status,
    val mapping: WeGlideAircraftMapping? = null,
    val aircraft: WeGlideAircraft? = null
) {
    enum class Status {
        MAPPED,
        MAPPING_MISSING,
        AIRCRAFT_MISSING
    }
}
