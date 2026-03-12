package com.example.xcpro.weglide.api

import com.example.xcpro.weglide.domain.WeGlideAircraft
import com.google.gson.annotations.SerializedName

data class WeGlideAircraftDto(
    val id: Long,
    val name: String,
    val kind: String? = null,
    @SerializedName("sc_class") val scoringClass: String? = null
)

fun WeGlideAircraftDto.toDomain(): WeGlideAircraft {
    return WeGlideAircraft(
        aircraftId = id,
        name = name,
        kind = kind,
        scoringClass = scoringClass
    )
}
