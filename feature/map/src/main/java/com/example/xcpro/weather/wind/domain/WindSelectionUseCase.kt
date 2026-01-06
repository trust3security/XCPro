package com.example.xcpro.weather.wind.domain

import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindVector

data class WindCandidate(
    val vector: WindVector,
    val source: WindSource,
    val quality: Int,
    val timestampMillis: Long
)

class WindSelectionUseCase {

    fun select(
        auto: WindCandidate?,
        manual: WindCandidate?,
        external: WindCandidate?
    ): WindCandidate? {
        val manualTimestamp = manual?.timestampMillis ?: 0L
        val autoNewerThanManual = auto != null && auto.timestampMillis > manualTimestamp

        return when {
            autoNewerThanManual -> auto
            external != null -> external
            manual != null -> manual
            else -> auto
        }
    }
}
