package com.trust3.xcpro.qnh

import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.di.QnhRuntimeScope
import com.trust3.xcpro.external.ExternalFlightSettingsReadPort
import com.trust3.xcpro.map.QnhPreferencesRepository
import com.trust3.xcpro.sensors.SensorFusionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Singleton
class QnhRepositoryImpl @Inject constructor(
    private val qnhPreferencesRepository: QnhPreferencesRepository,
    private val sensorFusionRepository: SensorFusionRepository,
    private val externalFlightSettingsReadPort: ExternalFlightSettingsReadPort,
    @QnhRuntimeScope private val scope: CoroutineScope,
    private val clock: Clock
) : QnhRepository {

    private val _qnhState = MutableStateFlow(
        QnhValue(
            hpa = DEFAULT_QNH_HPA,
            source = QnhSource.STANDARD,
            calibratedAtMillis = 0L,
            confidence = QnhConfidence.LOW
        )
    )
    override val qnhState: StateFlow<QnhValue> = _qnhState.asStateFlow()

    private val _calibrationState = MutableStateFlow<QnhCalibrationState>(QnhCalibrationState.Idle)
    override val calibrationState: StateFlow<QnhCalibrationState> = _calibrationState.asStateFlow()
    private var baseQnhState: QnhValue = _qnhState.value
    private var externalOverrideQnhHpa: Double? = null

    init {
        scope.launch {
            hydrateFromActiveProfile()
        }
        scope.launch {
            externalFlightSettingsReadPort.externalFlightSettingsSnapshot.collect { snapshot ->
                applyExternalOverride(snapshot.qnhHpa)
            }
        }
    }

    override suspend fun setActiveProfileId(profileId: String) {
        qnhPreferencesRepository.setActiveProfileId(profileId)
        hydrateFromActiveProfile()
    }

    override suspend fun setManualQnh(hpa: Double) {
        applyManualInternal(hpa, persist = true)
    }

    override suspend fun resetToStandard() {
        resetToStandardInternal(persist = true)
    }

    override suspend fun applyAutoQnh(value: QnhValue) {
        qnhPreferencesRepository.clearManualQnh()
        baseQnhState = value
        syncEffectiveQnhState()
    }

    override fun updateCalibrationState(state: QnhCalibrationState) {
        _calibrationState.value = state
    }

    private suspend fun hydrateFromActiveProfile() {
        val storedManual = qnhPreferencesRepository.readActiveManualQnh()
        if (storedManual != null) {
            applyManualInternal(
                hpa = storedManual.qnhHpa,
                persist = false,
                capturedAtWallMs = storedManual.capturedAtWallMs,
                source = parseSource(storedManual.source)
            )
        } else {
            resetToStandardInternal(persist = false)
        }
    }

    private suspend fun applyManualInternal(hpa: Double, persist: Boolean) {
        applyManualInternal(
            hpa = hpa,
            persist = persist,
            capturedAtWallMs = null,
            source = QnhSource.MANUAL
        )
    }

    private suspend fun applyManualInternal(
        hpa: Double,
        persist: Boolean,
        capturedAtWallMs: Long?,
        source: QnhSource
    ) {
        val capturedAt = capturedAtWallMs ?: clock.nowWallMs()
        if (persist) {
            qnhPreferencesRepository.setManualQnh(
                qnhHpa = hpa,
                capturedAtWallMs = capturedAt,
                source = source.name
            )
        }
        baseQnhState = QnhValue(
            hpa = hpa,
            source = source,
            calibratedAtMillis = capturedAt,
            confidence = QnhConfidence.HIGH
        )
        syncEffectiveQnhState()
        _calibrationState.value = QnhCalibrationState.Idle
    }

    private suspend fun resetToStandardInternal(persist: Boolean) {
        if (persist) {
            qnhPreferencesRepository.clearManualQnh()
        }
        baseQnhState = QnhValue(
            hpa = DEFAULT_QNH_HPA,
            source = QnhSource.STANDARD,
            calibratedAtMillis = clock.nowWallMs(),
            confidence = QnhConfidence.LOW
        )
        syncEffectiveQnhState()
        _calibrationState.value = QnhCalibrationState.Idle
    }

    private companion object {
        private const val DEFAULT_QNH_HPA = 1013.25
    }

    private fun parseSource(raw: String?): QnhSource {
        return runCatching {
            raw?.let(QnhSource::valueOf)
        }.getOrNull() ?: QnhSource.MANUAL
    }

    private suspend fun applyExternalOverride(qnhHpa: Double?) {
        externalOverrideQnhHpa = qnhHpa?.takeIf { it.isFinite() && it > 0.0 }
        syncEffectiveQnhState()
    }

    private suspend fun syncEffectiveQnhState() {
        val externalQnh = externalOverrideQnhHpa
        if (externalQnh != null) {
            sensorFusionRepository.setManualQnh(externalQnh)
            _qnhState.value = QnhValue(
                hpa = externalQnh,
                source = QnhSource.EXTERNAL,
                calibratedAtMillis = clock.nowWallMs(),
                confidence = QnhConfidence.HIGH
            )
            return
        }

        when (baseQnhState.source) {
            QnhSource.STANDARD -> sensorFusionRepository.resetQnhToStandard()
            else -> sensorFusionRepository.setManualQnh(baseQnhState.hpa)
        }
        _qnhState.value = baseQnhState
    }
}
