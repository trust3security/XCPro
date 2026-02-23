package com.example.xcpro.forecast

import com.example.xcpro.core.time.Clock
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow

class ObserveForecastOverlayStateUseCase @Inject constructor(
    private val repository: ForecastOverlayRepository
) {
    operator fun invoke(): Flow<ForecastOverlayUiState> = repository.overlayState
}

class SetForecastEnabledUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferencesRepository.setOverlayEnabled(enabled)
    }
}

class SelectForecastParameterUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository,
    private val catalogPort: ForecastCatalogPort
) {
    suspend operator fun invoke(parameterId: ForecastParameterId) {
        val availableParameters = catalogPort.getParameters()
            .filter(::isPrimaryParameterMeta)
        val selectedId = availableParameters.firstOrNull { meta ->
            meta.id.value.equals(parameterId.value, ignoreCase = true)
        }?.id
            ?: availableParameters.firstOrNull()?.id
            ?: DEFAULT_FORECAST_PARAMETER_ID
        preferencesRepository.setSelectedPrimaryParameterId(selectedId)
    }
}

class ToggleForecastPrimaryOverlaySelectionUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository,
    private val catalogPort: ForecastCatalogPort
) {
    suspend operator fun invoke(parameterId: ForecastParameterId) {
        val availableParameters = catalogPort.getParameters()
            .filter(::isPrimaryParameterMeta)
        if (availableParameters.isEmpty()) return

        val preferences = preferencesRepository.currentPreferences()
        val selectedPrimaryId = resolveSelectedParameterId(
            requested = preferences.selectedPrimaryParameterId,
            availableParameters = availableParameters,
            fallback = DEFAULT_FORECAST_PARAMETER_ID
        ) ?: availableParameters.first().id
        val secondaryCandidates = availableParameters.filterNot { meta ->
            matchesParameterId(meta.id, selectedPrimaryId)
        }
        val selectedSecondaryId = resolveSelectedParameterId(
            requested = preferences.selectedSecondaryPrimaryParameterId,
            availableParameters = secondaryCandidates,
            fallback = DEFAULT_FORECAST_SECONDARY_PRIMARY_PARAMETER_ID
        )
        val secondaryEnabled = preferences.secondaryPrimaryOverlayEnabled &&
            selectedSecondaryId != null &&
            secondaryCandidates.isNotEmpty()
        val activeSecondaryId = if (secondaryEnabled) selectedSecondaryId else null

        val requestedId = availableParameters.firstOrNull { meta ->
            matchesParameterId(meta.id, parameterId)
        }?.id ?: return

        val isPrimary = matchesParameterId(requestedId, selectedPrimaryId)
        val isSecondary = activeSecondaryId != null &&
            matchesParameterId(requestedId, activeSecondaryId)

        when {
            isPrimary && isSecondary -> Unit
            isPrimary && activeSecondaryId != null -> {
                preferencesRepository.setSelectedPrimaryParameterId(activeSecondaryId)
                preferencesRepository.setSelectedSecondaryPrimaryParameterId(selectedPrimaryId)
                preferencesRepository.setSecondaryPrimaryOverlayEnabled(false)
            }
            isPrimary -> Unit
            isSecondary -> {
                preferencesRepository.setSecondaryPrimaryOverlayEnabled(false)
            }
            !secondaryEnabled -> {
                preferencesRepository.setSelectedSecondaryPrimaryParameterId(requestedId)
                preferencesRepository.setSecondaryPrimaryOverlayEnabled(true)
            }
            else -> Unit
        }
    }
}

