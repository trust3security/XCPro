package com.example.xcpro.map.widgets

import com.example.xcpro.core.common.geometry.DensityScale
import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.core.common.geometry.dpToPx
import javax.inject.Inject

class MapWidgetLayoutUseCase @Inject constructor(
    private val repository: MapWidgetLayoutRepository
) {
    fun loadLayout(screenWidthPx: Float, screenHeightPx: Float, density: DensityScale): MapWidgetOffsets {
        val sideSize = resolvedSize(
            widgetId = MapWidgetId.SIDE_HAMBURGER,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = density
        )
        val sideRawOffset = repository.readOffset(MapWidgetId.SIDE_HAMBURGER)
            ?: OffsetPx(
                x = HAMBURGER_DEFAULT_X,
                y = hamburgerDefaultY(density)
            )
        val side = clampOffset(
            offset = sideRawOffset,
            sizePx = sideSize,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx
        )
        val flightMode = repository.readOffset(MapWidgetId.FLIGHT_MODE)
            ?: OffsetPx(
                x = FLIGHT_MODE_DEFAULT_X,
                y = flightModeDefaultY(density)
            )
        val settingsSize = resolvedSize(
            widgetId = MapWidgetId.SETTINGS_SHORTCUT,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = density
        )
        val settingsRawOffset = repository.readOffset(MapWidgetId.SETTINGS_SHORTCUT)
            ?: OffsetPx(
                x = SETTINGS_SHORTCUT_DEFAULT_X,
                y = settingsShortcutDefaultY(density)
            )
        val settings = clampOffset(
            offset = settingsRawOffset,
            sizePx = settingsSize,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx
        )
        val ballast = repository.readOffset(MapWidgetId.BALLAST)
            ?: OffsetPx(
                x = ballastDefaultX(screenWidthPx, density),
                y = ballastDefaultY(density)
            )

        if (side != sideRawOffset) {
            repository.saveOffset(MapWidgetId.SIDE_HAMBURGER, side)
        }
        if (settings != settingsRawOffset) {
            repository.saveOffset(MapWidgetId.SETTINGS_SHORTCUT, settings)
        }

        return MapWidgetOffsets(
            sideHamburger = side,
            flightMode = flightMode,
            settingsShortcut = settings,
            ballast = ballast,
            sideHamburgerSizePx = sideSize,
            settingsShortcutSizePx = settingsSize
        )
    }

    fun saveOffset(widgetId: MapWidgetId, offset: OffsetPx) {
        repository.saveOffset(widgetId, offset)
    }

    fun saveSizePx(widgetId: MapWidgetId, sizePx: Float) {
        if (!MapWidgetSizePolicy.supportsSize(widgetId)) return
        repository.saveSizePx(widgetId, sizePx)
    }

    fun commitOffset(
        current: MapWidgetOffsets,
        widgetId: MapWidgetId,
        offset: OffsetPx,
        screenWidthPx: Float,
        screenHeightPx: Float
    ): MapWidgetOffsets {
        val updated = when (widgetId) {
            MapWidgetId.SIDE_HAMBURGER -> {
                val clamped = clampOffset(
                    offset = offset,
                    sizePx = current.sideHamburgerSizePx,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx
                )
                current.copy(sideHamburger = clamped)
            }
            MapWidgetId.FLIGHT_MODE -> current.copy(flightMode = offset)
            MapWidgetId.SETTINGS_SHORTCUT -> {
                val clamped = clampOffset(
                    offset = offset,
                    sizePx = current.settingsShortcutSizePx,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx
                )
                current.copy(settingsShortcut = clamped)
            }
            MapWidgetId.BALLAST -> current.copy(ballast = offset)
        }
        repository.saveOffset(widgetId, offsetFor(updated, widgetId))
        return updated
    }

    fun commitSize(
        current: MapWidgetOffsets,
        widgetId: MapWidgetId,
        sizePx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        density: DensityScale
    ): MapWidgetOffsets {
        if (!MapWidgetSizePolicy.supportsSize(widgetId)) return current

        val clampedSize = MapWidgetSizePolicy.clampSizePx(
            widgetId = widgetId,
            requestedSizePx = sizePx,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = density
        )

        val updated = when (widgetId) {
            MapWidgetId.SIDE_HAMBURGER -> {
                val clampedOffset = clampOffset(
                    offset = current.sideHamburger,
                    sizePx = clampedSize,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx
                )
                current.copy(
                    sideHamburger = clampedOffset,
                    sideHamburgerSizePx = clampedSize
                )
            }
            MapWidgetId.SETTINGS_SHORTCUT -> {
                val clampedOffset = clampOffset(
                    offset = current.settingsShortcut,
                    sizePx = clampedSize,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx
                )
                current.copy(
                    settingsShortcut = clampedOffset,
                    settingsShortcutSizePx = clampedSize
                )
            }
            else -> current
        }

        repository.saveSizePx(widgetId, clampedSize)
        repository.saveOffset(widgetId, offsetFor(updated, widgetId))
        return updated
    }

    private fun hamburgerDefaultY(density: DensityScale): Float =
        density.dpToPx(HAMBURGER_PADDING_TOP_DP)

    private fun flightModeDefaultY(density: DensityScale): Float =
        density.dpToPx(FLIGHT_MODE_OFFSET_DP)

    private fun settingsShortcutDefaultY(density: DensityScale): Float =
        density.dpToPx(SETTINGS_SHORTCUT_OFFSET_DP)

    private fun ballastDefaultX(screenWidthPx: Float, density: DensityScale): Float {
        val pillWidthPx = density.dpToPx(BALLAST_WIDTH_DP)
        val paddingPx = density.dpToPx(BALLAST_PADDING_END_DP)
        return (screenWidthPx - paddingPx - pillWidthPx).coerceAtLeast(0f)
    }

    private fun ballastDefaultY(density: DensityScale): Float =
        density.dpToPx(BALLAST_PADDING_TOP_DP)

    private fun resolvedSize(
        widgetId: MapWidgetId,
        screenWidthPx: Float,
        screenHeightPx: Float,
        density: DensityScale
    ): Float {
        val persisted = repository.readSizePx(widgetId)
        val defaultSize = MapWidgetSizePolicy.defaultSizePx(widgetId, density)
        val clamped = MapWidgetSizePolicy.clampSizePx(
            widgetId = widgetId,
            requestedSizePx = persisted ?: defaultSize,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = density
        )
        if (persisted == null || persisted != clamped) {
            repository.saveSizePx(widgetId, clamped)
        }
        return clamped
    }

    private fun clampOffset(
        offset: OffsetPx,
        sizePx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float
    ): OffsetPx {
        val maxX = (screenWidthPx - sizePx).coerceAtLeast(0f)
        val maxY = (screenHeightPx - sizePx).coerceAtLeast(0f)
        return OffsetPx(
            x = offset.x.coerceIn(0f, maxX),
            y = offset.y.coerceIn(0f, maxY)
        )
    }

    private fun offsetFor(layout: MapWidgetOffsets, widgetId: MapWidgetId): OffsetPx =
        when (widgetId) {
            MapWidgetId.SIDE_HAMBURGER -> layout.sideHamburger
            MapWidgetId.FLIGHT_MODE -> layout.flightMode
            MapWidgetId.SETTINGS_SHORTCUT -> layout.settingsShortcut
            MapWidgetId.BALLAST -> layout.ballast
        }

    private companion object {
        private const val HAMBURGER_DEFAULT_X = 16f
        private const val FLIGHT_MODE_DEFAULT_X = 16f
        private const val SETTINGS_SHORTCUT_DEFAULT_X = 16f
        private const val HAMBURGER_PADDING_TOP_DP = 16f
        private const val FLIGHT_MODE_OFFSET_DP = 80f
        private const val SETTINGS_SHORTCUT_OFFSET_DP = 140f
        private const val BALLAST_WIDTH_DP = 40f
        private const val BALLAST_PADDING_END_DP = 16f
        private const val BALLAST_PADDING_TOP_DP = 140f
    }
}
