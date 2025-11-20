package com.example.dfcards.dfcards

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

@Stable
data class FlightData(
    val id: String,
    val label: String,
    val primaryValue: String,
    val secondaryValue: String? = null,
    val labelFontSize: Int = 8,
    val primaryFontSize: Int = 11,
    val secondaryFontSize: Int = 3,
    val primaryValueNumber: String? = null,
    val primaryValueUnit: String? = null,
    val primaryColorOverride: Color? = null
)

@Stable
data class CardState(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val flightData: FlightData
)
