package com.trust3.xcpro.glider

import com.trust3.xcpro.common.glider.ActivePolarSnapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trust3.xcpro.common.glider.GliderConfig
import com.trust3.xcpro.common.glider.GliderModel
import com.trust3.xcpro.common.glider.ThreePointPolar
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class GliderUiState(
    val config: GliderConfig = GliderConfig(),
    val selectedModel: GliderModel? = null,
    val effectiveModel: GliderModel? = null,
    val isFallbackPolarActive: Boolean = false,
    val activePolar: ActivePolarSnapshot? = null,
    val models: List<GliderModel> = emptyList(),
    val displayThreePointPolar: ThreePointPolar = ThreePointPolar()
)

private data class GliderDomainState(
    val config: GliderConfig,
    val selectedModel: GliderModel?,
    val effectiveModel: GliderModel,
    val isFallbackPolarActive: Boolean,
    val activePolar: ActivePolarSnapshot
)

private data class GliderBaseDomainState(
    val config: GliderConfig,
    val selectedModel: GliderModel?,
    val effectiveModel: GliderModel,
    val isFallbackPolarActive: Boolean
)

@HiltViewModel
class GliderViewModel @Inject constructor(
    private val useCase: GliderUseCase,
    private val polarPreviewUseCase: PolarPreviewUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GliderUiState(models = useCase.listModels()))
    val uiState: StateFlow<GliderUiState> = _uiState.asStateFlow()

    init {
        val baseDomainState = combine(
            useCase.config,
            useCase.selectedModel,
            useCase.effectiveModel,
            useCase.isFallbackPolarActive
        ) { config, selectedModel, effectiveModel, fallbackActive ->
            GliderBaseDomainState(
                config = config,
                selectedModel = selectedModel,
                effectiveModel = effectiveModel,
                isFallbackPolarActive = fallbackActive
            )
        }

        val domainState = combine(baseDomainState, useCase.activePolar) { base, activePolar ->
            GliderDomainState(
                config = base.config,
                selectedModel = base.selectedModel,
                effectiveModel = base.effectiveModel,
                isFallbackPolarActive = base.isFallbackPolarActive,
                activePolar = activePolar
            )
        }

        combine(domainState, useCase.activeProfile) { domain, activeProfile ->
            val models = useCase.listModelsFor(activeProfile?.aircraftType)
            val visibleSelectedModel = domain.selectedModel?.takeIf { selected ->
                models.any { model -> model.id == selected.id }
            }
            GliderUiState(
                config = domain.config,
                selectedModel = visibleSelectedModel,
                effectiveModel = domain.effectiveModel,
                isFallbackPolarActive = domain.isFallbackPolarActive,
                activePolar = domain.activePolar,
                models = models,
                displayThreePointPolar = useCase.displayThreePointPolar(domain.config, domain.effectiveModel)
            )
        }.onEach { state ->
            _uiState.value = state
        }.launchIn(viewModelScope)
    }

    fun selectModel(id: String) {
        useCase.selectModelById(id)
    }

    fun setPilotAndGearKg(value: Double) {
        useCase.updateConfig { it.copy(pilotAndGearKg = value.coerceAtLeast(0.0)) }
    }

    fun setWaterBallastKg(value: Double) {
        useCase.updateConfig { it.copy(waterBallastKg = value.coerceAtLeast(0.0)) }
    }

    fun setBugsPercent(value: Int) {
        useCase.updateConfig { it.copy(bugsPercent = value.coerceIn(0, 50)) }
    }

    fun setBallastDrainMinutes(value: Double?) {
        val sanitized = value?.coerceIn(0.5, 60.0)
        useCase.updateConfig { it.copy(ballastDrainMinutes = sanitized ?: 0.0) }
    }

    fun setReferenceWeightKg(weight: Double?) {
        useCase.setReferenceWeightKg(weight)
    }

    fun setHideBallastPill(enabled: Boolean) {
        useCase.updateConfig { it.copy(hideBallastPill = enabled) }
    }

    fun setThreePointPolar(polar: ThreePointPolar) {
        useCase.setThreePointPolar(polar)
    }

    fun previewAtSpeedKmh(activePolar: ActivePolarSnapshot?, speedKmh: Float): PolarPreviewState =
        polarPreviewUseCase.resolve(activePolar = activePolar, speedKmh = speedKmh.toDouble())
}
