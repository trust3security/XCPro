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
import kotlinx.coroutines.flow.first
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
            val storedQnh = qnhPreferencesRepository.qnhHpaFlow.first()
            if (storedQnh != null) {
                applyManualInternal(storedQnh, persist = false)
            } else {
                resetToStandardInternal(persist = false)
            }
        }
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

    private suspend fun applyManualInternal(hpa: Double, persist: Boolean) {
        sensorFusionRepository.setManualQnh(hpa)
        if (persist) {
            qnhPreferencesRepository.setManualQnh(hpa)
        }
        _qnhState.value = QnhValue(
            hpa = hpa,
            source = QnhSource.MANUAL,
            calibratedAtMillis = clock.nowWallMs(),
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
}
