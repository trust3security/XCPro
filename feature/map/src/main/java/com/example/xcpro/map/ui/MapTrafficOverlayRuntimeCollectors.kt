package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.AdsbTrafficUiModel
import com.example.xcpro.map.Icao24
import com.example.xcpro.map.OgnTrafficTarget
import com.example.xcpro.map.SelectedOgnThermalContext
import com.example.xcpro.map.SelectedOgnThermalOverlayContext
import com.example.xcpro.map.TrafficMapCoordinate
import com.example.xcpro.map.model.MapLocationUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
internal fun BindMapTrafficOverlayRuntime(
    port: TrafficOverlayRenderPort,
    telemetrySink: TrafficOverlayCollectorTelemetrySink = NoOpTrafficOverlayCollectorTelemetrySink,
    inputs: MapTrafficOverlayRuntimeInputs,
    renderLocalOwnship: Boolean
) {
    val renderLocalOwnshipState = rememberUpdatedState(renderLocalOwnship)
    LaunchedEffect(port, telemetrySink, inputs) {
        bindMapTrafficOverlayRuntime(
            scope = this,
            port = port,
            telemetrySink = telemetrySink,
            inputs = inputs,
            renderLocalOwnship = snapshotFlow { renderLocalOwnshipState.value }.distinctUntilChanged()
        )
    }
}

