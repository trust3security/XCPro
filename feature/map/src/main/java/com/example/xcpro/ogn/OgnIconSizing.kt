package com.example.xcpro.ogn

const val OGN_ICON_SIZE_MIN_PX = 50
const val OGN_ICON_SIZE_DEFAULT_PX = 56
const val OGN_ICON_SIZE_MAX_PX = 124

fun clampOgnIconSizePx(sizePx: Int): Int =
    sizePx.coerceIn(OGN_ICON_SIZE_MIN_PX, OGN_ICON_SIZE_MAX_PX)
