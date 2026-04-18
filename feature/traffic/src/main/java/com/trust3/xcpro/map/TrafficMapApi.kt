package com.trust3.xcpro.map

import com.trust3.xcpro.adsb.AdsbTrafficSnapshot
import com.trust3.xcpro.adsb.AdsbTrafficUiModel
import com.trust3.xcpro.adsb.metadata.domain.MetadataSyncState
import com.trust3.xcpro.ogn.OgnDisplayUpdateMode
import com.trust3.xcpro.ogn.OgnGliderTrailSegment
import com.trust3.xcpro.ogn.SelectedOgnThermalContext
import com.trust3.xcpro.ogn.OgnThermalHotspot
import com.trust3.xcpro.ogn.OgnTrafficSnapshot
import com.trust3.xcpro.ogn.OgnTrafficTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface OgnTrafficFacade {
    val targets: StateFlow<List<OgnTrafficTarget>>
    val suppressedTargetIds: StateFlow<Set<String>>
    val snapshot: StateFlow<OgnTrafficSnapshot>
    val isStreamingEnabled: StateFlow<Boolean>
    val overlayEnabled: Flow<Boolean>
    val iconSizePx: Flow<Int>
    val displayUpdateMode: Flow<OgnDisplayUpdateMode>
    val showSciaEnabled: Flow<Boolean>
    val targetEnabled: Flow<Boolean>
    val targetAircraftKey: Flow<String?>
    val thermalHotspots: StateFlow<List<OgnThermalHotspot>>
    val showThermalsEnabled: Flow<Boolean>
    val gliderTrailSegments: Flow<List<OgnGliderTrailSegment>>
    fun selectedThermalContext(selectedThermalId: Flow<String?>): Flow<SelectedOgnThermalContext?>

    fun setStreamingEnabled(enabled: Boolean)
    fun updateCenter(latitude: Double, longitude: Double)
    fun updateAutoReceiveRadiusContext(
        zoomLevel: Float,
        groundSpeedMs: Double,
        isFlying: Boolean
    )

    suspend fun setOverlayEnabled(enabled: Boolean)
    suspend fun setIconSizePx(iconSizePx: Int)
    suspend fun setDisplayUpdateMode(mode: OgnDisplayUpdateMode)
    suspend fun setShowSciaEnabled(enabled: Boolean)
    suspend fun setOverlayAndShowSciaEnabled(
        overlayEnabled: Boolean,
        showSciaEnabled: Boolean
    )

    suspend fun setShowThermalsEnabled(enabled: Boolean)
    suspend fun setTargetSelection(enabled: Boolean, aircraftKey: String?)
    suspend fun clearTargetSelection()
    fun stop()
}

interface AdsbTrafficFacade {
    val targets: StateFlow<List<AdsbTrafficUiModel>>
    val snapshot: StateFlow<AdsbTrafficSnapshot>
    val isStreamingEnabled: StateFlow<Boolean>
    val overlayEnabled: Flow<Boolean>
    val iconSizePx: Flow<Int>
    val emergencyFlashEnabled: Flow<Boolean>
    val defaultMediumUnknownIconEnabled: Flow<Boolean>
    val defaultMediumUnknownIconRollbackReason: Flow<String?>
    val maxDistanceKm: Flow<Int>
    val verticalAboveMeters: Flow<Double>
    val verticalBelowMeters: Flow<Double>
    val metadataSyncState: StateFlow<MetadataSyncState>

    fun setStreamingEnabled(enabled: Boolean)
    fun clearTargets()
    fun updateCenter(latitude: Double, longitude: Double)
    fun updateOwnshipOrigin(latitude: Double, longitude: Double)
    fun updateOwnshipMotion(trackDeg: Double?, speedMps: Double?)
    fun clearOwnshipOrigin()
    fun updateOwnshipAltitudeMeters(altitudeMeters: Double?)
    fun updateOwnshipCirclingContext(
        isCircling: Boolean,
        circlingFeatureEnabled: Boolean
    )

    fun updateDisplayFilters(
        maxDistanceKm: Int,
        verticalAboveMeters: Double,
        verticalBelowMeters: Double
    )

    suspend fun setOverlayEnabled(enabled: Boolean)
    suspend fun setIconSizePx(iconSizePx: Int)
    suspend fun setDefaultMediumUnknownIconEnabled(enabled: Boolean)
    suspend fun latchDefaultMediumUnknownIconRollback(reason: String)
    suspend fun clearDefaultMediumUnknownIconRollback()
    suspend fun bootstrapMetadataSync()
    fun stop()
}
