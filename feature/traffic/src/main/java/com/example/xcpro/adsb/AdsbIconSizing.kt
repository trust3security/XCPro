package com.example.xcpro.adsb

const val ADSB_ICON_SIZE_MIN_PX = 124
const val ADSB_ICON_SIZE_DEFAULT_PX = 124
const val ADSB_ICON_SIZE_MAX_PX = 248

fun clampAdsbIconSizePx(sizePx: Int): Int =
    sizePx.coerceIn(ADSB_ICON_SIZE_MIN_PX, ADSB_ICON_SIZE_MAX_PX)