class ToggleSkySightPrimaryOverlaySelectionUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository,
    private val catalogPort: ForecastCatalogPort
) {
    suspend operator fun invoke(parameterId: ForecastParameterId) {
        if (!isSkySightPrimaryParameterId(parameterId)) return

        val availableParameters = catalogPort.getParameters()
            .filter(::isPrimaryParameterMeta)
        if (availableParameters.isEmpty()) return

        val availableSkySightIds = SKY_SIGHT_PRIMARY_PARAMETER_IDS.mapNotNull { skySightId ->
            availableParameters.firstOrNull { meta ->
                matchesParameterId(meta.id, skySightId)
            }?.id
        }
        val requestedId = availableSkySightIds.firstOrNull { availableId ->
            matchesParameterId(availableId, parameterId)
        } ?: return
        val otherId = availableSkySightIds.firstOrNull { availableId ->
            !matchesParameterId(availableId, requestedId)
        }

        val preferences = preferencesRepository.currentPreferences()
        val selectedPrimaryId = resolveSelectedParameterId(
            requested = preferences.selectedPrimaryParameterId,
            availableParameters = availableParameters,
            fallback = DEFAULT_FORECAST_PARAMETER_ID
        ) ?: availableParameters.first().id
        val secondaryCandidates = availableParameters.filterNot { meta ->
            matchesParameterId(meta.id, selectedPrimaryId)
        }
        val selectedSecondaryId = resolveSelectedParameterId(
            requested = preferences.selectedSecondaryPrimaryParameterId,
            availableParameters = secondaryCandidates,
            fallback = DEFAULT_FORECAST_SECONDARY_PRIMARY_PARAMETER_ID
        )
        val secondaryEnabled = preferences.secondaryPrimaryOverlayEnabled &&
            selectedSecondaryId != null &&
            secondaryCandidates.isNotEmpty()
        val activeSecondaryId = if (secondaryEnabled) selectedSecondaryId else null

        val requestedIsPrimary = matchesParameterId(selectedPrimaryId, requestedId)
        val requestedIsSecondary = activeSecondaryId != null &&
            matchesParameterId(activeSecondaryId, requestedId)
        val requestedActive = requestedIsPrimary || requestedIsSecondary
        val otherActive = otherId != null && (
            matchesParameterId(selectedPrimaryId, otherId) ||
                (activeSecondaryId != null && matchesParameterId(activeSecondaryId, otherId))
            )

        when {
            requestedActive && otherActive -> {
                val promotedPrimaryId = requireNotNull(otherId)
                preferencesRepository.setSelectedPrimaryParameterId(promotedPrimaryId)
                preferencesRepository.setSelectedSecondaryPrimaryParameterId(requestedId)
                preferencesRepository.setSecondaryPrimaryOverlayEnabled(false)
            }

            requestedIsSecondary -> {
                preferencesRepository.setSecondaryPrimaryOverlayEnabled(false)
            }

            requestedActive -> Unit

            otherActive -> {
                val keptPrimaryId = requireNotNull(otherId)
                preferencesRepository.setSelectedPrimaryParameterId(keptPrimaryId)
                preferencesRepository.setSelectedSecondaryPrimaryParameterId(requestedId)
                preferencesRepository.setSecondaryPrimaryOverlayEnabled(true)
            }

            else -> {
                preferencesRepository.setSelectedPrimaryParameterId(requestedId)
                preferencesRepository.setSecondaryPrimaryOverlayEnabled(false)
            }
        }
    }
}

class SetForecastSecondaryPrimaryOverlayEnabledUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferencesRepository.setSecondaryPrimaryOverlayEnabled(enabled)
    }
}

class SelectForecastSecondaryPrimaryParameterUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository,
    private val catalogPort: ForecastCatalogPort
) {
    suspend operator fun invoke(parameterId: ForecastParameterId) {
        val selectedPrimaryId = preferencesRepository.currentPreferences().selectedPrimaryParameterId
        val availableParameters = catalogPort.getParameters()
            .filter(::isPrimaryParameterMeta)
            .filterNot { meta ->
                meta.id.value.equals(selectedPrimaryId.value, ignoreCase = true)
            }
        val selectedId = availableParameters.firstOrNull { meta ->
            meta.id.value.equals(parameterId.value, ignoreCase = true)
        }?.id
            ?: availableParameters.firstOrNull()?.id
            ?: DEFAULT_FORECAST_SECONDARY_PRIMARY_PARAMETER_ID
        preferencesRepository.setSelectedSecondaryPrimaryParameterId(selectedId)
    }
}

class SetForecastWindOverlayEnabledUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferencesRepository.setWindOverlayEnabled(enabled)
    }
}

class SelectForecastWindParameterUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository,
    private val catalogPort: ForecastCatalogPort
) {
    suspend operator fun invoke(parameterId: ForecastParameterId) {
        val windParameters = catalogPort.getParameters()
            .filter(::isWindParameterMeta)
        val selectedId = windParameters.firstOrNull { meta ->
            meta.id.value.equals(parameterId.value, ignoreCase = true)
        }?.id
            ?: windParameters.firstOrNull()?.id
            ?: DEFAULT_FORECAST_WIND_PARAMETER_ID
        preferencesRepository.setSelectedWindParameterId(selectedId)
    }
}

class SetForecastAutoTimeEnabledUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferencesRepository.setAutoTimeEnabled(enabled)
    }
}

class SetForecastFollowTimeOffsetUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository
) {
    suspend operator fun invoke(offsetMinutes: Int) {
        preferencesRepository.setFollowTimeOffsetMinutes(offsetMinutes)
    }
}

class SetForecastTimeUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository,
    private val catalogPort: ForecastCatalogPort,
    private val clock: Clock
) {
    suspend operator fun invoke(timeUtcMs: Long) {
        val regionCode = preferencesRepository.currentPreferences().selectedRegion
        val slots = catalogPort.getTimeSlots(
            nowUtcMs = clock.nowWallMs(),
            regionCode = regionCode
        )
        if (slots.isEmpty()) {
            preferencesRepository.setAutoTimeEnabled(false)
            preferencesRepository.setSelectedTimeUtcMs(timeUtcMs)
            return
        }
        val clampedTime = slots.minByOrNull { slot ->
            abs(slot.validTimeUtcMs - timeUtcMs)
        }?.validTimeUtcMs ?: slots.first().validTimeUtcMs
        preferencesRepository.setAutoTimeEnabled(false)
        preferencesRepository.setSelectedTimeUtcMs(clampedTime)
    }
}

class SetForecastOpacityUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository
) {
    suspend operator fun invoke(opacity: Float) {
        preferencesRepository.setOpacity(opacity)
    }
}

class SetForecastWindOverlayScaleUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository
) {
    suspend operator fun invoke(scale: Float) {
        preferencesRepository.setWindOverlayScale(scale)
    }
}

class SetForecastWindDisplayModeUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository
) {
    suspend operator fun invoke(mode: ForecastWindDisplayMode) {
        preferencesRepository.setWindDisplayMode(mode)
    }
}

class QueryForecastValueAtPointUseCase @Inject constructor(
    private val repository: ForecastOverlayRepository
) {
    suspend operator fun invoke(
        latitude: Double,
        longitude: Double
    ): ForecastPointQueryResult = repository.queryPointValue(latitude, longitude)
}

private fun isWindParameterMeta(meta: ForecastParameterMeta): Boolean =
    isForecastWindCategory(meta.category) || isForecastWindParameterId(meta.id)

private fun isPrimaryParameterMeta(meta: ForecastParameterMeta): Boolean =
    !isWindParameterMeta(meta)

private fun isSkySightPrimaryParameterId(parameterId: ForecastParameterId): Boolean =
    SKY_SIGHT_PRIMARY_PARAMETER_IDS.any { skySightId ->
        matchesParameterId(skySightId, parameterId)
    }

private fun matchesParameterId(first: ForecastParameterId, second: ForecastParameterId): Boolean =
    first.value.equals(second.value, ignoreCase = true)

private fun resolveSelectedParameterId(
    requested: ForecastParameterId,
    availableParameters: List<ForecastParameterMeta>,
    fallback: ForecastParameterId
): ForecastParameterId? {
    if (availableParameters.isEmpty()) return null
    val selected = availableParameters.firstOrNull { meta ->
        matchesParameterId(meta.id, requested)
    }?.id
    if (selected != null) return selected
    return availableParameters.firstOrNull { meta ->
        matchesParameterId(meta.id, fallback)
    }?.id ?: availableParameters.first().id
}

private val SKY_SIGHT_PRIMARY_PARAMETER_IDS = listOf(
    ForecastParameterId("dwcrit"),
    ForecastParameterId("wblmaxmin")
)
