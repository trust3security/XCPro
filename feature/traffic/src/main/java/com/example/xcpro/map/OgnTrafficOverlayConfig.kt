package com.example.xcpro.map

import android.graphics.Color
internal const val TAG = "OgnTrafficOverlay"

internal const val SOURCE_ID = "ogn-traffic-source"
internal const val ICON_LAYER_ID = "ogn-traffic-icon-layer"
internal const val TOP_LABEL_LAYER_ID = "ogn-traffic-label-top-layer"
internal const val BOTTOM_LABEL_LAYER_ID = "ogn-traffic-label-bottom-layer"
internal const val DEFAULT_ICON_IMAGE_ID = "ogn_icon_unknown"
internal const val SATELLITE_GLIDER_ICON_IMAGE_ID = "ogn_icon_glider_satellite"
internal const val RELATIVE_GLIDER_ABOVE_ICON_IMAGE_ID = "ogn_icon_glider_rel_above"
internal const val RELATIVE_GLIDER_BELOW_ICON_IMAGE_ID = "ogn_icon_glider_rel_below"
internal const val RELATIVE_GLIDER_NEAR_ICON_IMAGE_ID = "ogn_icon_glider_rel_near"

internal const val PROP_TOP_LABEL = "label_top"
internal const val PROP_BOTTOM_LABEL = "label_bottom"
internal const val PROP_ALPHA = "alpha"
internal const val PROP_TRACK_DEG = "track_deg"
internal const val PROP_TARGET_ID = "target_id"
internal const val PROP_TARGET_KEY = "target_key"
internal const val PROP_ICON_ID = "icon_id"

internal const val MAX_TARGETS = 500
internal const val STALE_VISUAL_AFTER_MS = 60_000L

internal const val LIVE_ALPHA = 0.90
internal const val STALE_ALPHA = 0.45

internal val ICON_BITMAP_BASE_SIZE_PX = OGN_ICON_SIZE_DEFAULT_PX

internal const val LABEL_TEXT_SIZE_BASE_SP = 13f
internal const val MIN_LABEL_TEXT_SIZE_SP = 12f
internal const val MAX_LABEL_TEXT_SIZE_SP = 17f
internal const val LABEL_TEXT_OFFSET_BASE_Y = 1.6f
internal const val LABEL_TEXT_COLOR = "#000000"

internal val LABEL_FONT_STACK = arrayOf(
    "Open Sans Semibold",
    "Noto Sans Medium",
    "Open Sans Regular",
    "Arial Unicode MS Regular"
)
internal val RELATIVE_GLIDER_ABOVE_TINT: Int = Color.parseColor("#1B5E20")
internal val RELATIVE_GLIDER_BELOW_TINT: Int = Color.parseColor("#0D47A1")
internal val RELATIVE_GLIDER_NEAR_TINT: Int = Color.parseColor("#101010")
