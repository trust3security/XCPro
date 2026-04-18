package com.trust3.xcpro.weglide.domain

data class WeGlidePostFlightUploadPrompt(
    val request: WeGlideFinalizedFlightUploadRequest,
    val profileId: String,
    val profileName: String?,
    val aircraftName: String,
    val fileName: String
)
