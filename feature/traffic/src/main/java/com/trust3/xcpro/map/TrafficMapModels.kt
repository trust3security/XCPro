package com.trust3.xcpro.map

import androidx.compose.runtime.Composable
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.adsb.ADSB_DEFAULT_MEDIUM_UNKNOWN_ICON_ENABLED_DEFAULT as adsbDefaultMediumUnknownIconEnabledDefault
import com.trust3.xcpro.adsb.ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT as adsbEmergencyFlashEnabledDefault
import com.trust3.xcpro.adsb.ADSB_ERROR_CIRCUIT_BREAKER_OPEN as adsbErrorCircuitBreakerOpen
import com.trust3.xcpro.adsb.ADSB_ERROR_CIRCUIT_BREAKER_PROBE as adsbErrorCircuitBreakerProbe
import com.trust3.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX as adsbIconSizeDefaultPx
import com.trust3.xcpro.adsb.ADSB_ICON_SIZE_MAX_PX as adsbIconSizeMaxPx
import com.trust3.xcpro.adsb.ADSB_ICON_SIZE_MIN_PX as adsbIconSizeMinPx
import com.trust3.xcpro.adsb.ADSB_MAX_DISTANCE_DEFAULT_KM as adsbMaxDistanceDefaultKm
import com.trust3.xcpro.adsb.ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS as adsbVerticalFilterAboveDefaultMeters
import com.trust3.xcpro.adsb.ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS as adsbVerticalFilterBelowDefaultMeters
import com.trust3.xcpro.adsb.AdsbAuthMode
import com.trust3.xcpro.adsb.AdsbConnectionState
import com.trust3.xcpro.adsb.AdsbEmergencyAudioKpiPolicy
import com.trust3.xcpro.adsb.AdsbNetworkFailureKind
import com.trust3.xcpro.adsb.AdsbProximityTier
import com.trust3.xcpro.adsb.AdsbMarkerDetailsSheet as adsbMarkerDetailsSheetInternal
import com.trust3.xcpro.adsb.AdsbSelectedTargetDetails
import com.trust3.xcpro.adsb.AdsbTrafficSnapshot
import com.trust3.xcpro.adsb.AdsbTrafficUiModel
import com.trust3.xcpro.adsb.Icao24
import com.trust3.xcpro.adsb.clampAdsbIconSizePx as clampAdsbIconSizePxInternal
import com.trust3.xcpro.adsb.ui.AdsbAircraftIcon
import com.trust3.xcpro.adsb.ui.iconForAircraft
import com.trust3.xcpro.ogn.OGN_ICON_SIZE_DEFAULT_PX as ognIconSizeDefaultPx
import com.trust3.xcpro.ogn.OGN_ICON_SIZE_MAX_PX as ognIconSizeMaxPx
import com.trust3.xcpro.ogn.OGN_ICON_SIZE_MIN_PX as ognIconSizeMinPx
import com.trust3.xcpro.ogn.OgnAircraftIcon
import com.trust3.xcpro.ogn.OgnConnectionIssue
import com.trust3.xcpro.ogn.OgnConnectionState
import com.trust3.xcpro.ogn.OgnDisplayUpdateMode
import com.trust3.xcpro.ogn.OgnGliderTrailSegment
import com.trust3.xcpro.ogn.OgnGliderTrailRepository
import com.trust3.xcpro.ogn.OgnThermalPoint
import com.trust3.xcpro.ogn.OgnSelectionLookup
import com.trust3.xcpro.ogn.SelectedOgnThermalContext
import com.trust3.xcpro.ogn.SelectedOgnThermalOverlayContext
import com.trust3.xcpro.ogn.OgnSubscriptionPolicy
import com.trust3.xcpro.ogn.OgnThermalHotspot
import com.trust3.xcpro.ogn.OgnThermalHotspotState
import com.trust3.xcpro.ogn.OgnThermalRepository
import com.trust3.xcpro.ogn.OgnThermalDetailsSheet as ognThermalDetailsSheetInternal
import com.trust3.xcpro.ogn.OgnViewportBounds
import com.trust3.xcpro.ogn.OgnTrafficPreferencesRepository
import com.trust3.xcpro.ogn.OgnTrailSelectionViewModel
import com.trust3.xcpro.ogn.OgnTrailSelectionPreferencesRepository
import com.trust3.xcpro.ogn.OgnTrafficRepository
import com.trust3.xcpro.ogn.OgnTrafficSnapshot
import com.trust3.xcpro.ogn.OgnTrafficTarget
import com.trust3.xcpro.ogn.OgnMarkerDetailsSheet as ognMarkerDetailsSheetInternal
import com.trust3.xcpro.ogn.expandOgnSelectionAliases as expandOgnSelectionAliasesInternal
import com.trust3.xcpro.ogn.buildOgnSelectionLookup as buildOgnSelectionLookupInternal
import com.trust3.xcpro.ogn.clampOgnIconSizePx as clampOgnIconSizePxInternal
import com.trust3.xcpro.ogn.legacyOgnKeyFromCanonicalOrNull as legacyOgnKeyFromCanonicalOrNullInternal
import com.trust3.xcpro.ogn.iconForOgnAircraftIdentity as iconForOgnAircraftIdentityInternal
import com.trust3.xcpro.ogn.isValidThermalCoordinate as isValidThermalCoordinateInternal
import com.trust3.xcpro.ogn.normalizeOgnAircraftKey as normalizeOgnAircraftKeyInternal
import com.trust3.xcpro.ogn.normalizeOgnAircraftKeyOrNull as normalizeOgnAircraftKeyOrNullInternal
import com.trust3.xcpro.ogn.selectionLookupContainsOgnKey as selectionLookupContainsOgnKeyInternal
import com.trust3.xcpro.ogn.snailColorHexStops as snailColorHexStopsInternal

