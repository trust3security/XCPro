package com.trust3.xcpro.map

data class MapViewSize(
    val widthPx: Int,
    val heightPx: Int
)

interface MapViewSizeProvider {
    fun size(): MapViewSize
}
