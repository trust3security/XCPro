package com.example.xcpro.weather.rain

internal const val WEATHER_RAIN_ATTRIBUTION_LINK_URL = "https://www.rainviewer.com"
internal const val WEATHER_RAIN_ATTRIBUTION_TEXT = "RainViewer"

internal fun weatherRainTileAttributionHtml(): String =
    "<a href=\"$WEATHER_RAIN_ATTRIBUTION_LINK_URL\">$WEATHER_RAIN_ATTRIBUTION_TEXT</a>"
