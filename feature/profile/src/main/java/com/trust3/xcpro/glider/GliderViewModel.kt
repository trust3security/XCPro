package com.trust3.xcpro.glider

import com.trust3.xcpro.common.glider.ActivePolarSnapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trust3.xcpro.common.glider.GliderConfig
import com.trust3.xcpro.common.glider.GliderModel
import com.trust3.xcpro.common.glider.ThreePointPolar
import com.trust3.xcpro.common.units.UnitsConverter
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
    val models: List<GliderModel> = emptyList()
)

@HiltViewModel
class GliderViewModel @Inject constructor(
    private val useCase: GliderUseCase,
    private val polarPreviewUseCase: PolarPreviewUseCase
) : ViewModel() {

    private val models = useCase.listModels()

    private val _uiState = MutableStateFlow(GliderUiState(models = models))
    val uiState: StateFlow<GliderUiState> = _uiState.asStateFlow()

    init {
        combine(
            useCase.config,
            useCase.selectedModel,
            useCase.effectiveModel,
            useCase.isFallbackPolarActive,
            useCase.activePolar
        ) { config, selectedModel, effectiveModel, fallbackActive, activePolar ->
            GliderUiState(
                config = config,
                selectedModel = selectedModel,
                effectiveModel = effectiveModel,
                isFallbackPolarActive = fallbackActive,
                activePolar = activePolar,
                models = models
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

    fun setIasMinMs(value: Double?) {
        useCase.setIasMinMs(value?.coerceAtLeast(0.0))
    }

    fun setIasMaxMs(value: Double?) {
        useCase.setIasMaxMs(value?.coerceAtLeast(0.0))
    }

    fun setIasMinKmh(value: Double?) {
        setIasMinMs(value?.coerceAtLeast(0.0)?.let(UnitsConverter::kmhToMs))
    }

    fun setIasMaxKmh(value: Double?) {
        setIasMaxMs(value?.coerceAtLeast(0.0)?.let(UnitsConverter::kmhToMs))
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
