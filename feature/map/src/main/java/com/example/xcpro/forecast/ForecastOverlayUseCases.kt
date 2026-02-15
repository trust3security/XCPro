package com.example.xcpro.forecast

import com.example.xcpro.core.time.Clock
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow

class ObserveForecastOverlayStateUseCase @Inject constructor(
    private val repository: ForecastOverlayRepository
) {
    operator fun invoke(): Flow<ForecastOverlayUiState> = repository.loadingOverlayState()
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
        val selectedId = availableParameters.firstOrNull { meta ->
            meta.id.value.equals(parameterId.value, ignoreCase = true)
        }?.id
            ?: availableParameters.firstOrNull()?.id
            ?: DEFAULT_FORECAST_PARAMETER_ID
        preferencesRepository.setSelectedParameterId(selectedId)
    }
}

class SetForecastAutoTimeEnabledUseCase @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferencesRepository.setAutoTimeEnabled(enabled)
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

class QueryForecastValueAtPointUseCase @Inject constructor(
    private val repository: ForecastOverlayRepository
) {
    suspend operator fun invoke(
        latitude: Double,
        longitude: Double
    ): ForecastPointQueryResult = repository.queryPointValue(latitude, longitude)
}