internal fun bindMapTrafficOverlayRuntime(
    scope: CoroutineScope,
    port: TrafficOverlayRenderPort,
    telemetrySink: TrafficOverlayCollectorTelemetrySink = NoOpTrafficOverlayCollectorTelemetrySink,
    inputs: MapTrafficOverlayRuntimeInputs,
    renderLocalOwnship: Flow<Boolean>
) {
    val overlayOwnshipAltitudeForRender = combine(
        inputs.overlayOwnshipAltitudeMeters,
        renderLocalOwnship
    ) { ownshipAltitudeMeters, renderOwnship ->
        ownshipAltitudeMeters.takeIf { renderOwnship }
    }.distinctUntilChanged()
    val ownshipCoordinateForRender = combine(
        inputs.currentLocation,
        renderLocalOwnship
    ) { locationForUi, renderOwnship ->
        locationForUi.toOwnshipCoordinateOrNull(renderOwnship = renderOwnship)
    }.distinctUntilChanged()

    scope.launch {
        inputs.ognDisplayUpdateMode.collect { mode ->
            port.setOgnDisplayUpdateMode(mode)
        }
    }
    scope.launch {
        val ognTrafficTargetsBase = combine(
            inputs.ognOverlayEnabled,
            inputs.ognTargets,
            inputs.selectedOgnTargetKey
        ) { overlayEnabled, targets, selectedTargetKey ->
            OgnTrafficTargetsBase(
                targets = if (overlayEnabled) targets else emptyList(),
                selectedTargetKey = selectedTargetKey
            )
        }
        combine(
            ognTrafficTargetsBase,
            overlayOwnshipAltitudeForRender,
            inputs.ognAltitudeUnit,
            inputs.unitsPreferences
        ) { base, ownshipAltitudeMeters, altitudeUnit, unitsPreferences ->
            OgnTrafficTargetsRenderRequest(
                targets = base.targets,
                selectedTargetKey = base.selectedTargetKey,
                ownshipAltitudeMeters = ownshipAltitudeMeters,
                altitudeUnit = altitudeUnit,
                unitsPreferences = unitsPreferences
            )
        }.collectDistinctRequests(
            telemetrySink = telemetrySink,
            onEmission = { onOgnTrafficCollectorEmission() },
            onDeduped = { onOgnTrafficCollectorDeduped() },
            signatureOf = OgnTrafficTargetsRenderRequest::toRenderSignature
        ) { request ->
            telemetrySink.onOgnTrafficPortUpdate()
            port.updateOgnTrafficTargets(
                targets = request.targets,
                selectedTargetKey = request.selectedTargetKey,
                ownshipAltitudeMeters = request.ownshipAltitudeMeters,
                altitudeUnit = request.altitudeUnit,
                unitsPreferences = request.unitsPreferences
            )
        }
    }
    scope.launch {
        combine(
            inputs.ognOverlayEnabled,
            inputs.showOgnThermalsEnabled,
            inputs.ognThermalHotspots
        ) { overlayEnabled, thermalsEnabled, hotspots ->
            if (overlayEnabled && thermalsEnabled) hotspots else emptyList()
        }
            .onEach { telemetrySink.onOgnThermalCollectorEmission() }
            .distinctUntilChanged()
            .collect { hotspots ->
            port.updateOgnThermalHotspots(hotspots)
        }
    }
    scope.launch {
        combine(
            inputs.ognOverlayEnabled,
            inputs.showOgnSciaEnabled,
            inputs.ognGliderTrailSegments
        ) { overlayEnabled, sciaEnabled, segments ->
            if (overlayEnabled && sciaEnabled) segments else emptyList()
        }
            .onEach { telemetrySink.onOgnTrailCollectorEmission() }
            .distinctUntilChanged()
            .collect { segments ->
            port.updateOgnGliderTrailSegments(segments)
        }
    }
    scope.launch {
        inputs.selectedOgnThermalContext
            .map { context -> context?.toOverlayContext() }
            .onEach { telemetrySink.onSelectedOgnThermalCollectorEmission() }
            .distinctUntilChanged()
            .collect { context ->
                port.updateSelectedOgnThermalContext(context)
            }
    }
    scope.launch {
        combine(
            combine(
                inputs.ognOverlayEnabled,
                inputs.ognTargetEnabled,
                inputs.ognResolvedTarget
            ) { overlayEnabled, targetEnabled, resolvedTarget ->
                OgnTargetVisualBase(
                    enabled = overlayEnabled && targetEnabled,
                    resolvedTarget = resolvedTarget?.takeIf { overlayEnabled && targetEnabled }
                )
            },
            ownshipCoordinateForRender,
            overlayOwnshipAltitudeForRender,
            inputs.ognAltitudeUnit,
            inputs.unitsPreferences
        ) { base, ownshipCoordinate, ownshipAltitudeMeters, altitudeUnit, unitsPreferences ->
            OgnTargetVisualRenderRequest(
                enabled = base.enabled,
                resolvedTarget = base.resolvedTarget,
                ownshipCoordinate = ownshipCoordinate,
                ownshipAltitudeMeters = ownshipAltitudeMeters,
                altitudeUnit = altitudeUnit,
                unitsPreferences = unitsPreferences
            )
        }.collectDistinctRequests(
            telemetrySink = telemetrySink,
            onEmission = { onOgnTargetVisualCollectorEmission() },
            onDeduped = { onOgnTargetVisualCollectorDeduped() },
            signatureOf = OgnTargetVisualRenderRequest::toRenderSignature
        ) { request ->
            telemetrySink.onOgnTargetVisualPortUpdate()
            port.updateOgnTargetVisuals(
                enabled = request.enabled,
                resolvedTarget = request.resolvedTarget,
                ownshipCoordinate = request.ownshipCoordinate,
                ownshipAltitudeMeters = request.ownshipAltitudeMeters,
                altitudeUnit = request.altitudeUnit,
                unitsPreferences = request.unitsPreferences
            )
        }
    }
    scope.launch {
        inputs.ognIconSizePx.collect { iconSizePx ->
            port.setOgnIconSizePx(iconSizePx)
        }
    }
    scope.launch {
        combine(
            inputs.adsbOverlayEnabled,
            inputs.adsbTargets,
            inputs.selectedAdsbTargetId,
            overlayOwnshipAltitudeForRender,
            inputs.unitsPreferences
        ) { overlayEnabled, targets, selectedTargetId, ownshipAltitudeMeters, unitsPreferences ->
            AdsbTrafficTargetsRenderRequest(
                targets = if (overlayEnabled) targets else emptyList(),
                selectedTargetId = selectedTargetId,
                ownshipAltitudeMeters = ownshipAltitudeMeters,
                unitsPreferences = unitsPreferences
            )
        }.collectDistinctRequests(
            telemetrySink = telemetrySink,
            onEmission = { onAdsbTrafficCollectorEmission() },
            onDeduped = { onAdsbTrafficCollectorDeduped() },
            signatureOf = AdsbTrafficTargetsRenderRequest::toRenderSignature
        ) { request ->
            telemetrySink.onAdsbTrafficPortUpdate()
            port.updateAdsbTrafficTargets(
                targets = request.targets,
                selectedTargetId = request.selectedTargetId,
                ownshipAltitudeMeters = request.ownshipAltitudeMeters,
                unitsPreferences = request.unitsPreferences
            )
        }
    }
    scope.launch {
        inputs.adsbIconSizePx.collect { iconSizePx ->
            port.setAdsbIconSizePx(iconSizePx)
        }
    }
    scope.launch {
        inputs.adsbEmergencyFlashEnabled.collect { enabled ->
            port.setAdsbEmergencyFlashEnabled(enabled)
        }
    }
    scope.launch {
        inputs.adsbDefaultMediumUnknownIconEnabled.collect { enabled ->
            port.setAdsbDefaultMediumUnknownIconEnabled(enabled)
        }
    }
}

