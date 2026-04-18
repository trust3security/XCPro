package com.trust3.xcpro.ogn

const val OGN_ICON_SIZE_MIN_PX = 124
const val OGN_ICON_SIZE_DEFAULT_PX = 124
const val OGN_ICON_SIZE_MAX_PX = 248

fun clampOgnIconSizePx(sizePx: Int): Int =
    sizePx.coerceIn(OGN_ICON_SIZE_MIN_PX, OGN_ICON_SIZE_MAX_PX)