// Transitional map-facing traffic surface used while modules decouple.
typealias AdsbAuthMode = com.trust3.xcpro.adsb.AdsbAuthMode
typealias AdsbConnectionState = com.trust3.xcpro.adsb.AdsbConnectionState
typealias AdsbEmergencyAudioKpiPolicy = com.trust3.xcpro.adsb.AdsbEmergencyAudioKpiPolicy
typealias AdsbNetworkFailureKind = com.trust3.xcpro.adsb.AdsbNetworkFailureKind
typealias AdsbProximityReason = com.trust3.xcpro.adsb.AdsbProximityReason
typealias AdsbProximityTier = com.trust3.xcpro.adsb.AdsbProximityTier
typealias AdsbSelectedTargetDetails = com.trust3.xcpro.adsb.AdsbSelectedTargetDetails
typealias AdsbTrafficSnapshot = com.trust3.xcpro.adsb.AdsbTrafficSnapshot
typealias AdsbTrafficUiModel = com.trust3.xcpro.adsb.AdsbTrafficUiModel
typealias AdsbMetadataEnrichmentUseCase =
    com.trust3.xcpro.adsb.metadata.domain.AdsbMetadataEnrichmentUseCase
typealias AircraftMetadata = com.trust3.xcpro.adsb.metadata.domain.AircraftMetadata
typealias AircraftMetadataRepository =
    com.trust3.xcpro.adsb.metadata.domain.AircraftMetadataRepository
typealias AircraftMetadataSyncRepository =
    com.trust3.xcpro.adsb.metadata.domain.AircraftMetadataSyncRepository
typealias AircraftMetadataSyncScheduler =
    com.trust3.xcpro.adsb.metadata.domain.AircraftMetadataSyncScheduler
typealias MetadataSyncRunResult = com.trust3.xcpro.adsb.metadata.domain.MetadataSyncRunResult
typealias MetadataSyncState = com.trust3.xcpro.adsb.metadata.domain.MetadataSyncState
typealias AdsbTrafficPreferencesRepository =
    com.trust3.xcpro.adsb.AdsbTrafficPreferencesRepository
typealias AdsbTrafficRepository = com.trust3.xcpro.adsb.AdsbTrafficRepository
typealias Icao24 = com.trust3.xcpro.adsb.Icao24
typealias AdsbAircraftIcon = com.trust3.xcpro.adsb.ui.AdsbAircraftIcon

typealias OgnAddressType = com.trust3.xcpro.ogn.OgnAddressType
typealias OgnAircraftIcon = com.trust3.xcpro.ogn.OgnAircraftIcon
typealias OgnConnectionIssue = com.trust3.xcpro.ogn.OgnConnectionIssue
typealias OgnConnectionState = com.trust3.xcpro.ogn.OgnConnectionState
typealias OgnDisplayUpdateMode = com.trust3.xcpro.ogn.OgnDisplayUpdateMode
typealias OgnGliderTrailRepository = com.trust3.xcpro.ogn.OgnGliderTrailRepository
typealias OgnGliderTrailSegment = com.trust3.xcpro.ogn.OgnGliderTrailSegment
typealias OgnThermalPoint = com.trust3.xcpro.ogn.OgnThermalPoint
typealias OgnSelectionLookup = com.trust3.xcpro.ogn.OgnSelectionLookup
typealias SelectedOgnThermalContext = com.trust3.xcpro.ogn.SelectedOgnThermalContext
typealias SelectedOgnThermalOverlayContext =
    com.trust3.xcpro.ogn.SelectedOgnThermalOverlayContext