private data class OgnTargetVisualBase(
    val enabled: Boolean,
    val resolvedTarget: OgnTrafficTarget?
)

private data class OgnTrafficTargetsBase(
    val targets: List<OgnTrafficTarget>,
    val selectedTargetKey: String?
)

internal data class OgnTrafficTargetsRenderRequest(
    val targets: List<OgnTrafficTarget>,
    val selectedTargetKey: String?,
    val ownshipAltitudeMeters: Double?,
    val altitudeUnit: AltitudeUnit,
    val unitsPreferences: UnitsPreferences
)

internal data class OgnTargetVisualRenderRequest(
    val enabled: Boolean,
    val resolvedTarget: OgnTrafficTarget?,
    val ownshipCoordinate: TrafficMapCoordinate?,
    val ownshipAltitudeMeters: Double?,
    val altitudeUnit: AltitudeUnit,
    val unitsPreferences: UnitsPreferences
)

internal data class AdsbTrafficTargetsRenderRequest(
    val targets: List<AdsbTrafficUiModel>,
    val selectedTargetId: Icao24?,
    val ownshipAltitudeMeters: Double?,
    val unitsPreferences: UnitsPreferences
)

private data class SignedRenderRequest<T, S>(
    val signature: S,
    val request: T
)

private data class OgnTrafficRenderSignature(
    val targets: List<OgnTrafficTargetRenderSignature>,
    val selectedTargetKey: String?,
    val ownshipAltitudeMeters: Double?,
    val altitudeUnit: AltitudeUnit,
    val unitsPreferences: UnitsPreferences
)

private data class OgnTrafficTargetRenderSignature(
    val canonicalKey: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val trackDegrees: Double?,
    val groundSpeedMps: Double?,
    val verticalSpeedMps: Double?,
    val displayLabel: String,
    val distanceMeters: Double?,
    val lastSeenMillis: Long,
    val addressType: com.example.xcpro.ogn.OgnAddressType
)

private data class OgnTargetVisualRenderSignature(
    val enabled: Boolean,
    val target: OgnTargetVisualTargetRenderSignature?,
    val ownshipCoordinate: TrafficMapCoordinate?,
    val ownshipAltitudeMeters: Double?,
    val altitudeUnit: AltitudeUnit,
    val unitsPreferences: UnitsPreferences
)

private data class OgnTargetVisualTargetRenderSignature(
    val canonicalKey: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val groundSpeedMps: Double?,
    val distanceMeters: Double?,
    val displayLabel: String
)

private data class AdsbTrafficRenderSignature(
    val targets: List<AdsbTrafficTargetRenderSignature>,
    val selectedTargetId: Icao24?,
    val ownshipAltitudeMeters: Double?,
    val unitsPreferences: UnitsPreferences
)

private data class AdsbTrafficTargetRenderSignature(
    val id: Icao24,
    val callsign: String?,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val speedMps: Double?,
    val trackDegrees: Double?,
    val climbMps: Double?,
    val ageSec: Int,
    val isStale: Boolean,
    val distanceMeters: Double,
    val bearingDegFromUser: Double,
    val usesOwnshipReference: Boolean,
    val positionSource: Int?,
    val category: Int?,
    val proximityTier: com.example.xcpro.adsb.AdsbProximityTier,
    val proximityReason: com.example.xcpro.adsb.AdsbProximityReason,
    val isClosing: Boolean,
    val closingRateMps: Double?,
    val isEmergencyCollisionRisk: Boolean,
    val isEmergencyAudioEligible: Boolean,
    val emergencyAudioIneligibilityReason: com.example.xcpro.adsb.AdsbEmergencyAudioIneligibilityReason?,
    val isCirclingEmergencyRedRule: Boolean,
    val metadataTypecode: String?,
    val metadataIcaoAircraftType: String?,
    val positionAgeSec: Int,
    val contactAgeSec: Int?,
    val isPositionStale: Boolean,
    val positionTimestampEpochSec: Long?,
    val effectivePositionEpochSec: Long?,
    val positionFreshnessSource: com.example.xcpro.adsb.AdsbPositionFreshnessSource
)

private suspend inline fun <T, S> Flow<T>.collectDistinctRequests(
    telemetrySink: TrafficOverlayCollectorTelemetrySink,
    crossinline onEmission: TrafficOverlayCollectorTelemetrySink.() -> Unit,
    crossinline onDeduped: TrafficOverlayCollectorTelemetrySink.() -> Unit,
    crossinline signatureOf: (T) -> S,
    crossinline onForward: (T) -> Unit
) {
    map { request ->
        telemetrySink.onEmission()
        SignedRenderRequest(
            signature = signatureOf(request),
            request = request
        )
    }.distinctUntilChanged { previous, next ->
        val same = previous.signature == next.signature
        if (same) {
            telemetrySink.onDeduped()
        }
        same
    }.collect { signedRequest ->
        onForward(signedRequest.request)
    }
}

