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

class SelectForecastPrimaryParameterUseCase @Inject constructor(
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
