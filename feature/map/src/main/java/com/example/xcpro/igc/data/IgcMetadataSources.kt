package com.example.xcpro.igc.data

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import com.example.xcpro.igc.domain.IgcGpsAltitudeDatum
import com.example.xcpro.igc.domain.IgcPressureAltitudeDatum
import com.example.xcpro.igc.domain.IgcProfileMetadata
import com.example.xcpro.igc.domain.IgcProfileMetadataSource
import com.example.xcpro.igc.domain.IgcRecorderMetadata
import com.example.xcpro.igc.domain.IgcRecorderMetadataSource
import com.example.xcpro.igc.domain.IgcSecuritySignatureProfile
import com.example.xcpro.igc.domain.IgcTaskDeclarationSnapshot
import com.example.xcpro.igc.domain.IgcTaskDeclarationStartSnapshot
import com.example.xcpro.igc.domain.IgcTaskDeclarationSource
import com.example.xcpro.igc.domain.IgcTaskDeclarationWaypoint
import com.example.xcpro.profiles.ProfileUseCase
import com.example.xcpro.tasks.TaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileUseCaseIgcProfileMetadataSource @Inject constructor(
    private val profileUseCase: ProfileUseCase
) : IgcProfileMetadataSource {
    override fun activeProfileMetadata(): IgcProfileMetadata? {
        val profile = profileUseCase.activeProfile.value ?: return null
        return IgcProfileMetadata(
            pilotName = profile.name,
            crew2 = null,
            gliderType = profile.aircraftModel ?: profile.aircraftType.displayName,
            gliderId = profile.description
        )
    }
}

@Singleton
class TaskRepositoryIgcTaskDeclarationSource @Inject constructor(
    private val taskRepository: TaskRepository
) : IgcTaskDeclarationSource {
    override fun snapshotForStart(
        sessionId: Long,
        capturedAtUtcMs: Long
    ): IgcTaskDeclarationStartSnapshot {
        val taskState = taskRepository.state.value
        val task = taskState.task
        if (task.waypoints.isEmpty()) {
            return IgcTaskDeclarationStartSnapshot.Absent
        }

        var hasInvalidCoordinate = false
        val waypoints = task.waypoints.mapNotNull { waypoint ->
            val latitude = waypoint.lat
            val longitude = waypoint.lon
            if (!latitude.isFinite() || !longitude.isFinite()) {
                hasInvalidCoordinate = true
                return@mapNotNull null
            }
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
                hasInvalidCoordinate = true
                return@mapNotNull null
            }
            IgcTaskDeclarationWaypoint(
                name = waypoint.title,
                latitudeDegrees = latitude,
                longitudeDegrees = longitude
            )
        }
        if (hasInvalidCoordinate) {
            return IgcTaskDeclarationStartSnapshot.Invalid(
                reason = "WAYPOINT_COORDINATE_INVALID"
            )
        }
        if (waypoints.size < 2) {
            return IgcTaskDeclarationStartSnapshot.Invalid(
                reason = "WAYPOINT_COUNT_LT_2"
            )
        }
        return IgcTaskDeclarationStartSnapshot.Available(
            snapshot = IgcTaskDeclarationSnapshot(
                taskId = task.id.ifBlank { "TASK-$sessionId" },
                capturedAtUtcMs = capturedAtUtcMs,
                waypoints = waypoints
            )
        )
    }
}

@Singleton
class AndroidIgcRecorderMetadataSource @Inject constructor(
    @ApplicationContext private val appContext: Context
) : IgcRecorderMetadataSource {

    override fun recorderMetadata(): IgcRecorderMetadata {
        val hasPressureSensor = hasPressureSensor()
        return IgcRecorderMetadata(
            manufacturerId = "XCS",
            recorderType = "XCPro,SignedMobile",
            firmwareVersion = readVersionName(),
            hardwareVersion = "${Build.MODEL} / Android ${Build.VERSION.RELEASE}",
            gpsReceiver = "NKN",
            pressureSensor = if (hasPressureSensor) "ANDROID_BARO" else "NKN",
            securityStatus = "SIGNED",
            securitySignatureProfile = IgcSecuritySignatureProfile.XCS,
            gpsAltitudeDatum = IgcGpsAltitudeDatum.ELL,
            pressureAltitudeDatum = if (hasPressureSensor) {
                IgcPressureAltitudeDatum.ISA
            } else {
                IgcPressureAltitudeDatum.NIL
            }
        )
    }

    private fun hasPressureSensor(): Boolean {
        val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        return sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
    }

    @Suppress("DEPRECATION")
    private fun readVersionName(): String? {
        val packageManager = appContext.packageManager ?: return null
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    appContext.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                packageManager.getPackageInfo(appContext.packageName, 0)
            }
            info.versionName
        }.getOrNull()
    }
}
