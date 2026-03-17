package com.example.xcpro.common.glider

enum class ActivePolarSource {
    SELECTED_MODEL,
    MANUAL_THREE_POINT,
    FALLBACK_MODEL
}

data class ActivePolarSnapshot(
    val source: ActivePolarSource,
    val selectedModelId: String?,
    val selectedModelName: String?,
    val effectiveModelId: String,
    val effectiveModelName: String,
    val isFallbackPolarActive: Boolean,
    val hasThreePointPolar: Boolean,
    val referenceWeightConfigured: Boolean,
    val userCoefficientsConfigured: Boolean,
    val iasMinMs: Double?,
    val iasMaxMs: Double?
)
