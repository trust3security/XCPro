package com.example.xcpro.adsb

const val ADSB_ICON_SIZE_MIN_PX = 50
const val ADSB_ICON_SIZE_DEFAULT_PX = 56
const val ADSB_ICON_SIZE_MAX_PX = 124

fun clampAdsbIconSizePx(sizePx: Int): Int =
    sizePx.coerceIn(ADSB_ICON_SIZE_MIN_PX, ADSB_ICON_SIZE_MAX_PX)
