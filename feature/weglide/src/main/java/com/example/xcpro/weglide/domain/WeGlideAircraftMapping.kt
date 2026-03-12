package com.example.xcpro.weglide.domain

data class WeGlideAircraftMapping(
    val localProfileId: String,
    val weglideAircraftId: Long,
    val weglideAircraftName: String,
    val updatedAtEpochMs: Long
)
