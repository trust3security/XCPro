package com.trust3.xcpro.glider

import com.trust3.xcpro.common.glider.ActivePolarSnapshot
import com.trust3.xcpro.common.glider.GliderConfig
import com.trust3.xcpro.common.glider.GliderConfigRepository
import com.trust3.xcpro.common.glider.GliderModel
import com.trust3.xcpro.common.glider.GliderAircraftTypes
import com.trust3.xcpro.common.glider.ThreePointPolar
import com.trust3.xcpro.common.glider.ThreePointPolarDeriver
import com.trust3.xcpro.common.glider.UserPolarCoefficients
import com.trust3.xcpro.profiles.AircraftType
import com.trust3.xcpro.profiles.ProfileUseCase
import com.trust3.xcpro.profiles.UserProfile
import com.trust3.xcpro.profiles.canonicalForPersistence
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class GliderUseCase @Inject constructor(
    private val repository: GliderConfigRepository,
    private val profileUseCase: ProfileUseCase
) {
    val selectedModel: StateFlow<GliderModel?> = repository.selectedModel
    val effectiveModel: StateFlow<GliderModel> = repository.effectiveModel
    val isFallbackPolarActive: StateFlow<Boolean> = repository.isFallbackPolarActive
    val config: StateFlow<GliderConfig> = repository.config
    val activePolar: StateFlow<ActivePolarSnapshot> = repository.activePolar
    val activeProfile: StateFlow<UserProfile?> = profileUseCase.activeProfile

    fun listModels(): List<GliderModel> = repository.listModels()

    fun listModelsFor(aircraftType: AircraftType?): List<GliderModel> {
        val typeKey = aircraftType?.toGliderAircraftTypeKey() ?: return repository.listModels()
        return repository.listModels().filter { model -> model.aircraftType == typeKey }
    }

    fun selectModelById(id: String) {
        val allowedIds = listModelsFor(profileUseCase.activeProfile.value?.aircraftType)
            .mapTo(mutableSetOf()) { it.id }
        if (allowedIds.isNotEmpty() && id !in allowedIds) return
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

    fun displayThreePointPolar(config: GliderConfig, effectiveModel: GliderModel?): ThreePointPolar =
        config.threePointPolar ?: ThreePointPolarDeriver.fromModel(effectiveModel) ?: ThreePointPolar()

    private fun AircraftType.toGliderAircraftTypeKey(): String = when (canonicalForPersistence()) {
        AircraftType.PARAGLIDER -> GliderAircraftTypes.PARAGLIDER
        AircraftType.HANG_GLIDER -> GliderAircraftTypes.HANG_GLIDER
        AircraftType.SAILPLANE,
        AircraftType.GLIDER -> GliderAircraftTypes.SAILPLANE
    }
}
