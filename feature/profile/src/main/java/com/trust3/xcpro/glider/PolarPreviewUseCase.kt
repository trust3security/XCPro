package com.trust3.xcpro.glider

import com.trust3.xcpro.common.glider.ActivePolarSnapshot
import com.trust3.xcpro.common.glider.ActivePolarSource
import javax.inject.Inject

data class PolarPreviewState(
    val headline: String,
    val hint: String,
    val sinkMs: Double?,
    val showFallbackHelp: Boolean
)

class PolarPreviewUseCase @Inject constructor(
    private val sinkProvider: StillAirSinkProvider
) {
    fun resolve(
        activePolar: ActivePolarSnapshot?,
        speedKmh: Double
    ): PolarPreviewState {
        val safeSpeedKmh = speedKmh.coerceAtLeast(0.0)
        val speedLabel = safeSpeedKmh.toInt()
        val sink = sinkProvider.sinkAtSpeed(safeSpeedKmh / 3.6)
            ?.takeIf { it.isFinite() }
        val snapshot = activePolar

        if (snapshot == null) {
            return PolarPreviewState(
                headline = "Select an aircraft to preview polar",
                hint = "Using model polar",
                sinkMs = null,
                showFallbackHelp = false
            )
        }

        val headline = when (snapshot.source) {
            ActivePolarSource.MANUAL_THREE_POINT -> {
                val selectedName = snapshot.selectedModelName
                if (selectedName.isNullOrBlank()) {
                    "3-point polar - $speedLabel km/h"
                } else {
                    "3-point: $selectedName - $speedLabel km/h"
                }
            }

            ActivePolarSource.FALLBACK_MODEL ->
                "Fallback active: ${snapshot.effectiveModelName} - $speedLabel km/h"

            ActivePolarSource.SELECTED_MODEL ->
                "Model: ${snapshot.selectedModelName ?: snapshot.effectiveModelName} - $speedLabel km/h"
        }

        val hint = when (snapshot.source) {
            ActivePolarSource.MANUAL_THREE_POINT -> "Using 3-point polar"
            ActivePolarSource.FALLBACK_MODEL -> "Using default club fallback polar"
            ActivePolarSource.SELECTED_MODEL -> "Using model polar"
        }

        return PolarPreviewState(
            headline = headline,
            hint = hint,
            sinkMs = sink,
            showFallbackHelp = snapshot.source == ActivePolarSource.FALLBACK_MODEL
        )
    }
}