private fun OgnTrafficTargetsRenderRequest.toRenderSignature(): OgnTrafficRenderSignature =
    OgnTrafficRenderSignature(
        targets = targets.map { it.toTrafficRenderSignature() },
        selectedTargetKey = selectedTargetKey,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        altitudeUnit = altitudeUnit,
        unitsPreferences = unitsPreferences
    )

private fun OgnTrafficTarget.toTrafficRenderSignature(): OgnTrafficTargetRenderSignature =
    OgnTrafficTargetRenderSignature(
        canonicalKey = canonicalKey,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitudeMeters,
        trackDegrees = trackDegrees,
        groundSpeedMps = groundSpeedMps,
        verticalSpeedMps = verticalSpeedMps,
        displayLabel = displayLabel,
        distanceMeters = distanceMeters,
        lastSeenMillis = lastSeenMillis,
        addressType = addressType
    )

private fun OgnTargetVisualRenderRequest.toRenderSignature(): OgnTargetVisualRenderSignature =
    OgnTargetVisualRenderSignature(
        enabled = enabled,
        target = resolvedTarget?.toTargetVisualRenderSignature(),
        ownshipCoordinate = ownshipCoordinate,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        altitudeUnit = altitudeUnit,
        unitsPreferences = unitsPreferences
    )

private fun OgnTrafficTarget.toTargetVisualRenderSignature(): OgnTargetVisualTargetRenderSignature =
    OgnTargetVisualTargetRenderSignature(
        canonicalKey = canonicalKey,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitudeMeters,
        groundSpeedMps = groundSpeedMps,
        distanceMeters = distanceMeters,
        displayLabel = displayLabel
    )

private fun AdsbTrafficTargetsRenderRequest.toRenderSignature(): AdsbTrafficRenderSignature =
    AdsbTrafficRenderSignature(
        targets = targets.map { it.toTrafficRenderSignature() },
        selectedTargetId = selectedTargetId,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        unitsPreferences = unitsPreferences
    )

private fun AdsbTrafficUiModel.toTrafficRenderSignature(): AdsbTrafficTargetRenderSignature =
    AdsbTrafficTargetRenderSignature(
        id = id,
        callsign = callsign,
        latitude = lat,
        longitude = lon,
        altitudeMeters = altitudeM,
        speedMps = speedMps,
        trackDegrees = trackDeg,
        climbMps = climbMps,
        ageSec = ageSec,
        isStale = isStale,
        distanceMeters = distanceMeters,
        bearingDegFromUser = bearingDegFromUser,
        usesOwnshipReference = usesOwnshipReference,
        positionSource = positionSource,
        category = category,
        proximityTier = proximityTier,
        proximityReason = proximityReason,
        isClosing = isClosing,
        closingRateMps = closingRateMps,
        isEmergencyCollisionRisk = isEmergencyCollisionRisk,
        isEmergencyAudioEligible = isEmergencyAudioEligible,
        emergencyAudioIneligibilityReason = emergencyAudioIneligibilityReason,
        isCirclingEmergencyRedRule = isCirclingEmergencyRedRule,
        metadataTypecode = metadataTypecode,
        metadataIcaoAircraftType = metadataIcaoAircraftType,
        positionAgeSec = positionAgeSec,
        contactAgeSec = contactAgeSec,
        isPositionStale = isPositionStale,
        positionTimestampEpochSec = positionTimestampEpochSec,
        effectivePositionEpochSec = effectivePositionEpochSec,
        positionFreshnessSource = positionFreshnessSource
    )

private fun MapLocationUiModel?.toOwnshipCoordinateOrNull(renderOwnship: Boolean): TrafficMapCoordinate? {
    if (!renderOwnship) return null
    val location = this ?: return null
    return TrafficMapCoordinate(
        latitude = location.latitude,
        longitude = location.longitude
    )
}

private fun SelectedOgnThermalContext.toOverlayContext(): SelectedOgnThermalOverlayContext =
    SelectedOgnThermalOverlayContext(
        hotspotId = hotspot.id,
        snailColorIndex = hotspot.snailColorIndex,
        hotspotPoint = hotspotPoint,
        highlightedSegments = highlightedSegments,
        occupancyHullPoints = occupancyHullPoints,
        startPoint = startPoint,
        latestPoint = latestPoint
    )
