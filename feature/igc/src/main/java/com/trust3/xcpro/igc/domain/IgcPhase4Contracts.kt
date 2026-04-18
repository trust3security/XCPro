package com.trust3.xcpro.igc.domain

import java.time.LocalDate

enum class IgcGpsAltitudeDatum(val value: String) {
    ELL("ELL"),
    GEO("GEO"),
    NKN("NKN"),
    NIL("NIL")
}

enum class IgcPressureAltitudeDatum(val value: String) {
    ISA("ISA"),
    MSL("MSL"),
    NKN("NKN"),
    NIL("NIL")
}

enum class IgcSecuritySignatureProfile {
    NONE,
    XCS
}

data class IgcProfileMetadata(
    val pilotName: String?,
    val crew2: String?,
    val gliderType: String?,
    val gliderId: String?
)

data class IgcRecorderMetadata(
    val manufacturerId: String = "XCP",
    val recorderType: String = "XCPro,Mobile",
    val firmwareVersion: String?,
    val hardwareVersion: String?,
    val gpsReceiver: String?,
    val pressureSensor: String?,
    val securityStatus: String = "UNSIGNED",
    val securitySignatureProfile: IgcSecuritySignatureProfile = IgcSecuritySignatureProfile.NONE,
    val gpsAltitudeDatum: IgcGpsAltitudeDatum = IgcGpsAltitudeDatum.GEO,
    val pressureAltitudeDatum: IgcPressureAltitudeDatum = IgcPressureAltitudeDatum.ISA
)

data class IgcHeaderContext(
    val utcDate: LocalDate,
    val flightNumberOfDay: Int,
    val profileMetadata: IgcProfileMetadata?,
    val recorderMetadata: IgcRecorderMetadata
)

data class IgcTaskDeclarationWaypoint(
    val name: String,
    val latitudeDegrees: Double,
    val longitudeDegrees: Double
)

data class IgcTaskDeclarationSnapshot(
    val taskId: String,
    val capturedAtUtcMs: Long,
    val waypoints: List<IgcTaskDeclarationWaypoint>
)

sealed interface IgcTaskDeclarationStartSnapshot {
    data object Absent : IgcTaskDeclarationStartSnapshot

    data class Available(
        val snapshot: IgcTaskDeclarationSnapshot
    ) : IgcTaskDeclarationStartSnapshot

    data class Invalid(
        val reason: String
    ) : IgcTaskDeclarationStartSnapshot
}

interface IgcProfileMetadataSource {
    fun activeProfileMetadata(): IgcProfileMetadata?
}

interface IgcRecorderMetadataSource {
    fun recorderMetadata(): IgcRecorderMetadata
}

interface IgcTaskDeclarationSource {
    fun snapshotForStart(sessionId: Long, capturedAtUtcMs: Long): IgcTaskDeclarationStartSnapshot
}
