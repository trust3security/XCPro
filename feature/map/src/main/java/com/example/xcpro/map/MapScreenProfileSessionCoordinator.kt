package com.example.xcpro.map

import com.example.xcpro.MapOrientationSettingsRepository
import com.example.xcpro.common.glider.GliderConfigRepository
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.map.trail.MapTrailSettingsUseCase
import com.example.xcpro.qnh.QnhRepository
import com.example.xcpro.variometer.layout.VariometerLayoutUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class MapScreenProfileSessionCoordinator(
    private val scope: CoroutineScope,
    private val mapStyleRepository: MapStyleRepository,
    private val unitsRepository: UnitsRepository,
    private val orientationSettingsRepository: MapOrientationSettingsRepository,
    private val gliderConfigRepository: GliderConfigRepository,
    private val variometerLayoutUseCase: VariometerLayoutUseCase,
    private val trailSettingsUseCase: MapTrailSettingsUseCase,
    private val qnhRepository: QnhRepository,
    private val mapStateStore: MapStateStore,
    private val emitMapCommand: (MapCommand) -> Unit
) {
    private var activeProfileId: String = DEFAULT_PROFILE_ID
    private var qnhProfileSwitchJob: Job? = null

    fun persistMapStyle(styleName: String) {
        scope.launch {
            mapStyleRepository.saveStyle(styleName)
        }
    }

    fun setActiveProfileId(profileId: String) {
        val resolved = profileId.trim().ifBlank { DEFAULT_PROFILE_ID }
        if (activeProfileId == resolved) return
        activeProfileId = resolved
        mapStyleRepository.setActiveProfileId(resolved)
        val profileStyle = mapStyleRepository.readProfileStyle(resolved)
        val styleMutation = mapStateStore.setBaseMapStyle(profileStyle)
        if (styleMutation.effectiveStyleChanged) {
            emitMapCommand(MapCommand.SetStyle(mapStateStore.mapStyleName.value))
        }
        unitsRepository.setActiveProfileId(resolved)
        orientationSettingsRepository.setActiveProfileId(resolved)
        gliderConfigRepository.setActiveProfileId(resolved)
        variometerLayoutUseCase.setActiveProfileId(resolved)
        trailSettingsUseCase.setActiveProfileId(resolved)
        mapStateStore.setTrailSettings(trailSettingsUseCase.getSettings())
        qnhProfileSwitchJob?.cancel()
        qnhProfileSwitchJob = scope.launch {
            qnhRepository.setActiveProfileId(resolved)
        }
    }

    fun ensureVariometerLayout(
        profileId: String,
        screenWidthPx: Float,
        screenHeightPx: Float,
        defaultSizePx: Float,
        minSizePx: Float,
        maxSizePx: Float
    ) {
        val resolved = profileId.trim().ifBlank { DEFAULT_PROFILE_ID }
        if (activeProfileId != resolved) {
            setActiveProfileId(resolved)
        }
        ensureVariometerLayout(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            defaultSizePx = defaultSizePx,
            minSizePx = minSizePx,
            maxSizePx = maxSizePx
        )
    }

    fun ensureVariometerLayout(
        screenWidthPx: Float,
        screenHeightPx: Float,
        defaultSizePx: Float,
        minSizePx: Float,
        maxSizePx: Float
    ) {
        variometerLayoutUseCase.ensureLayout(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            defaultSizePx = defaultSizePx,
            minSizePx = minSizePx,
            maxSizePx = maxSizePx
        )
    }

    private companion object {
        private const val DEFAULT_PROFILE_ID = "default-profile"
    }
}
