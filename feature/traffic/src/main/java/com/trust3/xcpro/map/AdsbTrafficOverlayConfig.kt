package com.trust3.xcpro.map

import android.graphics.Color

internal const val ADSB_TRAFFIC_OVERLAY_TAG = "AdsbTrafficOverlay"

internal const val ADSB_TRAFFIC_SOURCE_ID = "adsb-traffic-source"
internal const val ADSB_TRAFFIC_LEADER_LINE_SOURCE_ID = "adsb-traffic-leader-line-source"
internal const val ADSB_TRAFFIC_LEADER_LINE_LAYER_ID = "adsb-traffic-leader-line-layer"
internal const val ADSB_TRAFFIC_ICON_OUTLINE_LAYER_ID = "adsb-traffic-icon-outline-layer"
internal const val ADSB_TRAFFIC_ICON_LAYER_ID = "adsb-traffic-icon-layer"
internal const val ADSB_TRAFFIC_TOP_LABEL_LAYER_ID = "adsb-traffic-top-label-layer"
internal const val ADSB_TRAFFIC_BOTTOM_LABEL_LAYER_ID = "adsb-traffic-bottom-label-layer"

internal const val ADSB_TRAFFIC_MAX_TARGETS = 120
internal const val ADSB_TRAFFIC_LIVE_ALPHA = 0.90
internal const val ADSB_TRAFFIC_STALE_ALPHA = 0.45

internal const val ADSB_TRAFFIC_LABEL_TEXT_SIZE_SP = 13f
internal const val ADSB_TRAFFIC_LABEL_TEXT_OFFSET_BASE_Y = 1.7f
internal const val ADSB_TRAFFIC_LABEL_TEXT_COLOR = "#000000"
internal const val ADSB_TRAFFIC_PACKED_GROUP_COLLISION_SIZE_DP = 40f
internal const val ADSB_TRAFFIC_OUTLINE_ICON_SCALE_MULTIPLIER = 1.14f
internal val ADSB_TRAFFIC_LABEL_FONT_STACK = arrayOf(
    "Open Sans Semibold",
    "Noto Sans Medium",
    "Open Sans Regular",
    "Arial Unicode MS Regular"
)

internal const val ADSB_TRAFFIC_ANIMATION_FRAME_INTERVAL_MS = 66L
internal val ADSB_TRAFFIC_EMERGENCY_ICON_COLOR: Int = Color.parseColor(AdsbProximityColorPolicy.EMERGENCY_HEX)
