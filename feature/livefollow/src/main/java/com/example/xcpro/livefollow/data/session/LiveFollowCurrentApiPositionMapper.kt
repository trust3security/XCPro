package com.example.xcpro.livefollow.data.session

import com.google.gson.JsonObject
import com.example.xcpro.livefollow.data.transport.currentApiIsoUtcFromWallMs
import com.example.xcpro.livefollow.model.LiveOwnshipSnapshot

internal data class CurrentApiPositionUploadRequest(
    val sessionId: String,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeMslMeters: Double,
    val groundSpeedMs: Double,
    val headingDeg: Double,
    val fixWallMs: Long
) {
    val timestampIsoUtc: String = currentApiIsoUtcFromWallMs(fixWallMs)

    fun toJsonString(): String = JsonObject().apply {
        addProperty("session_id", sessionId)
        addProperty("lat", latitudeDeg)
        addProperty("lon", longitudeDeg)
        addProperty("alt", altitudeMslMeters)
        // AI-NOTE: The deployed LiveFollow contract does not label speed units.
        // This slice freezes the wire field to XCPro's canonical groundSpeedMs
        // value in m/s so we do not hide an implicit unit conversion.
        addProperty("speed", groundSpeedMs)
        addProperty("heading", headingDeg)
        addProperty("timestamp", timestampIsoUtc)
    }.toString()
}

internal object LiveFollowCurrentApiPositionMapper {
    fun map(
        sessionId: String,
        snapshot: LiveOwnshipSnapshot
    ): CurrentApiPositionUploadRequest? {
        val normalizedSessionId = sessionId.trim().takeIf { it.isNotEmpty() } ?: return null
        val latitudeDeg = snapshot.latitudeDeg.takeFiniteOrNull() ?: return null
        val longitudeDeg = snapshot.longitudeDeg.takeFiniteOrNull() ?: return null
        val altitudeMslMeters = (
            snapshot.pressureAltitudeMslMeters.takeFiniteOrNull()
                ?: snapshot.gpsAltitudeMslMeters.takeFiniteOrNull()
            ) ?: return null
        val groundSpeedMs = snapshot.groundSpeedMs.takeFiniteOrNull() ?: return null
        val headingDeg = snapshot.trackDeg.takeFiniteOrNull() ?: return null
        val fixWallMs = snapshot.fixWallMs?.takeIf { it > 0L } ?: return null

        return CurrentApiPositionUploadRequest(
            sessionId = normalizedSessionId,
            latitudeDeg = latitudeDeg,
            longitudeDeg = longitudeDeg,
            altitudeMslMeters = altitudeMslMeters,
            groundSpeedMs = groundSpeedMs,
            headingDeg = headingDeg,
            fixWallMs = fixWallMs
        )
    }
}

private fun Double?.takeFiniteOrNull(): Double? = this?.takeIf { it.isFinite() }
