package com.example.xcpro.glider

import android.content.Context
import android.content.SharedPreferences
import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderConfigRepository
import com.example.xcpro.common.glider.GliderModel
import com.example.xcpro.common.glider.ThreePointPolar
import com.example.xcpro.common.glider.UserPolarCoefficients
import com.example.xcpro.common.glider.defaultClubFallbackGliderModel
import com.example.xcpro.common.glider.defaultGliderModels
import com.example.xcpro.common.units.UnitsConverter
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class GliderRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : GliderConfigRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("glider_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val models: List<GliderModel> = defaultGliderModels()
    private val fallbackModel: GliderModel = defaultClubFallbackGliderModel()

    private val _selectedModel = MutableStateFlow<GliderModel?>(null)
    override val selectedModel: StateFlow<GliderModel?> = _selectedModel.asStateFlow()
    private val _effectiveModel = MutableStateFlow(fallbackModel)
    override val effectiveModel: StateFlow<GliderModel> = _effectiveModel.asStateFlow()
    private val _isFallbackPolarActive = MutableStateFlow(true)
    override val isFallbackPolarActive: StateFlow<Boolean> = _isFallbackPolarActive.asStateFlow()

    private val _config = MutableStateFlow(GliderConfig())
    override val config: StateFlow<GliderConfig> = _config.asStateFlow()

    init {
        load()
    }

    override fun listModels(): List<GliderModel> = models

    override fun selectModelById(id: String) {
        val model = models.find { it.id == id }
        _selectedModel.value = model
        refreshDerivedModelState()
        save()
    }

    override fun updateConfig(update: (GliderConfig) -> GliderConfig) {
        val next = sanitizeConfig(update(_config.value))
        _config.value = next
        refreshDerivedModelState()
        save()
    }

    fun setThreePointPolar(p: ThreePointPolar?) {
        updateConfig { it.copy(threePointPolar = p) }
    }

    fun setReferenceWeightKg(weight: Double?) {
        updateConfig { it.copy(referenceWeightKg = weight) }
    }

    fun setUserCoefficients(coeff: UserPolarCoefficients?) {
        updateConfig { it.copy(userCoefficients = coeff) }
    }

    fun setIasMinMs(value: Double?) {
        updateConfig { it.copy(iasMinMs = value) }
    }

    fun setIasMaxMs(value: Double?) {
        updateConfig { it.copy(iasMaxMs = value) }
    }

    fun setIasMinKmh(value: Double?) {
        setIasMinMs(value?.let(UnitsConverter::kmhToMs))
    }

    fun setIasMaxKmh(value: Double?) {
        setIasMaxMs(value?.let(UnitsConverter::kmhToMs))
    }

    private fun load() {
        val id = prefs.getString(KEY_SELECTED_ID, null)
        val json = prefs.getString(KEY_CONFIG_JSON, null)
        if (id != null) {
            _selectedModel.value = models.find { it.id == id }
        }
        if (json != null) {
            try {
                _config.value = loadPersistedConfig(json)
            } catch (_: Exception) { /* keep defaults */ }
        }
        refreshDerivedModelState()
    }

    private fun save() {
        prefs.edit()
            .putString(KEY_SELECTED_ID, _selectedModel.value?.id)
            .putString(KEY_CONFIG_JSON, gson.toJson(_config.value))
            .apply()
    }

    private fun sanitizeConfig(config: GliderConfig): GliderConfig {
        val minMs = config.iasMinMs?.takeIf { it.isFinite() && it > 0.0 }
        val maxMs = config.iasMaxMs?.takeIf { it.isFinite() && it > 0.0 }
        if (config.waterBallastKg > 0.0 && config.hideBallastPill) {
            return config.copy(hideBallastPill = false, iasMinMs = minMs, iasMaxMs = maxMs)
        }
        return config.copy(iasMinMs = minMs, iasMaxMs = maxMs)
    }

    private fun refreshDerivedModelState() {
        val configValue = _config.value
        val selectedValue = _selectedModel.value
        val selectedHasUsablePolar = selectedValue != null && GliderSpeedBoundsResolver.hasPolar(selectedValue, configValue)
        val fallbackPolarActive = !selectedHasUsablePolar && !GliderSpeedBoundsResolver.hasPolar(null, configValue)
        _effectiveModel.value = if (selectedHasUsablePolar) {
            selectedValue ?: fallbackModel
        } else {
            fallbackModel
        }
        _isFallbackPolarActive.value = fallbackPolarActive
    }

    private fun loadPersistedConfig(json: String): GliderConfig {
        val defaults = GliderConfig()
        val persisted = gson.fromJson(json, GliderConfigPersistence::class.java) ?: return defaults
        val threePointPolar = restoreThreePointPolar(persisted.threePointPolar)
        return sanitizeConfig(
            GliderConfig(
                pilotAndGearKg = persisted.pilotAndGearKg ?: defaults.pilotAndGearKg,
                waterBallastKg = persisted.waterBallastKg ?: defaults.waterBallastKg,
                bugsPercent = persisted.bugsPercent ?: defaults.bugsPercent,
                referenceWeightKg = persisted.referenceWeightKg,
                iasMinMs = restoreSpeedMs(persisted.iasMinMs, persisted.iasMinKmh),
                iasMaxMs = restoreSpeedMs(persisted.iasMaxMs, persisted.iasMaxKmh),
                threePointPolar = threePointPolar,
                userCoefficients = persisted.userCoefficients,
                ballastDrainMinutes = persisted.ballastDrainMinutes ?: defaults.ballastDrainMinutes,
                hideBallastPill = persisted.hideBallastPill ?: defaults.hideBallastPill
            )
        )
    }

    private fun restoreSpeedMs(valueMs: Double?, legacyKmh: Double?): Double? =
        valueMs ?: legacyKmh?.let(UnitsConverter::kmhToMs)

    private fun restoreThreePointPolar(persisted: ThreePointPolarPersistence?): ThreePointPolar? {
        if (persisted == null) return null
        val hasAny = listOf(
            persisted.lowMs,
            persisted.lowSinkMs,
            persisted.midMs,
            persisted.midSinkMs,
            persisted.highMs,
            persisted.highSinkMs,
            persisted.lowKmh,
            persisted.midKmh,
            persisted.highKmh
        ).any { it != null }
        if (!hasAny) return null

        val defaults = ThreePointPolar()
        val lowMs = restoreSpeedMs(persisted.lowMs, persisted.lowKmh) ?: defaults.lowMs
        val midMs = restoreSpeedMs(persisted.midMs, persisted.midKmh) ?: defaults.midMs
        val highMs = restoreSpeedMs(persisted.highMs, persisted.highKmh) ?: defaults.highMs

        return ThreePointPolar(
            lowMs = lowMs,
            lowSinkMs = persisted.lowSinkMs ?: defaults.lowSinkMs,
            midMs = midMs,
            midSinkMs = persisted.midSinkMs ?: defaults.midSinkMs,
            highMs = highMs,
            highSinkMs = persisted.highSinkMs ?: defaults.highSinkMs
        )
    }

    companion object {
        private const val KEY_SELECTED_ID = "selected_model_id"
        private const val KEY_CONFIG_JSON = "glider_config_json"
    }

    private data class GliderConfigPersistence(
        val pilotAndGearKg: Double? = null,
        val waterBallastKg: Double? = null,
        val bugsPercent: Int? = null,
        val referenceWeightKg: Double? = null,
        val iasMinMs: Double? = null,
        val iasMaxMs: Double? = null,
        val iasMinKmh: Double? = null,
        val iasMaxKmh: Double? = null,
        val threePointPolar: ThreePointPolarPersistence? = null,
        val userCoefficients: UserPolarCoefficients? = null,
        val ballastDrainMinutes: Double? = null,
        val hideBallastPill: Boolean? = null
    )

    private data class ThreePointPolarPersistence(
        val lowMs: Double? = null,
        val lowSinkMs: Double? = null,
        val midMs: Double? = null,
        val midSinkMs: Double? = null,
        val highMs: Double? = null,
        val highSinkMs: Double? = null,
        val lowKmh: Double? = null,
        val midKmh: Double? = null,
        val highKmh: Double? = null
    )
}
