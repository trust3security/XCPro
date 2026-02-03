package com.example.xcpro.glider

import android.content.Context
import android.content.SharedPreferences
import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderConfigRepository
import com.example.xcpro.common.glider.GliderModel
import com.example.xcpro.common.glider.ThreePointPolar
import com.example.xcpro.common.glider.UserPolarCoefficients
import com.example.xcpro.common.glider.defaultGliderModels
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

    private val _selectedModel = MutableStateFlow<GliderModel?>(null)
    override val selectedModel: StateFlow<GliderModel?> = _selectedModel.asStateFlow()

    private val _config = MutableStateFlow(GliderConfig())
    override val config: StateFlow<GliderConfig> = _config.asStateFlow()

    init {
        load()
    }

    override fun listModels(): List<GliderModel> = models

    override fun selectModelById(id: String) {
        val model = models.find { it.id == id }
        _selectedModel.value = model
        save()
    }

    override fun updateConfig(update: (GliderConfig) -> GliderConfig) {
        val next = sanitizeConfig(update(_config.value))
        _config.value = next
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

    fun setIasMinKmh(value: Double?) {
        updateConfig { it.copy(iasMinKmh = value) }
    }

    fun setIasMaxKmh(value: Double?) {
        updateConfig { it.copy(iasMaxKmh = value) }
    }

    private fun load() {
        val id = prefs.getString(KEY_SELECTED_ID, null)
        val json = prefs.getString(KEY_CONFIG_JSON, null)
        if (id != null) {
            _selectedModel.value = models.find { it.id == id }
        }
        if (json != null) {
            try {
                _config.value = sanitizeConfig(gson.fromJson(json, GliderConfig::class.java))
            } catch (_: Exception) { /* keep defaults */ }
        }
    }

    private fun save() {
        prefs.edit()
            .putString(KEY_SELECTED_ID, _selectedModel.value?.id)
            .putString(KEY_CONFIG_JSON, gson.toJson(_config.value))
            .apply()
    }

    private fun sanitizeConfig(config: GliderConfig): GliderConfig {
        val minKmh = config.iasMinKmh?.takeIf { it.isFinite() && it > 0.0 }
        val maxKmh = config.iasMaxKmh?.takeIf { it.isFinite() && it > 0.0 }
        if (config.waterBallastKg > 0.0 && config.hideBallastPill) {
            return config.copy(hideBallastPill = false, iasMinKmh = minKmh, iasMaxKmh = maxKmh)
        }
        return config.copy(iasMinKmh = minKmh, iasMaxKmh = maxKmh)
    }

    companion object {
        private const val KEY_SELECTED_ID = "selected_model_id"
        private const val KEY_CONFIG_JSON = "glider_config_json"
    }
}
