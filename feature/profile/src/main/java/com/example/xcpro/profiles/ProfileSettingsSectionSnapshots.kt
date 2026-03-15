package com.example.xcpro.profiles

import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.units.UnitsPreferences

internal data class FlightMgmtSectionSnapshot(
    val lastActiveTab: String,
    val profileLastFlightModes: Map<String, String>
)

internal data class LookAndFeelSectionSnapshot(
    val statusBarStyleByProfile: Map<String, String>,
    val cardStyleByProfile: Map<String, String>
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

internal data class WindOverrideSectionSnapshot(
    val manualOverride: ManualWindOverrideSnapshot?
)

internal data class ManualWindOverrideSnapshot(
    val speedMs: Double,
    val directionFromDeg: Double,
    val timestampMillis: Long,
    val source: String
)
