package com.example.xcpro.qnh

import kotlinx.coroutines.flow.StateFlow

interface QnhRepository {
    val qnhState: StateFlow<QnhValue>
    val calibrationState: StateFlow<QnhCalibrationState>

    suspend fun setActiveProfileId(profileId: String)
    suspend fun setManualQnh(hpa: Double)
    suspend fun resetToStandard()
    suspend fun applyAutoQnh(value: QnhValue)
    fun updateCalibrationState(state: QnhCalibrationState)
}
