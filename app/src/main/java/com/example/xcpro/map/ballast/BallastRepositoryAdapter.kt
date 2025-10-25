package com.example.xcpro.map.ballast

import com.example.xcpro.glider.GliderConfig
import com.example.xcpro.glider.GliderModel
import com.example.xcpro.glider.GliderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.max

private const val DEFAULT_MAX_WATER_KG = 200.0

class BallastRepositoryAdapter(
    private val repository: GliderRepository
) {
    val snapshots: Flow<BallastSnapshot> =
        repository.config.combine(repository.selectedModel) { config, model ->
            val maxKg = resolveMaxBallastKg(model, config)
            BallastSnapshot.create(config.waterBallastKg, maxKg)
        }.distinctUntilChanged()

    fun updateWaterBallast(targetKg: Double) {
        repository.updateConfig { config ->
            val maxKg = resolveMaxBallastKg(repository.selectedModel.value, config)
            val clamped = targetKg.coerceIn(0.0, maxKg.takeIf { it > 0.0 } ?: targetKg)
            if (config.waterBallastKg.isCloseTo(clamped)) {
                config
            } else {
                config.copy(waterBallastKg = clamped)
            }
        }
    }

    private fun resolveMaxBallastKg(
        model: GliderModel?,
        config: GliderConfig
    ): Double {
        val fromModel = model?.water?.totalLiters?.toDouble()
        val baseline = fromModel ?: DEFAULT_MAX_WATER_KG
        return max(baseline, config.waterBallastKg)
    }
}
