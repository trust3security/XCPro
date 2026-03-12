package com.example.xcpro.profiles

import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.units.UnitsPreferences

internal data class CardPreferencesSectionSnapshot(
    val templates: List<CardTemplateSnapshot>,
    val profileTemplateCards: Map<String, Map<String, List<String>>>,
    val profileFlightModeTemplates: Map<String, Map<String, String>>,
    val profileFlightModeVisibilities: Map<String, Map<String, Boolean>>,
    val profileCardPositions: Map<String, Map<String, Map<String, CardPositionSnapshot>>>,
    val cardsAcrossPortrait: Int,
    val cardsAnchorPortrait: String,
    val lastActiveTemplate: String?,
    val varioSmoothingAlpha: Float
)

internal data class CardTemplateSnapshot(
    val id: String,
    val name: String,
    val description: String,
    val cardIds: List<String>,
    val isPreset: Boolean,
    val createdAt: Long
)

internal data class CardPositionSnapshot(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

internal data class FlightMgmtSectionSnapshot(
    val lastActiveTab: String,
    val profileLastFlightModes: Map<String, String>
)

internal data class LookAndFeelSectionSnapshot(
    val statusBarStyleByProfile: Map<String, String>,
    val cardStyleByProfile: Map<String, String>,
    val colorThemeByProfile: Map<String, String>
)

internal data class ThemeSectionSnapshot(
    val themeIdByProfile: Map<String, String>,
    val customColorsByProfileAndTheme: Map<String, Map<String, String>>
)

internal data class MapWidgetLayoutSectionSnapshot(
    val widgetsByProfile: Map<String, Map<String, MapWidgetPlacementSnapshot>> = emptyMap(),
    val widgets: Map<String, MapWidgetPlacementSnapshot>? = null
)

internal data class MapWidgetPlacementSnapshot(
    val offset: OffsetSnapshot?,
    val sizePx: Float?
)

internal data class OffsetSnapshot(
    val x: Float,
    val y: Float
)

internal data class VariometerWidgetLayoutSectionSnapshot(
    val layoutsByProfile: Map<String, VariometerLayoutProfileSnapshot> = emptyMap(),
    val offset: OffsetSnapshot? = null,
    val sizePx: Float? = null,
    val hasPersistedOffset: Boolean? = null,
    val hasPersistedSize: Boolean? = null
)

internal data class GliderSectionSnapshot(
    val profiles: Map<String, GliderProfileSectionSnapshot> = emptyMap(),
    val selectedModelId: String? = null,
    val effectiveModelId: String? = null,
    val isFallbackPolarActive: Boolean? = null,
    val config: GliderConfig? = null
)

internal data class UnitsSectionSnapshot(
    val unitsByProfile: Map<String, UnitsPreferences> = emptyMap()
)

internal data class MapStyleSectionSnapshot(
    val stylesByProfile: Map<String, String> = emptyMap()
)

internal data class SnailTrailSectionSnapshot(
    val settingsByProfile: Map<String, SnailTrailProfileSectionSnapshot> = emptyMap()
)

internal data class SnailTrailProfileSectionSnapshot(
    val length: String,
    val type: String,
    val windDriftEnabled: Boolean,
    val scalingEnabled: Boolean
)

internal data class OrientationSectionSnapshot(
    val settingsByProfile: Map<String, OrientationProfileSectionSnapshot> = emptyMap()
)

internal data class OrientationProfileSectionSnapshot(
    val cruiseMode: String,
    val circlingMode: String,
    val minSpeedThresholdMs: Double,
    val gliderScreenPercent: Int,
    val mapShiftBiasMode: String,
    val mapShiftBiasStrength: Double,
    val autoResetEnabled: Boolean? = null,
    val autoResetTimeoutSeconds: Int? = null,
    val bearingSmoothingEnabled: Boolean? = null
)

internal data class QnhSectionSnapshot(
    val valuesByProfile: Map<String, QnhProfileSectionSnapshot> = emptyMap()
)

internal data class QnhProfileSectionSnapshot(
    val manualQnhHpa: Double? = null,
    val capturedAtWallMs: Long? = null,
    val source: String? = null
)

internal data class WaypointFileSectionSnapshot(
    val selectionsByProfile: Map<String, WaypointFileProfileSectionSnapshot> = emptyMap()
)

internal data class WaypointFileProfileSectionSnapshot(
    val selectedFiles: Map<String, Boolean> = emptyMap()
)

internal data class AirspaceSectionSnapshot(
    val settingsByProfile: Map<String, AirspaceProfileSectionSnapshot> = emptyMap()
)

internal data class AirspaceProfileSectionSnapshot(
    val selectedFiles: Map<String, Boolean> = emptyMap(),
    val selectedClasses: Map<String, Boolean> = emptyMap()
)

internal data class VariometerLayoutProfileSnapshot(
    val offset: OffsetSnapshot,
    val sizePx: Float,
    val hasPersistedOffset: Boolean,
    val hasPersistedSize: Boolean
)

internal data class GliderProfileSectionSnapshot(
    val selectedModelId: String?,
    val effectiveModelId: String?,
    val isFallbackPolarActive: Boolean,
    val config: GliderConfig
)

internal data class LevoVarioSectionSnapshot(
    val macCready: Double,
    val macCreadyRisk: Double,
    val autoMcEnabled: Boolean,
    val teCompensationEnabled: Boolean,
    val showWindSpeedOnVario: Boolean,
    val showHawkCard: Boolean,
    val enableHawkUi: Boolean,
    val audioEnabled: Boolean,
    val audioVolume: Float,
    val audioLiftThreshold: Double,
    val audioSinkSilenceThreshold: Double,
    val audioDutyCycle: Double,
    val audioDeadbandMin: Double,
    val audioDeadbandMax: Double,
    val hawkNeedleOmegaMinHz: Double,
    val hawkNeedleOmegaMaxHz: Double,
    val hawkNeedleTargetTauSec: Double,
    val hawkNeedleDriftTauMinSec: Double,
    val hawkNeedleDriftTauMaxSec: Double
)

internal data class ThermallingModeSectionSnapshot(
    val enabled: Boolean,
    val switchToThermalMode: Boolean,
    val zoomOnlyFallbackWhenThermalHidden: Boolean,
    val enterDelaySeconds: Int,
    val exitDelaySeconds: Int,
    val applyZoomOnEnter: Boolean,
    val thermalZoomLevel: Float,
    val rememberManualThermalZoomInSession: Boolean,
    val restorePreviousModeOnExit: Boolean,
    val restorePreviousZoomOnExit: Boolean
)

internal data class OgnTrafficSectionSnapshot(
    val enabled: Boolean,
    val iconSizePx: Int,
    val receiveRadiusKm: Int,
    val autoReceiveRadiusEnabled: Boolean,
    val displayUpdateMode: String,
    val showSciaEnabled: Boolean,
    val showThermalsEnabled: Boolean,
    val thermalRetentionHours: Int,
    val hotspotsDisplayPercent: Int,
    val targetEnabled: Boolean,
    val targetAircraftKey: String?,
    val ownFlarmHex: String?,
    val ownIcaoHex: String?,
    val clientCallsign: String?
)

internal data class OgnTrailSelectionSectionSnapshot(
    val selectedAircraftKeys: Set<String>
)

internal data class AdsbTrafficSectionSnapshot(
    val enabled: Boolean,
    val iconSizePx: Int,
    val maxDistanceKm: Int,
    val verticalAboveMeters: Double,
    val verticalBelowMeters: Double,
    val emergencyFlashEnabled: Boolean,
    val defaultMediumUnknownIconEnabled: Boolean? = null,
    val emergencyAudioEnabled: Boolean,
    val emergencyAudioCooldownMs: Long,
    val emergencyAudioMasterEnabled: Boolean,
    val emergencyAudioShadowMode: Boolean,
    val emergencyAudioRollbackLatched: Boolean,
    val emergencyAudioRollbackReason: String?,
    val defaultMediumUnknownIconRollbackLatched: Boolean? = null,
    val defaultMediumUnknownIconRollbackReason: String? = null
)

internal data class WeatherOverlaySectionSnapshot(
    val enabled: Boolean,
    val opacity: Float,
    val animatePastWindow: Boolean,
    val animationWindow: String,
    val animationSpeed: String,
    val transitionQuality: String,
    val frameMode: String,
    val manualFrameIndex: Int,
    val smooth: Boolean,
    val snow: Boolean
)

internal data class ForecastSectionSnapshot(
    val overlayEnabled: Boolean,
    val opacity: Float,
    val windOverlayScale: Float,
    val windOverlayEnabled: Boolean,
    val windDisplayMode: String,
    val skySightSatelliteOverlayEnabled: Boolean,
    val skySightSatelliteImageryEnabled: Boolean,
    val skySightSatelliteRadarEnabled: Boolean,
    val skySightSatelliteLightningEnabled: Boolean,
    val skySightSatelliteAnimateEnabled: Boolean,
    val skySightSatelliteHistoryFrames: Int,
    val selectedPrimaryParameterId: String,
    val selectedWindParameterId: String,
    val selectedTimeUtcMs: Long?,
    val selectedRegion: String,
    val followTimeOffsetMinutes: Int,
    val autoTimeEnabled: Boolean
)

internal data class WindOverrideSectionSnapshot(
    val manualOverride: ManualWindOverrideSnapshot?
)

internal data class ManualWindOverrideSnapshot(
    val speedMs: Double,
    val directionFromDeg: Double,
    val timestampMillis: Long,
    val source: String
)
