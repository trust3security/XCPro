package com.example.xcpro.map.widgets

import com.example.xcpro.core.common.geometry.DensityScale
import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.core.common.geometry.dpToPx
import javax.inject.Inject

class MapWidgetLayoutUseCase @Inject constructor(
    private val repository: MapWidgetLayoutRepository
) {
    fun loadLayout(screenWidthPx: Float, screenHeightPx: Float, density: DensityScale): MapWidgetOffsets {
        val side = repository.readOffset(MapWidgetId.SIDE_HAMBURGER)
            ?: OffsetPx(
                x = HAMBURGER_DEFAULT_X,
                y = hamburgerDefaultY(screenHeightPx, density)
            )
        val flightMode = repository.readOffset(MapWidgetId.FLIGHT_MODE)
            ?: OffsetPx(
                x = FLIGHT_MODE_DEFAULT_X,
                y = flightModeDefaultY(density)
            )
        val ballast = repository.readOffset(MapWidgetId.BALLAST)
            ?: OffsetPx(
                x = ballastDefaultX(screenWidthPx, density),
                y = ballastDefaultY(density)
            )

        return MapWidgetOffsets(
            sideHamburger = side,
            flightMode = flightMode,
            ballast = ballast
        )
    }

    fun saveOffset(widgetId: MapWidgetId, offset: OffsetPx) {
        repository.saveOffset(widgetId, offset)
    }

    private fun hamburgerDefaultY(screenHeightPx: Float, density: DensityScale): Float {
        val offsetPx = density.dpToPx(HAMBURGER_OFFSET_DP)
        return (screenHeightPx / 2f - offsetPx).coerceAtLeast(0f)
    }

    private fun flightModeDefaultY(density: DensityScale): Float =
        density.dpToPx(FLIGHT_MODE_OFFSET_DP)

    private fun ballastDefaultX(screenWidthPx: Float, density: DensityScale): Float {
        val pillWidthPx = density.dpToPx(BALLAST_WIDTH_DP)
        val paddingPx = density.dpToPx(BALLAST_PADDING_END_DP)
        return (screenWidthPx - paddingPx - pillWidthPx).coerceAtLeast(0f)
    }

    private fun ballastDefaultY(density: DensityScale): Float =
        density.dpToPx(BALLAST_PADDING_TOP_DP)

    private companion object {
        private const val HAMBURGER_DEFAULT_X = 16f
        private const val FLIGHT_MODE_DEFAULT_X = 16f
        private const val HAMBURGER_OFFSET_DP = 32f
        private const val FLIGHT_MODE_OFFSET_DP = 80f
        private const val BALLAST_WIDTH_DP = 40f
        private const val BALLAST_PADDING_END_DP = 16f
        private const val BALLAST_PADDING_TOP_DP = 140f
    }
}
