package com.example.xcpro.qnh

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.core.time.Clock
import com.example.xcpro.map.QnhPreferencesRepository
import com.example.xcpro.vario.VarioServiceManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class QnhRepositoryImpl @Inject constructor(
    private val qnhPreferencesRepository: QnhPreferencesRepository,
    private val varioServiceManager: VarioServiceManager,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    private val clock: Clock
) : QnhRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val sensorFusionRepository = varioServiceManager.sensorFusionRepository

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

    init {
        scope.launch {
            hydrateFromActiveProfile()
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
        sensorFusionRepository.setManualQnh(value.hpa)
        qnhPreferencesRepository.clearManualQnh()
        _qnhState.value = value
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
        sensorFusionRepository.setManualQnh(hpa)
        if (persist) {
            qnhPreferencesRepository.setManualQnh(
                qnhHpa = hpa,
                capturedAtWallMs = capturedAt,
                source = source.name
            )
        }
        _qnhState.value = QnhValue(
            hpa = hpa,
            source = source,
            calibratedAtMillis = capturedAt,
            confidence = QnhConfidence.HIGH
        )
        _calibrationState.value = QnhCalibrationState.Idle
    }

    private suspend fun resetToStandardInternal(persist: Boolean) {
        sensorFusionRepository.resetQnhToStandard()
        if (persist) {
            qnhPreferencesRepository.clearManualQnh()
        }
        _qnhState.value = QnhValue(
            hpa = DEFAULT_QNH_HPA,
            source = QnhSource.STANDARD,
            calibratedAtMillis = clock.nowWallMs(),
            confidence = QnhConfidence.LOW
        )
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
}
