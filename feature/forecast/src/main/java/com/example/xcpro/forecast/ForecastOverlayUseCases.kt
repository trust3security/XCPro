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

class ToggleForecastPrimaryOverlaySelectionUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository,
    private val catalogPort: ForecastCatalogPort
) {
    suspend operator fun invoke(parameterId: ForecastParameterId) {
        val availableParameters = catalogPort.getParameters()
            .filter(::isPrimaryParameterMeta)
        if (availableParameters.isEmpty()) return

        val requestedId = availableParameters.firstOrNull { meta ->
            matchesParameterId(meta.id, parameterId)
        }?.id ?: return
        preferencesRepository.setSelectedPrimaryParameterId(requestedId)
    }
}

class ToggleSkySightPrimaryOverlaySelectionUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository,
    private val catalogPort: ForecastCatalogPort
) {
    suspend operator fun invoke(parameterId: ForecastParameterId) {
        val availableParameters = catalogPort.getParameters()
            .filter(::isPrimaryParameterMeta)
        if (availableParameters.isEmpty()) return

        val requestedId = availableParameters.firstOrNull { meta ->
            matchesParameterId(meta.id, parameterId)
        }?.id ?: return
        preferencesRepository.setSelectedPrimaryParameterId(requestedId)
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

class SetSkySightSatelliteOverlayEnabledUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferencesRepository.setSkySightSatelliteOverlayEnabled(enabled)
    }
}

class SetSkySightSatelliteImageryEnabledUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferencesRepository.setSkySightSatelliteImageryEnabled(enabled)
    }
}

class SetSkySightSatelliteRadarEnabledUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferencesRepository.setSkySightSatelliteRadarEnabled(enabled)
    }
}

class SetSkySightSatelliteLightningEnabledUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferencesRepository.setSkySightSatelliteLightningEnabled(enabled)
    }
}

class SetSkySightSatelliteAnimateEnabledUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferencesRepository.setSkySightSatelliteAnimateEnabled(enabled)
    }
}

class SetSkySightSatelliteHistoryFramesUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository
) {
    suspend operator fun invoke(frameCount: Int) {
        preferencesRepository.setSkySightSatelliteHistoryFrames(frameCount)
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

private fun matchesParameterId(first: ForecastParameterId, second: ForecastParameterId): Boolean =
    first.value.equals(second.value, ignoreCase = true)
