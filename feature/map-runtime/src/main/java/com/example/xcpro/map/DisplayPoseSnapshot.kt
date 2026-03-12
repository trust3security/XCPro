package com.example.xcpro.map

import org.maplibre.android.geometry.LatLng

data class DisplayPoseSnapshot(
    val location: LatLng,
    val timestampMs: Long,
    val frameId: Long
)
