package com.example.xcpro.glider

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.gson.Gson

data class ThreePointPolar(
    val lowKmh: Double = 80.0,
    val lowSinkMs: Double = 0.5,
    val midKmh: Double = 120.0,
    val midSinkMs: Double = 0.8,
    val highKmh: Double = 180.0,
    val highSinkMs: Double = 2.0
)

data class UserPolarCoefficients(
    val a: Double? = null,
    val b: Double? = null,
    val c: Double? = null
)

data class GliderConfig(
    val pilotAndGearKg: Double = 90.0,
    val waterBallastKg: Double = 0.0,
    val bugsPercent: Int = 0,
    val referenceWeightKg: Double? = null,
    val threePointPolar: ThreePointPolar? = null,
    val userCoefficients: UserPolarCoefficients? = null
)

class GliderRepository private constructor(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("glider_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val models: List<GliderModel> = defaultGliderModels()

    private val _selectedModel = MutableStateFlow<GliderModel?>(null)
    val selectedModel: StateFlow<GliderModel?> = _selectedModel.asStateFlow()

    private val _config = MutableStateFlow(GliderConfig())
    val config: StateFlow<GliderConfig> = _config.asStateFlow()

    init {
        load()
    }

    fun listModels(): List<GliderModel> = models

    fun selectModelById(id: String) {
        val model = models.find { it.id == id }
        _selectedModel.value = model
        save()
    }

    fun updateConfig(update: (GliderConfig) -> GliderConfig) {
        _config.value = update(_config.value)
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

    private fun load() {
        val id = prefs.getString(KEY_SELECTED_ID, null)
        val json = prefs.getString(KEY_CONFIG_JSON, null)
        if (id != null) {
            _selectedModel.value = models.find { it.id == id }
        }
        if (json != null) {
            try {
                _config.value = gson.fromJson(json, GliderConfig::class.java)
            } catch (_: Exception) { /* keep defaults */ }
        }
    }

    private fun save() {
        prefs.edit()
            .putString(KEY_SELECTED_ID, _selectedModel.value?.id)
            .putString(KEY_CONFIG_JSON, gson.toJson(_config.value))
            .apply()
    }

    companion object {
        private const val KEY_SELECTED_ID = "selected_model_id"
        private const val KEY_CONFIG_JSON = "glider_config_json"

        @Volatile private var INSTANCE: GliderRepository? = null
        fun getInstance(context: Context): GliderRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: GliderRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