typealias OgnThermalRepository = com.trust3.xcpro.ogn.OgnThermalRepository
typealias OgnThermalHotspot = com.trust3.xcpro.ogn.OgnThermalHotspot
typealias OgnThermalHotspotState = com.trust3.xcpro.ogn.OgnThermalHotspotState
typealias OgnTrafficPreferencesRepository = com.trust3.xcpro.ogn.OgnTrafficPreferencesRepository
typealias OgnTrafficSnapshot = com.trust3.xcpro.ogn.OgnTrafficSnapshot
typealias OgnTrafficRepository = com.trust3.xcpro.ogn.OgnTrafficRepository
typealias OgnTrailSelectionPreferencesRepository =
    com.trust3.xcpro.ogn.OgnTrailSelectionPreferencesRepository
typealias OgnTrafficTarget = com.trust3.xcpro.ogn.OgnTrafficTarget
typealias OgnTrailSelectionViewModel = com.trust3.xcpro.ogn.OgnTrailSelectionViewModel
typealias OgnViewportBounds = com.trust3.xcpro.ogn.OgnViewportBounds

fun adsbConnectionStateDisabled(): AdsbConnectionState =
    com.trust3.xcpro.adsb.AdsbConnectionState.Disabled
fun adsbConnectionStateActive(): AdsbConnectionState =
    com.trust3.xcpro.adsb.AdsbConnectionState.Active
fun adsbConnectionStateBackingOff(retryAfterSec: Int): AdsbConnectionState =
    com.trust3.xcpro.adsb.AdsbConnectionState.BackingOff(retryAfterSec)
fun adsbConnectionStateError(message: String): AdsbConnectionState =
    com.trust3.xcpro.adsb.AdsbConnectionState.Error(message)

fun metadataSyncStateIdle(): MetadataSyncState =
    com.trust3.xcpro.adsb.metadata.domain.MetadataSyncState.Idle
fun metadataSyncStateScheduled(): MetadataSyncState =
    com.trust3.xcpro.adsb.metadata.domain.MetadataSyncState.Scheduled
fun metadataSyncStatePausedByUser(lastSuccessWallMs: Long? = null): MetadataSyncState =
    com.trust3.xcpro.adsb.metadata.domain.MetadataSyncState.PausedByUser(lastSuccessWallMs)
fun metadataSyncRunResultSkipped(): MetadataSyncRunResult =
    com.trust3.xcpro.adsb.metadata.domain.MetadataSyncRunResult.Skipped

val ADSB_DEFAULT_MEDIUM_UNKNOWN_ICON_ENABLED_DEFAULT: Boolean =
    adsbDefaultMediumUnknownIconEnabledDefault
val ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT: Boolean = adsbEmergencyFlashEnabledDefault
val ADSB_ERROR_CIRCUIT_BREAKER_OPEN: String = adsbErrorCircuitBreakerOpen
val ADSB_ERROR_CIRCUIT_BREAKER_PROBE: String = adsbErrorCircuitBreakerProbe
val ADSB_ICON_SIZE_DEFAULT_PX: Int = adsbIconSizeDefaultPx
val ADSB_ICON_SIZE_MIN_PX: Int = adsbIconSizeMinPx
val ADSB_ICON_SIZE_MAX_PX: Int = adsbIconSizeMaxPx
val ADSB_MAX_DISTANCE_DEFAULT_KM: Int = adsbMaxDistanceDefaultKm
val ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS: Double =
    adsbVerticalFilterAboveDefaultMeters
val ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS: Double =
    adsbVerticalFilterBelowDefaultMeters
val OGN_ICON_SIZE_DEFAULT_PX: Int = ognIconSizeDefaultPx
val OGN_ICON_SIZE_MIN_PX: Int = ognIconSizeMinPx
val OGN_ICON_SIZE_MAX_PX: Int = ognIconSizeMaxPx

fun clampAdsbIconSizePx(sizePx: Int): Int = clampAdsbIconSizePxInternal(sizePx)
fun clampOgnIconSizePx(sizePx: Int): Int = clampOgnIconSizePxInternal(sizePx)

fun AdsbTrafficUiModel.aircraftIcon(): AdsbAircraftIcon = iconForAircraft(
    category = category,
    metadataTypecode = metadataTypecode,
    metadataIcaoAircraftType = metadataIcaoAircraftType,
    icao24Raw = id.raw
)
fun AdsbAircraftIcon.emergencyStyleImageId(): String = "${styleImageId}_emergency"

