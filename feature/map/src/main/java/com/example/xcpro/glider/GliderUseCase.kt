package com.example.xcpro.glider

import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderConfigRepository
import com.example.xcpro.common.glider.GliderModel
import com.example.xcpro.common.glider.ThreePointPolar
import com.example.xcpro.common.glider.UserPolarCoefficients
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class GliderUseCase @Inject constructor(
    private val repository: GliderConfigRepository
) {
    val selectedModel: StateFlow<GliderModel?> = repository.selectedModel
    val config: StateFlow<GliderConfig> = repository.config

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

    fun setIasMinKmh(value: Double?) {
        repository.updateConfig { it.copy(iasMinKmh = value) }
    }

    fun setIasMaxKmh(value: Double?) {
        repository.updateConfig { it.copy(iasMaxKmh = value) }
    }
}
