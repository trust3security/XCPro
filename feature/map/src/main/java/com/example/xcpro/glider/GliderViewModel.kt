package com.example.xcpro.glider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderModel
import com.example.xcpro.common.glider.ThreePointPolar
import com.example.xcpro.common.units.UnitsConverter
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
    val models: List<GliderModel> = emptyList()
)

@HiltViewModel
class GliderViewModel @Inject constructor(
    private val useCase: GliderUseCase
) : ViewModel() {

    private val models = useCase.listModels()

    private val _uiState = MutableStateFlow(GliderUiState(models = models))
    val uiState: StateFlow<GliderUiState> = _uiState.asStateFlow()

    init {
        combine(useCase.config, useCase.selectedModel) { config, model ->
            GliderUiState(config = config, selectedModel = model, models = models)
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
}
