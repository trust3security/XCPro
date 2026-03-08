package com.example.xcpro.map

internal const val ADSB_ICON_STYLE_UNKNOWN_DEFAULT = "adsb_icon_unknown"
internal const val ADSB_ICON_STYLE_UNKNOWN_LEGACY = "adsb_icon_unknown_legacy"

internal fun isUnknownAdsbIconStyleId(styleImageId: String): Boolean =
    styleImageId == ADSB_ICON_STYLE_UNKNOWN_DEFAULT ||
        styleImageId == ADSB_ICON_STYLE_UNKNOWN_LEGACY