fun isValidOgnThermalCoordinate(latitude: Double, longitude: Double): Boolean =
    isValidThermalCoordinateInternal(latitude = latitude, longitude = longitude)

fun snailColorHexStops(): Array<String> = snailColorHexStopsInternal()

fun iconForOgnAircraftIdentity(
    aircraftTypeCode: Int?,
    competitionNumber: String?
): OgnAircraftIcon = iconForOgnAircraftIdentityInternal(
    aircraftTypeCode = aircraftTypeCode,
    competitionNumber = competitionNumber
)

fun isInViewport(
    latitude: Double,
    longitude: Double,
    bounds: OgnViewportBounds
): Boolean = OgnSubscriptionPolicy.isInViewport(
    latitude = latitude,
    longitude = longitude,
    bounds = bounds
)

fun buildOgnSelectionLookup(selectedKeys: Set<String>): OgnSelectionLookup =
    buildOgnSelectionLookupInternal(selectedKeys)

fun expandOgnSelectionAliases(key: String): Set<String> =
    expandOgnSelectionAliasesInternal(key)

fun normalizeOgnAircraftKey(raw: String): String =
    normalizeOgnAircraftKeyInternal(raw)

fun legacyOgnKeyFromCanonicalOrNull(key: String): String? =
    legacyOgnKeyFromCanonicalOrNullInternal(key)

fun normalizeOgnAircraftKeyOrNull(raw: String?): String? =
    normalizeOgnAircraftKeyOrNullInternal(raw)

fun selectionLookupContainsOgnKey(
    lookup: OgnSelectionLookup,
    candidateKey: String
): Boolean = selectionLookupContainsOgnKeyInternal(
    lookup = lookup,
    candidateKey = candidateKey
)

fun haversineMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double = OgnSubscriptionPolicy.haversineMeters(
    lat1 = lat1,
    lon1 = lon1,
    lat2 = lat2,
    lon2 = lon2
)

@Composable
fun AdsbMarkerDetailsSheet(
    target: AdsbSelectedTargetDetails,
    unitsPreferences: UnitsPreferences,
    onDismiss: () -> Unit
) = adsbMarkerDetailsSheetInternal(
    target = target,
    unitsPreferences = unitsPreferences,
    onDismiss = onDismiss
)

@Composable
fun OgnMarkerDetailsSheet(
    target: OgnTrafficTarget,
    sciaEnabledForAircraft: Boolean,
    onSciaEnabledForAircraftChanged: (Boolean) -> Unit,
    targetEnabledForAircraft: Boolean,
    onTargetEnabledForAircraftChanged: (Boolean) -> Unit,
    targetToggleEnabled: Boolean,
    unitsPreferences: UnitsPreferences,
    onDismiss: () -> Unit
) = ognMarkerDetailsSheetInternal(
    target = target,
    sciaEnabledForAircraft = sciaEnabledForAircraft,
    onSciaEnabledForAircraftChanged = onSciaEnabledForAircraftChanged,
    targetEnabledForAircraft = targetEnabledForAircraft,
    onTargetEnabledForAircraftChanged = onTargetEnabledForAircraftChanged,
    targetToggleEnabled = targetToggleEnabled,
    unitsPreferences = unitsPreferences,
    onDismiss = onDismiss
)

@Composable
fun OgnThermalDetailsSheet(
    hotspot: OgnThermalHotspot,
    context: SelectedOgnThermalContext?,
    distanceMeters: Double?,
    unitsPreferences: UnitsPreferences,
    onDismiss: () -> Unit
) = ognThermalDetailsSheetInternal(
    hotspot = hotspot,
    context = context,
    distanceMeters = distanceMeters,
    unitsPreferences = unitsPreferences,
    onDismiss = onDismiss
)

fun AdsbConnectionState.isDisabled(): Boolean =
    this is com.trust3.xcpro.adsb.AdsbConnectionState.Disabled

fun AdsbConnectionState.isActive(): Boolean =
    this is com.trust3.xcpro.adsb.AdsbConnectionState.Active

fun AdsbConnectionState.isBackingOff(): Boolean =
    this is com.trust3.xcpro.adsb.AdsbConnectionState.BackingOff

fun AdsbConnectionState.isError(): Boolean =
    this is com.trust3.xcpro.adsb.AdsbConnectionState.Error

fun AdsbConnectionState.backoffRetryAfterSec(): Int? = when (this) {
    is com.trust3.xcpro.adsb.AdsbConnectionState.BackingOff -> retryAfterSec
    else -> null
}
