package com.trust3.xcpro.common.glider

import kotlinx.coroutines.flow.StateFlow

interface GliderConfigRepository {
    val selectedModel: StateFlow<GliderModel?>
    val effectiveModel: StateFlow<GliderModel>
    val isFallbackPolarActive: StateFlow<Boolean>
    val config: StateFlow<GliderConfig>
    val activePolar: StateFlow<ActivePolarSnapshot>

    fun listModels(): List<GliderModel>
    fun selectModelById(id: String)
    fun updateConfig(update: (GliderConfig) -> GliderConfig)

    fun setActiveProfileId(profileId: String) = Unit

    fun clearProfile(profileId: String) = Unit
}
