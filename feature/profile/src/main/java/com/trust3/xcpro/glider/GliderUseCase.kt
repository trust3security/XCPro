package com.trust3.xcpro.glider

import com.trust3.xcpro.common.glider.ActivePolarSnapshot
import com.trust3.xcpro.common.glider.GliderConfig
import com.trust3.xcpro.common.glider.GliderConfigRepository
import com.trust3.xcpro.common.glider.GliderModel
import com.trust3.xcpro.common.glider.ThreePointPolar
import com.trust3.xcpro.common.glider.UserPolarCoefficients
import com.trust3.xcpro.common.units.UnitsConverter
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class GliderUseCase @Inject constructor(
    private val repository: GliderConfigRepository
) {
    val selectedModel: StateFlow<GliderModel?> = repository.selectedModel
    val effectiveModel: StateFlow<GliderModel> = repository.effectiveModel
    val isFallbackPolarActive: StateFlow<Boolean> = repository.isFallbackPolarActive
    val config: StateFlow<GliderConfig> = repository.config
    val activePolar: StateFlow<ActivePolarSnapshot> = repository.activePolar

    fun listModels(): List<GliderModel> = repository.listModels()

    fun selectModelById(id: String) {
        repository.selectModelById(id)
    }

    fun updateConfig(update: (GliderConfig) -> GliderConfig) {
        repository.updateConfig(update)
    }

    fun setThreePointPolar(polar: ThreePointPolar?) {
        repository.updateConfig { it.copy(threePointPolar = polar) }
    }

    fun setReferenceWeightKg(weight: Double?) {
        repository.updateConfig { it.copy(referenceWeightKg = weight) }
    }

    fun setUserCoefficients(coefficients: UserPolarCoefficients?) {
        repository.updateConfig { it.copy(userCoefficients = coefficients) }
    }

    fun setIasMinMs(value: Double?) {
        repository.updateConfig { it.copy(iasMinMs = value) }
    }

    fun setIasMaxMs(value: Double?) {
        repository.updateConfig { it.copy(iasMaxMs = value) }
    }

    fun setIasMinKmh(value: Double?) {
        setIasMinMs(value?.let(UnitsConverter::kmhToMs))
    }

    fun setIasMaxKmh(value: Double?) {
        setIasMaxMs(value?.let(UnitsConverter::kmhToMs))
    }
}
