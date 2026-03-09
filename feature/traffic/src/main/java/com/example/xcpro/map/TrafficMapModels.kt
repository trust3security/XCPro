package com.example.xcpro.map

import androidx.compose.runtime.Composable
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.adsb.ADSB_DEFAULT_MEDIUM_UNKNOWN_ICON_ENABLED_DEFAULT as adsbDefaultMediumUnknownIconEnabledDefault
import com.example.xcpro.adsb.ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT as adsbEmergencyFlashEnabledDefault
import com.example.xcpro.adsb.ADSB_ERROR_CIRCUIT_BREAKER_OPEN as adsbErrorCircuitBreakerOpen
import com.example.xcpro.adsb.ADSB_ERROR_CIRCUIT_BREAKER_PROBE as adsbErrorCircuitBreakerProbe
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX as adsbIconSizeDefaultPx
import com.example.xcpro.adsb.ADSB_ICON_SIZE_MAX_PX as adsbIconSizeMaxPx
import com.example.xcpro.adsb.ADSB_ICON_SIZE_MIN_PX as adsbIconSizeMinPx
import com.example.xcpro.adsb.ADSB_MAX_DISTANCE_DEFAULT_KM as adsbMaxDistanceDefaultKm
import com.example.xcpro.adsb.ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS as adsbVerticalFilterAboveDefaultMeters
import com.example.xcpro.adsb.ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS as adsbVerticalFilterBelowDefaultMeters
import com.example.xcpro.adsb.AdsbAuthMode
import com.example.xcpro.adsb.AdsbConnectionState
import com.example.xcpro.adsb.AdsbEmergencyAudioKpiPolicy
import com.example.xcpro.adsb.AdsbNetworkFailureKind
import com.example.xcpro.adsb.AdsbProximityTier
import com.example.xcpro.adsb.AdsbMarkerDetailsSheet as adsbMarkerDetailsSheetInternal
import com.example.xcpro.adsb.AdsbSelectedTargetDetails
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.clampAdsbIconSizePx as clampAdsbIconSizePxInternal
import com.example.xcpro.adsb.ui.AdsbAircraftIcon
import com.example.xcpro.adsb.ui.iconForAircraft
import com.example.xcpro.ogn.OGN_ICON_SIZE_DEFAULT_PX as ognIconSizeDefaultPx
import com.example.xcpro.ogn.OGN_ICON_SIZE_MAX_PX as ognIconSizeMaxPx
import com.example.xcpro.ogn.OGN_ICON_SIZE_MIN_PX as ognIconSizeMinPx
import com.example.xcpro.ogn.OgnAircraftIcon
import com.example.xcpro.ogn.OgnConnectionState
import com.example.xcpro.ogn.OgnDisplayUpdateMode
import com.example.xcpro.ogn.OgnGliderTrailSegment
import com.example.xcpro.ogn.OgnGliderTrailRepository
import com.example.xcpro.ogn.OgnSelectionLookup
import com.example.xcpro.ogn.OgnSubscriptionPolicy
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.OgnThermalHotspotState
import com.example.xcpro.ogn.OgnThermalRepository
import com.example.xcpro.ogn.OgnThermalDetailsSheet as ognThermalDetailsSheetInternal
import com.example.xcpro.ogn.OgnViewportBounds
import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import com.example.xcpro.ogn.OgnTrailSelectionViewModel
import com.example.xcpro.ogn.OgnTrailSelectionPreferencesRepository
import com.example.xcpro.ogn.OgnTrafficRepository
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnMarkerDetailsSheet as ognMarkerDetailsSheetInternal
import com.example.xcpro.ogn.expandOgnSelectionAliases as expandOgnSelectionAliasesInternal
import com.example.xcpro.ogn.buildOgnSelectionLookup as buildOgnSelectionLookupInternal
import com.example.xcpro.ogn.clampOgnIconSizePx as clampOgnIconSizePxInternal
import com.example.xcpro.ogn.legacyOgnKeyFromCanonicalOrNull as legacyOgnKeyFromCanonicalOrNullInternal
import com.example.xcpro.ogn.iconForOgnAircraftIdentity as iconForOgnAircraftIdentityInternal
import com.example.xcpro.ogn.isValidThermalCoordinate as isValidThermalCoordinateInternal
import com.example.xcpro.ogn.normalizeOgnAircraftKey as normalizeOgnAircraftKeyInternal
import com.example.xcpro.ogn.normalizeOgnAircraftKeyOrNull as normalizeOgnAircraftKeyOrNullInternal
import com.example.xcpro.ogn.selectionLookupContainsOgnKey as selectionLookupContainsOgnKeyInternal
import com.example.xcpro.ogn.snailColorHexStops as snailColorHexStopsInternal

// Transitional map-facing traffic surface used while modules decouple.
typealias AdsbAuthMode = com.example.xcpro.adsb.AdsbAuthMode
typealias AdsbConnectionState = com.example.xcpro.adsb.AdsbConnectionState
typealias AdsbEmergencyAudioKpiPolicy = com.example.xcpro.adsb.AdsbEmergencyAudioKpiPolicy
typealias AdsbNetworkFailureKind = com.example.xcpro.adsb.AdsbNetworkFailureKind
typealias AdsbProximityReason = com.example.xcpro.adsb.AdsbProximityReason
typealias AdsbProximityTier = com.example.xcpro.adsb.AdsbProximityTier
typealias AdsbSelectedTargetDetails = com.example.xcpro.adsb.AdsbSelectedTargetDetails
typealias AdsbTrafficSnapshot = com.example.xcpro.adsb.AdsbTrafficSnapshot
typealias AdsbTrafficUiModel = com.example.xcpro.adsb.AdsbTrafficUiModel
typealias AdsbMetadataEnrichmentUseCase =
    com.example.xcpro.adsb.metadata.domain.AdsbMetadataEnrichmentUseCase
