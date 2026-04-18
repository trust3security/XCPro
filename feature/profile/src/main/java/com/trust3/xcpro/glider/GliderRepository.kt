package com.trust3.xcpro.glider

import android.content.Context
import android.content.SharedPreferences
import com.trust3.xcpro.common.glider.ActivePolarSnapshot
import com.trust3.xcpro.common.glider.ActivePolarSource
import com.trust3.xcpro.common.glider.GliderConfig
import com.trust3.xcpro.common.glider.GliderConfigRepository
import com.trust3.xcpro.common.glider.GliderModel
import com.trust3.xcpro.common.glider.ThreePointPolar
import com.trust3.xcpro.common.glider.UserPolarCoefficients
import com.trust3.xcpro.common.glider.defaultClubFallbackGliderModel
import com.trust3.xcpro.common.glider.defaultGliderModels
import com.trust3.xcpro.common.units.UnitsConverter
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
    private val _activePolar = MutableStateFlow(
        ActivePolarSnapshot(
            source = ActivePolarSource.FALLBACK_MODEL,
            selectedModelId = null,
            selectedModelName = null,
            effectiveModelId = fallbackModel.id,
            effectiveModelName = fallbackModel.name,
            isFallbackPolarActive = true,
            hasThreePointPolar = false,
            referenceWeightConfigured = false,
            userCoefficientsConfigured = false,
            iasMinMs = null,
            iasMaxMs = null
        )
    )
    override val activePolar: StateFlow<ActivePolarSnapshot> = _activePolar.asStateFlow()

    private val _config = MutableStateFlow(GliderConfig())
    override val config: StateFlow<GliderConfig> = _config.asStateFlow()
    private var activeProfileId: String = DEFAULT_PROFILE_ID

    init {
        loadActiveProfile()
    }

    override fun listModels(): List<GliderModel> = models

    override fun selectModelById(id: String) {
        val model = models.find { it.id == id }
        _selectedModel.value = model
        refreshDerivedModelState()
        saveActiveProfile()
    }

    override fun updateConfig(update: (GliderConfig) -> GliderConfig) {
        val next = sanitizeConfig(update(_config.value))
        _config.value = next
        refreshDerivedModelState()
        saveActiveProfile()
    }

    override fun setActiveProfileId(profileId: String) {
        val resolved = resolveProfileId(profileId)
        if (activeProfileId == resolved) return
        activeProfileId = resolved
        loadActiveProfile()
    }

    override fun clearProfile(profileId: String) {
        val resolved = resolveProfileId(profileId)
        val editor = prefs.edit()
            .remove(scopedSelectedKey(resolved))
            .remove(scopedConfigKey(resolved))
        if (isLegacyFallbackEligible(resolved)) {
            editor.remove(KEY_SELECTED_ID)
            editor.remove(KEY_CONFIG_JSON)
        }
        editor.apply()
        if (activeProfileId == resolved) {
            _selectedModel.value = null
            _config.value = GliderConfig()
            refreshDerivedModelState()
        }
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

    fun loadProfileSnapshot(profileId: String): GliderProfileSnapshot {
        val persisted = readPersistedState(resolveProfileId(profileId))
        val selected = persisted.selectedId?.let { id -> models.find { it.id == id } }
        val config = persisted.configJson?.let { json ->
            runCatching { loadPersistedConfig(json) }.getOrNull()
        } ?: GliderConfig()
        val effective = resolveEffectiveModel(selected, config)
        val fallbackPolarActive = isFallbackPolarActive(selected, config)
        return GliderProfileSnapshot(
            selectedModelId = selected?.id,
            effectiveModelId = effective.id,
            isFallbackPolarActive = fallbackPolarActive,
            config = config
        )
    }

    fun saveProfileSnapshot(profileId: String, selectedModelId: String?, config: GliderConfig) {
        val resolvedProfileId = resolveProfileId(profileId)
        val sanitizedConfig = sanitizeConfig(config)
        writeScopedState(
            profileId = resolvedProfileId,
            selectedModelId = selectedModelId,
            config = sanitizedConfig
        )
        if (activeProfileId != resolvedProfileId) return
        _selectedModel.value = selectedModelId?.let { id -> models.find { it.id == id } }
        _config.value = sanitizedConfig
        refreshDerivedModelState()
    }

    private fun loadActiveProfile() {
        val persisted = readPersistedState(activeProfileId)
        val id = persisted.selectedId
        val json = persisted.configJson
        if (id != null) {
            _selectedModel.value = models.find { it.id == id }
        } else {
            _selectedModel.value = null
        }
        if (json != null) {
            try {
                _config.value = loadPersistedConfig(json)
            } catch (_: Exception) { /* keep defaults */ }
        } else {
            _config.value = GliderConfig()
        }
        refreshDerivedModelState()
    }

    private fun saveActiveProfile() {
        writeScopedState(
            profileId = activeProfileId,
            selectedModelId = _selectedModel.value?.id,
            config = _config.value
        )
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
        val fallbackPolarActive = isFallbackPolarActive(selectedValue, configValue)
        val effectiveModel = resolveEffectiveModel(selectedValue, configValue)
        _effectiveModel.value = effectiveModel
        _isFallbackPolarActive.value = fallbackPolarActive
        _activePolar.value = buildActivePolarSnapshot(
            selectedModel = selectedValue,
            effectiveModel = effectiveModel,
            config = configValue,
            isFallbackPolarActive = fallbackPolarActive
        )
    }

    private fun buildActivePolarSnapshot(
        selectedModel: GliderModel?,
        effectiveModel: GliderModel,
        config: GliderConfig,
        isFallbackPolarActive: Boolean
    ): ActivePolarSnapshot {
        val source = when {
            config.threePointPolar != null -> ActivePolarSource.MANUAL_THREE_POINT
            isFallbackPolarActive -> ActivePolarSource.FALLBACK_MODEL
            else -> ActivePolarSource.SELECTED_MODEL
        }
        val bounds = GliderSpeedBoundsResolver.resolveIasBoundsMs(effectiveModel, config)
        return ActivePolarSnapshot(
            source = source,
            selectedModelId = selectedModel?.id,
            selectedModelName = selectedModel?.name,
            effectiveModelId = effectiveModel.id,
            effectiveModelName = effectiveModel.name,
            isFallbackPolarActive = isFallbackPolarActive,
            hasThreePointPolar = config.threePointPolar != null,
            referenceWeightConfigured = config.referenceWeightKg != null,
            userCoefficientsConfigured = config.userCoefficients != null,
            iasMinMs = bounds?.minMs,
            iasMaxMs = bounds?.maxMs
        )
    }

    private fun isFallbackPolarActive(
        selectedModel: GliderModel?,
        config: GliderConfig
    ): Boolean {
        val selectedHasUsablePolar =
            selectedModel != null && GliderSpeedBoundsResolver.hasPolar(selectedModel, config)
        return !selectedHasUsablePolar && !GliderSpeedBoundsResolver.hasPolar(null, config)
    }

    private fun resolveEffectiveModel(
        selectedModel: GliderModel?,
        config: GliderConfig
    ): GliderModel {
        val selectedHasUsablePolar =
            selectedModel != null && GliderSpeedBoundsResolver.hasPolar(selectedModel, config)
        return if (selectedHasUsablePolar) {
            selectedModel ?: fallbackModel
        } else {
            fallbackModel
        }
    }

    private fun writeScopedState(
        profileId: String,
        selectedModelId: String?,
        config: GliderConfig
    ) {
        val configJson = gson.toJson(config)
        val editor = prefs.edit()
            .putString(scopedSelectedKey(profileId), selectedModelId)
            .putString(scopedConfigKey(profileId), configJson)
        if (isLegacyFallbackEligible(profileId)) {
            editor.putString(KEY_SELECTED_ID, selectedModelId)
            editor.putString(KEY_CONFIG_JSON, configJson)
        }
        editor.apply()
    }

    private fun readPersistedState(profileId: String): PersistedState {
        val scopedSelectedKey = scopedSelectedKey(profileId)
        val scopedConfigKey = scopedConfigKey(profileId)
        val scopedSelected = prefs.getString(scopedSelectedKey, null)
        val scopedConfig = prefs.getString(scopedConfigKey, null)
        if (scopedSelected != null || scopedConfig != null || hasScopedState(profileId)) {
            return PersistedState(
                selectedId = scopedSelected,
                configJson = scopedConfig
            )
        }

        if (!isLegacyFallbackEligible(profileId)) {
            return PersistedState(selectedId = null, configJson = null)
        }

        val legacySelected = prefs.getString(KEY_SELECTED_ID, null)
        val legacyConfig = prefs.getString(KEY_CONFIG_JSON, null)
        if (legacySelected != null || legacyConfig != null) {
            writeScopedState(
                profileId = profileId,
                selectedModelId = legacySelected,
                config = legacyConfig?.let { json ->
                    runCatching { loadPersistedConfig(json) }.getOrElse { GliderConfig() }
                } ?: GliderConfig()
            )
            return PersistedState(
                selectedId = legacySelected,
                configJson = legacyConfig
            )
        }
        return PersistedState(selectedId = null, configJson = null)
    }

    private fun hasScopedState(profileId: String): Boolean {
        return prefs.contains(scopedSelectedKey(profileId)) || prefs.contains(scopedConfigKey(profileId))
    }

    private fun scopedSelectedKey(profileId: String): String =
        "profile_${profileId}_${KEY_SELECTED_ID}"

    private fun scopedConfigKey(profileId: String): String =
        "profile_${profileId}_${KEY_CONFIG_JSON}"

    private fun resolveProfileId(profileId: String): String =
        profileId.trim().ifBlank { DEFAULT_PROFILE_ID }

    private fun isLegacyFallbackEligible(profileId: String): Boolean {
        return profileId == DEFAULT_PROFILE_ID
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
        private const val DEFAULT_PROFILE_ID = "default-profile"
        private const val KEY_SELECTED_ID = "selected_model_id"
        private const val KEY_CONFIG_JSON = "glider_config_json"
    }

    private data class PersistedState(
        val selectedId: String?,
        val configJson: String?
    )

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

data class GliderProfileSnapshot(
    val selectedModelId: String?,
    val effectiveModelId: String?,
    val isFallbackPolarActive: Boolean,
    val config: GliderConfig
)
