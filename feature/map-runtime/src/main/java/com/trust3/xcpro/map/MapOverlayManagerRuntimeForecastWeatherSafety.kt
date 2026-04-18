package com.trust3.xcpro.map

import com.trust3.xcpro.forecast.ForecastLegendSpec
import com.trust3.xcpro.forecast.ForecastTileSpec
import com.trust3.xcpro.forecast.ForecastWindDisplayMode

internal fun renderForecastRasterOverlaySafely(
    overlay: ForecastRasterOverlay?,
    tileSpec: ForecastTileSpec,
    opacity: Float,
    windOverlayScale: Float,
    windDisplayMode: ForecastWindDisplayMode,
    legendSpec: ForecastLegendSpec?,
    fallbackErrorMessage: String,
    onFailure: (Throwable) -> Unit
): String? = runCatching {
    overlay?.render(
        tileSpec = tileSpec,
        opacity = opacity,
        windOverlayScale = windOverlayScale,
        windDisplayMode = windDisplayMode,
        legendSpec = legendSpec
    )
    null
}.getOrElse { throwable ->
    onFailure(throwable)
    throwable.message?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackErrorMessage
}