typealias AircraftMetadata = com.example.xcpro.adsb.metadata.domain.AircraftMetadata
typealias AircraftMetadataRepository =
    com.example.xcpro.adsb.metadata.domain.AircraftMetadataRepository
typealias AircraftMetadataSyncRepository =
    com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncRepository
typealias AircraftMetadataSyncScheduler =
    com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncScheduler
typealias MetadataSyncRunResult = com.example.xcpro.adsb.metadata.domain.MetadataSyncRunResult
typealias MetadataSyncState = com.example.xcpro.adsb.metadata.domain.MetadataSyncState
typealias AdsbTrafficPreferencesRepository =
    com.example.xcpro.adsb.AdsbTrafficPreferencesRepository
typealias AdsbTrafficRepository = com.example.xcpro.adsb.AdsbTrafficRepository
typealias Icao24 = com.example.xcpro.adsb.Icao24
typealias AdsbAircraftIcon = com.example.xcpro.adsb.ui.AdsbAircraftIcon

typealias OgnAddressType = com.example.xcpro.ogn.OgnAddressType
typealias OgnAircraftIcon = com.example.xcpro.ogn.OgnAircraftIcon
typealias OgnConnectionState = com.example.xcpro.ogn.OgnConnectionState
typealias OgnDisplayUpdateMode = com.example.xcpro.ogn.OgnDisplayUpdateMode
typealias OgnGliderTrailRepository = com.example.xcpro.ogn.OgnGliderTrailRepository
typealias OgnGliderTrailSegment = com.example.xcpro.ogn.OgnGliderTrailSegment
typealias OgnSelectionLookup = com.example.xcpro.ogn.OgnSelectionLookup
typealias OgnThermalRepository = com.example.xcpro.ogn.OgnThermalRepository
typealias OgnThermalHotspot = com.example.xcpro.ogn.OgnThermalHotspot
typealias OgnThermalHotspotState = com.example.xcpro.ogn.OgnThermalHotspotState
typealias OgnTrafficPreferencesRepository = com.example.xcpro.ogn.OgnTrafficPreferencesRepository
typealias OgnTrafficSnapshot = com.example.xcpro.ogn.OgnTrafficSnapshot
typealias OgnTrafficRepository = com.example.xcpro.ogn.OgnTrafficRepository
typealias OgnTrailSelectionPreferencesRepository =
    com.example.xcpro.ogn.OgnTrailSelectionPreferencesRepository
typealias OgnTrafficTarget = com.example.xcpro.ogn.OgnTrafficTarget
typealias OgnTrailSelectionViewModel = com.example.xcpro.ogn.OgnTrailSelectionViewModel
typealias OgnViewportBounds = com.example.xcpro.ogn.OgnViewportBounds

fun adsbConnectionStateDisabled(): AdsbConnectionState =
    com.example.xcpro.adsb.AdsbConnectionState.Disabled
fun adsbConnectionStateActive(): AdsbConnectionState =
    com.example.xcpro.adsb.AdsbConnectionState.Active
fun adsbConnectionStateBackingOff(retryAfterSec: Int): AdsbConnectionState =
    com.example.xcpro.adsb.AdsbConnectionState.BackingOff(retryAfterSec)
fun adsbConnectionStateError(message: String): AdsbConnectionState =
    com.example.xcpro.adsb.AdsbConnectionState.Error(message)

fun metadataSyncStateIdle(): MetadataSyncState =
    com.example.xcpro.adsb.metadata.domain.MetadataSyncState.Idle
fun metadataSyncStateScheduled(): MetadataSyncState =
    com.example.xcpro.adsb.metadata.domain.MetadataSyncState.Scheduled
fun metadataSyncStatePausedByUser(lastSuccessWallMs: Long? = null): MetadataSyncState =
    com.example.xcpro.adsb.metadata.domain.MetadataSyncState.PausedByUser(lastSuccessWallMs)
fun metadataSyncRunResultSkipped(): MetadataSyncRunResult =
    com.example.xcpro.adsb.metadata.domain.MetadataSyncRunResult.Skipped

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
    distanceMeters: Double?,
    unitsPreferences: UnitsPreferences,
    onDismiss: () -> Unit
) = ognThermalDetailsSheetInternal(
    hotspot = hotspot,
    distanceMeters = distanceMeters,
    unitsPreferences = unitsPreferences,
    onDismiss = onDismiss
)

fun AdsbConnectionState.isDisabled(): Boolean =
    this is com.example.xcpro.adsb.AdsbConnectionState.Disabled

fun AdsbConnectionState.isActive(): Boolean =
    this is com.example.xcpro.adsb.AdsbConnectionState.Active

fun AdsbConnectionState.isBackingOff(): Boolean =
    this is com.example.xcpro.adsb.AdsbConnectionState.BackingOff

fun AdsbConnectionState.isError(): Boolean =
    this is com.example.xcpro.adsb.AdsbConnectionState.Error

fun AdsbConnectionState.backoffRetryAfterSec(): Int? = when (this) {
    is com.example.xcpro.adsb.AdsbConnectionState.BackingOff -> retryAfterSec
    else -> null
}
