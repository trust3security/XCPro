package com.example.xcpro.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MapScreenProfileSessionCoordinator(
    private val scope: CoroutineScope,
    private val dependencies: MapScreenProfileSessionDependencies,
    private val mapStateStore: MapStateStore,
    private val emitMapCommand: (MapCommand) -> Unit
) {
    private var activeProfileId: String = DEFAULT_PROFILE_ID
    private var qnhProfileSwitchJob: Job? = null

    fun persistMapStyle(styleName: String) {
        scope.launch {
            dependencies.mapStyleUseCase.saveStyle(styleName)
        }
    }

    fun setActiveProfileId(profileId: String) {
        val resolved = profileId.trim().ifBlank { DEFAULT_PROFILE_ID }
        if (activeProfileId == resolved) return
        activeProfileId = resolved
        dependencies.mapStyleUseCase.setActiveProfileId(resolved)
        val profileStyle = dependencies.mapStyleUseCase.readProfileStyle(resolved)
        if (mapStateStore.updateMapStyleName(profileStyle)) {
            emitMapCommand(MapCommand.SetStyle(profileStyle))
        }
        dependencies.unitsUseCase.setActiveProfileId(resolved)
        dependencies.orientationSettingsUseCase.setActiveProfileId(resolved)
        dependencies.gliderConfigUseCase.setActiveProfileId(resolved)
        dependencies.variometerLayoutUseCase.setActiveProfileId(resolved)
        dependencies.trailSettingsUseCase.setActiveProfileId(resolved)
        mapStateStore.setTrailSettings(dependencies.trailSettingsUseCase.getSettings())
        qnhProfileSwitchJob?.cancel()
        qnhProfileSwitchJob = scope.launch {
            dependencies.qnhUseCase.setActiveProfileId(resolved)
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
        dependencies.variometerLayoutUseCase.ensureLayout(
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
