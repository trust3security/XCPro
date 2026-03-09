package com.example.xcpro.ogn

data class OgnGliderTrailSegment(
    val id: String,
    val sourceTargetId: String,
    val sourceLabel: String,
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double,
    val endLongitude: Double,
    val colorIndex: Int,
    val widthPx: Float,
    val timestampMonoMs: Long
)
