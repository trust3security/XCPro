package com.trust3.xcpro.weather.wind.domain

import com.trust3.xcpro.weather.wind.model.WindSource
import com.trust3.xcpro.weather.wind.model.WindVector

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
            external != null -> external
            autoNewerThanManual -> auto
            manual != null -> manual
            else -> auto
        }
    }
}
