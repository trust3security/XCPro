package com.trust3.xcpro.adsb

// User-facing ADS-B icon size preference range.
const val ADSB_ICON_SIZE_MIN_PX = 80
const val ADSB_ICON_SIZE_DEFAULT_PX = 100
const val ADSB_ICON_SIZE_MAX_PX = 180

// Rendering baseline used for style image bitmap registration and scale math.
const val ADSB_ICON_BITMAP_BASE_SIZE_PX = 124

fun clampAdsbIconSizePx(sizePx: Int): Int =
    sizePx.coerceIn(ADSB_ICON_SIZE_MIN_PX, ADSB_ICON_SIZE_MAX_PX)
